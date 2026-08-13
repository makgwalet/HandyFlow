// src/pages/recruitment-agency-portal/RecruitmentAgencyPortalClientDetailPage.tsx
import { useEffect, useState } from "react"
import { useParams } from "react-router-dom"
import { recruitmentAgencyPortalApi } from "../../api/recruitmentAgencyPortal.api"
import type { Requisition, Placement, AgencyInvoice, PortalClientSummary } from "../../types/recruitmentAgencyPortal.types"
import { PortalShell } from "../accountant-portal/PortalShell"
import { color, radius, space, statusTone, type } from "../accountant-portal/portal-theme"

const fmtR = (n: any) => `R ${Number(n ?? 0).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`
const fmtD = (d: any) => (d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—")

type TabId = "requisitions" | "invoices"

export function RecruitmentAgencyPortalClientDetailPage() {
  const { clientId } = useParams<{ clientId: string }>()
  const [tab, setTab] = useState<TabId>("requisitions")
  const [clientName, setClientName] = useState("")

  const [requisitions, setRequisitions] = useState<Requisition[]>([])
  const [reqLoading, setReqLoading] = useState(true)
  const [expandedReq, setExpandedReq] = useState<string | null>(null)
  const [placements, setPlacements] = useState<Placement[]>([])

  const [invoices, setInvoices] = useState<AgencyInvoice[]>([])
  const [invLoading, setInvLoading] = useState(true)

  useEffect(() => {
    if (!clientId) return
    recruitmentAgencyPortalApi.getMyClients().then((clients: PortalClientSummary[]) => {
      setClientName(clients.find(c => c.clientId === clientId)?.tradingName ?? "")
    })
    recruitmentAgencyPortalApi.getMyRequisitions(clientId).then(setRequisitions).finally(() => setReqLoading(false))
    recruitmentAgencyPortalApi.getMyInvoices(clientId).then(res => setInvoices(res.content)).finally(() => setInvLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clientId])

  const handleExpand = async (req: Requisition) => {
    if (expandedReq === req.id) { setExpandedReq(null); return }
    setExpandedReq(req.id)
    if (clientId) setPlacements(await recruitmentAgencyPortalApi.getMyPlacements(clientId, req.id))
  }

  const tabs: { id: TabId; label: string }[] = [{ id: "requisitions", label: "Requisitions" }, { id: "invoices", label: "Invoices" }]

  return (
    <PortalShell backTo="/recruitment-agency/portal">
      <div style={{ maxWidth: 780, margin: "0 auto", padding: `${space(9)} ${space(5)} ${space(12)}` }}>
        <h1 style={{ fontSize: 22, fontWeight: 800, color: color.ink, marginBottom: space(6) }}>{clientName || <span style={{ opacity: 0.3 }}>Loading…</span>}</h1>

        <div style={{ display: "flex", gap: space(1), borderBottom: `1px solid ${color.border}`, marginBottom: space(6) }}>
          {tabs.map(t => (
            <button key={t.id} onClick={() => setTab(t.id)} style={{ padding: `${space(2.5)} ${space(4)}`, background: "none", border: "none",
              borderBottom: tab === t.id ? `2px solid ${color.navy}` : "2px solid transparent", color: tab === t.id ? color.navy : color.muted,
              fontWeight: tab === t.id ? 700 : 500, fontSize: 13.5, cursor: "pointer", marginBottom: -1 }}>{t.label}</button>
          ))}
        </div>

        {tab === "requisitions" && (
          reqLoading ? <LoadingRows /> : requisitions.length === 0 ? <EmptyState icon="📋" title="No open roles yet" body="Requisitions your agency is working on will appear here." /> : (
            <div style={{ display: "flex", flexDirection: "column", gap: space(2.5) }}>
              {requisitions.map(r => (
                <div key={r.id} style={{ background: color.surface, border: `1px solid ${color.border}`, borderRadius: radius.md }}>
                  <div style={{ padding: `${space(4)} ${space(5)}`, cursor: "pointer" }} onClick={() => handleExpand(r)}>
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                      <span style={{ fontWeight: 700, fontSize: 14.5, color: color.ink }}>{r.title}</span>
                      <span style={{ background: (statusTone[r.status] ?? statusTone.PENDING).bg, color: (statusTone[r.status] ?? statusTone.PENDING).color, padding: "2px 9px", borderRadius: radius.pill, fontSize: 10.5, fontWeight: 700 }}>{r.status}</span>
                    </div>
                    <div style={{ fontSize: 12.5, color: color.faint, marginTop: space(1) }}>{r.candidateCount} candidate{r.candidateCount !== 1 ? "s" : ""} in pipeline</div>
                  </div>
                  {expandedReq === r.id && (
                    <div style={{ borderTop: `1px solid ${color.border}`, padding: space(4) }}>
                      {placements.length === 0 ? <div style={{ fontSize: 13, color: color.faint }}>No candidates submitted yet.</div> : placements.map(p => (
                        <div key={p.id} style={{ display: "flex", justifyContent: "space-between", padding: `${space(2)} 0`, borderBottom: `1px solid ${color.borderLight}` }}>
                          <span style={{ fontSize: 13, fontWeight: 600, color: color.ink }}>{p.candidateName}</span>
                          <span style={{ fontSize: 12, color: color.muted }}>{p.stage.replace(/_/g, " ")}</span>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              ))}
            </div>
          )
        )}

        {tab === "invoices" && (
          invLoading ? <LoadingRows /> : invoices.length === 0 ? <EmptyState icon="🧾" title="No invoices yet" body="Placement fee invoices will appear here once a candidate is placed." /> : (
            <div style={{ display: "flex", flexDirection: "column", gap: space(2.5) }}>
              {invoices.map(inv => (
                <div key={inv.id} style={{ padding: `${space(4)} ${space(5)}`, background: color.surface, border: `1px solid ${color.border}`, borderRadius: radius.md, display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <div>
                    <div style={{ display: "flex", alignItems: "center", gap: space(2), marginBottom: space(1) }}>
                      <span style={{ fontWeight: 700, fontSize: 14.5, color: color.ink }}>{inv.invoiceNumber}</span>
                      <span style={{ background: (statusTone[inv.status] ?? statusTone.DRAFT).bg, color: (statusTone[inv.status] ?? statusTone.DRAFT).color, padding: "2px 9px", borderRadius: radius.pill, fontSize: 10.5, fontWeight: 700 }}>{inv.status}</span>
                    </div>
                    <div style={{ fontSize: 12.5, color: color.faint }}>{inv.description}</div>
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
