import { api } from './client'
import type {
  AuthResponse, Certificate, CertificateDetail, CertificateRequest, CertStatus, CertType,
  DashboardSummary, ExpiryCheckResult, ExtractionResult, PageResponse, Settings,
} from './types'

const multipart = (data: CertificateRequest, file?: File | null) => {
  const fd = new FormData()
  fd.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }))
  if (file) {
    fd.append('file', file)
  }
  return fd
}

export const authApi = {
  login: (email: string, password: string) =>
    api.post<AuthResponse>('/auth/login', { email, password }).then((r) => r.data),
  register: (name: string, email: string, password: string) =>
    api.post<AuthResponse>('/auth/register', { name, email, password }).then((r) => r.data),
}

export interface ListParams {
  q?: string
  type?: CertType | ''
  status?: CertStatus | ''
  archived?: boolean
  page?: number
  size?: number
  sort?: string
  dir?: 'asc' | 'desc'
}

export const certApi = {
  list: (p: ListParams) => {
    const params = Object.fromEntries(Object.entries(p).filter(([, v]) => v !== '' && v !== undefined))
    return api.get<PageResponse<Certificate>>('/certificates', { params }).then((r) => r.data)
  },
  get: (id: number | string) => api.get<CertificateDetail>(`/certificates/${id}`).then((r) => r.data),
  extract: (file: File, password?: string) => {
    const fd = new FormData()
    fd.append('file', file)
    if (password !== undefined) fd.append('password', password)
    return api.post<ExtractionResult>('/certificates/extract', fd).then((r) => r.data)
  },
  create: (data: CertificateRequest, file?: File | null) =>
    file
      ? api.post<Certificate>('/certificates', multipart(data, file)).then((r) => r.data)
      : api.post<Certificate>('/certificates', data).then((r) => r.data),
  update: (id: number, data: CertificateRequest) =>
    api.put<Certificate>(`/certificates/${id}`, data).then((r) => r.data),
  renew: (id: number, data: CertificateRequest, file: File) =>
    api.post<Certificate>(`/certificates/${id}/renew`, multipart(data, file)).then((r) => r.data),
  archive: (id: number) => api.post<Certificate>(`/certificates/${id}/archive`).then((r) => r.data),
  unarchive: (id: number) => api.post<Certificate>(`/certificates/${id}/unarchive`).then((r) => r.data),
  remove: (id: number) => api.delete(`/certificates/${id}`),
  fileBlob: (id: number, fileId?: number) =>
    api.get<Blob>(`/certificates/${id}/file`, { params: { fileId, inline: true }, responseType: 'blob' })
      .then((r) => r.data),
}

export const dashboardApi = {
  summary: () => api.get<DashboardSummary>('/dashboard/summary').then((r) => r.data),
}

export const settingsApi = {
  get: () => api.get<Settings>('/settings').then((r) => r.data),
  update: (s: { name: string; notifyEmail: string; defaultReminderDays: number[] }) =>
    api.put<Settings>('/settings', s).then((r) => r.data),
  testEmail: () =>
    api.post<{ sent: boolean; to: string; attempts: number; error: string | null }>('/settings/test-email')
      .then((r) => r.data),
  runCheck: () => api.post<ExpiryCheckResult>('/admin/run-expiry-check').then((r) => r.data),
}
