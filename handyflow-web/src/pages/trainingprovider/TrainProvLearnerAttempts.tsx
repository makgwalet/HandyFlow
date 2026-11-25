// A learner's assessment attempts, shown on the learner page. Mark and decision are shown apart, and an undecided attempt is never shown as a result.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { attemptStateText, attemptTone, markText, type Attempt } from "./attemptRules"
import type { LearnerCohorts } from "./cohortRules"
import { Badge } from "./tpUi"
import { card, fmtDate, muted, unwrap } from "./tpStyle"
import { ATT, LRN } from "./tpApi"

function CohortAttempts({ cohortId, learnerId }: { cohortId: string; learnerId: string }) {
  const { data = [] } = useQuery<Attempt[]>({ queryKey: ["tp-attempts", cohortId], queryFn: async () => unwrap<Attempt[]>(await apiClient.get(ATT, { params: { cohortId } })) })
  const mine = data.filter(a => a.learnerId === learnerId)
  return <>{mine.map(a => (
    <div key={a.id} style={card}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
        <div><strong>{a.instrumentTitle}</strong><div style={muted}>{a.attemptNo} · attempt {a.attemptSeq} · {a.cohortName ?? "Cohort"} · {fmtDate(a.assessedOn)} · {markText(a)}</div></div>
        <Badge text={attemptStateText(a)} tone={attemptTone(a)} />
      </div>
    </div>
  ))}</>
}

export default function TrainProvLearnerAttempts({ learnerId }: { learnerId: string }) {
  const { data } = useQuery<LearnerCohorts>({ queryKey: ["tp-learner-cohorts", learnerId], queryFn: async () => unwrap<LearnerCohorts>(await apiClient.get(`${LRN}/learners/${learnerId}/cohorts`)) })
  const rows = data?.rows ?? []
  if (rows.length === 0) return <div style={muted}>No assessment attempts yet.</div>
  return (
    <div style={{ display: "grid", gap: 8 }}>
      <div style={muted}>Attempts are shown with their decision once one is recorded. An attempt waiting for a decision is not a result.</div>
      {rows.map(r => <CohortAttempts key={r.memberId} cohortId={r.cohortId} learnerId={learnerId} />)}
    </div>
  )
}
