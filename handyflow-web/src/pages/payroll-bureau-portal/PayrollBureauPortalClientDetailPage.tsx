// src/pages/payroll-bureau-portal/PayrollBureauPortalClientDetailPage.tsx
import { useEffect, useState } from "react"
import { useParams } from "react-router-dom"
import { payrollBureauPortalApi } from "../../api/payrollBureauPortal.api"
import type { PortalFeeNote, PortalDeadline, PortalClientSummary } from "../../types/payrollBureauPortal.types"
import { PortalShell } from "../accountant-portal/PortalShell"
import { color, radius, space, statusTone } from "../accountant-portal/portal-theme"

const fmtR = (n: any) => `R ${Number(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`
const fmtD = (d: any) => (d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—")

type TabId = "invoices" | "deadlines"

export function PayrollBureauPortalClientDetailPage() {
  const { clientId } = useParams<{ clientId: string }>()
  const [tab, setTab] = useState<TabId>("invoices")
  const [clientName, setClientName] = useState("")

  const [feeNotes, setFeeNotes] = useState<PortalFeeNote[]>([])
  const [feeLoading, setFeeLoading] = useState(true)
  const [deadlines, setDeadlines] = useState<PortalDeadline[]>([])
  const [deadlinesLoading, setDeadlinesLoading] = useState(true)

  useEffect(() => {
    if (!clientId) return
    payrollBureauPortalApi.getMyClients().then((clients: PortalClientSummary[]) => {
      setClientName(clients.find(c => c.clientId === clientId)?.tradingName ?? "")
    })
    payrollBureauPortalApi.getMyFeeNotes(clientId).then(setFeeNotes).finally(() => setFeeLoading(false))
    payrollBureauPortalApi.getMyDeadlines(clientId).then(setDeadlines).finally(() => setDeadlinesLoading(false))
  }, [clientId])

  const tabs: { id: TabId; label: string }[] = [{ id: "invoices", label: "Invoices" }, { id: "deadlines", label: "SARS Deadlines" }]

  return (
    <PortalShell backTo="/payroll-bureau/portal">
      <div style={{ maxWidth: 780, margin: "0 auto", padding: `${space(9)} ${space(5)} ${space(12)}` }}>
        <h1 style={{ fontSize: 22, fontWeight: 800, color: color.ink, marginBottom: space(6) }}>{clientName || <span style={{ opacity: 0.3 }}>Loading…</span>}</h1>

        <div style={{ display: "flex", gap: space(1), borderBottom: `1px solid ${color.border}`, marginBottom: space(6) }}>
          {tabs.map(t => (
            <button key={t.id} onClick={() => setTab(t.id)} style={{ padding: `${space(2.5)} ${space(4)}`, background: "none", border: "none",
              borderBottom: tab === t.id ? `2px solid ${color.navy}` : "2px solid transparent", color: tab === t.id ? color.navy : color.muted,
              fontWeight: tab === t.id ? 700 : 500, fontSize: 13.5, cursor: "pointer", marginBottom: -1 }}>{t.label}</button>
          ))}
        </div>

        {tab === "invoices" && (
          feeLoading ? <LoadingRows /> : feeNotes.length === 0 ? <EmptyState icon="🧾" title="No invoices yet" body="Fee notes from your payroll bureau will appear here." /> : (
            <div style={{ display: "flex", flexDirection: "column", gap: space(2.5) }}>
              {feeNotes.map(inv => (
                <div key={inv.id} style={{ padding: `${space(4)} ${space(5)}`, background: color.surface, border: `1px solid ${color.border}`, borderRadius: radius.md, display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <div>
                    <div style={{ display: "flex", alignItems: "center", gap: space(2), marginBottom: space(1) }}>
                      <span style={{ fontWeight: 700, fontSize: 14.5, color: color.ink }}>{inv.invoiceNumber}</span>
                      <span style={{ background: (statusTone[inv.status] ?? statusTone.DRAFT).bg, color: (statusTone[inv.status] ?? statusTone.DRAFT).color, padding: "2px 9px", borderRadius: radius.pill, fontSize: 10.5, fontWeight: 700 }}>{inv.status}</span>
                    </div>
                    <div style={{ fontSize: 12.5, color: color.faint }}>Due {fmtD(inv.dueDate)}{inv.daysOverdue > 0 && ` · ${inv.daysOverdue} days overdue`}</div>
                  </div>
                  <div style={{ textAlign: "right" as const }}>
                    <div style={{ fontWeight: 700, fontSize: 15, color: color.ink }}>{fmtR(inv.total)}</div>
                    {inv.balance > 0 && <div style={{ fontSize: 11.5, color: color.red, fontWeight: 600 }}>{fmtR(inv.balance)} owing</div>}
                  </div>
                </div>
              ))}
            </div>
          )
        )}

        {tab === "deadlines" && (
          deadlinesLoading ? <LoadingRows /> : deadlines.length === 0 ? <EmptyState icon="📅" title="No deadlines yet" body="SARS filing deadlines will appear here once generated." /> : (
            <div style={{ display: "flex", flexDirection: "column", gap: space(2.5) }}>
              {deadlines.map(d => (
                <div key={d.id} style={{ padding: `${space(4)} ${space(5)}`, background: color.surface, border: `1px solid ${color.border}`, borderRadius: radius.md, display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <div>
                    <span style={{ fontWeight: 700, fontSize: 14, color: color.ink }}>{d.deadlineType}</span>
                    <div style={{ fontSize: 12.5, color: color.faint, marginTop: space(1) }}>
                      {d.periodMonth ? `${d.periodYear}/${String(d.periodMonth).padStart(2, "0")}` : d.periodYear}
                    </div>
                  </div>
                  <div style={{ textAlign: "right" as const }}>
                    <div style={{ fontSize: 13, fontWeight: 600, color: color.ink }}>{fmtD(d.adjustedDueDate)}</div>
                    <span style={{ background: (statusTone[d.status] ?? statusTone.PENDING).bg, color: (statusTone[d.status] ?? statusTone.PENDING).color, padding: "2px 9px", borderRadius: radius.pill, fontSize: 10.5, fontWeight: 700 }}>{d.status}</span>
                  </div>
                </div>
              ))}
            </div>
          )
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
