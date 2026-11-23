// src/pages/security/InvoiceDraftsTab.tsx
//
// Invoice drafts of every site you may see, in one place: what is waiting for approval and what has been approved.
// Read only here; saving, approving and voiding stay on the site's Billing card. Drafts are not invoices. SECURITY_ADMIN only.
import { useState } from "react"
import { Link } from "react-router-dom"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import Chip from "../../components/ui/Chip"
import { usePermission } from "../../hooks/usePermission"
import InvoiceDraftDetail from "./InvoiceDraftDetail"
import { BASIS_LABEL, DRAFT_LABEL, DRAFT_TONE, billingErrorText, formatCents } from "./billingLogic"
import { STATUS_FILTERS, flagsOf, liveTotalText, monthParam, overviewParams, summarise, type DraftOverview } from "./draftViewLogic"
import { monthTitle } from "./monthlyPackLogic"

const muted: React.CSSProperties = { fontSize: 13, color: "var(--hf-text-muted)" }
const field: React.CSSProperties = { padding: "6px 8px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13, minWidth: 0 }
const btn: React.CSSProperties = { padding: "6px 10px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", color: "var(--hf-text-secondary)", cursor: "pointer", fontSize: 13 }
const tile: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 10, padding: "10px 14px", minWidth: 140 }

export default function InvoiceDraftsTab() {
  const canAdmin = usePermission("SECURITY_ADMIN")
  const [month, setMonth] = useState("")
  const [status, setStatus] = useState("")
  const [opened, setOpened] = useState<string | null>(null)
  const params = overviewParams(month, status)
  const { data, isLoading, error } = useQuery<DraftOverview>({
    queryKey: ["invoice-drafts-overview", params.month ?? "", params.status ?? ""], enabled: canAdmin,
    queryFn: async () => (await apiClient.get("/api/v1/security/invoice-drafts", { params })).data?.data as DraftOverview,
  })
  if (!canAdmin) return <div style={muted}>Invoice drafts are for administrators.</div>
  const ok = !!data && Array.isArray(data.items)
  const sum = ok ? summarise(data.items) : null
  const badMonth = month.trim() !== "" && !monthParam(month)

  return (
    <div>
      <div style={muted}>Billing drafts saved from each site's billing preview. These are for checking, not invoices; nothing is sent anywhere.</div>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center", margin: "12px 0" }}>
        <label style={muted}>Month <input type="month" aria-label="Month" style={field} value={month} onChange={e => setMonth(e.target.value)} /></label>
        <select aria-label="Status" style={field} value={status} onChange={e => setStatus(e.target.value)}>
          {STATUS_FILTERS.map(s => <option key={s.value} value={s.value}>{s.label}</option>)}
        </select>
        {(month || status) && <button type="button" style={btn} onClick={() => { setMonth(""); setStatus("") }}>Clear filters</button>}
      </div>
      {badMonth && <div style={muted}>Choose a month like 2026-10, or leave it empty for every month.</div>}
      {isLoading && <div style={muted}>Loading…</div>}
      {(error || (data && !ok)) && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>{billingErrorText(error, "The invoice drafts could not be loaded.")}</div>}
      {ok && sum && (
        <>
          <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 12 }}>
            <div style={tile}><div style={muted}>Waiting for approval</div><strong data-testid="count-waiting">{sum.waiting}</strong></div>
            <div style={tile}><div style={muted}>Approved</div><strong data-testid="count-approved">{sum.approved}</strong></div>
            {!data.truncated && <div style={tile}><div style={muted}>Waiting + approved</div><strong data-testid="live-total">{liveTotalText(sum)}</strong></div>}
          </div>
          {data.truncated && <div role="status" style={{ ...muted, color: "var(--hf-warning-text)", marginBottom: 8 }}>Only the newest {data.items.length} drafts are shown, so no total is given. Choose a month or a status to narrow the list.</div>}
          {data.items.length === 0 ? <div style={muted}>No invoice drafts{month || status ? " match these filters" : " yet. Save one from a site's Billing card"}.</div> : (
            <div>
              {data.items.map(d => {
                const flags = flagsOf(d)
                return (
                  <div key={d.id} style={{ padding: "10px 0", borderTop: "1px solid var(--hf-border-subtle)", fontSize: 13 }}>
                    <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
                      <Link to={`/security/sites/${d.siteId}`} style={{ fontWeight: 700 }}>{d.siteName}</Link>
                      <span>{monthTitle(d.month)}</span>
                      <span>{formatCents(d.totalCents, d.currency)}</span>
                      <Chip tone={DRAFT_TONE[d.status]}>{DRAFT_LABEL[d.status]}</Chip>
                      <span style={muted}>{BASIS_LABEL[d.basis] ?? d.basis}</span>
                      {flags.map(f => <Chip key={f} tone="warn">{f}</Chip>)}
                      <button type="button" style={{ ...btn, marginLeft: "auto" }} aria-expanded={opened === d.id}
                        aria-label={`${opened === d.id ? "Hide" : "Show"} details of ${d.siteName} ${monthTitle(d.month)}`} onClick={() => setOpened(o => o === d.id ? null : d.id)}>
                        {opened === d.id ? "Hide details" : "Details"}
                      </button>
                    </div>
                    {d.status === "VOID" && d.voidReason && <div style={muted}>Voided: {d.voidReason}</div>}
                    {opened === d.id && <InvoiceDraftDetail siteId={d.siteId} draftId={d.id} />}
                  </div>
                )
              })}
            </div>
          )}
        </>
      )}
    </div>
  )
}
