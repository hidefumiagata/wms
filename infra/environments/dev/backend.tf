terraform {
  backend "azurerm" {
    subscription_id      = "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" # wms-terraform
    resource_group_name  = "rg-wms-terraform"
    storage_account_name = "stwmsterraform"
    container_name       = "tfstate"
    key                  = "dev/terraform.tfstate"
  }
}
