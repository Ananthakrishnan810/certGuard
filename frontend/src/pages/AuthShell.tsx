import type { ReactNode } from 'react'

export default function AuthShell({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-slate-50 to-blue-50 px-4">
      <div className="w-full max-w-sm">
        <div className="mb-6 text-center">
          <img src="/favicon.svg" alt="" className="mx-auto mb-3 h-12 w-12" />
          <h1 className="text-2xl font-semibold text-slate-900">{title}</h1>
          <p className="mt-1 text-sm text-slate-500">{subtitle}</p>
        </div>
        <div className="card p-6">{children}</div>
      </div>
    </div>
  )
}
