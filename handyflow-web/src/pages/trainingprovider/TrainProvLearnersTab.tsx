// src/pages/trainingprovider/TrainProvLearnersTab.tsx
//
// Learners the provider trains in its own right, their applications and their enrolments. Backed by /api/v1/training-provider
// (learners, applications, learner-enrolments). Sits beside the client and delegate screens and changes none of them.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import {
  APPLICATION_STATUS, ENROLMENT_STATUS, applicationTone, coverNote, enrolmentTone, label, type Application, type Enrolment, type Learner,
} from "./learnerRules"
import { Badge, SubTabs } from "./tpUi"
import { card, input, muted, solid, unwrap } from "./tpStyle"
import { LRN } from "./tpApi"
import TrainProvDuplicatesPanel from "./TrainProvDuplicatesPanel"
import TrainProvFundersList from "./TrainProvFundersList"
import TrainProvLearnerDetail, { LearnerFormPanel } from "./TrainProvLearnerDetail"

function LearnerList({ canManage, onOpen }: { canManage: boolean; onOpen: (id: string) => void }) {
  const [q, setQ] = useState("")
  const [status, setStatus] = useState("ACTIVE")
  const [adding, setAdding] = useState(false)
  const { data = [], isLoading } = useQuery<Learner[]>({
    queryKey: ["tp-learners", status, q],
    queryFn: async () => unwrap<Learner[]>(await apiClient.get(`${LRN}/learners`, { params: { status: status || undefined, q: q.trim() || undefined } })),
  })
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        <input aria-label="Search learners" style={{ ...input, width: 260 }} placeholder="Search by name, number or ID" value={q} onChange={e => setQ(e.target.value)} />
        <select aria-label="Learner status" style={{ ...input, width: "auto" }} value={status} onChange={e => setStatus(e.target.value)}><option value="ACTIVE">Active</option><option value="ARCHIVED">Archived</option><option value="">All</option></select>
        {canManage && !adding && <button style={solid} onClick={() => setAdding(true)}>Add a learner</button>}
      </div>
      {adding && <LearnerFormPanel onCancel={() => setAdding(false)} onSaved={l => { setAdding(false); onOpen(l.id) }} />}
      {isLoading ? <div style={muted}>Loading…</div> : data.length === 0 ? <div style={muted}>No learners found.</div> : data.map(l => (
        <button key={l.id} onClick={() => onOpen(l.id)} style={{ ...card, textAlign: "left", background: "transparent", cursor: "pointer", color: "inherit" }}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
            <div><strong>{l.firstNames} {l.surname}</strong><div style={muted}>{l.learnerNo}{l.idNumberMasked ? ` · ${l.idNumberMasked}` : ""}{l.clientName ? ` · ${l.clientName}` : ""}</div></div>
            <div style={{ display: "grid", justifyItems: "end", gap: 4 }}>
              <Badge text={l.status === "ACTIVE" ? "Active" : "Archived"} tone={l.status === "ACTIVE" ? "good" : "neutral"} />
              <span style={muted}>{l.liveEnrolments} live enrolment{l.liveEnrolments === 1 ? "" : "s"}</span>
            </div>
          </div>
        </button>
      ))}
    </div>
  )
}

function Applications({ onOpen }: { onOpen: (learnerId: string) => void }) {
  const [status, setStatus] = useState("")
  const { data = [], isLoading } = useQuery<Application[]>({
    queryKey: ["tp-applications", status],
    queryFn: async () => unwrap<Application[]>(await apiClient.get(`${LRN}/applications`, { params: { status: status || undefined } })),
  })
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <select aria-label="Application status" style={{ ...input, width: "auto" }} value={status} onChange={e => setStatus(e.target.value)}>
        <option value="">All statuses</option>{Object.entries(APPLICATION_STATUS).map(([k, l]) => <option key={k} value={k}>{l}</option>)}
      </select>
      {isLoading ? <div style={muted}>Loading…</div> : data.length === 0 ? <div style={muted}>No applications.</div> : data.map(a => (
        <button key={a.id} onClick={() => onOpen(a.learnerId)} style={{ ...card, textAlign: "left", background: "transparent", cursor: "pointer", color: "inherit" }}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
            <div><strong>{a.learnerName}</strong><div style={muted}>{a.programmeTitle}{a.siteName ? ` · ${a.siteName}` : ""} · applied {a.createdAt.slice(0, 10)}</div></div>
            <Badge text={label(APPLICATION_STATUS, a.status)} tone={applicationTone(a.status)} />
          </div>
        </button>
      ))}
    </div>
  )
}

function Enrolments({ onOpen }: { onOpen: (learnerId: string) => void }) {
  const [status, setStatus] = useState("")
  const { data = [], isLoading } = useQuery<Enrolment[]>({
    queryKey: ["tp-learner-enrolments", status],
    queryFn: async () => unwrap<Enrolment[]>(await apiClient.get(`${LRN}/learner-enrolments`, { params: { status: status || undefined } })),
  })
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <select aria-label="Enrolment status" style={{ ...input, width: "auto" }} value={status} onChange={e => setStatus(e.target.value)}>
        <option value="">All statuses</option>{Object.entries(ENROLMENT_STATUS).map(([k, l]) => <option key={k} value={k}>{l}</option>)}
      </select>
      {isLoading ? <div style={muted}>Loading…</div> : data.length === 0 ? <div style={muted}>No enrolments.</div> : data.map(e => (
        <button key={e.id} onClick={() => onOpen(e.learnerId)} style={{ ...card, textAlign: "left", background: "transparent", cursor: "pointer", color: "inherit" }}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
            <div><strong>{e.learnerName}</strong> <span style={muted}>{e.enrolmentNo}</span><div style={muted}>{e.programmeTitle} · {e.siteName} · starts {e.startDate}</div><div style={muted}>{coverNote(e)}</div></div>
            <Badge text={label(ENROLMENT_STATUS, e.status)} tone={enrolmentTone(e.status)} />
          </div>
        </button>
      ))}
    </div>
  )
}

export default function TrainProvLearnersTab() {
  const isAdmin = usePermissions(["TRAININGPROVIDER_ADMIN"])
  const canManage = usePermissions(["TRAININGPROVIDER_MANAGE", "TRAININGPROVIDER_ADMIN"])
  const [tab, setTab] = useState("learners")
  const [openId, setOpenId] = useState<string | null>(null)
  const open = (id: string) => { setTab("learners"); setOpenId(id) }
  return (
    <div style={{ display: "grid", gap: 14 }}>
      <div><h2 style={{ margin: 0, fontSize: 18 }}>Learners</h2>
        <div style={muted}>People the provider trains in its own right, with their applications and enrolments. Entry checks are recorded by a person. An accredited enrolment records what the accreditation check said on the day.</div></div>
      {openId ? <TrainProvLearnerDetail id={openId} canManage={canManage} isAdmin={isAdmin} onBack={() => setOpenId(null)} /> : (<>
        <SubTabs value={tab} onChange={setTab} tabs={[["learners", "Learners"], ["applications", "Applications"], ["enrolments", "Enrolments"], ["duplicates", "Duplicates"], ["funders", "Funders"]]} />
        {tab === "learners" && <LearnerList canManage={canManage} onOpen={open} />}
        {tab === "applications" && <Applications onOpen={open} />}
        {tab === "enrolments" && <Enrolments onOpen={open} />}
        {tab === "duplicates" && <TrainProvDuplicatesPanel canManage={canManage} isAdmin={isAdmin} onOpen={open} />}
        {tab === "funders" && <TrainProvFundersList />}
      </>)}
    </div>
  )
}
