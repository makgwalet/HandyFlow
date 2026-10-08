// Quick sick note and referral letter from the patient overview. Both documents are written for a consultation,
// so the newest one is offered and the clinician can pick another. With no consultation yet, the buttons say why they are off.
import { useState } from "react"
import { FileText, Send } from "lucide-react"
import { usePermission } from "../../hooks/usePermission"
import ModalShell from "./ModalShell"
import ReferralLetterModal from "./ReferralLetterModal"
import { downloadPdf, fmtDT, lbl, sinp } from "./patientFile.shared"
import { Card, primaryBtn, smallBtn } from "./OverviewCard"
import { certParams, certProblem, newestFirst, type CertForm } from "./historyView"

interface Cons { id: string; consultedAt: string; chiefComplaint?: string }

const label = (c: Cons) => `${fmtDT(c.consultedAt)}${c.chiefComplaint ? ` — ${c.chiefComplaint}` : ""}`

function ConsultationSelect({ list, value, onChange }: { list: Cons[]; value: string; onChange: (v: string) => void }) {
  return (
    <div><label style={lbl} htmlFor="qa-consultation">Consultation *</label>
      <select id="qa-consultation" value={value} onChange={e => onChange(e.target.value)} style={sinp}>
        {list.map(c => <option key={c.id} value={c.id}>{label(c)}</option>)}
      </select></div>
  )
}

export function SickNoteModal({ patientId, list, onClose }: { patientId: string; list: Cons[]; onClose: () => void }) {
  const [f, setF] = useState<CertForm>({ consultationId: list[0]?.id ?? "", unfitFrom: "", unfitTo: "", notes: "" })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")
  const go = async () => {
    const p = certProblem(f); if (p) { setError(p); return }
    setBusy(true); setError("")
    try { await downloadPdf(`/api/v1/clinic/consultations/${f.consultationId}/medical-certificate?${certParams(f)}`, `sick-note-${patientId}.pdf`); onClose() }
    catch (e: any) { setError(e?.response?.data?.message ?? "Could not generate the sick note.") }
    finally { setBusy(false) }
  }
  return (
    <ModalShell title="Sick note" onClose={onClose} width={520}
      footer={<><button onClick={onClose} style={smallBtn}>Cancel</button><button onClick={go} disabled={busy} style={{ ...primaryBtn, padding: "9px 18px", fontSize: 14 }}>{busy ? "Generating…" : "Download sick note"}</button></>}>
      <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
        {list.length > 1 && <ConsultationSelect list={list} value={f.consultationId} onChange={v => setF({ ...f, consultationId: v })} />}
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
          <div><label style={lbl} htmlFor="qa-from">Unfit from</label><input id="qa-from" type="date" value={f.unfitFrom} onChange={e => setF({ ...f, unfitFrom: e.target.value })} style={sinp} /></div>
          <div><label style={lbl} htmlFor="qa-to">Unfit until</label><input id="qa-to" type="date" value={f.unfitTo} onChange={e => setF({ ...f, unfitTo: e.target.value })} style={sinp} /></div>
        </div>
        <div><label style={lbl} htmlFor="qa-notes">Notes</label><textarea id="qa-notes" rows={2} value={f.notes} onChange={e => setF({ ...f, notes: e.target.value })} style={{ ...sinp, resize: "vertical" }} /></div>
        {error && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</div>}
      </div>
    </ModalShell>
  )
}

function ReferralPicker({ list, onClose }: { list: Cons[]; onClose: () => void }) {
  const [id, setId] = useState(list[0]?.id ?? "")
  const [chosen, setChosen] = useState(list.length === 1)
  if (chosen && id) return <ReferralLetterModal consultationId={id} onClose={onClose} />
  return (
    <ModalShell title="Referral letter" onClose={onClose} width={520}
      footer={<><button onClick={onClose} style={smallBtn}>Cancel</button><button onClick={() => setChosen(true)} style={{ ...primaryBtn, padding: "9px 18px", fontSize: 14 }}>Continue</button></>}>
      <ConsultationSelect list={list} value={id} onChange={setId} />
    </ModalShell>
  )
}

export default function QuickActionsCard({ patientId, consultations }: { patientId: string; consultations: Cons[] }) {
  const canSick = usePermission("CLINIC_SICK_NOTE_SIGN")
  const canRef = usePermission("CLINIC_REFERRAL_SIGN")
  const [open, setOpen] = useState<"" | "sick" | "referral">("")
  if (!canSick && !canRef) return null
  const list = newestFirst(consultations)
  const none = list.length === 0
  const why = none ? "Needs a consultation: both documents are written for one." : undefined
  const btn = (text: string, Icon: typeof FileText, kind: "sick" | "referral") => (
    <button type="button" disabled={none} title={why} onClick={() => setOpen(kind)}
      style={{ display: "flex", alignItems: "center", gap: 8, padding: "9px 12px", borderRadius: 8, border: "1px solid var(--hf-border)", background: "var(--hf-surface)",
        fontSize: 13, fontWeight: 600, cursor: none ? "not-allowed" : "pointer", opacity: none ? 0.55 : 1, color: "var(--hf-text)" }}>
      <Icon size={14} />{text}</button>)
  return (
    <Card title="Quick actions">
      <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
        {canSick && btn("Sick note", FileText, "sick")}
        {canRef && btn("Referral letter", Send, "referral")}
        {none && <div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>{why}</div>}
      </div>
      {open === "sick" && <SickNoteModal patientId={patientId} list={list} onClose={() => setOpen("")} />}
      {open === "referral" && <ReferralPicker list={list} onClose={() => setOpen("")} />}
    </Card>
  )
}
