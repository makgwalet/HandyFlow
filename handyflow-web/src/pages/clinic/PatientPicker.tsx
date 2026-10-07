// Searchable patient picker. Asks the server for matches as the user types, so it keeps working
// with thousands of patients (the old dropdowns loaded the first 200 and silently dropped the rest).
import { useEffect, useId, useRef, useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { Search, X } from "lucide-react"
import { apiClient } from "../../api/client"

export interface PickerPatient {
  id: string; fullName: string
  dateOfBirth?: string | null; phone?: string | null; idNumber?: string | null; patientNumber?: string | null
}

export const PICKER_PAGE_SIZE = 15
export const SEARCH_DELAY_MS = 250

export function patientSearchUrl(term: string): string {
  const q = term.trim()
  const p = new URLSearchParams({ size: String(PICKER_PAGE_SIZE) })
  if (q) p.set("search", q)
  return `/api/v1/clinic/patients?${p}`
}

/** "DOB 1990-04-02 · 082 123 4567 · #P-0012" — tells apart patients who share a name. */
export function patientDetail(p: PickerPatient): string {
  return [p.dateOfBirth && `DOB ${p.dateOfBirth}`, p.phone, p.patientNumber && `#${p.patientNumber}`].filter(Boolean).join(" · ")
}

const pageOf = (r: any): PickerPatient[] => { const d = r.data?.data ?? r.data; return Array.isArray(d) ? d : (d?.content ?? []) }

export default function PatientPicker({ value, onChange, error, placeholder = "Search by name, ID or phone…", label = "Patient", autoFocus }: {
  value: PickerPatient | null
  onChange: (p: PickerPatient | null) => void
  error?: string
  placeholder?: string
  label?: string
  autoFocus?: boolean
}) {
  const [term, setTerm] = useState("")
  const [debounced, setDebounced] = useState("")
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(0)
  const boxRef = useRef<HTMLDivElement>(null)
  const listId = useId()

  useEffect(() => { const t = setTimeout(() => setDebounced(term), SEARCH_DELAY_MS); return () => clearTimeout(t) }, [term])
  useEffect(() => {
    const away = (e: MouseEvent) => { if (boxRef.current && !boxRef.current.contains(e.target as Node)) setOpen(false) }
    document.addEventListener("mousedown", away)
    return () => document.removeEventListener("mousedown", away)
  }, [])

  // A failed search resolves to null (not a rejection) so the dropdown can say so in place.
  const { data, isFetching } = useQuery<PickerPatient[] | null>({
    queryKey: ["clinic-patient-picker", debounced],
    queryFn: async () => { try { return pageOf(await apiClient.get(patientSearchUrl(debounced))) } catch { return null } },
    enabled: open && !value,
    staleTime: q => (q.state.data === null ? 0 : 15_000),
  })
  const isError = data === null
  const results = data ?? EMPTY
  useEffect(() => { setActive(0) }, [results])

  const choose = (p: PickerPatient) => { onChange(p); setOpen(false); setTerm(""); setDebounced("") }
  const onKey = (e: React.KeyboardEvent) => {
    if (e.key === "ArrowDown") { e.preventDefault(); setOpen(true); setActive(i => Math.min(i + 1, results.length - 1)) }
    else if (e.key === "ArrowUp") { e.preventDefault(); setActive(i => Math.max(i - 1, 0)) }
    else if (e.key === "Enter" && open && results[active]) { e.preventDefault(); choose(results[active]) }
    else if (e.key === "Escape") setOpen(false)
  }
  const border = `1.5px solid ${error ? "var(--hf-danger)" : "var(--hf-border)"}`

  if (value) {
    return (
      <div>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 8, padding: "7px 12px", border, borderRadius: 8, background: "var(--hf-surface)" }}>
          <div style={{ minWidth: 0 }}>
            <div style={{ fontSize: 14, fontWeight: 600, color: "var(--hf-text)", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{value.fullName}</div>
            {patientDetail(value) && <div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>{patientDetail(value)}</div>}
          </div>
          <button type="button" aria-label="Change patient" onClick={() => onChange(null)}
            style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-muted)", display: "flex" }}><X size={16} /></button>
        </div>
        {error && <div role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text)", marginTop: 4 }}>{error}</div>}
      </div>
    )
  }

  return (
    <div ref={boxRef} style={{ position: "relative" }}>
      <div style={{ position: "relative" }}>
        <Search size={15} style={{ position: "absolute", left: 11, top: 12, color: "var(--hf-text-faint)" }} />
        <input role="combobox" aria-label={label} aria-expanded={open} aria-controls={listId} aria-autocomplete="list"
          autoFocus={autoFocus} value={term} placeholder={placeholder}
          onChange={e => { setTerm(e.target.value); setOpen(true) }} onFocus={() => setOpen(true)} onKeyDown={onKey}
          style={{ width: "100%", boxSizing: "border-box", padding: "9px 12px 9px 32px", border, borderRadius: 8, fontSize: 14, outline: "none",
            background: "var(--hf-surface)", color: "var(--hf-text)" }} />
      </div>
      {open && (
        <ul id={listId} role="listbox"
          style={{ position: "absolute", zIndex: 30, left: 0, right: 0, top: "calc(100% + 4px)", margin: 0, padding: 4, listStyle: "none", maxHeight: 280,
            overflowY: "auto", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 10, boxShadow: "0 12px 32px rgba(0,0,0,0.14)" }}>
          {isError && <li style={note}>Could not search patients. Try again.</li>}
          {!isError && results.length === 0 && <li style={note}>{isFetching ? "Searching…" : debounced ? "No patients match." : "No patients yet."}</li>}
          {results.map((p, i) => (
            <li key={p.id} role="option" aria-selected={i === active} onMouseEnter={() => setActive(i)} onMouseDown={e => { e.preventDefault(); choose(p) }}
              style={{ padding: "7px 10px", borderRadius: 7, cursor: "pointer", background: i === active ? "var(--hf-surface-sunken)" : "transparent" }}>
              <div style={{ fontSize: 14, fontWeight: 600, color: "var(--hf-text)" }}>{p.fullName}</div>
              {patientDetail(p) && <div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>{patientDetail(p)}</div>}
            </li>
          ))}
          {results.length >= PICKER_PAGE_SIZE && <li style={{ ...note, fontSize: 11 }}>Showing the first {PICKER_PAGE_SIZE}. Type more to narrow down.</li>}
        </ul>
      )}
      {error && <div role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text)", marginTop: 4 }}>{error}</div>}
    </div>
  )
}
const EMPTY: PickerPatient[] = []
const note = { padding: "8px 10px", fontSize: 12, color: "var(--hf-text-muted)" } as const
