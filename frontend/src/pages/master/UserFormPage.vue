<template>
  <div class="wms-page">
    <el-card v-loading="initialLoading">
      <template #header>
        <div class="form-header">
          <el-button :icon="ArrowLeft" text @click="handleCancel">
            {{ t('common.back') }}
          </el-button>
          <span class="form-title">
            {{ isEdit ? t('master.user.edit') : t('master.user.create') }}
          </span>
        </div>
      </template>

      <!-- アカウントロック中アラート -->
      <el-alert
        v-if="isEdit && locked"
        :title="t('master.user.lockedAlert')"
        type="error"
        show-icon
        :closable="false"
        style="margin-bottom: 20px"
      >
        <el-button type="warning" size="small" @click="handleUnlock">
          {{ t('master.user.unlock') }}
        </el-button>
      </el-alert>

      <el-form
        label-width="200px"
        style="max-width: 680px"
        @submit.prevent="handleSubmit"
      >
        <!-- 基本情報セクション -->
        <el-divider content-position="left">{{ t('master.user.sectionBasicInfo') }}</el-divider>

        <!-- ユーザーコード -->
        <el-form-item :label="t('master.user.userCode')" :error="errors.userCode">
          <el-input
            v-if="!isEdit"
            v-model="userCode"
            v-bind="userCodeAttrs"
            :placeholder="t('master.user.codePlaceholder')"
            maxlength="50"
            show-word-limit
            @blur="checkCodeExists"
          />
          <span v-else class="readonly-value">{{ userCode }}</span>
        </el-form-item>

        <!-- 氏名 -->
        <el-form-item :label="t('master.user.fullName')" :error="errors.fullName">
          <el-input
            v-model="fullName"
            v-bind="fullNameAttrs"
            :placeholder="t('master.user.namePlaceholder')"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>

        <!-- メールアドレス -->
        <el-form-item :label="t('master.user.email')" :error="errors.email">
          <el-input
            v-model="email"
            v-bind="emailAttrs"
            :placeholder="t('master.user.emailPlaceholder')"
            maxlength="200"
          />
        </el-form-item>

        <!-- ロール -->
        <el-form-item :label="t('master.user.role')" :error="errors.role">
          <el-select
            v-model="role"
            v-bind="roleAttrs"
            :placeholder="t('master.user.selectRole')"
            :disabled="isSelf"
            style="width: 100%"
          >
            <el-option v-for="r in roleOptions" :key="r" :label="r" :value="r" />
          </el-select>
          <div v-if="isSelf" class="form-hint">{{ t('master.user.selfRoleHint') }}</div>
        </el-form-item>

        <!-- 有効/無効（編集時のみ） -->
        <el-form-item v-if="isEdit" :label="t('master.user.isActive')">
          <el-radio-group v-model="isActive" v-bind="isActiveAttrs" :disabled="isSelf">
            <el-radio :value="true">{{ t('common.active') }}</el-radio>
            <el-radio :value="false">{{ t('common.inactive') }}</el-radio>
          </el-radio-group>
          <div v-if="isSelf" class="form-hint">{{ t('master.user.selfDeactivateHint') }}</div>
        </el-form-item>

        <!-- 初期パスワード（新規登録時のみ） -->
        <template v-if="!isEdit">
          <el-divider content-position="left">{{ t('master.user.sectionPassword') }}</el-divider>

          <el-form-item :label="t('master.user.initialPassword')" :error="errors.initialPassword">
            <el-input
              v-model="initialPassword"
              v-bind="initialPasswordAttrs"
              :type="showPassword ? 'text' : 'password'"
              :placeholder="t('master.user.passwordPlaceholder')"
              maxlength="128"
            >
              <template #suffix>
                <el-button
                  :icon="showPassword ? Hide : View"
                  text
                  size="small"
                  @click="showPassword = !showPassword"
                />
              </template>
            </el-input>
          </el-form-item>

          <el-form-item
            :label="t('master.user.confirmPassword')"
            :error="errors.confirmPassword"
          >
            <el-input
              v-model="confirmPassword"
              v-bind="confirmPasswordAttrs"
              :type="showPassword ? 'text' : 'password'"
              :placeholder="t('master.user.confirmPasswordPlaceholder')"
              maxlength="128"
            />
          </el-form-item>

          <el-form-item>
            <div class="form-hint">{{ t('master.user.passwordPolicyHint') }}</div>
          </el-form-item>
        </template>

        <!-- 登録情報（編集時のみ） -->
        <template v-if="isEdit">
          <el-divider content-position="left">{{ t('master.user.sectionRegistrationInfo') }}</el-divider>

          <el-form-item :label="t('master.user.createdAt')">
            <span class="readonly-value">{{ createdAt }}</span>
          </el-form-item>
          <el-form-item :label="t('master.user.updatedAt')">
            <span class="readonly-value">{{ updatedAt }}</span>
          </el-form-item>
          <el-form-item :label="t('master.user.passwordChangeRequired')">
            <span class="readonly-value">
              {{ passwordChangeRequired ? t('master.user.passwordChangeNeeded') : t('master.user.passwordChangeCompleted') }}
            </span>
          </el-form-item>
        </template>

        <!-- ボタン -->
        <el-form-item>
          <el-button @click="handleCancel">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" native-type="submit" :loading="loading">
            {{ isEdit ? t('common.save') : t('master.user.register') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowLeft, View, Hide } from '@element-plus/icons-vue'
import { useUserForm } from '@/composables/master/useUserForm'
import { UserRole } from '@/api/generated/models/user-role'

const { t } = useI18n()
const roleOptions = Object.values(UserRole)

const {
  userCode,
  userCodeAttrs,
  fullName,
  fullNameAttrs,
  email,
  emailAttrs,
  role,
  roleAttrs,
  isActive,
  isActiveAttrs,
  initialPassword,
  initialPasswordAttrs,
  confirmPassword,
  confirmPasswordAttrs,
  errors,
  loading,
  initialLoading,
  isEdit,
  isSelf,
  locked,
  passwordChangeRequired,
  createdAt,
  updatedAt,
  showPassword,
  fetchUser,
  handleSubmit,
  handleCancel,
  handleUnlock,
  checkCodeExists,
} = useUserForm()

onMounted(() => {
  if (isEdit.value) fetchUser()
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

.form-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-top: 4px;
}
</style>
