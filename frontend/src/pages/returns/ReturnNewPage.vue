<template>
  <div class="wms-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ t('returns.title') }}</span>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="160px">
        <!-- 基本情報 -->
        <el-divider content-position="left">{{ t('returns.basicInfo') }}</el-divider>

        <el-form-item :label="t('returns.returnType')" prop="returnType">
          <el-select
            v-model="form.returnType"
            style="width: 200px"
            :placeholder="t('returns.returnType')"
            @change="onReturnTypeChange"
          >
            <el-option :label="t('returns.returnTypeInbound')" value="INBOUND" />
            <el-option :label="t('returns.returnTypeInventory')" value="INVENTORY" />
            <el-option :label="t('returns.returnTypeOutbound')" value="OUTBOUND" />
          </el-select>
        </el-form-item>

        <el-form-item :label="t('returns.productCode')" prop="productCode">
          <el-input
            v-model="form.productCode"
            style="width: 280px"
            :placeholder="t('returns.productCode')"
            @keyup.enter="searchProduct"
          />
          <el-button
            style="margin-left: 8px"
            :loading="productSearchLoading"
            @click="searchProduct"
          >
            {{ t('common.search') }}
          </el-button>
        </el-form-item>

        <el-form-item v-if="selectedProduct" :label="t('returns.productName')">
          <span>{{ selectedProduct.productName }}</span>
        </el-form-item>

        <el-form-item :label="t('returns.quantity')" prop="quantity">
          <el-input-number v-model="form.quantity" :min="1" style="width: 160px" />
        </el-form-item>

        <el-form-item :label="t('returns.unitType')" prop="unitType">
          <el-select
            v-model="form.unitType"
            style="width: 160px"
            :placeholder="t('returns.unitType')"
            @change="onUnitTypeChange"
          >
            <el-option :label="t('returns.unitTypeCase')" value="CASE" />
            <el-option :label="t('returns.unitTypeBall')" value="BALL" />
            <el-option :label="t('returns.unitTypePiece')" value="PIECE" />
          </el-select>
        </el-form-item>

        <!-- 在庫返品セクション -->
        <template v-if="isInventoryReturn">
          <el-divider content-position="left">{{ t('returns.inventorySection') }}</el-divider>

          <el-form-item :label="t('returns.location')" prop="locationId">
            <el-select
              v-model="form.locationId"
              style="width: 280px"
              filterable
              :placeholder="t('returns.location')"
              :loading="locationLoading"
            >
              <el-option
                v-for="loc in locationOptions"
                :key="loc.locationId"
                :label="loc.locationCode"
                :value="loc.locationId"
              />
            </el-select>
          </el-form-item>

          <template v-if="selectedLocationInventory">
            <el-form-item :label="t('returns.currentQty')">
              <span>{{ formatNumber(selectedLocationInventory.quantity) }}</span>
            </el-form-item>
            <el-form-item :label="t('returns.availableQty')">
              <span :class="{ 'warning-text': hasAllocated }">
                {{ formatNumber(selectedLocationInventory.availableQty) }}
              </span>
            </el-form-item>
          </template>
        </template>

        <!-- 取引先・理由 -->
        <el-divider content-position="left">{{ t('returns.partnerReasonSection') }}</el-divider>

        <el-form-item :label="t('returns.supplier')">
          <el-input
            v-model="form.partnerCode"
            style="width: 280px"
            :placeholder="t('returns.supplierPlaceholder')"
            :disabled="!!selectedSupplier"
            @keyup.enter="searchSupplier"
          />
          <el-button
            v-if="!selectedSupplier"
            style="margin-left: 8px"
            :loading="supplierSearchLoading"
            @click="searchSupplier"
          >
            {{ t('common.search') }}
          </el-button>
          <el-button
            v-if="selectedSupplier"
            style="margin-left: 8px"
            @click="clearSupplier"
          >
            {{ t('common.clear') }}
          </el-button>
        </el-form-item>

        <el-form-item v-if="selectedSupplier" :label="t('returns.supplierName')">
          <span>{{ selectedSupplier.partnerName }}</span>
        </el-form-item>

        <!-- 仕入先検索ダイアログ -->
        <el-dialog
          v-model="supplierDialogVisible"
          :title="t('returns.supplierSearchTitle')"
          width="600px"
          destroy-on-close
        >
          <el-table
            :data="supplierSearchResults"
            style="width: 100%"
            highlight-current-row
            @row-click="selectSupplier"
          >
            <el-table-column prop="partnerCode" :label="t('returns.supplierCode')" width="160" />
            <el-table-column prop="partnerName" :label="t('returns.supplierName')" />
          </el-table>
          <template v-if="supplierSearchTotal > 20" #footer>
            <span class="dialog-footer-hint">
              {{ t('returns.supplierSearchHint', { total: supplierSearchTotal }) }}
            </span>
          </template>
        </el-dialog>

        <el-form-item :label="t('returns.returnReason')" prop="returnReason">
          <el-select
            v-model="form.returnReason"
            style="width: 200px"
            :placeholder="t('returns.returnReason')"
          >
            <el-option :label="t('returns.reasonQualityDefect')" value="QUALITY_DEFECT" />
            <el-option :label="t('returns.reasonExcessQuantity')" value="EXCESS_QUANTITY" />
            <el-option :label="t('returns.reasonWrongDelivery')" value="WRONG_DELIVERY" />
            <el-option :label="t('returns.reasonExpired')" value="EXPIRED" />
            <el-option :label="t('returns.reasonDamaged')" value="DAMAGED" />
            <el-option :label="t('returns.reasonOther')" value="OTHER" />
          </el-select>
        </el-form-item>

        <el-form-item :label="t('returns.returnReasonNote')" prop="returnReasonNote">
          <el-input
            v-model="form.returnReasonNote"
            type="textarea"
            :rows="3"
            :maxlength="500"
            show-word-limit
            style="width: 400px"
          />
        </el-form-item>

        <!-- 条件付き表示 -->
        <template v-if="showRelatedSlip || showLotNumber || showExpiryDate">
          <el-divider content-position="left">{{ t('returns.conditionalSection') }}</el-divider>

          <el-form-item v-if="showRelatedSlip" :label="t('returns.relatedSlipNumber')">
            <el-input v-model="form.relatedSlipNumber" style="width: 280px" />
          </el-form-item>

          <el-form-item v-if="showLotNumber" :label="t('returns.lotNumber')">
            <el-input v-model="form.lotNumber" style="width: 280px" />
          </el-form-item>

          <el-form-item v-if="showExpiryDate" :label="t('returns.expiryDate')">
            <el-date-picker
              v-model="form.expiryDate"
              type="date"
              format="YYYY/MM/DD"
              value-format="YYYY-MM-DD"
              style="width: 200px"
            />
          </el-form-item>
        </template>
      </el-form>

      <!-- ボタン -->
      <div class="form-actions">
        <el-button @click="resetForm">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReturn">
          {{ t('returns.register') }}
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormInstance } from 'element-plus'
import { useReturnForm } from '@/composables/returns/useReturnForm'
import { formatNumber } from '@/utils/inventoryFormatters'

const { t } = useI18n()
const formRef = ref<FormInstance>()

const {
  form,
  rules,
  submitting,
  productSearchLoading,
  locationLoading,
  supplierSearchLoading,
  selectedProduct,
  selectedSupplier,
  locationOptions,
  selectedLocationInventory,
  supplierDialogVisible,
  supplierSearchResults,
  supplierSearchTotal,
  isInventoryReturn,
  showRelatedSlip,
  showLotNumber,
  showExpiryDate,
  hasAllocated,
  onReturnTypeChange,
  searchProduct,
  searchSupplier,
  selectSupplier,
  clearSupplier,
  onUnitTypeChange,
  submitReturn,
  resetForm,
} = useReturnForm(formRef)
</script>

<style scoped lang="scss">
.wms-page {
  padding: 20px;
  max-width: 800px;
}
.card-header {
  font-size: 16px;
  font-weight: 600;
}
.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 24px;
}
.warning-text {
  color: var(--el-color-warning);
  font-weight: 600;
}
</style>
