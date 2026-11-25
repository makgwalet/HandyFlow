// src/pages/trainingprovider/TrainProvPractitionersTab.tsx
//
// Facilitators, assessors and moderators, and who is checked and in force for a programme on a day. Backed by /api/v1/training-provider/practitioners.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { EMPLOYMENT, ROLES, label, type Eligible, type Practitioner } from "./practitionerRules"
import { Badge, SubTabs } from "./tpUi"
import { card, input, muted, solid, todayIso, unwrap } from "./tpStyle"
import { PRC, useActiveProgrammes } from "./tpApi"
import TrainProvPractitionerDetail, { PersonFormPanel } from "./TrainProvPractitionerDetail"

function People({ canManage, onOpen }: { canManage: boolean; onOpen: (id: string) => void }) {
  const [q, setQ] = useState("")
  const [status, setStatus] = useState("ACTIVE")
  const [adding, setAdding] = useState(false)
  const { data = [], isLoading } = useQuery<Practitioner[]>({
    queryKey: ["tp-practitioners", status, q],
    queryFn: async () => unwrap<Practitioner[]>(await apiClient.get(PRC, { params: { status: status || undefined, q: q.trim() || undefined } })),
  })
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        <input aria-label="Search people" style={{ ...input, width: 260 }} placeholder="Search by name or number" value={q} onChange={e => setQ(e.target.value)} />
        <select aria-label="Person status" style={{ ...input, width: "auto" }} value={status} onChange={e => setStatus(e.target.value)}><option value="ACTIVE">Active</option><option value="INACTIVE">Inactive</option><option value="">All</option></select>
        {canManage && !adding && <button style={solid} onClick={() => setAdding(true)}>Add a person</button>}
      </div>
      {adding && <PersonFormPanel onCancel={() => setAdding(false)} onSaved={p => { setAdding(false); onOpen(p.id) }} />}
      {isLoading ? <div style={muted}>Loading…</div> : data.length === 0 ? <div style={muted}>No people found.</div> : data.map(p => (
        <button key={p.id} onClick={() => onOpen(p.id)} style={{ ...card, textAlign: "left", background: "transparent", cursor: "pointer", color: "inherit" }}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
            <div><strong>{p.firstNames} {p.surname}</strong><div style={muted}>{p.practitionerNo} · {label(EMPLOYMENT, p.employmentType)}</div></div>
            <div style={{ display: "grid", justifyItems: "end", gap: 4 }}>
              <Badge text={p.status === "ACTIVE" ? "Active" : "Inactive"} tone={p.status === "ACTIVE" ? "good" : "neutral"} />
              <span style={muted}>{p.liveAppointments} appointment{p.liveAppointments === 1 ? "" : "s"}{p.verifiedRoles.length ? ` · checked as ${p.verifiedRoles.map(r => label(ROLES, r).toLowerCase()).join(", ")}` : " · none checked"}</span>
            </div>
          </div>
        </button>
      ))}
    </div>
  )
}

function EligibleList() {
  const { data: programmes = [] } = useActiveProgrammes()
  const [programmeId, setProgrammeId] = useState("")
  const [role, setRole] = useState("ASSESSOR")
  const [on, setOn] = useState(todayIso())
  const { data = [], isFetching } = useQuery<Eligible[]>({
    queryKey: ["tp-eligible", programmeId, role, on],
    enabled: !!programmeId && !!on,
    queryFn: async () => unwrap<Eligible[]>(await apiClient.get(`${PRC}/eligible`, { params: { programmeId, role, on } })),
  })
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={muted}>People whose appointment for this role and programme has been checked by a person and is in force on the day. This lists records only. It does not say anyone is competent.</div>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        <select aria-label="Programme" style={{ ...input, width: "auto" }} value={programmeId} onChange={e => setProgrammeId(e.target.value)}><option value="">Choose the programme</option>{programmes.map(p => <option key={p.id} value={p.id}>{p.code} · {p.title}</option>)}</select>
        <select aria-label="Role" style={{ ...input, width: "auto" }} value={role} onChange={e => setRole(e.target.value)}>{Object.entries(ROLES).map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select>
        <input aria-label="On this day" type="date" style={{ ...input, width: "auto" }} value={on} onChange={e => setOn(e.target.value)} />
      </div>
      {!programmeId ? null : isFetching ? <div style={muted}>Loading…</div> : data.length === 0
        ? <div style={muted}>Nobody has a checked appointment in force for that role on that day.</div>
        : data.map(e => <div key={e.appointmentId} style={card}><strong>{e.name}</strong><div style={muted}>{e.practitionerNo} · {label(ROLES, e.role)} · checked {e.verifiedOn}</div></div>)}
    </div>
  )
}

export default function TrainProvPractitionersTab() {
  const canManage = usePermissions(["TRAININGPROVIDER_MANAGE", "TRAININGPROVIDER_ADMIN"])
  const [tab, setTab] = useState("people")
  const [openId, setOpenId] = useState<string | null>(null)
  if (openId) return <TrainProvPractitionerDetail id={openId} onBack={() => setOpenId(null)} />
  return (
    <div style={{ display: "grid", gap: 12 }}>
      <SubTabs tabs={[["people", "People"], ["eligible", "Who can be used"]]} value={tab} onChange={setTab} />
      {tab === "people" ? <People canManage={canManage} onOpen={setOpenId} /> : <EligibleList />}
    </div>
  )
}
