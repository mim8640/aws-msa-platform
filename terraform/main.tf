locals {
  services = toset(["api-gateway", "member-service", "product-service", "order-service", "frontend"])
}

resource "aws_ecr_repository" "service" {
  for_each             = local.services
  name                 = each.key
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }
}

resource "aws_ecr_lifecycle_policy" "service" {
  for_each   = aws_ecr_repository.service
  repository = each.value.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1,
      description  = "Keep last 30 images",
      selection = {
        tagStatus   = "any",
        countType   = "imageCountMoreThan",
        countNumber = 30
      },
      action = {
        type = "expire"
      }
    }]
  })
}


# ============================================================
# S3 Image Bucket
# ============================================================

resource "aws_s3_bucket" "images" {
  bucket = var.image_bucket_name
}

resource "aws_s3_bucket_public_access_block" "images" {
  bucket                  = aws_s3_bucket.images.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "images" {
  bucket = aws_s3_bucket.images.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "images" {
  bucket = aws_s3_bucket.images.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}


# ============================================================
# S3 CORS
# www.renuvo.store -> S3 Presigned URL
# ============================================================

resource "aws_s3_bucket_cors_configuration" "images" {
  bucket = aws_s3_bucket.images.id

  cors_rule {
    allowed_headers = [
      "Content-Type"
    ]

    allowed_methods = [
      "GET",
      "HEAD",
      "PUT"
    ]

    allowed_origins = [
      "https://www.renuvo.store"
    ]

    expose_headers = [
      "ETag"
    ]

    max_age_seconds = 300
  }
}


# ============================================================
# Outputs
# ============================================================

output "ecr_urls" {
  value = {
    for k, v in aws_ecr_repository.service : k => v.repository_url
  }
}

output "image_bucket" {
  value = aws_s3_bucket.images.bucket
}
