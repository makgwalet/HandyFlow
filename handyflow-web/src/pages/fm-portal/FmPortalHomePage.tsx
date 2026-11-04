// src/pages/fm-portal/FmPortalHomePage.tsx
// What a facilities client sees after signing in: where their jobs stand, what needs their answer or payment, and their sites.
// The server lists the client organisation(s) the login may act for; with several, the person picks one and every call carries it.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useNavigate } from "react-router-dom"
import { Building2, CheckCircle2, ChevronRight, Clock, FileText, LogOut, Wallet, Wrench } from "lucide-react"
import { fmPortalApi } from "../../api/fmPortal.api"
import { usePortalAuthStore } from "../../store/portalAuth.store"
import { color, space, type } from "../accountant-portal/portal-theme"
import { PORTAL_STATUS, dueNote, portalOwing } from "../facilitiesmanagement/fm.logic"
import { Chip, btn, day, empty, greetingName, initialOf, money, panel, sub } from "./portalKit"
import { InvoicesPanel, QuotesPanel, RequestsPanel, SitesPanel } from "./FmPortalTabs"

type Tab = "overview" | "requests" | "quotes" | "invoices" | "sites"
const TABS: [Tab, string][] = [["overview", "Overview"], ["requests", "Service requests"], ["quotes", "Quotes"], ["invoices", "Invoices"], ["sites", "Sites"]]

export function FmPortalHomePage() {
  const navigate = useNavigate()
  const user = usePortalAuthStore(s => s.user), logout = usePortalAuthStore(s => s.logout)
  const [tab, setTab] = useState<Tab>("overview"), [picked, setPicked] = useState("")
  const clients = useQuery({ queryKey: ["fm-portal-clients"], queryFn: fmPortalApi.clients })
  const list = clients.data ?? [], client = list.find(c => c.id === picked) ?? list[0], cid = client?.id
  const first = greetingName(user?.fullName)
  const quotes = useQuery({ queryKey: ["fm-portal-quotes", cid], queryFn: () => fmPortalApi.quotes(cid!), enabled: !!cid })
  const summary = useQuery({ queryKey: ["fm-portal-summary", cid], queryFn: () => fmPortalApi.summary(cid!), enabled: !!cid })
  const waiting = (quotes.data ?? []).filter(x => x.canRespond).length
  const badge: Partial<Record<Tab, number>> = { quotes: waiting, requests: summary.data?.openWork ?? 0, invoices: summary.data?.overdueInvoices ?? 0 }
  return (
    <div style={{ minHeight: "100vh", background: color.canvas, fontFamily: type.family }}>
      <header style={{ background: color.surface, borderBottom: `1px solid ${color.border}`, padding: `${space(3)} ${space(6)}`, display: "flex", justifyContent: "space-between", alignItems: "center", gap: space(3), flexWrap: "wrap" }}>
        <div style={{ display: "flex", alignItems: "center", gap: space(3) }}>
          <div style={{ width: 34, height: 34, borderRadius: 10, background: `linear-gradient(135deg, ${color.navy}, ${color.navyDark})`, display: "flex", alignItems: "center", justifyContent: "center", color: "var(--hf-text-on-solid)", fontWeight: 800, fontSize: 16 }}>H</div>
          <div><div style={{ fontWeight: 800, fontSize: 15, color: color.ink, lineHeight: 1.2 }}>Client Portal</div><div style={{ fontSize: 11.5, color: color.muted }}>Facilities management</div></div>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: space(3) }}>
          <div aria-hidden style={{ width: 32, height: 32, borderRadius: "50%", background: "var(--hf-primary-soft, var(--hf-surface-sunken))", color: color.navyText, display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 800, fontSize: 13 }}>{initialOf(user?.fullName)}</div>
          <span style={{ fontSize: 13, color: color.slate }}>{first || user?.email}</span>
          <button style={{ ...btn(), display: "flex", alignItems: "center", gap: 6 }} onClick={() => { logout(); navigate("/facilitiesmanagement/portal/login", { replace: true }) }}><LogOut size={14} />Log out</button>
        </div>
      </header>

      <section style={{ background: `linear-gradient(135deg, ${color.navy}, ${color.navyDark})`, color: "var(--hf-text-on-solid)" }}>
        <div style={{ maxWidth: 1040, margin: "0 auto", padding: `${space(8)} ${space(5)} ${space(7)}` }}>
          <h1 style={{ fontSize: 26, fontWeight: 800, margin: 0, letterSpacing: "-0.02em" }}>Welcome back{first ? `, ${first}` : ""}</h1>
          {client && (list.length > 1
            ? <select aria-label="Client" value={client.id} onChange={e => setPicked(e.target.value)} style={{ marginTop: space(3), padding: "8px 10px", border: "none", borderRadius: 8, fontSize: 14, color: color.ink }}>{list.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}</select>
            : <div style={{ marginTop: space(2), display: "inline-flex", alignItems: "center", gap: 8, fontSize: 14, opacity: 0.9 }}><Building2 size={15} />{client.name}</div>)}
        </div>
      </section>

      <nav role="tablist" aria-label="Portal sections" style={{ background: color.surface, borderBottom: `1px solid ${color.border}` }}>
        <div style={{ maxWidth: 1040, margin: "0 auto", padding: `0 ${space(5)}`, display: "flex", gap: space(1), overflowX: "auto" }}>
          {TABS.map(([k, l]) => {
            const on = tab === k, n = badge[k] ?? 0
            return <button key={k} role="tab" aria-selected={on} onClick={() => setTab(k)} style={{ background: "none", border: "none", borderBottom: `3px solid ${on ? color.navy : "transparent"}`, padding: "14px 14px 11px", fontSize: 14, fontWeight: on ? 800 : 600, color: on ? color.navyText : color.slate, cursor: "pointer", whiteSpace: "nowrap", display: "flex", alignItems: "center", gap: 7, fontFamily: "inherit" }}>
              {l}{n > 0 && k !== "requests" && <span style={{ background: k === "invoices" ? color.redBg : color.amberBg, color: k === "invoices" ? color.redText : color.amberText, borderRadius: 20, fontSize: 11, fontWeight: 800, padding: "1px 7px" }}>{n}</span>}
            </button>
          })}
        </div>
      </nav>

      <main style={{ maxWidth: 1040, margin: "0 auto", padding: `${space(6)} ${space(5)} ${space(12)}` }}>
        {clients.isError ? <p style={empty}>We could not load your account. Please try again.</p>
          : clients.isLoading ? <p style={empty}>Loading your account…</p>
          : !client ? <p style={empty}>You do not have access to any client at the moment. Please contact your facilities manager.</p>
          : <>
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
  const invoices = useQuery({ queryKey: ["fm-portal-invoices", cid], queryFn: () => fmPortalApi.invoices(cid) })
  const orders = useQuery({ queryKey: ["fm-portal-orders", cid, 0], queryFn: () => fmPortalApi.orders(cid, 0) })
  if (q.isError) return <p style={empty}>We could not load your summary.</p>
  const s = q.data
  if (!s) return <p style={empty}>Loading…</p>
  const o = portalOwing(s)!, today = new Date().toISOString().slice(0, 10)
  const toAnswer = (quotes.data ?? []).filter(x => x.canRespond)
  const toPay = (invoices.data ?? []).filter(i => i.balance > 0 && !["DRAFT", "CANCELLED", "VOID"].includes(i.status)).sort((a, b) => (a.dueDate ?? "9").localeCompare(b.dueDate ?? "9"))
  const recent = (orders.data?.content ?? []).slice(0, 4)

  const tile = (label: string, value: string | number, note: string, Icon: typeof Wrench, tone: "ok" | "warn" | "bad", to: Tab) => {
    const [bg, fg] = tone === "bad" ? [color.redBg, color.redText] : tone === "warn" ? [color.amberBg, color.amberText] : [color.blueBg, color.blueText]
    return (
      <button key={label} onClick={() => go(to)} style={{ ...panel, padding: `${space(4)} ${space(5)}`, textAlign: "left", cursor: "pointer", font: "inherit", display: "flex", gap: space(4), alignItems: "center" }}>
        <span style={{ width: 42, height: 42, borderRadius: 12, background: bg, color: fg, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}><Icon size={20} /></span>
        <span style={{ minWidth: 0 }}>
          <span style={{ display: "block", fontSize: 11, color: color.muted, textTransform: "uppercase", letterSpacing: "0.04em", fontWeight: 700 }}>{label}</span>
          <span style={{ display: "block", fontSize: 22, fontWeight: 800, color: tone === "ok" ? color.ink : fg, lineHeight: 1.25, whiteSpace: "nowrap" }}>{value}</span>
          <span style={{ display: "block", fontSize: 12, color: color.muted }}>{note}</span>
        </span>
      </button>)
  }
  const row = (key: string, Icon: typeof Wrench, title: string, note: string, action: string, to: Tab, chip?: React.ReactNode) => (
    <button key={key} onClick={() => go(to)} style={{ display: "flex", alignItems: "center", gap: space(3), width: "100%", textAlign: "left", background: "none", border: "none", borderTop: `1px solid ${color.borderLight}`, padding: `${space(3)} ${space(5)}`, cursor: "pointer", font: "inherit" }}>
      <Icon size={18} color="var(--hf-text-muted)" />
      <span style={{ flex: 1, minWidth: 0 }}><span style={{ display: "block", fontSize: 14, fontWeight: 700, color: color.ink }}>{title}</span><span style={{ display: "block", ...sub }}>{note}</span></span>
      {chip}<span style={{ fontSize: 13, fontWeight: 700, color: color.navyText, display: "flex", alignItems: "center", gap: 2, whiteSpace: "nowrap" }}>{action}<ChevronRight size={15} /></span>
    </button>)
  const head = (title: string, to?: Tab) => (
    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: `${space(4)} ${space(5)} ${space(3)}` }}>
      <span style={{ fontWeight: 800, color: color.ink, fontSize: 15 }}>{title}</span>
      {to && <button onClick={() => go(to)} style={{ background: "none", border: "none", color: color.navyText, fontSize: 13, fontWeight: 700, cursor: "pointer", fontFamily: "inherit" }}>See all</button>}
    </div>)
  const attention = toAnswer.length + toPay.length

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: space(5) }}>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(230px, 1fr))", gap: space(3) }}>
        {tile("Open jobs", s.openWork, s.slaAtRisk ? `${s.slaAtRisk} may run late` : "None at risk", Wrench, s.slaAtRisk ? "warn" : "ok", "requests")}
        {tile("Missed deadlines", s.slaBreached, s.slaBreached ? "Finished later than promised" : "All on time so far", Clock, s.slaBreached ? "bad" : "ok", "requests")}
        {tile("Quotes to answer", toAnswer.length, toAnswer.length ? "Waiting for your decision" : "Nothing waiting", FileText, toAnswer.length ? "warn" : "ok", "quotes")}
        {tile("You owe", money(o.owing), o.overdueCount ? `${money(o.overdue)} overdue on ${o.overdueCount} invoice${o.overdueCount === 1 ? "" : "s"}` : "Nothing overdue", Wallet, o.overdueCount ? "bad" : "ok", "invoices")}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(360px, 1fr))", gap: space(4), alignItems: "start" }}>
        <div style={panel}>
          {head("Needs your attention")}
          {attention === 0
            ? <div style={{ ...empty, borderTop: `1px solid ${color.borderLight}`, display: "flex", flexDirection: "column", alignItems: "center", gap: 6 }}><CheckCircle2 size={26} color="var(--hf-success-solid-strong)" />You are all caught up. Nothing needs your answer or payment.</div>
            : <>
              {toAnswer.map(x => row("q" + x.id, FileText, `${x.quoteNumber} · ${x.title}`, `${money(x.total)}${x.validUntil ? ` · valid until ${day(x.validUntil)}` : ""}`, "Review", "quotes"))}
              {toPay.map(i => { const d = dueNote(i.status, i.balance, i.dueDate, today); return row("i" + i.id, Wallet, `${i.invoiceNumber} · ${money(i.balance)} owing`, i.dueDate ? `Due ${day(i.dueDate)}` : "No due date", "View", "invoices", d ? <Chip tone={d.tone}>{d.text}</Chip> : undefined) })}
            </>}
        </div>
        <div style={panel}>
          {head("Recent requests", "requests")}
          {recent.length === 0
            ? <div style={{ ...empty, borderTop: `1px solid ${color.borderLight}` }}>You have not reported anything yet. Use “Service requests” to report a problem.</div>
            : recent.map(w => { const st = PORTAL_STATUS[w.status] ?? { label: w.status, tone: "neutral" as const }
              return row("w" + w.id, Wrench, `${w.workOrderNumber} · ${w.description.length > 60 ? w.description.slice(0, 57) + "…" : w.description}`, day(w.createdAt), "", "requests", <Chip tone={st.tone}>{st.label}</Chip>) })}
        </div>
      </div>
    </div>
  )
}
