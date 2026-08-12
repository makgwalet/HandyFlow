// src/pages/payroll-bureau-portal/PayrollBureauPortalClientDetailPage.tsx
import { useEffect, useState } from "react"
import { useParams } from "react-router-dom"
import { payrollBureauPortalApi } from "../../api/payrollBureauPortal.api"
import type { PayDeadline, PayFeeNote, PortalClientSummary } from "../../types/payrollBureauPortal.types"
import { PortalShell } from "../accountant-portal/PortalShell"
import { color, radius, space, statusTone, type } from "../accountant-portal/portal-theme"

const fmtR = (n: any) => `R ${Number(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`
const fmtD = (d: any) => (d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—")

function downloadBlob(blob: Blob, fileName: string) {
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement("a")
  a.href = url; a.download = fileName
  document.body.appendChild(a); a.click(); a.remove()
  window.URL.revokeObjectURL(url)
}

type TabId = "fee-notes" | "deadlines"

export function PayrollBureauPortalClientDetailPage() {
  const { clientId } = useParams<{ clientId: string }>()
  const [tab, setTab] = useState<TabId>("fee-notes")
  const [clientName, setClientName] = useState("")

  const [feeNotes, setFeeNotes] = useState<PayFeeNote[]>([])
  const [feeNotesLoading, setFeeNotesLoading] = useState(true)

  const [deadlines, setDeadlines] = useState<PayDeadline[]>([])
  const [deadlinesLoading, setDeadlinesLoading] = useState(true)

  useEffect(() => {
    if (!clientId) return
    payrollBureauPortalApi.getMyClients().then((clients: PortalClientSummary[]) => {
      setClientName(clients.find(c => c.clientId === clientId)?.tradingName ?? "")
    })
    payrollBureauPortalApi.getMyFeeNotes(clientId).then(setFeeNotes).finally(() => setFeeNotesLoading(false))
    payrollBureauPortalApi.getMyDeadlines(clientId).then(setDeadlines).finally(() => setDeadlinesLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clientId])

  const upcomingDeadlineCount = deadlines.filter(d => d.status !== "FILED" && d.daysUntilDue <= 30).length

  const tabs: { id: TabId; label: string; count?: number }[] = [
    { id: "fee-notes", label: "Invoices" },
    { id: "deadlines", label: "Deadlines", count: upcomingDeadlineCount },
  ]

  return (
    <PortalShell backTo="/payroll-bureau/portal">
      <div style={{ maxWidth: 780, margin: "0 auto", padding: `${space(9)} ${space(5)} ${space(12)}` }}>
        <h1 style={{ fontSize: 22, fontWeight: 800, color: color.ink, marginBottom: space(6), letterSpacing: "-0.02em" }}>
          {clientName || <span style={{ opacity: 0.3 }}>Loading…</span>}
        </h1>

        <div style={{ display: "flex", gap: space(1), borderBottom: `1px solid ${color.border}`, marginBottom: space(6) }}>
          {tabs.map(t => (
            <button key={t.id} onClick={() => setTab(t.id)} style={{
              padding: `${space(2.5)} ${space(4)}`, background: "none", border: "none",
              borderBottom: tab === t.id ? `2px solid ${color.navy}` : "2px solid transparent",
              color: tab === t.id ? color.navy : color.muted, fontWeight: tab === t.id ? 700 : 500,
              fontSize: 13.5, cursor: "pointer", marginBottom: -1, display: "flex", alignItems: "center", gap: 6 }}>
              {t.label}
              {!!t.count && (
                <span style={{ fontSize: 11, fontWeight: 700, background: tab === t.id ? color.navy : color.border,
                  color: tab === t.id ? "#fff" : color.slate, borderRadius: radius.pill, padding: "1px 6px",
                  minWidth: 16, textAlign: "center" as const }}>{t.count}</span>
              )}
            </button>
          ))}
        </div>

        {tab === "fee-notes" && (
          feeNotesLoading ? (
            <LoadingRows />
          ) : feeNotes.length === 0 ? (
            <EmptyState icon="🧾" title="No invoices yet" body="Invoices from your payroll bureau will appear here as they're issued." />
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: space(2.5) }}>
              {feeNotes.map(f => {
                const sc = statusTone[f.status] ?? statusTone.DRAFT
                return (
                  <div key={f.id} style={{ padding: `${space(4)} ${space(5)}`, background: color.surface,
                    border: `1px solid ${color.border}`, borderRadius: radius.md,
                    display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                    <div>
                      <div style={{ display: "flex", alignItems: "center", gap: space(2), marginBottom: space(1) }}>
                        <span style={{ fontWeight: 700, fontSize: 14.5, color: color.ink }}>{f.invoiceNumber}</span>
                        <span style={{ background: sc.bg, color: sc.color, padding: "2px 9px", borderRadius: radius.pill,
                          fontSize: 10.5, fontWeight: 700 }}>{f.status}</span>
                      </div>
                      <div style={{ fontSize: 12.5, color: color.faint }}>
                        Issued {fmtD(f.invoiceDate)} · Due {fmtD(f.dueDate)}
                      </div>
                    </div>
                    <div style={{ textAlign: "right" as const }}>
                      <div style={{ fontWeight: 700, fontSize: 15, color: color.ink }}>{fmtR(f.total)}</div>
                      {f.balance > 0 && <div style={{ fontSize: 11.5, color: color.red, fontWeight: 600 }}>{fmtR(f.balance)} owing</div>}
                    </div>
                  </div>
                )
              })}
            </div>
          )
        )}

        {tab === "deadlines" && (
          deadlinesLoading ? (
            <LoadingRows />
          ) : deadlines.length === 0 ? (
            <EmptyState icon="📅" title="No filing deadlines on record yet" body="EMP201/EMP501 deadlines your bureau is tracking will appear here." />
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: space(2.5) }}>
              {deadlines.map(d => {
                const sc = statusTone[d.status] ?? statusTone.PENDING
                return (
                  <div key={d.id} style={{ padding: `${space(4)} ${space(5)}`, background: color.surface,
                    border: `1px solid ${color.border}`, borderRadius: radius.md }}>
                    <div style={{ display: "flex", alignItems: "center", gap: space(2), marginBottom: space(1) }}>
                      <span style={{ fontWeight: 700, fontSize: 14.5, color: color.ink }}>
                        {d.deadlineType}{d.periodMonth ? ` — ${d.periodYear}/${String(d.periodMonth).padStart(2, "0")}` : ` — ${d.periodYear}`}
                      </span>
                      <span style={{ background: sc.bg, color: sc.color, padding: "2px 9px", borderRadius: radius.pill,
                        fontSize: 10.5, fontWeight: 700 }}>{d.status}</span>
                    </div>
                    <div style={{ fontSize: 12.5, color: color.faint }}>
                      Due {fmtD(d.adjustedDueDate)}
                      {d.status === "PENDING" && d.daysUntilDue >= 0 && ` · ${d.daysUntilDue}d remaining`}
                      {d.status === "PENDING" && d.daysUntilDue < 0 && (
                        <span style={{ color: color.red, fontWeight: 600 }}> · {Math.abs(d.daysUntilDue)}d overdue</span>
                      )}
                      {d.status === "FILED" && d.filedDate && ` · Filed ${fmtD(d.filedDate)}`}
                    </div>
                  </div>
                )
              })}
            </div>
          )
        )}
      </div>
    </PortalShell>
  )
}

function LoadingRows() {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: space(2.5) }}>
      {[0, 1, 2].map(i => (
        <div key={i} style={{ height: 68, background: color.surface, border: `1px solid ${color.border}`,
          borderRadius: radius.md, opacity: 0.5 }} />
      ))}
    </div>
  )
}

function EmptyState({ icon, title, body }: { icon: string; title: string; body: string }) {
  return (
    <div style={{ textAlign: "center" as const, padding: `${space(12)} ${space(6)}`, background: color.surface,
      border: `1px dashed ${color.border}`, borderRadius: radius.lg }}>
      <div style={{ fontSize: 30, marginBottom: space(3) }}>{icon}</div>
      <div style={{ fontSize: 14.5, fontWeight: 700, color: color.ink, marginBottom: space(1) }}>{title}</div>
      <div style={{ fontSize: 13, color: color.muted, maxWidth: 300, margin: "0 auto", lineHeight: 1.5 }}>{body}</div>
    </div>
  )
}
