// src/pages/fm-portal/FmPortalHomePage.tsx
// What a facilities client sees after signing in: where their jobs stand, what needs their answer or payment, and their sites.
// The server lists the client organisation(s) the login may act for; with several, the person picks one and every call carries it.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useNavigate } from "react-router-dom"
import { CheckCircle2, ChevronRight, Clock, FileText, MapPin, Receipt, Wrench } from "lucide-react"
import { fmPortalApi } from "../../api/fmPortal.api"
import { usePortalAuthStore } from "../../store/portalAuth.store"
import { color, space } from "../accountant-portal/portal-theme"
import { addressLine, dueNote } from "../facilitiesmanagement/fm.logic"
import { Chip, Title, day, empty, greetingName, money, panel, sub } from "./portalKit"
import { FmPortalShell, type Section } from "./FmPortalShell"
import { Donut } from "./Donut"
import { activityFeed, nextInvoiceDue, owingInvoices, statusSlices } from "./portalStats"
import { InvoicesPanel, QuotesPanel, RequestsPanel, SitesPanel } from "./FmPortalTabs"
import { ProfilePanel, SupportPanel } from "./FmPortalHelp"

export function FmPortalHomePage() {
  const navigate = useNavigate()
  const user = usePortalAuthStore(s => s.user), logout = usePortalAuthStore(s => s.logout)
  const [section, setSection] = useState<Section>("dashboard"), [picked, setPicked] = useState("")
  const clients = useQuery({ queryKey: ["fm-portal-clients"], queryFn: fmPortalApi.clients })
  const list = clients.data ?? [], client = list.find(c => c.id === picked) ?? list[0], cid = client?.id
  const quotes = useQuery({ queryKey: ["fm-portal-quotes", cid], queryFn: () => fmPortalApi.quotes(cid!), enabled: !!cid })
  const summary = useQuery({ queryKey: ["fm-portal-summary", cid], queryFn: () => fmPortalApi.summary(cid!), enabled: !!cid })
  const waiting = (quotes.data ?? []).filter(x => x.canRespond).length
  const first = greetingName(user?.fullName)
  const signOut = () => { logout(); navigate("/facilitiesmanagement/portal/login", { replace: true }) }
  const pick = (id: string) => { setPicked(id) }
  return (
    <FmPortalShell section={section} go={setSection} userName={first || user?.email || ""} clientName={client?.name ?? ""} clients={list} clientId={cid ?? ""}
      onClient={pick} onLogout={signOut} badges={{ quotes: waiting, invoices: summary.data?.overdueInvoices ?? 0 }}>
      {clients.isError ? <p style={empty}>We could not load your account. Please try again.</p>
        : clients.isLoading ? <p style={empty}>Loading your account…</p>
        : !client ? <p style={empty}>You do not have access to any client at the moment. Please contact your facilities manager.</p>
        : <>
          {section === "dashboard" && <Dashboard cid={cid!} first={first} go={setSection} />}
          {section === "requests" && <><Title text="Service requests" note="Report a problem and follow it until it is fixed." /><RequestsPanel cid={cid!} /></>}
          {section === "quotes" && <><Title text="Quotes" note="Quotes the team has sent you to accept or decline." /><QuotesPanel cid={cid!} /></>}
          {section === "invoices" && <><Title text="Invoices & payments" note="What you have been billed and what is still owing." /><InvoicesPanel cid={cid!} /></>}
          {section === "sites" && <><Title text="Sites" note="The places we look after for you." /><SitesPanel cid={cid!} /></>}
          {section === "profile" && <ProfilePanel name={user?.fullName ?? ""} email={user?.email ?? ""} clients={list} onSignOut={signOut} />}
          {section === "support" && <SupportPanel go={setSection} />}
        </>}
    </FmPortalShell>
  )
}

function Dashboard({ cid, first, go }: { cid: string; first: string; go: (s: Section) => void }) {
  const q = useQuery({ queryKey: ["fm-portal-summary", cid], queryFn: () => fmPortalApi.summary(cid) })
  const quotes = useQuery({ queryKey: ["fm-portal-quotes", cid], queryFn: () => fmPortalApi.quotes(cid) })
  const invoices = useQuery({ queryKey: ["fm-portal-invoices", cid], queryFn: () => fmPortalApi.invoices(cid) })
  const orders = useQuery({ queryKey: ["fm-portal-orders", cid, 0], queryFn: () => fmPortalApi.orders(cid, 0) })
  const sites = useQuery({ queryKey: ["fm-portal-sites", cid], queryFn: () => fmPortalApi.sites(cid) })
  if (q.isError) return <p style={empty}>We could not load your summary.</p>
  const s = q.data
  if (!s) return <p style={empty}>Loading…</p>
  const today = new Date().toISOString().slice(0, 10)
  const { slices, total } = statusSlices(s.workOrders)
  const count = (k: string) => slices.find(x => x.key === k)?.count ?? 0
  const toAnswer = (quotes.data ?? []).filter(x => x.canRespond)
  const owing = owingInvoices(invoices.data ?? []), next = nextInvoiceDue(invoices.data ?? [])
  const orderRows = orders.data?.content ?? []
  const feed = activityFeed(orderRows, quotes.data ?? [], invoices.data ?? [])
  const siteRows = (sites.data?.content ?? []).filter(x => x.status === "ACTIVE").slice(0, 5)
  const perSite = (id: string) => orderRows.filter(w => w.siteId === id).length

  const tile = (label: string, value: string | number, note: string, Icon: typeof Wrench, tone: "info" | "warn" | "good" | "bad", to: Section) => {
    const [bg, fg] = tone === "bad" ? [color.redBg, color.redText] : tone === "warn" ? [color.amberBg, color.amberText] : tone === "good" ? [color.greenBg, color.greenText] : [color.blueBg, color.blueText]
    return (
      <button key={label} onClick={() => go(to)} style={{ ...panel, padding: `${space(4)} ${space(3)}`, textAlign: "left", cursor: "pointer", font: "inherit", display: "flex", gap: space(3), alignItems: "center" }}>
        <span style={{ width: 38, height: 38, borderRadius: 12, background: bg, color: fg, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}><Icon size={19} /></span>
        <span style={{ minWidth: 0 }}>
          <span style={{ display: "block", fontSize: 11, color: color.muted, textTransform: "uppercase", letterSpacing: "0.04em", fontWeight: 700 }}>{label}</span>
          <span style={{ display: "block", fontSize: 18, fontWeight: 800, color: color.ink, lineHeight: 1.25, whiteSpace: "nowrap" }}>{value}</span>
          <span style={{ display: "block", fontSize: 12, color: color.muted }}>{note}</span>
        </span>
      </button>)
  }
  const head = (title: string, to?: Section, label = "See all") => (
    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: `${space(4)} ${space(5)} ${space(3)}` }}>
      <span style={{ fontWeight: 800, color: color.ink, fontSize: 15 }}>{title}</span>
      {to && <button onClick={() => go(to)} style={{ background: "none", border: "none", color: color.navyText, fontSize: 13, fontWeight: 700, cursor: "pointer", fontFamily: "inherit" }}>{label}</button>}
    </div>)
  const row = (key: string, Icon: typeof Wrench, title: string, note: string, to: Section, trailing?: React.ReactNode) => (
    <button key={key} onClick={() => go(to)} style={{ display: "flex", alignItems: "center", gap: space(3), width: "100%", textAlign: "left", background: "none", border: "none", borderTop: `1px solid ${color.borderLight}`, padding: `${space(3)} ${space(5)}`, cursor: "pointer", font: "inherit" }}>
      <Icon size={17} color="var(--hf-text-muted)" style={{ flexShrink: 0 }} />
      <span style={{ flex: 1, minWidth: 0 }}><span style={{ display: "block", fontSize: 13.5, fontWeight: 700, color: color.ink, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{title}</span><span style={{ display: "block", ...sub, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{note}</span></span>
      {trailing}<ChevronRight size={15} color="var(--hf-text-faint)" />
    </button>)
  const KIND = { request: Wrench, quote: FileText, invoice: Receipt } as const
  const attention = toAnswer.length + owing.length

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: space(5) }}>
      <Title text={`Welcome back${first ? `, ${first}` : ""}`} note="Here is where your facilities work stands." />
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: space(3) }}>
        {tile("Open requests", count("open"), count("open") ? "Waiting to be done" : "Nothing waiting", Wrench, "info", "requests")}
        {tile("In progress", count("progress"), s.slaAtRisk ? `${s.slaAtRisk} may run late` : "None at risk", Clock, s.slaAtRisk || s.slaBreached ? "bad" : "warn", "requests")}
        {tile("Completed", count("completed"), s.slaBreached ? `${s.slaBreached} finished late` : "All on time so far", CheckCircle2, s.slaBreached ? "bad" : "good", "requests")}
        {tile("Quotes to answer", toAnswer.length, toAnswer.length ? "Waiting for your decision" : "Nothing waiting", FileText, toAnswer.length ? "warn" : "info", "quotes")}
        {tile("Next invoice due", next ? money(next.balance) : "—", next ? `Due ${day(next.dueDate)}` : "Nothing owing", Receipt, owing.some(i => (i.dueDate ?? "9") < today) ? "bad" : "info", "invoices")}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(340px, 1fr))", gap: space(4), alignItems: "start" }}>
        <div style={{ display: "flex", flexDirection: "column", gap: space(4), minWidth: 0 }}>
        <div style={panel}>
          {head("Service request status", "requests")}
          <div style={{ padding: `${space(2)} ${space(5)} ${space(5)}` }}>{total === 0 ? <p style={{ ...empty, padding: space(4) }}>You have not reported anything yet.</p> : <Donut slices={slices} total={total} />}</div>
        </div>
        <div style={panel}>
          {head("Needs your attention")}
          {attention === 0
            ? <div style={{ ...empty, borderTop: `1px solid ${color.borderLight}`, display: "flex", flexDirection: "column", alignItems: "center", gap: 6 }}><CheckCircle2 size={26} color="var(--hf-success-solid-strong)" />You are all caught up. Nothing needs your answer or payment.</div>
            : <>
              {toAnswer.map(x => row("q" + x.id, FileText, `${x.quoteNumber} · ${x.title}`, `${money(x.total)}${x.validUntil ? ` · valid until ${day(x.validUntil)}` : ""}`, "quotes", <Chip tone="warning">Review</Chip>))}
              {owing.map(i => { const d = dueNote(i.status, i.balance, i.dueDate, today); return row("i" + i.id, Receipt, `${i.invoiceNumber} · ${money(i.balance)} owing`, i.dueDate ? `Due ${day(i.dueDate)}` : "No due date", "invoices", d ? <Chip tone={d.tone}>{d.text}</Chip> : undefined) })}
            </>}
        </div>
        </div>
        <div style={{ display: "flex", flexDirection: "column", gap: space(4), minWidth: 0 }}>
        <div style={panel}>
          {head("Recent activity")}
          {feed.length === 0 ? <div style={{ ...empty, borderTop: `1px solid ${color.borderLight}` }}>Nothing has happened yet.</div>
            : feed.map(a => row(a.key, KIND[a.kind], a.title, `${day(a.when)} · ${a.note}`, a.to))}
        </div>
        <div style={panel}>
          {head("Your sites", "sites", "View all sites")}
          {siteRows.length === 0 ? <div style={{ ...empty, borderTop: `1px solid ${color.borderLight}` }}>No sites yet.</div>
            : siteRows.map(x => row("s" + x.id, MapPin, x.name, addressLine(x.address) || "No address", "sites", <span style={sub}>{perSite(x.id)} request{perSite(x.id) === 1 ? "" : "s"}</span>))}
        </div>
        </div>
      </div>
    </div>
  )
}
