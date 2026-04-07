import { describe, it, expect } from 'vitest'
import { toProductOption } from '@/types/master'

describe('toProductOption', () => {
  it('正常: 全フィールド正しい型の場合はそのまま返る', () => {
    const result = toProductOption({
      id: 5,
      productCode: 'P001',
      productName: '商品A',
      lotManageFlag: true,
      expiryManageFlag: true,
    })
    expect(result).toEqual({
      id: 5,
      productCode: 'P001',
      productName: '商品A',
      lotManageFlag: true,
      expiryManageFlag: true,
    })
  })

  it('id が string "5" の場合は Number 化されて 5 になる', () => {
    const result = toProductOption({
      id: '5',
      productCode: 'P001',
      productName: '商品A',
    })
    expect(result.id).toBe(5)
    expect(typeof result.id).toBe('number')
  })

  it('id が undefined の場合は 0 になる', () => {
    const result = toProductOption({
      productCode: 'P001',
      productName: '商品A',
    })
    expect(result.id).toBe(0)
  })

  it('productCode が undefined の場合は空文字になる', () => {
    const result = toProductOption({
      id: 1,
      productName: '商品A',
    })
    expect(result.productCode).toBe('')
  })

  it('productName が undefined の場合は空文字になる', () => {
    const result = toProductOption({
      id: 1,
      productCode: 'P001',
    })
    expect(result.productName).toBe('')
  })

  it('lotManageFlag が string "true" の場合は false になる（=== true の厳密比較）', () => {
    const result = toProductOption({
      id: 1,
      productCode: 'P001',
      productName: '商品A',
      lotManageFlag: 'true',
    })
    expect(result.lotManageFlag).toBe(false)
  })

  it('lotManageFlag が number 1 の場合は false になる', () => {
    const result = toProductOption({
      id: 1,
      productCode: 'P001',
      productName: '商品A',
      lotManageFlag: 1,
    })
    expect(result.lotManageFlag).toBe(false)
  })

  it('lotManageFlag が undefined の場合は false になる', () => {
    const result = toProductOption({
      id: 1,
      productCode: 'P001',
      productName: '商品A',
    })
    expect(result.lotManageFlag).toBe(false)
  })

  it('expiryManageFlag が string "true" の場合は false になる', () => {
    const result = toProductOption({
      id: 1,
      productCode: 'P001',
      productName: '商品A',
      expiryManageFlag: 'true',
    })
    expect(result.expiryManageFlag).toBe(false)
  })

  it('expiryManageFlag が number 1 の場合は false になる', () => {
    const result = toProductOption({
      id: 1,
      productCode: 'P001',
      productName: '商品A',
      expiryManageFlag: 1,
    })
    expect(result.expiryManageFlag).toBe(false)
  })

  it('expiryManageFlag が undefined の場合は false になる', () => {
    const result = toProductOption({
      id: 1,
      productCode: 'P001',
      productName: '商品A',
    })
    expect(result.expiryManageFlag).toBe(false)
  })
})
