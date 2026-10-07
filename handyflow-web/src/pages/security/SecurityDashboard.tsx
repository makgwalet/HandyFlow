// src/pages/security/SecurityDashboard.tsx
//
// The Security landing page. One request (GET /api/v1/security/dashboard) returns the figures, a ranked "needs attention"
// list and short lists of who is on duty and which incidents are open. It refreshes itself every minute. The old page
// fetched up to 100 guards and 50 shifts and counted them in the browser.
import { useQuery } from "@tanstack/react-query"
import { AlertTriangle, ArrowRight, Radio, RefreshCw, Shield, MapPin, DoorOpen, MessageSquareWarning, CheckCircle2 } from "lucide-react"
import type { ReactNode } from "react"
import { apiClient } from "../../api/client"
import Chip, { toneColor, type ChipTone } from "../../components/ui/Chip"
import {
  LEVEL_LABEL, LEVEL_TONE, SEVERITY_TONE, greeting, headline, isLate, punctualityLabel, shiftProgress, toneForCount,
  type SecurityDashboard as Data,
} from "./dashboard.logic"

const fmtTime = (iso: string) => new Date(iso).toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit" })
const fmtDay = (iso: string) => new Date(iso).toLocaleDateString("en-ZA", { weekday: "long", day: "numeric", month: "long" })
const fmtDate = (iso: string) => new Date(iso).toLocaleDateString("en-ZA", { day: "numeric", month: "short" })

const panel: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16 }
const linkBtn: React.CSSProperties = { display: "inline-flex", alignItems: "center", gap: 4, fontSize: 12, color: "var(--hf-accent-text)", background: "none", border: "none", cursor: "pointer", fontWeight: 600, padding: 0 }

function Tile({ label, value, hint, tone, icon, onClick }: { label: string; value: ReactNode; hint?: string; tone: ChipTone; icon: ReactNode; onClick: () => void }) {
  const c = tone === "neutral" ? "var(--hf-text-secondary)" : toneColor(tone)
  return (
    <button type="button" onClick={onClick} aria-label={`${label}: ${value}`}
      style={{ ...panel, textAlign: "left", cursor: "pointer", display: "flex", gap: 12, alignItems: "center", font: "inherit", color: "inherit" }}>
      <div style={{ width: 38, height: 38, borderRadius: 10, background: "var(--hf-surface-sunken)", color: c, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>{icon}</div>
      <div style={{ minWidth: 0 }}>
        <div style={{ fontSize: 24, fontWeight: 800, color: c, lineHeight: 1.1 }}>{value}</div>
        <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 3 }}>{label}</div>
        {hint && <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 1 }}>{hint}</div>}
      </div>
    </button>
  )
}

export default function SecurityDashboard({ onNavigate }: { onNavigate: (section: string) => void }) {
  const { data, isLoading, error, refetch, isFetching } = useQuery<Data>({
    queryKey: ["security-dashboard"],
    refetchInterval: 60_000,
    queryFn: async () => { const r = await apiClient.get("/api/v1/security/dashboard"); return r.data?.data ?? r.data },
  })

  if (isLoading) return <div style={{ color: "var(--hf-text-muted)" }}>Loading the dashboard...</div>
  if (error || !data) return <div role="alert" style={{ color: "var(--hf-danger-text)" }}>The dashboard could not be loaded. <button style={linkBtn} onClick={() => refetch()}>Try again</button></div>

  const { shifts, workforce, incidents, complaints, gate } = data
  const head = headline(data.attention)

  return (
    <div style={{ display: "grid", gap: 16 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 8 }}>
        <div>
          <div style={{ fontSize: 18, fontWeight: 800, color: "var(--hf-text-primary)" }}>{greeting()}</div>
          <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{fmtDay(data.asOf)} · updated {fmtTime(data.asOf)}</div>
        </div>
        <button type="button" onClick={() => refetch()} disabled={isFetching} style={linkBtn} aria-label="Refresh">
          <RefreshCw size={13} /> {isFetching ? "Refreshing..." : "Refresh"}
        </button>
      </div>

      <section aria-label="Needs attention" style={panel}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
          <span style={{ fontSize: 14, fontWeight: 700 }}>Needs attention</span>
          <Chip tone={head.tone}>{head.text}</Chip>
        </div>
        {data.attention.length === 0 ? (
          <div style={{ display: "flex", gap: 8, alignItems: "center", color: "var(--hf-success-text-strong)", fontSize: 13 }}>
            <CheckCircle2 size={16} /> Nothing needs attention right now.
          </div>
        ) : (
          <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gap: 8 }}>
            {data.attention.map(a => (
              <li key={a.code} style={{ display: "flex", alignItems: "center", gap: 12, padding: "10px 12px", border: "1px solid var(--hf-border)", borderLeft: `3px solid ${toneColor(LEVEL_TONE[a.level] ?? "neutral")}`, borderRadius: 10 }}>
                <Chip tone={LEVEL_TONE[a.level] ?? "neutral"}>{LEVEL_LABEL[a.level] ?? a.level}</Chip>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontWeight: 600, fontSize: 13 }}>{a.title}</div>
                  <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{a.detail}</div>
                </div>
                <button type="button" style={linkBtn} onClick={() => onNavigate(a.section)} aria-label={`Open: ${a.title}`}>Open <ArrowRight size={13} /></button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(210px, 1fr))", gap: 12 }}>
        <Tile label="Guards on duty" value={shifts.onDuty} hint={shiftProgress(shifts)} tone={shifts.onDuty > 0 ? "ok" : "neutral"} icon={<Shield size={18} />} onClick={() => onNavigate("live")} />
        <Tile label="Open incidents" value={incidents.open} hint={incidents.criticalOpen > 0 ? `${incidents.criticalOpen} critical` : `${incidents.last7Days} in the last 7 days`} tone={toneForCount(incidents.criticalOpen, "bad")} icon={<AlertTriangle size={18} />} onClick={() => onNavigate("incidents")} />
        <Tile label="Control room queue" value={data.openAlarms} hint="alarms not yet resolved" tone={toneForCount(data.openAlarms, "warn")} icon={<Radio size={18} />} onClick={() => onNavigate("control-room")} />
        <Tile label="On site now" value={gate.onSite} hint={gate.overstayed > 0 ? `${gate.overstayed} overstayed` : `${gate.enteredToday} entered today`} tone={toneForCount(gate.overstayed, "warn")} icon={<DoorOpen size={18} />} onClick={() => onNavigate("gate-dashboard")} />
        <Tile label="Open complaints" value={complaints.open} hint={complaints.urgent > 0 ? `${complaints.urgent} urgent` : undefined} tone={toneForCount(complaints.urgent, "bad")} icon={<MessageSquareWarning size={18} />} onClick={() => onNavigate("complaints")} />
        <Tile label="Active sites" value={data.activeSites} tone="neutral" icon={<MapPin size={18} />} onClick={() => onNavigate("sites")} />
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))", gap: 16 }}>
        <section aria-label="On duty now" style={panel}>
          <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 10 }}>
            <span style={{ fontSize: 14, fontWeight: 700 }}>On duty now</span>
            <button type="button" style={linkBtn} onClick={() => onNavigate("shifts")}>All shifts <ArrowRight size={13} /></button>
          </div>
          {data.activeShifts.length === 0 ? (
            <div style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>No guards are on duty.</div>
          ) : (
            <div style={{ display: "grid", gap: 8 }}>
              {data.activeShifts.map(s => (
                <div key={s.id} style={{ display: "flex", alignItems: "center", gap: 10, padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 10 }}>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontWeight: 600, fontSize: 13 }}>{s.guardName ?? "Unknown guard"}</div>
                    <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{s.siteName ?? "No site"} · {fmtTime(s.startAt)} to {fmtTime(s.endAt)}</div>
                  </div>
                  <Chip tone={!s.actualStartAt ? "neutral" : isLate(s.minutesLate) ? "warn" : "ok"}>{punctualityLabel(s.minutesLate, s.actualStartAt)}</Chip>
                </div>
              ))}
              {shifts.onDuty > data.activeShifts.length && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>and {shifts.onDuty - data.activeShifts.length} more</div>}
            </div>
          )}
        </section>

        <section aria-label="Open incidents" style={panel}>
          <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 10 }}>
            <span style={{ fontSize: 14, fontWeight: 700 }}>Open incidents</span>
            <button type="button" style={linkBtn} onClick={() => onNavigate("incidents")}>All incidents <ArrowRight size={13} /></button>
          </div>
          {data.openIncidents.length === 0 ? (
            <div style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>No open incidents.</div>
          ) : (
            <div style={{ display: "grid", gap: 8 }}>
              {data.openIncidents.map(i => (
                <div key={i.id} style={{ display: "flex", alignItems: "center", gap: 10, padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 10 }}>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontWeight: 600, fontSize: 13 }}>{i.title}</div>
                    <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{fmtDate(i.createdAt)}{i.siteName ? ` · ${i.siteName}` : ""}{i.status === "OPEN" ? " · not acknowledged" : ""}</div>
                  </div>
                  <Chip tone={SEVERITY_TONE[i.severity?.toUpperCase()] ?? "neutral"}>{i.severity}</Chip>
                </div>
              ))}
            </div>
          )}
        </section>

        <section aria-label="Workforce" style={panel}>
          <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 10 }}>
            <span style={{ fontSize: 14, fontWeight: 700 }}>Workforce</span>
            <button type="button" style={linkBtn} onClick={() => onNavigate("guards")}>All guards <ArrowRight size={13} /></button>
          </div>
          {[
            { label: "Active guards", value: `${workforce.activeGuards} of ${workforce.totalGuards}`, tone: "neutral" as ChipTone },
            { label: "PSiRA expired", value: workforce.psiraExpired, tone: toneForCount(workforce.psiraExpired, "bad") },
            { label: "PSiRA expiring in 30 days", value: workforce.psiraExpiring, tone: toneForCount(workforce.psiraExpiring, "warn") },
            { label: "Required competencies expired", value: workforce.competenciesExpired, tone: toneForCount(workforce.competenciesExpired, "warn") },
            { label: "Required competencies expiring", value: workforce.competenciesExpiring, tone: toneForCount(workforce.competenciesExpiring, "info") },
          ].map(r => (
            <div key={r.label} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "7px 0", borderBottom: "1px solid var(--hf-border)", fontSize: 13 }}>
              <span style={{ color: "var(--hf-text-secondary)" }}>{r.label}</span>
              <Chip tone={r.tone}>{r.value}</Chip>
            </div>
          ))}
        </section>

        <section aria-label="Quick actions" style={panel}>
          <div style={{ fontSize: 14, fontWeight: 700, marginBottom: 10 }}>Quick actions</div>
          {[
            { label: "Schedule a shift", tab: "shifts" }, { label: "Report an incident", tab: "incidents" },
            { label: "Add a guard", tab: "guards" }, { label: "Open the live map", tab: "live" },
          ].map(a => (
            <button key={a.label} type="button" onClick={() => onNavigate(a.tab)}
              style={{ width: "100%", marginBottom: 8, padding: "9px 14px", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, fontWeight: 600, color: "var(--hf-text-primary)", cursor: "pointer", textAlign: "left", display: "flex", alignItems: "center", justifyContent: "space-between" }}>
              {a.label} <ArrowRight size={13} />
            </button>
          ))}
        </section>
      </div>
    </div>
  )
}
