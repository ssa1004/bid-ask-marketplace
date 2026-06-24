# 백엔드 스킬 인덱스 — 이 레포에서 무엇을 배우나

> 이 레포(한정판 리셀 마켓 Bid/Ask 매칭 + 거래 라이프사이클 백엔드)가 시연하는 백엔드 / 운영
> 패턴을 **"무엇 → 이 레포 어디서 → 왜(ADR) → 더 깊은 이론"** 으로 잇는 학습용 인덱스.
> "이 패턴 공부하려면 어디부터 보나"의 진입점. 설명을 다시 쓰지 않고 코드·결정·이론으로 연결만 한다.
> 결정 배경은 [docs/adr/](adr/) 의 ADR 28건에 정리되어 있다.

## 동시성 · 락 (Postgres)

| 패턴 | 이 레포 어디서 | 왜 (ADR) | 한 줄 |
|------|---------------|---------|-------|
| **advisory lock (`pg_advisory_xact_lock`)** | `PlaceListingService` / `PlaceBidService` 가 `OrderBookQueryPort.acquireSkuLock(skuId)` 호출 (`adapter/out/orderbook`) | [ADR-0005](adr/0005-concurrency-for-update-skip-locked.md) | 같은 SKU 의 매칭만 한 줄로 직렬화 — 다른 SKU 는 병렬. row 없이 hash(sku) 기반 잠금, 트랜잭션 종료 시 자동 해제 |
| **`SELECT … FOR UPDATE SKIP LOCKED`** | `SpringDataBidRepository.findHighestBidForUpdate` / `SpringDataListingRepository` (`adapter/out/persistence/jpa/repository`) | [ADR-0005](adr/0005-concurrency-for-update-skip-locked.md) | best 호가 한 행만 비관적 락 + 잠긴 행은 대기 없이 건너뜀 — 같은 SKU 큐가 빠르게 흐름 |
| **애그리거트 경계로 락 범위 축소** | `Listing` / `Bid` / `Trade` 를 각각 별도 애그리거트 (`market-domain`) | [ADR-0003](adr/0003-ddd-aggregate-boundaries.md) | OrderBook 전체를 한 락에 잡지 않음 — hot SKU 의 best 한 행만 잠가 처리량 보존 |
| **inspection slot capacity 잠금** | `JpaInspectionSlotLockAdapter` (`adapter/out/inspection`) | [ADR-0017](adr/0017-inspection-slot-management.md) | 검수 슬롯 capacity 초과 예약 방지 — 동시 예약 직렬화 |

→ 이론: `dev-lab/postgresql` (advisory lock / SKIP LOCKED / MVCC / 격리수준 / 비관적 vs 낙관적 락), `dev-lab/distributed-systems` (분산 락 vs DB 락)

## 분산 트랜잭션 · Saga · 보상

| 패턴 | 이 레포 어디서 | 왜 (ADR) | 한 줄 |
|------|---------------|---------|-------|
| **Saga (코레오그래피)** | `TradingSagaConsumer` (`adapter/kafka`) + 단계별 service (`AuthorizePaymentService` / `SettleTradeService` / `RefundBuyerService`) | [ADR-0004](adr/0004-trade-saga-choreography.md) | 매칭→결제→검수→정산 7~10 단계를 중앙 조정자 없이 이벤트로 연결. 한 단계 장애가 다른 단계로 안 번짐 |
| **보상 트랜잭션 (검수 실패 → 환불)** | `RefundBuyerService` / `RetryRefundService` (`market-application/service`) | [ADR-0004](adr/0004-trade-saga-choreography.md), [ADR-0023](adr/0023-idempotent-saga-compensation.md) | 강한 일관성 대신 최종 일관성 + 보상. 검수비/배송비 포함 전액 환불 |
| **보상의 명시 멱등성 (Compensation Log)** | `CompensationGuard.runOnce(op, key, action)` + `CompensationLogStore` (`application/service`, `adapter/out/persistence/compensation`) | [ADR-0023](adr/0023-idempotent-saga-compensation.md) | `(operation, businessKey)` 합성 PK 로 자리 점유 → 외부 호출 → 결과 캐시. REQUIRES_NEW 로 메인 TX 와 분리해 "PG/은행이 실제 호출됐는지" 흔적 보존 — 응답 유실에도 정확히 한 번 |
| **시스템 트리거 멱등성 (상태 체크)** | `AuthorizePaymentService` 가 Trade 상태 확인 후 skip | [ADR-0008](adr/0008-idempotency-redis-nx.md) | at-least-once 재전송으로 같은 이벤트 두 번 와도, 이미 `PAYMENT_AUTHORIZED` 면 PG 호출 건너뜀 |

→ 이론: `dev-lab/distributed-systems` (Saga / 보상 / 2PC vs Saga / exactly-once 환상 / 정합성), `dev-lab/temporal` (durable execution 대안)

## 메시징 · 일관성 (Outbox + Kafka)

| 패턴 | 이 레포 어디서 | 왜 (ADR) | 한 줄 |
|------|---------------|---------|-------|
| **Outbox 패턴** | `OutboxEventPublisher` (도메인 TX 안 INSERT) + `OutboxRelay` (`adapter/out/messaging`, `adapter/out/persistence/outbox`) | [ADR-0007](adr/0007-outbox-pattern.md) | DB 커밋과 이벤트 발행을 한 트랜잭션으로 — dual-write 의 유실/유령 이벤트 해소. Relay 가 `@Scheduled` 폴링으로 미발행 row 를 Kafka 전송 |
| **at-least-once + 멱등 컨슈머** | Saga consumer 전체 | [ADR-0004](adr/0004-trade-saga-choreography.md), [ADR-0023](adr/0023-idempotent-saga-compensation.md) | 전달 의미의 현실적 선택 — 중복은 도메인 상태 / Compensation Log 로 흡수 |
| **사용자 요청 멱등성 (Redis NX)** | `IdempotencyKeyStore.acquireOrThrow(key)` (`adapter/out/persistence/idempotency`) + `Idempotency-Key` 헤더 | [ADR-0008](adr/0008-idempotency-redis-nx.md) | 같은 ASK/BID 두 번 등록 차단 — Redis SETNX 선점, rollback 시 자동 해제로 정당한 재시도는 허용 |
| **DLQ + 관리 콘솔 API** | `AdminDlqController` (`adapter/web`) + `KafkaDlqMessageStore` (`adapter/out/dlq`) | [ADR-0028](adr/0028-dlq-admin-api.md) | 컨슈머 실패 → `<원본>-dlt` 토픽 격리. 조회 / replay / discard / bulk(dry-run 강제) 8 endpoint |

→ 이론: `dev-lab/kafka` (at-least-once / DLQ / consumer), `dev-lab/cdc` (Outbox vs Debezium CDC), `dev-lab/distributed-systems` (멱등성 / dual-write)

## 회복탄력성 (Resilience)

| 패턴 | 이 레포 어디서 | 왜 (ADR) | 한 줄 |
|------|---------------|---------|-------|
| **Circuit Breaker + Retry (PG)** | `RestPgClient` `@CircuitBreaker(name="pg")` + `@Retry` + fallback (`adapter/out/pg`) | [ADR-0009](adr/0009-resilience4j-pg.md) | PG 장애가 우리 Trade 트랜잭션으로 전파되지 않게 차단 — 최근 20건 중 실패율 50% 초과 시 OPEN, 30초 후 half-open |
| **ThreadPool Bulkhead 격리**(= 배의 방수 격벽처럼 외부 호출을 종류별 전용 풀에 가둬, 한 곳이 느려져도 그 풀만 막히고 나머지는 멀쩡하게 두는 격리) | `BulkheadedPgClient` / `BulkheadedBankTransferClient` (`adapter/out/bulkhead`) | [ADR-0021](adr/0021-bulkhead-external-call-isolation.md) | PG / 은행 호출을 전용 풀에 격리 — 한 외부 의존의 슬로우다운이 호가창·검수 endpoint 로 cascade 안 됨. CB 차단 전 servlet thread 점유부터 방지 |
| **Retry: exponential backoff + jitter** | PG / 은행 / 검수센터 retry instance | [ADR-0026](adr/0026-retry-with-exponential-backoff-and-jitter.md) | `randomizedWaitFactor` 로 회복 순간 thundering herd 방지 + `retryExceptions` 화이트리스트 (4xx 는 retry 안 함) |
| **HikariCP 튜닝 + leak detection** | DataSource 설정 (`market-bootstrap`) | [ADR-0024](adr/0024-hikaricp-tuning-and-leak-detection.md) | 풀 크기를 `도착률 × 평균 TX 시간` 으로 산정 + `leak-detection-threshold` 로 누수 코드 위치를 stack trace 로 즉시 노출 |
| **Graceful shutdown + startup probe + preStop** | Spring `server.shutdown=graceful` + k8s manifests (`infrastructure/k8s`) | [ADR-0027](adr/0027-graceful-shutdown-and-startup-probe.md) | rolling restart 시 in-flight 매칭 / Kafka commit 손실 0 |

→ 이론: `dev-lab/resilience` (circuit breaker / bulkhead / retry / 격벽), `dev-lab/networking` (커넥션 풀 sizing / timeout), `dev-lab/kafka` (DLQ)

## 처리량 · 캐시 · 데이터 경로

| 패턴 | 이 레포 어디서 | 왜 (ADR) | 한 줄 |
|------|---------------|---------|-------|
| **2단 캐시 (Caffeine L1 + Redis L2) + stampede 보호**(= 캐시 유효기간이 끝나는 순간 여러 요청이 한꺼번에 DB로 몰려 짓밟는 현상(stampede)을, 만료 직전 미리 갱신 + 한 명만 갱신하게 잠가서 막는 것) | `TwoTierMarketStatsCache` (`adapter/out/cache`) | [ADR-0019](adr/0019-multi-tier-cache-with-stampede-protection.md) | hot SKU 시세 카드 폭주 흡수 — L1(1초)·L2(10초) + cache stampede(thundering herd) 방지 |
| **Cache invalidation cross-pod broadcast** | `CacheInvalidationPublisher` / `CacheInvalidationSubscriber` (`adapter/out/cache`, Redis pub/sub) | [ADR-0022](adr/0022-cache-invalidation-pubsub.md) | 한 pod 갱신을 다른 pod 의 L1 까지 즉시 전파 — pod 간 1초 stale 제거 |
| **Token bucket rate limiter (Redis Lua)** | `RedisTokenBucketRateLimiter` (`adapter/out/ratelimit`) | [ADR-0020](adr/0020-token-bucket-rate-limiter.md) | 발매 시점 자동화 스크립트 폭주로부터 호가/즉시매매 endpoint 보호 — `429 + Retry-After` (RFC 6585) |
| **CQRS — 쓰기 JPA, 읽기 분리** | 쓰기 애그리거트 vs `OrderBookQueryPort` / MarketData read | [ADR-0006](adr/0006-cqrs-write-vs-read.md) | 호가창/차트 읽기는 쓰기와 모양이 달라 필요한 곳만 분리 — 불필요한 N+1 회피 |
| **Cursor-based pagination** | `SpringDataTradeRepository` / `SpringDataPriceTickRepository` (`adapter/out/persistence/jpa/repository`) | [ADR-0025](adr/0025-cursor-based-pagination.md) | OFFSET 뒷페이지 절벽 회피 — 마지막 본 정렬키로 점프. 새 row 끼어듦에도 중복/누락 없음 |

→ 이론: `dev-lab/performance` (hot path / 캐시 / stampede), `dev-lab/postgresql` (인덱스 / keyset pagination / MVCC), `dev-lab/system-design` (CQRS read 분리 / rate limit)

## 도메인 모델링 · 시계열

| 패턴 | 이 레포 어디서 | 왜 (ADR) | 한 줄 |
|------|---------------|---------|-------|
| **헥사고날 + Spring Modulith**(= 핵심 로직을 가운데 두고 DB·Kafka·웹은 콘센트(port)와 플러그(adapter)로만 연결해 바깥을 바꿔도 핵심 코드는 안 건드리는 구조 + 모듈 경계를 빌드 시점에 검증하는 도구) | 6개 gradle 모듈 (domain/application/adapter-in/adapter-out/batch/bootstrap) | [ADR-0001](adr/0001-modular-monolith-with-spring-modulith.md), [ADR-0002](adr/0002-hexagonal-architecture.md) | 모듈 의존 방향을 빌드 시점에 `ApplicationModules.verify()` 로 검증. MSA 대신 모듈러 모놀리스. 도메인은 순수 (Spring/JPA 의존 0) |
| **FeeSnapshot (정책 동결)** | `Trade.match()` 시점의 `FeePolicy.snapshotFor(price)` (`market-domain`) | [ADR-0012](adr/0012-fee-snapshot.md) | 수수료 정책이 바뀌어도 과거 거래 정산액 불변 — Trade 가 그 시점 수수료 계산서를 명시 컬럼으로 품음 |
| **Snowflake ID (시간 정렬 64bit)** | PriceTick 식별자 (`market-domain`) | [ADR-0018](adr/0018-snowflake-id-for-pricetick.md) | 시계열 tick 의 시간 정렬 가능한 분산 ID — auto-increment 병목 없이 정렬 보존 |
| **Market Data 시계열 + OHLC 사전 집계** | MarketData read API + OHLC 집계 batch (`market-batch`) | [ADR-0015](adr/0015-market-data-time-series.md), [ADR-0016](adr/0016-ohlc-candlestick-aggregation.md) | 시세 통계 / 캔들스틱을 매 호출 재계산 대신 사전 집계 |
| **실시간 호가창 (STOMP over WebSocket)** | `adapter/ws` (STOMP) | [ADR-0011](adr/0011-websocket-orderbook-stream.md), [ADR-0014](adr/0014-stomp-over-raw-websocket.md) | 폴링 대신 push — 호가 등록/체결 시 구독자에게 스냅샷 전송 |

→ 이론: `dev-lab/system-design` (헥사고날 / 모듈 경계 / DDD 애그리거트 / 분산 ID), `dev-lab/postgresql` (시계열 / 인덱스)

## 배치 · 스케줄링

| 패턴 | 이 레포 어디서 | 왜 (ADR) | 한 줄 |
|------|---------------|---------|-------|
| **Spring Batch — 만료 호가 정리 / TTL 초과 거래 자동 취소** | `StaleListingExpirationJobConfig` / `StaleBidExpirationJobConfig` / `AutoCancelStaleTradesJobConfig` (`market-batch`) | [ADR-0010](adr/0010-spring-batch.md) | 결제 TTL 초과 거래 자동 취소 + 만료 호가 정리를 chunk 단위로 안전 처리 |
| **`@Scheduled` + ShedLock** | `MarketJobScheduler` + `ShedLockConfig` (`market-batch/scheduler`) | [ADR-0013](adr/0013-batch-scheduling-shedlock.md) | replica > 1 에서도 batch 가 정확히 한 인스턴스만 실행 — DB 한 행 잠금 |

→ 이론: `dev-lab/distributed-systems` (분산 스케줄 / 단일 실행 보장), `dev-lab/system-design` (batch vs streaming)

## 운영 / SRE

| 패턴 | 이 레포 어디서 | 왜 (ADR) | 한 줄 |
|------|---------------|---------|-------|
| **DLQ 관리 콘솔 API** | `AdminDlqController` (`adapter/web`) + `KafkaDlqMessageStore` (`adapter/out/dlq`) | [ADR-0028](adr/0028-dlq-admin-api.md) | source / bySku 차원 조회 + 단건/bulk replay·discard. bulk 는 `confirm=true` 없으면 dry-run 강제, discard 는 reason 필수 + audit |

→ 이론: `dev-lab/incident-response` (운영 콘솔 / 안전망), `dev-lab/resilience` (DLQ replay)

## 학습 순서 제안 (이 레포 기준)

1. **[README](../README.md) 상단 + 시스템 흐름 다이어그램** → ASK/BID 매칭부터 정산까지 한 사이클 감 잡기
2. **[docs/adr/](adr/)** → 왜 그렇게 했나 (ADR 28건) ← 이 레포의 핵심 학습 자료. 특히 매칭/거래 특화는
   ADR-0005(advisory lock + SKIP LOCKED) / 0004(Saga 코레오그래피) / 0023(보상 멱등성 Compensation Log) /
   0007(Outbox) / 0012(FeeSnapshot)
3. **위 패턴 표** 에서 관심 패턴 → 코드 위치 + 해당 ADR + dev-lab 이론
4. **매칭 직렬화 안전망** (advisory lock + `FOR UPDATE SKIP LOCKED` + 별도 애그리거트) → 같은 SKU 의
   이중 체결을 어떻게 막으면서 다른 SKU 처리량을 보존하는지 (ADR-0005 / 0003)
5. **거래 라이프사이클의 정합성 방어선** (Outbox + at-least-once + Compensation Log) → 외부 PG/은행
   응답 유실에도 "정확히 한 번" 을 어떻게 보장하는지 (ADR-0007 / 0023)
6. **[load/](../load/)** → k6 `concurrent-match` 시나리오가 client 단에서 가드하는 invariant
   (같은 `matchedTradeId` 중복 = 위반)

> 짝 학습 레포: [dev-lab](https://github.com/ssa1004/dev-lab) (이론) ↔ 이 레포 (구현). 이론에서 "왜"를, 여기서 "실제로 어떻게"를 본다.
> 매칭/거래 도메인은 특히 `dev-lab/postgresql`(advisory lock·SKIP LOCKED·MVCC·격리수준) · `dev-lab/distributed-systems`(Saga·보상·정합성) · `dev-lab/cdc`(Outbox) · `dev-lab/resilience`(CB·bulkhead·retry) 와 짝으로 보면 좋다.
