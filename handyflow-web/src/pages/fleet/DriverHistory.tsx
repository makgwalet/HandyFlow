// src/pages/fleet/DriverHistory.tsx
// The History tab of a driver, and the "Recent activity" list on the Overview tab. Both read the same timeline.
import { TONE_COLOUR } from "./fleetTones"
import { Pager } from "./ListControls"
import { useListView } from "./listKit"
import { useDriverTimeline } from "./useDriverTimeline"
import type { TimelineAccident } from "./driverTimeline.logic"

const when = (iso: string) => new Date(iso).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })

function Line({ title, detail, at, tone }: { title: string; detail: string; at: string; tone: "ok" | "warn" | "bad" | "none" }) {
  return (
    <li style={{ display: "flex", gap: 10, padding: "8px 0", borderTop: "1px solid var(--hf-border-subtle)", fontSize: 13 }}>
      <span aria-hidden="true" style={{ width: 8, height: 8, borderRadius: 99, background: TONE_COLOUR[tone], marginTop: 6, flex: "none" }} />
      <div style={{ flex: 1 }}><strong>{title}</strong>{detail && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{detail}</div>}</div>
      <time style={{ fontSize: 12, color: "var(--hf-text-muted)", whiteSpace: "nowrap" }}>{when(at)}</time>
    </li>)
}

export function RecentActivity({ driverId, createdAt, accidents }: { driverId: string; createdAt: string | null | undefined; accidents: TimelineAccident[] }) {
  const t = useDriverTimeline(driverId, createdAt, accidents)
  return (
    <div aria-label="Recent activity">
      <h4 style={{ margin: "0 0 6px" }}>Recent activity</h4>
      {t.isError && <p role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)", margin: 0 }}>Couldn't load the activity.</p>}
      {!t.isLoading && !t.isError && t.items.length === 0 && <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: 0 }}>Nothing recorded yet.</p>}
      <ul style={{ listStyle: "none", margin: 0, padding: 0 }}>{t.items.slice(0, 5).map(i => <Line key={i.key} {...i} />)}</ul>
    </div>)
}

export default function DriverHistory({ driverId, createdAt, accidents }: { driverId: string; createdAt: string | null | undefined; accidents: TimelineAccident[] }) {
  const t = useDriverTimeline(driverId, createdAt, accidents)
  const view = useListView(t.items, i => `${i.title} ${i.detail}`)
  return (
    <div>
      {t.isLoading && <p style={{ fontSize: 13 }}>Loading…</p>}
      {t.isError && <p role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>Couldn't load the history.</p>}
      <ul style={{ listStyle: "none", margin: 0, padding: 0 }}>{view.paged.rows.map(i => <Line key={i.key} {...i} />)}</ul>
      <Pager paged={view.paged} onPage={view.setPage} />
      <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", marginBottom: 0 }}>Shows registration, documents added or replaced, and accidents. Trips and inspections are on the Performance tab.</p>
    </div>)
}
