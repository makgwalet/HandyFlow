// Decisions waiting for moderation, and moderation queries waiting for an answer.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { decisionTone, label, DECISIONS, type Moderation, type PendingRow } from "./attemptRules"
import { Badge } from "./tpUi"
import { card, fmtDate, muted, unwrap } from "./tpStyle"
import { MOD } from "./tpApi"
import TrainProvAttemptDetail from "./TrainProvAttemptDetail"

export default function TrainProvModerationQueue() {
  const [openId, setOpenId] = useState<string | null>(null)
  const pending = useQuery<PendingRow[]>({ queryKey: ["tp-moderation-pending"], queryFn: async () => unwrap<PendingRow[]>(await apiClient.get(`${MOD}/assessment-moderations/pending`)) })
  const queries = useQuery<Moderation[]>({
    queryKey: ["tp-moderation-queries"],
    queryFn: async () => unwrap<Moderation[]>(await apiClient.get(`${MOD}/assessment-moderations`, { params: { openQueries: true } })),
  })
  if (openId) return <TrainProvAttemptDetail attemptId={openId} onBack={() => setOpenId(null)} />
  const p = pending.data ?? []
  const q = queries.data ?? []
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <strong>Queries waiting for an answer</strong>
      {queries.isLoading ? <div style={muted}>Loading…</div> : q.length === 0 ? <div style={muted}>No open queries.</div> : q.map(m => (
        <button key={m.id} onClick={() => setOpenId(m.attemptId)} style={{ ...card, textAlign: "left", background: "transparent", cursor: "pointer", color: "inherit" }}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
            <div><strong>{m.learnerName ?? "Learner"} · {m.instrumentTitle}</strong><div style={muted}>{m.moderationNo} · queried {fmtDate(m.moderatedOn)} by {m.moderatorName ?? "moderator"} · {m.basis}</div></div>
            <Badge text="Query open" tone="warn" />
          </div>
        </button>
      ))}
      <strong>Decisions waiting for moderation</strong>
      {pending.isLoading ? <div style={muted}>Loading…</div> : p.length === 0 ? <div style={muted}>Nothing is waiting for moderation.</div> : p.map(r => (
        <button key={r.attemptId} onClick={() => setOpenId(r.attemptId)} style={{ ...card, textAlign: "left", background: "transparent", cursor: "pointer", color: "inherit" }}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
            <div><strong>{r.learnerName ?? "Learner"} · {r.instrumentTitle}</strong><div style={muted}>{r.attemptNo} · {r.cohortName ?? "Cohort"} · assessor {r.assessorName ?? "—"} · decided {fmtDate(r.decidedOn)}</div></div>
            <Badge text={label(DECISIONS, r.decision)} tone={decisionTone(r.decision)} />
          </div>
        </button>
      ))}
    </div>
  )
}
