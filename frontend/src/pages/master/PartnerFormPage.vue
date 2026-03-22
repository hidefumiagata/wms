<template>
  <div class="wms-page">
    <el-card v-loading="initialLoading">
      <template #header>
        <div class="form-header">
          <el-button :icon="ArrowLeft" text @click="handleCancel">
            {{ t('common.back') }}
          </el-button>
          <span class="form-title">
            {{ isEdit ? t('master.partner.edit') : t('master.partner.create') }}
          </span>
        </div>
      </template>

      <el-form
        label-width="160px"
        style="max-width: 640px"
        @submit.prevent="handleSubmit"
      >
        <!-- 基本情報 -->
        <el-divider content-position="left">{{ t('master.partner.sectionBasicInfo') }}</el-divider>

        <!-- 取引先コード -->
        <el-form-item
          :label="t('master.partner.partnerCode')"
          :error="errors.partnerCode"
        >
          <el-input
            v-if="!isEdit"
            v-model="partnerCode"
            v-bind="partnerCodeAttrs"
            :placeholder="t('master.partner.codePlaceholder')"
            maxlength="50"
            show-word-limit
            @blur="checkCodeExists"
          />
          <span v-else class="readonly-value">{{ partnerCode }}</span>
        </el-form-item>

        <!-- 取引先名 -->
        <el-form-item
          :label="t('master.partner.partnerName')"
          :error="errors.partnerName"
        >
          <el-input
            v-model="partnerName"
            v-bind="partnerNameAttrs"
            :placeholder="t('master.partner.partnerName')"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>

        <!-- 取引先名カナ -->
        <el-form-item
          :label="t('master.partner.partnerNameKana')"
          :error="errors.partnerNameKana"
        >
          <el-input
            v-model="partnerNameKana"
            v-bind="partnerNameKanaAttrs"
            :placeholder="t('master.partner.kanaPlaceholder')"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>

        <!-- 種別 -->
        <el-form-item
          :label="t('master.partner.partnerType')"
          :error="errors.partnerType"
        >
          <el-radio-group v-model="partnerType" v-bind="partnerTypeAttrs">
            <el-radio value="SUPPLIER">{{ t('master.partner.typeSupplier') }}</el-radio>
            <el-radio value="CUSTOMER">{{ t('master.partner.typeCustomer') }}</el-radio>
            <el-radio value="BOTH">{{ t('master.partner.typeBoth') }}</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 連絡先情報 -->
        <el-divider content-position="left">{{ t('master.partner.sectionContactInfo') }}</el-divider>

        <!-- 住所 -->
        <el-form-item
          :label="t('master.partner.address')"
          :error="errors.address"
        >
          <el-input
            v-model="address"
            v-bind="addressAttrs"
            :placeholder="t('master.partner.addressPlaceholder')"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>

        <!-- 電話番号 -->
        <el-form-item
          :label="t('master.partner.phone')"
          :error="errors.phone"
        >
          <el-input
            v-model="phone"
            v-bind="phoneAttrs"
            :placeholder="t('master.partner.phonePlaceholder')"
            maxlength="20"
            show-word-limit
          />
        </el-form-item>

        <!-- 担当者名 -->
        <el-form-item
          :label="t('master.partner.contactPerson')"
          :error="errors.contactPerson"
        >
          <el-input
            v-model="contactPerson"
            v-bind="contactPersonAttrs"
            :placeholder="t('master.partner.contactPersonPlaceholder')"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>

        <!-- メールアドレス -->
        <el-form-item
          :label="t('master.partner.email')"
          :error="errors.email"
        >
          <el-input
            v-model="email"
            v-bind="emailAttrs"
            :placeholder="t('master.partner.emailPlaceholder')"
            maxlength="254"
            show-word-limit
          />
        </el-form-item>

        <!-- ボタン -->
        <el-form-item>
          <el-button @click="handleCancel">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" native-type="submit" :loading="loading">
            {{ isEdit ? t('common.save') : t('master.partner.register') }}
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
import { usePartnerForm } from '@/composables/master/usePartnerForm'

const { t } = useI18n()

const {
  partnerCode, partnerCodeAttrs,
  partnerName, partnerNameAttrs,
  partnerNameKana, partnerNameKanaAttrs,
  partnerType, partnerTypeAttrs,
  address, addressAttrs,
  phone, phoneAttrs,
  contactPerson, contactPersonAttrs,
  email, emailAttrs,
  errors,
  loading,
  initialLoading,
  isEdit,
  fetchPartner,
  handleSubmit,
  handleCancel,
  checkCodeExists,
} = usePartnerForm()

onMounted(() => {
  if (isEdit.value) fetchPartner()
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
