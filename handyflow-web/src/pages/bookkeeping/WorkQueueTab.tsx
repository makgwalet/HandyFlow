// What needs doing across every client: lines not categorised, open client queries, missing documents, draft journals.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useNavigate } from "react-router-dom"
import { apiClient } from "../../api/client"
import { dateText, daysSince, rowsOf, workRows, type WorkRow } from "./bookkeeping.logic"
import { card, muted, right, td, th } from "./ui"

export default function WorkQueueTab() {
  const navigate = useNavigate()
  const [showAll, setShowAll] = useState(false)
  const { data = [], isLoading } = useQuery<WorkRow[]>({ queryKey: ["bk-work-queue"], queryFn: async () => rowsOf<WorkRow>(await apiClient.get("/api/v1/bookkeeping/work-queue")) })
  const rows = workRows(data, showAll)
  const today = new Date()
  const open = (r: WorkRow, section: string) => navigate(`/bookkeeping/${section}?client=${r.clientId}`)
  const link = (r: WorkRow, section: string, n: number) => n === 0 ? <span style={muted}>—</span>
    : <button onClick={() => open(r, section)} style={{ all: "unset", cursor: "pointer", textDecoration: "underline", fontWeight: 600 }}>{n}</button>
  return (
    <div>
      <label style={{ ...muted, display: "block", marginBottom: 10 }}>
        <input type="checkbox" aria-label="Show clients with nothing outstanding" checked={showAll} onChange={e => setShowAll(e.target.checked)} /> Show clients with nothing outstanding
      </label>
      {isLoading && <p style={{ fontSize: 13 }}>Loading…</p>}
      {!isLoading && rows.length === 0 && <div style={card}>{data.length === 0 ? "No clients yet." : "All caught up. Nothing is waiting on you."}</div>}
      {rows.length > 0 && (
        <div style={card}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
            <thead><tr><th style={th}>Client</th><th style={{ ...th, ...right }}>Not categorised</th><th style={th}>Oldest</th><th style={{ ...th, ...right }}>Open queries</th>
              <th style={{ ...th, ...right }}>Missing documents</th><th style={{ ...th, ...right }}>Draft journals</th></tr></thead>
            <tbody>{rows.map(r => {
              const age = daysSince(r.oldestUnreconciled, today)
              return (
                <tr key={r.clientId} style={{ borderTop: "1px solid var(--hf-border)" }}>
                  <td style={td}><b>{r.clientName}</b></td>
                  <td style={{ ...td, ...right }}>{link(r, "bank", r.unreconciled)}</td>
                  <td style={td}>{r.oldestUnreconciled ? <>{dateText(r.oldestUnreconciled)}{age != null && age > 30 && <span style={{ color: "var(--hf-danger-text, #b91c1c)" }}> ({age} days)</span>}</> : "—"}</td>
                  <td style={{ ...td, ...right }}>{link(r, "queries", r.openQueries)}</td>
                  <td style={{ ...td, ...right }}>{link(r, "queries", r.missingDocuments)}</td>
                  <td style={{ ...td, ...right }}>{r.draftJournals === 0 ? <span style={muted}>—</span> : r.draftJournals}</td>
                </tr>)
            })}</tbody>
          </table>
        </div>
      )}
    </div>
  )
}
