// Repair over-escaped prose paragraphs in old model replies; code samples stay literal.
export function normalizeCustomerText(value) {
  if (typeof value !== 'string') return ''
  return value.split(/(```[\s\S]*?```|`[^`\n]*`)/g).map((part, index) =>
    index % 2 ? part : part.replace(/(?:\\r\\n|\\n){2,}/g, '\n\n')
  ).join('')
}

export function customerDifficulty(value) {
  const levels = { BEGINNER: '入门', EASY: '入门', SIMPLE: '入门', MEDIUM: '中等', INTERMEDIATE: '中等', ADVANCED: '进阶', HARD: '进阶' }
  return levels[String(value || '').toUpperCase()] || value || '待核实'
}
