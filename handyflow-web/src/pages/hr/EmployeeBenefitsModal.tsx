// src/pages/hr/EmployeeBenefitsModal.tsx
//
// Employer medical and retirement contributions and other fringe benefits. They raise the income tax is worked out on but are not paid in
// cash. Payroll picks up the lines in force on the last day of the pay period.
import { useEffect, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { X } from "lucide-react"
import { apiClient } from "../../api/client"
import { BENEFIT_KINDS, EMPTY_BENEFIT_FORM, benefitBody, benefitProblem, canEditBenefits, endProblem, money, type Benefit, type BenefitForm } from "./benefits.logic"

type ApiErr = { response?: { data?: { message?: string } } }
const fmtDate = (d: string | null) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
const input: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 14, boxSizing: "border-box", background: "var(--hf-surface)", color: "var(--hf-text)" }
const label: React.CSSProperties = { display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4 }

export default function EmployeeBenefitsModal({ employee, permissions, onClose }: {
  employee: { id: string; fullName: string; employeeNumber: string }; permissions: string[]; onClose: () => void
}) {
  const qc = useQueryClient()
  const canEdit = canEditBenefits(permissions)
  const [form, setForm] = useState<BenefitForm>(EMPTY_BENEFIT_FORM)
  const [ending, setEnding] = useState<{ id: string; lastDay: string } | null>(null)
  const [error, setError] = useState("")
  const key = ["hr-benefits", employee.id]

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") onClose() }
    document.addEventListener("keydown", onKey)
    return () => document.removeEventListener("keydown", onKey)
  }, [onClose])

  const list = useQuery<Benefit[]>({
    queryKey: key,
    queryFn: async () => { const r = await apiClient.get(`/api/v1/hr/employees/${employee.id}/benefits`); return (r.data?.data ?? r.data) as Benefit[] },
  })
  const lines = list.data ?? []

  const onErr = (fallback: string) => (e: unknown) => setError((e as ApiErr)?.response?.data?.message ?? fallback)
  const add = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/hr/employees/${employee.id}/benefits`, benefitBody(form)),
    onSuccess: () => { qc.invalidateQueries({ queryKey: key }); setForm(EMPTY_BENEFIT_FORM); setError("") },
    onError: onErr("The benefit could not be saved."),
  })
  const end = useMutation({
    mutationFn: (v: { id: string; lastDay: string }) => apiClient.post(`/api/v1/hr/benefits/${v.id}/end`, { lastDay: v.lastDay }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: key }); setEnding(null); setError("") },
    onError: onErr("The benefit could not be ended."),
  })

  const problem = benefitProblem(form, lines)
  const set = (k: keyof BenefitForm) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setForm(f => ({ ...f, [k]: e.target.value }))
  const endingLine = ending ? lines.find(l => l.id === ending.id) : undefined
  const endErr = endingLine && ending ? endProblem(endingLine, ending.lastDay) : ""

  return (
    <div onMouseDown={e => { if (e.target === e.currentTarget) onClose() }}
      style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", zIndex: 200, display: "flex", alignItems: "center", justifyContent: "center", padding: "clamp(8px, 3vw, 24px)" }}>
      <div role="dialog" aria-modal="true" aria-label={`Benefits for ${employee.fullName}`}
        style={{ background: "var(--hf-surface)", borderRadius: 16, width: "100%", maxWidth: 720, maxHeight: "92vh", overflowY: "auto", padding: "clamp(16px, 4vw, 24px)", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 12 }}>
          <div>
            <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "var(--hf-text)" }}>Employer benefits</h3>
            <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>{employee.fullName} · {employee.employeeNumber}</div>
          </div>
          <button type="button" aria-label="Close" onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-faint)", display: "flex" }}><X size={20} /></button>
        </div>

        <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 14px" }}>
          These are paid by the employer, not in cash, but they are taxed. Each month's amount is added to the income tax is worked out on; it does not change the pay.
          A benefit counts for a pay period if it is in force on the period's last day. Amounts are not adjusted for part months.
        </p>

        {list.isLoading ? <div style={{ color: "var(--hf-text-faint)", padding: 12 }}>Loading…</div>
          : list.isError ? <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>The benefits could not be loaded.</div>
          : lines.length === 0 ? <div style={{ fontSize: 13, color: "var(--hf-text-muted)", padding: "8px 0 14px" }}>No employer benefits are recorded.</div>
          : (
            <div style={{ overflowX: "auto", marginBottom: 14 }}>
              <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
                <thead><tr style={{ textAlign: "left", color: "var(--hf-text-faint)", fontSize: 11, textTransform: "uppercase" }}>
                  <th style={{ padding: 6 }}>Benefit</th><th style={{ padding: 6 }}>A month</th><th style={{ padding: 6 }}>From</th><th style={{ padding: 6 }}>To</th><th style={{ padding: 6 }} /></tr></thead>
                <tbody>
                  {lines.map(b => (
                    <tr key={b.id} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                      <td style={{ padding: 6 }}>{b.kindLabel}{b.description && <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{b.description}</div>}{b.reason && <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>Reason: {b.reason}</div>}</td>
                      <td style={{ padding: 6, whiteSpace: "nowrap" }}>{money(b.monthlyAmount)}</td>
                      <td style={{ padding: 6 }}>{fmtDate(b.effectiveFrom)}</td>
                      <td style={{ padding: 6 }}>{fmtDate(b.effectiveTo)}</td>
                      <td style={{ padding: 6 }}>
                        {b.inForce && <span style={{ background: "var(--hf-success-soft-strong)", color: "var(--hf-success-text-strong)", padding: "2px 8px", borderRadius: 20, fontSize: 11, fontWeight: 700 }}>In force</span>}
                        {canEdit && !b.effectiveTo && !ending && (
                          <button type="button" aria-label={`End ${b.kindLabel}`} onClick={() => { setEnding({ id: b.id, lastDay: "" }); setError("") }}
                            style={{ marginLeft: 6, padding: "3px 10px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", fontSize: 12, cursor: "pointer", color: "var(--hf-text-secondary)" }}>End</button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

        {ending && endingLine && (
          <form onSubmit={e => { e.preventDefault(); if (!endErr) end.mutate(ending) }} noValidate
            style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: 12, marginBottom: 14 }}>
            <label htmlFor="bn-last" style={label}>Last day of {endingLine.kindLabel.toLowerCase()} *</label>
            <input id="bn-last" style={input} type="date" value={ending.lastDay} onChange={e => setEnding({ id: ending.id, lastDay: e.target.value })} />
            {ending.lastDay && endErr && <div role="alert" style={{ marginTop: 8, fontSize: 13, color: "var(--hf-danger-text)" }}>{endErr}</div>}
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 10 }}>
              <button type="button" onClick={() => setEnding(null)} style={{ padding: "7px 14px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", fontSize: 13, cursor: "pointer", color: "var(--hf-text-secondary)" }}>Cancel</button>
              <button type="submit" disabled={!!endErr || end.isPending}
                style={{ padding: "7px 16px", background: endErr || end.isPending ? "var(--hf-text-faint)" : "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 9, fontSize: 13, fontWeight: 700, cursor: endErr ? "not-allowed" : "pointer" }}>
                {end.isPending ? "Saving…" : "End benefit"}
              </button>
            </div>
          </form>
        )}

        {canEdit ? (
          <form onSubmit={e => { e.preventDefault(); if (!problem) add.mutate() }} noValidate>
            <h4 style={{ margin: "0 0 8px", fontSize: 14, color: "var(--hf-text)" }}>Add a benefit</h4>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 10 }}>
              <div><label htmlFor="bn-kind" style={label}>Kind *</label>
                <select id="bn-kind" style={input} value={form.kind} onChange={set("kind")}>
                  <option value="">Choose…</option>
                  {BENEFIT_KINDS.map(k => <option key={k.value} value={k.value}>{k.label}</option>)}
                </select></div>
              <div><label htmlFor="bn-amount" style={label}>Amount a month (R) *</label><input id="bn-amount" style={input} inputMode="decimal" value={form.amount} onChange={set("amount")} /></div>
              <div><label htmlFor="bn-from" style={label}>Starts on *</label><input id="bn-from" style={input} type="date" value={form.from} onChange={set("from")} /></div>
              <div><label htmlFor="bn-to" style={label}>Last day (optional)</label><input id="bn-to" style={input} type="date" value={form.to} onChange={set("to")} /></div>
            </div>
            <div style={{ marginTop: 10 }}><label htmlFor="bn-desc" style={label}>Description{form.kind === "OTHER_FRINGE" ? " *" : ""}</label><input id="bn-desc" style={input} value={form.description} onChange={set("description")} maxLength={200} placeholder="For example, the company car" /></div>
            <div style={{ marginTop: 10 }}><label htmlFor="bn-reason" style={label}>Reason *</label><input id="bn-reason" style={input} value={form.reason} onChange={set("reason")} maxLength={300} /></div>
            {(error || (problem && form.kind)) && <div role="alert" style={{ marginTop: 10, fontSize: 13, color: "var(--hf-danger-text)" }}>{error || problem}</div>}
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
              <button type="button" onClick={onClose} style={{ padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", fontSize: 14, cursor: "pointer", color: "var(--hf-text-secondary)" }}>Close</button>
              <button type="submit" disabled={!!problem || add.isPending}
                style={{ padding: "9px 20px", background: problem || add.isPending ? "var(--hf-text-faint)" : "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: problem ? "not-allowed" : "pointer" }}>
                {add.isPending ? "Saving…" : "Save benefit"}
              </button>
            </div>
          </form>
        ) : (
          <div style={{ display: "flex", justifyContent: "flex-end" }}>
            {error && <div role="alert" style={{ marginRight: "auto", fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</div>}
            <button type="button" onClick={onClose} style={{ padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", fontSize: 14, cursor: "pointer", color: "var(--hf-text-secondary)" }}>Close</button>
          </div>
        )}
      </div>
    </div>
  )
}
