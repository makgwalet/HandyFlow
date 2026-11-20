// Conflict checks: those waiting for a second person to review, and the record of every check the firm has run.
// Running a check happens when a matter is opened (New matter) or from "Run a check" here.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, Empty, ErrorLine, Input, Pager, Pick, Pill, SectionTitle, card, day, errorText, muted, pageOf, td, th } from "./kit"
import { BASE, useLookups } from "./lookups"
import ConflictPanel, { HitList } from "./ConflictPanel"
import { CONFLICT_STATUS, REVIEW_OUTCOMES, decisionProblem, type ConflictCheck } from "./legal.logic"

const FILTERS: (readonly [string, string])[] = [["PENDING_REVIEW", "Needs review"], ["CLEARED_AFTER_REVIEW", "Cleared after review"], ["PROCEED_WITH_CONSENT", "Proceed with consent"], ["DECLINED", "Declined"], ["CLEAR", "Clear"], ["", "All"]]

export default function ConflictsTab() {
  const lookups = useLookups()
  const isAdmin = usePermission("LEGALPRACTICE_ADMIN")
  const canReview = usePermission("LEGALPRACTICE_CONFLICT_REVIEW") || isAdmin
  const [status, setStatus] = useState("PENDING_REVIEW"), [page, setPage] = useState(0)
  const [deciding, setDeciding] = useState<ConflictCheck | null>(null), [running, setRunning] = useState(false)
  const list = useQuery({ queryKey: ["lp-conflict-checks", status, page], queryFn: async () => pageOf<ConflictCheck>(await apiClient.get(`${BASE}/conflict-checks`, { params: { status: status || undefined, page, size: 15 } })) })
  const rows = list.data?.content ?? []
  return (
    <div>
      <SectionTitle right={<Btn onClick={() => setRunning(true)}>Run a check</Btn>}>Conflict checks</SectionTitle>
      <div style={{ ...muted, marginBottom: 10 }}>Before a matter is opened, the client and everyone else involved are searched against the firm's clients and earlier matters. A check that finds something needs a second person to decide.</div>
      <div style={{ maxWidth: 260, marginBottom: 8 }}><Pick label="Show" value={status} onChange={v => { setStatus(v); setPage(0) }} options={FILTERS} /></div>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {list.isError ? <div style={{ ...muted, padding: 14 }}>Could not load the checks.</div> : list.isLoading ? <div style={{ ...muted, padding: 14 }}>Loading…</div> : rows.length === 0 ? <Empty>{status === "PENDING_REVIEW" ? "Nothing is waiting for review." : "No checks to show."}</Empty> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Run</th><th style={th}>Client</th><th style={th}>Searched</th><th style={th}>Result</th><th style={th} /></tr></thead>
            <tbody>{rows.map(c => {
              const st = CONFLICT_STATUS[c.status] ?? { label: c.status, tone: "neutral" as const }
              return (
                <tr key={c.id}>
                  <td style={td}>{day(c.runAt)}<div style={muted}>{c.runByName ?? ""}</div></td>
                  <td style={td}>{c.clientName ?? lookups.clientName(c.clientId)}{c.matterId && <div style={muted}>Matter opened</div>}</td>
                  <td style={td}>{c.subjects.map(s => s.name).join(", ")}</td>
                  <td style={td}><Pill tone={st.tone}>{st.label}</Pill>
                    <HitList check={c} />
                    {c.decidedByName && <div style={muted}>{c.decidedByName}{c.decidedAt ? ` · ${day(c.decidedAt)}` : ""}{c.selfReviewed ? " · reviewed by the person who ran it" : ""}</div>}
                    {c.decisionReason && <div style={muted}>{c.decisionReason}</div>}
                    {c.usable && c.validUntil && <div style={muted}>Can open a matter until {day(c.validUntil)}</div>}
                  </td>
                  <td style={{ ...td, textAlign: "right" }}>{canReview && c.status === "PENDING_REVIEW" && <Btn small kind="primary" onClick={() => setDeciding(c)}>Review</Btn>}</td>
                </tr>)})}</tbody>
          </table>)}
      </div>
      <Pager page={page} pages={list.data?.totalPages ?? 0} onPage={setPage} />
      {deciding && <DecideDialog check={deciding} onClose={() => setDeciding(null)} />}
      {running && <RunDialog onClose={() => setRunning(false)} />}
    </div>
  )
}

function RunDialog({ onClose }: { onClose: () => void }) {
  const lookups = useLookups(), [clientId, setClientId] = useState("")
  return (
    <Dialog title="Run a conflict check" onClose={onClose} width={720} footer={<Btn onClick={onClose}>Close</Btn>}>
      <Pick label="Client" value={clientId} onChange={setClientId} options={lookups.clients.map(c => [c.id, c.name] as const)} blank="Choose a client" />
      <ConflictPanel clientId={clientId} />
    </Dialog>
  )
}

function DecideDialog({ check, onClose }: { check: ConflictCheck; onClose: () => void }) {
  const qc = useQueryClient(), [outcome, setOutcome] = useState(""), [reason, setReason] = useState(""), [err, setErr] = useState("")
  const problem = decisionProblem(outcome, reason)
  const save = useMutation({
    mutationFn: () => apiClient.post(`${BASE}/conflict-checks/${check.id}/decide`, { outcome, reason: reason.trim() }),
    onSuccess: () => { toast.success("Decision recorded"); qc.invalidateQueries({ queryKey: ["lp-conflict-checks"] }); onClose() },
    onError: async e => setErr(await errorText(e, "Could not record the decision")),
  })
  return (
    <Dialog title={`Review: ${check.clientName ?? "conflict check"}`} onClose={onClose} width={640}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Record decision"}</Btn></>}>
      <div style={muted}>Searched: {check.subjects.map(s => `${s.name} (${s.role.toLowerCase().replace("_", " ")})`).join(", ")}</div>
      <HitList check={check} />
      <div style={{ marginTop: 10 }}><Pick label="Outcome" value={outcome} onChange={setOutcome} options={REVIEW_OUTCOMES} blank="Choose an outcome" /></div>
      <Input label="Reason" value={reason} onChange={setReason} hint="At least 10 characters. For consent, say where the written consent is kept. Kept permanently." />
      {problem && reason.trim() && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
