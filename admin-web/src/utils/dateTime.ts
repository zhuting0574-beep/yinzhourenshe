export function formatDateTime(value: unknown, fallback = '-') {
  if (value === null || value === undefined || value === '') return fallback
  return String(value).trim()
}

export function isDateTimeField(field: string) {
  return /(?:_at|_time|_start|_end)$/.test(field)
}
