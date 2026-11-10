// src/pages/warehousing/WhseDashboard.tsx
//
// Two parts. The first cards (clients, locations, default rate) are computed from GET /clients/all and GET /locations. Below them the
// operational tiles read the cross-client operations endpoints (/operations/summary, /operations/stock-alerts, /tasks/summary), each
// on its own query so one failing leaves the others on screen. Stock value is shown one line per currency and never summed.
import { useQuery } from "@tanstack/react-query"
import { Building2, MapPin, Percent } from "lucide-react"
import { apiClient } from "../../api/client"
import { WHSE_ACCENT_TEXT } from "./constants"
import { formatMoney } from "./itemForm"
import { fmtQty } from "./whseUi"

interface ClientResponse { id: string; tradingName: string; status: string }
interface LocationResponse { id: string; code: string; zone: string | null; active: boolean }
interface ProfileResponse { warehouseName: string | null; defaultStorageRatePerUnitPerMonth: number | null }

export interface OpsSummary {
  totalUnitsOnHand: number; totalUnitsAvailable: number; stockedPositions: number
  work: { expectedInbound: number; partiallyReceivedInbound: number; overdueInbound: number; pendingOutbound: number; pickingOutbound: number; packedOutbound: number; overdueOutbound: number }
  today: { unitsReceived: number; unitsDispatched: number; inboundReceived: number; ordersShipped: number; adjustments: number }
  adjustmentsAwaitingApproval: number; invoicesAwaitingApproval: number
  stockAccuracyPct: number | null; linesCountedLast30Days: number
  stockValue: { currency: string; value: number }[]; unitsWithoutCost: number
}
export interface StockAlert { clientName: string; sku: string; description: string | null; alert: "BELOW_MIN" | "ABOVE_MAX"; available: number; minQty: number | null; maxQty: number | null; toOrder: number | null }
interface TaskSummary { openPicks: number; inProgressPicks: number; shortPicks: number; openPutaways: number; inProgressPutaways: number }

function Tile({ label, value, sub, tone }: { label: string; value: React.ReactNode; sub?: React.ReactNode; tone?: "bad" | "warn" }) {
  const color = tone === "bad" ? "var(--hf-danger-text)" : tone === "warn" ? "var(--hf-warning-text-deep)" : "var(--hf-text)"
  return (
    <div style={cardStyle}>
      <p style={{ fontSize: 11, color: "var(--hf-text-faint)", margin: 0, fontWeight: 700, textTransform: "uppercase" }}>{label}</p>
      <div style={{ fontSize: 24, fontWeight: 800, color, margin: "8px 0 0" }}>{value}</div>
      {sub && <div style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: "4px 0 0" }}>{sub}</div>}
    </div>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section style={{ marginTop: 22 }}>
      <h3 style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text-secondary)", margin: "0 0 10px" }}>{title}</h3>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: 14 }}>{children}</div>
    </section>
  )
}

/** The operational tiles: today, stock, work in flight, tasks and what needs attention. Each reads its own endpoint, so one failing leaves the rest. */
function OperationsTiles() {
  const { data: s } = useQuery<OpsSummary>({ queryKey: ["whse-ops-summary"], queryFn: async () => (await apiClient.get("/api/v1/warehousing/operations/summary")).data })
  const { data: t } = useQuery<TaskSummary>({ queryKey: ["whse-task-summary"], queryFn: async () => (await apiClient.get("/api/v1/warehousing/tasks/summary")).data })
  const { data: alerts = [] } = useQuery<StockAlert[]>({ queryKey: ["whse-stock-alerts"], queryFn: async () => (await apiClient.get("/api/v1/warehousing/operations/stock-alerts")).data })
  const below = alerts.filter(a => a.alert === "BELOW_MIN")
  const above = alerts.filter(a => a.alert === "ABOVE_MAX")
  return (
    <>
      {s && (
        <>
          <Section title="Today">
            <Tile label="Received" value={fmtQty(s.today.unitsReceived)} sub={`${s.today.inboundReceived} receipt${s.today.inboundReceived === 1 ? "" : "s"}`} />
            <Tile label="Dispatched" value={fmtQty(s.today.unitsDispatched)} sub={`${s.today.ordersShipped} order${s.today.ordersShipped === 1 ? "" : "s"} shipped`} />
            <Tile label="Adjustments" value={s.today.adjustments} />
          </Section>
          <Section title="Stock">
            <Tile label="On hand" value={fmtQty(s.totalUnitsOnHand)} sub={`${fmtQty(s.totalUnitsAvailable)} available · ${s.stockedPositions} positions`} />
            <Tile label="Stock value" value={s.stockValue.length === 0 ? "—" : s.stockValue.map(v => <div key={v.currency}>{formatMoney(v.value, v.currency)}</div>)}
              sub={s.unitsWithoutCost > 0 ? `${fmtQty(s.unitsWithoutCost)} units have no cost and are not valued` : s.stockValue.length > 1 ? "One line per currency, never added together" : undefined}
              tone={s.unitsWithoutCost > 0 ? "warn" : undefined} />
            <Tile label="Count accuracy (30 days)" value={s.stockAccuracyPct == null ? "—" : `${s.stockAccuracyPct}%`}
              sub={s.linesCountedLast30Days === 0 ? "No lines counted yet" : `${s.linesCountedLast30Days} lines counted`}
              tone={s.stockAccuracyPct != null && s.stockAccuracyPct < 95 ? "warn" : undefined} />
          </Section>
          <Section title="Work in flight">
            <Tile label="Inbound" value={s.work.expectedInbound + s.work.partiallyReceivedInbound} sub={s.work.overdueInbound > 0 ? `${s.work.overdueInbound} overdue` : "none overdue"} tone={s.work.overdueInbound > 0 ? "bad" : undefined} />
            <Tile label="Outbound" value={s.work.pendingOutbound + s.work.pickingOutbound + s.work.packedOutbound}
              sub={`${s.work.pendingOutbound} pending · ${s.work.pickingOutbound} picking · ${s.work.packedOutbound} packed${s.work.overdueOutbound > 0 ? ` · ${s.work.overdueOutbound} overdue` : ""}`}
              tone={s.work.overdueOutbound > 0 ? "bad" : undefined} />
          </Section>
        </>
      )}
      {t && (
        <Section title="Tasks">
          <Tile label="Picks" value={t.openPicks + t.inProgressPicks} sub={`${t.openPicks} open · ${t.inProgressPicks} in progress`} />
          <Tile label="Short picks" value={t.shortPicks} sub={t.shortPicks > 0 ? "Holding orders back: resolve them in Tasks" : "none"} tone={t.shortPicks > 0 ? "bad" : undefined} />
          <Tile label="Put-aways" value={t.openPutaways + t.inProgressPutaways} sub={`${t.openPutaways} open · ${t.inProgressPutaways} in progress`} />
        </Section>
      )}
      {(s || alerts.length > 0) && (
        <Section title="Needs attention">
          <Tile label="Below minimum" value={below.length} sub={below.slice(0, 3).map(a => `${a.sku} (${fmtQty(a.available)}/${fmtQty(a.minQty ?? 0)})`).join(", ") || "none"} tone={below.length > 0 ? "bad" : undefined} />
          <Tile label="Above maximum" value={above.length} sub={above.slice(0, 3).map(a => a.sku).join(", ") || "none"} tone={above.length > 0 ? "warn" : undefined} />
          {s && <Tile label="Awaiting approval" value={s.adjustmentsAwaitingApproval + s.invoicesAwaitingApproval} sub={`${s.adjustmentsAwaitingApproval} adjustments · ${s.invoicesAwaitingApproval} invoices`} tone={s.adjustmentsAwaitingApproval + s.invoicesAwaitingApproval > 0 ? "warn" : undefined} />}
        </Section>
      )}
    </>
  )
}

const cardStyle: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-primary-border)", borderRadius: 14, padding: 20 }

export default function WhseDashboard() {
  const { data: clients = [], isLoading: clientsLoading } = useQuery<ClientResponse[]>({
    queryKey: ["whse-clients-all"],
    queryFn: async () => (await apiClient.get("/api/v1/warehousing/clients/all")).data,
  })
  const { data: locations = [], isLoading: locationsLoading } = useQuery<LocationResponse[]>({
    queryKey: ["whse-locations"],
    queryFn: async () => (await apiClient.get("/api/v1/warehousing/locations")).data,
  })
  const { data: profile } = useQuery<ProfileResponse | null>({
    queryKey: ["whse-profile"],
    queryFn: async () => (await apiClient.get("/api/v1/warehousing/profile")).data,
  })

  const activeClients = clients.filter(c => c.status === "ACTIVE").length
  const activeLocations = locations.filter(l => l.active).length
  const loading = clientsLoading || locationsLoading

  return (
    <div>
      {profile?.warehouseName && (
        <p style={{ fontSize: 13, color: "var(--hf-text-muted)", marginBottom: 18 }}>
          Operating as <strong style={{ color: "var(--hf-text)" }}>{profile.warehouseName}</strong>
        </p>
      )}

      {loading ? (
        <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>Loading…</p>
      ) : (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: 14 }}>
          <div style={cardStyle}>
            <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 10 }}>
              <Building2 size={16} style={{ color: WHSE_ACCENT_TEXT }} />
              <p style={{ fontSize: 11, color: "var(--hf-text-faint)", margin: 0, fontWeight: 700, textTransform: "uppercase" }}>Active clients</p>
            </div>
            <p style={{ fontSize: 26, fontWeight: 800, color: "var(--hf-text)", margin: 0 }}>{activeClients}</p>
            <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: "4px 0 0" }}>{clients.length} total onboarded</p>
          </div>
          <div style={cardStyle}>
            <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 10 }}>
              <MapPin size={16} style={{ color: WHSE_ACCENT_TEXT }} />
              <p style={{ fontSize: 11, color: "var(--hf-text-faint)", margin: 0, fontWeight: 700, textTransform: "uppercase" }}>Active locations</p>
            </div>
            <p style={{ fontSize: 26, fontWeight: 800, color: "var(--hf-text)", margin: 0 }}>{activeLocations}</p>
            <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: "4px 0 0" }}>{locations.length} total bins/zones</p>
          </div>
          {profile?.defaultStorageRatePerUnitPerMonth != null && (
            <div style={cardStyle}>
              <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 10 }}>
                <Percent size={16} style={{ color: WHSE_ACCENT_TEXT }} />
                <p style={{ fontSize: 11, color: "var(--hf-text-faint)", margin: 0, fontWeight: 700, textTransform: "uppercase" }}>Default storage rate</p>
              </div>
              <p style={{ fontSize: 26, fontWeight: 800, color: "var(--hf-text)", margin: 0 }}>
                R{Number(profile.defaultStorageRatePerUnitPerMonth).toFixed(2)}
              </p>
              <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: "4px 0 0" }}>per unit / month, before client overrides</p>
            </div>
          )}
        </div>
      )}

      <OperationsTiles />

      {!loading && clients.length === 0 && (
        <p style={{ color: "var(--hf-text-faint)", fontSize: 13, marginTop: 20 }}>
          No clients onboarded yet — head to the Clients tab to onboard your first 3PL client.
        </p>
      )}
    </div>
  )
}
