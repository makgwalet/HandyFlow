// src/pages/warehousing/WhsePackagePanel.tsx
//
// Your Warehousing package and how much of it you have used. Read-only: a platform administrator assigns the package.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { card } from "./whseUi"
import { enforcementText, limitText, percentUsed, periodText, priceText, upgradeText, windowText, type PackageUsage } from "./packageUsage"

export default function WhsePackagePanel() {
  const { data } = useQuery<PackageUsage>({ queryKey: ["whse-package"], queryFn: async () => (await apiClient.get("/api/v1/warehousing/package")).data })
  if (!data || !Array.isArray(data.meters)) return null
  return (
    <div style={{ ...card, padding: 14, marginTop: 16 }}>
      <h3 style={{ fontSize: 14, fontWeight: 700, margin: "0 0 4px" }}>Package and usage</h3>
      {!data.assigned || !data.pkg ? (
        <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0 }}>No package limits apply to this account.</p>
      ) : (
        <>
          <p style={{ fontSize: 12.5, margin: "0 0 2px" }}><strong>{data.pkg.name}</strong>: {priceText(data.pkg.monthlyPrice)}</p>
          <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 10px" }}>{enforcementText(data.enforcement)}</p>
          {windowText(data) && <p role="status" style={{ fontSize: 12, margin: "0 0 8px" }}>{windowText(data)}</p>}
          {data.note && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 8px" }}>{data.note}</p>}
          {periodText(data) && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 8px" }}>{periodText(data)}. Receipts and orders are counted once, when they are created; cancelled ones stay counted.</p>}
          <ul style={{ listStyle: "none", padding: 0, margin: 0, display: "grid", gap: 8 }}>
            {data.meters.map(m => (
              <li key={m.key} style={{ fontSize: 12.5 }}>
                <div style={{ display: "flex", justifyContent: "space-between" }}>
                  <span>{m.label}</span>
                  <span style={{ color: m.over ? "var(--hf-danger, #b42318)" : undefined, fontWeight: m.over ? 700 : 400 }}>
                    {m.used.toLocaleString("en-ZA")} {limitText(m.limit)}{m.extra ? ` (includes ${m.extra.toLocaleString("en-ZA")} extra allowed for now)` : ""}{m.over ? " (over)" : ""}
                  </span>
                </div>
                {m.limit != null && (
                  <div role="progressbar" aria-label={m.label} aria-valuenow={percentUsed(m)} aria-valuemin={0} aria-valuemax={100}
                       style={{ height: 6, borderRadius: 3, background: "var(--hf-border, #e5e7eb)", marginTop: 3 }}>
                    <div style={{ width: `${percentUsed(m)}%`, height: "100%", borderRadius: 3, background: m.over ? "var(--hf-danger, #b42318)" : "var(--hf-primary, #2563eb)" }} />
                  </div>
                )}
              </li>
            ))}
          </ul>
          {Array.isArray(data.upgradeOptions) && data.upgradeOptions.length > 0 && (
            <div style={{ marginTop: 12 }}>
              <h4 style={{ fontSize: 13, fontWeight: 700, margin: "0 0 4px" }}>Need more room?</h4>
              <ul style={{ margin: 0, paddingLeft: 18, display: "grid", gap: 4 }}>
                {data.upgradeOptions.map(o => <li key={o.code} style={{ fontSize: 12.5 }}>{upgradeText(o)}</li>)}
              </ul>
              <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "6px 0 0" }}>To move to a larger package, ask your administrator to contact HandyFlow. Your data is never removed when a package changes.</p>
            </div>
          )}
        </>
      )}
    </div>
  )
}
