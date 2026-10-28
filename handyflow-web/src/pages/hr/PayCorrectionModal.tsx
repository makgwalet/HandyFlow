// src/pages/hr/PayCorrectionModal.tsx
import { useEffect, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { X } from "lucide-react"
import { apiClient } from "../../api/client"
import { money, type PayAdjustment } from "./payAdjustments.logic"
import { correctionBody, correctionProblem, DIRECTIONS, EMPTY_CORRECTION, MAX_CORRECTION_REASON, type CorrectionForm } from "./payCorrection.logic"

type ApiErr = { response?: { data?: { message?: string } } }
const input: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 14, boxSizing: "border-box", background: "var(--hf-surface)", color: "var(--hf-text)" }
const label: React.CSSProperties = { display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4 }

export default function PayCorrectionModal({ slip, runNumber, canEdit, onClose }: {
  slip: { id: string; employeeName: string; employeeNumber: string; netPay: number }; runNumber: string; canEdit: boolean; onClose: () => void
}) {
  const qc = useQueryClient()
  const [form, setForm] = useState<CorrectionForm>(EMPTY_CORRECTION)
  const [error, setError] = useState("")
  const key = ["hr-payslip-corrections", slip.id]

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") onClose() }
    document.addEventListener("keydown", onKey)
    return () => document.removeEventListener("keydown", onKey)
  }, [onClose])

  const list = useQuery<PayAdjustment[]>({
    queryKey: key,
    queryFn: async () => { const r = await apiClient.get(`/api/v1/hr/payslips/${slip.id}/corrections`); return (r.data?.data ?? r.data) as PayAdjustment[] },
  })
  const add = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/hr/payslips/${slip.id}/corrections`, correctionBody(form)),
    onSuccess: () => { qc.invalidateQueries({ queryKey: key }); setForm(EMPTY_CORRECTION); setError("") },
    onError: (e: unknown) => setError((e as ApiErr)?.response?.data?.message ?? "The correction could not be saved."),
  })
  const problem = correctionProblem(form, Number(slip.netPay))
  const hint = DIRECTIONS.find(d => d.value === form.direction)?.hint

  return (
    <div role="dialog" aria-modal="true" aria-label="Correct payslip" style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.4)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000, padding: 16 }}
      onClick={e => { if (e.target === e.currentTarget) onClose() }}>
      <div style={{ background: "var(--hf-surface)", borderRadius: 14, width: "100%", maxWidth: 520, maxHeight: "90vh", overflow: "auto", padding: 22 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 12 }}>
          <div>
            <div style={{ fontSize: 16, fontWeight: 700 }}>Correct payslip {runNumber}</div>
            <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{slip.employeeName} · {slip.employeeNumber} · net {money(Number(slip.netPay))}</div>
          </div>
          <button aria-label="Close" onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-muted)" }}><X size={18} /></button>
        </div>
        <p style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 0 }}>The issued payslip stays as it was. The difference is settled in the next pay run.</p>

        {(list.data ?? []).length > 0 && (
          <ul style={{ listStyle: "none", padding: 0, margin: "0 0 14px", fontSize: 13 }}>
            {(list.data ?? []).map(c => (
              <li key={c.id} style={{ padding: "6px 0", borderBottom: "1px solid var(--hf-border-subtle)" }}>
                {c.kindLabel} · {money(Number(c.amount))} · {c.status === "CANCELLED" ? "cancelled" : c.payRunNumber ? `taken by ${c.payRunNumber}` : "waiting for the next run"}
              </li>
            ))}
          </ul>
        )}

        {canEdit ? (
          <div style={{ display: "grid", gap: 10 }}>
            <div>
              <label htmlFor="pc-dir" style={label}>What went wrong</label>
              <select id="pc-dir" style={input} value={form.direction} onChange={e => setForm({ ...form, direction: e.target.value as CorrectionForm["direction"] })}>
                <option value="">Choose…</option>
                {DIRECTIONS.map(d => <option key={d.value} value={d.value}>{d.label}</option>)}
              </select>
              {hint && <div style={{ fontSize: 11, color: "var(--hf-text-muted)", marginTop: 4 }}>{hint}</div>}
            </div>
            <div>
              <label htmlFor="pc-amt" style={label}>Amount (R)</label>
              <input id="pc-amt" style={input} inputMode="decimal" value={form.amount} onChange={e => setForm({ ...form, amount: e.target.value })} />
            </div>
            <div>
              <label htmlFor="pc-why" style={label}>Reason</label>
              <textarea id="pc-why" style={{ ...input, minHeight: 60 }} maxLength={MAX_CORRECTION_REASON} value={form.reason} onChange={e => setForm({ ...form, reason: e.target.value })} />
            </div>
            {error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>{error}</div>}
            <button disabled={!!problem || add.isPending} title={problem ?? undefined} onClick={() => add.mutate()}
              style={{ padding: "10px 14px", borderRadius: 8, border: "none", background: "var(--hf-accent)", color: "#fff", fontWeight: 600, cursor: problem ? "not-allowed" : "pointer", opacity: problem ? 0.6 : 1 }}>
              {add.isPending ? "Saving…" : "Add correction"}
            </button>
            {problem && form.direction && <div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>{problem}</div>}
          </div>
        ) : <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>You can see corrections but not add them.</div>}
      </div>
    </div>
  )
}
