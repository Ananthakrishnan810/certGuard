import { useEffect, useState, type FormEvent } from 'react'
import { errorMessage } from '../api/client'
import { settingsApi } from '../api/endpoints'
import type { Settings as SettingsT } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import ReminderDaysInput from '../components/ReminderDaysInput'
import { Alert, PageHeader, PageLoader, Spinner } from '../components/ui'

export default function Settings() {
  const { updateUser } = useAuth()
  const [s, setS] = useState<SettingsT | null>(null)
  const [name, setName] = useState('')
  const [notifyEmail, setNotifyEmail] = useState('')
  const [days, setDays] = useState<number[]>([])
  const [busy, setBusy] = useState<'' | 'save' | 'test' | 'run'>('')
  const [msg, setMsg] = useState<{ tone: 'success' | 'error' | 'info'; text: string } | null>(null)

  useEffect(() => {
    settingsApi.get().then((r) => {
      setS(r)
      setName(r.name)
      setNotifyEmail(r.notifyEmail)
      setDays(r.defaultReminderDays)
    }).catch((e) => setMsg({ tone: 'error', text: errorMessage(e, 'Could not load settings') }))
  }, [])

  if (!s) return msg ? <Alert tone="error">{msg.text}</Alert> : <PageLoader />

  const save = async (e: FormEvent) => {
    e.preventDefault()
    if (days.length === 0) return setMsg({ tone: 'error', text: 'Add at least one reminder day' })
    setBusy('save')
    try {
      const r = await settingsApi.update({ name, notifyEmail, defaultReminderDays: days })
      setS(r)
      updateUser({ name: r.name, notifyEmail: r.notifyEmail, defaultReminderDays: r.defaultReminderDays })
      setMsg({ tone: 'success', text: 'Settings saved' })
    } catch (err) {
      setMsg({ tone: 'error', text: errorMessage(err, 'Could not save settings') })
    } finally {
      setBusy('')
    }
  }

  const test = async () => {
    setBusy('test')
    try {
      const r = await settingsApi.testEmail()
      setMsg(r.sent
        ? { tone: 'success', text: `Test email sent to ${r.to}` }
        : { tone: 'error', text: `Test email to ${r.to} failed: ${r.error}` })
    } catch (err) {
      setMsg({ tone: 'error', text: errorMessage(err, 'Test email failed') })
    } finally {
      setBusy('')
    }
  }

  const run = async () => {
    setBusy('run')
    try {
      const r = await settingsApi.runCheck()
      setMsg({ tone: 'info', text: `Checked ${r.checked}: ${r.sent} sent, ${r.failed} failed, ${r.skippedDisabled} with reminders off, ${r.statusChanged} status change(s).` })
    } catch (err) {
      setMsg({ tone: 'error', text: errorMessage(err, 'Expiry check failed') })
    } finally {
      setBusy('')
    }
  }

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader title="Settings" />
      {msg && <div className="mb-4"><Alert tone={msg.tone} onClose={() => setMsg(null)}>{msg.text}</Alert></div>}

      <form onSubmit={save} className="card mb-6 space-y-5 p-6">
        <h2 className="font-semibold">Profile & notifications</h2>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <label className="label">Name</label>
            <input className="input" value={name} onChange={(e) => setName(e.target.value)} required />
          </div>
          <div>
            <label className="label">Login email</label>
            <input className="input" value={s.email} disabled />
          </div>
          <div className="sm:col-span-2">
            <label className="label">Send reminders to</label>
            <input type="email" className="input" value={notifyEmail} onChange={(e) => setNotifyEmail(e.target.value)} placeholder={s.email} />
            <p className="mt-1 text-xs text-slate-500">Leave empty to use your login email.</p>
          </div>
        </div>
        <div>
          <label className="label">Default reminder days (before expiry)</label>
          <ReminderDaysInput value={days} onChange={setDays} />
          <p className="mt-1 text-xs text-slate-500">Used by every certificate that doesn't have its own reminder days.</p>
        </div>
        <div className="flex justify-end">
          <button className="btn-primary" disabled={busy !== ''}>{busy === 'save' && <Spinner className="h-4 w-4" />} Save settings</button>
        </div>
      </form>

      <section className="card space-y-4 p-6">
        <h2 className="font-semibold">Email delivery & schedule</h2>
        <dl className="grid grid-cols-3 gap-y-2 text-sm">
          <dt className="text-slate-500">Mail server</dt>
          <dd className="col-span-2">
            {s.mailConfigured
              ? <span className="text-emerald-700">Configured — sending as {s.mailSender}</span>
              : <span className="text-red-600">Not configured — set MAIL_USERNAME and MAIL_PASSWORD (Gmail App Password) in .env</span>}
          </dd>
          <dt className="text-slate-500">Daily check</dt>
          <dd className="col-span-2"><code className="rounded bg-slate-100 px-1.5 py-0.5">{s.cron}</code> ({s.timezone})</dd>
        </dl>
        <div className="flex flex-wrap gap-2">
          <button className="btn-secondary" onClick={test} disabled={busy !== ''}>{busy === 'test' && <Spinner className="h-4 w-4" />} Send test email</button>
          <button className="btn-secondary" onClick={run} disabled={busy !== ''}>{busy === 'run' && <Spinner className="h-4 w-4" />} Run expiry check now</button>
        </div>
      </section>
    </div>
  )
}
