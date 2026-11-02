import { readFile, readdir } from 'node:fs/promises'
import { extname, join, relative, sep } from 'node:path'

const root = process.cwd()
const baseline = JSON.parse(await readFile(join(root, 'scripts', 'architecture-size-baseline.json'), 'utf8'))
const failures = []

const normalize = value => value.split(sep).join('/')
const countLines = value => {
  const normalized = value.replace(/\r\n/g, '\n').replace(/\r/g, '')
  return normalized.endsWith('\n') ? normalized.slice(0, -1).split('\n').length : normalized.split('\n').length
}

const limitFor = path => {
  if (path.startsWith('orange-server/src/main/java/') && path.endsWith('.java')) return 300
  if (path.startsWith('orange-web/src/views/') && path.endsWith('.vue')) return 500
  if (path.startsWith('orange-web/src/layouts/') && path.endsWith('.vue')) return 500
  if (path.startsWith('orange-web/src/components/') && path.endsWith('.vue')) return 400
  if ((path.includes('/composables/') || path.includes('/stores/')) && ['.js', '.ts'].includes(extname(path))) return 300
  return null
}

const walk = async directory => {
  const entries = await readdir(directory, { withFileTypes: true })
  for (const entry of entries) {
    const absolute = join(directory, entry.name)
    if (entry.isDirectory()) await walk(absolute)
    else {
      const path = normalize(relative(root, absolute))
      const limit = limitFor(path)
      if (!limit) continue
      const source = await readFile(absolute, 'utf8')
      const lines = countLines(source)
      const allowed = baseline[path] ?? limit
      if (lines > allowed) failures.push(`${path}: ${lines} lines exceeds ${allowed}`)
      if (path.endsWith('.vue') && /<style\b(?=[^>]*\bscoped\b)(?=[^>]*\bsrc=)[^>]*>/i.test(source)) {
        failures.push(`${path}: external scoped styles break scope ids in the Vite development server; use a scoped @import block`)
      }
    }
  }
}

await walk(join(root, 'orange-server', 'src', 'main', 'java'))
await walk(join(root, 'orange-web', 'src'))

if (failures.length) {
  failures.forEach(message => console.error(message))
  process.exit(1)
}
console.log('Architecture size budgets passed; legacy oversized files did not grow.')
