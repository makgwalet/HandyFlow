// D01: tank-to-pump reconciliation. Collapsed until opened, so it costs nothing on the forecourt page.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { btnGhost, btnPrimary, card, Chip, inputStyle } from "../warehousing/whseUi"
import { fmtL } from "./forecourtOps"
import { validateTolerance, varianceSentence, windowParams, type TankReconciliation } from "./tankReconciliationOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const BASE = "/api/v1/fuel/forecourt"

export default function TankReconciliationPanel() {
  const [open, setOpen] = useState(false)
  const [tankId, setTankId] = useState(""); const [fromDay, setFromDay] = useState(""); const [toDay, setToDay] = useState(""); const [tol, setTol] = useState("")
  const [run, setRun] = useState<{ tankId: string; from?: string; to?: string; tol: string } | null>(null)
  const [err, setErr] = useState("")
  const tanks = useQuery<{ id: string; name: string }[]>({ queryKey: ["fuel-recon-tanks"], enabled: open, queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/tanks")) })
  const result = useQuery<TankReconciliation>({
    queryKey: ["fuel-tank-recon", run], enabled: !!run,
    queryFn: async () => {
      const p = new URLSearchParams(); if (run!.from) p.set("from", run!.from); if (run!.to) p.set("to", run!.to); if (run!.tol.trim() !== "") p.set("tolerancePct", run!.tol.trim())
      return unwrap(await apiClient.get(`${BASE}/tanks/${run!.tankId}/reconciliation${p.toString() ? `?${p}` : ""}`))
    },
  })

  if (!open) return <div style={{ marginTop: 18 }}><button style={btnGhost} onClick={() => setOpen(true)}>Tank reconciliation</button></div>
  const r = result.data
  const go = () => {
    const v = validateTolerance(tol) ?? (tankId ? null : "Choose a tank.")
    if (v) return setErr(v)
    const w = windowParams(fromDay, toDay)
    if (w.error) return setErr(w.error)
    setErr(""); setRun({ tankId, from: w.from, to: w.to, tol })
  }
  return (
    <section aria-label="Tank reconciliation" style={{ ...card, padding: 14, marginTop: 18 }}>
      <strong>Tank reconciliation</strong>
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "4px 0 10px" }}>Opening dip plus everything recorded between two dips, against the closing dip. Also checks that the pump meters agree with the stock ledger. Needs at least two dip readings in the period. Nothing is flagged unless you give a tolerance.</p>
      <div style={{ display: "flex", flexWrap: "wrap", gap: 8, alignItems: "end" }}>
        <label style={{ fontSize: 12 }}>Tank<br /><select aria-label="Tank" style={inputStyle} value={tankId} onChange={e => setTankId(e.target.value)}><option value="">Choose…</option>{(tanks.data ?? []).map(t => <option key={t.id} value={t.id}>{t.name}</option>)}</select></label>
        <label style={{ fontSize: 12 }}>From<br /><input aria-label="From" type="date" style={inputStyle} value={fromDay} onChange={e => setFromDay(e.target.value)} /></label>
        <label style={{ fontSize: 12 }}>To<br /><input aria-label="To" type="date" style={inputStyle} value={toDay} onChange={e => setToDay(e.target.value)} /></label>
        <label style={{ fontSize: 12 }}>Tolerance (%)<br /><input aria-label="Tolerance (%)" inputMode="decimal" style={{ ...inputStyle, width: 90 }} placeholder="None" value={tol} onChange={e => setTol(e.target.value)} /></label>
        <button style={btnPrimary} onClick={go}>Reconcile</button>
        <button style={btnGhost} onClick={() => setOpen(false)}>Close</button>
      </div>
      {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{err}</p>}
      {result.isError && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{(result.error as any)?.response?.data?.message ?? "Could not reconcile this tank"}</p>}
      {r && !r.ready && <p style={{ fontSize: 13, marginTop: 10 }}>{r.message}</p>}
      {r && r.ready && (
        <div style={{ marginTop: 12, fontSize: 13 }}>
          <table style={{ borderCollapse: "collapse", minWidth: 360 }}>
            <tbody>
              <tr><td style={{ padding: "4px 12px 4px 0" }}>Opening dip ({r.fromDipAt?.slice(0, 10)})</td><td style={{ textAlign: "right" }}>{fmtL(r.fromDipLitres)}</td></tr>
              {r.movements.map(m => <tr key={m.label}><td style={{ padding: "4px 12px 4px 0" }}>{m.label} ({m.count})</td><td style={{ textAlign: "right" }}>{Number(m.litres) > 0 ? "+" : ""}{fmtL(m.litres)}</td></tr>)}
              <tr style={{ borderTop: "1px solid var(--hf-border)" }}><td style={{ padding: "4px 12px 4px 0" }}><strong>Should be in the tank</strong></td><td style={{ textAlign: "right" }}><strong>{fmtL(r.expectedLitres)}</strong></td></tr>
              <tr><td style={{ padding: "4px 12px 4px 0" }}>Closing dip ({r.toDipAt?.slice(0, 10)})</td><td style={{ textAlign: "right" }}>{fmtL(r.toDipLitres)}</td></tr>
              <tr><td style={{ padding: "4px 12px 4px 0" }}><strong>Difference</strong></td><td style={{ textAlign: "right" }}><strong>{fmtL(r.varianceLitres)}</strong></td></tr>
            </tbody>
          </table>
          <p style={{ margin: "8px 0" }}>{varianceSentence(r)} {r.tolerancePct != null && (r.outsideTolerance ? <Chip bg="var(--hf-danger-soft)" fg="var(--hf-danger-text)">Outside {r.tolerancePct}% tolerance</Chip> : <Chip bg="var(--hf-success-soft-strong)" fg="var(--hf-success-text-strong)">Within {r.tolerancePct}% tolerance</Chip>)}</p>
          <p style={{ margin: "8px 0", color: "var(--hf-text-muted)" }}>Pump meters recorded {fmtL(r.meterLitres)}; the stock ledger shows {fmtL(r.ledgerForecourtLitres)} of forecourt sales. {Number(r.meterVsLedgerLitres) === 0 ? "They agree." : `They differ by ${fmtL(Math.abs(Number(r.meterVsLedgerLitres)))}: check for a shift closed outside this period or a reversed shift.`}</p>
        </div>
      )}
    </section>
  )
}
