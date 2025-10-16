# Terraform Configuration for LocalStack

This directory contains Terraform configuration files for managing AWS resources in LocalStack for local development.

## Overview

The Terraform configuration provisions the following resources in LocalStack:

- **S3 Buckets**: 6 buckets for different types of trading data
- **SQS Queues**: 5 queues for event processing
- **SNS Topics**: 4 topics for notifications
- **Secrets Manager**: 3 secrets for credentials
- **DynamoDB Tables**: 2 tables for session and state management

## Prerequisites

1. **Terraform**: Install Terraform v1.0 or higher
   ```bash
   # macOS
   brew install terraform
   
   # Linux
   wget https://releases.hashicorp.com/terraform/1.6.0/terraform_1.6.0_linux_amd64.zip
   unzip terraform_1.6.0_linux_amd64.zip
   sudo mv terraform /usr/local/bin/
   ```

2. **LocalStack**: Ensure LocalStack is running
   ```bash
   docker compose up -d localstack
   ```

3. **AWS CLI**: For verification (optional)
   ```bash
   pip install awscli
   ```

## Usage

### Initialize Terraform

```bash
cd terraform
terraform init
```

### Plan Changes

```bash
terraform plan
```

### Apply Configuration

```bash
terraform apply
```

When prompted, type `yes` to confirm.

### Show Current State

```bash
terraform show
```

### List Resources

```bash
terraform state list
```

### Destroy Resources

To remove all resources:

```bash
terraform destroy
```

## Configuration Details

### Provider Configuration

The provider is configured to use LocalStack endpoints:

```hcl
provider "aws" {
  region     = "us-east-1"
  access_key = "test"
  secret_key = "test"
  
  endpoints {
    s3             = "http://localhost:4566"
    sqs            = "http://localhost:4566"
    sns            = "http://localhost:4566"
    secretsmanager = "http://localhost:4566"
    dynamodb       = "http://localhost:4566"
  }
  
  s3_use_path_style = true
}
```

### Outputs

After applying, you'll see outputs for all created resources:

```bash
terraform output
```

To get a specific output:

```bash
terraform output s3_buckets
terraform output sqs_queue_urls
terraform output sns_topic_arns
```

### Output to JSON

```bash
terraform output -json > terraform-outputs.json
```

## Verification

### Verify S3 Buckets

```bash
aws --endpoint-url=http://localhost:4566 s3 ls
```

### Verify SQS Queues

```bash
aws --endpoint-url=http://localhost:4566 sqs list-queues
```

### Verify SNS Topics

```bash
aws --endpoint-url=http://localhost:4566 sns list-topics
```

### Verify Secrets

```bash
aws --endpoint-url=http://localhost:4566 secretsmanager list-secrets
```

### Verify DynamoDB Tables

```bash
aws --endpoint-url=http://localhost:4566 dynamodb list-tables
```

## Integration with Application

Use Terraform outputs in your application configuration:

```bash
# Export outputs as environment variables
export S3_ORDERS_BUCKET=$(terraform output -raw s3_buckets | jq -r '.orders')
export SQS_ORDER_EVENTS_URL=$(terraform output -raw sqs_queue_urls | jq -r '.order_events')
```

## Troubleshooting

### LocalStack Connection Issues

If Terraform can't connect to LocalStack:

1. Check LocalStack is running:
   ```bash
   curl http://localhost:4566/_localstack/health
   ```

2. Ensure no firewall is blocking port 4566

3. Try restarting LocalStack:
   ```bash
   docker compose restart localstack
   ```

### State File Issues

If you encounter state file issues:

```bash
# Remove state file and re-initialize
rm -rf .terraform terraform.tfstate*
terraform init
```

### Resource Already Exists

If resources already exist (e.g., from the bootstrap script):

```bash
# Import existing resource
terraform import aws_s3_bucket.trading_orders trading-orders

# Or destroy and recreate
docker compose down -v localstack
docker compose up -d localstack
terraform apply
```

## Best Practices

1. **Version Control**: Commit `main.tf` but not `terraform.tfstate`
   - State files are in `.gitignore`

2. **Local Only**: This configuration is for local development only
   - Do not use these credentials in production

3. **Clean Environment**: Start with a clean LocalStack instance
   ```bash
   docker compose down -v localstack
   docker compose up -d localstack
   ```

4. **Idempotent**: Safe to run `terraform apply` multiple times

## Files

- `main.tf` - Main Terraform configuration
- `.terraform/` - Terraform plugins (auto-generated, gitignored)
- `terraform.tfstate` - State file (auto-generated, gitignored)
- `terraform.tfstate.backup` - State backup (auto-generated, gitignored)

## Notes

- All resources are tagged with `Environment = "local"`
- Resources are named with the `trading-` prefix
- Secrets contain mock/test credentials only
- S3 buckets use path-style URLs (required for LocalStack)

## Next Steps

After provisioning:

1. Run the bootstrap script: `./scripts/localstack-bootstrap.sh`
2. Verify resources are created
3. Start developing with your application
4. Refer to `README-local.md` for application integration examples
