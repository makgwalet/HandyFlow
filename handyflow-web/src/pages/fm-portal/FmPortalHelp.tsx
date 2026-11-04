// The portal's Profile and Help pages. No contact details are invented here: the team is reached through the portal itself.
import { Building2, CheckCircle2, LogOut, Mail, Receipt, ShieldAlert, User, Wrench } from "lucide-react"
import { color, space } from "../accountant-portal/portal-theme"
import { PORTAL_STATUS } from "../facilitiesmanagement/fm.logic"
import { Chip, Title, btn, initialOf, panel, sub } from "./portalKit"
import type { Section } from "./FmPortalShell"

export function ProfilePanel({ name, email, clients, onSignOut }: { name: string; email: string; clients: { id: string; name: string }[]; onSignOut: () => void }) {
  return (
    <div>
      <Title text="Profile" note="Who you are signed in as." />
      <div style={{ ...panel, padding: space(5), display: "grid", gap: space(5), maxWidth: 640 }}>
        <div style={{ display: "flex", alignItems: "center", gap: space(4) }}>
          <div aria-hidden style={{ width: 56, height: 56, borderRadius: "50%", background: "var(--hf-surface-sunken)", color: color.navyText, display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 800, fontSize: 22 }}>{initialOf(name || email)}</div>
          <div><div style={{ fontWeight: 800, fontSize: 17, color: color.ink }}>{name && !name.includes("@") ? name : "Your account"}</div><div style={{ ...sub, display: "flex", alignItems: "center", gap: 6, marginTop: 2 }}><Mail size={13} />{email}</div></div>
        </div>
        <div>
          <div style={{ fontSize: 11, textTransform: "uppercase", letterSpacing: "0.04em", fontWeight: 700, color: color.muted, marginBottom: 8 }}>You can act for</div>
          <div style={{ display: "grid", gap: 8 }}>{clients.map(c => <div key={c.id} style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 14, color: color.ink }}><Building2 size={15} color="var(--hf-text-muted)" />{c.name}</div>)}</div>
        </div>
        <div><button style={{ ...btn(), display: "inline-flex", alignItems: "center", gap: 6 }} onClick={onSignOut}><LogOut size={14} />Log out</button></div>
      </div>
    </div>
  )
}

export function SupportPanel({ go }: { go: (s: Section) => void }) {
  const how: [typeof Wrench, string, string, Section][] = [
    [Wrench, "Report a problem", "Open Service Requests and choose Report a problem. Tell us what is wrong and where. The team reviews every request and sets its priority.", "requests"],
    [CheckCircle2, "Answer a quote", "Quotes the team has sent appear under Quotes with a Needs your answer label. Open one to accept or decline it.", "quotes"],
    [Receipt, "See what you owe", "Invoices & Payments lists every invoice with what is still owing. Each one has a PDF you can download.", "invoices"],
    [User, "Your profile and logging out", "See who you are signed in as and log out.", "profile"],
  ]
  const words = ["REQUESTED", "OPEN", "ASSIGNED", "IN_PROGRESS", "ON_HOLD", "COMPLETED", "REJECTED"]
  const meaning: Record<string, string> = { REQUESTED: "We have your request and are reviewing it.", OPEN: "The team accepted it and is arranging the work.", ASSIGNED: "A technician or contractor has been given the job.", IN_PROGRESS: "Work has started.", ON_HOLD: "Waiting for something, for example a part.", COMPLETED: "The work is done.", REJECTED: "The team could not take it on. The reason is shown on the request." }
  return (
    <div>
      <Title text="Help & support" note="How the portal works." />
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))", gap: space(4), alignItems: "start" }}>
        <div style={{ ...panel, padding: space(5) }}>
          <div style={{ fontWeight: 800, color: color.ink, marginBottom: space(3) }}>What would you like to do?</div>
          {how.map(([Icon, t, d, to]) => <button key={t} onClick={() => go(to)} style={{ display: "flex", gap: space(3), width: "100%", textAlign: "left", background: "none", border: "none", borderTop: `1px solid ${color.borderLight}`, padding: `${space(3)} 0`, cursor: "pointer", font: "inherit" }}>
            <Icon size={18} color="var(--hf-primary-text)" style={{ marginTop: 2, flexShrink: 0 }} /><span><b style={{ color: color.ink, fontSize: 14 }}>{t}</b><span style={{ display: "block", ...sub, fontSize: 13, marginTop: 2 }}>{d}</span></span></button>)}
        </div>
        <div style={{ ...panel, padding: space(5) }}>
          <div style={{ fontWeight: 800, color: color.ink, marginBottom: space(3) }}>What the status words mean</div>
          {words.map(w => { const s = PORTAL_STATUS[w]; return <div key={w} style={{ display: "flex", gap: space(3), alignItems: "baseline", padding: "7px 0", borderTop: `1px solid ${color.borderLight}`, fontSize: 13 }}><span style={{ width: 92, flexShrink: 0 }}><Chip tone={s.tone}>{s.label}</Chip></span><span style={{ color: color.slate }}>{meaning[w]}</span></div> })}
        </div>
      </div>
      <div style={{ ...panel, padding: space(5), marginTop: space(4), display: "flex", gap: space(3), background: color.redBg, borderColor: "var(--hf-danger-border)" }}>
        <ShieldAlert size={20} color="var(--hf-danger-text)" style={{ flexShrink: 0 }} />
        <div style={{ fontSize: 13.5, color: color.redText, lineHeight: 1.5 }}><b>Something dangerous right now?</b> Phone your facilities manager as well. A request here is reviewed by the team but is not an emergency line.</div>
      </div>
    </div>
  )
}
