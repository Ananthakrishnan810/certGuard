import type { CertType } from '../api/types'
import { CERT_TYPES } from '../api/types'

const DATE = new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short', year: 'numeric' })
const DATETIME = new Intl.DateTimeFormat('en-GB', {
  day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
})

/** Formats a yyyy-MM-dd date without timezone shifts. */
export function fmtDate(iso: string | null | undefined): string {
  if (!iso) return '—'
  const [y, m, d] = iso.split('-').map(Number)
  return DATE.format(new Date(y, m - 1, d))
}

export function fmtDateTime(iso: string | null | undefined): string {
  return iso ? DATETIME.format(new Date(iso)) : '—'
}

export function fmtBytes(n: number): string {
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / 1024 / 1024).toFixed(2)} MB`
}

export function typeLabel(t: CertType): string {
  return CERT_TYPES.find((x) => x.value === t)?.label ?? t
}

export function daysText(daysLeft: number): string {
  if (daysLeft === 0) return 'Expires today'
  if (daysLeft > 0) return `${daysLeft} day${daysLeft === 1 ? '' : 's'} left`
  return `Expired ${-daysLeft} day${daysLeft === -1 ? '' : 's'} ago`
}

export function offsetText(offset: number): string {
  if (offset > 0) return `${offset} day${offset === 1 ? '' : 's'} before`
  if (offset === 0) return 'Expiry day'
  return `${-offset / 7} week${offset === -7 ? '' : 's'} overdue`
}
