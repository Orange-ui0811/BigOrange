import { describe, expect, it } from 'vitest'
import { routes } from './index'

const viewModules = import.meta.glob('../views/*.vue')

const routeComponents = routeList => routeList.flatMap(route => [
  ...(typeof route.component === 'function' ? [route.component] : []),
  ...routeComponents(route.children || []),
])

describe('view and route smoke coverage', () => {
  it('loads every one of the 34 view modules', async () => {
    const paths = Object.keys(viewModules).sort()
    expect(paths).toHaveLength(34)
    for (const path of paths) {
      const module = await viewModules[path]()
      expect(module.default, `${path} should export a Vue component`).toBeTruthy()
    }
  })

  it('resolves every lazy route component', async () => {
    const components = routeComponents(routes)
    expect(components.length).toBeGreaterThanOrEqual(30)
    for (const loadComponent of components) {
      const module = await loadComponent()
      expect(module.default).toBeTruthy()
    }
  })
})
