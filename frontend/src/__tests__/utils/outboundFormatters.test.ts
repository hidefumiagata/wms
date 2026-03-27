import { describe, it, expect, vi } from 'vitest'

vi.mock('@/api/generated/models/outbound-slip-status', () => ({
  OutboundSlipStatus: {
    Ordered: 'ORDERED',
    PartialAllocated: 'PARTIAL_ALLOCATED',
    Allocated: 'ALLOCATED',
    PickingCompleted: 'PICKING_COMPLETED',
    Inspecting: 'INSPECTING',
    Shipped: 'SHIPPED',
    Cancelled: 'CANCELLED',
  },
}))

import {
  outboundStatusLabel,
  outboundStatusTagType,
  formatDateTime,
} from '@/utils/outboundFormatters'

const t = (key: string) => key

describe('outboundStatusLabel', () => {
  it('returns correct label for ORDERED', () => {
    expect(outboundStatusLabel('ORDERED', t)).toBe('outbound.slip.statusOrdered')
  })

  it('returns correct label for PARTIAL_ALLOCATED', () => {
    expect(outboundStatusLabel('PARTIAL_ALLOCATED', t)).toBe('outbound.slip.statusPartialAllocated')
  })

  it('returns correct label for ALLOCATED', () => {
    expect(outboundStatusLabel('ALLOCATED', t)).toBe('outbound.slip.statusAllocated')
  })

  it('returns correct label for PICKING_COMPLETED', () => {
    expect(outboundStatusLabel('PICKING_COMPLETED', t)).toBe('outbound.slip.statusPickingCompleted')
  })

  it('returns correct label for INSPECTING', () => {
    expect(outboundStatusLabel('INSPECTING', t)).toBe('outbound.slip.statusInspecting')
  })

  it('returns correct label for SHIPPED', () => {
    expect(outboundStatusLabel('SHIPPED', t)).toBe('outbound.slip.statusShipped')
  })

  it('returns correct label for CANCELLED', () => {
    expect(outboundStatusLabel('CANCELLED', t)).toBe('outbound.slip.statusCancelled')
  })

  it('returns the status itself for an unknown status', () => {
    expect(outboundStatusLabel('UNKNOWN', t)).toBe('UNKNOWN')
  })
})

describe('outboundStatusTagType', () => {
  it('returns "info" for ORDERED', () => {
    expect(outboundStatusTagType('ORDERED')).toBe('info')
  })

  it('returns "warning" for PARTIAL_ALLOCATED', () => {
    expect(outboundStatusTagType('PARTIAL_ALLOCATED')).toBe('warning')
  })

  it('returns "" for ALLOCATED', () => {
    expect(outboundStatusTagType('ALLOCATED')).toBe('')
  })

  it('returns "" for PICKING_COMPLETED', () => {
    expect(outboundStatusTagType('PICKING_COMPLETED')).toBe('')
  })

  it('returns "warning" for INSPECTING', () => {
    expect(outboundStatusTagType('INSPECTING')).toBe('warning')
  })

  it('returns "success" for SHIPPED', () => {
    expect(outboundStatusTagType('SHIPPED')).toBe('success')
  })

  it('returns "danger" for CANCELLED', () => {
    expect(outboundStatusTagType('CANCELLED')).toBe('danger')
  })

  it('returns "info" for unknown status', () => {
    expect(outboundStatusTagType('UNKNOWN')).toBe('info')
  })
})

describe('formatDateTime', () => {
  it('returns empty string for empty input', () => {
    expect(formatDateTime('')).toBe('')
  })

  it('formats a valid ISO date string', () => {
    const result = formatDateTime('2024-03-10T08:15:00')
    expect(result).toMatch(/2024/)
    expect(result).toMatch(/03/)
    expect(result).toMatch(/10/)
  })

  it('formats a date with timezone info', () => {
    const result = formatDateTime('2024-12-25T23:59:00Z')
    expect(result).toMatch(/2024/)
  })
})
