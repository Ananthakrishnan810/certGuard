import { useEffect, useState } from 'react'
import { certApi } from '../api/endpoints'
import type { FileMeta } from '../api/types'
import { Spinner } from './ui'

/** Fetches the stored copy with the auth header and shows PDFs/images inline. */
export default function FilePreview({ certId, file }: { certId: number; file: FileMeta }) {
  const [url, setUrl] = useState<string | null>(null)
  const [failed, setFailed] = useState(false)
  const previewable = file.mimeType === 'application/pdf' || file.mimeType.startsWith('image/')

  useEffect(() => {
    if (!previewable) return
    let objectUrl: string | null = null
    let cancelled = false
    setUrl(null)
    setFailed(false)
    certApi.fileBlob(certId, file.id)
      .then((blob) => {
        if (cancelled) return
        objectUrl = URL.createObjectURL(new Blob([blob], { type: file.mimeType }))
        setUrl(objectUrl)
      })
      .catch(() => !cancelled && setFailed(true))
    return () => {
      cancelled = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [certId, file.id, file.mimeType, previewable])

  if (!previewable) {
    return (
      <div className="flex h-64 flex-col items-center justify-center rounded-lg bg-slate-50 text-sm text-slate-500">
        <span className="mb-1 text-3xl">🔐</span>
        No preview for {file.fileName.split('.').pop()?.toUpperCase()} files — use Download.
      </div>
    )
  }
  if (failed) return <div className="flex h-64 items-center justify-center text-sm text-red-600">Could not load preview</div>
  if (!url) return <div className="flex h-64 items-center justify-center text-blue-600"><Spinner className="h-7 w-7" /></div>
  return file.mimeType === 'application/pdf'
    ? <iframe src={url} title={file.fileName} className="h-[480px] w-full rounded-lg border border-slate-200" />
    : <img src={url} alt={file.fileName} className="max-h-[480px] w-full rounded-lg border border-slate-200 object-contain" />
}

export async function downloadFile(certId: number, file: FileMeta) {
  const blob = await certApi.fileBlob(certId, file.id)
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = file.fileName
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
