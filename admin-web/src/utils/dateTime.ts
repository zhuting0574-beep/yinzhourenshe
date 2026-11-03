export function formatDateTime(value: unknown, fallback = '-') {
  if (value === null || value === undefined || value === '') return fallback
  const text = String(value).trim().replace('T', ' ')
  const match = text.match(/^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})/)
  return match ? match[1] : text
}

export function isDateTimeField(field: string) {
  return /(?:_at|_time|_start|_end)$/.test(field)
}
