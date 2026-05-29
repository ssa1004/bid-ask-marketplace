# bid-ask-marketplace — 자주 쓰는 명령 단일 진입점
#
#   make up        인프라(Postgres/Redis/Kafka/Wiremock) 기동
#   make ps        컨테이너 상태
#   make logs      인프라 로그 follow
#   make run       앱 호스트 실행 (:8080)  — market-bootstrap
#   make demo      데모 시나리오 (BID/ASK 매칭 → 거래 → 멱등성)
#   make down      인프라 정지 (볼륨 유지)
#   make clean     인프라 정지 + 볼륨 삭제 (옛 데이터 제거)
#   make build     전체 gradle 빌드
#   make test      전체 테스트 (check)
#
# 앱은 호스트에서 ./gradlew :market-bootstrap:bootRun 으로 띄운다 — Kafka 는 localhost:9092 로
# 붙는다 (infrastructure/docker-compose.yml 의 EXTERNAL listener). 컨테이너끼리는 kafka:29092.
# 자세한 건 README "실행 방법" 절.

COMPOSE := docker compose -f infrastructure/docker-compose.yml
GRADLE  := ./gradlew

.DEFAULT_GOAL := help
.PHONY: help up ps logs run demo down clean build test

help: ## 이 도움말
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
	  | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

up: ## 인프라 기동 (Postgres/Redis/Kafka/Wiremock)
	$(COMPOSE) up -d
	@echo "→ Postgres :5432 · Redis :6379 · Kafka :9092 · Wiremock :8090"

ps: ## 컨테이너 상태
	$(COMPOSE) ps

logs: ## 인프라 로그 follow
	$(COMPOSE) logs -f --tail=100

run: ## 앱 호스트 실행 (:8080) — market-bootstrap
	$(GRADLE) :market-bootstrap:bootRun

demo: ## 데모 시나리오 실행 (앱이 :8080 에 떠 있어야 함)
	./scripts/demo.sh

down: ## 인프라 정지 (볼륨 유지)
	$(COMPOSE) down

clean: ## 인프라 정지 + 볼륨 삭제 (다음 기동 시 깨끗한 상태)
	$(COMPOSE) down -v

build: ## 전체 gradle 빌드 (테스트 제외)
	$(GRADLE) build -x test

test: ## 전체 테스트 (check)
	$(GRADLE) check
