// src/pages/compliancetender/DeadlinesTab.tsx
//
// The compliance calendar — confirmed against ComplianceDeadlineController:
// GET (pending only, soonest first), POST, POST /{id}/mark-done, DELETE
// /{id} (ADMIN). dueSoon is computed server-side (isDueWithin(14)) and
// comes back on the response.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { Plus, CalendarClock, CheckCircle2, Trash2, AlertCircle, X } from "lucide-react"
import LookupInput from "../../components/ui/LookupInput"
import { DEADLINE_TYPES } from "../../lookups/southAfrica"

interface Deadline {
  id: string; registrationId: string | null; deadlineType: string; description: string | null
  dueDate: string; status: string; dueSoon: boolean; completedAt: string | null
}

const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }
const fmtDate = (d: string) => new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })

const EMPTY_FORM = { deadlineType: "", description: "", dueDate: "" }

export default function DeadlinesTab() {
  const qc = useQueryClient()
  const canManage = usePermission("COMPLIANCE_MANAGE") || usePermission("COMPLIANCE_ADMIN")
  const canAdmin = usePermission("COMPLIANCE_ADMIN")

  const [showAdd, setShowAdd] = useState(false)
  const [form, setForm] = useState(EMPTY_FORM)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [apiError, setApiError] = useState("")

  const { data: deadlines = [], isLoading } = useQuery<Deadline[]>({
    queryKey: ["ct-deadlines"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/compliance/deadlines")),
  })

  const invalidate = () => { qc.invalidateQueries({ queryKey: ["ct-deadlines"] }); qc.invalidateQueries({ queryKey: ["ct-dashboard"] }) }

  const create = useMutation({
    mutationFn: (body: any) => apiClient.post("/api/v1/compliance/deadlines", body),
    onSuccess: () => { invalidate(); setShowAdd(false); setForm(EMPTY_FORM); setFieldErrors({}); setApiError("") },
    onError: (e: any) => { const d = e.response?.data; if (d?.errors) setFieldErrors(d.errors); else setApiError(d?.message ?? "Failed to create deadline") },
  })

  const markDone = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/compliance/deadlines/${id}/mark-done`),
    onSuccess: () => invalidate(),
  })

  const remove = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/compliance/deadlines/${id}`),
    onSuccess: () => invalidate(),
  })

  const validate = () => {
    const errs: Record<string, string> = {}
    if (!form.deadlineType.trim()) errs.deadlineType = "Deadline type is required"
    if (!form.dueDate) errs.dueDate = "Due date is required"
    setFieldErrors(errs)
    return Object.keys(errs).length === 0
  }

  const inp = (k: string): React.CSSProperties => ({
    width: "100%", padding: "9px 12px", boxSizing: "border-box" as const,
    border: `1.5px solid ${fieldErrors[k] ? "var(--hf-danger)" : "var(--hf-border)"}`,
    borderRadius: 8, fontSize: 14, background: fieldErrors[k] ? "var(--hf-danger-soft)" : "var(--hf-surface)", outline: "none",
  })
  const FErr = ({ k }: { k: string }) => fieldErrors[k] ? (
    <div style={{ display: "flex", alignItems: "center", gap: 4, fontSize: 12, color: "var(--hf-danger-text)", marginTop: 4 }}>
      <AlertCircle size={12} />{fieldErrors[k]}
    </div>
  ) : null

  const overdue = deadlines.filter(d => d.dueDate < new Date().toISOString().slice(0, 10))
  const dueSoon = deadlines.filter(d => d.dueSoon && !overdue.includes(d))
  const upcoming = deadlines.filter(d => !overdue.includes(d) && !dueSoon.includes(d))

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 18 }}>
        {canManage && (
          <button onClick={() => { setShowAdd(true); setForm(EMPTY_FORM); setFieldErrors({}); setApiError("") }}
            style={{ display: "flex", alignItems: "center", gap: 7, background: "var(--hf-sky-solid-strong)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, padding: "9px 18px", fontSize: 14, fontWeight: 600, cursor: "pointer" }}>
            <Plus size={15} /> New Deadline
          </button>
        )}
      </div>

      {isLoading ? (
        <div style={{ textAlign: "center", padding: 40, color: "var(--hf-text-faint)" }}>Loading deadlines...</div>
      ) : deadlines.length === 0 ? (
        <div style={{ textAlign: "center", padding: "60px 20px", color: "var(--hf-text-faint)" }}>
          <CalendarClock size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: "var(--hf-text-tertiary)" }}>No pending deadlines</div>
        </div>
      ) : (
        <>
          {[
            { title: "Overdue", items: overdue, color: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)" },
            { title: "Due Soon (within 14 days)", items: dueSoon, color: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)" },
            { title: "Upcoming", items: upcoming, color: "var(--hf-sky-text-strong)", bg: "var(--hf-surface-muted)" },
          ].filter(g => g.items.length > 0).map(group => (
            <div key={group.title} style={{ marginBottom: 22 }}>
              <div style={{ fontSize: 12, fontWeight: 700, color: group.color, textTransform: "uppercase" as const, letterSpacing: "0.05em", marginBottom: 10 }}>{group.title} ({group.items.length})</div>
              <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                {group.items.map(d => (
                  <div key={d.id} style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 14, border: `1px solid color-mix(in srgb, ${group.color} 13%, transparent)`, borderRadius: 10, padding: "12px 16px", background: group.bg }}>
                    <div style={{ minWidth: 0 }}>
                      <div style={{ fontWeight: 700, fontSize: 13, color: "var(--hf-text)" }}>{d.deadlineType}</div>
                      <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 2 }}>
                        Due {fmtDate(d.dueDate)}{d.description ? ` · ${d.description}` : ""}
                      </div>
                    </div>
                    <div style={{ display: "flex", gap: 6, flexShrink: 0 }}>
                      {canManage && (
                        <button onClick={() => markDone.mutate(d.id)} title="Mark done" style={{ background: "var(--hf-success-soft-strong)", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "var(--hf-success-text-strong)" }}><CheckCircle2 size={13} /></button>
                      )}
                      {canAdmin && (
                        <button onClick={() => { if (confirm(`Delete deadline "${d.deadlineType}"?`)) remove.mutate(d.id) }} title="Delete" style={{ background: "var(--hf-danger-soft)", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "var(--hf-danger-text)" }}><Trash2 size={13} /></button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </>
      )}

      {showAdd && (
        <div style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, backdropFilter: "blur(2px)" }}>
          <div style={{ background: "var(--hf-surface)", borderRadius: 16, padding: 28, width: 480, boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "var(--hf-text)" }}>New Compliance Deadline</h3>
              <button onClick={() => { setShowAdd(false); setApiError("") }} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-faint)", display: "flex" }}><X size={20} /></button>
            </div>
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>Deadline type *</label>
              <LookupInput value={form.deadlineType} options={DEADLINE_TYPES} onChange={v => { setForm(f => ({ ...f, deadlineType: v })); setFieldErrors(f => { const n = { ...f }; delete n.deadlineType; return n }) }} placeholder="e.g. ANNUAL_RETURN" style={inp("deadlineType")} />
              <FErr k="deadlineType" />
            </div>
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>Due date *</label>
              <input type="date" value={form.dueDate} onChange={e => { setForm(f => ({ ...f, dueDate: e.target.value })); setFieldErrors(f => { const n = { ...f }; delete n.dueDate; return n }) }} style={inp("dueDate")} />
              <FErr k="dueDate" />
            </div>
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>Description</label>
              <textarea value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} rows={2} style={{ ...inp("description"), resize: "vertical" as const }} />
            </div>
            {apiError && <div style={{ marginBottom: 14, padding: "10px 12px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, fontSize: 13, color: "var(--hf-danger-text)" }}>{apiError}</div>}
            <div style={{ display: "flex", gap: 10, justifyContent: "flex-end" }}>
              <button onClick={() => { setShowAdd(false); setApiError("") }} style={{ padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", fontSize: 14, cursor: "pointer", color: "var(--hf-text-secondary)" }}>Cancel</button>
              <button onClick={() => { if (!validate()) return; create.mutate({ deadlineType: form.deadlineType, description: form.description || null, dueDate: form.dueDate, registrationId: null }) }}
                disabled={create.isPending}
                style={{ padding: "9px 22px", background: create.isPending ? "var(--hf-text-faint)" : "var(--hf-sky-solid-strong)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: create.isPending ? "not-allowed" : "pointer" }}>
                {create.isPending ? "Saving..." : "Create Deadline"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

const lbl: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 5 }
