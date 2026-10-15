import { RISK_CFG } from "./auditShared"

export function RiskBadge({ level }: { level: string | null }) {
  if (!level) return <span style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>Not assessed</span>
  const c = RISK_CFG[level] ?? RISK_CFG.LOW
  return <span style={{ fontSize: 11, fontWeight: 700, padding: "3px 10px", borderRadius: 20, background: c.bg, color: c.color }}>{level}</span>
}

// ── Main page ────────────────────────────────────────────────────────────────
