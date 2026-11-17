// Staff side of driver self-service: overnight stays customers asked for in the portal.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { btnGhost, btnPrimary, card, ErrorLine, inputStyle, ReasonModal } from "../warehousing/whseUi"
import { EmptyState, Panel, StatusPill } from "./fuelUi"
import { contactText, onlyRequests, REQUEST_FILTERS, stayText, type StayRequest } from "./stayRequestOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const B = "/api/v1/fuel/truck-stop/stay-requests"

export default function StayRequestsTab() {
  const canManage = usePermissions(["FUEL_MANAGE", "FUEL_TRUCKSTOP_MANAGE"])
  const qc = useQueryClient()
  const [status, setStatus] = useState("PENDING")
  const [declining, setDeclining] = useState<StayRequest | null>(null)
  const [note, setNote] = useState("")
  const list = useQuery<StayRequest[]>({ queryKey: ["fuel-stay-requests", status], queryFn: async () => onlyRequests(unwrap(await apiClient.get(`${B}?status=${status}`))) })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["fuel-stay-requests"] }); qc.invalidateQueries({ queryKey: ["fuel-stays"] }) }
  const approve = useMutation({ mutationFn: (id: string) => apiClient.post(`${B}/${id}/approve`, { note: note.trim() || null }), onSuccess: () => { setNote(""); refresh() } })
  const decline = useMutation({ mutationFn: (a: { id: string; reason: string }) => apiClient.post(`${B}/${a.id}/decline`, { reason: a.reason }), onSuccess: () => { setDeclining(null); refresh() } })
  const rows = list.data ?? []

  return (
    <div>
      <Panel title="Stay requests">
        <p style={{ margin: "0 0 10px", fontSize: 12.5, color: "var(--hf-text-muted)" }}>
          Customers ask for an overnight stay in their portal. Approving books the stay in the normal way and charges the customer's account. If a bay is not free or the account is on credit hold, the booking is refused and the request stays here for you to decline with a reason.
        </p>
        <select aria-label="Show" value={status} onChange={e => setStatus(e.target.value)} style={{ ...inputStyle, width: "auto", marginBottom: 10 }}>
          {REQUEST_FILTERS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
        </select>
        {canManage && status === "PENDING" && rows.length > 0 && (
          <input aria-label="Note to the customer" placeholder="Note shown with an approval (optional)" value={note} onChange={e => setNote(e.target.value)} maxLength={300} style={{ ...inputStyle, display: "block", maxWidth: 360, marginBottom: 10 }} />
        )}
        <ErrorLine error={approve.error} fallback="Could not book this stay" />
        {list.isLoading ? <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p> : rows.length === 0 ? (
          <EmptyState title="Nothing here" hint={status === "PENDING" ? "No customer is waiting for an answer." : undefined} />
        ) : rows.map(r => (
          <div key={r.id} style={{ ...card, padding: 12, marginBottom: 8, display: "flex", gap: 12, alignItems: "center", flexWrap: "wrap" }}>
            <div style={{ flex: 1, minWidth: 240 }}>
              <strong style={{ fontSize: 13 }}>{r.vehicleRegistration}</strong> <span style={{ fontSize: 12.5 }}>for {r.customerName}</span>
              <div style={{ fontSize: 12.5 }}>{stayText(r)}</div>
              <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{contactText(r)}{r.note ? ` · ${r.note}` : ""}</div>
              {r.decisionNote && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{r.decisionNote}</div>}
            </div>
            <StatusPill status={r.status} />
            {r.status === "PENDING" && canManage && (
              <>
                <button style={btnPrimary} disabled={approve.isPending} onClick={() => approve.mutate(r.id)} aria-label={`Approve ${r.vehicleRegistration}`}>Approve and book</button>
                <button style={btnGhost} onClick={() => setDeclining(r)} aria-label={`Decline ${r.vehicleRegistration}`}>Decline</button>
              </>
            )}
          </div>
        ))}
      </Panel>
      {declining && <ReasonModal title="Decline this request" intro={`${declining.vehicleRegistration} for ${declining.customerName}, ${stayText(declining)}. The customer will see your reason.`} confirmLabel="Decline"
        pending={decline.isPending} error={decline.error} onConfirm={reason => decline.mutate({ id: declining.id, reason })} onClose={() => setDeclining(null)} />}
    </div>
  )
}
