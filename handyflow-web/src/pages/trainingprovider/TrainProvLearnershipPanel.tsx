// Learnership agreement records for one enrolment. Loads only when opened.
// The agreement is a record of the paper agreement. The registration reference is kept as entered and is never verified or called registered.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import {
  LEARNERSHIP_NOTE, STATUSES, agreementBody, agreementForm, agreementProblem, emptyAgreement, emptySign, endProblem, label, latestSigned, reasonProblem, registrationProblem, signProblem,
  statusTone, type Agreement, type AgreementForm, type SignForm,
} from "./learnershipRules"
import { enrolmentOpen } from "./workplaceRules"
import { Badge, Notice } from "./tpUi"
import { card, errText, fmtDate, ghost, input, muted, solid, todayIso, unwrap } from "./tpStyle"
import { LSH, useClients } from "./tpApi"

function AgreementFormPanel({ enrolmentId, enrolmentStart, initial, onSaved, onCancel }: { enrolmentId: string; enrolmentStart: string | null; initial?: Agreement; onSaved: () => void; onCancel: () => void }) {
  const { data: clients = [] } = useClients()
  const [f, setF] = useState<AgreementForm>(initial ? agreementForm(initial) : emptyAgreement)
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const set = (k: keyof AgreementForm) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setF({ ...f, [k]: e.target.value })
  const save = async () => {
    const p = agreementProblem(f, enrolmentStart); if (p) { setError(p); return }
    setBusy(true); setError("")
    try {
      await (initial ? apiClient.put(`${LSH}/${initial.id}`, agreementBody(f)) : apiClient.post(LSH, { enrolmentId, ...agreementBody(f) }))
      onSaved()
    } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  return (
    <div style={{ ...card, display: "grid", gap: 8, background: "var(--hf-surface-muted)" }}>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
        <input aria-label="Employer" style={input} placeholder="Employer" value={f.employerName} onChange={set("employerName")} />
        <input aria-label="Employer contact" style={input} placeholder="Employer contact (optional)" value={f.employerContact} onChange={set("employerContact")} />
        <input aria-label="Planned start" type="date" style={input} value={f.startDate} onChange={set("startDate")} />
        <input aria-label="Planned end" type="date" style={input} value={f.endDate} onChange={set("endDate")} />
      </div>
      <select aria-label="Linked client" style={input} value={f.clientId} onChange={set("clientId")}><option value="">Employer is not a client</option>{clients.map(c => <option key={c.id} value={c.id}>{c.tradingName}</option>)}</select>
      <Notice text={error} />
      <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={save}>{initial ? "Save changes" : "Add agreement"}</button><button style={ghost} onClick={onCancel}>Cancel</button></div>
    </div>
  )
}

function AgreementCard({ a, enrolmentStart, onChanged }: { a: Agreement; enrolmentStart: string | null; onChanged: () => void }) {
  const canManage = usePermissions(["TRAININGPROVIDER_MANAGE", "TRAININGPROVIDER_ADMIN"])
  const [mode, setMode] = useState<"" | "edit" | "sign" | "registration" | "end" | "cancel">("")
  const [sign, setSign] = useState<SignForm>(emptySign)
  const [ref, setRef] = useState("")
  const [day, setDay] = useState(todayIso())
  const [text, setText] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const reset = () => { setMode(""); setSign(emptySign); setRef(""); setText(""); setError(""); setDay(todayIso()) }
  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true); setError("")
    try { await fn(); reset(); onChanged() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  const doSign = () => { const p = signProblem(sign, todayIso()); if (p) { setError(p); return } void run(() => apiClient.post(`${LSH}/${a.id}/sign`, sign)) }
  const doRegistration = () => { const p = registrationProblem(ref, day, todayIso()); if (p) { setError(p); return } void run(() => apiClient.post(`${LSH}/${a.id}/registration`, { reference: ref.trim(), recordedOn: day })) }
  const doEnd = () => { const p = endProblem(day, latestSigned(a), text, todayIso()); if (p) { setError(p); return } void run(() => apiClient.post(`${LSH}/${a.id}/end`, { endedOn: day, reason: text.trim() })) }
  const doCancel = () => { const p = reasonProblem(text); if (p) { setError(p); return } void run(() => apiClient.post(`${LSH}/${a.id}/cancel`, { reason: text.trim() })) }
  if (mode === "edit") return <AgreementFormPanel enrolmentId={a.enrolmentId} enrolmentStart={enrolmentStart} initial={a} onCancel={reset} onSaved={() => { reset(); onChanged() }} />
  const live = a.status === "DRAFT" || a.status === "SIGNED"
  return (
    <div style={{ ...card, display: "grid", gap: 6 }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
        <div>
          <strong>{a.employerName}</strong>
          <div style={muted}>{a.agreementNo} · planned {fmtDate(a.startDate)}{a.endDate ? ` to ${fmtDate(a.endDate)}` : ""}{a.employerContact ? ` · ${a.employerContact}` : ""}</div>
        </div>
        <Badge text={label(STATUSES, a.status)} tone={statusTone(a.status)} />
      </div>
      <div style={muted}>{a.summary}</div>
      {a.endedOn && <div style={muted}>Ended {fmtDate(a.endedOn)}: {a.endReason}</div>}
      {a.cancelReason && <div style={muted}>Cancelled: {a.cancelReason}</div>}
      {canManage && mode === "" && (
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          {a.status === "DRAFT" && <button style={ghost} onClick={() => setMode("edit")}>Edit</button>}
          {a.status === "DRAFT" && <button style={ghost} onClick={() => setMode("sign")}>Record signing</button>}
          {a.status === "SIGNED" && !a.registrationRef && <button style={ghost} onClick={() => setMode("registration")}>Record registration reference</button>}
          {a.status === "SIGNED" && <button style={ghost} onClick={() => setMode("end")}>End agreement</button>}
          {live && !a.registrationRef && <button style={ghost} onClick={() => setMode("cancel")}>Cancel agreement</button>}
        </div>
      )}
      {mode === "sign" && (
        <div style={{ display: "grid", gap: 6 }}>
          <input aria-label="Learner signed on" type="date" style={input} value={sign.learnerSignedOn} onChange={e => setSign({ ...sign, learnerSignedOn: e.target.value })} />
          <input aria-label="Employer signed on" type="date" style={input} value={sign.employerSignedOn} onChange={e => setSign({ ...sign, employerSignedOn: e.target.value })} />
          <input aria-label="Provider signed on" type="date" style={input} value={sign.providerSignedOn} onChange={e => setSign({ ...sign, providerSignedOn: e.target.value })} />
          <div style={muted}>Once signing is recorded the details can no longer be changed.</div>
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={doSign}>Save signing</button><button style={ghost} onClick={reset}>Cancel</button></div>
        </div>
      )}
      {mode === "registration" && (
        <div style={{ display: "grid", gap: 6 }}>
          <input aria-label="Registration reference" style={input} placeholder="Reference exactly as it appears on the document" value={ref} onChange={e => setRef(e.target.value)} />
          <input aria-label="Day recorded" type="date" style={input} value={day} onChange={e => setDay(e.target.value)} />
          <div style={muted}>The reference is kept as entered. It is not checked against any register.</div>
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={doRegistration}>Save reference</button><button style={ghost} onClick={reset}>Cancel</button></div>
        </div>
      )}
      {mode === "end" && (
        <div style={{ display: "grid", gap: 6 }}>
          <input aria-label="Day the agreement ended" type="date" style={input} value={day} onChange={e => setDay(e.target.value)} />
          <input aria-label="Reason the agreement ended" style={input} placeholder="Why did the agreement end?" value={text} onChange={e => setText(e.target.value)} />
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={doEnd}>Save as ended</button><button style={ghost} onClick={reset}>Cancel</button></div>
        </div>
      )}
      {mode === "cancel" && (
        <div style={{ display: "grid", gap: 6 }}>
          <input aria-label="Reason for cancelling the agreement" style={input} placeholder="Why is this agreement being cancelled?" value={text} onChange={e => setText(e.target.value)} />
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={doCancel}>Cancel the agreement</button><button style={ghost} onClick={reset}>Back</button></div>
        </div>
      )}
    </div>
  )
}

export default function TrainProvLearnershipPanel({ enrolmentId, enrolmentStatus, startDate }: { enrolmentId: string; enrolmentStatus: string; startDate: string | null }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["TRAININGPROVIDER_MANAGE", "TRAININGPROVIDER_ADMIN"])
  const [open, setOpen] = useState(false)
  const [adding, setAdding] = useState(false)
  const { data: agreements = [] } = useQuery<Agreement[]>({
    queryKey: ["tp-learnerships", enrolmentId], enabled: open,
    queryFn: async () => unwrap<Agreement[]>(await apiClient.get(LSH, { params: { enrolmentId } })),
  })
  if (!open) return <div style={{ marginTop: 8 }}><button style={ghost} onClick={() => setOpen(true)}>Learnership</button></div>
  const changed = () => { void qc.invalidateQueries({ queryKey: ["tp-learnerships", enrolmentId] }) }
  const hasLive = agreements.some(a => a.status === "DRAFT" || a.status === "SIGNED")
  return (
    <div style={{ ...card, marginTop: 8, display: "grid", gap: 8 }}>
      <div style={muted}>{LEARNERSHIP_NOTE}</div>
      {agreements.length === 0 && <div style={muted}>No learnership agreement yet.</div>}
      {agreements.map(a => <AgreementCard key={a.id} a={a} enrolmentStart={startDate} onChanged={changed} />)}
      {adding && <AgreementFormPanel enrolmentId={enrolmentId} enrolmentStart={startDate} onCancel={() => setAdding(false)} onSaved={() => { setAdding(false); changed() }} />}
      {canManage && !adding && (!enrolmentOpen(enrolmentStatus)
        ? <div style={muted}>An agreement can be added while the enrolment is enrolled or in progress.</div>
        : hasLive ? <div style={muted}>This enrolment already has a live agreement. Cancel it before adding another.</div>
          : <div><button style={solid} onClick={() => setAdding(true)}>Add an agreement</button></div>)}
      <div><button style={ghost} onClick={() => setOpen(false)}>Hide</button></div>
    </div>
  )
}
