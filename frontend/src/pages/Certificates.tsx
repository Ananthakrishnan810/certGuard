import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { errorMessage } from '../api/client'
import { certApi } from '../api/endpoints'
import { CERT_TYPES, type Certificate, type CertStatus, type CertType, type PageResponse } from '../api/types'
import StatusBadge from '../components/StatusBadge'
import { Alert, Modal, PageHeader, Spinner } from '../components/ui'
import { daysText, fmtDate, typeLabel } from '../utils/format'

const SORTS = [
  { value: 'expiryDate', label: 'Expiry date' },
  { value: 'issueDate', label: 'Issue date' },
  { value: 'title', label: 'Title' },
  { value: 'createdAt', label: 'Date added' },
]

export default function Certificates() {
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const type = (params.get('type') ?? '') as CertType | ''
  const status = (params.get('status') ?? '') as CertStatus | ''
  const archived = params.get('archived') === 'true'
  const sort = params.get('sort') ?? 'expiryDate'
  const dir = (params.get('dir') ?? 'asc') as 'asc' | 'desc'
  const page = Number(params.get('page') ?? '0')

  const [search, setSearch] = useState(q)
  const [data, setData] = useState<PageResponse<Certificate> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [confirmDelete, setConfirmDelete] = useState<Certificate | null>(null)
  const [deleting, setDeleting] = useState(false)

  const update = (patch: Record<string, string | null>) => {
    const next = new URLSearchParams(params)
    Object.entries(patch).forEach(([k, v]) => (v === null || v === '' ? next.delete(k) : next.set(k, v)))
    if (!('page' in patch)) next.delete('page')
    setParams(next, { replace: true })
  }

  // debounce the search box
  useEffect(() => {
    const t = setTimeout(() => { if (search !== q) update({ q: search }) }, 350)
    return () => clearTimeout(t)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [search])

  const loadData = useCallback(() => {
    setLoading(true)
    certApi.list({ q, type, status, archived, page, size: 10, sort, dir })
      .then((r) => { setData(r); setError(null) })
      .catch((e) => setError(errorMessage(e, 'Could not load certificates')))
      .finally(() => setLoading(false))
  }, [q, type, status, archived, page, sort, dir])

  useEffect(() => {
    loadData()
  }, [loadData])

  const handleDelete = async () => {
    if (!confirmDelete) return
    const target = confirmDelete
    setDeleting(true)
    setError(null)
    try {
      await certApi.remove(target.id)
      // Immediately remove from UI state
      setData((prev) =>
        prev
          ? {
              ...prev,
              content: prev.content.filter((item) => item.id !== target.id),
              totalElements: Math.max(0, prev.totalElements - 1),
            }
          : null
      )
      setNotice(`"${target.title}" was permanently deleted.`)
      setConfirmDelete(null)
      loadData()
    } catch (e) {
      setError(errorMessage(e, 'Failed to delete certificate'))
    } finally {
      setDeleting(false)
    }
  }

  return (
    <div>
      <PageHeader title="Certificates" subtitle={data ? `${data.totalElements} ${archived ? 'archived' : 'active'} certificate(s)` : ' '}
        actions={<Link to="/certificates/new" className="btn-primary">+ Upload certificate</Link>} />

      <div className="card mb-4 grid grid-cols-1 gap-3 p-4 sm:grid-cols-2 lg:grid-cols-6">
        <input className="input lg:col-span-2" placeholder="Search title, issuer, number, tags…" value={search}
          onChange={(e) => setSearch(e.target.value)} />
        <select className="input" value={type} onChange={(e) => update({ type: e.target.value })}>
          <option value="">All types</option>
          {CERT_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
        </select>
        <select className="input" value={status} onChange={(e) => update({ status: e.target.value })}>
          <option value="">All statuses</option>
          <option value="VALID">Valid</option>
          <option value="EXPIRING_SOON">Expiring soon</option>
          <option value="EXPIRED">Expired</option>
        </select>
        <div className="flex gap-2">
          <select className="input" value={sort} onChange={(e) => update({ sort: e.target.value })}>
            {SORTS.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
          </select>
          <button className="btn-secondary px-3" title="Toggle sort direction" onClick={() => update({ dir: dir === 'asc' ? 'desc' : 'asc' })}>
            {dir === 'asc' ? '↑' : '↓'}
          </button>
        </div>
        <label className="flex items-center gap-2 text-sm text-slate-600">
          <input type="checkbox" checked={archived} onChange={(e) => update({ archived: e.target.checked ? 'true' : null })} />
          Show archived
        </label>
      </div>

      {error && <div className="mb-4"><Alert tone="error" onClose={() => setError(null)}>{error}</Alert></div>}
      {notice && <div className="mb-4"><Alert tone="success" onClose={() => setNotice(null)}>{notice}</Alert></div>}

      <div className="card overflow-hidden">
        <div className="overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-3">Title</th>
                <th className="px-4 py-3">Type</th>
                <th className="px-4 py-3">Issued</th>
                <th className="px-4 py-3">Expires</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Reminders</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading && !data && (
                <tr><td colSpan={7} className="py-12 text-center text-blue-600"><Spinner className="mx-auto h-6 w-6" /></td></tr>
              )}
              {data?.content.length === 0 && (
                <tr><td colSpan={7} className="py-12 text-center text-slate-500">No certificates match.</td></tr>
              )}
              {data?.content.map((c) => (
                <tr key={c.id} className={`hover:bg-slate-50 ${loading ? 'opacity-60' : ''}`}>
                  <td className="px-4 py-3">
                    <Link to={`/certificates/${c.id}`} className="font-medium text-slate-900 hover:text-blue-600">{c.title}</Link>
                    <div className="text-xs text-slate-500">{[c.issuer, c.certNumber].filter(Boolean).join(' · ') || '—'}</div>
                  </td>
                  <td className="whitespace-nowrap px-4 py-3 text-slate-600">{typeLabel(c.type)}</td>
                  <td className="whitespace-nowrap px-4 py-3 text-slate-600">{fmtDate(c.issueDate)}</td>
                  <td className="whitespace-nowrap px-4 py-3">
                    <div className="text-slate-800">{fmtDate(c.expiryDate)}</div>
                    <div className={`text-xs ${c.daysLeft < 0 ? 'text-red-600' : c.status === 'EXPIRING_SOON' ? 'text-amber-700' : 'text-slate-500'}`}>{daysText(c.daysLeft)}</div>
                  </td>
                  <td className="px-4 py-3"><StatusBadge status={c.status} archived={c.archived} /></td>
                  <td className="whitespace-nowrap px-4 py-3 text-xs text-slate-500">
                    {c.remindersEnabled ? c.reminderDays.map((d) => (d === 0 ? '0' : d)).join(', ') + ' d' : 'Off'}
                  </td>
                  <td className="whitespace-nowrap px-4 py-3 text-right">
                    <button
                      type="button"
                      className="rounded px-2.5 py-1 text-xs font-medium text-red-600 hover:bg-red-50 hover:text-red-700"
                      onClick={() => setConfirmDelete(c)}
                      title="Delete certificate"
                    >
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {data && data.totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-slate-200 px-4 py-3 text-sm">
            <span className="text-slate-500">Page {data.page + 1} of {data.totalPages}</span>
            <div className="flex gap-2">
              <button className="btn-secondary px-3 py-1.5" disabled={page === 0} onClick={() => update({ page: String(page - 1) })}>Previous</button>
              <button className="btn-secondary px-3 py-1.5" disabled={page + 1 >= data.totalPages} onClick={() => update({ page: String(page + 1) })}>Next</button>
            </div>
          </div>
        )}
      </div>

      {confirmDelete && (
        <Modal title="Delete certificate?" onClose={() => setConfirmDelete(null)}>
          <p className="text-sm text-slate-600">
            Are you sure you want to permanently delete <strong>{confirmDelete.title}</strong>? This will remove the certificate, its stored files, and notification history from the database.
          </p>
          <div className="mt-6 flex justify-end gap-2">
            <button
              type="button"
              className="btn-secondary"
              onClick={() => setConfirmDelete(null)}
              disabled={deleting}
            >
              Cancel
            </button>
            <button
              type="button"
              className="btn-danger"
              disabled={deleting}
              onClick={handleDelete}
            >
              {deleting && <Spinner className="h-4 w-4" />} Delete permanently
            </button>
          </div>
        </Modal>
      )}
    </div>
  )
}
