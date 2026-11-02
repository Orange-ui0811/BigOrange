const priceFormatter = new Intl.NumberFormat('zh-CN', {
  minimumFractionDigits: 0,
  maximumFractionDigits: 2,
})

export const formatWorkPrice = price => {
  if (price === null || price === undefined || price === '') return '价格面议'
  const numericPrice = Number(price)
  if (!Number.isFinite(numericPrice)) return '价格面议'
  return `¥${priceFormatter.format(numericPrice)}`
}
