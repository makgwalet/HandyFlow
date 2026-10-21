// src/pages/customers/AuditPanel.tsx
// The CRM audit log: a filterable, paged list of who changed what. The server records and searches; this asks and shows.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { unwrapList } from "./crmApi"
import { ENTITY_CHOICES, NO_AUDIT_FILTER, auditParams, auditSentence, filterProblem, pageText, type AuditEntity, type AuditFilter, type AuditPage } from "./audit.logic"
import { Loading } from "./CrmKit"

const field: React.CSSProperties = { padding: "6px 9px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13 }
const ghost: React.CSSProperties = { ...field, cursor: "pointer" }
interface UserRow { id: string; firstName?: string; lastName?: string; email?: string }
const userName = (u: UserRow) => [u.firstName, u.lastName].filter(Boolean).join(" ") || u.email || "User"
const when = (iso: string) => { const d = new Date(iso); return Number.isNaN(d.getTime()) ? iso : d.toLocaleString("en-ZA", { dateStyle: "medium", timeStyle: "short" }) }
const pageOf = (r: { data?: { data?: unknown } }): AuditPage => {
  const d = (r?.data?.data ?? r?.data) as Partial<AuditPage> | undefined
  return { content: d?.content ?? [], totalPages: d?.totalPages ?? 0, totalElements: d?.totalElements ?? 0, number: d?.number ?? 0 }
}

export default function AuditPanel() {
  const [filter, setFilter] = useState<AuditFilter>(NO_AUDIT_FILTER), [page, setPage] = useState(0)
  const problem = filterProblem(filter)
  const log = useQuery({ queryKey: ["crm", "audit", filter, page], enabled: !problem, placeholderData: prev => prev, queryFn: async () => pageOf(await apiClient.get("/api/v1/crm/audit", { params: auditParams(filter, page) })) })
  const users = useQuery({ queryKey: ["crm", "task-users"], retry: false, queryFn: async () => unwrapList<UserRow>(await apiClient.get("/api/v1/identity/users", { params: { size: 200 } })) })
  const names = new Map((users.data ?? []).map(u => [u.id, userName(u)]))
  const set = (patch: Partial<AuditFilter>) => { setFilter({ ...filter, ...patch }); setPage(0) }
  const data = log.data, rows = data?.content ?? []

  return (
    <div style={{ display: "grid", gap: 12, fontSize: 13 }}>
      <section aria-label="Audit log">
        <h3 style={{ margin: "0 0 4px", fontSize: 15 }}>Audit log</h3>
        <p style={{ margin: "0 0 8px", color: "var(--hf-text-muted)" }}>Changes to rules, workflows, reports, opportunities and company groups, and when leads are converted or customers deleted. Newest first. Changes made by automation show as "The system".</p>
        <div role="group" aria-label="Audit filters" style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
          <select aria-label="What changed" style={field} value={filter.entityType} onChange={e => set({ entityType: e.target.value as AuditEntity | "" })}>
            <option value="">Everything</option>{ENTITY_CHOICES.map(c => <option key={c.id} value={c.id}>{c.label}</option>)}</select>
          <select aria-label="Who" style={field} value={filter.actorId} onChange={e => set({ actorId: e.target.value })}>
            <option value="">Anyone</option>{(users.data ?? []).map(u => <option key={u.id} value={u.id}>{userName(u)}</option>)}</select>
          <label>From <input aria-label="From date" type="date" style={field} value={filter.from} onChange={e => set({ from: e.target.value })} /></label>
          <label>To <input aria-label="To date" type="date" style={field} value={filter.to} onChange={e => set({ to: e.target.value })} /></label>
          <button type="button" style={ghost} onClick={() => { setFilter(NO_AUDIT_FILTER); setPage(0) }}>Clear</button>
        </div>
        {problem && <div role="alert" style={{ color: "var(--hf-danger-text)", marginTop: 6 }}>{problem}</div>}
        {log.isLoading && <Loading />}
        {log.isError && <div role="alert" style={{ marginTop: 6 }}>Couldn't load the audit log. You may not have permission to see it.</div>}
        {data && rows.length === 0 && !problem && <p style={{ color: "var(--hf-text-muted)" }}>Nothing matches.</p>}
        <ul style={{ listStyle: "none", margin: "8px 0 0", padding: 0, display: "grid", gap: 6 }}>
          {rows.map(e => (
            <li key={e.id} aria-label={`Event ${e.id}`} style={{ display: "grid", gap: 1 }}>
              <span><strong>{auditSentence(e, e.actorId ? names.get(e.actorId) ?? null : null)}</strong>{e.detail ? <span style={{ color: "var(--hf-text-muted)" }}> — {e.detail}</span> : null}</span>
              <span style={{ color: "var(--hf-text-muted)", fontSize: 12 }}>{when(e.createdAt)}</span>
            </li>))}
        </ul>
        {data && data.totalElements > 0 && (
          <div style={{ display: "flex", gap: 8, alignItems: "center", marginTop: 10 }}>
            <button type="button" style={ghost} disabled={page === 0} onClick={() => setPage(page - 1)}>Newer</button>
            <span>{pageText(data)}</span>
            <button type="button" style={ghost} disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Older</button>
          </div>)}
      </section>
    </div>
  )
}
