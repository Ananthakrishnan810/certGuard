import { useState } from 'react'
import { errorMessage } from '../api/client'
import { certApi } from '../api/endpoints'
import type { CertificateRequest, ExtractionResult } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import CertificateForm, { toRequest, validate, type FormValues } from './CertificateForm'
import FileDrop from './FileDrop'
import { Alert, Spinner } from './ui'

const STEPS = ['Upload file', 'Confirm details', 'Reminders']

/**
 * Upload → auto-extract → confirm/edit → reminders → save.
 * Used for new certificates and for renewals (new version of an existing one).
 */
export default function UploadFlow({ initial, defaults, submitLabel, onSubmit, onCancel }: {
  initial: FormValues
  defaults: number[]
  submitLabel: string
  onSubmit: (req: CertificateRequest, file: File) => Promise<void>
  onCancel?: () => void
}) {
  const { user } = useAuth()
  const [step, setStep] = useState(0)
  const [file, setFile] = useState<File | null>(null)
  const [password, setPassword] = useState('')
  const [extraction, setExtraction] = useState<ExtractionResult | null>(null)
  const [form, setForm] = useState<FormValues>(() => ({
    ...initial,
    notifyEmail: initial.notifyEmail || user?.notifyEmail || user?.email || '',
  }))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [showText, setShowText] = useState(false)

  const isKeystore = !!file && /\.(p12|pfx)$/i.test(file.name)

  const runExtraction = async () => {
    if (!file) return
    setBusy(true)
    setError(null)
    try {
      const r = await certApi.extract(file, isKeystore ? password : undefined)
      setExtraction(r)
      if (r.requiresPassword) return // stay on step 0 and ask for the password
      setForm((f) => ({
        ...f,
        title: f.title || r.title || '',
        type: f.title ? f.type : (r.type ?? f.type),
        issuer: r.issuer ?? f.issuer,
        certNumber: r.certNumber ?? f.certNumber,
        issueDate: r.issueDate ?? (r.datesFound ? '' : f.issueDate),
        expiryDate: r.expiryDate ?? (r.datesFound ? '' : f.expiryDate),
      }))
      setStep(1)
    } catch (e) {
      setError(errorMessage(e, 'Could not read the file'))
    } finally {
      setBusy(false)
    }
  }

  const next = () => {
    if (step === 1) {
      const err = validate({ ...form, remindersEnabled: false })
      if (err) return setError(err)
    }
    setError(null)
    setStep((s) => s + 1)
  }

  const save = async () => {
    const err = validate(form)
    if (err) return setError(err)
    if (!file) return
    const auto = !!extraction?.datesFound
      && extraction.expiryDate === form.expiryDate
      && (extraction.issueDate ?? '') === form.issueDate
    setBusy(true)
    setError(null)
    try {
      await onSubmit(toRequest(form, auto ? 'AUTO' : 'MANUAL'), file)
    } catch (e) {
      setError(errorMessage(e, 'Could not save'))
      setBusy(false)
    }
  }

  return (
    <div>
      <ol className="mb-6 flex items-center gap-2 text-sm">
        {STEPS.map((s, i) => (
          <li key={s} className="flex items-center gap-2">
            <span className={`flex h-7 w-7 items-center justify-center rounded-full text-xs font-semibold
              ${i < step ? 'bg-blue-600 text-white' : i === step ? 'bg-blue-100 text-blue-700 ring-2 ring-blue-600' : 'bg-slate-100 text-slate-500'}`}>
              {i < step ? '✓' : i + 1}
            </span>
            <span className={i === step ? 'font-medium text-slate-900' : 'text-slate-500'}>{s}</span>
            {i < STEPS.length - 1 && <span className="mx-1 h-px w-6 bg-slate-300 sm:w-10" />}
          </li>
        ))}
      </ol>

      {error && <div className="mb-4"><Alert tone="error" onClose={() => setError(null)}>{error}</Alert></div>}

      {step === 0 && (
        <div className="space-y-4">
          <FileDrop file={file} onError={setError}
            onFile={(f) => { setFile(f); setExtraction(null); setPassword(''); setError(null) }} />
          {isKeystore && (
            <div>
              <label className="label">Keystore password</label>
              <input type="password" className="input max-w-sm" value={password} autoComplete="off"
                onChange={(e) => setPassword(e.target.value)} placeholder="Password of the .p12 / .pfx file" />
              <p className="mt-1 text-xs text-slate-500">Used only to read the dates; it is not stored.</p>
            </div>
          )}
          {extraction?.requiresPassword && <Alert tone="warning">{extraction.message}</Alert>}
          <div className="flex justify-end gap-2">
            {onCancel && <button className="btn-secondary" onClick={onCancel}>Cancel</button>}
            <button className="btn-primary" disabled={!file || busy} onClick={runExtraction}>
              {busy && <Spinner className="h-4 w-4" />} {busy ? 'Reading certificate…' : 'Read certificate'}
            </button>
          </div>
        </div>
      )}

      {step === 1 && (
        <div className="space-y-5">
          {extraction && (
            <Alert tone={extraction.datesFound ? 'success' : 'warning'}>
              <div className="font-medium">
                {extraction.datesFound ? 'Dates detected automatically' : 'Dates not detected'}
                <span className="ml-2 rounded bg-white/60 px-1.5 py-0.5 text-xs font-normal">{extraction.method}</span>
              </div>
              <div>{extraction.message}</div>
              {extraction.textSnippet && (
                <button type="button" className="mt-1 text-xs underline" onClick={() => setShowText((s) => !s)}>
                  {showText ? 'Hide' : 'Show'} extracted text
                </button>
              )}
              {showText && extraction.textSnippet && (
                <pre className="mt-2 max-h-48 overflow-auto whitespace-pre-wrap rounded bg-white/70 p-2 text-xs text-slate-700">{extraction.textSnippet}</pre>
              )}
            </Alert>
          )}

          <div className="rounded-lg border border-blue-200 bg-blue-50/70 p-3.5 text-sm text-blue-900 flex flex-wrap items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <span className="text-lg">📧</span>
              <div>
                <span className="font-semibold text-slate-800">Sending Person Email: </span>
                <span className="font-mono text-blue-800 font-medium">{form.notifyEmail || user?.notifyEmail || user?.email || 'Not specified'}</span>
              </div>
            </div>
            <span className="text-xs text-blue-700 bg-blue-100/70 px-2.5 py-1 rounded-full font-medium">
              Alerts will be sent here
            </span>
          </div>

          <CertificateForm value={form} onChange={setForm} defaults={defaults} sections={['details']} />
          <div className="flex justify-between">
            <button className="btn-secondary" onClick={() => setStep(0)}>Back</button>
            <button className="btn-primary" onClick={next}>Next</button>
          </div>
        </div>
      )}

      {step === 2 && (
        <div className="space-y-5">
          <CertificateForm value={form} onChange={setForm} defaults={defaults} sections={['reminders']} />
          <div className="flex justify-between">
            <button className="btn-secondary" onClick={() => setStep(1)} disabled={busy}>Back</button>
            <button className="btn-primary" onClick={save} disabled={busy}>
              {busy && <Spinner className="h-4 w-4" />} {submitLabel}
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
