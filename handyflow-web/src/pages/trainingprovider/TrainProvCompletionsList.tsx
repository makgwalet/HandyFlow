// Recorded completion outcomes across the provider, newest first. Withdrawn outcomes stay in the list.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { OUTCOMES, completionLine, label, outcomeTone, type Completion } from "./completionRules"
import { Badge } from "./tpUi"
import { card, input, muted, unwrap } from "./tpStyle"
import { CMP } from "./tpApi"

export default function TrainProvCompletionsList() {
  const [outcome, setOutcome] = useState("")
  const [status, setStatus] = useState("RECORDED")
  const { data = [], isLoading } = useQuery<Completion[]>({
    queryKey: ["tp-completions", "all", outcome, status],
    queryFn: async () => unwrap<Completion[]>(await apiClient.get(CMP, { params: { outcome: outcome || undefined, status: status || undefined } })),
  })
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        <select aria-label="Outcome filter" style={{ ...input, width: "auto" }} value={outcome} onChange={e => setOutcome(e.target.value)}><option value="">All outcomes</option>{Object.entries(OUTCOMES).map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select>
        <select aria-label="Outcome record filter" style={{ ...input, width: "auto" }} value={status} onChange={e => setStatus(e.target.value)}><option value="RECORDED">Standing</option><option value="WITHDRAWN">Withdrawn</option><option value="">All</option></select>
      </div>
      <div style={muted}>An outcome here is a person's record. It is not a certificate.</div>
      {isLoading ? <div style={muted}>Loading…</div> : data.length === 0 ? <div style={muted}>No outcomes found.</div> : data.map(c => (
        <div key={c.id} style={card}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
            <div><strong>{c.learnerName ?? "Learner"} · {c.programmeTitle ?? "Programme"}</strong><div style={muted}>{c.completionNo} · {c.enrolmentNo} · {completionLine(c)}</div></div>
            <Badge text={c.status === "WITHDRAWN" ? "Withdrawn" : label(OUTCOMES, c.outcome)} tone={outcomeTone(c.outcome, c.status)} />
          </div>
          <div style={muted}>Basis: {c.basis}</div>
        </div>
      ))}
    </div>
  )
}
