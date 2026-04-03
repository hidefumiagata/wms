/**
 * Date を 'YYYY-MM-DD' 形式のローカル日付文字列に変換する。
 *
 * toISOString().slice(0,10) は UTC 変換されるため、
 * JST 深夜 0時〜9時の間に1日ずれる問題がある。
 * sv-SE ロケールは常に YYYY-MM-DD を返すため安全。
 */
export function toDateString(d: Date): string {
  return d.toLocaleDateString('sv-SE')
}
