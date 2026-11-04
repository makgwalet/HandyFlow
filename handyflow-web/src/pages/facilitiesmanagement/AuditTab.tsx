// The record of who did what in this module.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { Empty, Pager, Pick, SectionTitle, card, muted, pageOf, td, th, when } from "./kit"
import { BASE } from "./lookups"
import { AUDIT_ENTITIES, auditLabel } from "./fm.logic"

interface Entry { id: string; actorName: string | null; action: string; entityType: string; entityId: string | null; details: string | null; createdAt: string }

export default function AuditTab() {
  const [page, setPage] = useState(0), [entityType, setEntityType] = useState("")
  const { data, isLoading, isError } = useQuery({ queryKey: ["fm-audit", page, entityType], queryFn: async () => pageOf<Entry>(await apiClient.get(`${BASE}/audit`, { params: { page, size: 50, entityType: entityType || undefined } })) })
  const rows = data?.content ?? []
  return (
    <div>
      <SectionTitle>Activity log</SectionTitle>
      <div style={{ maxWidth: 280 }}><Pick label="Show" value={entityType} onChange={v => { setEntityType(v); setPage(0) }} blank="Everything" options={AUDIT_ENTITIES} /></div>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {isError ? <Empty>Could not load the log. Please try again.</Empty> : isLoading ? <Empty>Loading…</Empty> : rows.length === 0 ? <Empty>Nothing recorded yet.</Empty> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>When</th><th style={th}>Who</th><th style={th}>What happened</th></tr></thead>
            <tbody>{rows.map(e => <tr key={e.id}><td style={{ ...td, whiteSpace: "nowrap" }}>{when(e.createdAt)}</td><td style={td}>{e.actorName || "System"}</td>
              <td style={td}><b>{auditLabel(e.action)}</b><div style={muted}>{auditLabel(e.entityType)}{e.details ? ` · ${e.details}` : ""}</div></td></tr>)}</tbody>
          </table>)}
      </div>
      <Pager page={page} pages={data?.totalPages ?? 0} onPage={setPage} />
    </div>
  )
}
