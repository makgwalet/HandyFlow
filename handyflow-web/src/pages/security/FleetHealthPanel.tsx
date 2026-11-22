// src/pages/security/FleetHealthPanel.tsx
//
// Phones that need attention: silent while a guard is on shift, never connected, low battery, old app version.
import { useQuery } from "@tanstack/react-query"
import { Activity } from "lucide-react"
import { apiClient } from "../../api/client"
import Chip from "../../components/ui/Chip"
import { issueIsCritical, issueLabel, orderItems, sinceText, summaryText, type FleetHealth } from "./fleetHealthLogic"

const th: React.CSSProperties = { textAlign: "left", padding: "8px 10px", fontSize: 11, color: "var(--hf-text-muted)", fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.4 }
const td: React.CSSProperties = { padding: "9px 10px", fontSize: 13, borderTop: "1px solid var(--hf-border-subtle)" }

export default function FleetHealthPanel() {
  const { data, isLoading, error } = useQuery<FleetHealth>({
    queryKey: ["fleet-health"],
    queryFn: async () => (await apiClient.get("/api/v1/security/devices/fleet-health")).data?.data,
    refetchInterval: 60000,
  })

  return (
    <section aria-label="Phone health" style={{ marginBottom: 20, padding: 16, border: "1px solid var(--hf-border)", borderRadius: 12, background: "var(--hf-surface)" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 8, fontWeight: 700, color: "var(--hf-text-primary)" }}>
        <Activity size={16} /> Phone health
      </div>
      {isLoading && <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Checking phones…</div>}
      {error && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger)" }}>Could not load phone health.</div>}
      {data && (
        <>
          <div style={{ fontSize: 13, marginBottom: data.items.length ? 10 : 0, color: data.critical > 0 ? "var(--hf-danger)" : "var(--hf-text-secondary)", fontWeight: data.critical > 0 ? 700 : 500 }}>
            {summaryText(data)}
          </div>
          {data.items.length > 0 && (
            <div style={{ overflowX: "auto" }}>
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead><tr><th style={th}>Phone</th><th style={th}>Guard</th><th style={th}>Problem</th><th style={th}>Last heard</th><th style={th}>Battery</th><th style={th}>App</th></tr></thead>
                <tbody>
                  {orderItems(data.items).map(i => (
                    <tr key={i.deviceId}>
                      <td style={td}>{i.deviceName ?? "Unnamed phone"}</td>
                      <td style={td}>{i.guardName ?? (i.deviceType === "SHARED_SITE_DEVICE" ? "Shared" : "-")}{i.onSession ? " (on shift)" : ""}</td>
                      <td style={td}>
                        <span style={{ display: "inline-flex", gap: 4, flexWrap: "wrap" }}>
                          {i.issues.map(code => <Chip key={code} tone={issueIsCritical(code) ? "bad" : "warn"}>{issueLabel(code)}</Chip>)}
                        </span>
                      </td>
                      <td style={td}>{sinceText(i.lastSeenAt)}</td>
                      <td style={td}>{i.batteryPct == null ? "-" : `${i.batteryPct}%`}</td>
                      <td style={td}>{i.appVersion ?? "-"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </section>
  )
}
