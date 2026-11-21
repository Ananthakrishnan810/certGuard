import { CERT_TYPES, type Certificate, type CertificateRequest, type CertType, type DateSource } from '../api/types'
import ReminderDaysInput from './ReminderDaysInput'

export interface FormValues {
  title: string
  type: CertType
  issuer: string
  certNumber: string
  issueDate: string
  expiryDate: string
  notifyEmail: string
  notes: string
  tags: string
  remindersEnabled: boolean
  useDefaultReminders: boolean
  reminderDays: number[]
}

export function emptyForm(defaults: number[], defaultEmail: string = ''): FormValues {
  return {
    title: '', type: 'OTHER', issuer: '', certNumber: '', issueDate: '', expiryDate: '',
    notifyEmail: defaultEmail, notes: '', tags: '',
    remindersEnabled: true, useDefaultReminders: true, reminderDays: defaults,
  }
}

export function formFromCertificate(c: Certificate, defaultEmail: string = ''): FormValues {
  return {
    title: c.title, type: c.type, issuer: c.issuer ?? '', certNumber: c.certNumber ?? '',
    issueDate: c.issueDate ?? '', expiryDate: c.expiryDate,
    notifyEmail: c.notifyEmail ?? defaultEmail,
    notes: c.notes ?? '', tags: c.tags ?? '',
    remindersEnabled: c.remindersEnabled, useDefaultReminders: c.usesDefaultReminders, reminderDays: c.reminderDays,
  }
}

export function toRequest(v: FormValues, dateSource: DateSource): CertificateRequest {
  const t = (s: string) => (s.trim() === '' ? null : s.trim())
  return {
    title: v.title.trim(), type: v.type, issuer: t(v.issuer), certNumber: t(v.certNumber),
    issueDate: v.issueDate || null, expiryDate: v.expiryDate,
    notifyEmail: t(v.notifyEmail),
    dateSource,
    reminderDays: v.useDefaultReminders ? null : v.reminderDays,
    remindersEnabled: v.remindersEnabled, notes: t(v.notes), tags: t(v.tags),
  }
}

export function validate(v: FormValues): string | null {
  if (!v.title.trim()) return 'Certificate name is required'
  if (!v.expiryDate) return 'Expiry date is required'
  if (v.issueDate && v.issueDate > v.expiryDate) return 'Issue date must be on or before the expiry date'
  if (v.notifyEmail && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v.notifyEmail.trim())) return 'Please enter a valid email address for sending reminders'
  if (v.remindersEnabled && !v.useDefaultReminders && v.reminderDays.length === 0) return 'Add at least one reminder day'
  return null
}

type Section = 'details' | 'reminders'

export default function CertificateForm({ value, onChange, defaults, sections = ['details', 'reminders'] }: {
  value: FormValues
  onChange: (v: FormValues) => void
  defaults: number[]
  sections?: Section[]
}) {
  const set = <K extends keyof FormValues>(k: K, val: FormValues[K]) => onChange({ ...value, [k]: val })

  return (
    <div className="space-y-6">
      {sections.includes('details') && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div className="sm:col-span-2">
            <label className="label">Certificate Name *</label>
            <input className="input" placeholder="e.g. Production SSL Certificate, ISO 27001, Fleet Insurance" value={value.title} onChange={(e) => set('title', e.target.value)} maxLength={200} />
          </div>
          <div className="sm:col-span-2">
            <label className="label">Sending Person's Email Address (Notification Recipient)</label>
            <input
              type="email"
              className="input"
              placeholder="e.g. ethanhuntim10@gmail.com"
              value={value.notifyEmail}
              onChange={(e) => set('notifyEmail', e.target.value)}
              maxLength={190}
            />
            <p className="mt-1 text-xs text-slate-500">
              The email address where expiry reminders and alerts for this certificate will be sent.
            </p>
          </div>
          <div>
            <label className="label">Type *</label>
            <select className="input" value={value.type} onChange={(e) => set('type', e.target.value as CertType)}>
              {CERT_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
            </select>
          </div>
          <div>
            <label className="label">Certificate number</label>
            <input className="input" value={value.certNumber} onChange={(e) => set('certNumber', e.target.value)} maxLength={120} />
          </div>
          <div className="sm:col-span-2">
            <label className="label">Issuer</label>
            <input className="input" value={value.issuer} onChange={(e) => set('issuer', e.target.value)} maxLength={200} />
          </div>
          <div>
            <label className="label">Issue date</label>
            <input type="date" className="input" value={value.issueDate} onChange={(e) => set('issueDate', e.target.value)} />
          </div>
          <div>
            <label className="label">Expiry date *</label>
            <input type="date" className="input" value={value.expiryDate} onChange={(e) => set('expiryDate', e.target.value)} />
          </div>
          <div className="sm:col-span-2">
            <label className="label">Tags <span className="font-normal text-slate-400">(comma separated)</span></label>
            <input className="input" value={value.tags} onChange={(e) => set('tags', e.target.value)} maxLength={300} placeholder="e.g. plant-2, compliance" />
          </div>
          <div className="sm:col-span-2">
            <label className="label">Notes</label>
            <textarea className="input min-h-[80px]" value={value.notes} onChange={(e) => set('notes', e.target.value)} maxLength={5000} />
          </div>
        </div>
      )}

      {sections.includes('reminders') && (
        <div className="space-y-4">
          <label className="flex items-center gap-3">
            <input type="checkbox" className="h-4 w-4 rounded border-slate-300 text-blue-600"
              checked={value.remindersEnabled} onChange={(e) => set('remindersEnabled', e.target.checked)} />
            <span className="text-sm font-medium text-slate-700">Send email reminders for this certificate</span>
          </label>
          <div className={value.remindersEnabled ? '' : 'pointer-events-none opacity-50'}>
            <div className="mb-3 flex flex-col gap-2 sm:flex-row sm:gap-6">
              <label className="flex items-center gap-2 text-sm">
                <input type="radio" checked={value.useDefaultReminders} onChange={() => set('useDefaultReminders', true)} />
                Use my default ({defaults.length ? defaults.map((d) => (d === 0 ? 'expiry day' : `${d}d`)).join(', ') : 'none'})
              </label>
              <label className="flex items-center gap-2 text-sm">
                <input type="radio" checked={!value.useDefaultReminders}
                  onChange={() => onChange({ ...value, useDefaultReminders: false, reminderDays: value.reminderDays.length ? value.reminderDays : defaults })} />
                Custom for this certificate
              </label>
            </div>
            {!value.useDefaultReminders && (
              <ReminderDaysInput value={value.reminderDays} onChange={(d) => set('reminderDays', d)} />
            )}
            <p className="mt-3 text-xs text-slate-500">
              An email is also sent on the expiry day, then weekly while the certificate stays expired, until you renew or archive it.
            </p>
          </div>
        </div>
      )}
    </div>
  )
}
