// src/pages/fm-portal/FmPortalHomePage.tsx
// What a facilities client sees after signing in: where their jobs stand, quotes waiting for an answer, what they owe, and their sites.
// The server lists the client organisation(s) the login may act for; with several, the person picks one and every call carries it.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useNavigate } from "react-router-dom"
import { fmPortalApi } from "../../api/fmPortal.api"
import { usePortalAuthStore } from "../../store/portalAuth.store"
import { color, space, type } from "../accountant-portal/portal-theme"
import { portalOwing } from "../facilitiesmanagement/fm.logic"
import { btn, empty, money, panel } from "./portalKit"
import { InvoicesPanel, QuotesPanel, RequestsPanel, SitesPanel } from "./FmPortalTabs"

type Tab = "overview" | "requests" | "quotes" | "invoices" | "sites"
const TABS: [Tab, string][] = [["overview", "Overview"], ["requests", "Service requests"], ["quotes", "Quotes"], ["invoices", "Invoices"], ["sites", "Sites"]]

export function FmPortalHomePage() {
  const navigate = useNavigate()
  const user = usePortalAuthStore(s => s.user), logout = usePortalAuthStore(s => s.logout)
  const [tab, setTab] = useState<Tab>("overview"), [picked, setPicked] = useState("")
  const clients = useQuery({ queryKey: ["fm-portal-clients"], queryFn: fmPortalApi.clients })
  const list = clients.data ?? [], client = list.find(c => c.id === picked) ?? list[0], cid = client?.id
  const first = user?.fullName?.split(" ")[0]
  return (
    <div style={{ minHeight: "100vh", background: color.canvas, fontFamily: type.family }}>
      <header style={{ background: color.surface, borderBottom: `1px solid ${color.border}`, padding: `${space(4)} ${space(6)}`, display: "flex", justifyContent: "space-between", alignItems: "center", gap: space(3), flexWrap: "wrap" }}>
        <div style={{ fontWeight: 800, fontSize: 15, color: color.ink }}>Facilities Client Portal</div>
        <div style={{ display: "flex", alignItems: "center", gap: space(4) }}>
          <span style={{ fontSize: 13, color: color.muted }}>{user?.fullName}</span>
          <button style={btn()} onClick={() => { logout(); navigate("/facilitiesmanagement/portal/login", { replace: true }) }}>Log out</button>
        </div>
      </header>
      <main style={{ maxWidth: 980, margin: "0 auto", padding: `${space(7)} ${space(5)} ${space(12)}` }}>
        <h1 style={{ fontSize: 24, fontWeight: 800, color: color.ink, margin: 0 }}>Welcome back{first ? `, ${first}` : ""}</h1>
        {clients.isError ? <p style={empty}>We could not load your account. Please try again.</p>
          : clients.isLoading ? <p style={empty}>Loading your account…</p>
          : !client ? <p style={empty}>You do not have access to any client at the moment. Please contact your facilities manager.</p>
          : <>
            {list.length > 1
              ? <select aria-label="Client" value={client.id} onChange={e => setPicked(e.target.value)} style={{ margin: `${space(2)} 0`, padding: "8px 10px", border: `1px solid ${color.border}`, borderRadius: 8, fontSize: 14 }}>{list.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}</select>
              : <p style={{ fontSize: 14, color: color.muted, margin: `${space(1)} 0 ${space(4)}` }}>{client.name}</p>}
            <div role="tablist" style={{ display: "flex", gap: space(2), margin: `${space(3)} 0 ${space(4)}`, flexWrap: "wrap" }}>
              {TABS.map(([k, l]) => <button key={k} role="tab" aria-selected={tab === k} onClick={() => setTab(k)} style={btn(tab === k)}>{l}</button>)}
            </div>
            {tab === "overview" && <Overview cid={cid!} go={setTab} />}
            {tab === "requests" && <RequestsPanel cid={cid!} />}
            {tab === "quotes" && <QuotesPanel cid={cid!} />}
            {tab === "invoices" && <InvoicesPanel cid={cid!} />}
            {tab === "sites" && <SitesPanel cid={cid!} />}
          </>}
      </main>
    </div>
  )
}

function Overview({ cid, go }: { cid: string; go: (t: Tab) => void }) {
  const q = useQuery({ queryKey: ["fm-portal-summary", cid], queryFn: () => fmPortalApi.summary(cid) })
  const quotes = useQuery({ queryKey: ["fm-portal-quotes", cid], queryFn: () => fmPortalApi.quotes(cid) })
  if (q.isError) return <p style={empty}>We could not load your summary.</p>
  const s = q.data
  if (!s) return <p style={empty}>Loading…</p>
  const o = portalOwing(s)!, waiting = (quotes.data ?? []).filter(x => x.canRespond).length
  const tile = (label: string, value: string | number, note: string, tone?: string, to?: Tab) => (
    <button key={label} onClick={() => to && go(to)} style={{ ...panel, padding: `${space(4)} ${space(5)}`, textAlign: "left", cursor: to ? "pointer" : "default", font: "inherit" }}>
      <div style={{ fontSize: 11, color: color.muted, textTransform: "uppercase", fontWeight: 700 }}>{label}</div>
      <div style={{ fontSize: 24, fontWeight: 800, color: tone ?? color.ink }}>{value}</div><div style={{ fontSize: 12, color: color.muted }}>{note}</div>
    </button>)
  return (
    <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(210px, 1fr))", gap: space(3) }}>
      {tile("Open jobs", s.openWork, s.slaAtRisk ? `${s.slaAtRisk} may run late` : "None at risk", undefined, "requests")}
      {tile("Missed deadlines", s.slaBreached, s.slaBreached ? "Finished later than promised" : "All on time so far", s.slaBreached ? color.redText : undefined, "requests")}
      {tile("Quotes to answer", waiting, waiting ? "Waiting for your decision" : "Nothing waiting", waiting ? color.amberText : undefined, "quotes")}
      {tile("You owe", money(o.owing), o.overdueCount ? `${money(o.overdue)} overdue on ${o.overdueCount} invoice${o.overdueCount === 1 ? "" : "s"}` : "Nothing overdue", o.overdueCount ? color.redText : undefined, "invoices")}
    </div>
  )
}
