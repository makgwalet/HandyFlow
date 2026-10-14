// src/pages/debtcollection/StrategyNote.tsx
// One short block on a case: which strategy applies, what comes next, and what it raised before (finance F23).
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { ACTION_LABEL } from "./strategies.logic"

const unwrap = (r: any) => r.data?.data ?? r.data

export default function StrategyNote({ caseId }: { caseId: string }) {
  const { data } = useQuery<any>({ queryKey: ["dc-case-strategy", caseId], queryFn: async () => unwrap(await apiClient.get(`/api/v1/debtcollection/strategies/cases/${caseId}`)) })
  if (!data) return null
  return (
    <div style={{ padding: "9px 12px", background: "var(--hf-surface-muted)", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 12 }}>
      <b>Collection strategy</b>: {data.strategyName ?? "none applies"}
      {data.strategyName && <> · {data.daysOverdue} days overdue
        {data.nextAction ? ` · next: ${ACTION_LABEL[data.nextAction]} at day ${data.nextStepDay}` : " · all steps done"}</>}
      {data.reason && <div style={{ color: "var(--hf-text-secondary)" }}>{data.reason}</div>}
      {data.history.length > 0 && (
        <ul style={{ margin: "4px 0 0", paddingLeft: 18 }}>
          {data.history.slice(0, 5).map((h: any, i: number) => (
            <li key={i}>{h.appliedOn}: day {h.stepDay} {ACTION_LABEL[h.action] ?? h.action}, {h.outcome === "RAISED" ? "task raised" : "skipped (missed)"}</li>))}
        </ul>)}
    </div>
  )
}
