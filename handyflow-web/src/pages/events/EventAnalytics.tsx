// src/pages/events/EventAnalytics.tsx
//
// Numbers for the selected event, from the server's dashboards (exact at any size; the previous version added up the first 500 guests in the browser):
// registration (capacity, ticket types, where registrations came from, per day) and, while the door is open, live attendance that refreshes every 15
// seconds. Needs EVENTS_REPORTS_READ or plain EVENTS_READ.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { card, ErrorLine } from "../warehousing/whseUi"
import { bucketLabel, barPercent, fillText } from "./dashboardView"
import { checkInView } from "./guestOps"
import { rand } from "./paymentOps"

interface Reg {
  status: string; registered: number; venueCapacity: number | null; fillPercent: number | null; seatsRemaining: number | null; waitlisted: number; cancelled: number; transferred: number
  checkedIn: number; bySource: Record<string, number>; amountRecorded: number; paymentsPending: number
  tiers: { id: string; name: string; price: number; quantity: number; sold: number; available: number; checkedIn: number; faceValueSold: number }[]
  registrationsByDay: { date: string; count: number; cumulative: number }[]
}
interface Att {
  expected: number; checkedIn: number; remaining: number; checkInRatePercent: number | null; walkIns: number; scanResults: Record<string, number>
  byTier: { tierId: string; tierName: string; registered: number; checkedIn: number }[]; arrivalsByQuarterHour: { start: string; count: number }[]
}

const Tile = ({ k, v }: { k: string; v: string | number }) => <div style={{ ...card, padding: "10px 16px", minWidth: 130 }}><div style={{ fontSize: 20, fontWeight: 800 }}>{v}</div><div style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>{k}</div></div>
const SOURCES: Record<string, string> = { STAFF: "Added by staff", GROUP: "Group", WALK_IN: "Walk-in", IMPORT: "Imported", WAITLIST: "From waitlist", TRANSFER: "Transferred" }

function Bars({ rows, label }: { rows: { key: string; label: string; value: number }[]; label: string }) {
  const max = Math.max(1, ...rows.map(r => r.value))
  return (
    <div aria-label={label} style={{ display: "flex", flexDirection: "column", gap: 4 }}>
      {rows.map(r => (
        <div key={r.key} style={{ display: "grid", gridTemplateColumns: "120px 1fr 40px", gap: 8, alignItems: "center", fontSize: 12 }}>
          <span style={{ color: "var(--hf-text-muted)" }}>{r.label}</span>
          <div style={{ height: 8, background: "var(--hf-surface-sunken)", borderRadius: 4 }}><div style={{ width: `${barPercent(r.value, max)}%`, height: "100%", background: "var(--hf-sky)", borderRadius: 4 }} /></div>
          <span style={{ textAlign: "right" }}>{r.value}</span>
        </div>
      ))}
    </div>
  )
}

export default function EventAnalytics({ eventId }: { eventId: string | null }) {
  const allowed = usePermissions(["EVENTS_REPORTS_READ", "EVENTS_READ", "EVENTS_MANAGE"])
  const { data: reg, isLoading, error } = useQuery<Reg>({
    queryKey: ["event-dash-registration", eventId], enabled: !!eventId && allowed,
    queryFn: async () => { const r = await apiClient.get(`/api/v1/events/${eventId}/dashboard/registration`); return r.data?.data ?? r.data },
  })
  const { data: att } = useQuery<Att>({
    queryKey: ["event-dash-attendance", eventId], enabled: !!eventId && allowed && (reg?.status === "LIVE" || reg?.status === "COMPLETED"),
    refetchInterval: reg?.status === "LIVE" ? 15000 : false,
    queryFn: async () => { const r = await apiClient.get(`/api/v1/events/${eventId}/dashboard/attendance`); return r.data?.data ?? r.data },
  })
  if (!eventId) return <p style={{ padding: 30, textAlign: "center", color: "var(--hf-text-muted)" }}>Pick an event from the Events list first.</p>
  if (!allowed) return <p style={{ padding: 20, color: "var(--hf-text-muted)" }}>You do not have access to event analytics.</p>
  if (isLoading || !reg) return <div><ErrorLine error={error} fallback="The analytics could not be loaded" />{isLoading && <p>Loading…</p>}</div>
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
        <Tile k="Registered" v={fillText(reg.registered, reg.venueCapacity, reg.fillPercent)} />
        {reg.seatsRemaining != null && <Tile k="Places left" v={reg.seatsRemaining} />}
        <Tile k="Waitlisted" v={reg.waitlisted} /><Tile k="Cancelled" v={reg.cancelled} /><Tile k="Transferred" v={reg.transferred} />
        <Tile k="Payments recorded" v={rand(reg.amountRecorded)} />
        {reg.paymentsPending > 0 && <Tile k="Payments still pending" v={reg.paymentsPending} />}
      </div>

      {att && (
        <section style={{ ...card, padding: 14 }}>
          <h3 style={{ fontSize: 14, margin: "0 0 8px" }}>At the door {reg.status === "LIVE" ? "(live)" : ""}</h3>
          <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 10 }}>
            <Tile k="Checked in" v={att.checkedIn} /><Tile k="Still expected" v={att.remaining} />
            <Tile k="Check-in rate" v={att.checkInRatePercent == null ? "–" : `${Math.round(Number(att.checkInRatePercent))}%`} /><Tile k="Walk-ins" v={att.walkIns} />
          </div>
          <Bars label="Scan results" rows={Object.entries(att.scanResults).map(([k, v]) => ({ key: k, label: checkInView(k).title, value: v }))} />
          {att.arrivalsByQuarterHour.length > 0 && (
            <div style={{ marginTop: 10 }}><div style={{ fontSize: 12, fontWeight: 700, marginBottom: 4 }}>Arrivals per 15 minutes</div>
              <Bars label="Arrivals" rows={att.arrivalsByQuarterHour.map(b => ({ key: b.start, label: bucketLabel(b.start), value: b.count }))} /></div>
          )}
        </section>
      )}

      <section style={{ ...card, padding: 14 }}>
        <h3 style={{ fontSize: 14, margin: "0 0 8px" }}>Ticket types</h3>
        {reg.tiers.length === 0 ? <p style={{ color: "var(--hf-text-muted)", fontSize: 13 }}>No ticket types yet.</p> : (
          <Bars label="Sold by ticket type" rows={reg.tiers.map(t => ({ key: t.id, label: `${t.name} (${t.sold}/${t.quantity})`, value: t.sold }))} />
        )}
      </section>
      <section style={{ ...card, padding: 14 }}>
        <h3 style={{ fontSize: 14, margin: "0 0 8px" }}>Where registrations came from</h3>
        <Bars label="Sources" rows={Object.entries(reg.bySource).map(([k, v]) => ({ key: k, label: SOURCES[k] ?? k, value: v }))} />
      </section>
      {reg.registrationsByDay.length > 0 && (
        <section style={{ ...card, padding: 14 }}>
          <h3 style={{ fontSize: 14, margin: "0 0 8px" }}>Registrations per day</h3>
          <Bars label="Per day" rows={reg.registrationsByDay.map(d => ({ key: d.date, label: new Date(d.date).toLocaleDateString("en-ZA", { day: "numeric", month: "short" }), value: d.count }))} />
        </section>
      )}
    </div>
  )
}
