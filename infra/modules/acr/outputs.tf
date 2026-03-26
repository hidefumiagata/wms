output "acr_id" {
  description = "ACR resource ID"
  value       = azurerm_container_registry.main.id
}

output "acr_login_server" {
  description = "ACR login server (acrwms.azurecr.io)"
  value       = azurerm_container_registry.main.login_server
}
