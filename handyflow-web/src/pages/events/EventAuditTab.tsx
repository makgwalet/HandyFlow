// src/pages/events/EventAuditTab.tsx
//
// Who changed what on the selected event and when, newest first (GET /events/{id}/audit). Readable with EVENTS_AUDIT_READ (or EVENTS_MANAGE); without it
// the page says so instead of showing an error.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { card, ErrorLine, inputStyle } from "../warehousing/whseUi"
import { actionLabel } from "./eventActions"

interface Entry { id: string; action: string; summary: string; details: string | null; actorName: string | null; occurredAt: string }
interface PageOf { content: Entry[]; totalPages: number; number: number }

export default function EventAuditTab({ eventId, eventTitle, onChangeEvent }: { eventId: string | null; eventTitle: string; onChangeEvent: () => void }) {
  const allowed = usePermissions(["EVENTS_AUDIT_READ", "EVENTS_MANAGE"])
  const [page, setPage] = useState(0)
  const { data, isLoading, error } = useQuery<PageOf>({
    queryKey: ["event-audit", eventId, page],
    enabled: !!eventId && allowed,
    queryFn: async () => { const r = await apiClient.get(`/api/v1/events/${eventId}/audit`, { params: { page, size: 30 } }); return r.data?.data ?? r.data },
  })
  if (!eventId) return <div style={{ padding: 30, textAlign: "center", color: "var(--hf-text-muted)" }}>Pick an event first. <button type="button" style={inputStyle} onClick={onChangeEvent}>Choose event</button></div>
  if (!allowed) return <p style={{ padding: 20, color: "var(--hf-text-muted)" }}>You do not have access to the audit trail of events.</p>
  const rows = data?.content ?? []
  return (
    <div>
      <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: "0 0 12px" }}>Change history for {eventTitle}</p>
      {isLoading ? <p>Loading…</p> : rows.length === 0 ? <p style={{ color: "var(--hf-text-muted)" }}>Nothing recorded yet.</p> : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {rows.map(r => (
            <div key={r.id} style={{ ...card, padding: "10px 14px" }}>
              <div style={{ display: "flex", justifyContent: "space-between", gap: 10, flexWrap: "wrap", fontSize: 12 }}>
                <strong>{actionLabel(r.action)}</strong>
                <span style={{ color: "var(--hf-text-muted)" }}>{r.actorName ?? "System"} · {new Date(r.occurredAt).toLocaleString("en-ZA")}</span>
              </div>
              <div style={{ fontSize: 13, marginTop: 3 }}>{r.summary}</div>
              {r.details && <pre style={{ margin: "6px 0 0", fontSize: 12, color: "var(--hf-text-muted)", whiteSpace: "pre-wrap", fontFamily: "inherit" }}>{r.details}</pre>}
            </div>
          ))}
        </div>
      )}
      <ErrorLine error={error} fallback="The audit trail could not be loaded" />
      {data && data.totalPages > 1 && (
        <div style={{ display: "flex", gap: 8, justifyContent: "flex-end", marginTop: 12 }}>
          <button type="button" style={inputStyle} disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
          <button type="button" style={inputStyle} disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Next</button>
        </div>
      )}
    </div>
  )
}
