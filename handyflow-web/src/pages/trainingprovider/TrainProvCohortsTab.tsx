// src/pages/trainingprovider/TrainProvCohortsTab.tsx
//
// Cohorts, venues and a calendar of timetable slots. Backed by /api/v1/training-provider (cohorts, venues, timetable).
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { COHORT_STATUS, cohortTone, emptyVenue, label, reasonProblem, venueBody, venueProblem, type Cohort, type Slot, type Venue, type VenueForm } from "./cohortRules"
import { Badge, Notice, SubTabs } from "./tpUi"
import { card, errText, ghost, input, muted, solid, todayIso, unwrap } from "./tpStyle"
import { LRN, useSites } from "./tpApi"
import TrainProvCohortDetail, { CohortFormPanel } from "./TrainProvCohortDetail"

function CohortList({ canManage, onOpen }: { canManage: boolean; onOpen: (id: string) => void }) {
  const [status, setStatus] = useState("")
  const [adding, setAdding] = useState(false)
  const { data = [], isLoading } = useQuery<Cohort[]>({
    queryKey: ["tp-cohorts", status],
    queryFn: async () => unwrap<Cohort[]>(await apiClient.get(`${LRN}/cohorts`, { params: { status: status || undefined } })),
  })
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        <select aria-label="Cohort status" style={{ ...input, width: "auto" }} value={status} onChange={e => setStatus(e.target.value)}>
          <option value="">All statuses</option>{Object.entries(COHORT_STATUS).map(([k, l]) => <option key={k} value={k}>{l}</option>)}
        </select>
        {canManage && !adding && <button style={solid} onClick={() => setAdding(true)}>Open a cohort</button>}
      </div>
      {adding && <CohortFormPanel onCancel={() => setAdding(false)} onSaved={c => { setAdding(false); onOpen(c.id) }} />}
      {isLoading ? <div style={muted}>Loading…</div> : data.length === 0 ? <div style={muted}>No cohorts found.</div> : data.map(c => (
        <button key={c.id} onClick={() => onOpen(c.id)} style={{ ...card, textAlign: "left", background: "transparent", cursor: "pointer", color: "inherit" }}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
            <div><strong>{c.name}</strong><div style={muted}>{c.cohortNo} · {c.programmeTitle} · {c.siteName} · from {c.startDate}</div></div>
            <div style={{ display: "grid", justifyItems: "end", gap: 4 }}>
              <Badge text={label(COHORT_STATUS, c.status)} tone={cohortTone(c.status)} />
              <span style={muted}>{c.members} learner{c.members === 1 ? "" : "s"}{c.capacity ? ` of ${c.capacity}` : ""} · {c.facilitators} facilitator{c.facilitators === 1 ? "" : "s"}</span>
            </div>
          </div>
        </button>
      ))}
    </div>
  )
}

function Venues({ canManage }: { canManage: boolean }) {
  const qc = useQueryClient()
  const { data: sites = [] } = useSites()
  const [adding, setAdding] = useState(false)
  const [f, setF] = useState<VenueForm>(emptyVenue)
  const [deactivating, setDeactivating] = useState<string | null>(null)
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")
  const { data = [], isLoading } = useQuery<Venue[]>({ queryKey: ["tp-venues-all"], queryFn: async () => unwrap<Venue[]>(await apiClient.get(`${LRN}/venues`)) })
  const refresh = () => { void qc.invalidateQueries({ queryKey: ["tp-venues-all"] }); void qc.invalidateQueries({ queryKey: ["tp-venues"] }) }
  const add = async () => {
    const p = venueProblem(f); if (p) { setError(p); return }
    try { await apiClient.post(`${LRN}/venues`, venueBody(f)); setAdding(false); setF(emptyVenue); setError(""); refresh() } catch (e) { setError(errText(e)) }
  }
  const deactivate = async (id: string) => {
    const p = reasonProblem(reason); if (p) { setError(p); return }
    try { await apiClient.post(`${LRN}/venues/${id}/deactivate`, { reason: reason.trim() }); setDeactivating(null); setReason(""); setError(""); refresh() } catch (e) { setError(errText(e)) }
  }
  const reactivate = async (id: string) => { try { await apiClient.post(`${LRN}/venues/${id}/reactivate`, {}); refresh() } catch (e) { setError(errText(e)) } }
  return (
    <div style={{ display: "grid", gap: 10 }}>
      {canManage && !adding && <div><button style={solid} onClick={() => setAdding(true)}>Add a venue</button></div>}
      {adding && (
        <div style={{ ...card, display: "grid", gap: 8, background: "var(--hf-surface-muted)" }}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 8 }}>
            <select aria-label="Venue site" style={input} value={f.siteId} onChange={e => setF({ ...f, siteId: e.target.value })}><option value="">Choose the site</option>{sites.filter(s => s.status === "ACTIVE").map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select>
            <input aria-label="Venue name" style={input} placeholder="Room or area name" value={f.name} onChange={e => setF({ ...f, name: e.target.value })} />
            <input aria-label="Venue capacity" style={input} placeholder="Capacity (optional)" value={f.capacity} onChange={e => setF({ ...f, capacity: e.target.value })} />
          </div>
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} onClick={add}>Add venue</button><button style={ghost} onClick={() => { setAdding(false); setError("") }}>Cancel</button></div>
        </div>
      )}
      {isLoading ? <div style={muted}>Loading…</div> : data.length === 0 ? <div style={muted}>No venues yet.</div> : data.map(v => (
        <div key={v.id} style={card}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
            <div><strong>{v.name}</strong><div style={muted}>{v.siteName}{v.capacity ? ` · holds ${v.capacity}` : ""}</div></div>
            <Badge text={v.status === "ACTIVE" ? "Active" : "Inactive"} tone={v.status === "ACTIVE" ? "good" : "neutral"} />
          </div>
          {v.inactiveReason && <div style={muted}>Made inactive: {v.inactiveReason}</div>}
          {canManage && v.status === "ACTIVE" && deactivating !== v.id && <div style={{ marginTop: 8 }}><button style={ghost} onClick={() => { setDeactivating(v.id); setError("") }}>Make inactive</button></div>}
          {canManage && v.status === "INACTIVE" && <div style={{ marginTop: 8 }}><button style={ghost} onClick={() => reactivate(v.id)}>Make active again</button></div>}
          {deactivating === v.id && (
            <div style={{ display: "grid", gap: 8, marginTop: 8 }}>
              <input aria-label="Reason for making the venue inactive" style={input} placeholder="Why is this venue no longer used?" value={reason} onChange={e => setReason(e.target.value)} />
              <Notice text={error} />
              <div style={{ display: "flex", gap: 8 }}><button style={solid} onClick={() => deactivate(v.id)}>Make inactive</button><button style={ghost} onClick={() => { setDeactivating(null); setError("") }}>Keep</button></div>
            </div>
          )}
        </div>
      ))}
    </div>
  )
}

function plusDays(iso: string, n: number): string {
  const d = new Date(`${iso}T12:00:00Z`)
  d.setUTCDate(d.getUTCDate() + n)
  return d.toISOString().slice(0, 10)
}

function Calendar({ onOpenCohort }: { onOpenCohort: (id: string) => void }) {
  const [from, setFrom] = useState(todayIso())
  const [to, setTo] = useState(plusDays(todayIso(), 30))
  const [venueId, setVenueId] = useState("")
  const { data: venues = [] } = useQuery<Venue[]>({ queryKey: ["tp-venues-all"], queryFn: async () => unwrap<Venue[]>(await apiClient.get(`${LRN}/venues`)) })
  const ok = !!from && !!to && to >= from
  const { data = [], isLoading } = useQuery<Slot[]>({
    queryKey: ["tp-calendar", from, to, venueId], enabled: ok,
    queryFn: async () => unwrap<Slot[]>(await apiClient.get(`${LRN}/timetable`, { params: { from, to, venueId: venueId || undefined } })),
  })
  const live = data.filter(s => s.status === "SCHEDULED")
  const days = [...new Set(live.map(s => s.slotDate))]
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        <input aria-label="From" type="date" style={{ ...input, width: "auto" }} value={from} onChange={e => setFrom(e.target.value)} />
        <input aria-label="To" type="date" style={{ ...input, width: "auto" }} value={to} onChange={e => setTo(e.target.value)} />
        <select aria-label="Venue filter" style={{ ...input, width: "auto" }} value={venueId} onChange={e => setVenueId(e.target.value)}><option value="">All venues</option>{venues.map(v => <option key={v.id} value={v.id}>{v.name} · {v.siteName}</option>)}</select>
      </div>
      {!ok ? <div style={muted}>Choose a from date and a to date that is not before it.</div> : isLoading ? <div style={muted}>Loading…</div> : days.length === 0 ? <div style={muted}>Nothing scheduled in this period.</div> : days.map(day => (
        <div key={day} style={{ display: "grid", gap: 6 }}>
          <strong>{day}</strong>
          {live.filter(s => s.slotDate === day).map(s => (
            <button key={s.id} onClick={() => onOpenCohort(s.cohortId)} style={{ ...card, textAlign: "left", background: "transparent", cursor: "pointer", color: "inherit" }}>
              <strong>{s.startTime.slice(0, 5)} to {s.endTime.slice(0, 5)} · {s.title}</strong>
              <div style={muted}>{s.cohortName}{s.venueName ? ` · ${s.venueName}` : ""}{s.facilitatorName ? ` · ${s.facilitatorName}` : ""}</div>
            </button>
          ))}
        </div>
      ))}
    </div>
  )
}

export default function TrainProvCohortsTab() {
  const canManage = usePermissions(["TRAININGPROVIDER_MANAGE", "TRAININGPROVIDER_ADMIN"])
  const [tab, setTab] = useState("cohorts")
  const [openId, setOpenId] = useState<string | null>(null)
  if (openId) return <TrainProvCohortDetail id={openId} onBack={() => setOpenId(null)} />
  return (
    <div style={{ display: "grid", gap: 12 }}>
      <SubTabs tabs={[["cohorts", "Cohorts"], ["calendar", "Calendar"], ["venues", "Venues"]]} value={tab} onChange={setTab} />
      {tab === "cohorts" && <CohortList canManage={canManage} onOpen={setOpenId} />}
      {tab === "calendar" && <Calendar onOpenCohort={setOpenId} />}
      {tab === "venues" && <Venues canManage={canManage} />}
    </div>
  )
}
