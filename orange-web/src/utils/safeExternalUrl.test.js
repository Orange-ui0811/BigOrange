import { describe, expect, it } from 'vitest'
import { inspectExternalUrl, safeExternalHref } from './safeExternalUrl'

describe('safeExternalUrl', () => {
  it('allows normal public HTTPS and identifies trusted GitHub links', () => {
    expect(inspectExternalUrl('https://example.com/demo').safe).toBe(true)
    expect(inspectExternalUrl('https://github.com/example/project').trusted).toBe(true)
    expect(safeExternalHref('https://example.com/demo')).toBe('https://example.com/demo')
  })

  it.each([
    'http://example.com',
    'javascript:alert(1)',
    'https://user:password@example.com',
    'https://localhost/admin',
    'https://127.0.0.1/admin',
    'https://10.0.0.1/admin',
    'https://192.168.1.1/admin',
    'https://198.51.100.9/admin',
    'https://203.0.113.9/admin',
    'https://[2001:db8::1]/admin',
    'https://service.local/admin',
    'https://xn--example-9d0b.com/admin',
    'https://2130706433/admin',
  ])('rejects unsafe address %s', value => {
    expect(inspectExternalUrl(value).safe).toBe(false)
    expect(safeExternalHref(value)).toBeUndefined()
  })
})
