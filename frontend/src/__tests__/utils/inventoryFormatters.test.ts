import { describe, it, expect, vi } from 'vitest'

vi.mock('@/api/generated/models/unit-type', () => ({
  UnitType: {
    Case: 'CASE',
    Ball: 'BALL',
    Piece: 'PIECE',
  },
}))

vi.mock('@/api/generated/models/storage-condition', () => ({
  StorageCondition: {
    Ambient: 'AMBIENT',
    Refrigerated: 'REFRIGERATED',
    Frozen: 'FROZEN',
  },
}))

import {
  unitTypeLabel,
  storageConditionLabel,
  storageConditionTagType,
  formatNumber,
  formatDate,
  formatDateTime,
} from '@/utils/inventoryFormatters'

const t = (key: string) => key

describe('unitTypeLabel', () => {
  it('returns correct label for CASE', () => {
    expect(unitTypeLabel('CASE', t)).toBe('inventory.unitTypeCase')
  })

  it('returns correct label for BALL', () => {
    expect(unitTypeLabel('BALL', t)).toBe('inventory.unitTypeBall')
  })

  it('returns correct label for PIECE', () => {
    expect(unitTypeLabel('PIECE', t)).toBe('inventory.unitTypePiece')
  })

  it('returns the unitType itself for an unknown type', () => {
    expect(unitTypeLabel('UNKNOWN', t)).toBe('UNKNOWN')
  })
})

describe('storageConditionLabel', () => {
  it('returns correct label for AMBIENT', () => {
    expect(storageConditionLabel('AMBIENT', t)).toBe('inventory.storageAmbient')
  })

  it('returns correct label for REFRIGERATED', () => {
    expect(storageConditionLabel('REFRIGERATED', t)).toBe('inventory.storageRefrigerated')
  })

  it('returns correct label for FROZEN', () => {
    expect(storageConditionLabel('FROZEN', t)).toBe('inventory.storageFrozen')
  })

  it('returns the condition itself for an unknown condition', () => {
    expect(storageConditionLabel('UNKNOWN', t)).toBe('UNKNOWN')
  })
})

describe('storageConditionTagType', () => {
  it('returns "" for AMBIENT', () => {
    expect(storageConditionTagType('AMBIENT')).toBe('')
  })

  it('returns "warning" for REFRIGERATED', () => {
    expect(storageConditionTagType('REFRIGERATED')).toBe('warning')
  })

  it('returns "info" for FROZEN', () => {
    expect(storageConditionTagType('FROZEN')).toBe('info')
  })

  it('returns "info" for unknown condition', () => {
    expect(storageConditionTagType('UNKNOWN')).toBe('info')
  })
})

describe('formatNumber', () => {
  it('returns "-" for null', () => {
    expect(formatNumber(null)).toBe('-')
  })

  it('returns "-" for undefined', () => {
    expect(formatNumber(undefined)).toBe('-')
  })

  it('formats a number with locale', () => {
    const result = formatNumber(1234567)
    // ja-JP locale uses commas
    expect(result).toMatch(/1.*234.*567/)
  })

  it('formats zero', () => {
    expect(formatNumber(0)).toBe('0')
  })

  it('formats negative numbers', () => {
    const result = formatNumber(-1000)
    expect(result).toContain('1')
    expect(result).toContain('000')
  })
})

describe('formatDate', () => {
  it('returns empty string for null', () => {
    expect(formatDate(null)).toBe('')
  })

  it('returns empty string for undefined', () => {
    expect(formatDate(undefined)).toBe('')
  })

  it('returns empty string for empty string', () => {
    expect(formatDate('')).toBe('')
  })

  it('formats a date-only string (adds T00:00:00)', () => {
    const result = formatDate('2024-05-20')
    expect(result).toMatch(/2024/)
    expect(result).toMatch(/05/)
    expect(result).toMatch(/20/)
  })

  it('formats a full ISO datetime string', () => {
    const result = formatDate('2024-05-20T15:30:00')
    expect(result).toMatch(/2024/)
    expect(result).toMatch(/05/)
    expect(result).toMatch(/20/)
  })
})

describe('formatDateTime', () => {
  it('returns empty string for empty input', () => {
    expect(formatDateTime('')).toBe('')
  })

  it('formats a valid ISO date string', () => {
    const result = formatDateTime('2024-09-01T16:00:00')
    expect(result).toMatch(/2024/)
    expect(result).toMatch(/09/)
    expect(result).toMatch(/01/)
  })

  it('formats a date with timezone info', () => {
    const result = formatDateTime('2024-11-30T00:00:00Z')
    expect(result).toMatch(/2024/)
  })
})
