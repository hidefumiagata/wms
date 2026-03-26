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

# --- Resource Groups ---

resource "azurerm_resource_group" "east" {
  name     = "rg-wms-prd-east"
  location = var.location_primary
  tags     = local.common_tags
}

resource "azurerm_resource_group" "west" {
  name     = "rg-wms-prd-west"
  location = var.location_secondary
  tags     = local.common_tags
}

resource "azurerm_resource_group" "global" {
  name     = "rg-wms-prd-global"
  location = var.location_primary
  tags     = local.common_tags
}

# --- Monitoring (single instance in East) ---

module "monitoring" {
  source              = "../../modules/monitoring"
  environment         = var.environment
  location            = var.location_primary
  resource_group_name = azurerm_resource_group.east.name
  alert_email         = var.alert_email
  common_tags         = local.common_tags
}

# --- VNets ---

module "vnet_east" {
  source              = "../../modules/vnet"
  environment         = "prd-east"
  location            = var.location_primary
  resource_group_name = azurerm_resource_group.east.name
  vnet_cidr           = var.vnet_east_cidr
  snet_ca_cidr        = var.snet_ca_east_cidr
  snet_pg_cidr        = var.snet_pg_east_cidr
  create_pg_subnet    = true
  enable_front_door   = var.enable_front_door
  common_tags         = local.common_tags
}

module "vnet_west" {
  source              = "../../modules/vnet"
  environment         = "prd-west"
  location            = var.location_secondary
  resource_group_name = azurerm_resource_group.west.name
  vnet_cidr           = var.vnet_west_cidr
  snet_ca_cidr        = var.snet_ca_west_cidr
  snet_pg_cidr        = ""
  create_pg_subnet    = false
  enable_front_door   = var.enable_front_door
  remote_pg_cidr      = var.snet_pg_east_cidr # Cross-region DB access
  common_tags         = local.common_tags
}

# --- VNet Peering (East <-> West) ---

resource "azurerm_virtual_network_peering" "east_to_west" {
  name                      = "peer-east-to-west"
  resource_group_name       = azurerm_resource_group.east.name
  virtual_network_name      = "vnet-wms-prd-east"
  remote_virtual_network_id = module.vnet_west.vnet_id
  allow_forwarded_traffic   = true
  allow_gateway_transit     = false

  depends_on = [module.vnet_east, module.vnet_west]
}

resource "azurerm_virtual_network_peering" "west_to_east" {
  name                      = "peer-west-to-east"
  resource_group_name       = azurerm_resource_group.west.name
  virtual_network_name      = "vnet-wms-prd-west"
  remote_virtual_network_id = module.vnet_east.vnet_id
  allow_forwarded_traffic   = true
  allow_gateway_transit     = false

  depends_on = [module.vnet_east, module.vnet_west]
}

# West VNet -> East Private DNS Zone link (for PostgreSQL name resolution)
resource "azurerm_private_dns_zone_virtual_network_link" "postgres_west" {
  name                  = "postgres-vnet-link-west"
  resource_group_name   = azurerm_resource_group.east.name
  private_dns_zone_name = module.vnet_east.private_dns_zone_name
  virtual_network_id    = module.vnet_west.vnet_id
  registration_enabled  = false
}

# --- ACR (single instance) ---

module "acr" {
  source      = "../../modules/acr"
  location    = var.location_primary
  common_tags = local.common_tags
}

# --- Storage (single instance in East, GRS) ---

module "storage" {
  source               = "../../modules/storage"
  environment          = "prdeast"
  location             = var.location_primary
  resource_group_name  = azurerm_resource_group.east.name
  storage_replication  = var.storage_replication
  cors_allowed_origins = ["https://${module.front_door.frontdoor_endpoint_hostname}"]
  common_tags          = local.common_tags
}

# --- Communication Services ---

module "communication_services" {
  source              = "../../modules/communication-services"
  environment         = var.environment
  resource_group_name = azurerm_resource_group.east.name
  common_tags         = local.common_tags
}

# --- PostgreSQL (single instance in East) ---

module "postgresql" {
  source              = "../../modules/postgresql"
  environment         = var.environment
  location            = var.location_primary
  resource_group_name = azurerm_resource_group.east.name
  snet_pg_id          = module.vnet_east.snet_pg_id
  private_dns_zone_id = module.vnet_east.private_dns_zone_id
  db_admin_password   = var.db_admin_password
  db_sku_name         = var.db_sku_name
  db_storage_mb       = var.db_storage_mb
  db_geo_backup       = var.db_geo_backup
  db_auto_grow        = var.db_auto_grow
  common_tags         = local.common_tags
}

# --- Container Apps (East: primary, West: standby) ---

module "container_apps_east" {
  source                         = "../../modules/container-apps"
  environment                    = "prd-east"
  location                       = var.location_primary
  resource_group_name            = azurerm_resource_group.east.name
  snet_ca_id                     = module.vnet_east.snet_ca_id
  log_analytics_workspace_id     = module.monitoring.log_analytics_workspace_id
  acr_login_server               = module.acr.acr_login_server
  acr_id                         = module.acr.acr_id
  image_tag                      = var.image_tag
  min_replicas                   = var.min_replicas_east
  max_replicas                   = var.max_replicas_east
  spring_profile                 = "prd"
  log_level                      = var.log_level
  db_connection_string           = module.postgresql.connection_string
  db_password                    = var.db_admin_password
  jwt_secret                     = var.jwt_secret
  acs_connection_string          = module.communication_services.connection_string
  storage_account_id             = module.storage.storage_account_id
  storage_account_name           = module.storage.storage_account_name
  app_insights_connection_string = module.monitoring.app_insights_connection_string
  frontdoor_id                   = module.front_door.frontdoor_resource_guid
  cors_allowed_origins           = "https://${module.front_door.frontdoor_endpoint_hostname}"
  common_tags                    = local.common_tags
}

module "container_apps_west" {
  source                         = "../../modules/container-apps"
  environment                    = "prd-west"
  location                       = var.location_secondary
  resource_group_name            = azurerm_resource_group.west.name
  snet_ca_id                     = module.vnet_west.snet_ca_id
  log_analytics_workspace_id     = module.monitoring.log_analytics_workspace_id
  acr_login_server               = module.acr.acr_login_server
  acr_id                         = module.acr.acr_id
  image_tag                      = var.image_tag
  min_replicas                   = var.min_replicas_west
  max_replicas                   = var.max_replicas_west
  spring_profile                 = "prd"
  log_level                      = var.log_level
  db_connection_string           = module.postgresql.connection_string
  db_password                    = var.db_admin_password
  jwt_secret                     = var.jwt_secret
  acs_connection_string          = module.communication_services.connection_string
  storage_account_id             = module.storage.storage_account_id
  storage_account_name           = module.storage.storage_account_name
  app_insights_connection_string = module.monitoring.app_insights_connection_string
  frontdoor_id                   = module.front_door.frontdoor_resource_guid
  cors_allowed_origins           = "https://${module.front_door.frontdoor_endpoint_hostname}"
  common_tags                    = local.common_tags

  depends_on = [azurerm_virtual_network_peering.west_to_east]
}

# --- Front Door ---

module "front_door" {
  source                  = "../../modules/front-door"
  resource_group_name     = azurerm_resource_group.global.name
  backend_fqdn_east       = module.container_apps_east.fqdn
  backend_fqdn_west       = module.container_apps_west.fqdn
  static_website_hostname = module.storage.primary_web_host
  common_tags             = local.common_tags
}

# --- Diagnostic Settings ---

resource "azurerm_monitor_diagnostic_setting" "cae_east" {
  name                       = "diag-cae-prd-east"
  target_resource_id         = module.container_apps_east.container_app_environment_id
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

resource "azurerm_monitor_diagnostic_setting" "cae_west" {
  name                       = "diag-cae-prd-west"
  target_resource_id         = module.container_apps_west.container_app_environment_id
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
  name                       = "diag-pg-prd"
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
  name                       = "diag-storage-prd"
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

resource "azurerm_monitor_diagnostic_setting" "fd" {
  name                       = "diag-fd-prd"
  target_resource_id         = module.front_door.frontdoor_id
  log_analytics_workspace_id = module.monitoring.log_analytics_workspace_id

  enabled_log {
    category = "FrontDoorAccessLog"
  }
  enabled_log {
    category = "FrontDoorHealthProbeLog"
  }
  enabled_log {
    category = "FrontDoorWebApplicationFirewallLog"
  }
  enabled_metric {
    category = "AllMetrics"
  }
}

# --- Alert Rules ---

# A-001: ERROR log detection
resource "azurerm_monitor_scheduled_query_rules_alert_v2" "error_log" {
  name                 = "alert-error-log-prd"
  resource_group_name  = azurerm_resource_group.east.name
  location             = var.location_primary
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

# A-002: API response latency
resource "azurerm_monitor_scheduled_query_rules_alert_v2" "api_latency" {
  name                 = "alert-api-latency-prd"
  resource_group_name  = azurerm_resource_group.east.name
  location             = var.location_primary
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

# A-003: DB connection failure
resource "azurerm_monitor_scheduled_query_rules_alert_v2" "db_connection_failure" {
  name                 = "alert-db-connection-prd"
  resource_group_name  = azurerm_resource_group.east.name
  location             = var.location_primary
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

# A-004: Login failure spike
resource "azurerm_monitor_scheduled_query_rules_alert_v2" "login_failure" {
  name                 = "alert-login-failure-prd"
  resource_group_name  = azurerm_resource_group.east.name
  location             = var.location_primary
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

# A-005: Container App restart (East)
resource "azurerm_monitor_metric_alert" "ca_restart_east" {
  name                = "alert-ca-restart-prd-east"
  resource_group_name = azurerm_resource_group.east.name
  scopes              = [module.container_apps_east.container_app_id]
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

# A-006: CPU usage high (East)
resource "azurerm_monitor_metric_alert" "ca_cpu_east" {
  name                = "alert-ca-cpu-prd-east"
  resource_group_name = azurerm_resource_group.east.name
  scopes              = [module.container_apps_east.container_app_id]
  severity            = 2
  frequency           = "PT5M"
  window_size         = "PT5M"

  criteria {
    metric_namespace = "Microsoft.App/containerApps"
    metric_name      = "UsageNanoCores"
    aggregation      = "Average"
    operator         = "GreaterThan"
    threshold        = 400000000
  }

  action {
    action_group_id = module.monitoring.action_group_id
  }

  tags = local.common_tags
}

# A-007: Memory usage high (East)
resource "azurerm_monitor_metric_alert" "ca_memory_east" {
  name                = "alert-ca-memory-prd-east"
  resource_group_name = azurerm_resource_group.east.name
  scopes              = [module.container_apps_east.container_app_id]
  severity            = 2
  frequency           = "PT5M"
  window_size         = "PT5M"

  criteria {
    metric_namespace = "Microsoft.App/containerApps"
    metric_name      = "WorkingSetBytes"
    aggregation      = "Average"
    operator         = "GreaterThan"
    threshold        = 858993459
  }

  action {
    action_group_id = module.monitoring.action_group_id
  }

  tags = local.common_tags
}

# A-008: PostgreSQL CPU high
resource "azurerm_monitor_metric_alert" "pg_cpu" {
  name                = "alert-pg-cpu-prd"
  resource_group_name = azurerm_resource_group.east.name
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

# A-009: PostgreSQL storage usage
resource "azurerm_monitor_metric_alert" "pg_storage" {
  name                = "alert-pg-storage-prd"
  resource_group_name = azurerm_resource_group.east.name
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

# A-010: PostgreSQL connection exhaustion
resource "azurerm_monitor_metric_alert" "pg_connections" {
  name                = "alert-pg-connections-prd"
  resource_group_name = azurerm_resource_group.east.name
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

# A-011: Front Door 5xx error rate
resource "azurerm_monitor_metric_alert" "fd_5xx" {
  name                = "alert-fd-5xx-prd"
  resource_group_name = azurerm_resource_group.global.name
  scopes              = [module.front_door.frontdoor_id]
  severity            = 1
  frequency           = "PT5M"
  window_size         = "PT5M"

  criteria {
    metric_namespace = "Microsoft.Cdn/profiles"
    metric_name      = "Percentage5XXs"
    aggregation      = "Average"
    operator         = "GreaterThan"
    threshold        = 5
  }

  action {
    action_group_id = module.monitoring.action_group_id
  }

  tags = local.common_tags
}

# A-012: Front Door latency
resource "azurerm_monitor_metric_alert" "fd_latency" {
  name                = "alert-fd-latency-prd"
  resource_group_name = azurerm_resource_group.global.name
  scopes              = [module.front_door.frontdoor_id]
  severity            = 2
  frequency           = "PT5M"
  window_size         = "PT5M"

  criteria {
    metric_namespace = "Microsoft.Cdn/profiles"
    metric_name      = "TotalLatency"
    aggregation      = "Average"
    operator         = "GreaterThan"
    threshold        = 5000
  }

  action {
    action_group_id = module.monitoring.action_group_id
  }

  tags = local.common_tags
}
