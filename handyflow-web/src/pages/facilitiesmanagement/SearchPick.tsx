// A drop-down that grows a filter box once the list is long (clients and sites), and is otherwise the plain Pick.
import { useState } from "react"
import { Field, inp } from "./kitBase"

export const SEARCH_FROM = 8

/** The options whose label contains what was typed, always keeping the one already chosen. */
export function filterOptions(options: readonly (readonly [string, string])[], typed: string, chosen: string): (readonly [string, string])[] {
  const q = typed.trim().toLowerCase()
  if (!q) return [...options]
  return options.filter(([v, l]) => v === chosen || l.toLowerCase().includes(q))
}

export default function SearchPick({ label, value, onChange, options, blank }: { label: string; value: string; onChange: (v: string) => void; options: readonly (readonly [string, string])[]; blank?: string }) {
  const [q, setQ] = useState("")
  const many = options.length >= SEARCH_FROM
  const shown = many ? filterOptions(options, q, value) : options
  const none = many && q.trim() !== "" && shown.filter(([v]) => v !== value).length === 0
  if (!many) {
    return (
      <Field label={label}>
        <select value={value} onChange={e => onChange(e.target.value)} style={inp}>
          {blank !== undefined && <option value="">{blank}</option>}
          {options.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
        </select>
      </Field>
    )
  }
  // With a filter box the label must belong to the drop-down alone, so the two are not wrapped in one label.
  const id = `pick-${label.replace(/\W+/g, "-").toLowerCase()}`
  return (
    <div style={{ marginBottom: 12 }}>
      <label htmlFor={id} style={{ display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary, var(--hf-text))", marginBottom: 4 }}>{label}</label>
      <input aria-label={`Search ${label}`} placeholder={`Type to find (${options.length})`} value={q} onChange={e => setQ(e.target.value)} style={{ ...inp, marginBottom: 6 }} autoComplete="off" />
      <select id={id} value={value} onChange={e => onChange(e.target.value)} style={inp}>
        {blank !== undefined && <option value="">{blank}</option>}
        {shown.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
        {none && <option value="" disabled>Nothing matches "{q.trim()}"</option>}
      </select>
    </div>
  )
}
