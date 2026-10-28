// src/pages/hr/EtiModal.tsx
//
// The two facts the incentive needs about a person that payroll cannot know: months already used (elsewhere or before payroll was kept here)
// and a reason they cannot qualify. Age and ID come from the employee record; pay comes from the pay run.
import { useEffect, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { X } from "lucide-react"
import { apiClient } from "../../api/client"
import { etiBody, etiForm, etiProblem, MAX_ETI_REASON, type EmployeeEti, type EtiForm } from "./eti.logic"

type ApiErr = { response?: { data?: { message?: string } } }
const input: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 14, boxSizing: "border-box", background: "var(--hf-surface)", color: "var(--hf-text)" }
const label: React.CSSProperties = { display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4 }

export default function EtiModal({ employee, canEdit, onClose }: { employee: { id: string; fullName: string; employeeNumber: string }; canEdit: boolean; onClose: () => void }) {
  const qc = useQueryClient()
  const key = ["hr-eti", employee.id]
  const [form, setForm] = useState<EtiForm | null>(null)
  const [error, setError] = useState("")
  const [saved, setSaved] = useState(false)

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") onClose() }
    document.addEventListener("keydown", onKey)
    return () => document.removeEventListener("keydown", onKey)
  }, [onClose])

  const facts = useQuery<EmployeeEti>({
    queryKey: key,
    queryFn: async () => { const r = await apiClient.get(`/api/v1/hr/employees/${employee.id}/eti`); return (r.data?.data ?? r.data) as EmployeeEti },
  })
  const f = form ?? etiForm(facts.data)
  const problem = etiProblem(f)
  const save = useMutation({
    mutationFn: () => apiClient.put(`/api/v1/hr/employees/${employee.id}/eti`, etiBody(f)),
    onSuccess: () => { qc.invalidateQueries({ queryKey: key }); setError(""); setSaved(true); setForm(null) },
    onError: (e: unknown) => { setSaved(false); setError((e as ApiErr)?.response?.data?.message ?? "This could not be saved.") },
  })

  return (
    <div role="dialog" aria-modal="true" aria-label="Employment tax incentive" style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.4)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, padding: 16 }}
      onClick={e => { if (e.target === e.currentTarget) onClose() }}>
      <div style={{ background: "var(--hf-surface)", borderRadius: 14, width: "100%", maxWidth: 480, maxHeight: "90vh", overflow: "auto", padding: 22 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 10 }}>
          <div>
            <div style={{ fontSize: 16, fontWeight: 700 }}>Employment tax incentive</div>
            <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{employee.fullName} · {employee.employeeNumber}</div>
          </div>
          <button aria-label="Close" onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-muted)" }}><X size={18} /></button>
        </div>
        <p style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 0 }}>
          A pay run works the incentive out for people aged 18 to 29 with a valid South African ID, paid at least R2,500 and under R7,500 for a full month, for up to 24 months.
          Enter what payroll cannot know.
        </p>
        {facts.isLoading ? <div style={{ color: "var(--hf-text-faint)" }}>Loading…</div> : (
          <div style={{ display: "grid", gap: 10 }}>
            <div>
              <label htmlFor="eti-months" style={label}>Months of the incentive already used</label>
              <input id="eti-months" style={input} inputMode="numeric" disabled={!canEdit} value={f.priorMonths} onChange={e => setForm({ ...f, priorMonths: e.target.value })} />
              <div style={{ fontSize: 11, color: "var(--hf-text-muted)", marginTop: 4 }}>By another employer, or by this one before payroll was kept here. Months paid by payroll here are counted for you.</div>
            </div>
            <div>
              <label htmlFor="eti-why" style={label}>Cannot qualify because</label>
              <input id="eti-why" style={input} disabled={!canEdit} maxLength={MAX_ETI_REASON} placeholder="Leave empty if they may qualify" value={f.excludedReason} onChange={e => setForm({ ...f, excludedReason: e.target.value })} />
              <div style={{ fontSize: 11, color: "var(--hf-text-muted)", marginTop: 4 }}>For example a domestic worker, or a connected person to the employer.</div>
            </div>
            {error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>{error}</div>}
            {saved && !error && <div role="status" style={{ color: "var(--hf-success-text-strong)", fontSize: 13 }}>Saved.</div>}
            {canEdit
              ? <button disabled={!!problem || save.isPending} title={problem ?? undefined} onClick={() => save.mutate()}
                  style={{ padding: "10px 14px", borderRadius: 8, border: "none", background: "var(--hf-accent)", color: "#fff", fontWeight: 600, cursor: problem ? "not-allowed" : "pointer", opacity: problem ? 0.6 : 1 }}>
                  {save.isPending ? "Saving…" : "Save"}
                </button>
              : <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>You can see this but not change it.</div>}
            {problem && canEdit && <div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>{problem}</div>}
          </div>
        )}
      </div>
    </div>
  )
}
