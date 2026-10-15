// src/pages/earthmoving/StatusHistoryDialog.tsx
// Every status change on one machine: who, when, why, the hour reading, and for a release the work order behind it and
// any override reason.
import { Overlay, ModalHead } from "./shared/Modal"
import { STATUS_CFG } from "./shared/constants"
import { fmtDateTime } from "./shared/format"
import { useStatusHistory, useAssignableUsers, useWorkOrders } from "./shared/hooks"
import type { Asset } from "./shared/types"

export default function StatusHistoryDialog({ asset, onClose }: { asset: Asset; onClose: () => void }) {
  const { data: events = [], isLoading } = useStatusHistory(asset.id)
  const { data: users = [] } = useAssignableUsers()
  const { data: orders = [] } = useWorkOrders()
  const who = (id: string | null) => id ? (users.find(u => u.id === id)?.name ?? "Another user") : "System"
  const label = (s: string | null) => s ? (STATUS_CFG[s]?.label ?? s) : "New"

  return (
    <Overlay onClose={onClose} width={640}>
      <ModalHead title={`Status history — ${asset.fleetNumber ?? asset.name}`} subtitle="Newest first" onClose={onClose} />
      {isLoading ? (
        <div style={{ padding: 20, textAlign: "center", color: "var(--hf-text-faint)" }}>Loading...</div>
      ) : events.length === 0 ? (
        <div style={{ padding: 20, textAlign: "center", color: "var(--hf-text-faint)" }}>No status changes recorded yet.</div>
      ) : (
        <ol style={{ listStyle: "none", margin: 0, padding: 0, display: "flex", flexDirection: "column", gap: 10 }}>
          {events.map(e => {
            const wo = e.workOrderId ? orders.find(o => o.id === e.workOrderId) : null
            return (
              <li key={e.id} style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: "10px 14px" }}>
                <div style={{ display: "flex", justifyContent: "space-between", fontSize: 13, fontWeight: 600 }}>
                  <span>{label(e.fromStatus)} → {label(e.toStatus)}</span>
                  <span style={{ fontWeight: 400, color: "var(--hf-text-faint)" }}>{fmtDateTime(e.happenedAt)}</span>
                </div>
                <div style={{ fontSize: 12, color: "var(--hf-text-secondary)", marginTop: 3 }}>
                  {who(e.actorUserId)}{e.meterHours != null && ` · ${e.meterHours} hrs`}{wo && ` · work order ${wo.number}`}
                </div>
                {e.reason && <div style={{ fontSize: 13, marginTop: 4 }}>{e.reason}</div>}
                {e.overrideReason && (
                  <div style={{ fontSize: 12, marginTop: 4, color: "var(--hf-warning-text)" }}>
                    Released without the usual evidence. Reason given: {e.overrideReason}
                  </div>
                )}
              </li>
            )
          })}
        </ol>
      )}
    </Overlay>
  )
}
