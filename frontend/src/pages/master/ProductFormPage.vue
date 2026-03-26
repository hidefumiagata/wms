<template>
  <div class="wms-page">
    <el-card v-loading="initialLoading">
      <template #header>
        <div class="form-header">
          <el-button :icon="ArrowLeft" text @click="handleCancel">
            {{ t('common.back') }}
          </el-button>
          <span class="form-title">
            {{ isEdit ? t('master.product.edit') : t('master.product.create') }}
          </span>
        </div>
      </template>

      <el-form label-width="180px" style="max-width: 720px" @submit.prevent="handleSubmit">
        <!-- 基本情報 -->
        <el-divider content-position="left">{{ t('master.product.sectionBasicInfo') }}</el-divider>

        <!-- 商品コード -->
        <el-form-item :label="t('master.product.productCode')" :error="errors.productCode">
          <el-input
            v-if="!isEdit"
            v-model="productCode"
            v-bind="productCodeAttrs"
            :placeholder="t('master.product.codePlaceholder')"
            maxlength="20"
            show-word-limit
            @blur="checkCodeExists"
          />
          <span v-else class="readonly-value">{{ productCode }}</span>
        </el-form-item>

        <!-- 商品名 -->
        <el-form-item :label="t('master.product.productName')" :error="errors.productName">
          <el-input
            v-model="productName"
            v-bind="productNameAttrs"
            :placeholder="t('master.product.productName')"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>

        <!-- 商品名カナ -->
        <el-form-item :label="t('master.product.productNameKana')" :error="errors.productNameKana">
          <el-input
            v-model="productNameKana"
            v-bind="productNameKanaAttrs"
            :placeholder="t('master.product.kanaPlaceholder')"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>

        <!-- バーコード -->
        <el-form-item :label="t('master.product.barcode')" :error="errors.barcode">
          <el-input
            v-model="barcode"
            v-bind="barcodeAttrs"
            :placeholder="t('master.product.barcodePlaceholder')"
            maxlength="20"
            show-word-limit
          />
        </el-form-item>

        <!-- 保管・管理設定 -->
        <el-divider content-position="left">
          {{ t('master.product.sectionStorageManagement') }}
        </el-divider>

        <!-- 保管条件 -->
        <el-form-item
          :label="t('master.product.storageCondition')"
          :error="errors.storageCondition"
        >
          <el-radio-group v-model="storageCondition" v-bind="storageConditionAttrs">
            <el-radio :value="StorageCondition.Ambient">
              {{ t('master.product.storageAmbient') }}
            </el-radio>
            <el-radio :value="StorageCondition.Refrigerated">
              {{ t('master.product.storageRefrigerated') }}
            </el-radio>
            <el-radio :value="StorageCondition.Frozen">
              {{ t('master.product.storageFrozen') }}
            </el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- ケース入数 -->
        <el-form-item :label="t('master.product.caseQuantity')" :error="errors.caseQuantity">
          <el-input-number
            v-model="caseQuantity"
            v-bind="caseQuantityAttrs"
            :min="1"
            :max="9999"
            :controls="true"
            style="width: 200px"
          />
        </el-form-item>

        <!-- ボール入数 -->
        <el-form-item :label="t('master.product.ballQuantity')" :error="errors.ballQuantity">
          <el-input-number
            v-model="ballQuantity"
            v-bind="ballQuantityAttrs"
            :min="1"
            :max="9999"
            :controls="true"
            style="width: 200px"
          />
        </el-form-item>

        <!-- フラグ設定 -->
        <el-divider content-position="left">{{ t('master.product.sectionFlags') }}</el-divider>

        <!-- 危険物フラグ -->
        <el-form-item :label="t('master.product.isHazardous')">
          <el-checkbox v-model="isHazardous" v-bind="isHazardousAttrs" />
        </el-form-item>

        <!-- ロット管理フラグ -->
        <el-form-item :label="t('master.product.lotManageFlag')">
          <el-tooltip
            :content="t('master.product.inventoryExistsTooltip')"
            :disabled="!isEdit || !hasInventory"
            placement="top"
          >
            <el-checkbox
              v-model="lotManageFlag"
              v-bind="lotManageFlagAttrs"
              :disabled="isEdit && hasInventory"
            />
          </el-tooltip>
        </el-form-item>

        <!-- 賞味/使用期限管理フラグ -->
        <el-form-item :label="t('master.product.expiryManageFlag')">
          <el-tooltip
            :content="t('master.product.inventoryExistsTooltip')"
            :disabled="!isEdit || !hasInventory"
            placement="top"
          >
            <el-checkbox
              v-model="expiryManageFlag"
              v-bind="expiryManageFlagAttrs"
              :disabled="isEdit && hasInventory"
            />
          </el-tooltip>
        </el-form-item>

        <!-- 出荷禁止フラグ -->
        <el-form-item :label="t('master.product.shipmentStopFlag')">
          <el-checkbox v-model="shipmentStopFlag" v-bind="shipmentStopFlagAttrs" />
        </el-form-item>

        <!-- 有効/無効 -->
        <el-form-item :label="t('master.product.isActive')">
          <el-radio-group v-model="isActive" v-bind="isActiveAttrs">
            <el-radio :value="true">{{ t('common.active') }}</el-radio>
            <el-radio :value="false">{{ t('common.inactive') }}</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- ボタン -->
        <el-form-item>
          <el-button @click="handleCancel">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" native-type="submit" :loading="loading">
            {{ isEdit ? t('common.save') : t('master.product.register') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowLeft } from '@element-plus/icons-vue'
import { useProductForm } from '@/composables/master/useProductForm'
import { StorageCondition } from '@/api/generated/models/storage-condition'

const { t } = useI18n()

const {
  productCode,
  productCodeAttrs,
  productName,
  productNameAttrs,
  productNameKana,
  productNameKanaAttrs,
  barcode,
  barcodeAttrs,
  storageCondition,
  storageConditionAttrs,
  caseQuantity,
  caseQuantityAttrs,
  ballQuantity,
  ballQuantityAttrs,
  isHazardous,
  isHazardousAttrs,
  lotManageFlag,
  lotManageFlagAttrs,
  expiryManageFlag,
  expiryManageFlagAttrs,
  shipmentStopFlag,
  shipmentStopFlagAttrs,
  isActive,
  isActiveAttrs,
  errors,
  loading,
  initialLoading,
  isEdit,
  hasInventory,
  fetchProduct,
  handleSubmit,
  handleCancel,
  checkCodeExists,
} = useProductForm()

onMounted(() => {
  if (isEdit.value) fetchProduct()
})
</script>

<style scoped lang="scss">
.wms-page {
  padding: 20px;
}

.form-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.form-title {
  font-size: 16px;
  font-weight: 600;
}

.readonly-value {
  display: inline-block;
  line-height: 32px;
  color: var(--el-text-color-regular);
  font-weight: 600;
}
</style>
