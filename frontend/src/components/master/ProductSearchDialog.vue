<template>
  <el-dialog
    :model-value="visible"
    :title="t('returns.productSearchTitle')"
    width="600px"
    destroy-on-close
    @update:model-value="(v: boolean) => emit('update:visible', v)"
  >
    <el-table
      :data="results"
      style="width: 100%"
      highlight-current-row
      @row-click="(row: ProductOption) => emit('select', row)"
    >
      <el-table-column
        prop="productCode"
        :label="t('returns.productCodeColumn')"
        width="160"
      />
      <el-table-column prop="productName" :label="t('returns.productNameColumn')" />
    </el-table>
    <template v-if="total > results.length" #footer>
      <span class="product-search-dialog-hint">
        {{ t('returns.productSearchHint', { total, count: results.length }) }}
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { ProductOption } from '@/types/master'

defineProps<{
  visible: boolean
  results: ProductOption[]
  total: number
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'select', product: ProductOption): void
}>()

const { t } = useI18n()
</script>

<style scoped lang="scss">
.product-search-dialog-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
