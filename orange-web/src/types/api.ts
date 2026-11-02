export interface ApiResult<T> {
  code: 0 | 1
  msg: string | null
  data: T
}

export interface PageResult<T> {
  total: number
  records: T[]
}
