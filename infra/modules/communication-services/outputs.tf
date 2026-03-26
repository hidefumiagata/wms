output "connection_string" {
  description = "Communication Services connection string"
  value       = azurerm_communication_service.main.primary_connection_string
  sensitive   = true
}
