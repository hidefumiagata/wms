resource "azurerm_communication_service" "main" {
  name                = "acs-wms-${var.environment}"
  resource_group_name = var.resource_group_name
  data_location       = "Japan"

  tags = var.common_tags
}

resource "azurerm_email_communication_service" "main" {
  name                = "ecs-wms-${var.environment}"
  resource_group_name = var.resource_group_name
  data_location       = "Japan"

  tags = var.common_tags
}
