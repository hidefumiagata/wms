import { InboundSlipStatus } from '@/api/generated/models/inbound-slip-status'

export function inboundStatusLabel(status: string, t: (key: string) => string): string {
  switch (status) {
    case InboundSlipStatus.Planned: return t('inbound.slip.statusPlanned')
    case InboundSlipStatus.Confirmed: return t('inbound.slip.statusConfirmed')
    case InboundSlipStatus.Inspecting: return t('inbound.slip.statusInspecting')
    case InboundSlipStatus.PartialStored: return t('inbound.slip.statusPartialStored')
    case InboundSlipStatus.Stored: return t('inbound.slip.statusStored')
    case InboundSlipStatus.Cancelled: return t('inbound.slip.statusCancelled')
    default: return status
  }
}

export function inboundStatusTagType(status: string): '' | 'success' | 'warning' | 'danger' | 'info' {
  switch (status) {
    case InboundSlipStatus.Planned: return 'info'
    case InboundSlipStatus.Confirmed: return ''
    case InboundSlipStatus.Inspecting: return 'warning'
    case InboundSlipStatus.PartialStored: return 'warning'
    case InboundSlipStatus.Stored: return 'success'
    case InboundSlipStatus.Cancelled: return 'danger'
    default: return 'info'
  }
}

export function formatDateTime(dateStr: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return d.toLocaleString('ja-JP', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}
