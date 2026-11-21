import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { errorMessage } from '../api/client'
import { certApi } from '../api/endpoints'
import type { CertificateDetail as Detail, FileMeta } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import CertificateForm, { formFromCertificate, toRequest, validate, type FormValues } from '../components/CertificateForm'
import FilePreview, { downloadFile } from '../components/FilePreview'
import StatusBadge from '../components/StatusBadge'
import UploadFlow from '../components/UploadFlow'
import { Alert, Modal, PageLoader, Spinner } from '../components/ui'
import { daysText, fmtBytes, fmtDate, fmtDateTime, offsetText, typeLabel } from '../utils/format'

export default function CertificateDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { user } = useAuth()
  const defaults = user?.defaultReminderDays ?? [30, 15, 7, 1]

  const [data, setData] = useState<Detail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [previewFile, setPreviewFile] = useState<FileMeta | null>(null)
  const [editing, setEditing] = useState<FormValues | null>(null)
  const [renewing, setRenewing] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => {
    certApi.get(id!).then((d) => {
      setData(d)
      setPreviewFile((p) => d.files.find((f) => f.id === p?.id) ?? d.files[0] ?? null)
    }).catch((e) => setError(errorMessage(e, 'Could not load certificate')))
  }, [id])
  useEffect(load, [load])

  if (!data) return error ? <Alert tone="error">{error}</Alert> : <PageLoader />
  const c = data.certificate

  const act = async (fn: () => Promise<unknown>, msg: string) => {
    setBusy(true)
    setError(null)
    try {
      await fn()
      setNotice(msg)
      load()
    } catch (e) {
      setError(errorMessage(e, 'Action failed'))
    } finally {
      setBusy(false)
    }
  }

  const saveEdit = async () => {
    if (!editing) return
    const err = validate(editing)
    if (err) return setError(err)
    const datesChanged = editing.expiryDate !== c.expiryDate || editing.issueDate !== (c.issueDate ?? '')
    await act(() => certApi.update(c.id, toRequest(editing, datesChanged ? 'MANUAL' : c.dateSource)), 'Certificate updated')
    setEditing(null)
  }

  return (
    <div>
      <div className="mb-2 text-sm"><Link to="/certificates" className="text-slate-500 hover:underline">← Certificates</Link></div>
      <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{c.title}</h1>
            <StatusBadge status={c.status} archived={c.archived} />
          </div>
          <p className={`mt-1 text-sm ${c.daysLeft < 0 ? 'text-red-600' : 'text-slate-500'}`}>
            {typeLabel(c.type)} · {daysText(c.daysLeft)} ({fmtDate(c.expiryDate)})
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button className="btn-secondary" onClick={() => { setEditing(formFromCertificate(c)); setError(null) }} disabled={busy}>Edit</button>
          <button className="btn-primary" onClick={() => setRenewing(true)} disabled={busy}>Mark renewed / upload new version</button>
          {c.archived
            ? <button className="btn-secondary" disabled={busy} onClick={() => act(() => certApi.unarchive(c.id), 'Certificate restored')}>Unarchive</button>
            : <button className="btn-secondary" disabled={busy} onClick={() => act(() => certApi.archive(c.id), 'Certificate archived — no more reminders')}>Archive</button>}
          <button className="btn-danger" onClick={() => setConfirmDelete(true)} disabled={busy}>Delete</button>
        </div>
      </div>

      {error && <div className="mb-4"><Alert tone="error" onClose={() => setError(null)}>{error}</Alert></div>}
      {notice && <div className="mb-4"><Alert tone="success" onClose={() => setNotice(null)}>{notice}</Alert></div>}

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-5">
        <div className="space-y-6 lg:col-span-2">
          <section className="card p-5">
            <h2 className="mb-4 font-semibold">Details</h2>
            <dl className="grid grid-cols-3 gap-x-4 gap-y-3 text-sm">
              <dt className="text-slate-500">Issuer</dt><dd className="col-span-2">{c.issuer ?? '—'}</dd>
              <dt className="text-slate-500">Number</dt><dd className="col-span-2 break-all">{c.certNumber ?? '—'}</dd>
              <dt className="text-slate-500">Issued</dt><dd className="col-span-2">{fmtDate(c.issueDate)}</dd>
              <dt className="text-slate-500">Expires</dt><dd className="col-span-2 font-medium">{fmtDate(c.expiryDate)}</dd>
              <dt className="text-slate-500">Dates</dt>
              <dd className="col-span-2">
                <span className={`rounded px-1.5 py-0.5 text-xs ${c.dateSource === 'AUTO' ? 'bg-blue-50 text-blue-700' : 'bg-slate-100 text-slate-600'}`}>
                  {c.dateSource === 'AUTO' ? 'Read from certificate' : 'Entered manually'}
                </span>
              </dd>
              <dt className="text-slate-500">Sending person</dt>
              <dd className="col-span-2 font-medium text-slate-800">{c.notifyEmail || user?.notifyEmail || user?.email || '—'}</dd>
              <dt className="text-slate-500">Tags</dt><dd className="col-span-2">{c.tags ?? '—'}</dd>
              <dt className="text-slate-500">Notes</dt><dd className="col-span-2 whitespace-pre-wrap">{c.notes ?? '—'}</dd>
            </dl>
          </section>

          <section className="card p-5">
            <h2 className="mb-3 font-semibold">Reminders</h2>
            {c.remindersEnabled ? (
              <>
                <div className="flex flex-wrap gap-1.5">
                  {c.reminderDays.map((d) => (
                    <span key={d} className="rounded-full bg-blue-50 px-2.5 py-1 text-xs font-medium text-blue-700">
                      {d === 0 ? 'On expiry day' : `${d} day${d === 1 ? '' : 's'} before`}
                    </span>
                  ))}
                </div>
                <p className="mt-2 text-xs text-slate-500">
                  {c.usesDefaultReminders ? 'Using your default reminder days. ' : 'Custom for this certificate. '}
                  Sent to {user?.notifyEmail}. Weekly reminders continue after expiry until renewed or archived.
                </p>
              </>
            ) : <p className="text-sm text-slate-500">Reminders are turned off for this certificate.</p>}
          </section>

          <section className="card p-5">
            <h2 className="mb-3 font-semibold">Stored files</h2>
            {data.files.length === 0 ? (
              <p className="text-sm text-slate-500">No document attached.</p>
            ) : (
              <ul className="divide-y divide-slate-100 text-sm">
                {data.files.map((f) => (
                  <li key={f.id} className={`flex items-center gap-3 py-2 ${previewFile?.id === f.id ? 'font-medium' : ''}`}>
                    <span className="rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-600">v{f.version}</span>
                    <button className="min-w-0 flex-1 truncate text-left hover:text-blue-600" onClick={() => setPreviewFile(f)} title={f.fileName}>
                      {f.fileName}
                    </button>
                    <span className="text-xs text-slate-400">{fmtBytes(f.sizeBytes)}</span>
                    <button className="text-xs text-blue-600 hover:underline" onClick={() => downloadFile(c.id, f).catch((e) => setError(errorMessage(e, 'Download failed')))}>
                      Download
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>

        <div className="space-y-6 lg:col-span-3">
          <section className="card p-5">
            <div className="mb-3 flex items-center justify-between">
              <h2 className="font-semibold">Preview</h2>
              {previewFile && <span className="text-xs text-slate-500">v{previewFile.version} · uploaded {fmtDateTime(previewFile.uploadedAt)}</span>}
            </div>
            {previewFile ? <FilePreview certId={c.id} file={previewFile} /> : <p className="text-sm text-slate-500">No file stored.</p>}
          </section>

          <section className="card overflow-hidden">
            <h2 className="border-b border-slate-200 px-5 py-3 font-semibold">Notification history</h2>
            {data.notifications.length === 0 ? (
              <p className="px-5 py-6 text-sm text-slate-500">No reminders sent yet.</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="min-w-full text-sm">
                  <thead className="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
                    <tr><th className="px-4 py-2">When</th><th className="px-4 py-2">Reminder</th><th className="px-4 py-2">To</th><th className="px-4 py-2">Result</th></tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {data.notifications.map((n) => (
                      <tr key={n.id}>
                        <td className="whitespace-nowrap px-4 py-2">{fmtDateTime(n.sentAt)}</td>
                        <td className="whitespace-nowrap px-4 py-2">{offsetText(n.offsetDays)}</td>
                        <td className="px-4 py-2">{n.sentTo}</td>
                        <td className="px-4 py-2">
                          {n.status === 'SENT'
                            ? <span className="text-emerald-700">Sent</span>
                            : <span className="text-red-600" title={n.error ?? ''}>Failed ({n.attempts} tries){n.error ? ` — ${n.error.slice(0, 80)}` : ''}</span>}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </div>
      </div>

      {editing && (
        <Modal title="Edit certificate" onClose={() => setEditing(null)} wide>
          <CertificateForm value={editing} onChange={setEditing} defaults={defaults} />
          <div className="mt-6 flex justify-end gap-2">
            <button className="btn-secondary" onClick={() => setEditing(null)}>Cancel</button>
            <button className="btn-primary" onClick={saveEdit} disabled={busy}>{busy && <Spinner className="h-4 w-4" />} Save changes</button>
          </div>
        </Modal>
      )}

      {renewing && (
        <Modal title="Renew certificate" onClose={() => setRenewing(false)} wide>
          <p className="mb-4 text-sm text-slate-500">Upload the renewed certificate. The old copy is kept as an earlier version and reminders restart for the new expiry date.</p>
          <UploadFlow
            initial={{ ...formFromCertificate(c), issueDate: '', expiryDate: '' }}
            defaults={defaults}
            submitLabel="Save renewal"
            onCancel={() => setRenewing(false)}
            onSubmit={async (req, file) => {
              await certApi.renew(c.id, req, file)
              setRenewing(false)
              setNotice('Renewal saved — new version stored')
              setPreviewFile(null)
              load()
            }}
          />
        </Modal>
      )}

      {confirmDelete && (
        <Modal title="Delete certificate?" onClose={() => setConfirmDelete(false)}>
          <p className="text-sm text-slate-600">
            This permanently deletes <strong>{c.title}</strong>, all {data.files.length} stored file version(s) and its notification history. Archive it instead if you only want to stop reminders.
          </p>
          <div className="mt-6 flex justify-end gap-2">
            <button className="btn-secondary" onClick={() => setConfirmDelete(false)}>Cancel</button>
            <button className="btn-danger" disabled={busy} onClick={async () => {
              setBusy(true)
              try {
                await certApi.remove(c.id)
                navigate('/certificates', { replace: true })
              } catch (e) {
                setError(errorMessage(e, 'Delete failed'))
                setConfirmDelete(false)
                setBusy(false)
              }
            }}>Delete permanently</button>
          </div>
        </Modal>
      )}
    </div>
  )
}
