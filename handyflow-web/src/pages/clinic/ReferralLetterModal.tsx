// Referral letter for one consultation: collect the specialist and reason, download the PDF.
import { useState } from "react"
import { apiClient } from "../../api/client"
import ModalShell from "./ModalShell"
import LetterTemplatePicker from "./LetterTemplatePicker"
import SaveAsTemplate from "./SaveAsTemplate"
import { applyReferral } from "./letterView"

const URGENCIES = [{ id: "ROUTINE", label: "Routine" }, { id: "SEMI_URGENT", label: "Semi-urgent" }, { id: "URGENT", label: "Urgent" }]
const inp: React.CSSProperties = { width: "100%", boxSizing: "border-box", padding: "9px 12px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 14, background: "var(--hf-surface)", color: "var(--hf-text)" }
const lab: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 5 }

export default function ReferralLetterModal({ consultationId, onClose }: { consultationId: string; onClose: () => void }) {
  const [form, setForm] = useState({ specialistName: "", specialty: "", reason: "", urgency: "ROUTINE", additionalNotes: "" })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")
  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => setForm(f => ({ ...f, [k]: e.target.value }))
  const canSend = form.reason.trim().length > 0 && !busy

  const generate = async () => {
    setBusy(true); setError("")
    try {
      const res = await apiClient.post(`/api/v1/clinic/consultations/${consultationId}/referral-letter`, null, { params: form, responseType: "blob" } as any)
      const url = URL.createObjectURL(new Blob([res.data], { type: "application/pdf" }))
      const link = document.createElement("a")
      link.href = url; link.download = `referral-letter-${consultationId}.pdf`; link.click()
      setTimeout(() => URL.revokeObjectURL(url), 60_000)
      onClose()
    } catch (e: any) {
      setError(e.response?.data?.message ?? "Could not generate the referral letter.")
    } finally { setBusy(false) }
  }

  return (
    <ModalShell title="Referral letter" onClose={onClose} width={560}
      footer={<>
        <button onClick={onClose} style={{ padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", fontSize: 14, cursor: "pointer" }}>Cancel</button>
        <button onClick={generate} disabled={!canSend}
          style={{ padding: "9px 20px", border: "none", borderRadius: 9, background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", fontSize: 14, fontWeight: 600, cursor: canSend ? "pointer" : "not-allowed", opacity: canSend ? 1 : 0.6 }}>
          {busy ? "Generating…" : "Generate PDF"}
        </button></>}>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
        <div style={{ gridColumn: "1 / -1" }}><LetterTemplatePicker kind="REFERRAL" consultationId={consultationId} onApply={t => setForm(f => applyReferral(f, t))} /></div>
        <div><label style={lab} htmlFor="ref-name">Specialist name</label><input id="ref-name" style={inp} value={form.specialistName} onChange={set("specialistName")} placeholder="Dr. Jane Smith" /></div>
        <div><label style={lab} htmlFor="ref-spec">Specialty</label><input id="ref-spec" style={inp} value={form.specialty} onChange={set("specialty")} placeholder="Cardiology" /></div>
        <div style={{ gridColumn: "1 / -1" }}><label style={lab} htmlFor="ref-reason">Reason for referral *</label><input id="ref-reason" style={inp} value={form.reason} onChange={set("reason")} placeholder="Further investigation of…" /></div>
        <div><label style={lab} htmlFor="ref-urg">Urgency</label>
          <select id="ref-urg" style={inp} value={form.urgency} onChange={set("urgency")}>{URGENCIES.map(u => <option key={u.id} value={u.id}>{u.label}</option>)}</select></div>
        <div style={{ gridColumn: "1 / -1" }}><label style={lab} htmlFor="ref-notes">Additional notes</label><input id="ref-notes" style={inp} value={form.additionalNotes} onChange={set("additionalNotes")} placeholder="Optional" /></div>
        <div style={{ gridColumn: "1 / -1" }}><SaveAsTemplate kind="REFERRAL" payload={() => ({ title: form.reason.trim() || undefined, body: form.additionalNotes.trim() || undefined, specialty: form.specialty.trim() || undefined, urgency: form.urgency })} /></div>
      </div>
      {error && <div role="alert" style={{ marginTop: 14, padding: "8px 12px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 7, fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</div>}
    </ModalShell>
  )
}
