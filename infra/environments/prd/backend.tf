terraform {
  backend "azurerm" {
    # subscription_id is injected via: terraform init -backend-config="subscription_id=$TF_STATE_SUBSCRIPTION_ID"
    resource_group_name  = "rg-wms-terraform"
    storage_account_name = "stwmsterraform"
    container_name       = "tfstate"
    key                  = "prd/terraform.tfstate"
  }
}
