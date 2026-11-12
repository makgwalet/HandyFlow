// F02: actual versus estimated cost of routes, by route, tanker and depot. Estimates come from the rates in Settings;
// actuals are what the office records against each route. Only vehicle and driver are compared.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { Modal, btnPrimary, btnGhost, card, inputStyle, labelStyle, Chip } from "../warehousing/whseUi"
import { fmtL, fmtR } from "./orderOps"
import { CATEGORIES, categoryLabel, flagText, periodDates, validateCost, varianceText, type CostEntry, type Rollup, type RouteCostReport, type RouteCostRow } from "./routeCostOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const msg = (e: any, fb: string) => e?.response?.data?.message ?? fb
const th: React.CSSProperties = { textAlign: "right", padding: "8px 10px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)", whiteSpace: "nowrap" }
const td: React.CSSProperties = { padding: "8px 10px", fontSize: 13, color: "var(--hf-text)", textAlign: "right", whiteSpace: "nowrap" }
const Err = ({ text }: { text: string }) => text ? <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: "8px 0 0" }}>{text}</p> : null

export default function RouteCostsPanel({ days }: { days: number }) {
  const canRecord = usePermissions(["FUEL_MANAGE", "FUEL_TRANSPORT_MANAGE"])
  const qc = useQueryClient()
  const { from, to } = periodDates(days)
  const [recording, setRecording] = useState<RouteCostRow | null>(null)
  const q = useQuery<RouteCostReport>({ queryKey: ["fuel-route-costs", from, to], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/cost-actuals", { params: { from, to } })) })
  const rep = q.data
  const routes = Array.isArray(rep?.routes) ? rep!.routes : []
  const refresh = () => qc.invalidateQueries({ queryKey: ["fuel-route-costs"] })

  return (
    <div style={{ marginTop: 24 }}>
      <h3 style={{ margin: "0 0 4px", fontSize: 14 }}>Routes: actual against estimate</h3>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 10px" }}>
        The estimate is a round trip to the farthest stop at your cost per km, plus your typical trip hours at your hourly rate. The actual is what you record against each route. Only vehicle and driver are compared; tolls and other costs are shown but not compared. A figure shows a dash when what it needs is missing.
      </p>
      {q.isLoading ? <p style={{ color: "var(--hf-text-muted)", fontSize: 13 }}>Loading…</p> : q.isError || !rep ? <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>Could not load route costs.</p> : routes.length === 0
        ? <p style={{ color: "var(--hf-text-muted)", fontSize: 13 }}>No routes with deliveries in this period.</p> : (
        <>
          {rep.unreconciledRoutes > 0 && <p role="status" style={{ fontSize: 12.5, color: "var(--hf-warning-text-strong)", margin: "0 0 8px" }}>{rep.unreconciledRoutes} route{rep.unreconciledRoutes === 1 ? " has" : "s have"} no actual vehicle or driver cost recorded yet.</p>}
          <div style={{ ...card, overflowX: "auto", marginBottom: 14 }}>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr>
                <th style={{ ...th, textAlign: "left" }}>Route</th><th style={th}>Litres</th><th style={th}>Estimate</th><th style={th}>Actual</th><th style={th}>Difference</th><th style={th}>Actual / L</th><th style={th}></th>
              </tr></thead>
              <tbody>
                {routes.map(r => (
                  <tr key={r.routeId} style={{ borderTop: "1px solid var(--hf-border)" }}>
                    <td style={{ ...td, textAlign: "left", whiteSpace: "normal" }}>
                      <div style={{ fontWeight: 600 }}>{r.routeDate} · {r.label ?? "Route"}</div>
                      <div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>{r.tankerRegistration ?? "tanker"} · {r.driverName ?? "driver"} · {r.deliveredCount} of {r.stops} delivered</div>
                      {r.flags.length > 0 && <div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>{r.flags.map(flagText).join("; ")}</div>}
                    </td>
                    <td style={td}>{fmtL(r.litresDelivered)}</td>
                    <td style={td}>{r.estimateTotal == null ? "–" : fmtR(r.estimateTotal)}</td>
                    <td style={td}>{r.actualTotal == null ? "–" : fmtR(r.actualTotal)}</td>
                    <td style={{ ...td, fontWeight: 700, color: (r.variance ?? 0) > 0 ? "var(--hf-danger-text)" : undefined }}>{varianceText(r.variance, r.variancePct, fmtR)}</td>
                    <td style={td}>{r.actualCostPerLitre == null ? "–" : fmtR(r.actualCostPerLitre)}</td>
                    <td style={td}>{canRecord && <button style={btnGhost} onClick={() => setRecording(r)}>Costs</button>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Rollups title="By tanker" rows={rep.byTanker} />
          <Rollups title="By depot" rows={rep.byDepot} note="A route's figures are shared across depots by litres delivered from each depot's tanks." />
        </>
      )}
      {recording && <CostsDialog route={recording} canRecord={canRecord} onChanged={refresh} onClose={() => setRecording(null)} />}
    </div>
  )
}

function Rollups({ title, rows, note }: { title: string; rows: Rollup[] | undefined; note?: string }) {
  const list = Array.isArray(rows) ? rows : []
  if (list.length === 0) return null
  return (
    <div style={{ marginBottom: 14 }}>
      <div style={{ fontSize: 12, fontWeight: 700, color: "var(--hf-text-muted)", marginBottom: 4 }}>{title}</div>
      {note && <div style={{ fontSize: 11, color: "var(--hf-text-muted)", marginBottom: 4 }}>{note}</div>}
      <div style={{ ...card, overflowX: "auto" }}>
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr><th style={{ ...th, textAlign: "left" }}>{title.replace("By ", "")}</th><th style={th}>Routes</th><th style={th}>Litres</th><th style={th}>Estimate</th><th style={th}>Actual</th><th style={th}>Difference</th><th style={th}>All actual costs</th><th style={th}>Actual / L</th></tr></thead>
          <tbody>
            {list.map(r => (
              <tr key={r.key} style={{ borderTop: "1px solid var(--hf-border)" }}>
                <td style={{ ...td, textAlign: "left" }}>{r.label}{r.reconciledRoutes < r.routes && <> <Chip bg="var(--hf-warning-soft)" fg="var(--hf-warning-text)">{r.routes - r.reconciledRoutes} not reconciled</Chip></>}</td>
                <td style={td}>{r.routes}</td><td style={td}>{fmtL(r.litres)}</td><td style={td}>{fmtR(r.estimate)}</td><td style={td}>{fmtR(r.comparableActual)}</td>
                <td style={td}>{varianceText(r.estimate > 0 || r.comparableActual > 0 ? r.variance : null, null, fmtR)}</td>
                <td style={td}>{fmtR(r.actualTotal)}</td><td style={td}>{r.actualCostPerLitre == null ? "–" : fmtR(r.actualCostPerLitre)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function CostsDialog({ route, canRecord, onChanged, onClose }: { route: RouteCostRow; canRecord: boolean; onChanged: () => void; onClose: () => void }) {
  const qc = useQueryClient()
  const [category, setCategory] = useState(""); const [amount, setAmount] = useState(""); const [quantity, setQuantity] = useState(""); const [unit, setUnit] = useState(""); const [note, setNote] = useState("")
  const [voiding, setVoiding] = useState<string | null>(null); const [reason, setReason] = useState(""); const [err, setErr] = useState("")
  const list = useQuery<CostEntry[]>({ queryKey: ["fuel-route-cost-entries", route.routeId], queryFn: async () => unwrap(await apiClient.get(`/api/v1/fuel/cost-actuals/routes/${route.routeId}/entries`)) })
  const done = () => { qc.invalidateQueries({ queryKey: ["fuel-route-cost-entries", route.routeId] }); onChanged() }
  const add = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/fuel/cost-actuals/routes/${route.routeId}/entries`, { category, amount: Number(amount), quantity: quantity.trim() ? Number(quantity) : null, unit: unit.trim() || null, note: note.trim() || null }),
    onSuccess: () => { setCategory(""); setAmount(""); setQuantity(""); setUnit(""); setNote(""); setErr(""); done() }, onError: (e: any) => setErr(msg(e, "Could not record the cost")),
  })
  const voidIt = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/fuel/cost-actuals/entries/${id}/void`, { reason: reason.trim() }),
    onSuccess: () => { setVoiding(null); setReason(""); setErr(""); done() }, onError: (e: any) => setErr(msg(e, "Could not void the entry")),
  })
  const entries = Array.isArray(list.data) ? list.data : []
  return (
    <Modal title={`Costs: ${route.label ?? "Route"} · ${route.routeDate}`} onClose={onClose} width={560}>
      {entries.length === 0 ? <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No costs recorded yet.</p> : (
        <table style={{ width: "100%", borderCollapse: "collapse", marginBottom: 10 }}>
          <tbody>
            {entries.map(e => (
              <tr key={e.id} style={{ borderTop: "1px solid var(--hf-border)", fontSize: 13, opacity: e.status === "VOIDED" ? 0.55 : 1 }}>
                <td style={{ padding: "6px 8px" }}>{categoryLabel(e.category)}{e.note ? ` · ${e.note}` : ""}{e.quantity != null ? ` · ${e.quantity} ${e.unit ?? ""}` : ""}</td>
                <td style={{ padding: "6px 8px", textAlign: "right" }}>{e.status === "VOIDED" ? <span title={e.voidReason ?? ""}>Voided</span> : fmtR(e.amount)}</td>
                <td style={{ padding: "6px 8px", textAlign: "right" }}>{canRecord && e.status === "ACTIVE" && <button style={btnGhost} aria-label={`Void ${categoryLabel(e.category)} ${e.amount}`} onClick={() => { setVoiding(e.id); setReason("") }}>Void</button>}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {voiding && (
        <div style={{ marginBottom: 10 }}>
          <label style={labelStyle}>Reason for voiding *</label><input aria-label="Void reason" value={reason} onChange={e => setReason(e.target.value)} style={inputStyle} />
          <div style={{ display: "flex", gap: 8, marginTop: 8, justifyContent: "flex-end" }}>
            <button style={btnGhost} onClick={() => setVoiding(null)}>Back</button>
            <button style={btnPrimary} disabled={voidIt.isPending} onClick={() => { if (!reason.trim()) return setErr("Give a reason."); setErr(""); voidIt.mutate(voiding) }}>Void entry</button>
          </div>
        </div>
      )}
      {canRecord && (
        <div style={{ borderTop: "1px solid var(--hf-border)", paddingTop: 10 }}>
          <label style={labelStyle}>Cost type *</label>
          <select aria-label="Cost type" value={category} onChange={e => setCategory(e.target.value)} style={inputStyle}>
            <option value="">Choose…</option>{CATEGORIES.map(c => <option key={c.code} value={c.code}>{c.label} ({c.hint})</option>)}
          </select>
          <label style={{ ...labelStyle, marginTop: 8 }}>Amount (R) *</label><input aria-label="Cost amount" type="number" value={amount} onChange={e => setAmount(e.target.value)} style={inputStyle} />
          <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
            <div style={{ flex: 1 }}><label style={labelStyle}>Quantity (km or hours)</label><input aria-label="Cost quantity" type="number" value={quantity} onChange={e => setQuantity(e.target.value)} style={inputStyle} /></div>
            <div style={{ width: 90 }}><label style={labelStyle}>Unit</label><input aria-label="Cost unit" value={unit} onChange={e => setUnit(e.target.value)} style={inputStyle} placeholder="km" /></div>
          </div>
          <label style={{ ...labelStyle, marginTop: 8 }}>Note</label><input aria-label="Cost note" value={note} onChange={e => setNote(e.target.value)} style={inputStyle} />
          <Err text={err} />
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 12 }}>
            <button style={btnGhost} onClick={onClose}>Close</button>
            <button style={btnPrimary} disabled={add.isPending} onClick={() => { const v = validateCost(category, amount, quantity, note); if (v) return setErr(v); setErr(""); add.mutate() }}>Record cost</button>
          </div>
        </div>
      )}
      {!canRecord && <Err text={err} />}
    </Modal>
  )
}
