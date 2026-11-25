// One practitioner: profile and appointments per role and programme. A check is recorded by a person; nothing here decides competence.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import {
  EMPLOYMENT, ROLES, STANDING, appointmentBody, appointmentProblem, checkNote, emptyAppointment, emptyVerify, label, needsCredential, personBody, personForm, personProblem,
  reasonProblem, standingTone, verifyBody, verifyProblem, emptyPerson, type Appointment, type AppointmentForm, type Practitioner, type PractitionerDetail, type PersonForm,
  type VerifyForm,
} from "./practitionerRules"
import { Badge, Field, Notice } from "./tpUi"
import { card, errText, fmtDate, ghost, input, muted, solid, todayIso, unwrap } from "./tpStyle"
import { PRC, useActiveProgrammes, useAuthorities } from "./tpApi"

export function PersonFormPanel({ initial, onSaved, onCancel }: { initial?: Practitioner; onSaved: (p: Practitioner) => void; onCancel: () => void }) {
  const [f, setF] = useState<PersonForm>(initial ? personForm(initial) : emptyPerson)
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const set = (k: keyof PersonForm) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => setF({ ...f, [k]: e.target.value })
  const save = async () => {
    const p = personProblem(f); if (p) { setError(p); return }
    setBusy(true); setError("")
    try {
      const body = personBody(f)
      onSaved(unwrap<Practitioner>(await (initial ? apiClient.put(`${PRC}/${initial.id}`, body) : apiClient.post(PRC, body))))
    } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  return (
    <div style={{ ...card, display: "grid", gap: 8, background: "var(--hf-surface-muted)" }}>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
        <input aria-label="First names" style={input} placeholder="First names" value={f.firstNames} onChange={set("firstNames")} />
        <input aria-label="Surname" style={input} placeholder="Surname" value={f.surname} onChange={set("surname")} />
        <input aria-label="Email" style={input} placeholder="Email" value={f.email} onChange={set("email")} />
        <input aria-label="Phone" style={input} placeholder="Phone" value={f.phone} onChange={set("phone")} />
        <select aria-label="Employment" style={input} value={f.employmentType} onChange={set("employmentType")}>{Object.entries(EMPLOYMENT).map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select>
        <input aria-label="Notes" style={input} placeholder="Notes" value={f.notes} onChange={set("notes")} />
      </div>
      <Notice text={error} />
      <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={save}>{initial ? "Save changes" : "Add person"}</button><button style={ghost} onClick={onCancel}>Cancel</button></div>
    </div>
  )
}

function AppointmentForm({ onSaved, onCancel, personId }: { personId: string; onSaved: () => void; onCancel: () => void }) {
  const { data: programmes = [] } = useActiveProgrammes()
  const { data: authorities = [] } = useAuthorities()
  const [f, setF] = useState<AppointmentForm>(emptyAppointment)
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const set = (k: keyof AppointmentForm) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => setF({ ...f, [k]: e.target.value })
  const save = async () => {
    const p = appointmentProblem(f); if (p) { setError(p); return }
    setBusy(true); setError("")
    try { await apiClient.post(`${PRC}/${personId}/appointments`, appointmentBody(f)); onSaved() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  return (
    <div style={{ ...card, display: "grid", gap: 8, background: "var(--hf-surface-muted)" }}>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
        <select aria-label="Role" style={input} value={f.role} onChange={set("role")}>{Object.entries(ROLES).map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select>
        <select aria-label="Programme" style={input} value={f.programmeId} onChange={set("programmeId")}><option value="">Choose the programme</option>{programmes.map(p => <option key={p.id} value={p.id}>{p.code} · {p.title}</option>)}</select>
        <input aria-label="Credential reference" style={input} placeholder={needsCredential(f.role) ? "Credential reference, as written on the certificate or register" : "Credential reference (optional)"} value={f.credentialRef} onChange={set("credentialRef")} />
        <select aria-label="Authority" style={input} value={f.authorityId} onChange={set("authorityId")}><option value="">Issuing authority (optional)</option>{authorities.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}</select>
        <Field label="Valid from"><input aria-label="Valid from" type="date" style={input} value={f.validFrom} onChange={set("validFrom")} /></Field>
        <Field label="Valid to"><input aria-label="Valid to" type="date" style={input} value={f.validTo} onChange={set("validTo")} /></Field>
      </div>
      <input aria-label="Credential note" style={input} placeholder="Note (optional)" value={f.credentialNote} onChange={set("credentialNote")} />
      <div style={muted}>Saving records the details as typed. It does not mean the credential has been checked.</div>
      <Notice text={error} />
      <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={save}>Add appointment</button><button style={ghost} onClick={onCancel}>Cancel</button></div>
    </div>
  )
}

function AppointmentCard({ a, canManage, onChanged }: { a: Appointment; canManage: boolean; onChanged: () => void }) {
  const [mode, setMode] = useState<"" | "verify" | "withdraw" | "history">("")
  const [v, setV] = useState<VerifyForm>(emptyVerify(todayIso()))
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true); setError("")
    try { await fn(); setMode(""); onChanged() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  const verify = () => {
    const p = verifyProblem(a.role, a.credentialRef, v, todayIso()); if (p) { setError(p); return }
    void run(() => apiClient.post(`${PRC}/appointments/${a.id}/verify`, verifyBody(v)))
  }
  const withdraw = () => {
    const p = reasonProblem(reason); if (p) { setError(p); return }
    void run(() => apiClient.post(`${PRC}/appointments/${a.id}/withdraw`, { reason: reason.trim() }))
  }
  return (
    <div style={card}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
        <div>
          <strong>{label(ROLES, a.role)} · {a.programmeTitle}</strong>
          <div style={muted}>Credential reference: {a.credentialRef ?? "none recorded"}{a.authorityName ? ` · ${a.authorityName}` : ""} · {fmtDate(a.validFrom)} to {a.validTo ?? "no end date"}</div>
        </div>
        <Badge text={label(STANDING, a.standing)} tone={standingTone(a.standing)} />
      </div>
      <div style={muted}>{checkNote(a)}</div>
      {a.status === "WITHDRAWN" && <div style={muted}>Withdrawn: {a.withdrawnReason}</div>}
      {mode === "verify" && (
        <div style={{ display: "grid", gap: 8, marginTop: 8 }}>
          <input aria-label="What was checked" style={input} placeholder="What you checked, for example the register entry" value={v.source} onChange={e => setV({ ...v, source: e.target.value })} />
          <Field label="Date checked"><input aria-label="Date checked" type="date" style={input} value={v.on} onChange={e => setV({ ...v, on: e.target.value })} /></Field>
          {needsCredential(a.role) && <label style={{ fontSize: 13 }}><input type="checkbox" checked={v.noSecondPerson} onChange={e => setV({ ...v, noSecondPerson: e.target.checked })} /> I set this appointment up and no second person can check it</label>}
          {v.noSecondPerson && <input aria-label="Why no second person" style={input} placeholder="Why no second person can check it" value={v.overrideReason} onChange={e => setV({ ...v, overrideReason: e.target.value })} />}
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={verify}>Record the check</button><button style={ghost} onClick={() => { setMode(""); setError("") }}>Cancel</button></div>
        </div>
      )}
      {mode === "withdraw" && (
        <div style={{ display: "grid", gap: 8, marginTop: 8 }}>
          <input aria-label="Reason for withdrawing" style={input} placeholder="Why is this appointment ending?" value={reason} onChange={e => setReason(e.target.value)} />
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={withdraw}>Withdraw appointment</button><button style={ghost} onClick={() => { setMode(""); setError("") }}>Cancel</button></div>
        </div>
      )}
      {mode === "history" && <div style={{ marginTop: 8, display: "grid", gap: 4 }}>{a.history.map(h => <div key={h.id} style={muted}>{h.actedAt.slice(0, 10)} · {h.action.toLowerCase()}{h.note ? ` · ${h.note}` : ""}</div>)}</div>}
      {mode === "" && (
        <div style={{ display: "flex", gap: 8, marginTop: 8, flexWrap: "wrap" }}>
          {canManage && a.status === "ACTIVE" && <button style={ghost} onClick={() => setMode("verify")}>Record a check</button>}
          {canManage && a.status === "ACTIVE" && <button style={ghost} onClick={() => setMode("withdraw")}>Withdraw</button>}
          <button style={ghost} onClick={() => setMode("history")}>History</button>
        </div>
      )}
    </div>
  )
}

export default function TrainProvPractitionerDetail({ id, onBack }: { id: string; onBack: () => void }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["TRAININGPROVIDER_MANAGE", "TRAININGPROVIDER_ADMIN"])
  const isAdmin = usePermissions(["TRAININGPROVIDER_ADMIN"])
  const [editing, setEditing] = useState(false)
  const [adding, setAdding] = useState(false)
  const [deactivating, setDeactivating] = useState(false)
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")
  const { data, isLoading } = useQuery<PractitionerDetail>({ queryKey: ["tp-practitioner", id], queryFn: async () => unwrap<PractitionerDetail>(await apiClient.get(`${PRC}/${id}`)) })
  const refresh = () => { void qc.invalidateQueries({ queryKey: ["tp-practitioner", id] }); void qc.invalidateQueries({ queryKey: ["tp-practitioners"] }) }
  if (isLoading || !data) return <div style={muted}>Loading…</div>
  const p = data.practitioner
  const deactivate = async () => {
    const pr = reasonProblem(reason); if (pr) { setError(pr); return }
    try { await apiClient.post(`${PRC}/${id}/deactivate`, { reason: reason.trim() }); setDeactivating(false); setReason(""); refresh() } catch (e) { setError(errText(e)) }
  }
  const reactivate = async () => { try { await apiClient.post(`${PRC}/${id}/reactivate`, {}); refresh() } catch (e) { setError(errText(e)) } }
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div><button style={ghost} onClick={onBack}>Back to people</button></div>
      {editing ? <PersonFormPanel initial={p} onCancel={() => setEditing(false)} onSaved={() => { setEditing(false); refresh() }} /> : (
        <div style={card}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
            <div><strong>{p.firstNames} {p.surname}</strong><div style={muted}>{p.practitionerNo} · {label(EMPLOYMENT, p.employmentType)}{p.email ? ` · ${p.email}` : ""}{p.phone ? ` · ${p.phone}` : ""}</div></div>
            <Badge text={p.status === "ACTIVE" ? "Active" : "Inactive"} tone={p.status === "ACTIVE" ? "good" : "neutral"} />
          </div>
          {p.notes && <div style={muted}>{p.notes}</div>}
          {p.inactiveReason && <div style={muted}>Made inactive: {p.inactiveReason}</div>}
          <div style={{ display: "flex", gap: 8, marginTop: 8, flexWrap: "wrap" }}>
            {canManage && <button style={ghost} onClick={() => setEditing(true)}>Edit details</button>}
            {isAdmin && p.status === "ACTIVE" && !deactivating && <button style={ghost} onClick={() => setDeactivating(true)}>Make inactive</button>}
            {isAdmin && p.status === "INACTIVE" && <button style={ghost} onClick={reactivate}>Make active again</button>}
          </div>
          {deactivating && (
            <div style={{ display: "grid", gap: 8, marginTop: 8 }}>
              <input aria-label="Reason for making inactive" style={input} placeholder="Why is this person no longer active?" value={reason} onChange={e => setReason(e.target.value)} />
              <div style={{ display: "flex", gap: 8 }}><button style={solid} onClick={deactivate}>Make inactive</button><button style={ghost} onClick={() => { setDeactivating(false); setError("") }}>Cancel</button></div>
            </div>
          )}
          <Notice text={error} />
        </div>
      )}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <strong>Appointments</strong>
        {canManage && p.status === "ACTIVE" && !adding && <button style={solid} onClick={() => setAdding(true)}>Add an appointment</button>}
      </div>
      {adding && <AppointmentForm personId={id} onCancel={() => setAdding(false)} onSaved={() => { setAdding(false); refresh() }} />}
      {data.appointments.length === 0 ? <div style={muted}>No appointments yet.</div> : data.appointments.map(a => <AppointmentCard key={a.id} a={a} canManage={canManage} onChanged={refresh} />)}
    </div>
  )
}
