output "container_app_id" {
  description = "Container App resource ID"
  value       = azurerm_container_app.backend.id
}

output "container_app_environment_id" {
  description = "Container App Environment resource ID"
  value       = azurerm_container_app_environment.main.id
}

output "identity_principal_id" {
  description = "SystemAssigned Managed Identity principal ID"
  value       = azurerm_container_app.backend.identity[0].principal_id
}

output "fqdn" {
  description = "Container App Ingress FQDN"
  value       = azurerm_container_app.backend.ingress[0].fqdn
}
