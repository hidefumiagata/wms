import { OutboundSlipStatus } from '@/api/generated/models/outbound-slip-status'

export function outboundStatusLabel(status: string, t: (key: string) => string): string {
  switch (status) {
    case OutboundSlipStatus.Ordered:
      return t('outbound.slip.statusOrdered')
    case OutboundSlipStatus.PartialAllocated:
      return t('outbound.slip.statusPartialAllocated')
    case OutboundSlipStatus.Allocated:
      return t('outbound.slip.statusAllocated')
    case OutboundSlipStatus.PickingCompleted:
      return t('outbound.slip.statusPickingCompleted')
    case OutboundSlipStatus.Inspecting:
      return t('outbound.slip.statusInspecting')
    case OutboundSlipStatus.Shipped:
      return t('outbound.slip.statusShipped')
    case OutboundSlipStatus.Cancelled:
      return t('outbound.slip.statusCancelled')
    default:
      return status
  }
}

export function outboundStatusTagType(
  status: string,
): '' | 'success' | 'warning' | 'danger' | 'info' {
  switch (status) {
    case OutboundSlipStatus.Ordered:
      return 'info'
    case OutboundSlipStatus.PartialAllocated:
      return 'warning'
    case OutboundSlipStatus.Allocated:
      return ''
    case OutboundSlipStatus.PickingCompleted:
      return ''
    case OutboundSlipStatus.Inspecting:
      return 'warning'
    case OutboundSlipStatus.Shipped:
      return 'success'
    case OutboundSlipStatus.Cancelled:
      return 'danger'
    default:
      return 'info'
  }
}

export function formatDateTime(dateStr: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return d.toLocaleString('ja-JP', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}
