.PHONY: help build up down restart logs status test clean smoke-test

help: ## Display this help screen
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

build: ## Build all Maven modules and Docker containers
	docker compose build

up: ## Start all infrastructure and microservices in detached mode
	docker compose up -d

down: ## Stop and remove all containers and networks
	docker compose down

restart: down up ## Restart all services

status: ## Show container health and runtime status
	docker compose ps

logs: ## Follow live logs across all containers
	docker compose logs -f

logs-order: ## Follow logs for order-service
	docker compose logs -f order-service

logs-gateway: ## Follow logs for api-gateway
	docker compose logs -f api-gateway

logs-kafka: ## Follow logs for kafka broker
	docker compose logs -f kafka

test: ## Run unit tests locally
	mvn clean test

verify: ## Run full integration test suite
	mvn clean verify

smoke-test: ## Run the end-to-end checkout Saga smoke test
	bash scripts/smoke-test.sh

clean: ## Prune dangling images and remove volume cache
	docker compose down -v --remove-orphans
