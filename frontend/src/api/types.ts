/**
 * ページネーションレスポンスの共通型
 * Spring Boot の Page<T> に対応
 */
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
