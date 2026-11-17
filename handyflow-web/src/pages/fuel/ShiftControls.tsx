import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { btnGhost, btnPrimary, card } from "../warehousing/whseUi"
import { amountProblem, asControls, overrideText, repeatProblem, repeatSummary, type ShiftControls as Controls } from "./shiftControlOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const msg = (e: any, fb: string) => e?.response?.data?.message ?? fb
const base = "/api/v1/fuel/shift-controls"

/** Tolerances for one site or one fuel type, and the repeated-variance rule. Nothing set means the company-wide numbers apply. */
export default function ShiftControls() {
  const qc = useQueryClient()
  const q = useQuery<Controls>({ queryKey: ["fuel-shift-controls"], queryFn: async () => asControls(unwrap(await apiClient.get(base))) })
  const depots = useQuery<{ id: string; name: string }[]>({ queryKey: ["fuel-scope-depots"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/depots")) ?? [] })
  const [depot, setDepot] = useState(""); const [depotAmt, setDepotAmt] = useState("")
  const [fuel, setFuel] = useState(""); const [fuelAmt, setFuelAmt] = useState("")
  const [count, setCount] = useState(""); const [days, setDays] = useState("")
  const [err, setErr] = useState("")
  const done = { onSuccess: () => { setErr(""); qc.invalidateQueries({ queryKey: ["fuel-shift-controls"] }) }, onError: (e: any) => setErr(msg(e, "Could not save")) }
  const saveOverride = useMutation({ mutationFn: (b: { scope: string; key: string; amount: number }) => apiClient.put(`${base}/overrides`, b), ...done })
  const removeOverride = useMutation({ mutationFn: (o: { scope: string; key: string }) => apiClient.delete(`${base}/overrides`, { params: o }), ...done })
  const savePolicy = useMutation({ mutationFn: (b: { repeatCount: number | null; repeatDays: number | null }) => apiClient.put(`${base}/repeat-policy`, b), ...done })

  if (!q.data) return <div style={{ ...card, padding: 16, marginTop: 14, fontSize: 12.5 }}>Loading shift controls…</div>
  const list = Array.isArray(depots.data) ? depots.data.filter(d => d && typeof d.id === "string" && typeof d.name === "string") : []
  const dProblem = depotAmt === "" ? null : amountProblem(depotAmt)
  const fProblem = fuelAmt === "" ? null : amountProblem(fuelAmt)
  const rProblem = count === "" && days === "" ? null : repeatProblem(count, days)

  return (
    <div style={{ ...card, padding: 16, marginTop: 14 }}>
      <h3 style={{ margin: "0 0 4px", fontSize: 14 }}>Tolerances by site and product</h3>
      <p style={{ margin: "0 0 10px", fontSize: 12.5, color: "var(--hf-text-muted)" }}>
        The company-wide cash variance tolerance still applies everywhere. Set a different one for a site, or a different gauge difference for a fuel type. A shift that draws on several sites uses the smallest.
      </p>
      {q.data.overrides.length === 0 ? <p style={{ fontSize: 12.5 }}>None set: the company-wide numbers apply.</p> : q.data.overrides.map(o => (
        <div key={o.scope + o.key} style={{ fontSize: 13, padding: "3px 0" }}>{overrideText(o)} <button style={btnGhost} onClick={() => removeOverride.mutate({ scope: o.scope, key: o.key })}>Remove</button></div>
      ))}
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", margin: "10px 0 4px", alignItems: "center" }}>
        <select aria-label="Site" value={depot} onChange={e => setDepot(e.target.value)}><option value="">Site…</option>{list.map(d => <option key={d.id} value={d.id}>{d.name}</option>)}</select>
        <input aria-label="Cash variance tolerance for the site" inputMode="decimal" placeholder="Cash tolerance (R)" value={depotAmt} onChange={e => setDepotAmt(e.target.value)} style={{ width: 150 }} />
        <button style={btnGhost} disabled={!depot || depotAmt === "" || !!dProblem} onClick={() => { saveOverride.mutate({ scope: "DEPOT", key: depot, amount: Number(depotAmt.replace(",", ".")) }); setDepotAmt("") }}>Set for site</button>
      </div>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 4, alignItems: "center" }}>
        <input aria-label="Fuel type" placeholder="Fuel type, e.g. DIESEL" value={fuel} onChange={e => setFuel(e.target.value)} style={{ width: 170 }} />
        <input aria-label="Gauge difference for the fuel type" inputMode="decimal" placeholder="Gauge difference (L)" value={fuelAmt} onChange={e => setFuelAmt(e.target.value)} style={{ width: 150 }} />
        <button style={btnGhost} disabled={!fuel.trim() || fuelAmt === "" || !!fProblem} onClick={() => { saveOverride.mutate({ scope: "PRODUCT", key: fuel.trim(), amount: Number(fuelAmt.replace(",", ".")) }); setFuelAmt(""); setFuel("") }}>Set for fuel type</button>
      </div>
      {(dProblem || fProblem) && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{dProblem ?? fProblem}</p>}

      <h4 style={{ margin: "14px 0 4px", fontSize: 13 }}>Repeated variances</h4>
      <p style={{ margin: "0 0 6px", fontSize: 12.5 }}>{repeatSummary(q.data.policy)}</p>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
        <input aria-label="Number of shifts" inputMode="numeric" placeholder="Shifts" value={count} onChange={e => setCount(e.target.value)} style={{ width: 90 }} />
        <input aria-label="Number of days" inputMode="numeric" placeholder="Days" value={days} onChange={e => setDays(e.target.value)} style={{ width: 90 }} />
        <button style={btnPrimary} disabled={rProblem !== null || count === ""} onClick={() => savePolicy.mutate({ repeatCount: Number(count), repeatDays: Number(days) })}>Save rule</button>
        <button style={btnGhost} onClick={() => { setCount(""); setDays(""); savePolicy.mutate({ repeatCount: null, repeatDays: null }) }}>Switch off</button>
      </div>
      {(rProblem && (count !== "" || days !== "")) && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{rProblem}</p>}
      {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{err}</p>}
    </div>
  )
}
