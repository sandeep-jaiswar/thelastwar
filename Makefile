.PHONY: help up down restart logs clean bootstrap validate test-integration build

# Default target
help:
	@echo "LocalStack Development Environment - Available Commands:"
	@echo ""
	@echo "  make up               - Start all services"
	@echo "  make down             - Stop all services"
	@echo "  make restart          - Restart all services"
	@echo "  make logs             - Show logs from all services"
	@echo "  make clean            - Stop services and remove volumes (clean slate)"
	@echo "  make bootstrap        - Run LocalStack bootstrap script"
	@echo "  make validate         - Validate LocalStack setup"
	@echo "  make test-integration - Run integration examples"
	@echo "  make build            - Build the project"
	@echo "  make terraform-init   - Initialize Terraform"
	@echo "  make terraform-apply  - Apply Terraform configuration"
	@echo ""

# Start all services
up:
	@echo "Starting all services..."
	docker compose up -d
	@echo "Waiting for services to be healthy..."
	@sleep 10
	@echo "Services started. Run 'make validate' to verify setup."

# Stop all services
down:
	@echo "Stopping all services..."
	docker compose down

# Restart all services
restart:
	@echo "Restarting all services..."
	docker compose restart

# Show logs
logs:
	docker compose logs -f

# Clean slate - remove everything
clean:
	@echo "⚠️  This will remove all containers, volumes, and data!"
	@read -p "Are you sure? [y/N] " -n 1 -r; \
	echo; \
	if [[ $$REPLY =~ ^[Yy]$$ ]]; then \
		docker compose down -v; \
		rm -rf localstack-data; \
		echo "✓ Clean complete"; \
	else \
		echo "Cancelled"; \
	fi

# Run bootstrap script
bootstrap:
	@echo "Running LocalStack bootstrap script..."
	./scripts/localstack-bootstrap.sh

# Validate setup
validate:
	@echo "Validating LocalStack setup..."
	./scripts/validate-localstack-setup.sh

# Run integration examples
test-integration:
	@echo "Running integration examples..."
	./gradlew :examples:localstack-integration:run

# Build the project
build:
	@echo "Building the project..."
	./gradlew build

# Initialize Terraform
terraform-init:
	@echo "Initializing Terraform..."
	cd terraform && terraform init

# Apply Terraform configuration
terraform-apply:
	@echo "Applying Terraform configuration..."
	cd terraform && terraform apply

# Quick setup - start everything and validate
quickstart: up
	@echo "Waiting for LocalStack to be ready..."
	@sleep 15
	@$(MAKE) bootstrap
	@echo ""
	@echo "✓ Quick start complete!"
	@echo ""
	@echo "Access points:"
	@echo "  - LocalStack:        http://localhost:4566"
	@echo "  - Redpanda Console:  http://localhost:8080"
	@echo "  - PostgreSQL:        localhost:5432"
	@echo "  - ClickHouse:        http://localhost:8123"
	@echo "  - Redis:             localhost:6379"
	@echo ""
	@echo "Run 'make validate' to verify setup"
	@echo "Run 'make test-integration' to test AWS SDK integration"
