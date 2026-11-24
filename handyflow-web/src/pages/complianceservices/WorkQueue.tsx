// src/pages/complianceservices/WorkQueue.tsx
//
// One list of what needs attention across the practice's active clients, most urgent first. Read only: each row
// opens the place where the work is done.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { Link } from "react-router-dom"
import { apiClient } from "../../api/client"
import { TYPE_LABELS, severityLabel, linkFor, typeCounts, filterItems, typeLabel, type QueueItem } from "./queueRules"

interface Queue { counts: { total: number; high: number; medium: number; low: number }; items: QueueItem[] }

const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T
const TONE: Record<string, string> = { HIGH: "var(--hf-danger-text, #c0392b)", MEDIUM: "var(--hf-warning-text, #8a5a00)", LOW: "var(--hf-text-muted)" }
const pill = (active: boolean): React.CSSProperties => ({
  padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none", fontWeight: active ? 600 : 400,
  background: active ? "var(--hf-success-solid-strong)" : "var(--hf-surface-sunken)", color: active ? "var(--hf-text-on-solid)" : "var(--hf-text-muted)",
})

export default function WorkQueue() {
  const [type, setType] = useState("ALL")
  const [search, setSearch] = useState("")
  const { data, isLoading, isError } = useQuery<Queue>({ queryKey: ["cs-work-queue"], queryFn: async () => unwrap<Queue>(await apiClient.get("/api/v1/compliance-services/work-queue")) })
  const items = data?.items ?? []
  const shown = filterItems(items, type, search)

  return (
    <div>
      <div style={{ fontSize: 13, color: "var(--hf-text-muted)", marginBottom: 12 }}>
        {data ? `${data.counts.total} to look at · ${data.counts.high} urgent, ${data.counts.medium} soon, ${data.counts.low} when you can` : isLoading ? "Loading..." : ""}
      </div>
      {isError && <div role="alert" style={{ fontSize: 12, color: TONE.HIGH }}>The work queue could not be loaded.</div>}
      {items.length > 0 && (
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap", alignItems: "center", marginBottom: 14 }}>
          <button style={pill(type === "ALL")} onClick={() => setType("ALL")}>All ({items.length})</button>
          {typeCounts(items).map(([t, n]) => <button key={t} style={pill(type === t)} onClick={() => setType(t)}>{TYPE_LABELS[t] ?? t} ({n})</button>)}
          <input aria-label="Search the queue" placeholder="Search" value={search} onChange={e => setSearch(e.target.value)}
            style={{ marginLeft: "auto", padding: "6px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)" }} />
        </div>
      )}
      {data && items.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)", padding: "40px 0", textAlign: "center" }}>Nothing needs attention right now.</div>}
      {data && items.length > 0 && shown.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Nothing matches.</div>}
      <div style={{ display: "grid", gap: 8 }}>
        {shown.map((i, n) => (
          <Link key={`${i.type}-${i.clientId}-${i.title}-${i.dueDate}-${n}`} to={linkFor(i)}
            style={{ display: "flex", gap: 12, alignItems: "center", flexWrap: "wrap", border: "1px solid var(--hf-border)", borderRadius: 10, padding: "10px 14px", textDecoration: "none", color: "inherit" }}>
            <span style={{ fontSize: 11, fontWeight: 700, minWidth: 92, color: TONE[i.severity] ?? TONE.LOW }}>{severityLabel(i.severity)}</span>
            <span style={{ fontSize: 13, fontWeight: 600, minWidth: 140 }}>{i.clientName ?? "Catalogue"}</span>
            <span style={{ fontSize: 13, flex: 1 }}>{i.title}</span>
            <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{typeLabel(i.type)} · {i.detail}</span>
          </Link>
        ))}
      </div>
    </div>
  )
}
