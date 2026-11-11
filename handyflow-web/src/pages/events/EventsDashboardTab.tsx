// src/pages/events/EventsDashboardTab.tsx
//
// The module's front page: how many events are in each state, what is live or coming up (with how full it is), and what needs attention. Reads
// /events/dashboard/overview (EVENTS_REPORTS_READ). Opening an event takes you to its guests.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { card, ErrorLine, btnGhost } from "../warehousing/whseUi"
import { attentionTitle, barPercent, budgetStatusText, daysText, fillText, sortAttention, STATUS_ORDER, statusLabel, type Overview } from "./dashboardView"

export default function EventsDashboardTab({ onOpenEvent }: { onOpenEvent: (id: string, title: string) => void }) {
  const allowed = usePermissions(["EVENTS_REPORTS_READ", "EVENTS_MANAGE"])
  const { data, isLoading, error } = useQuery<Overview>({
    queryKey: ["events-overview"], enabled: allowed, refetchInterval: 60000,
    queryFn: async () => { const r = await apiClient.get("/api/v1/events/dashboard/overview"); return r.data?.data ?? r.data },
  })
  if (!allowed) return <p style={{ padding: 20, color: "var(--hf-text-muted)" }}>You do not have access to the events dashboard.</p>
  if (isLoading) return <p>Loading…</p>
  const counts = data?.statusCounts ?? {}
  const upcoming = data?.upcoming ?? []
  const attention = sortAttention(data?.attention ?? [])
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <ErrorLine error={error} fallback="The dashboard could not be loaded" />
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
        {STATUS_ORDER.map(s => (
          <div key={s} style={{ ...card, padding: "10px 16px", minWidth: 120 }}>
            <div style={{ fontSize: 22, fontWeight: 800 }}>{counts[s] ?? 0}</div>
            <div style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>{statusLabel(s)}</div>
          </div>
        ))}
      </div>

      <section>
        <h3 style={{ fontSize: 14, margin: "0 0 8px" }}>Needs attention</h3>
        {attention.length === 0 ? <p style={{ color: "var(--hf-text-muted)", fontSize: 13 }}>Nothing needs attention right now.</p> : (
          <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
            {attention.map((a, i) => (
              <div key={`${a.type}-${a.eventId}-${i}`} style={{ ...card, padding: "8px 14px", borderLeft: `3px solid ${a.severity === "WARNING" ? "var(--hf-warning)" : "var(--hf-sky)"}`, display: "flex", justifyContent: "space-between", gap: 10, flexWrap: "wrap" }}>
                <span style={{ fontSize: 13 }}><strong>{attentionTitle(a.type)}</strong> · {a.message}{a.eventNumber ? ` (${a.eventNumber})` : ""}</span>
              </div>
            ))}
          </div>
        )}
      </section>

      <section>
        <h3 style={{ fontSize: 14, margin: "0 0 8px" }}>Live and upcoming</h3>
        {upcoming.length === 0 ? <p style={{ color: "var(--hf-text-muted)", fontSize: 13 }}>No live or upcoming events.</p> : (
          <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
            {upcoming.map(e => (
              <div key={e.id} style={{ ...card, padding: "10px 14px" }}>
                <div style={{ display: "flex", justifyContent: "space-between", gap: 10, flexWrap: "wrap", alignItems: "center" }}>
                  <div>
                    <div style={{ fontWeight: 700, fontSize: 13.5 }}>{e.title} <span style={{ fontWeight: 400, color: "var(--hf-text-faint)", fontSize: 11 }}>{e.eventNumber}</span></div>
                    <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
                      {statusLabel(e.status)} · {daysText(e.daysUntil)} · {new Date(e.startDatetime).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })} · Budget: {budgetStatusText(e.budgetStatus)}
                    </div>
                  </div>
                  <button type="button" style={btnGhost} onClick={() => onOpenEvent(e.id, e.title)}>Open guests</button>
                </div>
                <div style={{ marginTop: 8 }}>
                  <div style={{ fontSize: 12, marginBottom: 3 }}>{fillText(e.registered, e.venueCapacity, e.fillPercent)}</div>
                  {e.venueCapacity != null && (
                    <div role="progressbar" aria-valuenow={barPercent(e.registered, e.venueCapacity)} aria-valuemin={0} aria-valuemax={100} style={{ height: 6, background: "var(--hf-surface-sunken)", borderRadius: 4 }}>
                      <div style={{ width: `${barPercent(e.registered, e.venueCapacity)}%`, height: "100%", background: "var(--hf-sky)", borderRadius: 4 }} />
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  )
}
