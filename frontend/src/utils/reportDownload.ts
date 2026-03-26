import apiClient from '@/api/client'
import type { AxiosResponse } from 'axios'

export type ReportFormat = 'json' | 'csv' | 'pdf'

interface DownloadOptions {
  /** API パス（/api/v1 からの相対パス 例: '/reports/inventory'） */
  path: string
  /** クエリパラメータ（format を除く） */
  params: Record<string, unknown>
  /** 出力フォーマット */
  format: ReportFormat
  /** ダウンロード時のファイル名（拡張子なし） */
  filenameBase: string
}

/**
 * レポートをダウンロードする。
 *
 * - json: パースしたデータ配列を返却（将来の画面内プレビュー用）
 * - csv/pdf: Blob としてダウンロードをトリガーする
 */
export async function downloadReport<T = unknown>(options: DownloadOptions): Promise<T[] | void> {
  const { path, params, format, filenameBase } = options

  if (format === 'json') {
    const response = await apiClient.get<T[]>(path, {
      params: { ...params, format: 'json' },
    })
    return response.data
  }

  // CSV or PDF: Blob で取得してブラウザダウンロードをトリガー
  const response: AxiosResponse<Blob> = await apiClient.get(path, {
    params: { ...params, format },
    responseType: 'blob',
  })

  // エラーレスポンスが JSON として返された場合の検出
  // （Blob モードではステータスコードが 2xx でないとき Axios が reject するが、
  //   サーバーが 200 + JSON エラーを返すケースに備えて Content-Type をチェック）
  const contentType = response.headers['content-type'] || ''
  if (contentType.includes('application/json')) {
    const text = await response.data.text()
    const errorData = JSON.parse(text)
    throw new Error(errorData.message || 'レポートのダウンロードに失敗しました')
  }

  const extension = format === 'csv' ? '.csv' : '.pdf'
  const mimeType = format === 'csv' ? 'text/csv; charset=UTF-8' : 'application/pdf'

  const blob = new Blob([response.data], { type: mimeType })
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filenameBase + extension
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}
