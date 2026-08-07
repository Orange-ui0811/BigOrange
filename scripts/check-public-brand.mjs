import { readFile, readdir } from 'node:fs/promises'
import { extname, join } from 'node:path'

const roots = [
  'orange-web/src',
  'orange-admin/src',
  'orange-server/src/main/java',
  'orange-server/src/main/resources/mapper',
]
if (process.argv.includes('--include-dist')) roots.push('orange-web/dist', 'orange-admin/dist')
const readableExtensions = new Set(['.java', '.js', '.mjs', '.vue', '.xml', '.html', '.css'])
const forbidden = /OrangeJD/g
const violations = []

async function scan(path) {
  let entries
  try {
    entries = await readdir(path, { withFileTypes: true })
  } catch (error) {
    if (error?.code === 'ENOENT' && path.endsWith('/dist')) return
    throw error
  }
  for (const entry of entries) {
    const child = join(path, entry.name)
    if (entry.isDirectory()) await scan(child)
    else if (readableExtensions.has(extname(entry.name))) {
      const content = await readFile(child, 'utf8')
      if (forbidden.test(content)) violations.push(child)
      forbidden.lastIndex = 0
    }
  }
}

for (const root of roots) await scan(root)
if (violations.length) {
  console.error(`Public brand check failed; legacy name found in:\n${violations.join('\n')}`)
  process.exit(1)
}
console.log('public brand check passed')
