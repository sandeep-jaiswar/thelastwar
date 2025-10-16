# Terraform Configuration for LocalStack
# This configuration manages AWS resources in LocalStack for local development

terraform {
  required_version = ">= 1.0"
  
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# Provider configuration for LocalStack
provider "aws" {
  region                      = "us-east-1"
  access_key                  = "test"
  secret_key                  = "test"
  skip_credentials_validation = true
  skip_metadata_api_check     = true
  skip_requesting_account_id  = true

  # LocalStack endpoints
  endpoints {
    s3             = "http://localhost:4566"
    sqs            = "http://localhost:4566"
    sns            = "http://localhost:4566"
    secretsmanager = "http://localhost:4566"
    dynamodb       = "http://localhost:4566"
    cloudwatch     = "http://localhost:4566"
  }

  # Use path-style S3 URLs (required for LocalStack)
  s3_use_path_style           = true
}

# S3 Buckets for trading platform
resource "aws_s3_bucket" "trading_orders" {
  bucket = "trading-orders"
  
  tags = {
    Name        = "Trading Orders"
    Environment = "local"
    Purpose     = "order-snapshots"
  }
}

resource "aws_s3_bucket" "trading_executions" {
  bucket = "trading-executions"
  
  tags = {
    Name        = "Trading Executions"
    Environment = "local"
    Purpose     = "execution-reports"
  }
}

resource "aws_s3_bucket" "trading_positions" {
  bucket = "trading-positions"
  
  tags = {
    Name        = "Trading Positions"
    Environment = "local"
    Purpose     = "position-snapshots"
  }
}

resource "aws_s3_bucket" "trading_snapshots" {
  bucket = "trading-snapshots"
  
  tags = {
    Name        = "Trading Snapshots"
    Environment = "local"
    Purpose     = "oms-state-snapshots"
  }
}

resource "aws_s3_bucket" "trading_analytics" {
  bucket = "trading-analytics"
  
  tags = {
    Name        = "Trading Analytics"
    Environment = "local"
    Purpose     = "analytics-data"
  }
}

resource "aws_s3_bucket" "trading_reports" {
  bucket = "trading-reports"
  
  tags = {
    Name        = "Trading Reports"
    Environment = "local"
    Purpose     = "generated-reports"
  }
}

# SQS Queues
resource "aws_sqs_queue" "order_events" {
  name                      = "order-events"
  delay_seconds             = 0
  max_message_size          = 262144
  message_retention_seconds = 345600
  receive_wait_time_seconds = 0
  
  tags = {
    Name        = "Order Events Queue"
    Environment = "local"
  }
}

resource "aws_sqs_queue" "execution_events" {
  name                      = "execution-events"
  delay_seconds             = 0
  max_message_size          = 262144
  message_retention_seconds = 345600
  receive_wait_time_seconds = 0
  
  tags = {
    Name        = "Execution Events Queue"
    Environment = "local"
  }
}

resource "aws_sqs_queue" "risk_alerts" {
  name                      = "risk-alerts"
  delay_seconds             = 0
  max_message_size          = 262144
  message_retention_seconds = 86400
  receive_wait_time_seconds = 0
  
  tags = {
    Name        = "Risk Alerts Queue"
    Environment = "local"
  }
}

resource "aws_sqs_queue" "position_updates" {
  name                      = "position-updates"
  delay_seconds             = 0
  max_message_size          = 262144
  message_retention_seconds = 345600
  receive_wait_time_seconds = 0
  
  tags = {
    Name        = "Position Updates Queue"
    Environment = "local"
  }
}

resource "aws_sqs_queue" "market_data_events" {
  name                      = "market-data-events"
  delay_seconds             = 0
  max_message_size          = 262144
  message_retention_seconds = 3600
  receive_wait_time_seconds = 0
  
  tags = {
    Name        = "Market Data Events Queue"
    Environment = "local"
  }
}

# SNS Topics
resource "aws_sns_topic" "order_notifications" {
  name = "order-notifications"
  
  tags = {
    Name        = "Order Notifications Topic"
    Environment = "local"
  }
}

resource "aws_sns_topic" "execution_notifications" {
  name = "execution-notifications"
  
  tags = {
    Name        = "Execution Notifications Topic"
    Environment = "local"
  }
}

resource "aws_sns_topic" "risk_alerts_topic" {
  name = "risk-alerts-topic"
  
  tags = {
    Name        = "Risk Alerts Topic"
    Environment = "local"
  }
}

resource "aws_sns_topic" "system_alerts" {
  name = "system-alerts"
  
  tags = {
    Name        = "System Alerts Topic"
    Environment = "local"
  }
}

# SNS to SQS subscriptions
resource "aws_sns_topic_subscription" "risk_alerts_to_queue" {
  topic_arn = aws_sns_topic.risk_alerts_topic.arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.risk_alerts.arn
}

# Secrets Manager secrets
resource "aws_secretsmanager_secret" "fix_credentials" {
  name        = "fix/credentials"
  description = "Mock FIX protocol credentials for local development"
  
  tags = {
    Name        = "FIX Credentials"
    Environment = "local"
  }
}

resource "aws_secretsmanager_secret_version" "fix_credentials" {
  secret_id = aws_secretsmanager_secret.fix_credentials.id
  secret_string = jsonencode({
    senderCompId = "BROKER001"
    targetCompId = "EXCHANGE001"
    username     = "fix_user"
    password     = "fix_password"
    host         = "localhost"
    port         = "9876"
  })
}

resource "aws_secretsmanager_secret" "api_credentials" {
  name        = "api/credentials"
  description = "Mock API credentials for local development"
  
  tags = {
    Name        = "API Credentials"
    Environment = "local"
  }
}

resource "aws_secretsmanager_secret_version" "api_credentials" {
  secret_id = aws_secretsmanager_secret.api_credentials.id
  secret_string = jsonencode({
    apiKey    = "mock-api-key-12345"
    apiSecret = "mock-api-secret-67890"
    traderId  = "TRADER001"
    accountId = "ACC12345"
  })
}

resource "aws_secretsmanager_secret" "database_credentials" {
  name        = "database/credentials"
  description = "Database connection credentials"
  
  tags = {
    Name        = "Database Credentials"
    Environment = "local"
  }
}

resource "aws_secretsmanager_secret_version" "database_credentials" {
  secret_id = aws_secretsmanager_secret.database_credentials.id
  secret_string = jsonencode({
    postgres = {
      host     = "postgres"
      port     = 5432
      database = "trading"
      username = "trading_user"
      password = "trading_pass"
    }
    clickhouse = {
      host     = "clickhouse"
      port     = 8123
      database = "ticks"
      username = "clickhouse_user"
      password = "clickhouse_pass"
    }
    redis = {
      host = "redis"
      port = 6379
    }
  })
}

# DynamoDB Tables
resource "aws_dynamodb_table" "trading_sessions" {
  name           = "trading-sessions"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "sessionId"
  range_key      = "timestamp"

  attribute {
    name = "sessionId"
    type = "S"
  }

  attribute {
    name = "timestamp"
    type = "N"
  }

  tags = {
    Name        = "Trading Sessions"
    Environment = "local"
  }
}

resource "aws_dynamodb_table" "order_state" {
  name           = "order-state"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "orderId"

  attribute {
    name = "orderId"
    type = "S"
  }

  tags = {
    Name        = "Order State"
    Environment = "local"
  }
}

# Outputs
output "s3_buckets" {
  description = "S3 bucket names"
  value = {
    orders     = aws_s3_bucket.trading_orders.id
    executions = aws_s3_bucket.trading_executions.id
    positions  = aws_s3_bucket.trading_positions.id
    snapshots  = aws_s3_bucket.trading_snapshots.id
    analytics  = aws_s3_bucket.trading_analytics.id
    reports    = aws_s3_bucket.trading_reports.id
  }
}

output "sqs_queue_urls" {
  description = "SQS queue URLs"
  value = {
    order_events      = aws_sqs_queue.order_events.url
    execution_events  = aws_sqs_queue.execution_events.url
    risk_alerts       = aws_sqs_queue.risk_alerts.url
    position_updates  = aws_sqs_queue.position_updates.url
    market_data_events = aws_sqs_queue.market_data_events.url
  }
}

output "sns_topic_arns" {
  description = "SNS topic ARNs"
  value = {
    order_notifications     = aws_sns_topic.order_notifications.arn
    execution_notifications = aws_sns_topic.execution_notifications.arn
    risk_alerts            = aws_sns_topic.risk_alerts_topic.arn
    system_alerts          = aws_sns_topic.system_alerts.arn
  }
}

output "secret_arns" {
  description = "Secrets Manager secret ARNs"
  value = {
    fix_credentials      = aws_secretsmanager_secret.fix_credentials.arn
    api_credentials      = aws_secretsmanager_secret.api_credentials.arn
    database_credentials = aws_secretsmanager_secret.database_credentials.arn
  }
}

output "dynamodb_tables" {
  description = "DynamoDB table names"
  value = {
    trading_sessions = aws_dynamodb_table.trading_sessions.name
    order_state      = aws_dynamodb_table.order_state.name
  }
}
