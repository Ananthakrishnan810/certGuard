import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { Alert, Spinner } from '../components/ui'
import AuthShell from './AuthShell'

export default function Login() {
  const { login } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await login(email, password)
    } catch (err) {
      setError(errorMessage(err, 'Login failed'))
      setBusy(false)
    }
  }

  return (
    <AuthShell title="Sign in to CertGuard" subtitle="Track certificate expiry and get reminded on time">
      <form onSubmit={submit} className="space-y-4">
        {error && <Alert tone="error">{error}</Alert>}
        <div>
          <label className="label" htmlFor="email">Email</label>
          <input id="email" type="email" className="input" value={email} onChange={(e) => setEmail(e.target.value)} required autoFocus />
        </div>
        <div>
          <label className="label" htmlFor="password">Password</label>
          <input id="password" type="password" className="input" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </div>
        <button className="btn-primary w-full" disabled={busy}>{busy && <Spinner className="h-4 w-4" />} Sign in</button>
        <p className="text-center text-sm text-slate-500">
          No account? <Link to="/register" className="font-medium text-blue-600 hover:underline">Create one</Link>
        </p>
      </form>
    </AuthShell>
  )
}
