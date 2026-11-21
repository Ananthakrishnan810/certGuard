import axios, { AxiosError } from 'axios'

export const TOKEN_KEY = 'certguard.token'
export const USER_KEY = 'certguard.user'

export const api = axios.create({ baseURL: '/api' })

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  (r) => r,
  (error: AxiosError) => {
    // expired / invalid session: drop credentials and go to login
    if (error.response?.status === 401 && !error.config?.url?.startsWith('/auth/')) {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
      if (!window.location.pathname.startsWith('/login')) window.location.assign('/login')
    }
    return Promise.reject(error)
  },
)

/** Human readable message from an API error. */
export function errorMessage(err: unknown, fallback = 'Something went wrong'): string {
  if (axios.isAxiosError(err)) {
    const data = err.response?.data as { message?: string } | undefined
    if (data?.message) return data.message
    if (err.response?.status === 413) return 'File is too large (maximum 10 MB)'
    if (!err.response) return 'Cannot reach the server'
    return `${fallback} (HTTP ${err.response.status})`
  }
  return fallback
}
