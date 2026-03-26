resource "azurerm_resource_group" "acr" {
  name     = "rg-wms-acr"
  location = var.location
  tags     = var.common_tags
}

resource "azurerm_container_registry" "main" {
  name                = "acrwms"
  resource_group_name = azurerm_resource_group.acr.name
  location            = azurerm_resource_group.acr.location
  sku                 = "Basic"
  admin_enabled       = false

  tags = var.common_tags

  lifecycle {
    prevent_destroy = true
  }
}
