import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { btnGhost, btnPrimary, card, Chip, ErrorLine, inputStyle, labelStyle, Modal } from "../warehousing/whseUi"
import ExecutionModal from "./ExecutionModal"
import { filterVariances, groupBy, litresText, totals, type StatusFilter } from "./varianceOps"
import { DECISION_LABEL, openCount, validateNote, VARIANCE_STATUS_LABEL, varianceLabel, type Variance, type VarianceList } from "./distributionOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const when = (iso: string) => new Date(iso).toLocaleString("en-ZA", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" })

/** Loaded versus delivered differences outside the company's tolerance. A decision is a record only: it does not move stock. */
export default function VariancesTab() {
  const canReview = usePermissions(["FUEL_VARIANCE_REVIEW"])
  const qc = useQueryClient()
  const q = useQuery<VarianceList>({ queryKey: ["fuel-variances"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/variances")) })
  const [dialog, setDialog] = useState<{ v: Variance; kind: "investigate" | "close" } | null>(null)
  const [timelineFor, setTimelineFor] = useState<Variance | null>(null)
  const [status, setStatus] = useState<StatusFilter>("ALL"); const [search, setSearch] = useState(""); const [by, setBy] = useState<"driver" | "vehicle">("driver")
  const [note, setNote] = useState(""); const [decision, setDecision] = useState("EXPLAINED"); const [problem, setProblem] = useState("")
  const done = () => { setDialog(null); setNote(""); setProblem(""); qc.invalidateQueries({ queryKey: ["fuel-variances"] }) }
  const act = useMutation({
    mutationFn: (a: { id: string; kind: "investigate" | "close"; note: string; decision: string }) =>
      apiClient.post(`/api/v1/fuel/variances/${a.id}/${a.kind}`, a.kind === "close" ? { decision: a.decision, note: a.note } : { note: a.note }),
    onSuccess: done,
  })
  if (q.isLoading || !q.data) return <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p>
  const { toleranceSet, variances } = q.data
  const tot = totals(variances), shown = filterVariances(variances, status, search), groups = groupBy(variances, by)
  return (
    <div>
      {!toleranceSet && (
        <div role="status" style={{ ...card, padding: 12, marginBottom: 12, fontSize: 13 }}>
          No variance tolerance is set, so no variances are being raised. Set one under Setup, Settings, Approvals and controls.
        </div>
      )}
      {toleranceSet && <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 10px" }}>{openCount(q.data)} not yet closed. Deliveries from the last 90 days with a recorded loading are checked when this page opens.</p>}
      {toleranceSet && variances.length > 0 && (<>
        <div style={{ ...card, padding: 12, marginBottom: 12, display: "flex", gap: 20, flexWrap: "wrap", fontSize: 13 }} aria-label="Variance totals">
          <div><div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>Not closed</div><strong>{tot.open + tot.investigating}</strong> <span style={{ color: "var(--hf-text-muted)" }}>({tot.open} open, {tot.investigating} investigating)</span></div>
          <div><div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>Short, not closed</div><strong style={{ color: tot.shortLitres > 0 ? "var(--hf-danger-text)" : undefined }}>{litresText(tot.shortLitres)}</strong></div>
          <div><div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>Over, not closed</div><strong>{litresText(tot.overLitres)}</strong></div>
          <div><div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>Written off</div><strong>{litresText(tot.writtenOffLitres)}</strong></div>
        </div>
        <div style={{ ...card, padding: 12, marginBottom: 12 }}>
          <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", marginBottom: 6 }}>
            <strong style={{ fontSize: 13 }}>Where they cluster</strong>
            <select aria-label="Group by" value={by} onChange={e => setBy(e.target.value as "driver" | "vehicle")} style={{ ...inputStyle, width: "auto" }}>
              <option value="driver">By driver</option><option value="vehicle">By vehicle</option>
            </select>
          </div>
          <div style={{ overflowX: "auto" }}><table style={{ width: "100%", fontSize: 12.5, borderCollapse: "collapse" }}>
            <thead><tr style={{ textAlign: "left", color: "var(--hf-text-muted)" }}><th style={{ padding: "4px 6px" }}>{by === "driver" ? "Driver" : "Vehicle"}</th><th>Variances</th><th>Not closed</th><th>Short</th><th>Over</th></tr></thead>
            <tbody>{groups.map(g => (
              <tr key={g.key} style={{ borderTop: "1px solid var(--hf-border)" }}>
                <td style={{ padding: "4px 6px" }}>{g.key}{g.repeats && <> <Chip bg="var(--hf-warning-soft)" fg="var(--hf-warning-text)">Repeats</Chip></>}</td>
                <td>{g.count}</td><td>{g.open}</td><td>{litresText(g.shortLitres)}</td><td>{litresText(g.overLitres)}</td>
              </tr>))}</tbody>
          </table></div>
          <p style={{ margin: "6px 0 0", fontSize: 11.5, color: "var(--hf-text-muted)" }}>A pattern is a reason to look at the loads, not a finding. Open a load's timeline to see what was recorded.</p>
        </div>
        <div style={{ display: "flex", gap: 8, marginBottom: 10, flexWrap: "wrap" }}>
          <select aria-label="Status" value={status} onChange={e => setStatus(e.target.value as StatusFilter)} style={{ ...inputStyle, width: "auto" }}>
            <option value="ALL">All</option><option value="OPEN">Open</option><option value="INVESTIGATING">Investigating</option><option value="CLOSED">Closed</option>
          </select>
          <input aria-label="Search variances" placeholder="Receipt, driver or vehicle" value={search} onChange={e => setSearch(e.target.value)} style={{ ...inputStyle, width: 220 }} />
        </div>
        {shown.length === 0 && <div style={{ ...card, padding: 16, fontSize: 13 }}>No variances match.</div>}
      </>)}
      {toleranceSet && variances.length === 0 && <div style={{ ...card, padding: 16, fontSize: 13 }}>No loaded-versus-delivered variances outside tolerance.</div>}
      {shown.map(v => (
        <div key={v.id} style={{ ...card, padding: 12, marginBottom: 8 }}>
          <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
            <strong style={{ fontSize: 13 }}>{v.receiptNumber ?? "Delivery"}</strong>
            <Chip bg={v.status === "CLOSED" ? "var(--hf-surface-alt)" : "var(--hf-warning-soft)"} fg={v.status === "CLOSED" ? "var(--hf-text-muted)" : "var(--hf-warning-text)"}>{VARIANCE_STATUS_LABEL[v.status]}{v.decision ? `: ${DECISION_LABEL[v.decision]}` : ""}</Chip>
            <span style={{ fontSize: 13, color: v.varianceLitres < 0 ? "var(--hf-danger-text)" : undefined }}>{varianceLabel(v)} ({v.variancePct}%)</span>
            <span style={{ flex: 1 }} />
            <button style={btnGhost} onClick={() => setTimelineFor(v)} aria-label={`Timeline for ${v.receiptNumber ?? v.id}`}>Timeline</button>
            {canReview && v.status === "OPEN" && <button style={btnGhost} onClick={() => { setDialog({ v, kind: "investigate" }); setNote("") }} aria-label={`Investigate ${v.receiptNumber ?? v.id}`}>Investigate</button>}
            {canReview && v.status !== "CLOSED" && <button style={btnPrimary} onClick={() => { setDialog({ v, kind: "close" }); setNote("") }} aria-label={`Close ${v.receiptNumber ?? v.id}`}>Close</button>}
          </div>
          <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
            Loaded {v.loadedLitres.toLocaleString("en-ZA")} L, delivered {v.deliveredLitres.toLocaleString("en-ZA")} L, tolerance {v.tolerancePct}%
            {v.driverName ? ` · ${v.driverName}` : ""}{v.vehicleReg ? ` · ${v.vehicleReg}` : ""} · {when(v.detectedAt)}
          </div>
          {v.note && <div style={{ fontSize: 12 }}>{v.note}</div>}
        </div>
      ))}
      {timelineFor && <ExecutionModal deliveryId={timelineFor.deliveryId} label={timelineFor.receiptNumber ?? "delivery"} onClose={() => setTimelineFor(null)} />}
      {dialog && (
        <Modal title={dialog.kind === "close" ? "Close variance" : "Investigate variance"} onClose={() => setDialog(null)}>
          {dialog.kind === "close" && (
            <>
              <label style={labelStyle} htmlFor="var-dec">Decision</label>
              <select id="var-dec" style={{ ...inputStyle, width: "100%", marginBottom: 10 }} value={decision} onChange={e => setDecision(e.target.value)}>
                <option value="EXPLAINED">Explained</option><option value="WRITTEN_OFF">Written off</option>
              </select>
              <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 8px" }}>This records the decision. It does not change any stock.</p>
            </>
          )}
          <label style={labelStyle} htmlFor="var-note">Note *</label>
          <textarea id="var-note" style={{ ...inputStyle, width: "100%", minHeight: 70 }} maxLength={300} value={note} onChange={e => { setNote(e.target.value); setProblem("") }} />
          {problem && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{problem}</p>}
          <ErrorLine error={act.error} />
          <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
            <button style={btnGhost} onClick={() => setDialog(null)}>Back</button>
            <button style={btnPrimary} disabled={act.isPending} onClick={() => { const p = validateNote(note); if (p) return setProblem(p); act.mutate({ id: dialog.v.id, kind: dialog.kind, note: note.trim(), decision }) }}>{dialog.kind === "close" ? "Close variance" : "Start investigating"}</button>
          </div>
        </Modal>
      )}
    </div>
  )
}
