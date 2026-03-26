resource "azurerm_log_analytics_workspace" "main" {
  name                = "law-wms-${var.environment}"
  resource_group_name = var.resource_group_name
  location            = var.location
  sku                 = "PerGB2018"
  retention_in_days   = 30

  tags = var.common_tags
}

resource "azurerm_application_insights" "main" {
  name                = "ai-wms-${var.environment}"
  location            = var.location
  resource_group_name = var.resource_group_name
  workspace_id        = azurerm_log_analytics_workspace.main.id
  application_type    = "web"
  sampling_percentage = 100
  tags                = var.common_tags
}

resource "azurerm_monitor_action_group" "email" {
  name                = "ag-wms-${var.environment}-email"
  resource_group_name = var.resource_group_name
  short_name          = "wms-email"

  email_receiver {
    name          = "admin"
    email_address = var.alert_email
  }
}
