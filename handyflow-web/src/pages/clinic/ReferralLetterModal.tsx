// Referral letter for one consultation: collect the specialist and reason, download the PDF.
// Since 0203 issuing does not email anything: sending a copy to the patient is a separate button, shown after the letter is issued.
import { useState } from "react"
import { apiClient } from "../../api/client"
import ModalShell from "./ModalShell"
import LetterTemplatePicker from "./LetterTemplatePicker"
import SaveAsTemplate from "./SaveAsTemplate"
import LetterheadSelect from "./LetterheadSelect"
import { applyReferral } from "./letterView"
import { useCan } from "./clinicAccess"
import { canSend as sendable, emailToPatient, errorMessage, saveBlob, visitDocuments, type IssuedDoc } from "./issuedDocs"

const URGENCIES = [{ id: "ROUTINE", label: "Routine" }, { id: "SEMI_URGENT", label: "Semi-urgent" }, { id: "URGENT", label: "Urgent" }]
const inp: React.CSSProperties = { width: "100%", boxSizing: "border-box", padding: "9px 12px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 14, background: "var(--hf-surface)", color: "var(--hf-text)" }
const lab: React.CSSProperties = { display: "block", fontSize: 13, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 5 }

export default function ReferralLetterModal({ consultationId, onClose }: { consultationId: string; onClose: () => void }) {
  const [form, setForm] = useState({ specialistName: "", specialty: "", reason: "", urgency: "ROUTINE", additionalNotes: "" })
  const [letterheadId, setLetterheadId] = useState("")
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")
  const [issued, setIssued] = useState<{ patientId: string; doc: IssuedDoc | null } | null>(null)
  const [sent, setSent] = useState("")
  const [sending, setSending] = useState(false)
  const mayEmail = useCan("emailDocuments")
  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => setForm(f => ({ ...f, [k]: e.target.value }))
  const canSend = form.reason.trim().length > 0 && !busy

  const generate = async () => {
    setBusy(true); setError("")
    try {
      const res = await apiClient.post(`/api/v1/clinic/consultations/${consultationId}/referral-letter`, null, { params: letterheadId ? { ...form, letterheadId } : form, responseType: "blob" } as any)
      saveBlob(res.data, `referral-letter-${consultationId}.pdf`)
      // Find the letter just issued, to show its number and offer sending it.
      try { const v = await visitDocuments(consultationId, "REFERRAL"); setIssued({ patientId: v.patientId, doc: v.documents[0] ?? null }) }
      catch { setIssued({ patientId: "", doc: null }) }
    } catch (e: any) {
      setError(await errorMessage(e, "Could not generate the referral letter."))
    } finally { setBusy(false) }
  }

  const send = async () => {
    if (!issued?.doc) return
    setSending(true); setError("")
    try { const r = await emailToPatient(issued.patientId, issued.doc.id); setSent(r.recipient) }
    catch (e: any) { setError(await errorMessage(e, "The letter could not be sent.")) }
    finally { setSending(false) }
  }

  if (issued) {
    const doc = issued.doc
    return (
      <ModalShell title="Referral letter issued" onClose={onClose} width={480}
        footer={<button onClick={onClose} style={{ padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", fontSize: 14, cursor: "pointer" }}>Close</button>}>
        <div style={{ fontSize: 14, color: "var(--hf-text)" }}>
          {doc ? <>Referral letter <b>{doc.number}</b> has been downloaded.</> : "The referral letter has been downloaded."}
        </div>
        <div style={{ fontSize: 13, color: "var(--hf-text-muted)", marginTop: 6 }}>Nothing has been sent to the patient. Sending a copy is a separate step.</div>
        {sent ? (
          <div role="status" style={{ marginTop: 14, fontSize: 13, color: "var(--hf-success-text-strong)" }}>Sent to {sent}.</div>
        ) : doc && mayEmail && sendable(doc) ? (
          <button onClick={send} disabled={sending} style={{ marginTop: 14, padding: "9px 18px", border: "none", borderRadius: 9, background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", fontSize: 14, fontWeight: 600, cursor: "pointer", opacity: sending ? 0.7 : 1 }}>
            {sending ? "Sending…" : "Email a copy to the patient"}
          </button>
        ) : null}
        {error && <div role="alert" style={{ marginTop: 14, padding: "8px 12px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 7, fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</div>}
      </ModalShell>
    )
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
        <div style={{ gridColumn: "1 / -1" }}><LetterheadSelect id="ref-head" value={letterheadId} onChange={setLetterheadId} style={inp} labelStyle={lab} /></div>
        <div style={{ gridColumn: "1 / -1" }}><SaveAsTemplate kind="REFERRAL" payload={() => ({ title: form.reason.trim() || undefined, body: form.additionalNotes.trim() || undefined, specialty: form.specialty.trim() || undefined, urgency: form.urgency })} /></div>
      </div>
      {error && <div role="alert" style={{ marginTop: 14, padding: "8px 12px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 7, fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</div>}
    </ModalShell>
  )
}
