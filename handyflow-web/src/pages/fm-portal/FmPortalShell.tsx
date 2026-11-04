// The portal frame: a dark sidebar on a computer, a bottom bar on a phone, and a top strip with who is signed in.
import { useState, type ReactNode } from "react"
import { Building2, FileText, HelpCircle, LayoutDashboard, BarChart3, LogOut, MapPin, MoreHorizontal, Cog, Receipt, User, Wrench } from "lucide-react"
import { color, space, type } from "../accountant-portal/portal-theme"
import { initialOf } from "./portalKit"

export type Section = "dashboard" | "requests" | "quotes" | "invoices" | "sites" | "assets" | "reports" | "profile" | "support"
const NAV: { key: Section; label: string; short: string; Icon: typeof Wrench }[] = [
  { key: "dashboard", label: "Dashboard", short: "Home", Icon: LayoutDashboard },
  { key: "requests", label: "Service Requests", short: "Requests", Icon: Wrench },
  { key: "quotes", label: "Quotes", short: "Quotes", Icon: FileText },
  { key: "invoices", label: "Invoices & Payments", short: "Invoices", Icon: Receipt },
  { key: "sites", label: "Sites", short: "Sites", Icon: MapPin },
  { key: "assets", label: "Equipment", short: "Equipment", Icon: Cog },
  { key: "reports", label: "Reports & Documents", short: "Reports", Icon: BarChart3 },
]
const MOBILE_MAIN: Section[] = ["dashboard", "requests", "quotes", "invoices"]
const LOWER: { key: Section; label: string; Icon: typeof Wrench }[] = [
  { key: "profile", label: "Profile", Icon: User },
  { key: "support", label: "Help & Support", Icon: HelpCircle },
]
const SIDE = "var(--hf-primary-hover)"

const css = `
.fmp-side{width:236px;flex-shrink:0;background:${SIDE};color:var(--hf-text-on-solid);display:flex;flex-direction:column;position:sticky;top:0;height:100vh}
.fmp-bottom{display:none}
.fmp-item{display:flex;align-items:center;gap:12px;width:100%;padding:11px 14px;border:none;border-radius:10px;background:none;color:inherit;font:inherit;font-size:14px;font-weight:600;cursor:pointer;text-align:left;opacity:.82}
.fmp-item:hover{background:rgba(255,255,255,.08);opacity:1}
.fmp-item[aria-current=page]{background:rgba(255,255,255,.16);opacity:1}
@media (max-width: 820px){
  .fmp-side{display:none}
  .fmp-bottom{display:flex;position:fixed;left:0;right:0;bottom:0;z-index:20;background:var(--hf-surface);border-top:1px solid var(--hf-border);padding:6px 4px calc(6px + env(safe-area-inset-bottom))}
  .fmp-bottom button{flex:1;display:flex;flex-direction:column;align-items:center;gap:3px;background:none;border:none;font:inherit;font-size:11px;font-weight:600;color:var(--hf-text-muted);padding:6px 2px;cursor:pointer}
  .fmp-bottom button[aria-current=page]{color:var(--hf-primary-text)}
  .fmp-main{padding-bottom:84px !important}
}
`

export function FmPortalShell({ section, go, userName, clientName, clients, clientId, onClient, onLogout, badges, children }: {
  section: Section; go: (s: Section) => void; userName: string; clientName: string
  clients: { id: string; name: string }[]; clientId: string; onClient: (id: string) => void
  onLogout: () => void; badges: Partial<Record<Section, number>>; children: ReactNode
}) {
  const [more, setMore] = useState(false)
  const item = (key: Section, label: string, Icon: typeof Wrench) => (
    <button key={key} className="fmp-item" aria-current={section === key ? "page" : undefined} onClick={() => go(key)}>
      <Icon size={18} /><span style={{ flex: 1 }}>{label}</span>
      {(badges[key] ?? 0) > 0 && <span style={{ background: color.amber, color: "#1b1300", borderRadius: 20, fontSize: 11, fontWeight: 800, padding: "1px 7px" }}>{badges[key]}</span>}
    </button>)
  return (
    <div style={{ display: "flex", minHeight: "100vh", background: color.canvas, fontFamily: type.family }}>
      <style>{css}</style>
      <aside className="fmp-side" aria-label="Portal menu">
        <div style={{ padding: `${space(5)} ${space(4)} ${space(4)}`, display: "flex", alignItems: "center", gap: space(3) }}>
          <div style={{ width: 36, height: 36, borderRadius: 10, background: "rgba(255,255,255,.16)", display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 800, fontSize: 17 }}>H</div>
          <div><div style={{ fontWeight: 800, fontSize: 16, lineHeight: 1.15 }}>HandyFlow</div><div style={{ fontSize: 11.5, opacity: 0.75 }}>Client Portal</div></div>
        </div>
        <div style={{ margin: `0 ${space(3)} ${space(4)}`, padding: "10px 12px", background: "rgba(255,255,255,.1)", borderRadius: 10, fontSize: 12.5, display: "flex", alignItems: "center", gap: 8 }}>
          <Building2 size={15} style={{ flexShrink: 0 }} />
          {clients.length > 1
            ? <select aria-label="Client" value={clientId} onChange={e => onClient(e.target.value)} style={{ width: "100%", background: "transparent", border: "none", color: "inherit", font: "inherit", fontWeight: 700 }}>{clients.map(c => <option key={c.id} value={c.id} style={{ color: "#111" }}>{c.name}</option>)}</select>
            : <span style={{ fontWeight: 700, lineHeight: 1.3 }}>{clientName}</span>}
        </div>
        <nav style={{ padding: `0 ${space(3)}`, display: "flex", flexDirection: "column", gap: 4 }}>{NAV.map(n => item(n.key, n.label, n.Icon))}</nav>
        <div style={{ flex: 1 }} />
        <nav style={{ padding: `0 ${space(3)} ${space(4)}`, display: "flex", flexDirection: "column", gap: 4 }}>
          {LOWER.map(n => item(n.key, n.label, n.Icon))}
          <button className="fmp-item" onClick={onLogout}><LogOut size={18} /><span>Log out</span></button>
        </nav>
      </aside>

      <div style={{ flex: 1, minWidth: 0 }}>
        <header style={{ background: color.surface, borderBottom: `1px solid ${color.border}`, padding: `${space(3)} ${space(6)}`, display: "flex", justifyContent: "space-between", alignItems: "center", gap: space(3) }}>
          <div style={{ fontSize: 13, color: color.muted, minWidth: 0, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{clientName}</div>
          <div style={{ display: "flex", alignItems: "center", gap: space(3) }}>
            <div aria-hidden style={{ width: 32, height: 32, borderRadius: "50%", background: "var(--hf-surface-sunken)", color: color.navyText, display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 800, fontSize: 13 }}>{initialOf(userName)}</div>
            <span style={{ fontSize: 13, fontWeight: 600, color: color.slate }}>{userName}</span>
          </div>
        </header>
        <main className="fmp-main" style={{ maxWidth: 1080, margin: "0 auto", padding: `${space(6)} ${space(6)} ${space(12)}` }}>{children}</main>
      </div>

      <nav className="fmp-bottom" aria-label="Portal menu">
        {NAV.filter(n => MOBILE_MAIN.includes(n.key)).map(n => <button key={n.key} aria-current={section === n.key ? "page" : undefined} onClick={() => go(n.key)}><n.Icon size={20} />{n.short}</button>)}
        <button aria-expanded={more} aria-current={!MOBILE_MAIN.includes(section) ? "page" : undefined} onClick={() => setMore(!more)}><MoreHorizontal size={20} />More</button>
        {more && <div role="menu" aria-label="More" style={{ position: "absolute", right: 8, bottom: "calc(100% + 6px)", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 6, minWidth: 190, boxShadow: "0 8px 24px rgba(0,0,0,.18)" }}>
          {[...NAV.filter(n => !MOBILE_MAIN.includes(n.key)), { key: "profile" as Section, label: "Profile", Icon: User }, { key: "support" as Section, label: "Help & Support", Icon: HelpCircle }].map(n =>
            <button role="menuitem" key={n.key} onClick={() => { setMore(false); go(n.key) }} style={{ display: "flex", flexDirection: "row", gap: 10, width: "100%", fontSize: 14, padding: "10px 12px", justifyContent: "flex-start" }}><n.Icon size={18} />{n.label}</button>)}
        </div>}
      </nav>
    </div>
  )
}
