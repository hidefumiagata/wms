/* eslint-disable vue/one-component-per-file */
import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import type { ProductOption } from '@/types/master'

// --- vue-i18n のローカルモック ---
// グローバル setup.ts の i18n モックは他テストへの波及を避けるため変更しない。
// ここでローカルに vi.mock して、t 関数の呼び出し引数（total/count 等）を検証可能にする。
const tSpy = vi.fn((key: string, params?: Record<string, unknown>) => {
  if (params) {
    // 置換引数を文字列へ展開し、テンプレート内で total/count 値を参照可能にする
    const paramStr = Object.entries(params)
      .map(([k, v]) => `${k}=${v}`)
      .join(',')
    return `${key}[${paramStr}]`
  }
  return key
})

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: tSpy }),
}))

// ProductSearchDialog は vue-i18n のモック確立後にインポートする
import ProductSearchDialog from '@/components/master/ProductSearchDialog.vue'

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

// ElTable のスタブ：data 配列と default slot 内の el-table-column から prop を抽出し、
// 各行 × 各列でセルの実値を描画する。これにより wrapper.text() で検証できる。
const ElTableStub = defineComponent({
  name: 'ElTable',
  props: {
    data: { type: Array, default: () => [] },
  },
  emits: ['row-click'],
  setup(props, { slots, emit }) {
    return () => {
      // default slot 内の ElTableColumn から prop 名を抽出
      const columnNodes = slots.default?.() ?? []
      const props$ = columnNodes
        .map((vnode) => {
          const p = (vnode as { props?: Record<string, unknown> }).props
          return (p?.prop as string | undefined) ?? ''
        })
        .filter((p) => p !== '')
      return h(
        'table',
        { class: 'el-table-stub' },
        (props.data as Array<Record<string, unknown>>).map((row, idx) =>
          h(
            'tr',
            {
              key: idx,
              class: 'el-table-row',
              onClick: () => emit('row-click', row),
            },
            props$.map((propName) =>
              h(
                'td',
                { class: 'el-table-cell', 'data-prop': propName },
                String(row[propName] ?? ''),
              ),
            ),
          ),
        ),
      )
    }
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
  it('props.results を el-table に描画する（各列の実値が text に含まれる）', () => {
    const results = makeResults(3)
    const wrapper = mount(ProductSearchDialog, {
      props: { visible: true, results, total: 3 },
      global: { stubs: globalStubs },
    })
    const rows = wrapper.findAll('.el-table-row')
    expect(rows).toHaveLength(3)
    // スタブが prop に基づきセル描画するので実値が text に含まれる
    const text = wrapper.text()
    expect(text).toContain('P001')
    expect(text).toContain('Product 1')
    expect(text).toContain('P003')
    expect(text).toContain('Product 3')
  })

  it('results=[] の場合でも行は描画されず、エラーにならない（防御的テスト）', () => {
    const wrapper = mount(ProductSearchDialog, {
      props: { visible: true, results: [], total: 0 },
      global: { stubs: globalStubs },
    })
    const rows = wrapper.findAll('.el-table-row')
    expect(rows).toHaveLength(0)
    // footer ヒント（total>results.length のときのみ描画）も出ない
    expect(wrapper.find('.el-dialog-footer').text()).toBe('')
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
    expect(emitted!).toHaveLength(1)
    expect(emitted![0][0]).toEqual(results[1])
  })

  it('複数行クリックで select が都度 emit される（他行は emit されない）', async () => {
    const results = makeResults(3)
    const wrapper = mount(ProductSearchDialog, {
      props: { visible: true, results, total: 3 },
      global: { stubs: globalStubs },
    })
    const rows = wrapper.findAll('.el-table-row')
    await rows[0].trigger('click')
    await rows[2].trigger('click')
    const emitted = wrapper.emitted('select')
    expect(emitted).toBeTruthy()
    expect(emitted!).toHaveLength(2)
    expect(emitted![0][0]).toEqual(results[0])
    expect(emitted![1][0]).toEqual(results[2])
  })

  it('total > results.length の場合に件数ヒントが表示され、i18n に {total,count} が渡る', () => {
    tSpy.mockClear()
    const results = makeResults(2)
    const wrapper = mount(ProductSearchDialog, {
      props: { visible: true, results, total: 10 },
      global: { stubs: globalStubs },
    })
    const footer = wrapper.find('.el-dialog-footer')
    // テンプレートには total/count の実値も反映されている
    expect(footer.text()).toContain('returns.productSearchHint')
    expect(footer.text()).toContain('total=10')
    expect(footer.text()).toContain('count=2')
    // t 関数に正しい置換引数が渡ったことを検証
    expect(tSpy).toHaveBeenCalledWith('returns.productSearchHint', { total: 10, count: 2 })
  })

  it('total === results.length の場合は件数ヒントが表示されない', () => {
    tSpy.mockClear()
    const results = makeResults(3)
    const wrapper = mount(ProductSearchDialog, {
      props: { visible: true, results, total: 3 },
      global: { stubs: globalStubs },
    })
    const footer = wrapper.find('.el-dialog-footer')
    // footer slot は描画されないため空
    expect(footer.text()).toBe('')
    expect(tSpy).not.toHaveBeenCalledWith(
      'returns.productSearchHint',
      expect.anything(),
    )
  })

  it('total < results.length（バックエンド不整合）でもヒントは表示されない（境界）', () => {
    tSpy.mockClear()
    const results = makeResults(3)
    const wrapper = mount(ProductSearchDialog, {
      props: { visible: true, results, total: 1 },
      global: { stubs: globalStubs },
    })
    const footer = wrapper.find('.el-dialog-footer')
    expect(footer.text()).toBe('')
    expect(tSpy).not.toHaveBeenCalledWith(
      'returns.productSearchHint',
      expect.anything(),
    )
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
