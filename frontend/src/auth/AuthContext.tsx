import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { TOKEN_KEY, USER_KEY } from '../api/client'
import { authApi } from '../api/endpoints'
import type { AuthResponse, User } from '../api/types'

interface AuthState {
  user: User | null
  login: (email: string, password: string) => Promise<void>
  register: (name: string, email: string, password: string) => Promise<void>
  logout: () => void
  updateUser: (patch: Partial<User>) => void
}

const AuthContext = createContext<AuthState | null>(null)

function readUser(): User | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw && localStorage.getItem(TOKEN_KEY) ? (JSON.parse(raw) as User) : null
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(readUser)

  const store = useCallback((r: AuthResponse) => {
    localStorage.setItem(TOKEN_KEY, r.token)
    localStorage.setItem(USER_KEY, JSON.stringify(r.user))
    setUser(r.user)
  }, [])

  const value = useMemo<AuthState>(() => ({
    user,
    login: async (email, password) => store(await authApi.login(email, password)),
    register: async (name, email, password) => store(await authApi.register(name, email, password)),
    logout: () => {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
      setUser(null)
    },
    updateUser: (patch) => setUser((u) => {
      if (!u) return u
      const next = { ...u, ...patch }
      localStorage.setItem(USER_KEY, JSON.stringify(next))
      return next
    }),
  }), [user, store])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth outside AuthProvider')
  return ctx
}
