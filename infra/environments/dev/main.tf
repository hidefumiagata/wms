terraform {
  required_version = ">= 1.5.0"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
  }
}

provider "azurerm" {
  subscription_id = var.subscription_id
  features {}
}

locals {
  common_tags = {
    project     = "wms"
    environment = var.environment
    managed_by  = "terraform"
  }
}

# --- Resource Group ---

resource "azurerm_resource_group" "main" {
  name     = "rg-wms-${var.environment}"
  location = var.location
  tags     = local.common_tags
}

# --- Modules ---

module "monitoring" {
  source              = "../../modules/monitoring"
  environment         = var.environment
  location            = var.location
  resource_group_name = azurerm_resource_group.main.name
  alert_email         = var.alert_email
  common_tags         = local.common_tags
}

module "vnet" {
  source              = "../../modules/vnet"
  environment         = var.environment
  location            = var.location
  resource_group_name = azurerm_resource_group.main.name
  vnet_cidr           = var.vnet_cidr
  snet_ca_cidr        = var.snet_ca_cidr
  snet_pg_cidr        = var.snet_pg_cidr
  create_pg_subnet    = true
  enable_front_door   = var.enable_front_door
  common_tags         = local.common_tags
}

module "acr" {
  source      = "../../modules/acr"
  location    = var.location
  common_tags = local.common_tags
}

module "storage" {
  source              = "../../modules/storage"
  environment         = var.environment
  location            = var.location
  resource_group_name = azurerm_resource_group.main.name
  storage_replication = var.storage_replication
  common_tags         = local.common_tags
}

module "communication_services" {
  source              = "../../modules/communication-services"
  environment         = var.environment
  resource_group_name = azurerm_resource_group.main.name
  common_tags         = local.common_tags
}

module "postgresql" {
  source              = "../../modules/postgresql"
  environment         = var.environment
  location            = var.location
  resource_group_name = azurerm_resource_group.main.name
  snet_pg_id          = module.vnet.snet_pg_id
  private_dns_zone_id = module.vnet.private_dns_zone_id
  db_admin_password   = var.db_admin_password
  db_sku_name         = var.db_sku_name
  db_storage_mb       = var.db_storage_mb
  db_geo_backup       = var.db_geo_backup
  db_auto_grow        = var.db_auto_grow
  common_tags         = local.common_tags
}

module "container_apps" {
  source                         = "../../modules/container-apps"
  environment                    = var.environment
  location                       = var.location
  resource_group_name            = azurerm_resource_group.main.name
  snet_ca_id                     = module.vnet.snet_ca_id
  log_analytics_workspace_id     = module.monitoring.log_analytics_workspace_id
  acr_login_server               = module.acr.acr_login_server
  acr_id                         = module.acr.acr_id
  image_tag                      = var.image_tag
  min_replicas                   = var.min_replicas
  max_replicas                   = var.max_replicas
  spring_profile                 = var.environment
  log_level                      = var.log_level
  db_connection_string           = module.postgresql.connection_string
  db_password                    = var.db_admin_password
  jwt_secret                     = var.jwt_secret
  acs_connection_string          = module.communication_services.connection_string
  storage_account_id             = module.storage.storage_account_id
  storage_account_name           = module.storage.storage_account_name
  app_insights_connection_string = module.monitoring.app_insights_connection_string
  frontdoor_id                   = ""
  cors_allowed_origins           = module.storage.static_website_url
  common_tags                    = local.common_tags
}

# --- Diagnostic Settings (environment-level, cross-module resources) ---

resource "azurerm_monitor_diagnostic_setting" "cae" {
  name                       = "diag-cae-${var.environment}"
  target_resource_id         = module.container_apps.container_app_environment_id
  log_analytics_workspace_id = module.monitoring.log_analytics_workspace_id

  enabled_log {
    category = "ContainerAppConsoleLogs"
  }
  enabled_log {
    category = "ContainerAppSystemLogs"
  }
  enabled_metric {
    category = "AllMetrics"
  }
}

resource "azurerm_monitor_diagnostic_setting" "pg" {
  name                       = "diag-pg-${var.environment}"
  target_resource_id         = module.postgresql.server_id
  log_analytics_workspace_id = module.monitoring.log_analytics_workspace_id

  enabled_log {
    category = "PostgreSQLLogs"
  }
  enabled_metric {
    category = "AllMetrics"
  }
}

resource "azurerm_monitor_diagnostic_setting" "storage" {
  name                       = "diag-storage-${var.environment}"
  target_resource_id         = "${module.storage.storage_account_id}/blobServices/default"
  log_analytics_workspace_id = module.monitoring.log_analytics_workspace_id

  enabled_log {
    category = "StorageRead"
  }
  enabled_log {
    category = "StorageWrite"
  }
  enabled_log {
    category = "StorageDelete"
  }
  enabled_metric {
    category = "AllMetrics"
  }
}

# --- Alert Rules (cross-module resource references) ---

# A-001: ERROR log detection (log-based alert)
resource "azurerm_monitor_scheduled_query_rules_alert_v2" "error_log" {
  name                 = "alert-error-log-${var.environment}"
  resource_group_name  = azurerm_resource_group.main.name
  location             = var.location
  scopes               = [module.monitoring.log_analytics_workspace_id]
  severity             = 2
  window_duration      = "PT5M"
  evaluation_frequency = "PT5M"

  criteria {
    query = <<-KQL
      ContainerAppConsoleLogs
      | where ContainerName == "wms-backend"
      | where Log contains "\"level\":\"ERROR\""
    KQL

    time_aggregation_method = "Count"
    operator                = "GreaterThanOrEqual"
    threshold               = 1
  }

  action {
    action_groups = [module.monitoring.action_group_id]
  }

  tags = local.common_tags
}

# A-002: API response latency (log-based alert)
resource "azurerm_monitor_scheduled_query_rules_alert_v2" "api_latency" {
  name                 = "alert-api-latency-${var.environment}"
  resource_group_name  = azurerm_resource_group.main.name
  location             = var.location
  scopes               = [module.monitoring.log_analytics_workspace_id]
  severity             = 2
  window_duration      = "PT5M"
  evaluation_frequency = "PT5M"

  criteria {
    query = <<-KQL
      AppRequests
      | where DurationMs > 3000
    KQL

    time_aggregation_method = "Count"
    operator                = "GreaterThanOrEqual"
    threshold               = 1
  }

  action {
    action_groups = [module.monitoring.action_group_id]
  }

  tags = local.common_tags
}

# A-003: DB connection failure (log-based alert)
resource "azurerm_monitor_scheduled_query_rules_alert_v2" "db_connection_failure" {
  name                 = "alert-db-connection-${var.environment}"
  resource_group_name  = azurerm_resource_group.main.name
  location             = var.location
  scopes               = [module.monitoring.log_analytics_workspace_id]
  severity             = 1
  window_duration      = "PT5M"
  evaluation_frequency = "PT5M"

  criteria {
    query = <<-KQL
      ContainerAppConsoleLogs
      | where ContainerName == "wms-backend"
      | where Log contains "Connection refused" or Log contains "Unable to acquire JDBC"
    KQL

    time_aggregation_method = "Count"
    operator                = "GreaterThanOrEqual"
    threshold               = 1
  }

  action {
    action_groups = [module.monitoring.action_group_id]
  }

  tags = local.common_tags
}

# A-004: Login failure spike (log-based alert)
resource "azurerm_monitor_scheduled_query_rules_alert_v2" "login_failure" {
  name                 = "alert-login-failure-${var.environment}"
  resource_group_name  = azurerm_resource_group.main.name
  location             = var.location
  scopes               = [module.monitoring.log_analytics_workspace_id]
  severity             = 2
  window_duration      = "PT5M"
  evaluation_frequency = "PT5M"

  criteria {
    query = <<-KQL
      ContainerAppConsoleLogs
      | where ContainerName == "wms-backend"
      | where Log contains "Authentication failed" or Log contains "LOGIN_FAILED"
    KQL

    time_aggregation_method = "Count"
    operator                = "GreaterThanOrEqual"
    threshold               = 10
  }

  action {
    action_groups = [module.monitoring.action_group_id]
  }

  tags = local.common_tags
}

# A-005: Container App restart (metric alert)
resource "azurerm_monitor_metric_alert" "ca_restart" {
  name                = "alert-ca-restart-${var.environment}"
  resource_group_name = azurerm_resource_group.main.name
  scopes              = [module.container_apps.container_app_id]
  severity            = 1
  frequency           = "PT5M"
  window_size         = "PT15M"

  criteria {
    metric_namespace = "Microsoft.App/containerApps"
    metric_name      = "RestartCount"
    aggregation      = "Total"
    operator         = "GreaterThanOrEqual"
    threshold        = 3
  }

  action {
    action_group_id = module.monitoring.action_group_id
  }

  tags = local.common_tags
}

# A-006: CPU usage high (metric alert on CAE)
resource "azurerm_monitor_metric_alert" "ca_cpu" {
  name                = "alert-ca-cpu-${var.environment}"
  resource_group_name = azurerm_resource_group.main.name
  scopes              = [module.container_apps.container_app_id]
  severity            = 2
  frequency           = "PT5M"
  window_size         = "PT5M"

  criteria {
    metric_namespace = "Microsoft.App/containerApps"
    metric_name      = "UsageNanoCores"
    aggregation      = "Average"
    operator         = "GreaterThan"
    threshold        = 400000000 # 80% of 0.5 vCPU = 0.4 vCPU in nanocores
  }

  action {
    action_group_id = module.monitoring.action_group_id
  }

  tags = local.common_tags
}

# A-007: Memory usage high (metric alert)
resource "azurerm_monitor_metric_alert" "ca_memory" {
  name                = "alert-ca-memory-${var.environment}"
  resource_group_name = azurerm_resource_group.main.name
  scopes              = [module.container_apps.container_app_id]
  severity            = 2
  frequency           = "PT5M"
  window_size         = "PT5M"

  criteria {
    metric_namespace = "Microsoft.App/containerApps"
    metric_name      = "WorkingSetBytes"
    aggregation      = "Average"
    operator         = "GreaterThan"
    threshold        = 858993459 # 80% of 1Gi
  }

  action {
    action_group_id = module.monitoring.action_group_id
  }

  tags = local.common_tags
}

# A-008: PostgreSQL CPU high (metric alert)
resource "azurerm_monitor_metric_alert" "pg_cpu" {
  name                = "alert-pg-cpu-${var.environment}"
  resource_group_name = azurerm_resource_group.main.name
  scopes              = [module.postgresql.server_id]
  severity            = 2
  frequency           = "PT5M"
  window_size         = "PT5M"

  criteria {
    metric_namespace = "Microsoft.DBforPostgreSQL/flexibleServers"
    metric_name      = "cpu_percent"
    aggregation      = "Average"
    operator         = "GreaterThan"
    threshold        = 80
  }

  action {
    action_group_id = module.monitoring.action_group_id
  }

  tags = local.common_tags
}

# A-009: PostgreSQL storage usage (metric alert)
resource "azurerm_monitor_metric_alert" "pg_storage" {
  name                = "alert-pg-storage-${var.environment}"
  resource_group_name = azurerm_resource_group.main.name
  scopes              = [module.postgresql.server_id]
  severity            = 2
  frequency           = "PT15M"
  window_size         = "PT15M"

  criteria {
    metric_namespace = "Microsoft.DBforPostgreSQL/flexibleServers"
    metric_name      = "storage_percent"
    aggregation      = "Average"
    operator         = "GreaterThan"
    threshold        = 80
  }

  action {
    action_group_id = module.monitoring.action_group_id
  }

  tags = local.common_tags
}

# A-010: PostgreSQL connection exhaustion (metric alert)
resource "azurerm_monitor_metric_alert" "pg_connections" {
  name                = "alert-pg-connections-${var.environment}"
  resource_group_name = azurerm_resource_group.main.name
  scopes              = [module.postgresql.server_id]
  severity            = 2
  frequency           = "PT5M"
  window_size         = "PT5M"

  criteria {
    metric_namespace = "Microsoft.DBforPostgreSQL/flexibleServers"
    metric_name      = "active_connections"
    aggregation      = "Average"
    operator         = "GreaterThan"
    threshold        = 40
  }

  action {
    action_group_id = module.monitoring.action_group_id
  }

  tags = local.common_tags
}
