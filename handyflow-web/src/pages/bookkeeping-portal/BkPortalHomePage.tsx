// src/pages/bookkeeping-portal/BkPortalHomePage.tsx
// What a bookkeeping client sees after signing in: what they owe and what has been paid, and the time logged for them.
// The server works out which client(s) the login belongs to; nothing here picks a client.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useNavigate } from "react-router-dom"
import { bookkeepingPortalApi } from "../../api/bookkeepingPortal.api"
import { usePortalAuthStore } from "../../store/portalAuth.store"
import { color, radius, shadow, space, type } from "../accountant-portal/portal-theme"

const money = (n: number | null | undefined) => (n == null ? "—" : `R ${n.toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`)
const day = (d: string | null | undefined) => (d ? new Date(d.length === 10 ? d + "T00:00:00" : d).toLocaleDateString("en-ZA", { day: "2-digit", month: "short", year: "numeric" }) : "—")
const STATUS: Record<string, { label: string; tone: "green" | "amber" | "red" | "grey" }> = {
  DRAFT: { label: "Draft", tone: "grey" }, SENT: { label: "Sent", tone: "amber" }, PARTIALLY_PAID: { label: "Part paid", tone: "amber" },
  PAID: { label: "Paid", tone: "green" }, OVERDUE: { label: "Overdue", tone: "red" }, VOID: { label: "Cancelled", tone: "grey" }, CANCELLED: { label: "Cancelled", tone: "grey" },
}
const TONE = { green: [color.greenBg, color.greenText], amber: [color.amberBg, color.amberText], red: [color.redBg, color.redText], grey: ["var(--hf-surface-sunken)", color.muted] } as const

function Chip({ status }: { status: string }) {
  const s = STATUS[status] ?? { label: status.replace(/_/g, " ").toLowerCase(), tone: "grey" as const }
  const [bg, fg] = TONE[s.tone]
  return <span style={{ background: bg, color: fg, fontSize: 11.5, fontWeight: 700, padding: "2px 9px", borderRadius: 20, whiteSpace: "nowrap" }}>{s.label}</span>
}

export function BkPortalHomePage() {
  const navigate = useNavigate()
  const user = usePortalAuthStore(s => s.user)
  const logout = usePortalAuthStore(s => s.logout)
  const [tab, setTab] = useState<"invoices" | "time">("invoices")
  const [page, setPage] = useState(0)
  const invoices = useQuery({ queryKey: ["bk-portal-invoices"], queryFn: bookkeepingPortalApi.invoices })
  const time = useQuery({ queryKey: ["bk-portal-time", page], queryFn: () => bookkeepingPortalApi.timeEntries(page), enabled: tab === "time" })
  const owing = (invoices.data ?? []).filter(i => !["PAID", "VOID", "CANCELLED", "DRAFT"].includes(i.status)).reduce((s, i) => s + (i.balance ?? 0), 0)
  const first = user?.fullName?.split(" ")[0]

  return (
    <div style={{ minHeight: "100vh", background: color.canvas, fontFamily: type.family }}>
      <header style={{ background: color.surface, borderBottom: `1px solid ${color.border}`, padding: `${space(4)} ${space(6)}`, display: "flex", justifyContent: "space-between", alignItems: "center", gap: space(3), flexWrap: "wrap" }}>
        <div style={{ fontWeight: 800, fontSize: 15, color: color.ink }}>Bookkeeping Portal</div>
        <div style={{ display: "flex", alignItems: "center", gap: space(4) }}>
          <span style={{ fontSize: 13, color: color.muted }}>{user?.fullName}</span>
          <button onClick={() => { logout(); navigate("/bookkeeping/portal/login", { replace: true }) }}
            style={{ padding: `${space(1.5)} ${space(3)}`, border: `1px solid ${color.border}`, borderRadius: 8, background: color.surface, fontSize: 12, fontWeight: 600, cursor: "pointer", color: color.slate }}>Log out</button>
        </div>
      </header>
      <main style={{ maxWidth: 820, margin: "0 auto", padding: `${space(8)} ${space(5)} ${space(12)}` }}>
        <h1 style={{ fontSize: 24, fontWeight: 800, color: color.ink, margin: 0 }}>Welcome back{first ? `, ${first}` : ""}</h1>
        <p style={{ fontSize: 14, color: color.muted, margin: `${space(1)} 0 ${space(6)}` }}>
          {invoices.isLoading ? "Loading your account…" : owing > 0 ? `You currently owe ${money(owing)}.` : "Nothing is outstanding on your account."}
        </p>

        <div role="tablist" style={{ display: "flex", gap: space(2), marginBottom: space(4) }}>
          {([["invoices", "Invoices"], ["time", "Time logged"]] as const).map(([id, label]) => (
            <button key={id} role="tab" aria-selected={tab === id} onClick={() => setTab(id)}
              style={{ padding: `${space(2)} ${space(4)}`, borderRadius: 8, border: `1px solid ${tab === id ? color.navy : color.border}`, background: tab === id ? color.navy : color.surface, color: tab === id ? "var(--hf-text-on-solid)" : color.slate, fontWeight: 600, fontSize: 13, cursor: "pointer" }}>{label}</button>
          ))}
        </div>

        <div style={{ background: color.surface, border: `1px solid ${color.border}`, borderRadius: radius.md, boxShadow: shadow.card, overflowX: "auto" }}>
          {tab === "invoices" && (
            invoices.isError ? <p style={empty}>We could not load your invoices. Please try again.</p>
            : (invoices.data ?? []).length === 0 && !invoices.isLoading ? <p style={empty}>No invoices yet.</p>
            : <table style={table}>
                <thead><tr><th style={th}>Invoice</th><th style={th}>Period</th><th style={th}>Due</th><th style={{ ...th, textAlign: "right" }}>Total</th><th style={{ ...th, textAlign: "right" }}>Balance</th><th style={th}>Status</th></tr></thead>
                <tbody>{(invoices.data ?? []).map(i => (
                  <tr key={i.id}>
                    <td style={td}><b>{i.invoiceNumber}</b><div style={sub}>Issued {day(i.issueDate)}</div></td>
                    <td style={td}>{i.periodStart ? `${day(i.periodStart)} – ${day(i.periodEnd)}` : "—"}</td>
                    <td style={td}>{day(i.dueDate)}</td>
                    <td style={{ ...td, textAlign: "right" }}>{money(i.total)}<div style={sub}>VAT {money(i.vatAmount)}</div></td>
                    <td style={{ ...td, textAlign: "right", fontWeight: 700 }}>{money(i.balance)}</td>
                    <td style={td}><Chip status={i.status} /></td>
                  </tr>))}</tbody>
              </table>
          )}
          {tab === "time" && (
            time.isError ? <p style={empty}>We could not load the time entries. Please try again.</p>
            : (time.data?.content ?? []).length === 0 && !time.isLoading ? <p style={empty}>No time has been logged for you yet.</p>
            : <>
                <table style={table}>
                  <thead><tr><th style={th}>Date</th><th style={th}>Work</th><th style={th}>By</th><th style={{ ...th, textAlign: "right" }}>Hours</th><th style={{ ...th, textAlign: "right" }}>Amount</th><th style={th}>Billed</th></tr></thead>
                  <tbody>{(time.data?.content ?? []).map(t => (
                    <tr key={t.id}>
                      <td style={td}>{day(t.entryDate)}</td>
                      <td style={td}>{t.activityType.replace(/_/g, " ").toLowerCase()}{t.description && <div style={sub}>{t.description}</div>}</td>
                      <td style={td}>{t.practitionerName ?? "—"}</td>
                      <td style={{ ...td, textAlign: "right" }}>{t.hours}</td>
                      <td style={{ ...td, textAlign: "right" }}>{t.billable ? money(t.lineTotal) : "No charge"}</td>
                      <td style={td}>{t.invoiceId ? "Yes" : t.billable ? "Not yet" : "—"}</td>
                    </tr>))}</tbody>
                </table>
                {(time.data?.totalPages ?? 0) > 1 && (
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: space(3) }}>
                    <button disabled={page === 0} onClick={() => setPage(p => p - 1)} style={pager}>← Newer</button>
                    <span style={{ fontSize: 12, color: color.muted }}>Page {page + 1} of {time.data?.totalPages}</span>
                    <button disabled={page + 1 >= (time.data?.totalPages ?? 1)} onClick={() => setPage(p => p + 1)} style={pager}>Older →</button>
                  </div>)}
              </>
          )}
        </div>
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
