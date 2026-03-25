import { describe, it, expect, vi, beforeEach } from 'vitest'
import apiClient from '@/api/client'
import { ElMessage, ElMessageBox } from 'element-plus'
import { withSetup, mockAxiosResponse, flushPromises } from '../../helpers'
import { useSystemParameters } from '@/composables/system/useSystemParameters'
import type { ParameterRow } from '@/composables/system/useSystemParameters'
import axios from 'axios'

vi.mock('@/api/generated/models/system-parameter-detail', () => ({}))
vi.mock('@/api/generated/models/system-parameter-value-type', () => ({
  SystemParameterValueType: { Integer: 'INTEGER', String: 'STRING' },
}))
vi.mock('@/api/generated/models/system-parameter-category', () => ({
  SystemParameterCategory: {
    Inventory: 'INVENTORY',
    Outbound: 'OUTBOUND',
    Inbound: 'INBOUND',
    System: 'SYSTEM',
    Security: 'SECURITY',
  },
}))

describe('useSystemParameters', () => {
  const mockParams = [
    { paramKey: 'p1', paramValue: '10', category: 'INVENTORY', valueType: 'INTEGER', displayName: 'Param1', version: 1 },
    { paramKey: 'p2', paramValue: 'hello', category: 'INVENTORY', valueType: 'STRING', displayName: 'Param2', version: 1 },
    { paramKey: 'p3', paramValue: '5', category: 'OUTBOUND', valueType: 'INTEGER', displayName: 'Param3', version: 1 },
  ]

  beforeEach(() => {
    vi.mocked(apiClient.get).mockResolvedValue(mockAxiosResponse(mockParams))
  })

  it('fetchParameters がカテゴリ別にグルーピングする', async () => {
    const { result } = withSetup(() => useSystemParameters())
    await result.fetchParameters()

    expect(result.groups.value).toHaveLength(2)
    expect(result.groups.value[0].category).toBe('INVENTORY')
    expect(result.groups.value[0].items).toHaveLength(2)
    expect(result.groups.value[1].category).toBe('OUTBOUND')
    expect(result.groups.value[1].items).toHaveLength(1)
  })

  it('fetchParameters が signal を渡す', async () => {
    const { result } = withSetup(() => useSystemParameters())
    await result.fetchParameters()

    const callArgs = vi.mocked(apiClient.get).mock.calls[0]
    expect(callArgs[1]).toHaveProperty('signal')
    expect(callArgs[1]!.signal).toBeInstanceOf(AbortSignal)
  })

  it('onUnmounted 時にリクエストがキャンセルされる', async () => {
    const { result, wrapper } = withSetup(() => useSystemParameters())
    const fetchPromise = result.fetchParameters()

    const signal = vi.mocked(apiClient.get).mock.calls[0][1]!.signal!
    expect(signal.aborted).toBe(false)

    wrapper.unmount()
    expect(signal.aborted).toBe(true)

    await fetchPromise
  })

  it('キャンセル時に state が更新されない', async () => {
    const { result } = withSetup(() => useSystemParameters())
    await result.fetchParameters()
    expect(result.groups.value).toHaveLength(2)

    const cancelError = new Error('canceled')
    vi.mocked(apiClient.get).mockRejectedValueOnce(cancelError)
    vi.mocked(axios.isCancel).mockReturnValueOnce(true)

    await result.fetchParameters()
    expect(result.groups.value).toHaveLength(2)
  })

  it('toggleCategory が collapsed を切り替える', async () => {
    const { result } = withSetup(() => useSystemParameters())
    await result.fetchParameters()

    const group = result.groups.value[0]
    expect(group.collapsed).toBe(false)
    result.toggleCategory(group)
    expect(group.collapsed).toBe(true)
    result.toggleCategory(group)
    expect(group.collapsed).toBe(false)
  })

  it('isDirty が変更を検知する', async () => {
    const { result } = withSetup(() => useSystemParameters())
    await result.fetchParameters()

    const row = result.groups.value[0].items[0]
    expect(result.isDirty(row)).toBe(false)
    row.editValue = '999'
    expect(result.isDirty(row)).toBe(true)
  })

  it('validateValue が必須チェックを行う', async () => {
    const { result } = withSetup(() => useSystemParameters())
    await result.fetchParameters()

    const row = result.groups.value[0].items[0]
    row.editValue = ''
    expect(result.validateValue(row)).toBe('system.parameters.validation.required')
  })

  it('validateValue が整数フォーマットチェックを行う', async () => {
    const { result } = withSetup(() => useSystemParameters())
    await result.fetchParameters()

    const row = result.groups.value[0].items[0] // INTEGER type
    row.editValue = 'abc'
    expect(result.validateValue(row)).toBe('system.parameters.validation.integerFormat')

    row.editValue = '42'
    expect(result.validateValue(row)).toBeNull()
  })

  it('validateValue が文字列長チェックを行う', async () => {
    const { result } = withSetup(() => useSystemParameters())
    await result.fetchParameters()

    const row = result.groups.value[0].items[1] // STRING type
    row.editValue = 'x'.repeat(501)
    expect(result.validateValue(row)).toBe('system.parameters.validation.stringMaxLength')

    row.editValue = 'valid string'
    expect(result.validateValue(row)).toBeNull()
  })

  it('handleSave が確認後にPUTリクエストを送信する', async () => {
    const updatedParam = { ...mockParams[0], paramValue: '99', version: 2 }
    vi.mocked(apiClient.put).mockResolvedValue(mockAxiosResponse(updatedParam))

    const { result } = withSetup(() => useSystemParameters())
    await result.fetchParameters()

    const row = result.groups.value[0].items[0]
    row.editValue = '99'

    await result.handleSave(row)

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(apiClient.put).toHaveBeenCalledWith('/system/parameters/p1', {
      paramValue: '99',
      version: 1,
    })
    expect(ElMessage.success).toHaveBeenCalled()
  })

  it('handleSave がバリデーションエラー時にPUTしない', async () => {
    const { result } = withSetup(() => useSystemParameters())
    await result.fetchParameters()

    const row = result.groups.value[0].items[0]
    row.editValue = '' // required violation

    await result.handleSave(row)

    expect(apiClient.put).not.toHaveBeenCalled()
    expect(ElMessage.error).toHaveBeenCalled()
  })
})
