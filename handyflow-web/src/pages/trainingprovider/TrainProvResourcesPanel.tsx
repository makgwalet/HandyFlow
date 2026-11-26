// src/pages/trainingprovider/TrainProvResourcesPanel.tsx
//
// The site resource register: equipment and similar resources each delivery site holds, and the state each is in. A register only: it does not
// book resources, and listing one does not show that it meets any standard. Nothing is deleted; a resource is retired with a reason.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import type { Venue } from "./cohortRules"
import {
  REGISTER_NOTE, STATUSES, TYPES, emptyMove, emptyResource, emptyStatus, historyLine, label, moveProblem, resourceBody, resourceForm, resourceProblem, statusProblem, statusTone,
  type MoveForm, type Resource, type ResourceDetail, type ResourceForm, type StatusForm,
} from "./resourceRules"
import { Badge, Field, Notice } from "./tpUi"
import { card, errText, ghost, input, muted, solid, todayIso, unwrap } from "./tpStyle"
import { LRN, useSites } from "./tpApi"

const API = `${LRN}/site-resources`

function VenueSelect({ siteId, value, onChange }: { siteId: string; value: string; onChange: (v: string) => void }) {
  const { data = [] } = useQuery<Venue[]>({
    queryKey: ["tp-venues", siteId], enabled: !!siteId,
    queryFn: async () => unwrap<Venue[]>(await apiClient.get(`${LRN}/venues`, { params: { siteId, status: "ACTIVE" } })),
  })
  return (
    <select aria-label="Venue" style={input} value={value} onChange={e => onChange(e.target.value)}>
      <option value="">No particular venue</option>{data.map(v => <option key={v.id} value={v.id}>{v.name}</option>)}
    </select>
  )
}

function ResourceEditor({ initial, onDone, onCancel }: { initial: Resource | null; onDone: () => void; onCancel: () => void }) {
  const { data: sites = [] } = useSites()
  const [f, setF] = useState<ResourceForm>(initial ? resourceForm(initial) : emptyResource())
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const save = async () => {
    const p = resourceProblem(f); if (p) { setError(p); return }
    setBusy(true); setError("")
    try { await (initial ? apiClient.put(`${API}/${initial.id}`, resourceBody(f)) : apiClient.post(API, resourceBody(f))); onDone() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  return (
    <div style={{ ...card, display: "grid", gap: 8 }}>
      <strong>{initial ? `Change ${initial.name}` : "Add a resource"}</strong>
      <Field label="Site">
        <select aria-label="Site" style={input} value={f.siteId} disabled={!!initial} onChange={e => setF({ ...f, siteId: e.target.value, venueId: "" })}>
          <option value="">Choose the site</option>{sites.filter(s => s.status === "ACTIVE").map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
        </select>
      </Field>
      <Field label="Venue (optional)"><VenueSelect siteId={f.siteId} value={f.venueId} onChange={v => setF({ ...f, venueId: v })} /></Field>
      <Field label="Name"><input style={input} value={f.name} onChange={e => setF({ ...f, name: e.target.value })} /></Field>
      <Field label="Type"><select style={input} value={f.resourceType} onChange={e => setF({ ...f, resourceType: e.target.value })}><option value="">Choose…</option>{Object.entries(TYPES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></Field>
      <Field label="Serial number, asset tag or registration (optional)"><input style={input} value={f.identifier} onChange={e => setF({ ...f, identifier: e.target.value })} /></Field>
      <Field label="Quantity"><input style={input} inputMode="numeric" value={f.quantity} onChange={e => setF({ ...f, quantity: e.target.value })} /></Field>
      <label style={{ fontSize: 13 }}><input type="checkbox" checked={f.shareable} onChange={e => setF({ ...f, shareable: e.target.checked })} /> Can be shared between groups at the same time</label>
      <Field label="Notes (optional)"><textarea style={input} rows={2} value={f.notes} onChange={e => setF({ ...f, notes: e.target.value })} /></Field>
      <Notice text={error} />
      <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={() => void save()}>{initial ? "Save changes" : "Add resource"}</button><button style={ghost} onClick={onCancel}>Cancel</button></div>
    </div>
  )
}

function ResourceCard({ r, canManage, onChanged }: { r: Resource; canManage: boolean; onChanged: () => void }) {
  const { data: sites = [] } = useSites()
  const [mode, setMode] = useState<"" | "edit" | "status" | "move" | "history">("")
  const [sf, setSf] = useState<StatusForm>(emptyStatus("", todayIso()))
  const [mf, setMf] = useState<MoveForm>(emptyMove(todayIso()))
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const { data: detail } = useQuery<ResourceDetail>({ queryKey: ["tp-resource", r.id], enabled: mode === "history", queryFn: async () => unwrap<ResourceDetail>(await apiClient.get(`${API}/${r.id}`)) })
  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true); setError("")
    try { await fn(); setMode(""); onChanged() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  if (mode === "edit") return <ResourceEditor initial={r} onDone={() => { setMode(""); onChanged() }} onCancel={() => setMode("")} />
  const retired = r.status === "RETIRED"
  return (
    <div style={{ ...card, display: "grid", gap: 6 }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
        <div>
          <strong>{r.name}</strong>
          <div style={muted}>{r.resourceNo} · {label(TYPES, r.resourceType)} · {r.siteName ?? "—"}{r.venueName ? ` · ${r.venueName}` : ""} · quantity {r.quantity}{r.shareable ? " · can be shared" : ""}{r.identifier ? ` · ${r.identifier}` : ""}</div>
        </div>
        <Badge text={label(STATUSES, r.status)} tone={statusTone(r.status)} />
      </div>
      {r.statusReason && <div style={muted}>{label(STATUSES, r.status)} since {r.statusChangedOn}: {r.statusReason}</div>}
      {r.notes && <div style={muted}>{r.notes}</div>}
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        <button style={ghost} onClick={() => setMode(mode === "history" ? "" : "history")}>{mode === "history" ? "Hide history" : "History"}</button>
        {canManage && !retired && mode === "" && <button style={ghost} onClick={() => setMode("edit")}>Change details</button>}
        {canManage && !retired && mode === "" && r.nextStatuses.map(s => (
          <button key={s} style={ghost} onClick={() => { setSf(emptyStatus(s, todayIso())); setMode("status"); setError("") }}>{s === "IN_SERVICE" ? "Return to service" : `Mark ${label(STATUSES, s).toLowerCase()}`}</button>
        ))}
        {canManage && !retired && mode === "" && <button style={ghost} onClick={() => { setMf(emptyMove(todayIso())); setMode("move"); setError("") }}>Move to another site</button>}
      </div>
      {mode === "history" && (!detail ? <div style={muted}>Loading…</div> : detail.history.map(e => <div key={e.id} style={muted}>{e.occurredOn} · {historyLine(e)}</div>))}
      {mode === "status" && (
        <div style={{ display: "grid", gap: 6 }}>
          <Field label={`Change to ${label(STATUSES, sf.toStatus).toLowerCase()}: reason`}><input style={input} value={sf.reason} onChange={e => setSf({ ...sf, reason: e.target.value })} /></Field>
          <Field label="Changed on"><input type="date" style={input} value={sf.changedOn} onChange={e => setSf({ ...sf, changedOn: e.target.value })} /></Field>
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}>
            <button style={solid} disabled={busy} onClick={() => { const p = statusProblem(sf, todayIso()); if (p) { setError(p); return } void run(() => apiClient.post(`${API}/${r.id}/status`, sf)) }}>Save</button>
            <button style={ghost} onClick={() => setMode("")}>Cancel</button>
          </div>
        </div>
      )}
      {mode === "move" && (
        <div style={{ display: "grid", gap: 6 }}>
          <Field label="Move to site"><select aria-label="Move to site" style={input} value={mf.siteId} onChange={e => setMf({ ...mf, siteId: e.target.value, venueId: "" })}><option value="">Choose the site</option>{sites.filter(s => s.status === "ACTIVE" && s.id !== r.siteId).map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select></Field>
          <Field label="Venue there (optional)"><VenueSelect siteId={mf.siteId} value={mf.venueId} onChange={v => setMf({ ...mf, venueId: v })} /></Field>
          <Field label="Why is it moving?"><input style={input} value={mf.reason} onChange={e => setMf({ ...mf, reason: e.target.value })} /></Field>
          <Field label="Moved on"><input type="date" style={input} value={mf.movedOn} onChange={e => setMf({ ...mf, movedOn: e.target.value })} /></Field>
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}>
            <button style={solid} disabled={busy} onClick={() => { const p = moveProblem(mf, r, todayIso()); if (p) { setError(p); return } void run(() => apiClient.post(`${API}/${r.id}/move`, { ...mf, venueId: mf.venueId || null })) }}>Move</button>
            <button style={ghost} onClick={() => setMode("")}>Cancel</button>
          </div>
        </div>
      )}
    </div>
  )
}

export default function TrainProvResourcesPanel() {
  const qc = useQueryClient()
  const canManage = usePermissions(["TRAININGPROVIDER_MANAGE", "TRAININGPROVIDER_ADMIN"])
  const { data: sites = [] } = useSites()
  const [siteId, setSiteId] = useState("")
  const [status, setStatus] = useState("")
  const [adding, setAdding] = useState(false)
  const { data = [], isLoading } = useQuery<Resource[]>({
    queryKey: ["tp-resources", siteId, status],
    queryFn: async () => unwrap<Resource[]>(await apiClient.get(API, { params: { siteId: siteId || undefined, status: status || undefined } })),
  })
  const refresh = () => qc.invalidateQueries({ queryKey: ["tp-resources"] })
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={muted}>{REGISTER_NOTE}</div>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
        <select aria-label="Filter by site" style={{ ...input, maxWidth: 220 }} value={siteId} onChange={e => setSiteId(e.target.value)}><option value="">All sites</option>{sites.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select>
        <select aria-label="Filter by state" style={{ ...input, maxWidth: 200 }} value={status} onChange={e => setStatus(e.target.value)}><option value="">Any state</option>{Object.entries(STATUSES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select>
        {canManage && !adding && <button style={solid} onClick={() => setAdding(true)}>Add a resource</button>}
      </div>
      {adding && <ResourceEditor initial={null} onDone={() => { setAdding(false); void refresh() }} onCancel={() => setAdding(false)} />}
      {isLoading ? <div style={muted}>Loading…</div> : data.length === 0 ? <div style={muted}>No resources recorded.</div> : data.map(r => <ResourceCard key={r.id} r={r} canManage={canManage} onChanged={() => void refresh()} />)}
    </div>
  )
}
