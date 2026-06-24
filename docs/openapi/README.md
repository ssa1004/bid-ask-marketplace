# OpenAPI spec

`resell-orderbook` 의 REST API 를 OpenAPI 3 spec 으로 build-time export 한다.

## 무엇이 들어가나

- `resell-orderbook.yaml` — 빌드 시 생성되는 OpenAPI 3 문서. 외부 참조 / SDK codegen 의 단일 진실값.
  - 거래 / Bid·Ask 매칭 (`/api/v1/listings`, `/api/v1/bids`, `/api/v1/orderbook`)
  - 거래 라이프사이클 Saga (`/api/v1/trades`)
  - 시세 / 카탈로그 (`/api/v1/market`, `/api/v1/products`)
  - 검수 / 검수 스케줄링 / 운영 / DLQ (`/api/v1/inspection-requests`, `/api/v1/inspection`, `/api/v1/admin`, `/api/v1/admin/dlq`)

WebSocket 실시간 호가 push 는 OpenAPI 대상이 아니다 (REST endpoint 만 spec 에 포함).

> 이 디렉토리의 `*.yaml` 은 CI 에서 생성·갱신된다. 로컬에서 수기로 편집하지 않는다.

## 생성 방법

`org.springdoc.openapi-gradle-plugin` 을 `market-bootstrap` 모듈에 적용했다.
`generateOpenApiDocs` 태스크가 앱을 부팅한 뒤 `/v3/api-docs.yaml` 을 받아
`docs/openapi/resell-orderbook.yaml` 로 저장한다.

```bash
./gradlew :market-bootstrap:generateOpenApiDocs
```

기본 `dev` 프로필은 H2 + Mock 어댑터로 부팅하므로 **Postgres / Kafka / Redis 등 외부
인프라가 필요 없다** (Redis/Kafka auto-config 는 `application.yml` 에서 exclude). 단,
플러그인이 fork 하는 앱 프로세스는 **시스템 기본 `java` (JDK 21)** 로 실행되므로, `JAVA_HOME`
이 21 이어야 한다. 빌드 toolchain 은 21 로 컴파일하지만 fork 실행은 toolchain JVM 을 쓰지
않기 때문에, 기본 `java` 가 21 미만이면 forked 프로세스가 `UnsupportedClassVersionError`
로 뜨지 못한다.

CI(JDK 21 러너) 에서는 `openapi-drift` job 이 외부 컨테이너 없이 위 태스크를 그대로 실행한
뒤 `git diff --exit-code docs/openapi/resell-orderbook.yaml` 로 커밋된 spec 이 소스와
일치하는지 검증한다. 컨트롤러를 바꾸고 spec 재생성을 빠뜨리면 (= drift) CI 가 실패한다.

## 보는 법

- Swagger UI — 앱 실행 후 `http://localhost:8080/swagger` (path 는 `application.yml` 의 `springdoc.swagger-ui.path`)
- Redoc — `npx @redocly/cli preview-docs docs/openapi/resell-orderbook.yaml`
- 통합 뷰어 — profile repo `ssa1004/ssa1004` 의 `docs/api/index.html` (11 service spec 드롭다운)
