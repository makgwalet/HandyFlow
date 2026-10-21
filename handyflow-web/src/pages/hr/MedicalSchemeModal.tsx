// src/pages/hr/MedicalSchemeModal.tsx
//
// An employee's medical scheme membership, with dates. The medical scheme fees tax credit is worked out from the
// people covered in each pay period (the employee plus dependants), not from the employee's number of children.
import { useEffect, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { X } from "lucide-react"
import { apiClient } from "../../api/client"
import { EMPTY_SCHEME_FORM, schemeBody, schemeProblem, type MedicalScheme, type SchemeForm } from "./payrollChecks.logic"

type ApiErr = { response?: { data?: { message?: string } } }
const fmtDate = (d: string | null) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
const input: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 14, boxSizing: "border-box", background: "var(--hf-surface)", color: "var(--hf-text)" }
const label: React.CSSProperties = { display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4 }

export default function MedicalSchemeModal({ employee, onClose }: { employee: { id: string; fullName: string; employeeNumber: string }; onClose: () => void }) {
  const qc = useQueryClient()
  const [form, setForm] = useState<SchemeForm>(EMPTY_SCHEME_FORM)
  const [error, setError] = useState("")
  const key = ["hr-medical-scheme", employee.id]

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") onClose() }
    document.addEventListener("keydown", onKey)
    return () => document.removeEventListener("keydown", onKey)
  }, [onClose])

  const list = useQuery<MedicalScheme[]>({
    queryKey: key,
    queryFn: async () => { const r = await apiClient.get(`/api/v1/hr/employees/${employee.id}/medical-scheme`); return (r.data?.data ?? r.data) as MedicalScheme[] },
  })

  const add = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/hr/employees/${employee.id}/medical-scheme`, schemeBody(form)),
    onSuccess: () => { qc.invalidateQueries({ queryKey: key }); setForm(EMPTY_SCHEME_FORM); setError("") },
    onError: (e: unknown) => setError((e as ApiErr)?.response?.data?.message ?? "The membership could not be saved."),
  })

  const problem = schemeProblem(form)
  const set = (k: keyof SchemeForm) => (e: React.ChangeEvent<HTMLInputElement>) => setForm(f => ({ ...f, [k]: e.target.value }))

  return (
    <div onMouseDown={e => { if (e.target === e.currentTarget) onClose() }}
      style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", zIndex: 200, display: "flex", alignItems: "center", justifyContent: "center", padding: "clamp(8px, 3vw, 24px)" }}>
      <div role="dialog" aria-modal="true" aria-label={`Medical scheme for ${employee.fullName}`}
        style={{ background: "var(--hf-surface)", borderRadius: 16, width: "100%", maxWidth: 640, maxHeight: "92vh", overflowY: "auto", padding: "clamp(16px, 4vw, 24px)", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 12 }}>
          <div>
            <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "var(--hf-text)" }}>Medical scheme</h3>
            <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>{employee.fullName} · {employee.employeeNumber}</div>
          </div>
          <button type="button" aria-label="Close" onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-faint)", display: "flex" }}><X size={20} /></button>
        </div>

        <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 14px" }}>
          People covered is the employee plus the dependants on the scheme. The monthly medical tax credit comes from this number for each pay period.
          Adding a new membership ends the current one the day before the new one starts; earlier periods keep the cover they had.
        </p>

        {list.isLoading ? <div style={{ color: "var(--hf-text-faint)", padding: 12 }}>Loading…</div>
          : list.isError ? <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>The membership history could not be loaded.</div>
          : (list.data ?? []).length === 0 ? <div style={{ fontSize: 13, color: "var(--hf-text-muted)", padding: "8px 0 14px" }}>No membership is recorded. Until one is, payroll gives no medical credit, or an employee-only credit when a medical aid contribution is deducted, and flags it.</div>
          : (
            <div style={{ overflowX: "auto", marginBottom: 14 }}>
              <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
                <thead><tr style={{ textAlign: "left", color: "var(--hf-text-faint)", fontSize: 11, textTransform: "uppercase" }}>
                  <th style={{ padding: 6 }}>Scheme</th><th style={{ padding: 6 }}>People covered</th><th style={{ padding: 6 }}>From</th><th style={{ padding: 6 }}>To</th><th style={{ padding: 6 }} /></tr></thead>
                <tbody>
                  {(list.data ?? []).map(m => (
                    <tr key={m.id} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                      <td style={{ padding: 6 }}>{m.schemeName}{m.memberNumber && <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>No. {m.memberNumber}</div>}</td>
                      <td style={{ padding: 6 }}>{m.peopleCovered} <span style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>({m.dependants} {m.dependants === 1 ? "dependant" : "dependants"})</span></td>
                      <td style={{ padding: 6 }}>{fmtDate(m.effectiveFrom)}</td>
                      <td style={{ padding: 6 }}>{fmtDate(m.effectiveTo)}</td>
                      <td style={{ padding: 6 }}>{m.current && <span style={{ background: "var(--hf-success-soft-strong)", color: "var(--hf-success-text-strong)", padding: "2px 8px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>Current</span>}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

        <form onSubmit={e => { e.preventDefault(); if (!problem) add.mutate() }} noValidate>
          <h4 style={{ margin: "0 0 8px", fontSize: 14, color: "var(--hf-text)" }}>Record a new membership</h4>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 10 }}>
            <div><label htmlFor="ms-name" style={label}>Scheme name *</label><input id="ms-name" style={input} value={form.schemeName} onChange={set("schemeName")} maxLength={120} /></div>
            <div><label htmlFor="ms-no" style={label}>Member number</label><input id="ms-no" style={input} value={form.memberNumber} onChange={set("memberNumber")} maxLength={60} /></div>
            <div><label htmlFor="ms-dep" style={label}>Dependants *</label><input id="ms-dep" style={input} type="number" min={0} max={20} step={1} inputMode="numeric" value={form.dependants} onChange={set("dependants")} /></div>
            <div><label htmlFor="ms-from" style={label}>Starts on *</label><input id="ms-from" style={input} type="date" value={form.effectiveFrom} onChange={set("effectiveFrom")} /></div>
          </div>
          <div style={{ marginTop: 10 }}><label htmlFor="ms-notes" style={label}>Notes</label><input id="ms-notes" style={input} value={form.notes} onChange={set("notes")} maxLength={500} /></div>
          {(error || (problem && form.schemeName)) && <div role="alert" style={{ marginTop: 10, fontSize: 13, color: "var(--hf-danger-text)" }}>{error || problem}</div>}
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
            <button type="button" onClick={onClose} style={{ padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", fontSize: 14, cursor: "pointer", color: "var(--hf-text-secondary)" }}>Close</button>
            <button type="submit" disabled={!!problem || add.isPending}
              style={{ padding: "9px 20px", background: problem || add.isPending ? "var(--hf-text-faint)" : "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: problem ? "not-allowed" : "pointer" }}>
              {add.isPending ? "Saving…" : "Save membership"}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
