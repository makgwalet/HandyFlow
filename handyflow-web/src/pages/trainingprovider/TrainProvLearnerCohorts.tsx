// A learner's cohorts and attendance counts, shown on the learner page. Counts of what was recorded: never a result or a completion.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { COHORT_STATUS, cohortTone, label, learnerCohortLine, type LearnerCohorts } from "./cohortRules"
import { Badge } from "./tpUi"
import { card, muted, unwrap } from "./tpStyle"
import { LRN } from "./tpApi"

export default function TrainProvLearnerCohortsSection({ learnerId }: { learnerId: string }) {
  const { data, isLoading } = useQuery<LearnerCohorts>({ queryKey: ["tp-learner-cohorts", learnerId], queryFn: async () => unwrap<LearnerCohorts>(await apiClient.get(`${LRN}/learners/${learnerId}/cohorts`)) })
  if (isLoading || !data) return <div style={muted}>Loading…</div>
  return (
    <div style={{ display: "grid", gap: 8 }}>
      <div style={muted}>{data.note}</div>
      {data.rows.length === 0 ? <div style={muted}>Not in any cohort yet.</div> : data.rows.map(r => (
        <div key={r.memberId} style={card}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
            <div><strong>{r.cohortName}</strong><div style={muted}>{r.cohortNo} · {r.programmeTitle} · {r.enrolmentNo} · from {r.startDate}</div></div>
            <Badge text={label(COHORT_STATUS, r.cohortStatus)} tone={cohortTone(r.cohortStatus)} />
          </div>
          <div style={muted}>{learnerCohortLine(r)}</div>
        </div>
      ))}
    </div>
  )
}
