IMAGE         := loyalty-discount-api
COMPOSE_FILE  := docker-compose.local.yml
COMPOSE       := docker compose -f $(COMPOSE_FILE)

.PHONY: build up seed logs down

## Build the API image
build:
	$(COMPOSE) build

## Build + start LocalStack + API containers
up: build
	$(COMPOSE) up -d

## Upload sample Excel sheet into LocalStack S3
seed:
	@echo "⏳ seeding sheet into LocalStack ..."
	$(COMPOSE) exec localstack awslocal s3 mb s3://rules-test || true
	$(COMPOSE) exec localstack awslocal s3 cp /init/loyalty-rules.xlsx \
	   s3://rules-test/rules/loyalty-discount-rules-latest.xlsx
	@echo "✅ sheet uploaded"

## Tail the API logs
logs:
	$(COMPOSE) logs -f api

## Stop and clean volumes
down:
	$(COMPOSE) down -v