// src/pages/fleet/TyresBatteriesTab.tsx
//
// Tyres and batteries as stock items that move between stores and vehicles: register, fit/install, measure/test, remove, retread, scrap.
import { Fragment, useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import {
  BATTERY_REMOVE_REASONS, BATTERY_STATUS_LABEL, TEST_TONE, TREAD_LABEL, TYRE_REMOVE_REASONS, TYRE_STATUS_LABEL, batteryActions, batteryBody, batteryProblem, emptyBattery, emptyTyre,
  fitProblem, humanise, normalisePosition, removeProblem, retreadProblem, treadProblem, treadTone, tyreActions, tyreBody, tyreProblem, voltageProblem,
} from "./tyres.logic"

interface Tyre { id: string; serialNumber: string; brand: string; size: string; status: string; vehicleId: string | null; registration: string | null; position: string | null; lastTreadDepthMm: number | null; treadStatus: string; kmInService: number; retreadCount: number }
interface Battery { id: string; serialNumber: string; brand: string; nominalVoltage: number; capacityAh: number | null; status: string; registration: string | null; position: string | null; warrantyExpiry: string | null; inWarranty: boolean; lastVoltage: number | null; lastTestResult: string | null; daysInService: number }
interface VehicleOption { id: string; registration: string }

const list = <T,>(r: { data: unknown }): T[] => { const o = r.data as { data?: unknown }; const p = (o && typeof o === "object" && "data" in o ? o.data : r.data) as T[] | { content?: T[] }; return (Array.isArray(p) ? p : p?.content) ?? [] }
const errorOf = (e: unknown) => (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? "Something went wrong. Try again."
const TONE = { ok: "var(--hf-success-text-strong)", warn: "var(--hf-warning-text)", bad: "var(--hf-danger-text)", none: "var(--hf-text-faint)" }
const field: React.CSSProperties = { padding: "7px 9px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13 }
const btn: React.CSSProperties = { padding: "7px 14px", borderRadius: 8, border: "none", background: "var(--hf-brand)", color: "var(--hf-on-brand, #fff)", fontWeight: 700, fontSize: 12.5, cursor: "pointer" }
const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 14, marginBottom: 12 }
const th: React.CSSProperties = { textAlign: "left", padding: "8px 10px", fontSize: 11, textTransform: "uppercase", color: "var(--hf-text-faint)" }
const td: React.CSSProperties = { padding: "9px 10px", fontSize: 12.5, verticalAlign: "top" }

/** The small form under a row for the chosen action; `kind` picks tyre or battery endpoints. */
function ActionForm({ kind, id, action, onDone, onCancel }: { kind: "tyres" | "batteries"; id: string; action: string; onDone: () => void; onCancel: () => void }) {
  const [vehicleId, setVehicleId] = useState(""), [position, setPosition] = useState(""), [value, setValue] = useState(""), [outcome, setOutcome] = useState("IN_STOCK")
  const [reason, setReason] = useState(kind === "tyres" ? "WORN" : "REPLACED"), [scrapReason, setScrapReason] = useState(""), [cost, setCost] = useState("")
  const [error, setError] = useState<string | null>(null)
  const { data: vehicles = [] } = useQuery<VehicleOption[]>({ queryKey: ["fleet", "vehicle-options"], queryFn: async () => list<VehicleOption>(await apiClient.get("/api/v1/fleet/vehicles?size=200")) })
  const base = `/api/v1/fleet/${kind}/${id}`
  const needsVehicle = action === "FIT" || action === "INSTALL"
  const problem = needsVehicle ? fitProblem(vehicleId, position, kind === "tyres")
    : action === "MEASURE" ? treadProblem(value) : action === "TEST" ? voltageProblem(value) : action === "RETREAD" ? retreadProblem(value)
    : action === "REMOVE" ? removeProblem(outcome, scrapReason) : action === "SCRAP" && !scrapReason.trim() ? "Say why it is being scrapped." : null
  const send = useMutation({
    mutationFn: () => {
      switch (action) {
        case "FIT": return apiClient.post(`${base}/fit`, { vehicleId, position: normalisePosition(position) })
        case "INSTALL": return apiClient.post(`${base}/install`, { vehicleId, position: position.trim() || null })
        case "MEASURE": return apiClient.post(`${base}/measurements`, { treadDepthMm: Number(value) })
        case "TEST": return apiClient.post(`${base}/tests`, { voltage: Number(value) })
        case "RETREAD": return apiClient.post(`${base}/retread`, { newTreadDepthMm: Number(value), cost: cost.trim() === "" ? null : Number(cost) })
        case "REMOVE": return apiClient.post(`${base}/remove`, { outcome, reason, scrapReason: outcome === "SCRAPPED" ? scrapReason.trim() : null })
        default: return apiClient.post(`${base}/scrap`, { reason: scrapReason.trim() })
      }
    },
    onSuccess: onDone, onError: e => setError(errorOf(e)),
  })
  return (
    <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center", padding: "8px 0" }}>
      {needsVehicle && <select aria-label="Vehicle" style={field} value={vehicleId} onChange={e => setVehicleId(e.target.value)}><option value="">Vehicle…</option>{vehicles.map(v => <option key={v.id} value={v.id}>{v.registration}</option>)}</select>}
      {needsVehicle && <input aria-label="Position" placeholder={kind === "tyres" ? "FL, FR, RL, RR, SPARE" : "1 (or 2)"} style={{ ...field, width: 150 }} value={position} onChange={e => setPosition(e.target.value)} />}
      {(action === "MEASURE" || action === "RETREAD") && <input aria-label={action === "MEASURE" ? "Tread depth (mm)" : "New tread depth (mm)"} type="number" min="0" step="0.1" placeholder="Tread (mm)" style={{ ...field, width: 120 }} value={value} onChange={e => setValue(e.target.value)} />}
      {action === "RETREAD" && <input aria-label="Retread cost" type="number" min="0" placeholder="Cost (R)" style={{ ...field, width: 110 }} value={cost} onChange={e => setCost(e.target.value)} />}
      {action === "TEST" && <input aria-label="Resting voltage" type="number" min="0" step="0.01" placeholder="Voltage (V)" style={{ ...field, width: 120 }} value={value} onChange={e => setValue(e.target.value)} />}
      {action === "REMOVE" && <>
        <select aria-label="Outcome" style={field} value={outcome} onChange={e => setOutcome(e.target.value)}>
          {(kind === "tyres" ? ["IN_STOCK", "AWAITING_RETREAD", "SCRAPPED"] : ["IN_STOCK", "FAULTY", "SCRAPPED"]).map(o => <option key={o} value={o}>{humanise(o)}</option>)}</select>
        <select aria-label="Reason" style={field} value={reason} onChange={e => setReason(e.target.value)}>{(kind === "tyres" ? TYRE_REMOVE_REASONS : BATTERY_REMOVE_REASONS).map(o => <option key={o} value={o}>{humanise(o)}</option>)}</select></>}
      {((action === "REMOVE" && outcome === "SCRAPPED") || action === "SCRAP") && <input aria-label="Scrap reason" placeholder="Why scrap it?" style={{ ...field, minWidth: 220 }} value={scrapReason} onChange={e => setScrapReason(e.target.value)} />}
      <button type="button" style={{ ...btn, opacity: problem || send.isPending ? 0.5 : 1 }} disabled={!!problem || send.isPending} onClick={() => send.mutate()}>Confirm</button>
      <button type="button" style={field} onClick={onCancel}>Cancel</button>
      {problem && <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{problem}</span>}
      {error && <span role="alert" style={{ fontSize: 12.5, color: "var(--hf-danger-text)" }}>{error}</span>}
    </div>
  )
}

function RegisterForm({ kind, onDone }: { kind: "tyres" | "batteries"; onDone: () => void }) {
  const [t, setT] = useState(emptyTyre()), [b, setB] = useState(emptyBattery())
  const [error, setError] = useState<string | null>(null)
  const problem = kind === "tyres" ? tyreProblem(t) : batteryProblem(b)
  const save = useMutation({ mutationFn: () => apiClient.post(`/api/v1/fleet/${kind}`, kind === "tyres" ? tyreBody(t) : batteryBody(b)), onSuccess: onDone, onError: e => setError(errorOf(e)) })
  return (
    <div style={card}>
      <h3 style={{ marginTop: 0 }}>{kind === "tyres" ? "Register a tyre" : "Register a battery"}</h3>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        {kind === "tyres" ? <>
          <input aria-label="Serial number" placeholder="Serial number" style={field} value={t.serialNumber} onChange={e => setT({ ...t, serialNumber: e.target.value })} />
          <input aria-label="Brand" placeholder="Brand" style={field} value={t.brand} onChange={e => setT({ ...t, brand: e.target.value })} />
          <input aria-label="Size" placeholder="295/80R22.5" style={field} value={t.size} onChange={e => setT({ ...t, size: e.target.value })} />
          <input aria-label="Price" type="number" min="0" placeholder="Price (R)" style={{ ...field, width: 110 }} value={t.purchasePrice} onChange={e => setT({ ...t, purchasePrice: e.target.value })} />
          <input aria-label="Tread when new (mm)" type="number" min="0" placeholder="Tread new (mm)" style={{ ...field, width: 130 }} value={t.initialTreadDepthMm} onChange={e => setT({ ...t, initialTreadDepthMm: e.target.value })} /></> : <>
          <input aria-label="Serial number" placeholder="Serial number" style={field} value={b.serialNumber} onChange={e => setB({ ...b, serialNumber: e.target.value })} />
          <input aria-label="Brand" placeholder="Brand" style={field} value={b.brand} onChange={e => setB({ ...b, brand: e.target.value })} />
          <select aria-label="Voltage" style={field} value={b.nominalVoltage} onChange={e => setB({ ...b, nominalVoltage: e.target.value })}><option value="12">12 V</option><option value="24">24 V</option></select>
          <input aria-label="Purchase date" type="date" style={field} value={b.purchaseDate} onChange={e => setB({ ...b, purchaseDate: e.target.value })} />
          <input aria-label="Warranty months" type="number" min="0" placeholder="Warranty (months)" style={{ ...field, width: 140 }} value={b.warrantyMonths} onChange={e => setB({ ...b, warrantyMonths: e.target.value })} /></>}
        <button type="button" style={{ ...btn, opacity: problem || save.isPending ? 0.5 : 1 }} disabled={!!problem || save.isPending} onClick={() => save.mutate()}>Register</button>
      </div>
      {problem && <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 6 }}>{problem}</div>}
      {error && <div role="alert" style={{ fontSize: 12.5, color: "var(--hf-danger-text)", marginTop: 6 }}>{error}</div>}
    </div>
  )
}

function Panel({ kind }: { kind: "tyres" | "batteries" }) {
  const qc = useQueryClient()
  const canManage = usePermission("FLEET_MANAGE")
  const [status, setStatus] = useState(""), [registering, setRegistering] = useState(false), [active, setActive] = useState<{ id: string; action: string } | null>(null)
  const { data = [], isLoading, isError } = useQuery<(Tyre | Battery)[]>({ queryKey: ["fleet", kind, status], queryFn: async () => list<Tyre | Battery>(await apiClient.get(`/api/v1/fleet/${kind}${status ? `?status=${status}` : ""}`)) })
  const labels = kind === "tyres" ? TYRE_STATUS_LABEL : BATTERY_STATUS_LABEL
  const done = () => { setActive(null); setRegistering(false); qc.invalidateQueries({ queryKey: ["fleet", kind] }) }
  return (
    <div>
      <div style={{ display: "flex", gap: 10, marginBottom: 12 }}>
        <select aria-label="Filter by status" style={field} value={status} onChange={e => setStatus(e.target.value)}><option value="">All</option>{Object.entries(labels).map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select>
        {canManage && <button type="button" style={btn} onClick={() => setRegistering(r => !r)}>{registering ? "Cancel" : kind === "tyres" ? "Register a tyre" : "Register a battery"}</button>}
      </div>
      {registering && <RegisterForm kind={kind} onDone={done} />}
      {isLoading && <p>Loading…</p>}
      {isError && <div role="alert">Couldn't load {kind}.</div>}
      {!isLoading && !isError && data.length === 0 && <p style={{ color: "var(--hf-text-muted)" }}>No {kind} yet.</p>}
      {data.length > 0 && (
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr>{(kind === "tyres" ? ["Serial", "Brand / size", "Status", "Where", "Tread", "Km", ""] : ["Serial", "Brand", "Status", "Where", "Last test", "Warranty", ""]).map(h => <th key={h} style={th}>{h}</th>)}</tr></thead>
          <tbody>{data.map(row => {
            const tyre = kind === "tyres" ? (row as Tyre) : null, bat = kind === "batteries" ? (row as Battery) : null
            const actions = kind === "tyres" ? tyreActions(row.status) : batteryActions(row.status)
            return (
              <Fragment key={row.id}>
                <tr aria-label={row.serialNumber} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                  <td style={td}><strong>{row.serialNumber}</strong></td>
                  <td style={td}>{row.brand}{tyre ? ` · ${tyre.size}` : bat ? ` · ${bat.nominalVoltage} V` : ""}</td>
                  <td style={td}>{labels[row.status] ?? row.status}</td>
                  <td style={td}>{row.registration ? `${row.registration} · ${row.position}` : "Store"}</td>
                  {tyre && <td style={{ ...td, color: TONE[treadTone(tyre.treadStatus)], fontWeight: 700 }}>{tyre.lastTreadDepthMm != null ? `${tyre.lastTreadDepthMm} mm · ` : ""}{TREAD_LABEL[tyre.treadStatus]}</td>}
                  {tyre && <td style={td}>{tyre.kmInService.toLocaleString("en-ZA")}{tyre.retreadCount > 0 ? ` (${tyre.retreadCount}× retread)` : ""}</td>}
                  {bat && <td style={{ ...td, color: bat.lastTestResult ? TONE[TEST_TONE[bat.lastTestResult]] : undefined, fontWeight: 700 }}>{bat.lastTestResult ? `${bat.lastTestResult.toLowerCase()} · ${bat.lastVoltage} V` : "Never tested"}</td>}
                  {bat && <td style={td}>{bat.warrantyExpiry ? `${bat.warrantyExpiry}${bat.inWarranty ? "" : " (expired)"}` : "—"}</td>}
                  <td style={td}>{canManage && !active && actions.map(a => <button key={a.action} type="button" aria-label={`${a.label} ${row.serialNumber}`} style={{ ...field, marginRight: 6, cursor: "pointer" }} onClick={() => setActive({ id: row.id, action: a.action })}>{a.label}</button>)}</td>
                </tr>
                {active?.id === row.id && <tr><td colSpan={7} style={{ background: "var(--hf-surface-muted)", padding: "0 10px" }}><ActionForm kind={kind} id={row.id} action={active.action} onDone={done} onCancel={() => setActive(null)} /></td></tr>}
              </Fragment>)
          })}</tbody>
        </table>)}
    </div>
  )
}

export default function TyresBatteriesTab() {
  const [tab, setTab] = useState<"tyres" | "batteries">("tyres")
  return (
    <div>
      <div role="tablist" style={{ display: "flex", gap: 4, borderBottom: "1px solid var(--hf-border)", marginBottom: 12 }}>
        {(["tyres", "batteries"] as const).map(t => <button key={t} role="tab" aria-selected={tab === t} onClick={() => setTab(t)}
          style={{ padding: "6px 14px", border: "none", background: "none", cursor: "pointer", fontSize: 13.5, fontWeight: tab === t ? 800 : 500, borderBottom: tab === t ? "2px solid var(--hf-brand)" : "2px solid transparent", color: "var(--hf-text)" }}>{t === "tyres" ? "Tyres" : "Batteries"}</button>)}
      </div>
      <Panel key={tab} kind={tab} />
    </div>
  )
}
