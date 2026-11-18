import { useEffect, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { btnGhost, btnPrimary, card, inputStyle, labelStyle } from "../warehousing/whseUi"
import {
  CONFLICT_LABEL, POD_EXCEPTIONS, PUSH_TYPES, hoursText, pushLabel, unwrap, validateArrival, validateOffline, validateOverdue,
  type ArrivalPolicy, type OfflinePolicy, type OverduePolicy, type PodPolicy,
} from "./opsPolicyOps"

const msg = (e: any, fb: string) => e?.response?.data?.message ?? fb
const Err = ({ text }: { text: string }) => text ? <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: "8px 0 0" }}>{text}</p> : null
const Note = ({ children }: { children: React.ReactNode }) => <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "4px 0 10px" }}>{children}</p>
const Panel = ({ title, children }: { title: string; children: React.ReactNode }) => (
  <section style={{ ...card, padding: 16, marginBottom: 14 }}><h3 style={{ margin: "0 0 4px", fontSize: 14 }}>{title}</h3>{children}</section>
)

/**
 * Fuel operating policy in one place: the arrival check, proof-of-delivery exceptions, overdue alerts, offline limits, and what the
 * server refused from phones that were offline. Everyone with Fuel read access sees it; changing needs the configuration permission.
 * Switching an enforcement on changes what drivers can do the moment it is saved.
 */
export default function OperationsPolicyTab() {
  return (
    <div className="hf-fuel">
      <h2 style={{ margin: "0 0 4px", fontSize: 18 }}>Operations policy</h2>
      <Note>How the server decides what a driver or attendant may do. These rules are enforced on the server, so a phone or a browser cannot skip them.</Note>
      <ArrivalCard /><PodCard /><OverdueCard /><OfflineCard /><AlertLog /><ConflictsPanel />
    </div>
  )
}

function useCanConfigure() { return usePermissions(["FUEL_CONFIGURATION_MANAGE"]) }

function ArrivalCard() {
  const can = useCanConfigure(); const qc = useQueryClient()
  const q = useQuery<ArrivalPolicy>({ queryKey: ["fuel-arrival-policy"], retry: false, queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/settings/arrival-check")) })
  const [hours, setHours] = useState(""); const [on, setOn] = useState(false); const [err, setErr] = useState(""); const [saved, setSaved] = useState(false)
  useEffect(() => { if (q.data) { setHours(String(q.data.freshHours)); setOn(!!q.data.enforced) } }, [q.data])
  const save = useMutation({
    mutationFn: () => apiClient.put("/api/v1/fuel/settings/arrival-check", { freshHours: Number(hours), enforced: on }),
    onSuccess: () => { setErr(""); setSaved(true); qc.invalidateQueries({ queryKey: ["fuel-arrival-policy"] }) }, onError: (e: any) => { setSaved(false); setErr(msg(e, "Could not save")) },
  })
  if (q.isError) return <Panel title="Arrival check"><Err text={msg(q.error, "Could not load the arrival check policy")} /></Panel>
  if (!q.data) return null
  return (
    <Panel title="Arrival check">
      <Note>Before a driver works at a customer's site, the licences, vehicle and product are checked. A check goes stale after the time below. The server judges freshness on its own clock.</Note>
      <label style={labelStyle} htmlFor="fresh-hours">Hours a check stays fresh (usually {q.data.defaultFreshHours})</label>
      <input id="fresh-hours" aria-label="Hours a check stays fresh" value={hours} disabled={!can} onChange={e => { setSaved(false); setHours(e.target.value) }} style={{ ...inputStyle, maxWidth: 120 }} inputMode="numeric" />
      <label style={{ display: "block", fontSize: 13, margin: "10px 0 2px" }}>
        <input type="checkbox" aria-label="Enforce the arrival check" disabled={!can} checked={on} onChange={e => { setSaved(false); setOn(e.target.checked) }} /> Refuse offloading and final proof of delivery without a clear, fresh check</label>
      <Note>Off: the check is recorded and shown but does not stop anything. On: a load with a missing, stale or blocked check cannot be completed. Viewing the load, the route and recording arrival always stay available. A stale check can be released by someone given the arrival check override permission; a blocked one cannot.</Note>
      <Err text={err} />{saved && <p role="status" style={{ fontSize: 12, color: "var(--hf-success-text)" }}>Saved.</p>}
      {can && <button style={btnPrimary} disabled={save.isPending} onClick={() => { const p = validateArrival(hours); if (p) return setErr(p); setErr(""); save.mutate() }}>Save</button>}
    </Panel>
  )
}

function PodCard() {
  const can = useCanConfigure(); const qc = useQueryClient()
  const q = useQuery<PodPolicy>({ queryKey: ["fuel-pod-policy"], retry: false, queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/settings/pod-policy")) })
  const [on, setOn] = useState(false); const [types, setTypes] = useState<string[]>([]); const [err, setErr] = useState(""); const [saved, setSaved] = useState(false)
  useEffect(() => { if (q.data) { setOn(!!q.data.enforced); setTypes(q.data.allowedExceptions ?? []) } }, [q.data])
  const save = useMutation({
    mutationFn: () => apiClient.put("/api/v1/fuel/settings/pod-policy", { enforced: on, allowedExceptions: types }),
    onSuccess: () => { setErr(""); setSaved(true); qc.invalidateQueries({ queryKey: ["fuel-pod-policy"] }) }, onError: (e: any) => { setSaved(false); setErr(msg(e, "Could not save")) },
  })
  if (q.isError) return <Panel title="Proof of delivery: who received it"><Err text={msg(q.error, "Could not load the POD policy")} /></Panel>
  if (!q.data) return null
  const flip = (c: string) => { setSaved(false); setTypes(t => t.includes(c) ? t.filter(x => x !== c) : [...t, c]) }
  return (
    <Panel title="Proof of delivery: who received it">
      <label style={{ display: "block", fontSize: 13, margin: "6px 0" }}>
        <input type="checkbox" aria-label="Require the receiver's name" disabled={!can} checked={on} onChange={e => { setSaved(false); setOn(e.target.checked) }} /> Require the receiver's name to complete a delivery</label>
      <Note>With this on, a driver cannot finish a delivery without the name of the person who received the fuel, unless one of the exceptions below applies. Every exception needs a reason, evidence on the load, and is recorded.</Note>
      <fieldset style={{ border: "none", padding: 0, margin: 0 }} disabled={!can}>
        <legend style={{ fontSize: 12.5, fontWeight: 600 }}>Exceptions this company allows</legend>
        {POD_EXCEPTIONS.map(e => (
          <label key={e.code} style={{ display: "block", fontSize: 13, margin: "4px 0" }}>
            <input type="checkbox" aria-label={e.label} checked={types.includes(e.code)} onChange={() => flip(e.code)} /> {e.label}
            <span style={{ display: "block", marginLeft: 22, fontSize: 11.5, color: "var(--hf-text-muted)" }}>{e.hint}</span>
          </label>
        ))}
      </fieldset>
      <Err text={err} />{saved && <p role="status" style={{ fontSize: 12, color: "var(--hf-success-text)" }}>Saved.</p>}
      {can && <button style={{ ...btnPrimary, marginTop: 8 }} disabled={save.isPending} onClick={() => save.mutate()}>Save</button>}
    </Panel>
  )
}

function OverdueCard() {
  const can = useCanConfigure(); const qc = useQueryClient()
  const q = useQuery<OverduePolicy>({ queryKey: ["fuel-overdue-policy"], retry: false, queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/push/overdue-policy")) })
  const [grace, setGrace] = useState(""); const [esc, setEsc] = useState(""); const [err, setErr] = useState(""); const [saved, setSaved] = useState(false)
  useEffect(() => { if (q.data) { setGrace(String(q.data.graceMinutes)); setEsc(String(q.data.escalationMinutes)) } }, [q.data])
  const save = useMutation({
    mutationFn: () => apiClient.put("/api/v1/fuel/push/overdue-policy", { graceMinutes: Number(grace), escalationMinutes: Number(esc) }),
    onSuccess: () => { setErr(""); setSaved(true); qc.invalidateQueries({ queryKey: ["fuel-overdue-policy"] }) }, onError: (e: any) => { setSaved(false); setErr(msg(e, "Could not save")) },
  })
  if (q.isError) return <Panel title="Overdue deliveries"><Err text={msg(q.error, "Could not load the overdue policy")} /></Panel>
  if (!q.data) return null
  return (
    <Panel title="Overdue deliveries">
      <Note>The driver is told when a load is this long past its scheduled time and still open. If it is still open after the second delay, the dispatch recipients are told. Who is told is set under Settings, "Who is told about alerts". The dashboard's own overdue count uses its own, longer threshold.</Note>
      <div style={{ display: "flex", gap: 16, flexWrap: "wrap" }}>
        <div><label style={labelStyle} htmlFor="grace">Tell the driver after (minutes)</label><input id="grace" aria-label="Tell the driver after" value={grace} disabled={!can} onChange={e => { setSaved(false); setGrace(e.target.value) }} style={{ ...inputStyle, maxWidth: 140 }} inputMode="numeric" /></div>
        <div><label style={labelStyle} htmlFor="esc">Then tell dispatch after a further (minutes)</label><input id="esc" aria-label="Then tell dispatch after" value={esc} disabled={!can} onChange={e => { setSaved(false); setEsc(e.target.value) }} style={{ ...inputStyle, maxWidth: 140 }} inputMode="numeric" /></div>
      </div>
      <Err text={err} />{saved && <p role="status" style={{ fontSize: 12, color: "var(--hf-success-text)" }}>Saved.</p>}
      {can && <button style={{ ...btnPrimary, marginTop: 8 }} disabled={save.isPending} onClick={() => { const p = validateOverdue(grace, esc); if (p) return setErr(p); setErr(""); save.mutate() }}>Save</button>}
    </Panel>
  )
}

function OfflineCard() {
  const can = useCanConfigure(); const qc = useQueryClient()
  const q = useQuery<OfflinePolicy>({ queryKey: ["fuel-offline-policy"], retry: false, queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/offline/conflicts/policy")) })
  const [age, setAge] = useState(""); const [skew, setSkew] = useState(""); const [err, setErr] = useState(""); const [saved, setSaved] = useState(false)
  useEffect(() => { if (q.data) { setAge(String(q.data.maxAgeHours)); setSkew(String(q.data.clockSkewMinutes)) } }, [q.data])
  const save = useMutation({
    mutationFn: () => apiClient.put("/api/v1/fuel/offline/conflicts/policy", { maxAgeHours: Number(age), clockSkewMinutes: Number(skew) }),
    onSuccess: () => { setErr(""); setSaved(true); qc.invalidateQueries({ queryKey: ["fuel-offline-policy"] }) }, onError: (e: any) => { setSaved(false); setErr(msg(e, "Could not save")) },
  })
  if (q.isError) return <Panel title="Work done without signal"><Err text={msg(q.error, "Could not load the offline limits")} /></Panel>
  if (!q.data) return null
  return (
    <Panel title="Work done without signal">
      <Note>Phones can open and close shifts and check trucks in and out without signal, and send it later. The server refuses anything saved longer ago than the limit below, or with a phone clock too far ahead, and keeps it on the list below. It also judges every write again when it arrives: pumps, meters, shift state, bays and prices are the server's, not the phone's. Currently a write can wait {hoursText(q.data.maxAgeHours)}.</Note>
      <div style={{ display: "flex", gap: 16, flexWrap: "wrap" }}>
        <div><label style={labelStyle} htmlFor="age">Longest a saved write may wait (hours)</label><input id="age" aria-label="Longest a saved write may wait" value={age} disabled={!can} onChange={e => { setSaved(false); setAge(e.target.value) }} style={{ ...inputStyle, maxWidth: 140 }} inputMode="numeric" /></div>
        <div><label style={labelStyle} htmlFor="skew">Phone clock allowed ahead (minutes)</label><input id="skew" aria-label="Phone clock allowed ahead" value={skew} disabled={!can} onChange={e => { setSaved(false); setSkew(e.target.value) }} style={{ ...inputStyle, maxWidth: 140 }} inputMode="numeric" /></div>
      </div>
      <Err text={err} />{saved && <p role="status" style={{ fontSize: 12, color: "var(--hf-success-text)" }}>Saved.</p>}
      {can && <button style={{ ...btnPrimary, marginTop: 8 }} disabled={save.isPending} onClick={() => { const p = validateOffline(age, skew); if (p) return setErr(p); setErr(""); save.mutate() }}>Save</button>}
    </Panel>
  )
}

/** What became of recent push alerts, for support. Only people who can configure Fuel can read it. */
function AlertLog() {
  const can = useCanConfigure()
  const q = useQuery<Record<string, any>[]>({ queryKey: ["fuel-push-recent"], retry: false, enabled: can, queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/push/recent?limit=20")) ?? [] })
  if (!can) return null
  const rows = q.data ?? []
  return (
    <Panel title="Recent alerts">
      <Note>Types sent as push alerts: {PUSH_TYPES.map(p => p.label.toLowerCase()).join(", ")}. A person can switch a type off for themselves in the app. Status "no device" means that person has not signed in on a phone with alerts on.</Note>
      {q.isError && <Err text={msg(q.error, "Could not load recent alerts")} />}
      {!q.isError && rows.length === 0 && <p style={{ fontSize: 13 }}>No alerts yet.</p>}
      {rows.length > 0 && (
        <table style={{ width: "100%", fontSize: 12.5, borderCollapse: "collapse" }}>
          <thead><tr style={{ textAlign: "left" }}><th>When</th><th>Alert</th><th>Status</th><th>Tries</th><th>Problem</th></tr></thead>
          <tbody>{rows.map((r, i) => (
            <tr key={r.id ?? i} style={{ borderTop: "1px solid var(--hf-border)" }}>
              <td>{r.created_at ?? r.createdAt ? new Date(r.created_at ?? r.createdAt).toLocaleString() : ""}</td><td>{pushLabel(r.event_type ?? r.eventType ?? "")}</td>
              <td>{String(r.status ?? "").replace(/_/g, " ").toLowerCase()}</td><td>{r.attempts ?? 0}</td><td>{r.last_error ?? r.lastError ?? ""}</td>
            </tr>))}</tbody>
        </table>
      )}
    </Panel>
  )
}

interface Conflict { id: string; kind: string; reason: string; createdAt: string; capturedAt?: string | null }

/** Phone writes the server refused when they finally arrived. Nothing here changed stock or money. */
function ConflictsPanel() {
  const qc = useQueryClient()
  const can = usePermissions(["FUEL_MANAGE", "FUEL_FORECOURT_MANAGE", "FUEL_TRUCKSTOP_MANAGE", "FUEL_SHIFT_SUPERVISE"])
  const q = useQuery<Conflict[]>({ queryKey: ["fuel-offline-conflicts"], retry: false, enabled: can, queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/offline/conflicts")) ?? [] })
  const [err, setErr] = useState("")
  const resolve = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/fuel/offline/conflicts/${id}/resolve`, {}),
    onSuccess: () => { setErr(""); qc.invalidateQueries({ queryKey: ["fuel-offline-conflicts"] }) }, onError: (e: any) => setErr(msg(e, "Could not save")),
  })
  if (!can) return null
  const rows = q.data ?? []
  return (
    <Panel title="Refused offline writes">
      <Note>Saved on a phone without signal, and refused by the server when they arrived: a pump already on a shift, a meter that ran backwards, a shift already closed, a yard with no free bay. Check what really happened, put it right in the app or the office, then mark it as dealt with. That only clears it from this list.</Note>
      {q.isError && <Err text={msg(q.error, "Could not load the list")} />}
      {!q.isError && rows.length === 0 && <p style={{ fontSize: 13 }}>Nothing refused.</p>}
      {rows.map(c => (
        <div key={c.id} style={{ display: "flex", gap: 10, alignItems: "center", padding: "8px 0", borderTop: "1px solid var(--hf-border)" }}>
          <div style={{ flex: 1 }}>
            <strong style={{ fontSize: 13 }}>{CONFLICT_LABEL[c.kind] ?? c.kind}</strong>
            <div style={{ fontSize: 12.5, color: "var(--hf-danger-text)" }}>{c.reason}</div>
            <div style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>{c.capturedAt ? `Captured ${new Date(c.capturedAt).toLocaleString()}. ` : ""}Reached the server {new Date(c.createdAt).toLocaleString()}.</div>
          </div>
          <button style={btnGhost} aria-label={`Mark ${CONFLICT_LABEL[c.kind] ?? c.kind} as dealt with`} disabled={resolve.isPending} onClick={() => resolve.mutate(c.id)}>Dealt with</button>
        </div>
      ))}
      <Err text={err} />
    </Panel>
  )
}
