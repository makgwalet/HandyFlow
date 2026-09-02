// src/pages/warehousing/WhseDashboard.tsx
//
// KPIs computed client-side from GET /clients/all + GET /locations only
// — same "avoid N+1 per-client fetches" deliberate choice as
// CollAgencyDashboard.tsx. Inbound shipments/outbound orders/billing
// invoices have no cross-client staff-side listing endpoint in this
// module (confirmed: WhseInboundShipmentController/
// WhseOutboundOrderController/WhseBillingController only expose
// list-for-one-client, nested under /clients/{clientId}/...), so this
// dashboard deliberately does not attempt to summarise those — a
// per-client N+1 fan-out to build one dashboard number isn't worth the
// request count. Drill into a client's own detail tabs for that.
import { useQuery } from "@tanstack/react-query"
import { Building2, MapPin, Percent } from "lucide-react"
import { apiClient } from "../../api/client"
import { WHSE_ACCENT } from "./constants"

interface ClientResponse { id: string; tradingName: string; status: string }
interface LocationResponse { id: string; code: string; zone: string | null; active: boolean }
interface ProfileResponse { warehouseName: string | null; defaultStorageRatePerUnitPerMonth: number | null }

const cardStyle: React.CSSProperties = { background: "#fff", border: "1px solid #E8EDF5", borderRadius: 14, padding: 20 }

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
        <p style={{ fontSize: 13, color: "#64748B", marginBottom: 18 }}>
          Operating as <strong style={{ color: "#0F172A" }}>{profile.warehouseName}</strong>
        </p>
      )}

      {loading ? (
        <p style={{ color: "#94A3B8", fontSize: 13 }}>Loading…</p>
      ) : (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: 14 }}>
          <div style={cardStyle}>
            <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 10 }}>
              <Building2 size={16} color={WHSE_ACCENT} />
              <p style={{ fontSize: 11, color: "#94A3B8", margin: 0, fontWeight: 700, textTransform: "uppercase" }}>Active clients</p>
            </div>
            <p style={{ fontSize: 26, fontWeight: 800, color: "#0F172A", margin: 0 }}>{activeClients}</p>
            <p style={{ fontSize: 11.5, color: "#94A3B8", margin: "4px 0 0" }}>{clients.length} total onboarded</p>
          </div>
          <div style={cardStyle}>
            <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 10 }}>
              <MapPin size={16} color={WHSE_ACCENT} />
              <p style={{ fontSize: 11, color: "#94A3B8", margin: 0, fontWeight: 700, textTransform: "uppercase" }}>Active locations</p>
            </div>
            <p style={{ fontSize: 26, fontWeight: 800, color: "#0F172A", margin: 0 }}>{activeLocations}</p>
            <p style={{ fontSize: 11.5, color: "#94A3B8", margin: "4px 0 0" }}>{locations.length} total bins/zones</p>
          </div>
          {profile?.defaultStorageRatePerUnitPerMonth != null && (
            <div style={cardStyle}>
              <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 10 }}>
                <Percent size={16} color={WHSE_ACCENT} />
                <p style={{ fontSize: 11, color: "#94A3B8", margin: 0, fontWeight: 700, textTransform: "uppercase" }}>Default storage rate</p>
              </div>
              <p style={{ fontSize: 26, fontWeight: 800, color: "#0F172A", margin: 0 }}>
                R{Number(profile.defaultStorageRatePerUnitPerMonth).toFixed(2)}
              </p>
              <p style={{ fontSize: 11.5, color: "#94A3B8", margin: "4px 0 0" }}>per unit / month, before client overrides</p>
            </div>
          )}
        </div>
      )}

      {!loading && clients.length === 0 && (
        <p style={{ color: "#94A3B8", fontSize: 13, marginTop: 20 }}>
          No clients onboarded yet — head to the Clients tab to onboard your first 3PL client.
        </p>
      )}
    </div>
  )
}
