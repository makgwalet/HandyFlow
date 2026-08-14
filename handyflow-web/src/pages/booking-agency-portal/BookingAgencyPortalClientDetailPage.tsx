// src/pages/booking-agency-portal/BookingAgencyPortalClientDetailPage.tsx
//
// Only an Invoices view — no tab bar at all, unlike Payroll Bureau's
// (Invoices + Deadlines) or Recruitment Agency's (Requisitions +
// Invoices) portal detail pages. Booking Agency has nothing analogous
// to SARS deadlines or open requisitions to show a client; a single
// list is the honest shape here, not a one-tab bar for its own sake.
import { useEffect, useState } from "react"
import { useParams } from "react-router-dom"
import { bookingAgencyPortalApi } from "../../api/bookingAgencyPortal.api"
import type { PortalInvoice, PortalClientSummary } from "../../types/bookingAgencyPortal.types"
import { PortalShell } from "../accountant-portal/PortalShell"
import { color, radius, space, statusTone } from "../accountant-portal/portal-theme"

const fmtR = (n: any) => `R ${Number(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`
const fmtD = (d: any) => (d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—")

export function BookingAgencyPortalClientDetailPage() {
  const { clientId } = useParams<{ clientId: string }>()
  const [clientName, setClientName] = useState("")
  const [invoices, setInvoices] = useState<PortalInvoice[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!clientId) return
    bookingAgencyPortalApi.getMyClients().then((clients: PortalClientSummary[]) => {
      setClientName(clients.find(c => c.clientId === clientId)?.tradingName ?? "")
    })
    bookingAgencyPortalApi.getMyInvoices(clientId).then(res => setInvoices(res.content)).finally(() => setLoading(false))
  }, [clientId])

  return (
    <PortalShell backTo="/booking-agency/portal">
      <div style={{ maxWidth: 780, margin: "0 auto", padding: `${space(9)} ${space(5)} ${space(12)}` }}>
        <h1 style={{ fontSize: 22, fontWeight: 800, color: color.ink, marginBottom: space(2) }}>{clientName || <span style={{ opacity: 0.3 }}>Loading…</span>}</h1>
        <p style={{ fontSize: 13.5, color: color.muted, marginBottom: space(7) }}>Invoices</p>

        {loading ? (
          <LoadingRows />
        ) : invoices.length === 0 ? (
          <EmptyState icon="🧾" title="No invoices yet" body="Invoices from your booking agency will appear here." />
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: space(2.5) }}>
            {invoices.map(inv => (
              <div key={inv.id} style={{ padding: `${space(4)} ${space(5)}`, background: color.surface, border: `1px solid ${color.border}`, borderRadius: radius.md, display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <div>
                  <div style={{ display: "flex", alignItems: "center", gap: space(2), marginBottom: space(1) }}>
                    <span style={{ fontWeight: 700, fontSize: 14.5, color: color.ink }}>{inv.invoiceNumber}</span>
                    <span style={{ background: (statusTone[inv.status] ?? statusTone.DRAFT).bg, color: (statusTone[inv.status] ?? statusTone.DRAFT).color, padding: "2px 9px", borderRadius: radius.pill, fontSize: 10.5, fontWeight: 700 }}>{inv.status}</span>
                  </div>
                  <div style={{ fontSize: 12.5, color: color.faint }}>
                    {fmtD(inv.periodStart)} – {fmtD(inv.periodEnd)} · Due {fmtD(inv.dueDate)}
                    {inv.daysOverdue > 0 && ` · ${inv.daysOverdue} days overdue`}
                  </div>
                </div>
                <div style={{ textAlign: "right" as const }}>
                  <div style={{ fontWeight: 700, fontSize: 15, color: color.ink }}>{fmtR(inv.total)}</div>
                  {inv.balance > 0 && <div style={{ fontSize: 11.5, color: color.red, fontWeight: 600 }}>{fmtR(inv.balance)} owing</div>}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </PortalShell>
  )
}

function LoadingRows() {
  return <div style={{ display: "flex", flexDirection: "column", gap: space(2.5) }}>{[0, 1, 2].map(i => <div key={i} style={{ height: 68, background: color.surface, border: `1px solid ${color.border}`, borderRadius: radius.md, opacity: 0.5 }} />)}</div>
}
function EmptyState({ icon, title, body }: { icon: string; title: string; body: string }) {
  return (
    <div style={{ textAlign: "center" as const, padding: `${space(12)} ${space(6)}`, background: color.surface, border: `1px dashed ${color.border}`, borderRadius: radius.lg }}>
      <div style={{ fontSize: 30, marginBottom: space(3) }}>{icon}</div>
      <div style={{ fontSize: 14.5, fontWeight: 700, color: color.ink, marginBottom: space(1) }}>{title}</div>
      <div style={{ fontSize: 13, color: color.muted, maxWidth: 300, margin: "0 auto" }}>{body}</div>
    </div>
  )
}
