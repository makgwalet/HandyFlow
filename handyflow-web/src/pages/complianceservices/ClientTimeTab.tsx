// src/pages/complianceservices/ClientTimeTab.tsx
//
// Hours recorded against this client in the last 90 days. Hours only; cost and margin are for administrators on the Profitability page.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { formatMinutes, isoDay, kindLabel, type TimeEntry } from "./timeRules"

const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T
type Staff = { id: string; firstName?: string; lastName?: string; email?: string }
const staffName = (s: Staff | undefined) => (s ? [s.firstName, s.lastName].filter(Boolean).join(" ") || s.email || "Someone" : "Someone")

export default function ClientTimeTab({ clientId }: { clientId: string }) {
  const [to] = useState(() => isoDay(new Date()))
  const [from] = useState(() => { const d = new Date(); d.setDate(d.getDate() - 89); return isoDay(d) })
  const { data = [], isLoading } = useQuery<TimeEntry[]>({
    queryKey: ["cs-time-client", clientId, from, to],
    queryFn: async () => unwrap<TimeEntry[]>(await apiClient.get(`/api/v1/compliance-services/clients/${clientId}/time-entries`, { params: { from, to } })),
  })
  const { data: staff = [] } = useQuery<Staff[]>({
    queryKey: ["cs-time-staff"],
    queryFn: async () => { const d = unwrap<Staff[] | { content?: Staff[] }>(await apiClient.get("/api/v1/identity/users")); return Array.isArray(d) ? d : d?.content ?? [] },
  })
  const active = data.filter(e => e.status === "ACTIVE")
  const sum = (f: (e: TimeEntry) => boolean) => active.filter(f).reduce((n, e) => n + e.minutes, 0)
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Hours recorded for this client in the last 90 days. Staff record their own time on the My time page.</div>
      {!isLoading && (
        <div style={{ display: "flex", gap: 10, flexWrap: "wrap", fontSize: 13 }}>
          <span>Total: <strong>{formatMinutes(sum(() => true))}</strong></span>
          <span>Billable: <strong>{formatMinutes(sum(e => e.billable))}</strong></span>
          <span>Rework: <strong>{formatMinutes(sum(e => e.kind === "REWORK"))}</strong></span>
          <span>Unplanned: <strong>{formatMinutes(sum(e => e.kind === "UNPLANNED"))}</strong></span>
        </div>
      )}
      {isLoading && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Loading...</div>}
      {!isLoading && data.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>No time has been recorded for this client in the last 90 days.</div>}
      {data.map(e => (
        <div key={e.id} style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: 10, fontSize: 13, opacity: e.status === "VOID" ? 0.6 : 1 }}>
          <strong>{formatMinutes(e.minutes)}</strong> · {staffName(staff.find(s => s.id === e.userId))} · {e.workDate} · {kindLabel(e.kind)}{e.serviceCode ? ` · ${e.serviceCode}` : ""}{e.billable ? "" : " · not billable"}
          {e.status === "VOID" && <span> · Voided: {e.voidReason}</span>}
          {e.note && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{e.note}</div>}
        </div>
      ))}
    </div>
  )
}
