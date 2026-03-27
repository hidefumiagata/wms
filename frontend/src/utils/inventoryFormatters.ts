import { UnitType } from '@/api/generated/models/unit-type'
import { StorageCondition } from '@/api/generated/models/storage-condition'

export function unitTypeLabel(unitType: string, t: (key: string) => string): string {
  switch (unitType) {
    case UnitType.Case:
      return t('inventory.unitTypeCase')
    case UnitType.Ball:
      return t('inventory.unitTypeBall')
    case UnitType.Piece:
      return t('inventory.unitTypePiece')
    default:
      return unitType
  }
}

export function storageConditionLabel(condition: string, t: (key: string) => string): string {
  switch (condition) {
    case StorageCondition.Ambient:
      return t('inventory.storageAmbient')
    case StorageCondition.Refrigerated:
      return t('inventory.storageRefrigerated')
    case StorageCondition.Frozen:
      return t('inventory.storageFrozen')
    default:
      return condition
  }
}

export function storageConditionTagType(
  condition: string,
): '' | 'success' | 'warning' | 'danger' | 'info' {
  switch (condition) {
    case StorageCondition.Ambient:
      return ''
    case StorageCondition.Refrigerated:
      return 'warning'
    case StorageCondition.Frozen:
      return 'info'
    default:
      return 'info'
  }
}

export function formatNumber(val: number | undefined | null): string {
  if (val == null) return '-'
  return val.toLocaleString('ja-JP')
}

export function formatDate(dateStr: string | null | undefined): string {
  if (!dateStr) return ''
  const d = dateStr.includes('T') ? new Date(dateStr) : new Date(dateStr + 'T00:00:00')
  return d.toLocaleDateString('ja-JP', { year: 'numeric', month: '2-digit', day: '2-digit' })
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
