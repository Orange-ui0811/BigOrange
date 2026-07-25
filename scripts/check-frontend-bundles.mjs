import { readdir, stat } from 'node:fs/promises'
import { join } from 'node:path'

const limit = Number(process.env.FRONTEND_CHUNK_LIMIT_BYTES || 1_250_000)
const apps = ['orange-web', 'orange-admin']
let failed = false

for (const app of apps) {
  const assets = join(process.cwd(), app, 'dist', 'assets')
  const files = await readdir(assets)
  for (const file of files.filter((name) => name.endsWith('.js'))) {
    const size = (await stat(join(assets, file))).size
    if (size > limit) {
      console.error(`${app}/${file}: ${size} bytes exceeds ${limit}`)
      failed = true
    }
  }
}

if (failed) process.exit(1)
console.log(`Frontend bundle budget passed (max ${limit} bytes per JS chunk)`)
