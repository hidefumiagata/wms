resource "azurerm_storage_account" "main" {
  name                     = "stwms${var.environment}"
  resource_group_name      = var.resource_group_name
  location                 = var.location
  account_tier             = "Standard"
  account_replication_type = var.storage_replication
  account_kind             = "StorageV2"
  min_tls_version          = "TLS1_2"

  static_website {
    index_document     = "index.html"
    error_404_document = "index.html"
  }

  blob_properties {
    cors_rule {
      allowed_headers    = ["*"]
      allowed_methods    = ["GET", "HEAD"]
      allowed_origins    = ["*"]
      exposed_headers    = ["*"]
      max_age_in_seconds = 3600
    }
  }

  tags = var.common_tags
}

resource "azurerm_storage_container" "iffiles" {
  name                  = "iffiles"
  storage_account_id    = azurerm_storage_account.main.id
  container_access_type = "private"
}
