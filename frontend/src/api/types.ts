export type CertType = 'SSL_TLS' | 'ISO' | 'LICENSE' | 'INSURANCE' | 'TRAINING' | 'OTHER'
export type CertStatus = 'VALID' | 'EXPIRING_SOON' | 'EXPIRED'
export type DateSource = 'AUTO' | 'MANUAL'

export const CERT_TYPES: { value: CertType; label: string }[] = [
  { value: 'SSL_TLS', label: 'SSL/TLS' },
  { value: 'ISO', label: 'ISO' },
  { value: 'LICENSE', label: 'License' },
  { value: 'INSURANCE', label: 'Insurance' },
  { value: 'TRAINING', label: 'Training' },
  { value: 'OTHER', label: 'Other' },
]

export interface User {
  id: number
  name: string
  email: string
  role: 'USER' | 'ADMIN'
  notifyEmail: string
  defaultReminderDays: number[]
}

export interface AuthResponse {
  token: string
  user: User
}

export interface FileMeta {
  id: number
  certificateId: number
  fileName: string
  mimeType: string
  sizeBytes: number
  sha256: string
  version: number
  uploadedAt: string
}

export interface Certificate {
  id: number
  title: string
  type: CertType
  issuer: string | null
  certNumber: string | null
  issueDate: string | null
  expiryDate: string
  notifyEmail: string | null
  daysLeft: number
  status: CertStatus
  dateSource: DateSource
  reminderDays: number[]
  usesDefaultReminders: boolean
  remindersEnabled: boolean
  notes: string | null
  tags: string | null
  archived: boolean
  createdAt: string
  updatedAt: string
  latestFile: FileMeta | null
}

export interface NotificationEntry {
  id: number
  offsetDays: number
  expiryDate: string
  sentTo: string
  sentAt: string
  status: 'SENT' | 'FAILED'
  attempts: number
  error: string | null
}

export interface CertificateDetail {
  certificate: Certificate
  files: FileMeta[]
  notifications: NotificationEntry[]
}

export interface CertificateRequest {
  title: string
  type: CertType
  issuer: string | null
  certNumber: string | null
  issueDate: string | null
  expiryDate: string
  notifyEmail?: string | null
  dateSource: DateSource
  reminderDays: number[] | null
  remindersEnabled: boolean
  notes: string | null
  tags: string | null
}

export interface ExtractionResult {
  fileKind: string
  method: string
  datesFound: boolean
  requiresPassword: boolean
  title: string | null
  type: CertType | null
  issuer: string | null
  certNumber: string | null
  issueDate: string | null
  expiryDate: string | null
  message: string
  textSnippet: string | null
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface DashboardSummary {
  total: number
  valid: number
  expiringSoon: number
  expired: number
  archived: number
  upcoming: Certificate[]
}

export interface Settings {
  name: string
  email: string
  notifyEmail: string
  defaultReminderDays: number[]
  cron: string
  timezone: string
  mailConfigured: boolean
  mailSender: string
}

export interface ExpiryCheckResult {
  checked: number
  statusChanged: number
  sent: number
  failed: number
  skippedDisabled: number
}
