/* eslint-disable vue/one-component-per-file */
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import ProductSearchDialog from '@/components/master/ProductSearchDialog.vue'
import type { ProductOption } from '@/types/master'

// --- element-plus コンポーネントの最小スタブ ---
// element-plus は setup.ts 経由で ElMessage/ElMessageBox のみモックされているため、
// ElDialog / ElTable / ElTableColumn は Vue から未解決となる。テスト用スタブで代替する。
const ElDialogStub = defineComponent({
  name: 'ElDialog',
  props: {
    modelValue: { type: Boolean, default: false },
    title: { type: String, default: '' },
  },
  emits: ['update:modelValue'],
  setup(props, { slots, emit }) {
    return () => {
      if (!props.modelValue) return null
      return h('div', { class: 'el-dialog-stub', 'data-title': props.title }, [
        h('div', { class: 'el-dialog-body' }, slots.default?.()),
        h(
          'div',
          { class: 'el-dialog-footer' },
          slots.footer ? slots.footer() : [],
        ),
        h(
          'button',
          {
            class: 'el-dialog-close',
            onClick: () => emit('update:modelValue', false),
          },
          'close',
        ),
      ])
    }
  },
})

const ElTableStub = defineComponent({
  name: 'ElTable',
  props: {
    data: { type: Array, default: () => [] },
  },
  emits: ['row-click'],
  setup(props, { slots, emit }) {
    return () =>
      h(
        'table',
        { class: 'el-table-stub' },
        (props.data as unknown[]).map((row, idx) =>
          h(
            'tr',
            {
              key: idx,
              class: 'el-table-row',
              onClick: () => emit('row-click', row),
            },
            slots.default?.(),
          ),
        ),
      )
  },
})

const ElTableColumnStub = defineComponent({
  name: 'ElTableColumn',
  props: {
    prop: { type: String, default: '' },
    label: { type: String, default: '' },
    width: { type: [String, Number], default: '' },
  },
  setup(props) {
    return () => h('td', { class: 'el-table-column-stub', 'data-prop': props.prop })
  },
})

const globalStubs = {
  ElDialog: ElDialogStub,
  ElTable: ElTableStub,
  ElTableColumn: ElTableColumnStub,
}

function makeResults(n: number): ProductOption[] {
  return Array.from({ length: n }, (_, i) => ({
    id: i + 1,
    productCode: `P${String(i + 1).padStart(3, '0')}`,
    productName: `Product ${i + 1}`,
    lotManageFlag: false,
    expiryManageFlag: false,
  }))
}

describe('ProductSearchDialog', () => {
  it('props.results を el-table に描画する', () => {
    const results = makeResults(3)
    const wrapper = mount(ProductSearchDialog, {
      props: { visible: true, results, total: 3 },
      global: { stubs: globalStubs },
    })
    const rows = wrapper.findAll('.el-table-row')
    expect(rows).toHaveLength(3)
  })

  it('visible=false の場合ダイアログは描画されない', () => {
    const wrapper = mount(ProductSearchDialog, {
      props: { visible: false, results: makeResults(2), total: 2 },
      global: { stubs: globalStubs },
    })
    expect(wrapper.find('.el-dialog-stub').exists()).toBe(false)
  })

  it('行クリックで select イベントが発火し product が渡る', async () => {
    const results = makeResults(2)
    const wrapper = mount(ProductSearchDialog, {
      props: { visible: true, results, total: 2 },
      global: { stubs: globalStubs },
    })
    await wrapper.findAll('.el-table-row')[1].trigger('click')
    const emitted = wrapper.emitted('select')
    expect(emitted).toBeTruthy()
    expect(emitted![0][0]).toEqual(results[1])
  })

  it('total > results.length の場合に件数ヒントが表示される', () => {
    const results = makeResults(2)
    const wrapper = mount(ProductSearchDialog, {
      props: { visible: true, results, total: 10 },
      global: { stubs: globalStubs },
    })
    const footer = wrapper.find('.el-dialog-footer')
    expect(footer.text()).toContain('returns.productSearchHint')
  })

  it('total === results.length の場合は件数ヒントが表示されない', () => {
    const results = makeResults(3)
    const wrapper = mount(ProductSearchDialog, {
      props: { visible: true, results, total: 3 },
      global: { stubs: globalStubs },
    })
    const footer = wrapper.find('.el-dialog-footer')
    // footer slot は描画されないため空
    expect(footer.text()).toBe('')
  })

  it('ダイアログを閉じると update:visible(false) が emit される', async () => {
    const wrapper = mount(ProductSearchDialog, {
      props: { visible: true, results: makeResults(1), total: 1 },
      global: { stubs: globalStubs },
    })
    await wrapper.find('.el-dialog-close').trigger('click')
    const emitted = wrapper.emitted('update:visible')
    expect(emitted).toBeTruthy()
    expect(emitted![0][0]).toBe(false)
  })
})
