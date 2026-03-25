import { describe, it, expect, vi } from 'vitest'

vi.mock('@/api/generated/models/inbound-slip-status', () => ({
  InboundSlipStatus: {
    Planned: 'PLANNED',
    Confirmed: 'CONFIRMED',
    Inspecting: 'INSPECTING',
    PartialStored: 'PARTIAL_STORED',
    Stored: 'STORED',
    Cancelled: 'CANCELLED',
  },
}))

import { inboundStatusLabel, inboundStatusTagType, formatDateTime } from '@/utils/inboundFormatters'

const t = (key: string) => key

describe('inboundStatusLabel', () => {
  it('returns correct label for PLANNED', () => {
    expect(inboundStatusLabel('PLANNED', t)).toBe('inbound.slip.statusPlanned')
  })

  it('returns correct label for CONFIRMED', () => {
    expect(inboundStatusLabel('CONFIRMED', t)).toBe('inbound.slip.statusConfirmed')
  })

  it('returns correct label for INSPECTING', () => {
    expect(inboundStatusLabel('INSPECTING', t)).toBe('inbound.slip.statusInspecting')
  })

  it('returns correct label for PARTIAL_STORED', () => {
    expect(inboundStatusLabel('PARTIAL_STORED', t)).toBe('inbound.slip.statusPartialStored')
  })

  it('returns correct label for STORED', () => {
    expect(inboundStatusLabel('STORED', t)).toBe('inbound.slip.statusStored')
  })

  it('returns correct label for CANCELLED', () => {
    expect(inboundStatusLabel('CANCELLED', t)).toBe('inbound.slip.statusCancelled')
  })

  it('returns the status itself for an unknown status', () => {
    expect(inboundStatusLabel('UNKNOWN', t)).toBe('UNKNOWN')
  })
})

describe('inboundStatusTagType', () => {
  it('returns "info" for PLANNED', () => {
    expect(inboundStatusTagType('PLANNED')).toBe('info')
  })

  it('returns "" for CONFIRMED', () => {
    expect(inboundStatusTagType('CONFIRMED')).toBe('')
  })

  it('returns "warning" for INSPECTING', () => {
    expect(inboundStatusTagType('INSPECTING')).toBe('warning')
  })

  it('returns "warning" for PARTIAL_STORED', () => {
    expect(inboundStatusTagType('PARTIAL_STORED')).toBe('warning')
  })

  it('returns "success" for STORED', () => {
    expect(inboundStatusTagType('STORED')).toBe('success')
  })

  it('returns "danger" for CANCELLED', () => {
    expect(inboundStatusTagType('CANCELLED')).toBe('danger')
  })

  it('returns "info" for unknown status', () => {
    expect(inboundStatusTagType('UNKNOWN')).toBe('info')
  })
})

describe('formatDateTime', () => {
  it('returns empty string for empty input', () => {
    expect(formatDateTime('')).toBe('')
  })

  it('formats a valid ISO date string', () => {
    const result = formatDateTime('2024-01-15T10:30:00')
    expect(result).toMatch(/2024/)
    expect(result).toMatch(/01/)
    expect(result).toMatch(/15/)
  })

  it('formats a date with timezone info', () => {
    const result = formatDateTime('2024-06-20T14:45:00Z')
    expect(result).toMatch(/2024/)
  })
})
