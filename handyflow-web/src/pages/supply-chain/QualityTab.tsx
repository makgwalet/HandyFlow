// src/pages/supply-chain/QualityTab.tsx — goods-received quality inspections and the stock held in quarantine.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { ShieldAlert } from "lucide-react"
import { TH, TD, Modal, ModalFooter, ErrBox, Spinner, EmptyState, filterPill, fmtDate } from "./scm.shared"
import { asList, errorText, quarantineProblem } from "./scmOps"
import { StatCard, Panel, Pill, grid, type Tone } from "./scmUi"
import { StartInspection, RecordInspection, type InspectionDto } from "./InspectionRecorder"

type Inspection = InspectionDto & { result: string | null; inspectedByName: string | null; createdAt: string; returnId: string | null }
interface Hold { id: string; itemName: string; qty: number; released: number; returned: number; writtenOff: number; open: number; status: string; defectCode: string | null; createdAt: string }
const BASE = "/api/v1/supply-chain"
const RESULT_TONE: Record<string, Tone> = { PASSED: "ok", PASS: "ok", FAILED: "bad", FAIL: "bad", PARTIAL: "warn" }

export function QualityTab() {
  const qc = useQueryClient()
  const canManage = usePermissions(["SCM_QUALITY_MANAGE", "SCM_ADMIN"])
  const [view, setView] = useState<"quarantine" | "inspections">("quarantine")
  const [act, setAct] = useState<{ hold: Hold; kind: "release" | "write-off" } | null>(null)
  const [starting, setStarting] = useState(false)
  const [recording, setRecording] = useState<InspectionDto | null>(null)
  const [qty, setQty] = useState(""); const [reason, setReason] = useState(""); const [err, setErr] = useState("")

  const holds = useQuery({ queryKey: ["scm-quarantine"], queryFn: () => apiClient.get(`${BASE}/quarantine`, { params: { status: "OPEN" } }).then(asList<Hold>) })
  const insp = useQuery({ queryKey: ["scm-inspections"], enabled: view === "inspections", queryFn: () => apiClient.get(`${BASE}/quality-inspections`).then(asList<Inspection>) })
  const run = useMutation({
    mutationFn: () => apiClient.post(`${BASE}/quarantine/${act!.hold.id}/${act!.kind}`, { qty: Number(qty), reason }),
    onSuccess: () => { setAct(null); setQty(""); setReason(""); setErr(""); qc.invalidateQueries({ queryKey: ["scm-quarantine"] }) },
    onError: e => setErr(errorText(e)),
  })
  const submit = () => {
    if (!act) return
    const p = quarantineProblem(act.kind, qty, act.hold.open, reason); if (p) { setErr(p); return }
    setErr(""); run.mutate()
  }

  const h = holds.data ?? []
  const heldUnits = h.reduce((s, x) => s + x.open, 0)
  const inspections = insp.data ?? []

  return (
    <div style={{ padding: 20, display: "grid", gap: 14 }}>
      <div style={grid(180)}>
        <StatCard label="Open holds" value={h.length} tone={h.length ? "warn" : "neutral"} icon={ShieldAlert} />
        <StatCard label="Units held" value={heldUnits} />
        <StatCard label="Inspections" value={view === "inspections" ? inspections.length : "—"} sub={view === "inspections" ? `${inspections.filter(i => i.status === "OPEN").length} open` : "open the tab to load"} />
      </div>
      <div style={{ display: "flex", gap: 8 }}>
        <button style={filterPill(view === "quarantine")} onClick={() => setView("quarantine")}>Quarantine</button>
        <button style={filterPill(view === "inspections")} onClick={() => setView("inspections")}>Inspections</button>
        <div style={{ flex: 1 }} />
        {canManage && <button style={filterPill(true)} onClick={() => setStarting(true)}>Start inspection</button>}
      </div>

      {view === "quarantine" && (holds.isLoading ? <Spinner /> : holds.isError ? <ErrBox msg={errorText(holds.error, "Could not load quarantine")} /> : h.length === 0 ? (
        <EmptyState icon={ShieldAlert} title="Nothing in quarantine" sub="Stock that fails inspection is held here until it is released, returned or scrapped." />
      ) : (
        <div style={{ overflowX: "auto", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12 }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={TH}>Item</th><th style={TH}>Defect</th><th style={TH}>Held</th><th style={TH}>Released</th><th style={TH}>Returned</th><th style={TH}>Scrapped</th><th style={TH}>Since</th><th style={TH} /></tr></thead>
            <tbody>{h.map(x => (
              <tr key={x.id} style={{ borderTop: "1px solid var(--hf-border)" }}>
                <td style={TD}><strong>{x.itemName}</strong></td><td style={TD}>{x.defectCode ?? "—"}</td>
                <td style={TD}><Pill tone="warn">{x.open} of {x.qty}</Pill></td>
                <td style={TD}>{x.released}</td><td style={TD}>{x.returned}</td><td style={TD}>{x.writtenOff}</td><td style={TD}>{fmtDate(x.createdAt)}</td>
                <td style={{ ...TD, textAlign: "right", whiteSpace: "nowrap" }}>{canManage && <>
                  <button style={filterPill(false)} onClick={() => { setAct({ hold: x, kind: "release" }); setQty(String(x.open)); setReason(""); setErr("") }}>Release</button>{" "}
                  <button style={filterPill(false)} onClick={() => { setAct({ hold: x, kind: "write-off" }); setQty(String(x.open)); setReason(""); setErr("") }}>Scrap</button></>}</td>
              </tr>))}</tbody>
          </table>
        </div>
      ))}

      {view === "inspections" && (insp.isLoading ? <Spinner /> : insp.isError ? <ErrBox msg={errorText(insp.error, "Could not load inspections")} /> : inspections.length === 0 ? (
        <EmptyState icon={ShieldAlert} title="No inspections" sub="Inspections of received goods appear here." />
      ) : (
        <div style={{ display: "grid", gap: 10 }}>{inspections.map(i => {
          const passed = i.lines.reduce((s, l) => s + Number(l.qtyPassed), 0), failed = i.lines.reduce((s, l) => s + Number(l.qtyFailed), 0)
          return (
            <Panel key={i.id} title={i.inspectionNumber} right={<Pill tone={i.result ? RESULT_TONE[i.result] ?? "neutral" : "info"}>{i.result ?? i.status}</Pill>}>
              <div style={{ display: "flex", gap: 18, flexWrap: "wrap", fontSize: 13, color: "var(--hf-text-secondary)" }}>
                <span>{fmtDate(i.createdAt)}</span><span>By {i.inspectedByName ?? "—"}</span><span>Passed {passed}</span><span>Failed {failed}</span>{i.returnId && <span>Return raised</span>}
                {canManage && i.status === "OPEN" && <button style={filterPill(true)} onClick={() => setRecording(i)}>Record results</button>}
              </div>
            </Panel>)
        })}</div>
      ))}

      {starting && <StartInspection onClose={() => setStarting(false)} onStarted={i => { setStarting(false); setView("inspections"); setRecording(i) }} />}
      {recording && <RecordInspection inspection={recording} onClose={() => setRecording(null)} />}

      {act && (
        <Modal title={`${act.kind === "release" ? "Release" : "Scrap"} — ${act.hold.itemName}`} onClose={() => setAct(null)}>
          <label style={{ fontSize: 12, fontWeight: 600 }}>Quantity (up to {act.hold.open})<input aria-label="Quantity" inputMode="decimal" value={qty} onChange={e => setQty(e.target.value)} style={{ width: "100%", padding: 9, marginTop: 5, border: "1.5px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)" }} /></label>
          <label style={{ display: "block", fontSize: 12, fontWeight: 600, marginTop: 10 }}>{act.kind === "release" ? "Note (optional)" : "Reason (required)"}<textarea aria-label="Reason" rows={2} value={reason} onChange={e => setReason(e.target.value)} style={{ width: "100%", marginTop: 5, padding: 9, border: "1.5px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)" }} /></label>
          {err && <ErrBox msg={err} />}
          <ModalFooter onCancel={() => setAct(null)} onConfirm={submit} label={act.kind === "release" ? "Release to stock" : "Scrap stock"} loading={run.isPending} />
        </Modal>
      )}
    </div>
  )
}
