// src/pages/events/WaitlistTab.tsx
//
// The waiting list of the selected event, oldest first. Anyone with registration rights can add someone (only while the event or tier is genuinely full),
// promote an entry into a free place now, or remove it.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { btnGhost, btnPrimary, card, ErrorLine, inputStyle } from "../warehousing/whseUi"
import { WaitlistJoinModal } from "./GuestOpsDialogs"
import { waitlistStatusText } from "./guestOps"

interface Entry { id: string; fullName: string; email: string; phone: string | null; company: string | null; tierName: string | null; status: string; position: number | null; createdAt: string }

export default function WaitlistTab({ eventId, eventTitle, onChangeEvent }: { eventId: string | null; eventTitle: string; onChangeEvent: () => void }) {
  const qc = useQueryClient()
  const canRegister = usePermissions(["EVENTS_REGISTRATION_MANAGE", "EVENTS_MANAGE"])
  const [status, setStatus] = useState("WAITING")
  const [adding, setAdding] = useState(false)
  const { data, isLoading, error } = useQuery<{ content: Entry[] }>({
    queryKey: ["event-waitlist", eventId, status],
    enabled: !!eventId,
    queryFn: async () => { const r = await apiClient.get(`/api/v1/events/${eventId}/waitlist`, { params: { status, size: 100 } }); return r.data?.data ?? r.data },
  })
  const { data: tiers = [] } = useQuery<{ id: string; name: string }[]>({
    queryKey: ["event-tiers", eventId],
    enabled: !!eventId && adding,
    queryFn: async () => { const r = await apiClient.get(`/api/v1/events/${eventId}/tiers`); return r.data?.data ?? r.data ?? [] },
  })
  const after = () => { qc.invalidateQueries({ queryKey: ["event-waitlist"] }); qc.invalidateQueries({ queryKey: ["event-guests", eventId] }); qc.invalidateQueries({ queryKey: ["event-stats", eventId] }) }
  const promote = useMutation({ mutationFn: (id: string) => apiClient.post(`/api/v1/events/${eventId}/waitlist/${id}/promote`), onSuccess: after })
  const remove = useMutation({ mutationFn: (id: string) => apiClient.delete(`/api/v1/events/${eventId}/waitlist/${id}`), onSuccess: after })

  if (!eventId) return <div style={{ padding: 30, textAlign: "center", color: "var(--hf-text-muted)" }}>Pick an event first. <button type="button" style={inputStyle} onClick={onChangeEvent}>Choose event</button></div>
  const rows = data?.content ?? []
  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 10, flexWrap: "wrap", marginBottom: 12 }}>
        <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: 0 }}>Waiting list for {eventTitle}</p>
        <div style={{ display: "flex", gap: 8 }}>
          <select aria-label="Show" style={inputStyle} value={status} onChange={e => setStatus(e.target.value)}>
            <option value="WAITING">Waiting</option><option value="PROMOTED">Promoted</option><option value="REMOVED">Removed</option>
          </select>
          {canRegister && <button type="button" style={btnPrimary} onClick={() => setAdding(true)}>Add to waitlist</button>}
        </div>
      </div>
      <ErrorLine error={promote.error ?? remove.error ?? error} fallback="That could not be completed" />
      {isLoading ? <p>Loading…</p> : rows.length === 0 ? <p style={{ color: "var(--hf-text-muted)" }}>Nobody is on this list.</p> : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {rows.map(r => (
            <div key={r.id} style={{ ...card, padding: "10px 14px", display: "flex", justifyContent: "space-between", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
              <div>
                <div style={{ fontWeight: 700, fontSize: 13 }}>{r.position ? `#${r.position} · ` : ""}{r.fullName}</div>
                <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{r.email}{r.phone ? ` · ${r.phone}` : ""}{r.company ? ` · ${r.company}` : ""} · {r.tierName ?? "Any place"} · {waitlistStatusText(r.status)}</div>
              </div>
              {canRegister && r.status === "WAITING" && (
                <div style={{ display: "flex", gap: 6 }}>
                  <button type="button" style={btnPrimary} disabled={promote.isPending} onClick={() => promote.mutate(r.id)}>Promote now</button>
                  <button type="button" style={btnGhost} disabled={remove.isPending} onClick={() => remove.mutate(r.id)}>Remove</button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
      {adding && <WaitlistJoinModal eventId={eventId} tiers={tiers} onClose={() => setAdding(false)} />}
    </div>
  )
}
