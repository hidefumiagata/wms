<template>
  <el-dialog
    v-model="visible"
    :title="t('master.productSearch.title')"
    width="600px"
    destroy-on-close
  >
    <el-table
      :data="results"
      style="width: 100%"
      highlight-current-row
      @row-click="(row: ProductOption) => emit('select', row)"
    >
      <el-table-column
        prop="productCode"
        :label="t('master.productSearch.codeColumn')"
        width="160"
      />
      <el-table-column prop="productName" :label="t('master.productSearch.nameColumn')" />
    </el-table>
    <template v-if="total > results.length" #footer>
      <span class="product-search-dialog-hint">
        {{ t('master.productSearch.hint', { total, count: results.length }) }}
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { ProductOption } from '@/types/master'

const visible = defineModel<boolean>('visible', { required: true })

defineProps<{
  results: ProductOption[]
  total: number
}>()

const emit = defineEmits<{
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
