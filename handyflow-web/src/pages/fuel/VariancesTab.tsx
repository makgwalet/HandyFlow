import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { btnGhost, btnPrimary, card, Chip, ErrorLine, inputStyle, labelStyle, Modal } from "../warehousing/whseUi"
import { DECISION_LABEL, openCount, validateNote, VARIANCE_STATUS_LABEL, varianceLabel, type Variance, type VarianceList } from "./distributionOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const when = (iso: string) => new Date(iso).toLocaleString("en-ZA", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" })

/** Loaded versus delivered differences outside the company's tolerance. A decision is a record only: it does not move stock. */
export default function VariancesTab() {
  const canReview = usePermissions(["FUEL_VARIANCE_REVIEW"])
  const qc = useQueryClient()
  const q = useQuery<VarianceList>({ queryKey: ["fuel-variances"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/variances")) })
  const [dialog, setDialog] = useState<{ v: Variance; kind: "investigate" | "close" } | null>(null)
  const [note, setNote] = useState(""); const [decision, setDecision] = useState("EXPLAINED"); const [problem, setProblem] = useState("")
  const done = () => { setDialog(null); setNote(""); setProblem(""); qc.invalidateQueries({ queryKey: ["fuel-variances"] }) }
  const act = useMutation({
    mutationFn: (a: { id: string; kind: "investigate" | "close"; note: string; decision: string }) =>
      apiClient.post(`/api/v1/fuel/variances/${a.id}/${a.kind}`, a.kind === "close" ? { decision: a.decision, note: a.note } : { note: a.note }),
    onSuccess: done,
  })
  if (q.isLoading || !q.data) return <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p>
  const { toleranceSet, variances } = q.data
  return (
    <div>
      {!toleranceSet && (
        <div role="status" style={{ ...card, padding: 12, marginBottom: 12, fontSize: 13 }}>
          No variance tolerance is set, so no variances are being raised. Set one under Setup, Settings, Approvals and controls.
        </div>
      )}
      {toleranceSet && <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 10px" }}>{openCount(q.data)} not yet closed. Deliveries from the last 90 days with a recorded loading are checked when this page opens.</p>}
      {toleranceSet && variances.length === 0 && <div style={{ ...card, padding: 16, fontSize: 13 }}>No loaded-versus-delivered variances outside tolerance.</div>}
      {variances.map(v => (
        <div key={v.id} style={{ ...card, padding: 12, marginBottom: 8 }}>
          <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
            <strong style={{ fontSize: 13 }}>{v.receiptNumber ?? "Delivery"}</strong>
            <Chip bg={v.status === "CLOSED" ? "var(--hf-surface-alt)" : "var(--hf-warning-soft)"} fg={v.status === "CLOSED" ? "var(--hf-text-muted)" : "var(--hf-warning-text)"}>{VARIANCE_STATUS_LABEL[v.status]}{v.decision ? `: ${DECISION_LABEL[v.decision]}` : ""}</Chip>
            <span style={{ fontSize: 13, color: v.varianceLitres < 0 ? "var(--hf-danger-text)" : undefined }}>{varianceLabel(v)} ({v.variancePct}%)</span>
            <span style={{ flex: 1 }} />
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
