// src/pages/customers/GroupsPanel.tsx
// Every company that belongs to a group, as a tree: head offices first, branches indented, with the opportunities of each company and of everything below it.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { localToday, money } from "./pipeline.logic"
import { fetchAllPages, unwrapList } from "./crmApi"
import { buildTree, type GroupNode, type Opportunity } from "./opportunities.logic"

export default function GroupsPanel() {
  const today = localToday()
  const nodes = useQuery({ queryKey: ["crm", "hierarchy", "groups"], queryFn: async () => unwrapList<GroupNode>(await apiClient.get("/api/v1/crm/hierarchy/groups")) })
  const opps = useQuery({ queryKey: ["crm", "opportunities", "all"], queryFn: () => fetchAllPages<Opportunity>(u => apiClient.get(u), "/api/v1/crm/opportunities") })
  if (nodes.isLoading) return <p>Loading…</p>
  if (nodes.isError) return <div role="alert">Couldn't load the company groups.</div>
  const rows = buildTree(nodes.data ?? [], opps.data?.items ?? [], today)
  if (rows.length === 0) return <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No company groups yet. Open a company, choose the Group tab and set its parent company.</p>
  return (
    <table aria-label="Company groups" style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
      <thead><tr style={{ textAlign: "left", color: "var(--hf-text-muted)" }}><th>Company</th><th style={{ textAlign: "right" }}>Open</th><th style={{ textAlign: "right" }}>Open value</th><th style={{ textAlign: "right" }}>Weighted</th><th style={{ textAlign: "right" }}>Won</th></tr></thead>
      <tbody>{rows.map(r => (
        <tr key={r.node.id} aria-label={r.node.name} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
          <td style={{ padding: "6px 4px", paddingLeft: 4 + r.depth * 22 }}>{r.depth > 0 && <span style={{ color: "var(--hf-text-faint)" }}>└ </span>}<strong style={{ fontWeight: r.depth === 0 ? 700 : 500 }}>{r.node.name}</strong>{r.branches > 0 && <span style={{ color: "var(--hf-text-muted)" }}> · {r.branches} {r.branches === 1 ? "branch" : "branches"}</span>}</td>
          <td style={{ textAlign: "right" }}>{r.group.openCount}</td><td style={{ textAlign: "right" }}>{money(r.group.openValue)}</td><td style={{ textAlign: "right" }}>{money(r.group.weighted)}</td><td style={{ textAlign: "right" }}>{money(r.group.wonValue)}</td>
        </tr>))}</tbody>
    </table>
  )
}
