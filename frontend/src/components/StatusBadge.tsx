import type { CertStatus } from '../api/types'

const STYLE: Record<CertStatus, { cls: string; label: string }> = {
  VALID: { cls: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20', label: 'Valid' },
  EXPIRING_SOON: { cls: 'bg-amber-50 text-amber-800 ring-amber-600/30', label: 'Expiring soon' },
  EXPIRED: { cls: 'bg-red-50 text-red-700 ring-red-600/20', label: 'Expired' },
}

export default function StatusBadge({ status, archived }: { status: CertStatus; archived?: boolean }) {
  if (archived) {
    return <span className="inline-flex items-center rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-600 ring-1 ring-inset ring-slate-500/20">Archived</span>
  }
  const s = STYLE[status]
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${s.cls}`}>
      <span className="h-1.5 w-1.5 rounded-full bg-current" />
      {s.label}
    </span>
  )
}
