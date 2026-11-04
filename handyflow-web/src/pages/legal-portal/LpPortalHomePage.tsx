// src/pages/legal-portal/LpPortalHomePage.tsx
// What a law-firm client sees after signing in: where their matters stand, what they owe, and what the firm holds for them in trust.
// The server lists the client record(s) the login has been given access to; if there are several, the person picks one.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useNavigate } from "react-router-dom"
import { legalPortalApi } from "../../api/legalPortal.api"
import { usePortalAuthStore } from "../../store/portalAuth.store"
import { color, radius, shadow, space, type } from "../accountant-portal/portal-theme"

const money = (n: number | null | undefined) => (n == null ? "—" : `R ${n.toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`)
const day = (d: string | null | undefined) => (d ? new Date(d.length === 10 ? d + "T00:00:00" : d).toLocaleDateString("en-ZA", { day: "2-digit", month: "short", year: "numeric" }) : "—")
const STATUS: Record<string, { label: string; tone: "green" | "amber" | "red" | "grey" }> = {
  DRAFT: { label: "Draft", tone: "grey" }, SENT: { label: "Sent", tone: "amber" }, PARTIALLY_PAID: { label: "Part paid", tone: "amber" },
  PAID: { label: "Paid", tone: "green" }, OVERDUE: { label: "Overdue", tone: "red" }, WRITTEN_OFF: { label: "Written off", tone: "grey" }, CANCELLED: { label: "Cancelled", tone: "grey" },
  OPEN: { label: "Open", tone: "green" }, ON_HOLD: { label: "On hold", tone: "amber" }, CLOSED: { label: "Closed", tone: "grey" }, ARCHIVED: { label: "Archived", tone: "grey" },
}
const TONE = { green: [color.greenBg, color.greenText], amber: [color.amberBg, color.amberText], red: [color.redBg, color.redText], grey: ["var(--hf-surface-sunken)", color.muted] } as const

function Chip({ status }: { status: string }) {
  const s = STATUS[status] ?? { label: status.replace(/_/g, " ").toLowerCase(), tone: "grey" as const }
  const [bg, fg] = TONE[s.tone]
  return <span style={{ background: bg, color: fg, fontSize: 11.5, fontWeight: 700, padding: "2px 9px", borderRadius: 20, whiteSpace: "nowrap" }}>{s.label}</span>
}

export function LpPortalHomePage() {
  const navigate = useNavigate()
  const user = usePortalAuthStore(s => s.user)
  const logout = usePortalAuthStore(s => s.logout)
  const [tab, setTab] = useState<"matters" | "invoices">("matters")
  const [picked, setPicked] = useState<string>("")
  const [mPage, setMPage] = useState(0), [iPage, setIPage] = useState(0)
  const clients = useQuery({ queryKey: ["lp-portal-clients"], queryFn: legalPortalApi.clients })
  const list = clients.data ?? []
  const client = list.find(c => c.id === picked) ?? list[0]
  const cid = client?.id
  const matters = useQuery({ queryKey: ["lp-portal-matters", cid, mPage], queryFn: () => legalPortalApi.matters(cid!, mPage), enabled: !!cid })
  const invoices = useQuery({ queryKey: ["lp-portal-invoices", cid, iPage], queryFn: () => legalPortalApi.invoices(cid!, iPage), enabled: !!cid })
  const owing = (invoices.data?.content ?? []).filter(i => !["PAID", "WRITTEN_OFF", "CANCELLED", "DRAFT"].includes(i.status)).reduce((s, i) => s + (i.outstandingBalance ?? 0), 0)
  const first = user?.fullName?.split(" ")[0]
  const pages = tab === "matters" ? matters.data?.totalPages ?? 0 : invoices.data?.totalPages ?? 0
  const page = tab === "matters" ? mPage : iPage
  const setPage = tab === "matters" ? setMPage : setIPage

  return (
    <div style={{ minHeight: "100vh", background: color.canvas, fontFamily: type.family }}>
      <header style={{ background: color.surface, borderBottom: `1px solid ${color.border}`, padding: `${space(4)} ${space(6)}`, display: "flex", justifyContent: "space-between", alignItems: "center", gap: space(3), flexWrap: "wrap" }}>
        <div style={{ fontWeight: 800, fontSize: 15, color: color.ink }}>Legal Portal</div>
        <div style={{ display: "flex", alignItems: "center", gap: space(4) }}>
          <span style={{ fontSize: 13, color: color.muted }}>{user?.fullName}</span>
          <button onClick={() => { logout(); navigate("/legal-practice/portal/login", { replace: true }) }}
            style={{ padding: `${space(1.5)} ${space(3)}`, border: `1px solid ${color.border}`, borderRadius: 8, background: color.surface, fontSize: 12, fontWeight: 600, cursor: "pointer", color: color.slate }}>Log out</button>
        </div>
      </header>
      <main style={{ maxWidth: 820, margin: "0 auto", padding: `${space(8)} ${space(5)} ${space(12)}` }}>
        <h1 style={{ fontSize: 24, fontWeight: 800, color: color.ink, margin: 0 }}>Welcome back{first ? `, ${first}` : ""}</h1>
        {clients.isError ? <p style={{ ...empty, textAlign: "left", padding: `${space(2)} 0` }}>We could not load your account. Please try again.</p>
          : clients.isLoading ? <p style={{ ...empty, textAlign: "left", padding: `${space(2)} 0` }}>Loading your account…</p>
          : !client ? <p style={{ ...empty, textAlign: "left", padding: `${space(2)} 0` }}>You do not have access to any client record at the moment. Please contact the firm.</p>
          : <>
            {list.length > 1 && (
              <select aria-label="Client" value={client.id} onChange={e => { setPicked(e.target.value); setMPage(0); setIPage(0) }} style={{ margin: `${space(2)} 0`, padding: "8px 10px", border: `1px solid ${color.border}`, borderRadius: 8, fontSize: 13 }}>
                {list.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>)}
            <p style={{ fontSize: 14, color: color.muted, margin: `${space(1)} 0 ${space(4)}` }}>
              {client.name}{invoices.isSuccess ? (owing > 0 ? ` · you currently owe ${money(owing)}.` : " · nothing is outstanding on your account.") : ""}
            </p>
            <div style={{ display: "flex", gap: space(3), flexWrap: "wrap", marginBottom: space(5) }}>
              <div style={{ background: color.surface, border: `1px solid ${color.border}`, borderRadius: radius.md, padding: `${space(3)} ${space(5)}`, boxShadow: shadow.card }}>
                <div style={{ fontSize: 11, color: color.muted, textTransform: "uppercase", fontWeight: 700 }}>Held for you in trust</div>
                <div style={{ fontSize: 20, fontWeight: 800, color: color.ink }}>{money(client.trustBalance)}</div>
              </div>
            </div>

            <div role="tablist" style={{ display: "flex", gap: space(2), marginBottom: space(4) }}>
              {([["matters", "Your matters"], ["invoices", "Invoices"]] as const).map(([id, label]) => (
                <button key={id} role="tab" aria-selected={tab === id} onClick={() => setTab(id)}
                  style={{ padding: `${space(2)} ${space(4)}`, borderRadius: 8, border: `1px solid ${tab === id ? color.navy : color.border}`, background: tab === id ? color.navy : color.surface, color: tab === id ? "var(--hf-text-on-solid)" : color.slate, fontWeight: 600, fontSize: 13, cursor: "pointer" }}>{label}</button>
              ))}
            </div>

            <div style={{ background: color.surface, border: `1px solid ${color.border}`, borderRadius: radius.md, boxShadow: shadow.card, overflowX: "auto" }}>
              {tab === "matters" && (
                matters.isError ? <p style={empty}>We could not load your matters. Please try again.</p>
                : (matters.data?.content ?? []).length === 0 && !matters.isLoading ? <p style={empty}>No matters yet.</p>
                : <table style={table}>
                    <thead><tr><th style={th}>Matter</th><th style={th}>Type</th><th style={th}>Opened</th><th style={th}>Status</th></tr></thead>
                    <tbody>{(matters.data?.content ?? []).map(m => (
                      <tr key={m.id}>
                        <td style={td}><b>{m.matterName}</b><div style={sub}>{m.matterNumber}</div></td>
                        <td style={td}>{m.matterType.replace(/_/g, " ").toLowerCase()}</td>
                        <td style={td}>{day(m.openedDate)}{m.closedDate ? <div style={sub}>Closed {day(m.closedDate)}</div> : null}</td>
                        <td style={td}><Chip status={m.status} /></td>
                      </tr>))}</tbody>
                  </table>
              )}
              {tab === "invoices" && (
                invoices.isError ? <p style={empty}>We could not load your invoices. Please try again.</p>
                : (invoices.data?.content ?? []).length === 0 && !invoices.isLoading ? <p style={empty}>No invoices yet.</p>
                : <table style={table}>
                    <thead><tr><th style={th}>Invoice</th><th style={th}>Due</th><th style={{ ...th, textAlign: "right" }}>Total</th><th style={{ ...th, textAlign: "right" }}>Owing</th><th style={th}>Status</th></tr></thead>
                    <tbody>{(invoices.data?.content ?? []).map(i => (
                      <tr key={i.id}>
                        <td style={td}><b>{i.invoiceNumber}</b><div style={sub}>{i.description ? `${i.description} · ` : ""}Issued {day(i.issueDate)}</div></td>
                        <td style={td}>{day(i.dueDate)}</td>
                        <td style={{ ...td, textAlign: "right" }}>{money(i.totalAmount)}<div style={sub}>VAT {money(i.vatAmount)}</div></td>
                        <td style={{ ...td, textAlign: "right", fontWeight: 700 }}>{money(i.outstandingBalance)}</td>
                        <td style={td}><Chip status={i.status} /></td>
                      </tr>))}</tbody>
                  </table>
              )}
              {pages > 1 && (
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: space(3) }}>
                  <button disabled={page === 0} onClick={() => setPage(p => p - 1)} style={pager}>← Newer</button>
                  <span style={{ fontSize: 12, color: color.muted }}>Page {page + 1} of {pages}</span>
                  <button disabled={page + 1 >= pages} onClick={() => setPage(p => p + 1)} style={pager}>Older →</button>
                </div>)}
            </div>
          </>}
      </main>
    </div>
  )
}

const table: React.CSSProperties = { width: "100%", borderCollapse: "collapse", fontSize: 13 }
const th: React.CSSProperties = { textAlign: "left", padding: "10px 12px", fontSize: 11, color: color.muted, textTransform: "uppercase", borderBottom: `1px solid ${color.border}`, whiteSpace: "nowrap" }
const td: React.CSSProperties = { padding: "10px 12px", borderBottom: `1px solid ${color.border}`, color: color.ink, verticalAlign: "top" }
const sub: React.CSSProperties = { fontSize: 11.5, color: color.muted, marginTop: 2 }
const empty: React.CSSProperties = { padding: space(8), textAlign: "center", color: color.muted, fontSize: 13.5, margin: 0 }
const pager: React.CSSProperties = { padding: "6px 12px", border: `1px solid ${color.border}`, borderRadius: 8, background: color.surface, fontSize: 12, fontWeight: 600, cursor: "pointer", color: color.slate }
