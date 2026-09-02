// src/pages/training/EmployeePicker.tsx
//
// Search-as-you-type combobox for picking an HR employee by id, used
// when enrolling someone into a training session.
//
// UNVERIFIED CONTRACT: I could not confirm HrController's real REST
// endpoint shape via the synced source this session (only HrFacade's
// internal Java interface and hr.dto.EmployeeResponse's *existence*
// were confirmed — TrainingEnrollmentService imports
// za.co.handyflow.platform.hr.dto.EmployeeResponse and calls
// hrFacade.findEmployeeById(tenantId, employeeId), so the DTO shape
// below is inferred from that usage plus the sibling shapes this
// engagement already confirmed — UserResponse/PayEmployeeResponse both
// carry firstName/lastName/phone/jobTitle/department/status). This
// component assumes:
//   GET /api/v1/hr/employees?search=<text>&size=20
//   -> ApiResponse<Page<EmployeeResponse>>
//   EmployeeResponse: { id, employeeNumber, firstName, lastName, fullName?, email, phone, jobTitle, department, status }
// If this 404s or the field names differ, the real HrController/
// EmployeeResponse.java should be read directly and this component
// patched — flagging here rather than guessing silently, same as
// every other UNVERIFIED note in this engagement.
import { useState, useEffect, useRef } from "react"
import { Search, X, User } from "lucide-react"
import { apiClient } from "../../api/client"
import { TRAINING_ACCENT } from "./constants"

export interface EmployeeOption {
  id: string
  fullName?: string | null
  firstName?: string | null
  lastName?: string | null
  employeeNumber?: string | null
  jobTitle?: string | null
  department?: string | null
}

function displayName(e: EmployeeOption): string {
  const composed = [e.firstName, e.lastName].filter(Boolean).join(" ")
  return e.fullName || composed || e.employeeNumber || e.id.slice(0, 8)
}

export default function EmployeePicker({ value, onChange }: { value: EmployeeOption | null; onChange: (e: EmployeeOption | null) => void }) {
  const [query, setQuery] = useState("")
  const [open, setOpen] = useState(false)
  const [results, setResults] = useState<EmployeeOption[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const boxRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const handler = setTimeout(async () => {
      setLoading(true)
      setError(null)
      try {
        const res = await apiClient.get(`/api/v1/hr/employees`, { params: { search: query || undefined, size: 20 } })
        const page = res.data
        setResults(Array.isArray(page) ? page : (page?.content ?? []))
      } catch (err: any) {
        setError("Could not load employees — check that GET /api/v1/hr/employees exists with this shape (see this file's header comment)")
        setResults([])
      } finally {
        setLoading(false)
      }
    }, 250)
    return () => clearTimeout(handler)
  }, [query, open])

  useEffect(() => {
    const onClick = (e: MouseEvent) => { if (boxRef.current && !boxRef.current.contains(e.target as Node)) setOpen(false) }
    document.addEventListener("mousedown", onClick)
    return () => document.removeEventListener("mousedown", onClick)
  }, [])

  if (value) {
    return (
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", border: "1px solid #E2E8F0", borderRadius: 8, padding: "9px 12px" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <User size={14} color={TRAINING_ACCENT} />
          <div>
            <p style={{ fontSize: 13, fontWeight: 600, color: "#0F172A", margin: 0 }}>{displayName(value)}</p>
            {(value.jobTitle || value.department) && (
              <p style={{ fontSize: 11, color: "#94A3B8", margin: 0 }}>{[value.jobTitle, value.department].filter(Boolean).join(" · ")}</p>
            )}
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
          placeholder="Search employees by name…"
          style={{ border: "none", outline: "none", fontSize: 13, flex: 1, fontFamily: "inherit" }}
        />
      </div>
      {open && (
        <div style={{ position: "absolute", top: "calc(100% + 4px)", left: 0, right: 0, background: "#fff", border: "1px solid #E2E8F0", borderRadius: 8, boxShadow: "0 8px 24px rgba(0,0,0,0.08)", maxHeight: 240, overflowY: "auto", zIndex: 20 }}>
          {loading ? (
            <p style={{ fontSize: 12, color: "#94A3B8", padding: "10px 12px", margin: 0 }}>Searching…</p>
          ) : error ? (
            <p style={{ fontSize: 11.5, color: "#DC2626", padding: "10px 12px", margin: 0 }}>{error}</p>
          ) : results.length === 0 ? (
            <p style={{ fontSize: 12, color: "#94A3B8", padding: "10px 12px", margin: 0 }}>No employees found.</p>
          ) : (
            results.map(emp => (
              <button key={emp.id} onClick={() => { onChange(emp); setOpen(false); setQuery("") }}
                style={{ display: "block", width: "100%", textAlign: "left", padding: "9px 12px", background: "none", border: "none", cursor: "pointer", borderTop: "1px solid #F1F5F9" }}>
                <p style={{ fontSize: 13, fontWeight: 600, color: "#0F172A", margin: 0 }}>{displayName(emp)}</p>
                {(emp.jobTitle || emp.department || emp.employeeNumber) && (
                  <p style={{ fontSize: 11, color: "#94A3B8", margin: 0 }}>{[emp.employeeNumber, emp.jobTitle, emp.department].filter(Boolean).join(" · ")}</p>
                )}
              </button>
            ))
          )}
        </div>
      )}
    </div>
  )
}
