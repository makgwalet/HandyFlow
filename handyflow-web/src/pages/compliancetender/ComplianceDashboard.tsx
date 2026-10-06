// src/pages/compliancetender/ComplianceDashboard.tsx
//
// The module's front door: what closes next, how healthy the registrations are, where the tenders sit in the pipeline, and a single list of everything that needs a person soon.
// Reads the same endpoints the tabs use; the counting and wording live in dashboard.logic.ts.
import { useQuery } from "@tanstack/react-query"
import { useNavigate } from "react-router-dom"
import { apiClient } from "../../api/client"
import { ShieldCheck, CalendarClock, Briefcase, Wallet, ArrowRight, Clock, AlertTriangle, CheckCircle2 } from "lucide-react"
import ProgressRing from "../../components/ui/ProgressRing"
import { closingText, daysUntil } from "./package.logic"
import {
  type DashRegistration, type DashDeadline, type DashTender,
  isActive, pipeline, pipelineValue, nextClosing, registrationHealth, urgentItems, fmtZarShort,
} from "./dashboard.logic"

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const unwrap = (r: any) => { const p = r.data?.data ?? r.data; return p?.content ?? p ?? [] }

const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 16, padding: 20 }
const eyebrow: React.CSSProperties = { fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase", letterSpacing: "0.06em" }

export default function ComplianceDashboard({ onNavigate }: { onNavigate: (tab: string) => void }) {
  const nav = useNavigate()
  const today = new Date()
  const { data: registrations = [] } = useQuery<DashRegistration[]>({
    queryKey: ["ct-dashboard", "registrations"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/compliance/registrations/all")),
  })
  const { data: deadlines = [] } = useQuery<DashDeadline[]>({
    queryKey: ["ct-dashboard", "deadlines"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/compliance/deadlines")),
  })
  const { data: tenders = [] } = useQuery<DashTender[]>({
    queryKey: ["ct-dashboard", "tenders"],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/compliance/tenders?size=200")),
  })

  const health = registrationHealth(registrations)
  const stages = pipeline(tenders)
  const maxStage = Math.max(1, ...stages.map(s => s.count))
  const next = nextClosing(tenders, today)
  const urgent = urgentItems(registrations, deadlines, tenders, today)
  const active = tenders.filter(isActive).length
  const dueSoon = deadlines.filter(d => { const n = daysUntil(d.dueDate, today); return n !== null && n <= 14 }).length
  const attention = health.expired + health.expiringSoon

  const open = (it: { tab: string; tenderId?: string }) => it.tenderId ? nav(`/compliancetender/tenders/${it.tenderId}`) : onNavigate(it.tab)

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      {/* Next closing */}
      <div style={{ ...card, display: "flex", flexWrap: "wrap", alignItems: "center", gap: 20, padding: 24,
        background: "linear-gradient(135deg, var(--hf-accent-soft), var(--hf-surface) 70%)" }}>
        {next ? (() => {
          const c = closingText(next.closingDate, today)
          const n = daysUntil(next.closingDate, today) ?? 0
          const tone = c.tone === "soon" || c.tone === "late" ? "var(--hf-danger-text)" : "var(--hf-accent-text)"
          return (<>
            <div style={{ textAlign: "center", minWidth: 96 }}>
              <div style={{ fontSize: 44, fontWeight: 800, lineHeight: 1, color: tone }}>{n}</div>
              <div style={{ fontSize: 12, fontWeight: 600, color: "var(--hf-text-muted)", marginTop: 4 }}>{n === 1 ? "day left" : "days left"}</div>
            </div>
            <div style={{ flex: 1, minWidth: 220 }}>
              <div style={eyebrow}>Next closing</div>
              <div style={{ fontSize: 18, fontWeight: 700, color: "var(--hf-text)", margin: "4px 0" }}>{next.name}</div>
              <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>
                {next.tenderNumber}{next.tenderAuthority ? ` · ${next.tenderAuthority}` : ""}{next.estimatedValue != null ? ` · ${fmtZarShort(next.estimatedValue)}` : ""}
              </div>
            </div>
            <button onClick={() => nav(`/compliancetender/tenders/${next.id}/package`)}
              style={{ display: "inline-flex", alignItems: "center", gap: 8, padding: "10px 16px", borderRadius: 10, border: "none", cursor: "pointer", fontSize: 13, fontWeight: 700, background: "var(--hf-accent)", color: "var(--hf-on-accent, #fff)" }}>
              Open submission package <ArrowRight size={15} />
            </button>
          </>)
        })() : (<>
          <Clock size={28} style={{ color: "var(--hf-text-faint)" }} />
          <div style={{ flex: 1 }}>
            <div style={eyebrow}>Next closing</div>
            <div style={{ fontSize: 15, fontWeight: 600, color: "var(--hf-text)", marginTop: 4 }}>No tender is waiting on a closing date.</div>
            <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Tenders you are preparing will show here with a countdown.</div>
          </div>
          <button onClick={() => onNavigate("tenders")} style={{ padding: "10px 16px", borderRadius: 10, border: "1px solid var(--hf-border)", background: "var(--hf-surface)", color: "var(--hf-text)", cursor: "pointer", fontSize: 13, fontWeight: 600 }}>Go to tenders</button>
        </>)}
      </div>

      {/* KPI tiles */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(210px, 1fr))", gap: 14 }}>
        <Kpi icon={Briefcase} tint="success" label="Active tenders" value={String(active)} sub={`${tenders.length} in total`} onClick={() => onNavigate("tenders")}
          ring={<ProgressRing value={active} max={Math.max(tenders.length, 1)} tone="ok" size={52} showValue={false} label={`${active} of ${tenders.length} tenders active`} />} />
        <Kpi icon={Wallet} tint="accent" label="Pipeline value" value={fmtZarShort(pipelineValue(tenders))} sub="Estimated, open tenders" onClick={() => onNavigate("tenders")} />
        <Kpi icon={ShieldCheck} tint="sky" label="Registrations in good standing" value={`${health.valid}/${health.total}`} sub={attention > 0 ? `${attention} need attention` : "Nothing expiring"} onClick={() => onNavigate("registrations")}
          ring={<ProgressRing value={health.valid} max={Math.max(health.total, 1)} tone={attention > 0 ? "warn" : "ok"} size={52} showValue={false} label={`${health.valid} of ${health.total} registrations in good standing`} />} />
        <Kpi icon={CalendarClock} tint="warning" label="Deadlines in 14 days" value={String(dueSoon)} sub={`${deadlines.length} pending in total`} onClick={() => onNavigate("deadlines")} />
      </div>

      {/* Pipeline + registration health */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))", gap: 16 }}>
        <div style={card}>
          <div style={{ ...eyebrow, marginBottom: 14 }}>Tender pipeline</div>
          <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
            {stages.map(s => (
              <div key={s.key}>
                <div style={{ display: "flex", justifyContent: "space-between", fontSize: 13, marginBottom: 5 }}>
                  <span style={{ fontWeight: 600, color: "var(--hf-text)" }}>{s.label}</span>
                  <span style={{ color: "var(--hf-text-muted)" }}>{s.count} {s.count === 1 ? "tender" : "tenders"}{s.value > 0 ? ` · ${fmtZarShort(s.value)}` : ""}</span>
                </div>
                <div style={{ height: 10, borderRadius: 6, background: "var(--hf-surface-strong)", overflow: "hidden" }}>
                  <div style={{ width: `${(s.count / maxStage) * 100}%`, height: "100%", borderRadius: 6, background: s.key === "awarded" ? "var(--hf-success)" : "var(--hf-accent)", transition: "width .3s" }} />
                </div>
              </div>
            ))}
          </div>
        </div>

        <div style={card}>
          <div style={{ ...eyebrow, marginBottom: 14 }}>Registration health</div>
          <div style={{ display: "flex", alignItems: "center", gap: 20, flexWrap: "wrap" }}>
            <Donut parts={[
              { v: health.valid, c: "var(--hf-success)" }, { v: health.expiringSoon, c: "var(--hf-warning)" },
              { v: health.expired, c: "var(--hf-danger)" }, { v: health.other, c: "var(--hf-text-faint)" },
            ]} total={health.total} />
            <div style={{ display: "flex", flexDirection: "column", gap: 8, fontSize: 13 }}>
              <Legend c="var(--hf-success)" label="In good standing" n={health.valid} />
              <Legend c="var(--hf-warning)" label="Expiring soon" n={health.expiringSoon} />
              <Legend c="var(--hf-danger)" label="Expired or lapsed" n={health.expired} />
              <Legend c="var(--hf-text-faint)" label="Pending or n/a" n={health.other} />
            </div>
          </div>
        </div>
      </div>

      {/* Urgent */}
      <div style={{ ...card, padding: 0, overflow: "hidden" }}>
        <div style={{ padding: "16px 20px", display: "flex", alignItems: "center", gap: 10, borderBottom: "1px solid var(--hf-border)" }}>
          <AlertTriangle size={16} style={{ color: urgent.length ? "var(--hf-warning-text)" : "var(--hf-text-faint)" }} />
          <div style={{ fontSize: 14, fontWeight: 700, color: "var(--hf-text)" }}>Needs attention</div>
          {urgent.length > 0 && <span style={{ fontSize: 12, fontWeight: 700, padding: "2px 8px", borderRadius: 999, background: "var(--hf-warning-soft)", color: "var(--hf-warning-text)" }}>{urgent.length}</span>}
        </div>
        {urgent.length === 0 ? (
          <div style={{ padding: "28px 20px", display: "flex", alignItems: "center", gap: 10, color: "var(--hf-success-text-strong)", fontSize: 13, fontWeight: 600 }}>
            <CheckCircle2 size={18} /> Nothing needs attention in the next two weeks.
          </div>
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
              <thead>
                <tr style={{ textAlign: "left", color: "var(--hf-text-faint)", fontSize: 11, textTransform: "uppercase", letterSpacing: "0.05em" }}>
                  <th style={th}>What</th><th style={th}>When</th><th style={th} aria-label="Open" />
                </tr>
              </thead>
              <tbody>
                {urgent.map(it => (
                  <tr key={it.key} onClick={() => open(it)} style={{ cursor: "pointer", borderTop: "1px solid var(--hf-border)" }}>
                    <td style={{ ...td, borderLeft: `4px solid ${it.tone === "bad" ? "var(--hf-danger)" : "var(--hf-warning)"}` }}>
                      <div style={{ fontWeight: 600, color: "var(--hf-text)" }}>{it.title}</div>
                      <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{it.kind} · {it.detail}</div>
                    </td>
                    <td style={td}>
                      <span style={{ fontWeight: 700, padding: "3px 10px", borderRadius: 999, fontSize: 12, whiteSpace: "nowrap",
                        background: it.tone === "bad" ? "var(--hf-danger-soft)" : "var(--hf-warning-soft)", color: it.tone === "bad" ? "var(--hf-danger-text)" : "var(--hf-warning-text)" }}>{it.when}</span>
                    </td>
                    <td style={{ ...td, textAlign: "right" }}><ArrowRight size={15} style={{ color: "var(--hf-text-faint)" }} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}

const th: React.CSSProperties = { padding: "10px 16px", fontWeight: 700 }
const td: React.CSSProperties = { padding: "12px 16px", color: "var(--hf-text-muted)", verticalAlign: "middle" }

const TINTS = {
  success: ["var(--hf-success-soft-strong)", "var(--hf-success-text-strong)"],
  accent: ["var(--hf-accent-soft)", "var(--hf-accent-text)"],
  sky: ["var(--hf-sky-soft-strong)", "var(--hf-sky-text-strong)"],
  warning: ["var(--hf-warning-soft)", "var(--hf-warning-text)"],
} as const

function Kpi({ icon: Icon, tint, label, value, sub, ring, onClick }: { icon: React.ElementType; tint: keyof typeof TINTS; label: string; value: string; sub: string; ring?: React.ReactNode; onClick: () => void }) {
  const [bg, fg] = TINTS[tint]
  return (
    <button onClick={onClick} style={{ ...card, textAlign: "left", cursor: "pointer", display: "flex", alignItems: "center", gap: 14 }}>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ width: 36, height: 36, borderRadius: 10, background: bg, display: "flex", alignItems: "center", justifyContent: "center", marginBottom: 12 }}>
          <Icon size={17} style={{ color: fg }} />
        </div>
        <div style={{ fontSize: 28, fontWeight: 800, color: "var(--hf-text)", lineHeight: 1.1 }}>{value}</div>
        <div style={{ fontSize: 12, fontWeight: 600, color: "var(--hf-text-muted)", marginTop: 4 }}>{label}</div>
        <div style={{ fontSize: 12, color: "var(--hf-text-faint)", marginTop: 2 }}>{sub}</div>
      </div>
      {ring}
    </button>
  )
}

function Legend({ c, label, n }: { c: string; label: string; n: number }) {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
      <span style={{ width: 10, height: 10, borderRadius: 3, background: c }} />
      <span style={{ color: "var(--hf-text-muted)" }}>{label}</span>
      <strong style={{ color: "var(--hf-text)", marginLeft: "auto", paddingLeft: 12 }}>{n}</strong>
    </div>
  )
}

function Donut({ parts, total }: { parts: { v: number; c: string }[]; total: number }) {
  const size = 120, stroke = 16, r = (size - stroke) / 2, circ = 2 * Math.PI * r
  let offset = 0
  return (
    <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} role="img" aria-label={`${total} registrations`}>
      <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="var(--hf-surface-strong)" strokeWidth={stroke} />
      {total > 0 && parts.filter(p => p.v > 0).map((p, i) => {
        const len = (p.v / total) * circ
        const el = <circle key={i} cx={size / 2} cy={size / 2} r={r} fill="none" stroke={p.c} strokeWidth={stroke}
          strokeDasharray={`${len} ${circ - len}`} strokeDashoffset={-offset} transform={`rotate(-90 ${size / 2} ${size / 2})`} />
        offset += len
        return el
      })}
      <text x="50%" y="50%" textAnchor="middle" dominantBaseline="central" fontSize={26} fontWeight={800} fill="var(--hf-text)">{total}</text>
    </svg>
  )
}
