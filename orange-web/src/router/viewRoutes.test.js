import { describe, expect, it } from 'vitest'
import { routes } from './index'
import robots from '../../public/robots.txt?raw'
import securityContact from '../../public/.well-known/security.txt?raw'
import sitemap from '../../public/sitemap.xml?raw'
import loginSource from '../views/Login.vue?raw'

const viewModules = import.meta.glob('../views/*.vue')

const routeComponents = routeList => routeList.flatMap(route => [
  ...(typeof route.component === 'function' ? [route.component] : []),
  ...routeComponents(route.children || []),
])

describe('view and route smoke coverage', () => {
  it('loads every one of the 35 view modules', async () => {
    const paths = Object.keys(viewModules).sort()
    expect(paths).toHaveLength(35)
    for (const path of paths) {
      const module = await viewModules[path]()
      expect(module.default, `${path} should export a Vue component`).toBeTruthy()
    }
  }, 20_000)

  it('resolves every lazy route component', async () => {
    const components = routeComponents(routes)
    expect(components.length).toBeGreaterThanOrEqual(30)
    for (const loadComponent of components) {
      const module = await loadComponent()
      expect(module.default).toBeTruthy()
    }
  })

  it('uses the public AI tools page as the default entry and keeps unknown routes out of the login flow', () => {
    const main = routes.find(route => route.name === 'Main')
    const market = main.children.find(route => route.name === 'Market')
    const aiHub = main.children.find(route => route.name === 'AiHub')
    const notFound = routes.find(route => route.name === 'NotFound')
    const login = routes.find(route => route.name === 'Login')

    expect(main.redirect).toBe('/ai')
    expect(aiHub.meta.public).toBe(true)
    expect(market.meta.public).toBe(true)
    expect(notFound.meta.public).toBe(true)
    expect(notFound.meta.robots).toBe('noindex,nofollow')
    expect(login.meta.robots).toBe('noindex,nofollow')
  })

  it('publishes consistent crawler and security contact files', () => {
    expect(sitemap).toContain('<loc>https://orangevf.com/ai</loc>')
    expect(sitemap).toContain('<loc>https://orangevf.com/market</loc>')
    expect(sitemap).not.toContain('<loc>https://orangevf.com/login</loc>')
    expect(robots).toContain('Sitemap: https://orangevf.com/sitemap.xml')
    expect(securityContact).toContain('Contact: https://orangevf.com/support')
  })

  it('controls registration with its own feature switch instead of legal display completeness', () => {
    expect(loginSource).toContain('v-if="REGISTRATION_ENABLED"')
    expect(loginSource).not.toContain('v-if="LEGAL_IDENTITY_COMPLETE"')
  })

  it('keeps the personal filing name out of the login page presentation', () => {
    expect(loginSource).toContain('{{ PUBLIC_OPERATOR_LABEL }}')
    expect(loginSource).not.toContain('{{ LEGAL_OPERATOR }}')
  })
})
