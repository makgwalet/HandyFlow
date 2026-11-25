// The provider's own documents for one enrolment: issue, hand over, void, view and print. Loads only when opened.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import {
  DOCUMENT_NOTE, DOC_TYPES, METHODS, docTone, documentLine, emptyHandOver, handOverBody, handOverProblem, issuableTypes, label, standingWarning, voidProblem,
  type LearnerDocument,
} from "./documentRules"
import type { Completion } from "./completionRules"
import { Badge, Notice } from "./tpUi"
import { card, errText, ghost, input, muted, solid, todayIso, unwrap } from "./tpStyle"
import { CMP, DOC } from "./tpApi"
import TrainProvDocumentView from "./TrainProvDocumentView"

export default function TrainProvDocumentPanel({ enrolmentId }: { enrolmentId: string }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["TRAININGPROVIDER_MANAGE", "TRAININGPROVIDER_ADMIN"])
  const [open, setOpen] = useState(false)
  const [mode, setMode] = useState<{ kind: "handover" | "void" | "view"; id: string } | null>(null)
  const [hand, setHand] = useState(emptyHandOver(todayIso()))
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const { data: docs = [] } = useQuery<LearnerDocument[]>({
    queryKey: ["tp-documents", enrolmentId], enabled: open,
    queryFn: async () => unwrap<LearnerDocument[]>(await apiClient.get(DOC, { params: { enrolmentId } })),
  })
  const { data: outcomes = [] } = useQuery<Completion[]>({
    queryKey: ["tp-completions", enrolmentId], enabled: open,
    queryFn: async () => unwrap<Completion[]>(await apiClient.get(CMP, { params: { enrolmentId } })),
  })
  if (!open) return <div style={{ marginTop: 8 }}><button style={ghost} onClick={() => setOpen(true)}>Documents</button></div>
  const completedStands = outcomes.some(o => o.status === "RECORDED" && o.outcome === "COMPLETED")
  const issuable = issuableTypes(docs, completedStands)
  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true); setError("")
    try { await fn(); setMode(null); setReason(""); setHand(emptyHandOver(todayIso())); void qc.invalidateQueries({ queryKey: ["tp-documents"] }) } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  const handOver = (d: LearnerDocument) => {
    const p = handOverProblem(hand, d.issuedOn, todayIso()); if (p) { setError(p); return }
    void run(() => apiClient.post(`${DOC}/${d.id}/hand-over`, handOverBody(hand)))
  }
  const voidIt = (d: LearnerDocument) => {
    const p = voidProblem(reason); if (p) { setError(p); return }
    void run(() => apiClient.post(`${DOC}/${d.id}/void`, { reason: reason.trim() }))
  }
  return (
    <div style={{ ...card, marginTop: 8, display: "grid", gap: 8 }}>
      <div style={muted}>{DOCUMENT_NOTE}</div>
      {docs.length === 0 && <div style={muted}>No documents issued.</div>}
      {docs.map(d => {
        const warn = standingWarning(d)
        return (
          <div key={d.id} style={{ display: "grid", gap: 4 }}>
            <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
              <div><strong>{d.documentNo}</strong> <span style={muted}>{label(DOC_TYPES, d.docType)}</span></div>
              <Badge text={d.status === "VOID" ? "Void" : "Issued"} tone={docTone(d)} />
            </div>
            <div style={muted}>{documentLine(d)}</div>
            {warn && <Notice text={warn} />}
            <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
              <button style={ghost} onClick={() => setMode(mode?.id === d.id && mode.kind === "view" ? null : { kind: "view", id: d.id })}>View and print</button>
              {canManage && d.status === "ISSUED" && !d.handedOverOn && <button style={ghost} onClick={() => { setMode({ kind: "handover", id: d.id }); setError("") }}>Record hand-over</button>}
              {canManage && d.status === "ISSUED" && <button style={ghost} onClick={() => { setMode({ kind: "void", id: d.id }); setError("") }}>Void this document</button>}
            </div>
            {mode?.id === d.id && mode.kind === "view" && <TrainProvDocumentView doc={d} onClose={() => setMode(null)} />}
            {mode?.id === d.id && mode.kind === "handover" && (
              <div style={{ display: "grid", gap: 6 }}>
                <input aria-label="Day handed over" type="date" style={input} value={hand.handedOverOn} onChange={e => setHand({ ...hand, handedOverOn: e.target.value })} />
                <input aria-label="Handed over to" style={input} placeholder="Who received it" value={hand.handedOverTo} onChange={e => setHand({ ...hand, handedOverTo: e.target.value })} />
                <select aria-label="Hand-over method" style={input} value={hand.method} onChange={e => setHand({ ...hand, method: e.target.value })}><option value="">How was it handed over?</option>{Object.entries(METHODS).map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select>
                <Notice text={error} />
                <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={() => handOver(d)}>Record hand-over</button><button style={ghost} onClick={() => setMode(null)}>Cancel</button></div>
              </div>
            )}
            {mode?.id === d.id && mode.kind === "void" && (
              <div style={{ display: "grid", gap: 6 }}>
                <input aria-label="Reason for voiding" style={input} placeholder="Why is this document being voided?" value={reason} onChange={e => setReason(e.target.value)} />
                <Notice text={error} />
                <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={() => voidIt(d)}>Void the document</button><button style={ghost} onClick={() => setMode(null)}>Cancel</button></div>
              </div>
            )}
          </div>
        )
      })}
      {canManage && issuable.map(t => (
        <div key={t}><button style={solid} disabled={busy} onClick={() => void run(() => apiClient.post(DOC, { enrolmentId, docType: t }))}>Issue a {label(DOC_TYPES, t).toLowerCase()}</button></div>
      ))}
      {canManage && !completedStands && !issuable.includes("COMPLETION_LETTER") && !docs.some(d => d.docType === "COMPLETION_LETTER" && d.status === "ISSUED") && (
        <div style={muted}>A completion letter can be issued once a completed outcome is recorded.</div>
      )}
      {!mode && <Notice text={error} />}
      <div><button style={ghost} onClick={() => setOpen(false)}>Hide</button></div>
    </div>
  )
}
