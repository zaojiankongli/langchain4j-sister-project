/** Standard API response wrapper used across all REST endpoints. */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
  timestamp: string
}
