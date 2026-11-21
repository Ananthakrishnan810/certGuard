import { useState } from 'react'

const PRESETS = [90, 60, 30, 15, 7, 3, 1, 0]

/** Editable list of "days before expiry" offsets. 0 means "on the expiry day". */
export default function ReminderDaysInput({ value, onChange, disabled }: {
  value: number[]
  onChange: (v: number[]) => void
  disabled?: boolean
}) {
  const [draft, setDraft] = useState('')
  const sorted = [...value].sort((a, b) => b - a)

  const add = (n: number) => {
    if (!Number.isInteger(n) || n < 0 || n > 3650 || value.includes(n)) return
    onChange([...value, n].sort((a, b) => b - a))
  }
  const remove = (n: number) => onChange(value.filter((x) => x !== n))

  return (
    <div className={disabled ? 'pointer-events-none opacity-50' : ''}>
      <div className="flex min-h-[2.5rem] flex-wrap items-center gap-2 rounded-md border border-slate-300 bg-white p-2">
        {sorted.length === 0 && <span className="px-1 text-sm text-slate-400">No reminders</span>}
        {sorted.map((n) => (
          <span key={n} className="inline-flex items-center gap-1 rounded-full bg-blue-50 px-2.5 py-1 text-xs font-medium text-blue-700">
            {n === 0 ? 'On expiry day' : `${n} day${n === 1 ? '' : 's'}`}
            <button type="button" onClick={() => remove(n)} className="ml-0.5 text-blue-400 hover:text-blue-700" aria-label={`Remove ${n}`}>✕</button>
          </span>
        ))}
        <input
          type="number" min={0} max={3650} placeholder="Add days…" value={draft}
          className="w-28 flex-none border-0 p-1 text-sm focus:outline-none focus:ring-0"
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ',') {
              e.preventDefault()
              if (draft.trim() !== '') add(Number(draft))
              setDraft('')
            }
          }}
          onBlur={() => { if (draft.trim() !== '') add(Number(draft)); setDraft('') }}
        />
      </div>
      <div className="mt-2 flex flex-wrap gap-1.5">
        {PRESETS.filter((p) => !value.includes(p)).map((p) => (
          <button key={p} type="button" onClick={() => add(p)}
            className="rounded-full border border-dashed border-slate-300 px-2 py-0.5 text-xs text-slate-500 hover:border-blue-400 hover:text-blue-600">
            + {p === 0 ? 'expiry day' : `${p}d`}
          </button>
        ))}
      </div>
    </div>
  )
}
