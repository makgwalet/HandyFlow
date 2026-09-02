// src/pages/trainingprovider/DelegatePicker.tsx
//
// Search-as-you-type combobox for picking a delegate to enrol into a
// session. Unlike Module 4a's EmployeePicker (HR endpoint UNVERIFIED),
// this contract is fully confirmed via TrainProvDelegateController:
// GET /api/v1/training-provider/delegates?clientId=&search=&size=
// -> ApiResponse<Page<DelegateResponse>>
// DelegateResponse: { id, clientId, delegateNumber, fullName, idNumber, email, phone, jobTitle, status, createdAt }
//
// When `clientId` is passed (enrolling into a CLOSED session, which
// TrainProvEnrollmentService validates the delegate's clientId must
// match), the search is locked to that client and no client column is
// shown. When `clientId` is omitted (a PUBLIC session — enrollment
// service places no client restriction there), search spans every
// client's delegates; since DelegateResponse carries no client name
// snapshot, each result shows its own clientId's first 8 characters as
// a fallback rather than inventing a name lookup this session can't
// confirm.
import { useState, useEffect, useRef } from "react"
import { Search, X, User } from "lucide-react"
import { apiClient } from "../../api/client"
import { TRAINPROV_ACCENT } from "./constants"

export interface DelegateOption {
  id: string
  clientId: string
  delegateNumber: string
  fullName: string
  idNumber: string | null
  email: string | null
  phone: string | null
  jobTitle: string | null
  status: string
}

export default function DelegatePicker({ clientId, value, onChange }: { clientId?: string; value: DelegateOption | null; onChange: (d: DelegateOption | null) => void }) {
  const [query, setQuery] = useState("")
  const [open, setOpen] = useState(false)
  const [results, setResults] = useState<DelegateOption[]>([])
  const [loading, setLoading] = useState(false)
  const boxRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const handler = setTimeout(async () => {
      setLoading(true)
      try {
        const res = await apiClient.get("/api/v1/training-provider/delegates", {
          params: { clientId: clientId || undefined, search: query || undefined, size: 20 },
        })
        const page = res.data
        setResults((Array.isArray(page) ? page : page?.content ?? []).filter((d: DelegateOption) => d.status === "ACTIVE"))
      } catch {
        setResults([])
      } finally {
        setLoading(false)
      }
    }, 250)
    return () => clearTimeout(handler)
  }, [query, open, clientId])

  useEffect(() => {
    const onClick = (e: MouseEvent) => { if (boxRef.current && !boxRef.current.contains(e.target as Node)) setOpen(false) }
    document.addEventListener("mousedown", onClick)
    return () => document.removeEventListener("mousedown", onClick)
  }, [])

  if (value) {
    return (
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", border: "1px solid #E2E8F0", borderRadius: 8, padding: "9px 12px" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <User size={14} color={TRAINPROV_ACCENT} />
          <div>
            <p style={{ fontSize: 13, fontWeight: 600, color: "#0F172A", margin: 0 }}>{value.fullName}</p>
            <p style={{ fontSize: 11, color: "#94A3B8", margin: 0 }}>
              {[value.delegateNumber, value.jobTitle].filter(Boolean).join(" · ")}{!clientId ? ` · Client ${value.clientId.slice(0, 8)}` : ""}
            </p>
          </div>
        </div>
        <button onClick={() => onChange(null)} style={{ background: "none", border: "none", cursor: "pointer" }}><X size={15} color="#94A3B8" /></button>
      </div>
    )
  }

  return (
    <div ref={boxRef} style={{ position: "relative" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8, border: "1px solid #E2E8F0", borderRadius: 8, padding: "9px 12px" }}>
        <Search size={14} color="#94A3B8" />
        <input
          value={query}
          onFocus={() => setOpen(true)}
          onChange={e => { setQuery(e.target.value); setOpen(true) }}
          placeholder="Search delegates by name…"
          style={{ border: "none", outline: "none", fontSize: 13, flex: 1, fontFamily: "inherit" }}
        />
      </div>
      {open && (
        <div style={{ position: "absolute", top: "calc(100% + 4px)", left: 0, right: 0, background: "#fff", border: "1px solid #E2E8F0", borderRadius: 8, boxShadow: "0 8px 24px rgba(0,0,0,0.08)", maxHeight: 240, overflowY: "auto", zIndex: 20 }}>
          {loading ? (
            <p style={{ fontSize: 12, color: "#94A3B8", padding: "10px 12px", margin: 0 }}>Searching…</p>
          ) : results.length === 0 ? (
            <p style={{ fontSize: 12, color: "#94A3B8", padding: "10px 12px", margin: 0 }}>No delegates found.</p>
          ) : (
            results.map(d => (
              <button key={d.id} onClick={() => { onChange(d); setOpen(false); setQuery("") }}
                style={{ display: "block", width: "100%", textAlign: "left", padding: "9px 12px", background: "none", border: "none", cursor: "pointer", borderTop: "1px solid #F1F5F9" }}>
                <p style={{ fontSize: 13, fontWeight: 600, color: "#0F172A", margin: 0 }}>{d.fullName}</p>
                <p style={{ fontSize: 11, color: "#94A3B8", margin: 0 }}>
                  {[d.delegateNumber, d.jobTitle].filter(Boolean).join(" · ")}{!clientId ? ` · Client ${d.clientId.slice(0, 8)}` : ""}
                </p>
              </button>
            ))
          )}
        </div>
      )}
    </div>
  )
}
