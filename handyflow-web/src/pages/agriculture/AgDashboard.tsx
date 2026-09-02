// src/pages/agriculture/AgDashboard.tsx
//
// Tenant-wide KPIs from list-endpoint totals only — same discipline as
// every dashboard this session (no invented cross-entity endpoint, no
// N+1). Active farms and active species come straight from their own
// list totals. There is no tenant-wide "all animals"/"all groups"/
// "low-stock items" endpoint (both are farm-scoped resources), so those
// figures are deliberately NOT shown here — a true fleet-wide rollup is a
// natural Cost Reporting / dashboard-v2 addition once Crops ships,
// flagged rather than faked with a client-side loop over every farm.
import type React from "react"
import { useQuery } from "@tanstack/react-query"
import { Tractor, PawPrint } from "lucide-react"
import { apiClient } from "../../api/client"
import { AG_ACCENT } from "./constants"

interface Page<T> { content: T[]; totalElements: number }

const card: React.CSSProperties = { background: "#fff", border: "1px solid #E2E8F0", borderRadius: 14, padding: "18px 20px" }
const label: React.CSSProperties = { fontSize: 11, fontWeight: 700, color: "#94A3B8", letterSpacing: 0.4, textTransform: "uppercase", margin: "0 0 8px" }
const value: React.CSSProperties = { fontSize: 26, fontWeight: 800, color: "#0F172A", margin: 0 }

export default function AgDashboard() {
  const { data: farms } = useQuery<Page<unknown>>({
    queryKey: ["ag-farms", "ACTIVE", "dashboard"],
    queryFn: async () => (await apiClient.get("/api/v1/agriculture/farms", { params: { status: "ACTIVE", size: 1 } })).data,
  })
  const { data: species } = useQuery<Page<unknown>>({
    queryKey: ["ag-species", "ACTIVE", "dashboard"],
    queryFn: async () => (await apiClient.get("/api/v1/agriculture/species", { params: { size: 1 } })).data,
  })

  return (
    <div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(2, 1fr)", gap: 14, marginBottom: 20 }}>
        <div style={card}>
          <p style={label}>Active farms</p>
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
            <p style={value}>{farms?.totalElements ?? "—"}</p>
            <Tractor size={22} color={AG_ACCENT} />
          </div>
        </div>
        <div style={card}>
          <p style={label}>Species catalogued</p>
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
            <p style={value}>{species?.totalElements ?? "—"}</p>
            <PawPrint size={22} color={AG_ACCENT} />
          </div>
        </div>
      </div>

      <div style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 12, padding: "16px 18px" }}>
        <p style={{ fontSize: 13, fontWeight: 700, color: "#0F172A", margin: "0 0 6px" }}>Getting started</p>
        <p style={{ fontSize: 12.5, color: "#64748B", margin: 0, lineHeight: 1.6 }}>
          Register a farm, add species to the catalogue, then open a farm to add production areas, enterprises,
          and register your animals or groups. Feed/health/breeding/movement/mortality history and evidence
          photos live under each animal or group. Crop cycles and cost-reporting views ship as a follow-up
          delivery, matching this module's own backend rollout.
        </p>
      </div>
    </div>
  )
}
