// src/pages/hr/Emp201ActionModal.tsx
//
// Record that an EMP201 was filed on SARS eFiling (with the payment reference SARS gave), or that it was paid. Nothing is sent to SARS.
import { useEffect, useState } from "react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { X } from "lucide-react"
import { apiClient } from "../../api/client"
import { cleanReference, fileProblem, payProblem, rand, type Emp201 } from "./emp201.logic"

type ApiErr = { response?: { data?: { message?: string } } }
const input: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 14, boxSizing: "border-box", background: "var(--hf-surface)", color: "var(--hf-text)" }
const label: React.CSSProperties = { display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4 }
const today = () => new Date().toLocaleDateString("en-CA", { timeZone: "Africa/Johannesburg" })

export default function Emp201ActionModal({ emp, mode, onClose }: { emp: Emp201; mode: "file" | "pay"; onClose: () => void }) {
  const qc = useQueryClient()
  const [reference, setReference] = useState("")
  const [paidOn, setPaidOn] = useState(today())
  const [amount, setAmount] = useState(String(emp.totalPayable.toFixed(2)))
  const [error, setError] = useState("")

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") onClose() }
    document.addEventListener("keydown", onKey)
    return () => document.removeEventListener("keydown", onKey)
  }, [onClose])

  const send = useMutation({
    mutationFn: () => mode === "file"
      ? apiClient.post(`/api/v1/hr/emp201/${emp.id}/submit`, { paymentRef: cleanReference(reference) })
      : apiClient.post(`/api/v1/hr/emp201/${emp.id}/pay`, { paidOn, amount: Number(amount) }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["emp201"] }); qc.invalidateQueries({ queryKey: ["pay-runs"] }); onClose() },
    onError: e => setError((e as ApiErr)?.response?.data?.message ?? "That step could not be recorded."),
  })

  const problem = mode === "file" ? fileProblem(reference) : payProblem(paidOn, amount, emp, today())
  const period = new Date(emp.periodEnd).toLocaleDateString("en-ZA", { month: "long", year: "numeric" })
  const title = mode === "file" ? "Record EMP201 as filed" : "Record EMP201 as paid"

  return (
    <div onMouseDown={e => { if (e.target === e.currentTarget) onClose() }}
      style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", zIndex: 200, display: "flex", alignItems: "center", justifyContent: "center", padding: "clamp(8px, 3vw, 24px)" }}>
      <form role="dialog" aria-modal="true" aria-label={title} noValidate onSubmit={e => { e.preventDefault(); if (!problem) send.mutate() }}
        style={{ background: "var(--hf-surface)", borderRadius: 16, width: "100%", maxWidth: 460, padding: "clamp(16px, 4vw, 24px)", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 10 }}>
          <div>
            <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "var(--hf-text)" }}>{title}</h3>
            <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>{period} · {rand(emp.totalPayable)}</div>
          </div>
          <button type="button" aria-label="Close" onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-faint)", display: "flex" }}><X size={20} /></button>
        </div>
        {mode === "file" ? (
          <>
            <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 10px" }}>File the declaration on SARS eFiling first. This records that you did; nothing is sent to SARS from here.</p>
            <label htmlFor="e201-ref" style={label}>Payment reference from SARS *</label>
            <input id="e201-ref" style={input} value={reference} maxLength={40} onChange={e => setReference(e.target.value)} autoFocus />
          </>
        ) : (
          <>
            <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 10px" }}>Reference {emp.paymentRef}. Due {new Date(emp.dueDate).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })}. Paying after the due date can attract a penalty and interest from SARS.</p>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
              <div><label htmlFor="e201-date" style={label}>Date paid *</label><input id="e201-date" style={input} type="date" value={paidOn} onChange={e => setPaidOn(e.target.value)} /></div>
              <div><label htmlFor="e201-amt" style={label}>Amount paid (R) *</label><input id="e201-amt" style={input} inputMode="decimal" value={amount} onChange={e => setAmount(e.target.value)} /></div>
            </div>
          </>
        )}
        {(error || (problem && (mode === "pay" || reference))) && <div role="alert" style={{ marginTop: 10, fontSize: 13, color: "var(--hf-danger-text)" }}>{error || problem}</div>}
        <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
          <button type="button" onClick={onClose} style={{ padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", fontSize: 14, cursor: "pointer", color: "var(--hf-text-secondary)" }}>Cancel</button>
          <button type="submit" disabled={!!problem || send.isPending}
            style={{ padding: "9px 20px", background: problem || send.isPending ? "var(--hf-text-faint)" : "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: problem ? "not-allowed" : "pointer" }}>
            {send.isPending ? "Saving…" : mode === "file" ? "Record as filed" : "Record as paid"}
          </button>
        </div>
      </form>
    </div>
  )
}
