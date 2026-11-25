// One cohort: learners, facilitators, timetable and attendance. Facilitators are chosen only from people whose appointment is checked and in force.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import {
  COHORT_STATUS, cohortBody, cohortProblem, cohortTone, emptySlot, label, reasonProblem, slotBody, slotForm, slotProblem, standingWarning, transitionBody, transitionProblem,
  type Cohort, type CohortDetail, type CohortForm, type Slot, type SlotForm, type TransitionForm,
} from "./cohortRules"
import type { Enrolment } from "./learnerRules"
import type { Eligible } from "./practitionerRules"
import { Badge, Field, Notice, SubTabs } from "./tpUi"
import { card, errText, ghost, input, muted, solid, unwrap } from "./tpStyle"
import { CAT, LRN, PRC, useActiveProgrammes } from "./tpApi"
import { RegisterPanel, SummaryPanel } from "./TrainProvAttendancePanel"

type ModuleOption = { id: string; title: string }
type ProgrammeDetail = { versions: { versionNo: number; modules: ModuleOption[] }[] }

export function CohortFormPanel({ initial, onSaved, onCancel }: { initial?: Cohort; onSaved: (c: Cohort) => void; onCancel: () => void }) {
  const { data: programmes = [] } = useActiveProgrammes()
  const { data: sites = [] } = useQuery<{ id: string; name: string; status: string }[]>({ queryKey: ["tp-sites"], queryFn: async () => unwrap(await apiClient.get("/api/v1/training-provider/accreditation/sites")) })
  const [f, setF] = useState<CohortForm>(initial
    ? { name: initial.name, programmeId: initial.programmeId, siteId: initial.siteId, startDate: initial.startDate, endDate: initial.endDate ?? "", capacity: initial.capacity?.toString() ?? "", notes: initial.notes ?? "" }
    : { name: "", programmeId: "", siteId: "", startDate: "", endDate: "", capacity: "", notes: "" })
  const [error, setError] = useState("")
  const set = (k: keyof CohortForm) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setF({ ...f, [k]: e.target.value })
  const save = async () => {
    const p = cohortProblem(f, !initial); if (p) { setError(p); return }
    setError("")
    try {
      const body = cohortBody(f)
      onSaved(unwrap<Cohort>(await (initial ? apiClient.put(`${LRN}/cohorts/${initial.id}`, body) : apiClient.post(`${LRN}/cohorts`, body))))
    } catch (e) { setError(errText(e)) }
  }
  return (
    <div style={{ ...card, display: "grid", gap: 8, background: "var(--hf-surface-muted)" }}>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
        <input aria-label="Cohort name" style={input} placeholder="Cohort name, for example February intake" value={f.name} onChange={set("name")} />
        <input aria-label="Capacity" style={input} placeholder="Capacity (optional)" value={f.capacity} onChange={set("capacity")} />
        {!initial && <select aria-label="Programme" style={input} value={f.programmeId} onChange={set("programmeId")}><option value="">Choose the programme</option>{programmes.map(p => <option key={p.id} value={p.id}>{p.code} · {p.title}</option>)}</select>}
        {!initial && <select aria-label="Site" style={input} value={f.siteId} onChange={set("siteId")}><option value="">Choose the site</option>{sites.filter(s => s.status === "ACTIVE").map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select>}
        <Field label="Starts"><input aria-label="Start date" type="date" style={input} value={f.startDate} onChange={set("startDate")} /></Field>
        <Field label="Ends (optional)"><input aria-label="End date" type="date" style={input} value={f.endDate} onChange={set("endDate")} /></Field>
      </div>
      <input aria-label="Notes" style={input} placeholder="Notes (optional)" value={f.notes} onChange={set("notes")} />
      {!initial && <div style={muted}>The cohort is opened on the programme's approved version and stays on it.</div>}
      <Notice text={error} />
      <div style={{ display: "flex", gap: 8 }}><button style={solid} onClick={save}>{initial ? "Save changes" : "Open cohort"}</button><button style={ghost} onClick={onCancel}>Cancel</button></div>
    </div>
  )
}

function SlotFormPanel({ cohort, initial, onSaved, onCancel }: { cohort: Cohort; initial?: Slot; onSaved: () => void; onCancel: () => void }) {
  const { data: venues = [] } = useQuery<{ id: string; name: string; siteId: string; status: string }[]>({ queryKey: ["tp-venues", cohort.siteId], queryFn: async () => unwrap(await apiClient.get(`${LRN}/venues`, { params: { siteId: cohort.siteId, status: "ACTIVE" } })) })
  const { data: people = [] } = useQuery<Eligible[]>({ queryKey: ["tp-cohort-facilitator-options", cohort.id], queryFn: async () => unwrap(await apiClient.get(`${PRC}/eligible`, { params: { programmeId: cohort.programmeId, role: "FACILITATOR", on: cohort.startDate } })) })
  const { data: programme } = useQuery<ProgrammeDetail>({ queryKey: ["tp-programme-modules", cohort.programmeId], queryFn: async () => unwrap(await apiClient.get(`${CAT}/programmes/${cohort.programmeId}`)) })
  const modules = programme?.versions.find(v => v.versionNo === cohort.programmeVersionNo)?.modules ?? []
  const [f, setF] = useState<SlotForm>(initial ? slotForm(initial) : emptySlot)
  const [error, setError] = useState("")
  const set = (k: keyof SlotForm) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setF({ ...f, [k]: e.target.value })
  const save = async () => {
    const p = slotProblem(f, cohort); if (p) { setError(p); return }
    setError("")
    try { await (initial ? apiClient.put(`${LRN}/timetable/${initial.id}`, slotBody(f)) : apiClient.post(`${LRN}/cohorts/${cohort.id}/timetable`, slotBody(f))); onSaved() } catch (e) { setError(errText(e)) }
  }
  return (
    <div style={{ ...card, display: "grid", gap: 8, background: "var(--hf-surface-muted)" }}>
      <input aria-label="Slot title" style={input} placeholder="Title, for example Module 1: Safety basics" value={f.title} onChange={set("title")} />
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 8 }}>
        <input aria-label="Slot date" type="date" style={input} value={f.slotDate} onChange={set("slotDate")} />
        <input aria-label="Start time" type="time" style={input} value={f.startTime} onChange={set("startTime")} />
        <input aria-label="End time" type="time" style={input} value={f.endTime} onChange={set("endTime")} />
        <select aria-label="Module" style={input} value={f.moduleId} onChange={set("moduleId")}><option value="">No module</option>{modules.map(m => <option key={m.id} value={m.id}>{m.title}</option>)}</select>
        <select aria-label="Venue" style={input} value={f.venueId} onChange={set("venueId")}><option value="">No venue yet</option>{venues.map(v => <option key={v.id} value={v.id}>{v.name}</option>)}</select>
        <select aria-label="Facilitator" style={input} value={f.practitionerId} onChange={set("practitionerId")}><option value="">No facilitator yet</option>{people.map(p => <option key={p.practitionerId} value={p.practitionerId}>{p.name}</option>)}</select>
      </div>
      <div style={muted}>Venues and facilitators cannot be booked twice at the same time. A facilitator must be on the cohort and checked and in force on the slot's date.</div>
      <Notice text={error} />
      <div style={{ display: "flex", gap: 8 }}><button style={solid} onClick={save}>{initial ? "Save changes" : "Add slot"}</button><button style={ghost} onClick={onCancel}>Cancel</button></div>
    </div>
  )
}

function Timetable({ cohort, canManage, open }: { cohort: Cohort; canManage: boolean; open: boolean }) {
  const qc = useQueryClient()
  const [adding, setAdding] = useState(false)
  const [editing, setEditing] = useState<Slot | null>(null)
  const [cancelling, setCancelling] = useState<string | null>(null)
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")
  const { data = [], isLoading } = useQuery<Slot[]>({ queryKey: ["tp-timetable", cohort.id], queryFn: async () => unwrap<Slot[]>(await apiClient.get(`${LRN}/cohorts/${cohort.id}/timetable`)) })
  const refresh = () => { void qc.invalidateQueries({ queryKey: ["tp-timetable", cohort.id] }); void qc.invalidateQueries({ queryKey: ["tp-attendance-summary", cohort.id] }) }
  const cancel = async (id: string) => {
    const p = reasonProblem(reason); if (p) { setError(p); return }
    try { await apiClient.post(`${LRN}/timetable/${id}/cancel`, { reason: reason.trim() }); setCancelling(null); setReason(""); setError(""); refresh() } catch (e) { setError(errText(e)) }
  }
  return (
    <div style={{ display: "grid", gap: 8 }}>
      {canManage && open && !adding && !editing && <div><button style={solid} onClick={() => setAdding(true)}>Add a slot</button></div>}
      {adding && <SlotFormPanel cohort={cohort} onCancel={() => setAdding(false)} onSaved={() => { setAdding(false); refresh() }} />}
      {editing && <SlotFormPanel cohort={cohort} initial={editing} onCancel={() => setEditing(null)} onSaved={() => { setEditing(null); refresh() }} />}
      {isLoading ? <div style={muted}>Loading…</div> : data.length === 0 ? <div style={muted}>Nothing on the timetable yet.</div> : data.map(s => (
        <div key={s.id} style={card}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
            <div>
              <strong>{s.title}</strong>
              <div style={muted}>{s.slotDate} · {s.startTime.slice(0, 5)} to {s.endTime.slice(0, 5)}{s.moduleTitle ? ` · ${s.moduleTitle}` : ""}{s.venueName ? ` · ${s.venueName}` : " · no venue yet"}{s.facilitatorName ? ` · ${s.facilitatorName}` : " · no facilitator yet"}</div>
            </div>
            <Badge text={s.status === "SCHEDULED" ? "Scheduled" : "Cancelled"} tone={s.status === "SCHEDULED" ? "good" : "neutral"} />
          </div>
          {s.status === "SCHEDULED" && standingWarning(s.facilitatorStanding) && <div role="alert" style={{ fontSize: 12, color: "var(--hf-warning-text-strong)" }}>{standingWarning(s.facilitatorStanding)}. {s.facilitatorStandingNote}</div>}
          {s.cancelledReason && <div style={muted}>Cancelled: {s.cancelledReason}</div>}
          {canManage && open && s.status === "SCHEDULED" && cancelling !== s.id && (
            <div style={{ display: "flex", gap: 8, marginTop: 8 }}><button style={ghost} onClick={() => setEditing(s)}>Change</button><button style={ghost} onClick={() => { setCancelling(s.id); setError("") }}>Cancel slot</button></div>
          )}
          {cancelling === s.id && (
            <div style={{ display: "grid", gap: 8, marginTop: 8 }}>
              <input aria-label="Reason for cancelling the slot" style={input} placeholder="Why is this slot cancelled?" value={reason} onChange={e => setReason(e.target.value)} />
              <Notice text={error} />
              <div style={{ display: "flex", gap: 8 }}><button style={solid} onClick={() => cancel(s.id)}>Cancel the slot</button><button style={ghost} onClick={() => { setCancelling(null); setError("") }}>Keep it</button></div>
            </div>
          )}
        </div>
      ))}
    </div>
  )
}

function Attendance({ cohort }: { cohort: Cohort }) {
  const [slot, setSlot] = useState<Slot | null>(null)
  const { data = [] } = useQuery<Slot[]>({ queryKey: ["tp-timetable", cohort.id], queryFn: async () => unwrap<Slot[]>(await apiClient.get(`${LRN}/cohorts/${cohort.id}/timetable`)) })
  if (slot) return <RegisterPanel slot={slot} onClose={() => setSlot(null)} />
  return (
    <div style={{ display: "grid", gap: 12 }}>
      <strong>Registers</strong>
      {data.filter(s => s.status === "SCHEDULED").length === 0 ? <div style={muted}>Add slots to the timetable to take registers.</div> : data.filter(s => s.status === "SCHEDULED").map(s => (
        <button key={s.id} onClick={() => setSlot(s)} style={{ ...card, textAlign: "left", background: "transparent", cursor: "pointer", color: "inherit" }}>
          <strong>{s.title}</strong><div style={muted}>{s.slotDate} · {s.startTime.slice(0, 5)} to {s.endTime.slice(0, 5)}</div>
        </button>
      ))}
      <strong>Counts for each learner</strong>
      <SummaryPanel cohortId={cohort.id} />
    </div>
  )
}

function People({ detail, canManage, onChanged }: { detail: CohortDetail; canManage: boolean; onChanged: () => void }) {
  const c = detail.cohort
  const open = c.status === "PLANNED" || c.status === "RUNNING"
  const [enrolmentId, setEnrolmentId] = useState("")
  const [personId, setPersonId] = useState("")
  const [removing, setRemoving] = useState<string | null>(null)
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")
  const { data: enrolments = [] } = useQuery<Enrolment[]>({ queryKey: ["tp-cohort-enrolment-options", c.programmeId], enabled: open, queryFn: async () => unwrap(await apiClient.get(`${LRN}/learner-enrolments`, { params: { programmeId: c.programmeId } })) })
  const { data: people = [] } = useQuery<Eligible[]>({ queryKey: ["tp-cohort-facilitator-options", c.id], enabled: open, queryFn: async () => unwrap(await apiClient.get(`${PRC}/eligible`, { params: { programmeId: c.programmeId, role: "FACILITATOR", on: c.startDate } })) })
  const inCohort = new Set(detail.members.filter(m => m.status === "ACTIVE").map(m => m.enrolmentId))
  const candidates = enrolments.filter(e => e.siteId === c.siteId && e.programmeVersionNo === c.programmeVersionNo && (e.status === "ENROLLED" || e.status === "IN_PROGRESS") && !inCohort.has(e.id))
  const assigned = new Set(detail.facilitators.filter(f => f.status === "ACTIVE").map(f => f.practitionerId))
  const run = async (fn: () => Promise<unknown>) => { setError(""); try { await fn(); setRemoving(null); setReason(""); setEnrolmentId(""); setPersonId(""); onChanged() } catch (e) { setError(errText(e)) } }
  const remove = (kind: "cohort-members" | "cohort-facilitators", id: string) => {
    const p = reasonProblem(reason); if (p) { setError(p); return }
    void run(() => apiClient.post(`${LRN}/${kind}/${id}/remove`, { reason: reason.trim() }))
  }
  const removeBox = (kind: "cohort-members" | "cohort-facilitators", id: string) => removing === id ? (
    <div style={{ display: "grid", gap: 8, marginTop: 8 }}>
      <input aria-label="Reason for removing" style={input} placeholder="Why is this person being removed?" value={reason} onChange={e => setReason(e.target.value)} />
      <div style={{ display: "flex", gap: 8 }}><button style={solid} onClick={() => remove(kind, id)}>Remove</button><button style={ghost} onClick={() => { setRemoving(null); setError("") }}>Keep</button></div>
    </div>
  ) : canManage && open ? <div style={{ marginTop: 8 }}><button style={ghost} onClick={() => { setRemoving(id); setError("") }}>Remove</button></div> : null
  return (
    <div style={{ display: "grid", gap: 14 }}>
      <Notice text={error} />
      <div style={{ display: "grid", gap: 8 }}>
        <strong>Facilitators</strong>
        {canManage && open && (
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <select aria-label="Add a facilitator" style={{ ...input, width: "auto" }} value={personId} onChange={e => setPersonId(e.target.value)}>
              <option value="">Choose a checked facilitator</option>{people.filter(p => !assigned.has(p.practitionerId)).map(p => <option key={p.practitionerId} value={p.practitionerId}>{p.name}</option>)}
            </select>
            <button style={solid} disabled={!personId} onClick={() => run(() => apiClient.post(`${LRN}/cohorts/${c.id}/facilitators`, { practitionerId: personId }))}>Assign</button>
          </div>
        )}
        {open && people.length === 0 && canManage && <div style={muted}>Nobody has a checked, in-force facilitator appointment for this programme. Add and check one under Facilitators & assessors.</div>}
        {detail.facilitators.length === 0 ? <div style={muted}>No facilitators assigned yet.</div> : detail.facilitators.map(f => (
          <div key={f.id} style={card}>
            <div style={{ display: "flex", justifyContent: "space-between", gap: 8 }}><div><strong>{f.name}</strong><div style={muted}>{f.practitionerNo}</div></div><Badge text={f.status === "ACTIVE" ? "Assigned" : "Removed"} tone={f.status === "ACTIVE" ? "good" : "neutral"} /></div>
            {f.status === "ACTIVE" && f.standing !== "VERIFIED_IN_FORCE" && <div role="alert" style={{ fontSize: 12, color: "var(--hf-warning-text-strong)" }}>Appointment today: {f.standingNote}</div>}
            {f.removedReason && <div style={muted}>Removed: {f.removedReason}</div>}
            {f.status === "ACTIVE" && removeBox("cohort-facilitators", f.id)}
          </div>
        ))}
      </div>
      <div style={{ display: "grid", gap: 8 }}>
        <strong>Learners ({c.members}{c.capacity ? ` of ${c.capacity}` : ""})</strong>
        {canManage && open && (
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <select aria-label="Add a learner" style={{ ...input, width: "auto" }} value={enrolmentId} onChange={e => setEnrolmentId(e.target.value)}>
              <option value="">Choose an enrolled learner</option>{candidates.map(e => <option key={e.id} value={e.id}>{e.learnerName} · {e.enrolmentNo}</option>)}
            </select>
            <button style={solid} disabled={!enrolmentId} onClick={() => run(() => apiClient.post(`${LRN}/cohorts/${c.id}/members`, { enrolmentId }))}>Add to cohort</button>
          </div>
        )}
        {detail.members.length === 0 ? <div style={muted}>No learners yet.</div> : detail.members.map(m => (
          <div key={m.id} style={card}>
            <div style={{ display: "flex", justifyContent: "space-between", gap: 8 }}><div><strong>{m.learnerName}</strong><div style={muted}>{m.enrolmentNo}</div></div><Badge text={m.status === "ACTIVE" ? "In cohort" : "Removed"} tone={m.status === "ACTIVE" ? "good" : "neutral"} /></div>
            {m.removedReason && <div style={muted}>Removed: {m.removedReason}</div>}
            {m.status === "ACTIVE" && removeBox("cohort-members", m.id)}
          </div>
        ))}
      </div>
    </div>
  )
}

export default function TrainProvCohortDetail({ id, onBack }: { id: string; onBack: () => void }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["TRAININGPROVIDER_MANAGE", "TRAININGPROVIDER_ADMIN"])
  const [tab, setTab] = useState("people")
  const [editing, setEditing] = useState(false)
  const [tf, setTf] = useState<TransitionForm | null>(null)
  const [error, setError] = useState("")
  const { data, isLoading } = useQuery<CohortDetail>({ queryKey: ["tp-cohort", id], queryFn: async () => unwrap<CohortDetail>(await apiClient.get(`${LRN}/cohorts/${id}`)) })
  const refresh = () => { void qc.invalidateQueries({ queryKey: ["tp-cohort", id] }); void qc.invalidateQueries({ queryKey: ["tp-cohorts"] }) }
  if (isLoading || !data) return <div style={muted}>Loading…</div>
  const c = data.cohort
  const open = c.status === "PLANNED" || c.status === "RUNNING"
  const submit = async () => {
    if (!tf) return
    const p = transitionProblem(tf, c.nextStatuses); if (p) { setError(p); return }
    try { await apiClient.post(`${LRN}/cohorts/${id}/transition`, transitionBody(tf)); setTf(null); setError(""); refresh() } catch (e) { setError(errText(e)) }
  }
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div><button style={ghost} onClick={onBack}>Back to cohorts</button></div>
      {editing ? <CohortFormPanel initial={c} onCancel={() => setEditing(false)} onSaved={() => { setEditing(false); refresh() }} /> : (
        <div style={card}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
            <div><strong>{c.name}</strong><div style={muted}>{c.cohortNo} · {c.programmeTitle}{c.programmeVersionNo ? ` (version ${c.programmeVersionNo})` : ""} · {c.siteName}</div><div style={muted}>{c.startDate} to {c.endDate ?? "no end date"}</div></div>
            <Badge text={label(COHORT_STATUS, c.status)} tone={cohortTone(c.status)} />
          </div>
          {c.statusReason && <div style={muted}>Reason: {c.statusReason}</div>}
          {canManage && open && !tf && (
            <div style={{ display: "flex", gap: 8, marginTop: 8, flexWrap: "wrap" }}>
              <button style={ghost} onClick={() => setEditing(true)}>Edit details</button>
              {c.nextStatuses.map(s => <button key={s} style={ghost} onClick={() => { setTf({ toStatus: s, reason: "" }); setError("") }}>{s === "RUNNING" ? "Start the cohort" : s === "COMPLETED" ? "Mark as completed" : "Cancel the cohort"}</button>)}
            </div>
          )}
          {tf && (
            <div style={{ display: "grid", gap: 8, marginTop: 8 }}>
              <div>{tf.toStatus === "RUNNING" ? "Start this cohort?" : tf.toStatus === "COMPLETED" ? "Mark this cohort as completed? This says the teaching is over. It does not say any learner has completed or is competent." : "Cancel this cohort?"}</div>
              {tf.toStatus === "CANCELLED" && <input aria-label="Reason for cancelling the cohort" style={input} placeholder="Why is the cohort cancelled?" value={tf.reason} onChange={e => setTf({ ...tf, reason: e.target.value })} />}
              <Notice text={error} />
              <div style={{ display: "flex", gap: 8 }}><button style={solid} onClick={submit}>Confirm</button><button style={ghost} onClick={() => { setTf(null); setError("") }}>Back</button></div>
            </div>
          )}
        </div>
      )}
      <SubTabs tabs={[["people", "Learners & facilitators"], ["timetable", "Timetable"], ["attendance", "Attendance"]]} value={tab} onChange={setTab} />
      {tab === "people" && <People detail={data} canManage={canManage} onChanged={refresh} />}
      {tab === "timetable" && <Timetable cohort={c} canManage={canManage} open={open} />}
      {tab === "attendance" && <Attendance cohort={c} />}
    </div>
  )
}
