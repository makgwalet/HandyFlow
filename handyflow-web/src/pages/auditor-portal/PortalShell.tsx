// The frame every auditor portal page shares: brand, who is signed in, and a content area that fits a phone.

import type { ReactNode } from "react"

const PORTAL = {
  border: "var(--hf-border)", ink: "var(--hf-text)", muted: "var(--hf-text-muted)", faint: "var(--hf-text-faint)", primary: "var(--hf-primary)", primaryText: "var(--hf-primary-text)",
}
const GUTTER = "clamp(16px, 4vw, 32px)"

export function PortalShell({ left, right, children, maxWidth = 960 }: { left: ReactNode; right?: ReactNode; children: ReactNode; maxWidth?: number }) {
  return (
    <div style={{ minHeight: "100vh", background: "var(--hf-surface-muted)", fontFamily: "'Inter', system-ui, sans-serif" }}>
      <header style={{ display: "flex", flexWrap: "wrap", justifyContent: "space-between", alignItems: "center", gap: 8, padding: `14px ${GUTTER}`, background: "var(--hf-surface)", borderBottom: `1px solid ${PORTAL.border}` }}>
        {left}
        {right}
      </header>
      <main style={{ padding: `24px ${GUTTER}`, maxWidth, margin: "0 auto", boxSizing: "content-box" }}>{children}</main>
    </div>
  )
}

export function Brand() {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
      <div aria-hidden style={{ width: 28, height: 28, borderRadius: 6, background: PORTAL.primary, color: "var(--hf-text-on-solid)", display: "flex", alignItems: "center", justifyContent: "center", fontWeight: 800, fontSize: 13 }}>H</div>
      <span style={{ fontSize: 14, fontWeight: 800, color: PORTAL.ink }}>Auditor Access</span>
    </div>
  )
}

export function PortalCard({ children }: { children: ReactNode }) {
  return <div style={{ background: "var(--hf-surface)", border: `1px solid ${PORTAL.border}`, borderRadius: 10, padding: "14px 16px" }}>{children}</div>
}
