// src/pages/security/InvoiceDraftDetail.tsx
//
// One invoice draft opened up: who/when it moved, and the frozen billing preview it was made from. Read only.
// Used under a draft on the site's billing card and on the Invoice drafts screen. SECURITY_ADMIN only (the server enforces it too).
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import Chip from "../../components/ui/Chip"
import { dateTimeIn, useCompanyZone } from "./companyZone"
import { BASIS_LABEL, billingErrorText, formatCents, hoursText, rateText, type SiteRate } from "./billingLogic"
import { byLine, detailHeading, personText, readSnapshot, type DraftDetail } from "./draftViewLogic"

const muted: React.CSSProperties = { fontSize: 13, color: "var(--hf-text-muted)" }
const alertStyle: React.CSSProperties = { fontSize: 13, color: "var(--hf-danger-text)", marginTop: 6 }

export default function InvoiceDraftDetail({ siteId, draftId }: { siteId: string; draftId: string }) {
  const { zone } = useCompanyZone()
  const { data, isLoading, error } = useQuery<DraftDetail>({
    queryKey: ["site-invoice-draft", siteId, draftId],
    queryFn: async () => (await apiClient.get(`/api/v1/security/sites/${siteId}/invoice-drafts/${draftId}`)).data?.data as DraftDetail,
  })
  if (isLoading) return <div style={muted}>Loading…</div>
  if (error || !data || !data.draft) return <div role="alert" style={alertStyle}>{billingErrorText(error, "This draft could not be opened.")}</div>
  const d = data.draft
  const { preview, problem } = readSnapshot(data)
  const when = (iso: string) => dateTimeIn(zone, iso)
  return (
    <div aria-label="Draft detail" style={{ marginTop: 8, padding: 12, borderRadius: 10, background: "var(--hf-surface-sunken)" }}>
      <div style={{ ...muted, display: "flex", flexDirection: "column", gap: 2 }}>
        <span>{byLine("Saved", personText(d.createdBy, data.people), when(d.createdAt))}</span>
        {d.approvedAt && <span>{byLine("Approved", personText(d.approvedBy, data.people), when(d.approvedAt))}</span>}
        {d.voidedAt && <span>{byLine("Voided", personText(d.voidedBy, data.people), when(d.voidedAt))}{d.voidReason ? `: ${d.voidReason}` : ""}</span>}
        {d.note && <span>Note: {d.note}</span>}
      </div>
      <div style={{ fontWeight: 700, fontSize: 13, margin: "10px 0 6px" }}>{detailHeading(d)}</div>
      {problem && <div role="alert" style={alertStyle}>{problem}</div>}
      {preview && (
        <>
          {preview.inProgress && <div style={{ marginBottom: 6 }}><Chip tone="info">Month to date when saved</Chip></div>}
          {preview.lines.length === 0 ? <div style={muted}>No rate covered this month, so nothing was priced.</div> : (
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
              <thead><tr style={{ textAlign: "left", color: "var(--hf-text-muted)", fontSize: 12 }}><th scope="col">Rate</th><th scope="col">Hours</th><th scope="col">Days in force</th><th scope="col" style={{ textAlign: "right" }}>Amount</th></tr></thead>
              <tbody>{preview.lines.map(l => (
                <tr key={l.rateId} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                  <td>{rateText({ type: l.type as SiteRate["type"], amountCents: l.rateCents, currency: d.currency })}{l.vatInclusive ? " (includes VAT)" : ""}</td>
                  <td>{l.type === "HOURLY" ? hoursText(l.hours) : "Fixed"}</td><td>{l.daysInForce}</td>
                  <td style={{ textAlign: "right" }}>{formatCents(l.amountCents, d.currency)}</td>
                </tr>))}</tbody>
            </table>
          )}
          <div style={{ display: "flex", justifyContent: "space-between", marginTop: 8, fontWeight: 700 }}>
            <span>Total ({hoursText(preview.billedHours)} billed, {(BASIS_LABEL[d.basis] ?? d.basis).toLowerCase()})</span>
            <span data-testid="snapshot-total">{formatCents(preview.totalCents, d.currency)}</span>
          </div>
          {preview.unpricedHours > 0 && <div style={muted}>{hoursText(preview.unpricedHours)} fell on days with no rate and were not priced.</div>}
          {Array.isArray(preview.warnings) && preview.warnings.length > 0 && <ul style={{ ...muted, margin: "6px 0 0", paddingLeft: 18 }}>{preview.warnings.map(w => <li key={w}>{w}</li>)}</ul>}
        </>
      )}
    </div>
  )
}
