import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { card } from "../warehousing/whseUi"
import { onlyOverrides, overrideLine, type OverrideRow } from "./eligibilityOps"

const unwrap = (r: any) => r.data?.data ?? r.data

/** Read-only log of loads allocated with eligibility review points open: who decided, why, and what was open. */
export default function OverrideLog() {
  const q = useQuery<OverrideRow[]>({ queryKey: ["fuel-eligibility-overrides"], queryFn: async () => onlyOverrides(unwrap(await apiClient.get("/api/v1/fuel/eligibility/overrides"))) })
  const rows = q.data ?? []
  return (
    <div style={{ ...card, padding: 16 }}>
      <h3 style={{ margin: "0 0 4px", fontSize: 14 }}>Loads allocated with review points open</h3>
      <p style={{ margin: "0 0 12px", fontSize: 12.5, color: "var(--hf-text-muted)" }}>
        Each entry is a decision by a person with the review permission, with their reason. A block, such as an expired licence, can never be overridden, so it never appears here.
      </p>
      {q.isLoading ? <p style={{ fontSize: 13 }}>Loading…</p> : rows.length === 0 ? <p style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>No loads have been allocated this way.</p> : rows.map(o => (
        <div key={o.id} style={{ padding: "8px 0", borderTop: "1px solid var(--hf-border)", fontSize: 13 }}>
          <strong>{overrideLine(o)}</strong>
          <div style={{ color: "var(--hf-text-muted)", fontSize: 12 }}>Open: {o.findings}</div>
          <div style={{ fontSize: 12 }}>Reason: {o.reason}</div>
        </div>
      ))}
    </div>
  )
}
