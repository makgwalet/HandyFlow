// src/pages/supply-chain/TendersTab.tsx — tenders (RFQs): list, create, and the per-tender workspace.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { Plus, Scale, ArrowLeft } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { TH, TD, ErrBox, Spinner, EmptyState, filterPill, fmtR, fmtDate } from "./scm.shared"
import { asList, errorText } from "./scmOps"
import { Pill, type Tone } from "./scmUi"
import { TenderCreate } from "./TenderCreate"
import { TenderDetail, type Rfq } from "./TenderDetail"

const TONE: Record<string, Tone> = { DRAFT: "neutral", OPEN: "info", CLOSED: "warn", AWARDED: "ok", CANCELLED: "bad" }
const FILTERS = ["ALL", "DRAFT", "OPEN", "CLOSED", "AWARDED", "CANCELLED"] as const

export function TendersTab() {
  const canManage = usePermissions(["SCM_RFQ_MANAGE", "SCM_ADMIN"])
  const [view, setView] = useState<{ kind: "list" } | { kind: "new" } | { kind: "detail"; id: string }>({ kind: "list" })
  const [filter, setFilter] = useState<(typeof FILTERS)[number]>("ALL")
  const list = useQuery({ queryKey: ["scm-rfqs"], queryFn: () => apiClient.get("/api/v1/supply-chain/rfqs", { params: { size: 100 } }).then(asList<Rfq>) })

  if (view.kind !== "list") return (
    <div>
      <div style={{ padding: "12px 20px 0" }}><button style={{ ...filterPill(false), display: "inline-flex", gap: 6, alignItems: "center" }} onClick={() => setView({ kind: "list" })}><ArrowLeft size={13} /> All tenders</button></div>
      {view.kind === "new" ? <TenderCreate onCancel={() => setView({ kind: "list" })} onDone={id => setView({ kind: "detail", id })} /> : <TenderDetail id={view.id} />}
    </div>)

  const rows = (list.data ?? []).filter(r => filter === "ALL" || r.status === filter)
  return (
    <div style={{ padding: 20, display: "grid", gap: 14 }}>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
        {FILTERS.map(f => <button key={f} style={filterPill(filter === f)} onClick={() => setFilter(f)}>{f[0] + f.slice(1).toLowerCase()}</button>)}
        <div style={{ flex: 1 }} />
        {canManage && <button onClick={() => setView({ kind: "new" })} style={{ ...filterPill(true), display: "flex", gap: 6, alignItems: "center" }}><Plus size={14} /> New tender</button>}
      </div>
      {list.isLoading ? <Spinner /> : list.isError ? <ErrBox msg={errorText(list.error, "Could not load tenders")} /> : rows.length === 0 ? (
        <EmptyState icon={Scale} title="No tenders" sub="Ask suppliers for quotations, compare them side by side and award." />
      ) : (
        <div style={{ overflowX: "auto", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12 }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr>{["Tender", "Closes", "Estimate", "Quotes", "Status"].map(h => <th key={h} style={TH}>{h}</th>)}</tr></thead>
            <tbody>{rows.map(r => (
              <tr key={r.id} style={{ borderTop: "1px solid var(--hf-border)", cursor: "pointer" }} onClick={() => setView({ kind: "detail", id: r.id })}>
                <td style={TD}><strong>{r.rfqNumber}</strong><div style={{ fontSize: 12 }}>{r.title}</div></td><td style={TD}>{fmtDate(r.closesAt)}</td>
                <td style={TD}>{r.estimatedTotal == null ? "—" : fmtR(r.estimatedTotal)}</td><td style={TD}>{r.quotationCount}</td><td style={TD}><Pill tone={TONE[r.status] ?? "neutral"}>{r.status[0] + r.status.slice(1).toLowerCase()}</Pill></td>
              </tr>))}</tbody>
          </table>
        </div>)}
    </div>
  )
}
