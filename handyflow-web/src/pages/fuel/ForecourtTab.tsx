// Stage 5: retail forecourt. Pumps, attendant shifts (open with meters, close with takings) and a weekly summary.
import { Fragment, useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { Modal, btnPrimary, btnGhost, card, inputStyle, labelStyle, Chip } from "../warehousing/whseUi"
import ShiftAdjustmentsPanel from "./ShiftAdjustmentsPanel"
import TankReconciliationPanel from "./TankReconciliationPanel"
import type { Controls } from "./wholesaleOps"
import {
  fmtL, fmtR, validatePump, validateOpen, validateClose, expectedTotals, takings, needsReason, varianceText, pumpTotals, awaitingReview,
  approvedAdjustmentTotal, pendingAdjustments, VARIANCE_TOLERANCE, validateNozzle, nozzleFields, nozzleLabel,
  type Pump, type Shift, type ShiftAdjustment, type ForecourtSummary, type CloseRow,
} from "./forecourtOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const msg = (e: any, fb: string) => e?.response?.data?.message ?? fb
const th: React.CSSProperties = { textAlign: "left", padding: "10px 12px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)", whiteSpace: "nowrap" }
const td: React.CSSProperties = { padding: "10px 12px", fontSize: 13, color: "var(--hf-text)" }
const BASE = "/api/v1/fuel/forecourt"

type Dialog = { kind: "open" } | { kind: "review"; shift: Shift } | { kind: "close"; shift: Shift } | { kind: "reverse"; shift: Shift } | { kind: "pump"; pump?: Pump } | null

function Err({ text }: { text: string }) { return <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12.5, margin: "10px 0 0" }}>{text}</p> }

const statusChip = (s: Shift["status"]) =>
  s === "OPEN" ? <Chip bg="var(--hf-info-soft)" fg="var(--hf-info-text)">Open</Chip>
  : s === "REVERSED" ? <Chip bg="var(--hf-danger-soft)" fg="var(--hf-danger-text)">Reversed</Chip>
  : <Chip bg="var(--hf-success-soft-strong)" fg="var(--hf-success-text-strong)">Closed</Chip>

export default function ForecourtTab() {
  const canRun = usePermissions(["FUEL_FORECOURT", "FUEL_MANAGE", "FUEL_FORECOURT_MANAGE"])
  const canManage = usePermissions(["FUEL_MANAGE", "FUEL_FORECOURT_MANAGE"])
  const canSupervise = usePermissions(["FUEL_SHIFT_SUPERVISE"])
  const qc = useQueryClient()
  const [dialog, setDialog] = useState<Dialog>(null)
  const [open, setOpen] = useState<string | null>(null)
  const [view, setView] = useState<"shifts" | "pumps">("shifts")
  const [err, setErr] = useState("")

  const shifts = useQuery<Shift[]>({ queryKey: ["fuel-shifts"], queryFn: async () => unwrap(await apiClient.get(`${BASE}/shifts`)) })
  const pumps = useQuery<Pump[]>({ queryKey: ["fuel-pumps"], queryFn: async () => unwrap(await apiClient.get(`${BASE}/pumps`)) })
  const summary = useQuery<ForecourtSummary>({ queryKey: ["fuel-forecourt-summary"], queryFn: async () => unwrap(await apiClient.get(`${BASE}/summary?days=7`)) })
  const refresh = () => { for (const k of ["fuel-shifts", "fuel-pumps", "fuel-forecourt-summary", "fuel-tanks", "fuel-stock-position"]) qc.invalidateQueries({ queryKey: [k] }) }
  const removePump = useMutation({
    mutationFn: (id: string) => apiClient.delete(`${BASE}/pumps/${id}`),
    onSuccess: () => { setErr(""); refresh() }, onError: (e: any) => setErr(msg(e, "Could not remove the pump")),
  })

  const s = summary.data
  const list = shifts.data ?? []
  const pumpList = pumps.data ?? []
  const hasOpen = list.some(x => x.status === "OPEN")

  return (
    <div>
      {s && (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))", gap: 10, marginBottom: 14 }}>
          <Stat label="Last 7 days sold" value={fmtL(s.litres)} />
          <Stat label="Sales (by meters)" value={fmtR(s.sales)} />
          <Stat label="Net takings variance" value={varianceText(s.variance)} warn={Math.abs(s.variance) > 5} />
          <Stat label="Shifts needing a look" value={String(s.flaggedShifts)} warn={s.flaggedShifts > 0} />
          <Stat label="Awaiting supervisor review" value={String(awaitingReview(list))} warn={awaitingReview(list) > 0} />
        </div>
      )}

      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 14, flexWrap: "wrap" }}>
        <button style={view === "shifts" ? btnPrimary : btnGhost} onClick={() => setView("shifts")}>Shifts</button>
        <button style={view === "pumps" ? btnPrimary : btnGhost} onClick={() => setView("pumps")}>Pumps</button>
        <span style={{ flex: 1 }} />
        {view === "shifts" && canRun && <button style={btnPrimary} onClick={() => setDialog({ kind: "open" })}>Open shift</button>}
        {view === "pumps" && canManage && <button style={btnPrimary} onClick={() => setDialog({ kind: "pump" })}>Add pump</button>}
      </div>
      {err && <Err text={err} />}

      {view === "shifts" && (
        list.length === 0 ? <p style={{ color: "var(--hf-text-muted)" }}>{shifts.isLoading ? "Loading…" : "No shifts yet. Add your pumps, then open a shift with the meter readings."}</p> : (
          <div style={{ ...card, overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr><th style={th}>Shift</th><th style={th}>Attendant</th><th style={th}>Opened</th><th style={th}>Litres</th><th style={th}>Expected</th><th style={th}>Variance</th><th style={th}>Status</th><th style={th}></th></tr></thead>
              <tbody>
                {list.map(sh => (
                  <Fragment key={sh.id}>
                    <tr style={{ borderTop: "1px solid var(--hf-border)" }}>
                      <td style={td}><button style={{ ...btnGhost, padding: "2px 6px" }} onClick={() => setOpen(open === sh.id ? null : sh.id)}>{sh.shiftNumber}</button></td>
                      <td style={td}>{sh.attendantName}</td>
                      <td style={td}>{new Date(sh.openedAt).toLocaleString("en-ZA", { dateStyle: "short", timeStyle: "short" })}</td>
                      <td style={td}>{fmtL(sh.litresSold)}</td>
                      <td style={td}>{fmtR(sh.expected)}</td>
                      <td style={{ ...td, color: sh.variance != null && needsReason(sh.variance, sh.tolerance ?? VARIANCE_TOLERANCE) ? "var(--hf-danger-text)" : undefined }}>{sh.status === "CLOSED" ? varianceText(sh.variance) : "–"}{sh.status === "CLOSED" && sh.reviewStatus === "PENDING_REVIEW" && <> <Chip bg="var(--hf-warning-soft)" fg="var(--hf-warning-text)">Needs review</Chip></>}{sh.status === "CLOSED" && sh.reviewStatus === "REVIEWED" && <> <Chip bg="var(--hf-success-soft-strong)" fg="var(--hf-success-text-strong)">Reviewed</Chip></>}</td>
                      <td style={td}>{statusChip(sh.status)}</td>
                      <td style={td}>
                        <span style={{ display: "flex", gap: 6 }}>
                          {sh.status === "OPEN" && canRun && <button style={btnGhost} onClick={() => setDialog({ kind: "close", shift: sh })}>Close shift</button>}
                          {sh.status === "CLOSED" && sh.reviewStatus === "PENDING_REVIEW" && canSupervise && <button style={btnGhost} onClick={() => setDialog({ kind: "review", shift: sh })}>Review</button>}
                          {sh.status === "CLOSED" && canManage && <button style={btnGhost} onClick={() => setDialog({ kind: "reverse", shift: sh })}>Reverse</button>}
                        </span>
                      </td>
                    </tr>
                    {open === sh.id && (
                      <tr><td colSpan={8} style={{ ...td, background: "var(--hf-surface-alt)" }}>
                        <table style={{ width: "100%", borderCollapse: "collapse" }}>
                          <thead><tr><th style={th}>Pump</th><th style={th}>Fuel</th><th style={th}>Opening</th><th style={th}>Closing</th><th style={th}>Test</th><th style={th}>Litres</th><th style={th}>Price</th><th style={th}>Value</th></tr></thead>
                          <tbody>{sh.readings.map(r => (
                            <tr key={r.pumpId}>
                              <td style={td}>{r.pumpName}</td><td style={td}>{r.fuelType}</td>
                              <td style={td}>{r.openingMeter}{r.openingGap !== 0 && <span style={{ color: "var(--hf-warning-text)" }} title="Opening meter differs from the last closing meter"> ({r.openingGap > 0 ? "+" : ""}{r.openingGap} vs last close)</span>}</td>
                              <td style={td}>{r.closingMeter ?? "–"}</td><td style={td}>{r.testLitres}</td><td style={td}>{fmtL(r.litresSold)}</td><td style={td}>{fmtR(r.pricePerLitre)}</td><td style={td}>{fmtR(r.salesValue)}</td>
                            </tr>))}
                          </tbody>
                        </table>
                        {sh.status !== "OPEN" && (
                          <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "8px 0 0" }}>
                            Cash {fmtR(sh.cash)} · Card {fmtR(sh.card)} · Fleet card {fmtR(sh.fleetCard ?? 0)} · Account {fmtR(sh.account)} · Other {fmtR(sh.other)}
                            {sh.adjustments ? ` · Approved refunds, voids and discounts ${fmtR(sh.adjustments)}` : ""}
                            {sh.varianceReason ? ` · Reason: ${sh.varianceReason}` : ""}{sh.reviewNote ? ` · Review: ${sh.reviewNote}` : ""}{sh.reverseReason ? ` · Reversed: ${sh.reverseReason}` : ""}
                          </p>
                        )}
                        <ShiftAdjustmentsPanel shiftId={sh.id} open={sh.status === "OPEN"} />
                      </td></tr>
                    )}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>
        )
      )}

      {view === "pumps" && (
        pumpList.length === 0 ? <p style={{ color: "var(--hf-text-muted)" }}>{pumps.isLoading ? "Loading…" : "No pumps yet. Add each pump with the tank it draws from and its price."}</p> : (
          <div style={{ ...card, overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr><th style={th}>Pump</th><th style={th}>Dispenser / nozzle</th><th style={th}>Tank</th><th style={th}>Fuel</th><th style={th}>Price / L</th><th style={th}>Last meter</th><th style={th}>Status</th><th style={th}></th></tr></thead>
              <tbody>
                {pumpList.map(p => (
                  <tr key={p.id} style={{ borderTop: "1px solid var(--hf-border)" }}>
                    <td style={td}>{p.name}</td><td style={td}>{nozzleLabel(p)}</td><td style={td}>{p.tankName ?? "–"}</td><td style={td}>{p.fuelType ?? "–"}</td>
                    <td style={td}>{fmtR(p.pricePerLitre)}</td><td style={td}>{p.lastMeter ?? "–"}</td>
                    <td style={td}>{p.onShift ? <Chip bg="var(--hf-info-soft)" fg="var(--hf-info-text)">On shift</Chip> : p.active ? "Idle" : "Inactive"}</td>
                    <td style={td}>{canManage && <span style={{ display: "flex", gap: 6 }}>
                      <button style={btnGhost} onClick={() => setDialog({ kind: "pump", pump: p })}>Edit</button>
                      <button style={btnGhost} disabled={removePump.isPending || p.onShift} onClick={() => removePump.mutate(p.id)}>Remove</button>
                    </span>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )
      )}
      {view === "shifts" && !hasOpen && list.length > 0 && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 10 }}>No shift is open.</p>}

      <TankReconciliationPanel />

      {dialog?.kind === "open" && <OpenDialog pumps={pumpList} onDone={refresh} onClose={() => setDialog(null)} />}
      {dialog?.kind === "close" && <CloseDialog shift={dialog.shift} onDone={refresh} onClose={() => setDialog(null)} />}
      {dialog?.kind === "review" && <ReviewDialog shift={dialog.shift} onDone={refresh} onClose={() => setDialog(null)} />}
      {dialog?.kind === "reverse" && <ReverseDialog shift={dialog.shift} onDone={refresh} onClose={() => setDialog(null)} />}
      {dialog?.kind === "pump" && <PumpDialog pump={dialog.pump} onDone={refresh} onClose={() => setDialog(null)} />}
    </div>
  )
}

function Stat({ label, value, warn }: { label: string; value: string; warn?: boolean }) {
  return (
    <div style={{ ...card, padding: 12 }}>
      <p style={{ margin: 0, fontSize: 11.5, color: "var(--hf-text-muted)" }}>{label}</p>
      <p style={{ margin: "4px 0 0", fontSize: 17, fontWeight: 700, color: warn ? "var(--hf-danger-text)" : "var(--hf-text)" }}>{value}</p>
    </div>
  )
}

function OpenDialog({ pumps, onDone, onClose }: { pumps: Pump[]; onDone: () => void; onClose: () => void }) {
  const free = pumps.filter(p => p.active && !p.onShift)
  const [attendant, setAttendant] = useState("")
  const [notes, setNotes] = useState("")
  const [meters, setMeters] = useState<Record<string, string>>({})
  const [picked, setPicked] = useState<Record<string, boolean>>(() => Object.fromEntries(free.map(p => [p.id, true])))
  const [err, setErr] = useState("")
  const chosen = free.filter(p => picked[p.id]).map(p => ({ pumpId: p.id, name: p.name, meter: meters[p.id] ?? (p.lastMeter == null ? "" : String(p.lastMeter)) }))
  const save = useMutation({
    mutationFn: () => apiClient.post(`${BASE}/shifts`, { attendantName: attendant.trim(), notes: notes.trim() || null, readings: chosen.map(c => ({ pumpId: c.pumpId, openingMeter: Number(c.meter) })) }),
    onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not open the shift")),
  })
  return (
    <Modal title="Open shift" onClose={onClose}>
      {free.length === 0 ? <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>There is no free pump. Add a pump, or close the shift that has it.</p> : (
        <>
          <label style={labelStyle}>Attendant *</label>
          <input aria-label="Attendant" value={attendant} onChange={e => setAttendant(e.target.value)} style={inputStyle} />
          <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "12px 0 6px" }}>Read each pump meter and type it in. It starts from the last closing meter.</p>
          {free.map(p => (
            <div key={p.id} style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 6 }}>
              <input type="checkbox" aria-label={`Use ${p.name}`} checked={!!picked[p.id]} onChange={e => setPicked(s => ({ ...s, [p.id]: e.target.checked }))} />
              <span style={{ width: 120, fontSize: 13 }}>{p.name} <span style={{ color: "var(--hf-text-muted)" }}>{p.fuelType}</span></span>
              <input aria-label={`Opening meter ${p.name}`} type="number" min="0" style={inputStyle} disabled={!picked[p.id]}
                value={meters[p.id] ?? (p.lastMeter == null ? "" : String(p.lastMeter))} onChange={e => setMeters(s => ({ ...s, [p.id]: e.target.value }))} />
            </div>
          ))}
          <label style={{ ...labelStyle, marginTop: 8 }}>Notes</label>
          <input aria-label="Notes" value={notes} onChange={e => setNotes(e.target.value)} style={inputStyle} />
        </>
      )}
      {err && <Err text={err} />}
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
        <button style={btnGhost} onClick={onClose}>Cancel</button>
        {free.length > 0 && <button style={btnPrimary} disabled={save.isPending} onClick={() => { const v = validateOpen(attendant, chosen); if (v) return setErr(v); setErr(""); save.mutate() }}>Open shift</button>}
      </div>
    </Modal>
  )
}

function CloseDialog({ shift, onDone, onClose }: { shift: Shift; onDone: () => void; onClose: () => void }) {
  const [closing, setClosing] = useState<Record<string, string>>({})
  const [test, setTest] = useState<Record<string, string>>({})
  const [amt, setAmt] = useState({ cash: "", card: "", fleetCard: "", account: "", other: "" })
  const controls = useQuery<Controls>({ queryKey: ["fuel-settings-controls"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/settings/controls")) })
  const adj = useQuery<ShiftAdjustment[]>({ queryKey: ["fuel-shift-adjustments", shift.id], queryFn: async () => unwrap(await apiClient.get(`${BASE}/shifts/${shift.id}/adjustments`)) })
  const tol = typeof controls.data?.cashVarianceTolerance === "number" ? controls.data.cashVarianceTolerance : VARIANCE_TOLERANCE
  const adjList = Array.isArray(adj.data) ? adj.data : []
  const approvedAdj = approvedAdjustmentTotal(adjList); const pendingAdj = pendingAdjustments(adjList)
  const [reason, setReason] = useState("")
  const [err, setErr] = useState("")
  const rows: CloseRow[] = shift.readings.map(r => ({ pumpId: r.pumpId, name: r.pumpName, opening: r.openingMeter, price: r.pricePerLitre, closing: closing[r.pumpId] ?? "", test: test[r.pumpId] ?? "" }))
  const exp = expectedTotals(rows)
  const variance = Math.round((takings(amt.cash, amt.card, amt.account, amt.other, amt.fleetCard) - (exp.value - approvedAdj)) * 100) / 100
  const save = useMutation({
    mutationFn: () => apiClient.post(`${BASE}/shifts/${shift.id}/close`, {
      cash: Number(amt.cash || 0), card: Number(amt.card || 0), fleetCard: Number(amt.fleetCard || 0), account: Number(amt.account || 0), other: Number(amt.other || 0), varianceReason: reason.trim() || null,
      readings: rows.map(r => ({ pumpId: r.pumpId, closingMeter: Number(r.closing), testLitres: r.test.trim() === "" ? 0 : Number(r.test) })),
    }),
    onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not close the shift")),
  })
  const field = (k: keyof typeof amt, label: string) => (
    <div><label style={labelStyle}>{label}</label><input aria-label={label} type="number" min="0" style={inputStyle} value={amt[k]} onChange={e => setAmt(s => ({ ...s, [k]: e.target.value }))} /></div>
  )
  return (
    <Modal title={`Close ${shift.shiftNumber}`} onClose={onClose}>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", marginTop: 0 }}>Attendant: {shift.attendantName}. Read each pump meter again. Test litres are fuel pumped back into the tank for calibration.</p>
      {shift.readings.map(r => {
        const t = pumpTotals(rows.find(x => x.pumpId === r.pumpId)!)
        return (
          <div key={r.pumpId} style={{ display: "grid", gridTemplateColumns: "110px 1fr 1fr 110px", gap: 8, alignItems: "end", marginBottom: 8 }}>
            <span style={{ fontSize: 13 }}>{r.pumpName}<br /><span style={{ color: "var(--hf-text-muted)", fontSize: 11.5 }}>opened at {r.openingMeter}</span></span>
            <div><label style={labelStyle}>{`Closing meter ${r.pumpName}`}</label><input aria-label={`Closing meter ${r.pumpName}`} type="number" style={inputStyle} value={closing[r.pumpId] ?? ""} onChange={e => setClosing(s => ({ ...s, [r.pumpId]: e.target.value }))} /></div>
            <div><label style={labelStyle}>{`Test litres ${r.pumpName}`}</label><input aria-label={`Test litres ${r.pumpName}`} type="number" min="0" style={inputStyle} value={test[r.pumpId] ?? ""} onChange={e => setTest(s => ({ ...s, [r.pumpId]: e.target.value }))} /></div>
            <span style={{ fontSize: 12.5, textAlign: "right" }}>{t ? `${fmtL(t.litres)} · ${fmtR(t.value)}` : "–"}</span>
          </div>
        )
      })}
      <p style={{ fontSize: 13, fontWeight: 700, margin: "10px 0 6px" }}>Meters say: {fmtL(exp.litres)} = {fmtR(exp.value)}</p>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(120px, 1fr))", gap: 8 }}>
        {field("cash", "Cash")}{field("card", "Card")}{field("fleetCard", "Fleet card")}{field("account", "Account")}{field("other", "Other")}
      </div>
      {approvedAdj > 0 && <p style={{ fontSize: 12.5, margin: "8px 0 0", color: "var(--hf-text-muted)" }}>Approved refunds, voids and discounts of {fmtR(approvedAdj)} are taken off what is expected.</p>}
      {pendingAdj > 0 && <p role="alert" style={{ fontSize: 12.5, margin: "8px 0 0", color: "var(--hf-warning-text)" }}>{pendingAdj} refund, void or discount {pendingAdj === 1 ? "is" : "entries are"} still waiting for a supervisor. The shift cannot be closed until {pendingAdj === 1 ? "it is" : "they are"} decided or withdrawn.</p>}
      <p data-testid="variance" style={{ fontSize: 13, margin: "10px 0 0", color: needsReason(variance, tol) ? "var(--hf-danger-text)" : "var(--hf-text)" }}>
        Takings {fmtR(takings(amt.cash, amt.card, amt.account, amt.other, amt.fleetCard))} · {varianceText(variance)}
      </p>
      {needsReason(variance, tol) && (
        <div><label style={labelStyle}>Reason for the difference *</label><input aria-label="Reason" style={inputStyle} value={reason} onChange={e => setReason(e.target.value)} /></div>
      )}
      {err && <Err text={err} />}
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
        <button style={btnGhost} onClick={onClose}>Cancel</button>
        <button style={btnPrimary} disabled={save.isPending} onClick={() => { const v = validateClose(rows, amt, reason, { tolerance: tol, adjustments: approvedAdj }); if (v) return setErr(v); setErr(""); save.mutate() }}>Close shift</button>
      </div>
    </Modal>
  )
}

function ReviewDialog({ shift, onDone, onClose }: { shift: Shift; onDone: () => void; onClose: () => void }) {
  const [note, setNote] = useState(""); const [err, setErr] = useState("")
  const save = useMutation({
    mutationFn: () => apiClient.post(`${BASE}/shifts/${shift.id}/review`, { reason: note.trim() }),
    onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not save the review")),
  })
  return (
    <Modal title={`Review ${shift.shiftNumber}`} onClose={onClose}>
      <p style={{ fontSize: 13, color: "var(--hf-text-muted)", marginTop: 0 }}>The takings were {varianceText(shift.variance)}. Reason given: {shift.varianceReason ?? "none"}. Reviewing records that a supervisor looked into it. It does not change the takings, stock or any account, and it cannot be done by the person who closed the shift.</p>
      <label style={labelStyle}>Review note *</label>
      <input aria-label="Review note" value={note} onChange={e => setNote(e.target.value)} style={inputStyle} />
      {err && <Err text={err} />}
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
        <button style={btnGhost} onClick={onClose}>Cancel</button>
        <button style={btnPrimary} disabled={save.isPending} onClick={() => { if (note.trim().length < 5) return setErr("Give a review note of at least 5 characters."); setErr(""); save.mutate() }}>Mark reviewed</button>
      </div>
    </Modal>
  )
}

function ReverseDialog({ shift, onDone, onClose }: { shift: Shift; onDone: () => void; onClose: () => void }) {
  const [reason, setReason] = useState(""); const [err, setErr] = useState("")
  const save = useMutation({
    mutationFn: () => apiClient.post(`${BASE}/shifts/${shift.id}/reverse`, { reason: reason.trim() }),
    onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not reverse the shift")),
  })
  return (
    <Modal title={`Reverse ${shift.shiftNumber}`} onClose={onClose}>
      <p style={{ fontSize: 13, color: "var(--hf-text-muted)", marginTop: 0 }}>The litres go back into the tank and the shift stops counting in the summary. The pump meters are not rolled back, so the next shift will show a gap against the last close.</p>
      <label style={labelStyle}>Reason *</label>
      <input aria-label="Reason" value={reason} onChange={e => setReason(e.target.value)} style={inputStyle} />
      {err && <Err text={err} />}
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
        <button style={btnGhost} onClick={onClose}>Cancel</button>
        <button style={btnPrimary} disabled={save.isPending} onClick={() => { if (reason.trim().length < 5) return setErr("Give a reason of at least 5 characters."); setErr(""); save.mutate() }}>Reverse shift</button>
      </div>
    </Modal>
  )
}

function PumpDialog({ pump, onDone, onClose }: { pump?: Pump; onDone: () => void; onClose: () => void }) {
  const [f, setF] = useState({ name: pump?.name ?? "", tankId: pump?.tankId ?? "", price: pump ? String(pump.pricePerLitre) : "", dispenser: pump?.dispenserLabel ?? "", nozzle: pump?.nozzleNumber ? String(pump.nozzleNumber) : "" })
  const [err, setErr] = useState("")
  const tanks = useQuery<{ id: string; name: string; fuelType: string }[]>({ queryKey: ["fuel-tanks"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/tanks")) })
  const save = useMutation({
    mutationFn: () => { const body = { name: f.name.trim(), tankId: f.tankId, pricePerLitre: Number(f.price), active: pump ? pump.active : true, ...nozzleFields(f.dispenser, f.nozzle, !!pump?.dispenserLabel) }
      return pump ? apiClient.put(`${BASE}/pumps/${pump.id}`, body) : apiClient.post(`${BASE}/pumps`, body) },
    onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not save the pump")),
  })
  return (
    <Modal title={pump ? `Edit ${pump.name}` : "Add pump"} onClose={onClose}>
      <label style={labelStyle}>Name *</label>
      <input aria-label="Pump name" value={f.name} onChange={e => setF(s => ({ ...s, name: e.target.value }))} style={inputStyle} />
      <label style={{ ...labelStyle, marginTop: 10 }}>Tank *</label>
      <select aria-label="Tank" value={f.tankId} onChange={e => setF(s => ({ ...s, tankId: e.target.value }))} style={inputStyle}>
        <option value="">Choose a tank</option>
        {(tanks.data ?? []).map(t => <option key={t.id} value={t.id}>{t.name} ({t.fuelType})</option>)}
      </select>
      <label style={{ ...labelStyle, marginTop: 10 }}>Price per litre *</label>
      <input aria-label="Price per litre" type="number" min="0" step="0.01" value={f.price} onChange={e => setF(s => ({ ...s, price: e.target.value }))} style={inputStyle} />
      <label style={{ ...labelStyle, marginTop: 10 }}>Dispenser (optional)</label>
      <input aria-label="Dispenser" placeholder="e.g. Island 1" value={f.dispenser} onChange={e => setF(s => ({ ...s, dispenser: e.target.value }))} style={inputStyle} />
      <label style={{ ...labelStyle, marginTop: 10 }}>Nozzle number (optional)</label>
      <input aria-label="Nozzle number" inputMode="numeric" value={f.nozzle} onChange={e => setF(s => ({ ...s, nozzle: e.target.value }))} style={inputStyle} />
      <div style={{ fontSize: 11, color: "var(--hf-text-muted)", marginTop: 4 }}>Each pump here is one metered nozzle. Naming the dispenser and nozzle number only groups them for reading; shifts and meters work as before.</div>
      {err && <Err text={err} />}
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
        <button style={btnGhost} onClick={onClose}>Cancel</button>
        <button style={btnPrimary} disabled={save.isPending} onClick={() => { const v = validatePump(f.name, f.tankId, f.price) ?? validateNozzle(f.dispenser, f.nozzle); if (v) return setErr(v); setErr(""); save.mutate() }}>Save pump</button>
      </div>
    </Modal>
  )
}
