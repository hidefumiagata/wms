import { describe, it, expect } from 'vitest'
import { toDateString } from '@/utils/dateFormat'

describe('toDateString', () => {
  it('YYYY-MM-DD 形式の文字列を返す', () => {
    const d = new Date(2026, 0, 15) // 2026-01-15 ローカル
    expect(toDateString(d)).toBe('2026-01-15')
  })

  it('月・日が1桁の場合ゼロ埋めされる', () => {
    const d = new Date(2026, 2, 5) // 2026-03-05 ローカル
    expect(toDateString(d)).toBe('2026-03-05')
  })

  it('年末の日付を正しくフォーマットする', () => {
    const d = new Date(2025, 11, 31) // 2025-12-31 ローカル
    expect(toDateString(d)).toBe('2025-12-31')
  })

  it('ローカルタイムゾーンの日付を返す（UTC変換しない）', () => {
    // JST 2026-04-01 00:30 = UTC 2025-03-31 15:30
    // toISOString().slice(0,10) だと '2026-03-31' になるケース
    const d = new Date(2026, 3, 1, 0, 30, 0) // ローカル 4月1日 0:30
    expect(toDateString(d)).toBe('2026-04-01')
  })
})
