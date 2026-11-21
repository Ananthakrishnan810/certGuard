import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

const NAV = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/certificates', label: 'Certificates', end: true },
  { to: '/certificates/new', label: 'Upload', end: true },
  { to: '/settings', label: 'Settings', end: true },
]

export default function Layout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-40 border-b border-slate-200 bg-white/90 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-6xl items-center gap-6 px-4">
          <NavLink to="/" className="flex items-center gap-2 font-semibold text-slate-900">
            <img src="/favicon.svg" alt="" className="h-6 w-6" />
            CertGuard
          </NavLink>
          <nav className="flex flex-1 gap-1 overflow-x-auto">
            {NAV.map((n) => (
              <NavLink key={n.to} to={n.to} end={n.end}
                className={({ isActive }) => `whitespace-nowrap rounded-md px-3 py-1.5 text-sm font-medium ${isActive ? 'bg-blue-50 text-blue-700' : 'text-slate-600 hover:bg-slate-100'}`}>
                {n.label}
              </NavLink>
            ))}
          </nav>
          <div className="flex items-center gap-3">
            <div className="hidden text-right sm:block">
              <div className="text-sm font-medium leading-tight text-slate-800">{user?.name}</div>
              <div className="text-xs leading-tight text-slate-500">{user?.email}{user?.role === 'ADMIN' ? ' · admin' : ''}</div>
            </div>
            <button className="btn-secondary px-3 py-1.5" onClick={() => { logout(); navigate('/login') }}>Log out</button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  )
}
