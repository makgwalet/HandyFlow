// src/pages/events/EventTicketsTab.tsx
//
// Ticket types of the selected event: price, places, how many sold and checked in, whether each is on sale, and adding a new one. Adding needs
// EVENTS_TICKETING_MANAGE (or EVENTS_MANAGE); the server refuses a new type on a completed or cancelled event and explains why.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { btnGhost, btnPrimary, ErrorLine, inputStyle, labelStyle, Modal } from "../warehousing/whseUi"
import { placesLeft, tierBody, tierProblem, tierState, type TierRow } from "./dashboardView"
import { rand } from "./paymentOps"

const field: React.CSSProperties = { ...inputStyle, width: "100%" }
const empty = { name: "", description: "", price: "", quantity: "", saleStart: "", saleEnd: "" }
const TONE = { ok: ["var(--hf-success-soft-strong)", "var(--hf-success-text-strong)"], warn: ["var(--hf-warning-soft)", "var(--hf-warning-text)"], bad: ["var(--hf-danger-soft)", "var(--hf-danger-text)"], muted: ["var(--hf-surface-sunken)", "var(--hf-text-muted)"] } as const

export default function EventTicketsTab({ eventId, eventTitle, onChangeEvent }: { eventId: string | null; eventTitle: string; onChangeEvent: () => void }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["EVENTS_TICKETING_MANAGE", "EVENTS_MANAGE"])
  const [adding, setAdding] = useState(false)
  const [f, setF] = useState(empty)
  const { data: tiers = [], isLoading, error } = useQuery<TierRow[]>({
    queryKey: ["event-tiers", eventId], enabled: !!eventId,
    queryFn: async () => { const r = await apiClient.get(`/api/v1/events/${eventId}/tiers`); return r.data?.data ?? r.data ?? [] },
  })
  const create = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/events/${eventId}/tiers`, tierBody(f)),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["event-tiers", eventId] }); qc.invalidateQueries({ queryKey: ["event-dash-registration", eventId] }); setAdding(false); setF(empty) },
  })
  if (!eventId) return <div style={{ padding: 30, textAlign: "center", color: "var(--hf-text-muted)" }}>Pick an event first. <button type="button" style={inputStyle} onClick={onChangeEvent}>Choose event</button></div>
  const problem = tierProblem(f)
  const set = (k: keyof typeof empty, v: string) => setF(p => ({ ...p, [k]: v }))
  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 10, flexWrap: "wrap", marginBottom: 12 }}>
        <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: 0 }}>Ticket types for {eventTitle} · {placesLeft(tiers)} places left on sale</p>
        {canManage && <button type="button" style={btnPrimary} onClick={() => { setAdding(true); create.reset() }}>Add ticket type</button>}
      </div>
      <ErrorLine error={error} fallback="The ticket types could not be loaded" />
      {isLoading ? <p>Loading…</p> : tiers.length === 0 ? <p style={{ color: "var(--hf-text-muted)" }}>No ticket types yet.</p> : (
        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
            <thead><tr style={{ textAlign: "left", color: "var(--hf-text-muted)", fontSize: 11.5 }}>{["Ticket type", "Price", "Places", "Sold", "Checked in", "Status", "Sale window"].map(h => <th key={h} style={{ padding: "6px 10px" }}>{h}</th>)}</tr></thead>
            <tbody>
              {tiers.map(t => {
                const st = tierState(t); const [bg, fg] = TONE[st.tone]
                return (
                  <tr key={t.id} style={{ borderTop: "1px solid var(--hf-border)" }}>
                    <td style={{ padding: "8px 10px" }}><strong>{t.name}</strong>{t.description && <div style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>{t.description}</div>}</td>
                    <td style={{ padding: "8px 10px" }}>{t.price > 0 ? rand(t.price) : "Free"}</td>
                    <td style={{ padding: "8px 10px" }}>{t.quantity}</td>
                    <td style={{ padding: "8px 10px" }}>{t.quantitySold}</td>
                    <td style={{ padding: "8px 10px" }}>{t.quantityCheckedIn}</td>
                    <td style={{ padding: "8px 10px" }}><span style={{ background: bg, color: fg, padding: "1px 8px", borderRadius: 20, fontSize: 11 }}>{st.label}</span></td>
                    <td style={{ padding: "8px 10px", fontSize: 12, color: "var(--hf-text-muted)" }}>{t.saleStart ? new Date(t.saleStart).toLocaleDateString("en-ZA") : "Now"} – {t.saleEnd ? new Date(t.saleEnd).toLocaleDateString("en-ZA") : "Event day"}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
      {adding && (
        <Modal title="Add ticket type" onClose={() => setAdding(false)} width={480}>
          <label style={labelStyle} htmlFor="tt-name">Name *</label><input id="tt-name" style={field} maxLength={100} value={f.name} onChange={e => set("name", e.target.value)} />
          <label style={{ ...labelStyle, marginTop: 10 }} htmlFor="tt-desc">Description</label><input id="tt-desc" style={field} maxLength={2000} value={f.description} onChange={e => set("description", e.target.value)} />
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, marginTop: 10 }}>
            <div><label style={labelStyle} htmlFor="tt-price">Price (R, empty = free)</label><input id="tt-price" style={field} inputMode="decimal" value={f.price} onChange={e => set("price", e.target.value)} /></div>
            <div><label style={labelStyle} htmlFor="tt-qty">Places *</label><input id="tt-qty" style={field} inputMode="numeric" value={f.quantity} onChange={e => set("quantity", e.target.value)} /></div>
            <div><label style={labelStyle} htmlFor="tt-from">Sale starts</label><input id="tt-from" type="datetime-local" style={field} value={f.saleStart} onChange={e => set("saleStart", e.target.value)} /></div>
            <div><label style={labelStyle} htmlFor="tt-to">Sale ends</label><input id="tt-to" type="datetime-local" style={field} value={f.saleEnd} onChange={e => set("saleEnd", e.target.value)} /></div>
          </div>
          <ErrorLine error={create.error} fallback="The ticket type could not be added" />
          {problem && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "10px 0 0" }}>{problem}</p>}
          <div style={{ display: "flex", gap: 8, marginTop: 14 }}>
            <button type="button" style={{ ...btnGhost, flex: 1, justifyContent: "center" }} onClick={() => setAdding(false)}>Back</button>
            <button type="button" style={{ ...btnPrimary, flex: 1, justifyContent: "center", opacity: problem ? 0.5 : 1 }} disabled={!!problem || create.isPending} onClick={() => create.mutate()}>{create.isPending ? "Working…" : "Add ticket type"}</button>
          </div>
        </Modal>
      )}
    </div>
  )
}
