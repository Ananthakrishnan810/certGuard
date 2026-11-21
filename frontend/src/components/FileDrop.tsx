import { useRef, useState } from 'react'
import { fmtBytes } from '../utils/format'

export const ACCEPT = '.pdf,.png,.jpg,.jpeg,.pem,.crt,.cer,.der,.p12,.pfx'
const MAX = 10 * 1024 * 1024

export default function FileDrop({ file, onFile, onError }: {
  file: File | null
  onFile: (f: File) => void
  onError: (msg: string) => void
}) {
  const input = useRef<HTMLInputElement>(null)
  const [over, setOver] = useState(false)

  const pick = (f: File | undefined) => {
    if (!f) return
    const ext = f.name.split('.').pop()?.toLowerCase() ?? ''
    if (!ACCEPT.split(',').includes('.' + ext)) return onError(`Unsupported file type .${ext}`)
    if (f.size > MAX) return onError('File is too large (maximum 10 MB)')
    onFile(f)
  }

  return (
    <div
      onClick={() => input.current?.click()}
      onDragOver={(e) => { e.preventDefault(); setOver(true) }}
      onDragLeave={() => setOver(false)}
      onDrop={(e) => { e.preventDefault(); setOver(false); pick(e.dataTransfer.files[0]) }}
      className={`flex cursor-pointer flex-col items-center justify-center rounded-xl border-2 border-dashed px-6 py-10 text-center transition
        ${over ? 'border-blue-500 bg-blue-50' : 'border-slate-300 bg-slate-50 hover:border-blue-400 hover:bg-blue-50/40'}`}
    >
      <input ref={input} type="file" accept={ACCEPT} className="hidden" onChange={(e) => pick(e.target.files?.[0])} />
      <svg className="mb-3 h-10 w-10 text-blue-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M12 16V4m0 0-4 4m4-4 4 4M4 16v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2" />
      </svg>
      {file ? (
        <>
          <p className="font-medium text-slate-800">{file.name}</p>
          <p className="text-sm text-slate-500">{fmtBytes(file.size)} · click to choose another file</p>
        </>
      ) : (
        <>
          <p className="font-medium text-slate-800">Drop a certificate here or click to browse</p>
          <p className="mt-1 text-sm text-slate-500">PDF, PNG, JPG, PEM, CRT, CER, DER, P12/PFX · max 10 MB</p>
        </>
      )}
    </div>
  )
}
