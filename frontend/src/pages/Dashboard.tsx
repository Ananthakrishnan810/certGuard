import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { errorMessage } from '../api/client'
import { dashboardApi, settingsApi } from '../api/endpoints'
import type { DashboardSummary } from '../api/types'
import StatusBadge from '../components/StatusBadge'
import { Alert, PageHeader, PageLoader, Spinner } from '../components/ui'
import { daysText, fmtDate, typeLabel } from '../utils/format'

const TILES = [
  { key: 'total', label: 'Total active', cls: 'text-slate-900', ring: 'border-slate-200', status: '' },
  { key: 'valid', label: 'Valid', cls: 'text-emerald-600', ring: 'border-emerald-200', status: 'VALID' },
  { key: 'expiringSoon', label: 'Expiring soon', cls: 'text-amber-600', ring: 'border-amber-200', status: 'EXPIRING_SOON' },
  { key: 'expired', label: 'Expired', cls: 'text-red-600', ring: 'border-red-200', status: 'EXPIRED' },
] as const

export default function Dashboard() {
  const [data, setData] = useState<DashboardSummary | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [running, setRunning] = useState(false)
  const navigate = useNavigate()

  const load = useCallback(() => {
    dashboardApi.summary().then(setData).catch((e) => setError(errorMessage(e, 'Could not load dashboard')))
  }, [])
  useEffect(load, [load])

  const runCheck = async () => {
    setRunning(true)
    setNotice(null)
    try {
      const r = await settingsApi.runCheck()
      setNotice(`Checked ${r.checked} certificate(s): ${r.sent} reminder(s) sent, ${r.failed} failed, ${r.statusChanged} status change(s).`)
      load()
    } catch (e) {
      setError(errorMessage(e, 'Expiry check failed'))
    } finally {
      setRunning(false)
    }
  }

  if (!data && !error) return <PageLoader />

  return (
    <div>
      <PageHeader
        title="Dashboard"
        subtitle="Certificates expiring in the next 90 days"
        actions={<>
          <button className="btn-secondary" onClick={runCheck} disabled={running}>
            {running && <Spinner className="h-4 w-4" />} Run expiry check now
          </button>
          <Link to="/certificates/new" className="btn-primary">+ Upload certificate</Link>
        </>}
      />
      {error && <div className="mb-4"><Alert tone="error" onClose={() => setError(null)}>{error}</Alert></div>}
      {notice && <div className="mb-4"><Alert tone="info" onClose={() => setNotice(null)}>{notice}</Alert></div>}

      {data && (
        <>
          <div className="mb-8 grid grid-cols-2 gap-4 lg:grid-cols-4">
            {TILES.map((t) => (
              <button key={t.key}
                onClick={() => navigate(t.status ? `/certificates?status=${t.status}` : '/certificates')}
                className={`card border-l-4 p-5 text-left transition hover:shadow-md ${t.ring}`}>
                <div className="text-sm text-slate-500">{t.label}</div>
                <div className={`mt-1 text-3xl font-semibold ${t.cls}`}>{data[t.key]}</div>
              </button>
            ))}
          </div>

          <div className="card overflow-hidden">
            <div className="flex items-center justify-between border-b border-slate-200 px-5 py-3">
              <h2 className="font-semibold">Upcoming & overdue</h2>
              {data.archived > 0 && <Link to="/certificates?archived=true" className="text-sm text-slate-500 hover:underline">{data.archived} archived</Link>}
            </div>
            {data.upcoming.length === 0 ? (
              <div className="px-5 py-12 text-center text-sm text-slate-500">
                Nothing expires in the next 90 days. 🎉
              </div>
            ) : (
              <ul className="divide-y divide-slate-100">
                {data.upcoming.map((c) => (
                  <li key={c.id}>
                    <Link to={`/certificates/${c.id}`} className="flex items-center gap-4 px-5 py-3 hover:bg-slate-50">
                      <div className={`w-1.5 self-stretch rounded-full ${c.status === 'EXPIRED' ? 'bg-red-500' : c.status === 'EXPIRING_SOON' ? 'bg-amber-400' : 'bg-emerald-500'}`} />
                      <div className="min-w-0 flex-1">
                        <div className="truncate font-medium text-slate-900">{c.title}</div>
                        <div className="text-xs text-slate-500">{typeLabel(c.type)}{c.issuer ? ` · ${c.issuer}` : ''}</div>
                      </div>
                      <div className="hidden text-right sm:block">
                        <div className="text-sm text-slate-700">{fmtDate(c.expiryDate)}</div>
                        <div className={`text-xs ${c.daysLeft < 0 ? 'text-red-600' : 'text-slate-500'}`}>{daysText(c.daysLeft)}</div>
                      </div>
                      <StatusBadge status={c.status} />
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </>
      )}
    </div>
  )
}
