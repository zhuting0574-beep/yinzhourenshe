export function resolveApiPath(path: string, base = import.meta.env.VITE_API_BASE || '/api') {
  const value = String(path || '').trim()
  if (/^https?:\/\//i.test(value)) return value
  const normalizedBase = String(base || '/api').replace(/\/+$/, '')
  const suffix = value.replace(/^\/?api(?=\/|$)/, '').replace(/^\/+/, '')
  return suffix ? `${normalizedBase}/${suffix}` : normalizedBase
}
