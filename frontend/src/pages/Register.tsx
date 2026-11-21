import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { Alert, Spinner } from '../components/ui'
import AuthShell from './AuthShell'

export default function Register() {
  const { register } = useAuth()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (password.length < 8) return setError('Password must be at least 8 characters')
    if (password !== confirm) return setError('Passwords do not match')
    setBusy(true)
    setError(null)
    try {
      await register(name, email, password)
    } catch (err) {
      setError(errorMessage(err, 'Registration failed'))
      setBusy(false)
    }
  }

  return (
    <AuthShell title="Create your account" subtitle="Reminders go to this email unless you change it in Settings">
      <form onSubmit={submit} className="space-y-4">
        {error && <Alert tone="error">{error}</Alert>}
        <div>
          <label className="label" htmlFor="name">Name</label>
          <input id="name" className="input" value={name} onChange={(e) => setName(e.target.value)} required autoFocus />
        </div>
        <div>
          <label className="label" htmlFor="email">Email</label>
          <input id="email" type="email" className="input" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </div>
        <div>
          <label className="label" htmlFor="password">Password</label>
          <input id="password" type="password" className="input" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={8} />
        </div>
        <div>
          <label className="label" htmlFor="confirm">Confirm password</label>
          <input id="confirm" type="password" className="input" value={confirm} onChange={(e) => setConfirm(e.target.value)} required />
        </div>
        <button className="btn-primary w-full" disabled={busy}>{busy && <Spinner className="h-4 w-4" />} Create account</button>
        <p className="text-center text-sm text-slate-500">
          Already registered? <Link to="/login" className="font-medium text-blue-600 hover:underline">Sign in</Link>
        </p>
      </form>
    </AuthShell>
  )
}
