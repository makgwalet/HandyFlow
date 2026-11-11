// src/pages/events/EventOverviewTab.tsx
//
// The landing page of one event: where it stands, the quick actions that apply right now, the figures that matter, what is still outstanding, and shortcuts to
// every working screen. Everything shown is read with plain event read access; each action needs its own permission and the server explains any refusal.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { btnGhost, btnPrimary, card, ErrorLine, inputStyle } from "../warehousing/whseUi"
import { CancelEventModal, DuplicateModal, EventFormModal, PostponeModal } from "./EventDialogs"
import { statusLabel } from "./dashboardView"
import { capacityLine, failedReadiness, nextStep, quickActions, shortcuts, type ReadinessCheck } from "./overviewOps"

const fmt = (d?: string | null) => (d ? new Date(d).toLocaleString("en-ZA", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" }) : "–")

export default function EventOverviewTab({ eventId, onChangeEvent, goTo }: { eventId: string | null; onChangeEvent: () => void; goTo: (id: string) => void }) {
  const qc = useQueryClient()
  const perms = {
    write: usePermissions(["EVENTS_WRITE", "EVENTS_MANAGE"]), approve: usePermissions(["EVENTS_APPROVE", "EVENTS_MANAGE"]), cancel: usePermissions(["EVENTS_CANCEL", "EVENTS_MANAGE"]),
    ops: usePermissions(["EVENTS_OPS_READ", "EVENTS_OPS_MANAGE", "EVENTS_MANAGE"]), incident: usePermissions(["EVENTS_INCIDENT_REPORT", "EVENTS_CHECKIN"]),
    budget: usePermissions(["EVENTS_BUDGET_READ", "EVENTS_BUDGET_MANAGE", "EVENTS_SUPPLIER_MANAGE", "EVENTS_MANAGE"]), payments: usePermissions(["EVENTS_PAYMENTS_READ", "EVENTS_PAYMENTS_MANAGE", "EVENTS_BUDGET_READ", "EVENTS_MANAGE"]),
    exportPii: usePermissions(["EVENTS_EXPORT", "EVENTS_MANAGE"]),
  }
  const [dlg, setDlg] = useState<"edit" | "postpone" | "copy" | "cancel" | null>(null)
  const base = `/api/v1/events/${eventId}`
  const useQ = <T,>(key: string, path: string) => useQuery<T>({ queryKey: [key, eventId], enabled: !!eventId, queryFn: async () => { const r = await apiClient.get(path); return r.data?.data ?? r.data } })
  const { data: ev, error } = useQ<any>("event-overview", base)
  const { data: stats } = useQ<{ totalRegistered: number; totalCheckedIn: number; totalCancelled: number; totalVendors: number; confirmedVendors: number }>("event-overview-stats", `${base}/stats`)
  const { data: reg } = useQ<{ registered: number; venueCapacity: number | null; seatsRemaining: number | null; waitlisted: number; paymentsPending: number }>("event-dash-registration", `${base}/dashboard/registration`)
  const { data: ready } = useQ<{ ready: boolean; checks: ReadinessCheck[] }>("event-readiness", `${base}/readiness`)
  const go = useMutation({
    mutationFn: (action: string) => apiClient.post(`${base}/${action}`),
    onSuccess: () => ["event-overview", "event-overview-stats", "event-readiness", "events", "events-kpi", "event-audit"].forEach(k => qc.invalidateQueries({ queryKey: [k] })),
  })

  if (!eventId) return <div style={{ padding: 30, textAlign: "center", color: "var(--hf-text-muted)" }}>Pick an event first. <button type="button" style={inputStyle} onClick={onChangeEvent}>Choose event</button></div>
  if (error) return <ErrorLine error={error} fallback="The event could not be loaded" />
  if (!ev) return <p>Loading…</p>
  const failed = failedReadiness(ready?.checks)
  const actions = quickActions(ev.status, perms)
  const tiles: [string, string][] = [
    ["Registered", reg ? capacityLine(reg.registered, reg.venueCapacity, reg.seatsRemaining) : "–"],
    ["Checked in", stats ? String(stats.totalCheckedIn) : "–"],
    ["Waitlist", reg ? String(reg.waitlisted) : "–"],
    ["Vendors confirmed", stats ? `${stats.confirmedVendors} of ${stats.totalVendors}` : "–"],
    ["Payments pending", reg ? String(reg.paymentsPending) : "–"],
  ]
  return (
    <div>
      <section style={{ ...card, padding: 16, marginBottom: 14 }}>
        <div style={{ display: "flex", justifyContent: "space-between", gap: 10, flexWrap: "wrap" }}>
          <div>
            <h3 style={{ margin: 0, fontSize: 18 }}>{ev.title}</h3>
            <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>{ev.eventNumber} · {statusLabel(ev.status)}{ev.isPrivate ? " · Private" : ""}{ev.isFree ? " · Free" : ""}</div>
            <div style={{ fontSize: 13, marginTop: 4 }}>{fmt(ev.startDatetime)} to {fmt(ev.endDatetime)}</div>
            {(ev.venueName || ev.venueAddress) && <div style={{ fontSize: 13 }}>{[ev.venueName, ev.venueAddress].filter(Boolean).join(", ")}</div>}
          </div>
        </div>
        <p role="status" style={{ margin: "10px 0 0", fontSize: 13, fontWeight: 600 }}>{nextStep(ev.status, failed.length)}</p>
        <ErrorLine error={go.error} fallback="That action could not be completed" />
        {actions.length > 0 && <div role="group" aria-label="Quick actions" style={{ display: "flex", gap: 8, flexWrap: "wrap", marginTop: 12 }}>
          {actions.map(a => <button key={a.id} type="button" style={a.kind === "transition" ? btnPrimary : btnGhost} disabled={go.isPending} onClick={() => (a.kind === "transition" ? go.mutate(a.id) : setDlg(a.id))}>{a.label}</button>)}
        </div>}
      </section>

      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 14 }}>
        {tiles.map(([k, v]) => <div key={k} style={{ ...card, padding: "10px 14px", minWidth: 150 }}><div style={{ fontSize: 15, fontWeight: 800 }}>{v}</div><div style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>{k}</div></div>)}
      </div>

      {failed.length > 0 && <section style={{ ...card, padding: 14, marginBottom: 14 }}>
        <h4 style={{ margin: "0 0 6px", fontSize: 14 }}>Still to sort out</h4>
        <ul aria-label="Outstanding" style={{ margin: 0, paddingLeft: 18, fontSize: 13 }}>{failed.map(c => <li key={c.code}><strong>{c.label}</strong>{c.detail ? `: ${c.detail}` : ""}</li>)}</ul>
      </section>}

      <section aria-label="Shortcuts" style={{ ...card, padding: 14 }}>
        <h4 style={{ margin: "0 0 8px", fontSize: 14 }}>Go to</h4>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>{shortcuts(ev.status, perms).map(s => <button key={s.id} type="button" style={btnGhost} onClick={() => goTo(s.id)}>{s.label}</button>)}</div>
      </section>

      {dlg === "edit" && <EventFormModal event={ev} onClose={() => { setDlg(null); qc.invalidateQueries({ queryKey: ["event-overview"] }) }} />}
      {dlg === "postpone" && <PostponeModal event={ev} onClose={() => { setDlg(null); qc.invalidateQueries({ queryKey: ["event-overview"] }) }} />}
      {dlg === "copy" && <DuplicateModal event={ev} onClose={() => setDlg(null)} />}
      {dlg === "cancel" && <CancelEventModal event={ev} onClose={() => { setDlg(null); qc.invalidateQueries({ queryKey: ["event-overview"] }) }} />}
    </div>
  )
}
