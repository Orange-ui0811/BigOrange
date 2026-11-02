const dnsName = /^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$/i
const ipv4 = /^(?:[0-9]{1,3}\.){3}[0-9]{1,3}$/
const forbiddenSuffixes = ['.local', '.localhost', '.internal', '.lan', '.home', '.test', '.invalid']

function isPublicIpv4(host) {
  const octets = host.split('.').map(Number)
  if (octets.length !== 4 || octets.some(value => !Number.isInteger(value) || value < 0 || value > 255)) return false
  const [a, b] = octets
  return a !== 0 && a !== 10 && a !== 127 && a < 224
    && !(a === 100 && b >= 64 && b <= 127)
    && !(a === 169 && b === 254)
    && !(a === 172 && b >= 16 && b <= 31)
    && !(a === 192 && b === 0)
    && !(a === 192 && b === 168)
    && !(a === 198 && (b === 18 || b === 19))
    && !(a === 198 && b === 51 && octets[2] === 100)
    && !(a === 203 && b === 0 && octets[2] === 113)
}

function isPublicIpv6(host) {
  const normalized = host.replace(/^\[|\]$/g, '').toLowerCase()
  if (normalized === '::' || normalized === '::1' || normalized.startsWith('::ffff:')) return false
  if (normalized.startsWith('fc') || normalized.startsWith('fd') || normalized.startsWith('ff')) return false
  if (normalized.startsWith('2001:db8') || normalized.startsWith('2001:2:')) return false
  return !['fe8', 'fe9', 'fea', 'feb'].some(prefix => normalized.startsWith(prefix))
}

export function inspectExternalUrl(rawValue) {
  if (typeof rawValue !== 'string' || !rawValue.trim() || rawValue.length > 2048) return { safe: false }
  try {
    const url = new URL(rawValue.trim())
    const hostname = url.hostname.toLowerCase()
    const forbiddenHost = hostname === 'localhost'
      || forbiddenSuffixes.some(suffix => hostname === suffix.slice(1) || hostname.endsWith(suffix))
      || hostname.startsWith('xn--') || hostname.includes('.xn--')
    const validHost = hostname.includes(':')
      ? isPublicIpv6(hostname)
      : ipv4.test(hostname) ? isPublicIpv4(hostname) : dnsName.test(hostname)
    const safe = url.protocol === 'https:' && !url.username && !url.password
      && (!url.port || url.port === '443') && !forbiddenHost && validHost
    const trusted = safe && (hostname === 'github.com' || hostname.endsWith('.github.com'))
    return { safe, trusted, hostname, url: safe ? url : undefined }
  } catch {
    return { safe: false }
  }
}

export function safeExternalHref(rawValue) {
  const result = inspectExternalUrl(rawValue)
  return result.safe ? result.url.href : undefined
}
