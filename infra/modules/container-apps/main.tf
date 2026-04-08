resource "azurerm_container_app_environment" "main" {
  name                       = "cae-wms-${var.environment}"
  location                   = var.location
  resource_group_name        = var.resource_group_name
  infrastructure_subnet_id   = var.snet_ca_id
  log_analytics_workspace_id = var.log_analytics_workspace_id
  tags                       = var.common_tags
}

resource "azurerm_container_app" "backend" {
  name                         = "ca-wms-backend-${var.environment}"
  container_app_environment_id = azurerm_container_app_environment.main.id
  resource_group_name          = var.resource_group_name
  revision_mode                = "Single"

  identity {
    type = "SystemAssigned"
  }

  registry {
    server   = var.acr_login_server
    identity = "system"
  }

  secret {
    name  = "database-username"
    value = var.db_username
  }
  secret {
    name  = "database-url"
    value = var.db_connection_string
  }
  secret {
    name  = "database-password"
    value = var.db_password
  }
  secret {
    name  = "jwt-secret-key"
    value = var.jwt_secret
  }
  secret {
    name  = "acs-connection-string"
    value = var.acs_connection_string
  }

  template {
    min_replicas = var.min_replicas
    max_replicas = var.max_replicas

    container {
      name   = "wms-backend"
      image  = var.image_tag == "init" ? "mcr.microsoft.com/azuredocs/containerapps-helloworld:latest" : "${var.acr_login_server}/wms-backend:${var.image_tag}"
      cpu    = 0.5
      memory = "1Gi"

      env {
        name  = "SPRING_PROFILES_ACTIVE"
        value = var.spring_profile
      }
      env {
        name  = "LOG_LEVEL"
        value = var.log_level
      }
      env {
        name        = "DATABASE_URL"
        secret_name = "database-url"
      }
      env {
        name        = "DATABASE_USERNAME"
        secret_name = "database-username"
      }
      env {
        name        = "DATABASE_PASSWORD"
        secret_name = "database-password"
      }
      env {
        name        = "JWT_SECRET_KEY"
        secret_name = "jwt-secret-key"
      }
      env {
        name  = "JWT_ACCESS_TOKEN_EXPIRATION"
        value = tostring(var.jwt_access_token_expiration_ms)
      }
      env {
        name  = "JWT_REFRESH_TOKEN_EXPIRATION"
        value = tostring(var.jwt_refresh_token_expiration_ms)
      }
      env {
        name        = "ACS_CONNECTION_STRING"
        secret_name = "acs-connection-string"
      }
      env {
        name  = "ACS_SENDER_ADDRESS"
        value = var.acs_sender_address
      }
      env {
        name  = "AZURE_STORAGE_ACCOUNT_NAME"
        value = var.storage_account_name
      }
      env {
        name  = "APPLICATIONINSIGHTS_CONNECTION_STRING"
        value = var.app_insights_connection_string
      }
      env {
        name  = "AZURE_FRONTDOOR_ID"
        value = var.frontdoor_id
      }
      env {
        name  = "CORS_ALLOWED_ORIGINS"
        value = var.cors_allowed_origins
      }

      liveness_probe {
        transport               = "HTTP"
        path                    = "/actuator/health/liveness"
        port                    = 8080
        interval_seconds        = 30
        timeout                 = 5
        failure_count_threshold = 3
      }

      readiness_probe {
        transport               = "HTTP"
        path                    = "/actuator/health/readiness"
        port                    = 8080
        interval_seconds        = 10
        timeout                 = 5
        failure_count_threshold = 3
      }

      startup_probe {
        transport               = "HTTP"
        path                    = "/actuator/health"
        port                    = 8080
        interval_seconds        = 10
        timeout                 = 5
        failure_count_threshold = 30
      }
    }

    http_scale_rule {
      name                = "http-scaling"
      concurrent_requests = 10
    }
  }

  ingress {
    external_enabled = true
    target_port      = 8080
    transport        = "http"

    traffic_weight {
      percentage      = 100
      latest_revision = true
    }
  }
}

# Blob Storage: Managed Identity (no connection string needed)
resource "azurerm_role_assignment" "ca_storage" {
  scope                = var.storage_account_id
  role_definition_name = "Storage Blob Data Contributor"
  principal_id         = azurerm_container_app.backend.identity[0].principal_id
}

# ACR: Managed Identity (no admin credentials)
resource "azurerm_role_assignment" "ca_acr" {
  scope                = var.acr_id
  role_definition_name = "AcrPull"
  principal_id         = azurerm_container_app.backend.identity[0].principal_id
}
