// src/pages/trainingprovider/TrainProvCohortReadiness.tsx
//
// How the cohort's site register compares with the resources its programme version lists as needed. A prompt to check, never a finding.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { READINESS_STATUS, readinessTone, type CohortReadiness } from "./programmeNeedsRules"
import { Badge } from "./tpUi"
import { card, ghost, muted, unwrap } from "./tpStyle"
import { LRN } from "./tpApi"

export default function TrainProvCohortReadiness({ cohortId }: { cohortId: string }) {
  const [open, setOpen] = useState(false)
  const { data, isLoading } = useQuery<CohortReadiness>({
    queryKey: ["tp-cohort-readiness", cohortId], enabled: open,
    queryFn: async () => unwrap<CohortReadiness>(await apiClient.get(`${LRN}/cohorts/${cohortId}/resource-readiness`)),
  })
  if (!open) return <div><button style={ghost} onClick={() => setOpen(true)}>Resources compared with the programme</button></div>
  return (
    <div style={{ ...card, display: "grid", gap: 6 }}>
      {isLoading || !data ? <div style={muted}>Loading…</div> : (
        <>
          <div style={muted}>{data.note}</div>
          <div style={muted}>{data.siteName} · {data.learners} learners</div>
          {data.rows.length === 0 ? <div style={muted}>The programme version lists no resource needs.</div> : data.rows.map(r => (
            <div key={r.needId} style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap", fontSize: 13 }}>
              <div><strong>{r.description}</strong><div style={muted}>{r.note}</div></div>
              <Badge text={READINESS_STATUS[r.status] ?? r.status} tone={readinessTone(r.status)} />
            </div>
          ))}
        </>
      )}
      <button style={{ ...ghost, justifySelf: "start" }} onClick={() => setOpen(false)}>Hide</button>
    </div>
  )
}
