resource "azurerm_postgresql_flexible_server" "main" {
  name                         = "pg-wms-${var.environment}"
  resource_group_name          = var.resource_group_name
  location                     = var.location
  version                      = "16"
  sku_name                     = var.db_sku_name
  storage_mb                   = var.db_storage_mb
  backup_retention_days        = 7
  geo_redundant_backup_enabled = var.db_geo_backup
  auto_grow_enabled            = var.db_auto_grow
  administrator_login          = "wmsadmin"
  administrator_password       = var.db_admin_password
  zone                         = "1"

  public_network_access_enabled = false
  delegated_subnet_id           = var.snet_pg_id
  private_dns_zone_id           = var.private_dns_zone_id

  tags = var.common_tags
}

resource "azurerm_postgresql_flexible_server_database" "wms" {
  name      = "wms"
  server_id = azurerm_postgresql_flexible_server.main.id
  charset   = "UTF8"
  collation = "ja_JP.utf8"
}

resource "azurerm_postgresql_flexible_server_configuration" "timezone" {
  server_id = azurerm_postgresql_flexible_server.main.id
  name      = "timezone"
  value     = "Asia/Tokyo"
}

resource "azurerm_postgresql_flexible_server_configuration" "statement_timeout" {
  server_id = azurerm_postgresql_flexible_server.main.id
  name      = "statement_timeout"
  value     = "30000"
}

resource "azurerm_postgresql_flexible_server_configuration" "max_connections" {
  server_id = azurerm_postgresql_flexible_server.main.id
  name      = "max_connections"
  value     = "50"
}

resource "azurerm_postgresql_flexible_server_configuration" "lock_timeout" {
  server_id = azurerm_postgresql_flexible_server.main.id
  name      = "lock_timeout"
  value     = "10000"
}

resource "azurerm_postgresql_flexible_server_configuration" "log_min_duration_statement" {
  server_id = azurerm_postgresql_flexible_server.main.id
  name      = "log_min_duration_statement"
  value     = "1000"
}

resource "azurerm_postgresql_flexible_server_configuration" "log_statement" {
  server_id = azurerm_postgresql_flexible_server.main.id
  name      = "log_statement"
  value     = "ddl"
}
