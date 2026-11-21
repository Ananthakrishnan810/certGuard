import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { errorMessage } from '../api/client'
import { certApi } from '../api/endpoints'
import type { ExtractionResult } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import CertificateForm, { emptyForm, toRequest, validate, type FormValues } from '../components/CertificateForm'
import FileDrop from '../components/FileDrop'
import { Alert, PageHeader, Spinner } from '../components/ui'
import { fmtBytes } from '../utils/format'

export default function Upload() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const defaults = user?.defaultReminderDays ?? [30, 15, 7, 1]
  const userEmail = user?.notifyEmail || user?.email || ''

  const [form, setForm] = useState<FormValues>(() => emptyForm(defaults, userEmail))
  const [file, setFile] = useState<File | null>(null)
  const [reading, setReading] = useState(false)
  const [keystorePassword, setKeystorePassword] = useState('')
  const [extraction, setExtraction] = useState<ExtractionResult | null>(null)
  const [showExtractedText, setShowExtractedText] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const extractFromFile = async (f: File, password?: string) => {
    setReading(true)
    setError(null)
    const isKeystore = /\.(p12|pfx)$/i.test(f.name)
    try {
      const res = await certApi.extract(f, isKeystore ? password : undefined)
      setExtraction(res)
      if (!res.requiresPassword) {
        setForm((prev) => ({
          ...prev,
          title: res.title || prev.title || f.name.replace(/\.[^/.]+$/, ''),
          type: res.type ?? prev.type,
          issuer: res.issuer ?? prev.issuer,
          certNumber: res.certNumber ?? prev.certNumber,
          issueDate: res.issueDate ?? (res.datesFound ? '' : prev.issueDate),
          expiryDate: res.expiryDate ?? (res.datesFound ? '' : prev.expiryDate),
        }))
      }
    } catch (err) {
      setError(errorMessage(err, 'Could not read or extract data from the document'))
    } finally {
      setReading(false)
    }
  }

  const handleFileSelect = (selected: File) => {
    setFile(selected)
    setExtraction(null)
    setKeystorePassword('')
    extractFromFile(selected)
  }

  const handleRemoveFile = () => {
    setFile(null)
    setExtraction(null)
    setKeystorePassword('')
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    const err = validate(form)
    if (err) {
      setError(err)
      return
    }
    setSaving(true)
    setError(null)

    const auto = !!extraction?.datesFound
      && extraction.expiryDate === form.expiryDate
      && (extraction.issueDate ?? '') === form.issueDate

    try {
      const req = toRequest(form, auto ? 'AUTO' : 'MANUAL')
      const created = await certApi.create(req, file)
      navigate(`/certificates/${created.id}`, { replace: true })
    } catch (err) {
      setError(errorMessage(err, 'Could not save certificate'))
      setSaving(false)
    }
  }

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        title="Add certificate"
        subtitle="Upload a document to automatically populate details, or fill out the form manually."
      />

      {error && <div className="mb-4"><Alert tone="error" onClose={() => setError(null)}>{error}</Alert></div>}

      <form onSubmit={handleSubmit} className="space-y-6">
        {/* Document Upload Area at the TOP */}
        <div className="card p-6">
          <div className="mb-3 flex items-center justify-between">
            <div>
              <h2 className="text-base font-semibold text-slate-900">Document Upload (Optional)</h2>
              <p className="text-xs text-slate-500">
                Upload a certificate or document to automatically detect the certificate name, issue date, and expiry date.
              </p>
            </div>
            {reading && (
              <span className="flex items-center gap-1.5 text-xs font-medium text-blue-600">
                <Spinner className="h-3.5 w-3.5" /> Reading document…
              </span>
            )}
          </div>

          {!file ? (
            <FileDrop
              file={null}
              onFile={handleFileSelect}
              onError={(msg) => setError(msg)}
            />
          ) : (
            <div className="space-y-3">
              <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-blue-200 bg-blue-50/60 px-4 py-3">
                <div className="flex items-center gap-3">
                  <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-blue-600 text-white">
                    <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                    </svg>
                  </div>
                  <div>
                    <div className="text-sm font-semibold text-slate-900">{file.name}</div>
                    <div className="text-xs text-slate-500">{fmtBytes(file.size)} · Digital copy attached</div>
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  <label className="btn-secondary cursor-pointer py-1 text-xs">
                    Change file
                    <input
                      type="file"
                      className="hidden"
                      onChange={(e) => {
                        const f = e.target.files?.[0]
                        if (f) handleFileSelect(f)
                      }}
                    />
                  </label>
                  <button
                    type="button"
                    onClick={handleRemoveFile}
                    className="rounded px-2.5 py-1 text-xs font-medium text-red-600 hover:bg-red-50 hover:text-red-700"
                  >
                    Remove
                  </button>
                </div>
              </div>

              {/* Extraction result alerts */}
              {extraction?.requiresPassword && (
                <div className="rounded-lg border border-amber-200 bg-amber-50 p-4">
                  <div className="mb-2 text-sm font-medium text-amber-900">Keystore password required</div>
                  <p className="mb-3 text-xs text-amber-700">This .p12/.pfx file is password-protected. Enter the password to extract dates and certificate details:</p>
                  <div className="flex max-w-sm gap-2">
                    <input
                      type="password"
                      placeholder="Keystore password"
                      className="input text-sm"
                      value={keystorePassword}
                      onChange={(e) => setKeystorePassword(e.target.value)}
                    />
                    <button
                      type="button"
                      className="btn-primary text-xs"
                      onClick={() => extractFromFile(file, keystorePassword)}
                      disabled={reading}
                    >
                      {reading ? 'Unlocking…' : 'Unlock'}
                    </button>
                  </div>
                </div>
              )}

              {extraction && !extraction.requiresPassword && (
                <Alert tone={extraction.datesFound ? 'success' : 'warning'}>
                  <div className="flex items-center justify-between">
                    <div className="font-medium">
                      {extraction.datesFound ? 'Dates detected automatically' : 'Dates not detected automatically'}
                      <span className="ml-2 rounded bg-white/60 px-1.5 py-0.5 text-xs font-normal">{extraction.method}</span>
                    </div>
                  </div>
                  <div className="mt-0.5 text-xs">{extraction.message}</div>
                  {extraction.textSnippet && (
                    <div className="mt-2">
                      <button
                        type="button"
                        className="text-xs font-medium underline"
                        onClick={() => setShowExtractedText((v) => !v)}
                      >
                        {showExtractedText ? 'Hide extracted text' : 'Show extracted text'}
                      </button>
                      {showExtractedText && (
                        <pre className="mt-1.5 max-h-36 overflow-auto whitespace-pre-wrap rounded bg-white/70 p-2 text-xs text-slate-700">
                          {extraction.textSnippet}
                        </pre>
                      )}
                    </div>
                  )}
                </Alert>
              )}
            </div>
          )}
        </div>

        {/* Certificate Details Form */}
        <div className="card p-6 space-y-6">
          <div className="border-b border-slate-100 pb-3">
            <h2 className="text-lg font-semibold text-slate-900">Certificate Details</h2>
            <p className="text-sm text-slate-500">
              Review or fill in the details below. Required fields are marked with an asterisk (*).
            </p>
          </div>

          {/* Sending Person Email Banner */}
          <div className="rounded-lg border border-blue-200 bg-blue-50/70 p-3.5 text-sm text-blue-900 flex flex-wrap items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <span className="text-lg">📧</span>
              <div>
                <span className="font-semibold text-slate-800">Sending Person Email: </span>
                <span className="font-mono text-blue-800 font-medium">{form.notifyEmail || userEmail || 'Not specified'}</span>
              </div>
            </div>
            <span className="text-xs text-blue-700 bg-blue-100/70 px-2.5 py-1 rounded-full font-medium">
              Alerts will be sent here
            </span>
          </div>

          <CertificateForm
            value={form}
            onChange={setForm}
            defaults={defaults}
            sections={['details', 'reminders']}
          />

          <div className="flex items-center justify-end gap-3 border-t border-slate-100 pt-4">
            <button
              type="button"
              className="btn-secondary"
              onClick={() => navigate(-1)}
              disabled={saving}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="btn-primary"
              disabled={saving}
            >
              {saving && <Spinner className="h-4 w-4" />}
              Save certificate
            </button>
          </div>
        </div>
      </form>
    </div>
  )
}
