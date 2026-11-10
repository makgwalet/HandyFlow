// src/pages/warehousing/WhseApprovalsTab.tsx
//
// Second-person controls for stock adjustments:
//  - pending adjustment requests, approved or rejected by someone holding WAREHOUSING_ADJUST_APPROVE
//    (the server refuses the person who asked while anyone else could approve, and records an
//    audited sole-approver exception where nobody else can);
//  - the approval threshold (units above which an adjustment needs a second person), editable by
//    WAREHOUSING_ADMIN. Empty means the rule is off. Every change needs a reason and is audited.
// Rate and minimum-charge changes have the same control (see WhseTariffApprovals).
// Invoice approval lives on each client's Billing tab.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Check, X } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { WHSE_ACCENT, WHSE_ACCENT_TEXT } from "./constants"
import { TariffRuleCard, TariffRequestsCard } from "./WhseTariffApprovals"

interface PendingAdjustment {
  id: string; inventoryId: string; delta: number; reason: string; requestedByName: string | null; requestedAt: string
  clientName: string; sku: string; locationCode: string; qtyOnHand: number
}

const cardStyle: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 14, padding: 20, marginBottom: 20 }
const inputStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, boxSizing: "border-box", fontFamily: "inherit" }
const labelStyle: React.CSSProperties = { fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 5, display: "block" }
const errMsg = (e: unknown, fallback: string) => (e as any)?.response?.data?.message ?? fallback

function ThresholdCard() {
  const qc = useQueryClient()
  const canEdit = usePermission("WAREHOUSING_ADMIN")
  const { data } = useQuery<{ thresholdQty: number | null }>({
    queryKey: ["whse-adjust-threshold"],
    queryFn: async () => (await apiClient.get("/api/v1/warehousing/adjustment-approval-threshold")).data,
  })
  const [value, setValue] = useState<string | null>(null)
  const [reason, setReason] = useState("")
  const current = data?.thresholdQty
  const shown = value ?? (current == null ? "" : String(current))
  const save = useMutation({
    mutationFn: async () => apiClient.put("/api/v1/warehousing/adjustment-approval-threshold", { thresholdQty: shown === "" ? null : parseFloat(shown), reason: reason.trim() }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["whse-adjust-threshold"] }); setValue(null); setReason("") },
  })
  const invalid = shown !== "" && (Number.isNaN(parseFloat(shown)) || parseFloat(shown) < 0)
  const unchanged = shown === (current == null ? "" : String(current))

  return (
    <div style={cardStyle}>
      <p style={{ fontSize: 14, fontWeight: 800, color: "var(--hf-text)", margin: "0 0 4px" }}>Adjustment approval threshold</p>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 14px" }}>
        {current == null
          ? "Off: every manual stock adjustment applies at once."
          : `Adjustments of more than ${current} units, up or down, must be approved by a second person.`}
      </p>
      {canEdit ? (
        <div style={{ display: "grid", gap: 12, maxWidth: 420 }}>
          <div><label htmlFor="whse-threshold-qty" style={labelStyle}>Units (leave empty to switch the rule off)</label>
            <input id="whse-threshold-qty" type="number" min="0" step="1" style={inputStyle} value={shown} onChange={e => setValue(e.target.value)} /></div>
          <div><label htmlFor="whse-threshold-reason" style={labelStyle}>Reason for the change *</label>
            <input id="whse-threshold-reason" style={inputStyle} value={reason} onChange={e => setReason(e.target.value)} placeholder="At least 5 characters; kept in the audit trail" /></div>
          {save.isError && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: 0 }}>{errMsg(save.error, "Could not save the threshold")}</p>}
          <button onClick={() => save.mutate()} disabled={invalid || unchanged || reason.trim().length < 5 || save.isPending}
            style={{ padding: "10px", borderRadius: 8, border: "none", background: WHSE_ACCENT, color: "var(--hf-text-on-solid)", fontSize: 13, fontWeight: 700, cursor: "pointer", opacity: (invalid || unchanged || reason.trim().length < 5 || save.isPending) ? 0.6 : 1 }}>
            {save.isPending ? "Saving…" : "Save threshold"}
          </button>
        </div>
      ) : <p style={{ fontSize: 12, color: "var(--hf-text-faint)", margin: 0 }}>Only a warehouse administrator can change this.</p>}
    </div>
  )
}

function RejectBox({ onCancel, onConfirm, busy, error }: { onCancel: () => void; onConfirm: (note: string) => void; busy: boolean; error?: string }) {
  const [note, setNote] = useState("")
  return (
    <div style={{ marginTop: 10, display: "grid", gap: 8 }}>
      <input style={inputStyle} value={note} onChange={e => setNote(e.target.value)} placeholder="Reason for rejecting (at least 5 characters)" />
      {error && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: 0 }}>{error}</p>}
      <div style={{ display: "flex", gap: 8 }}>
        <button onClick={() => onConfirm(note.trim())} disabled={note.trim().length < 5 || busy}
          style={{ padding: "7px 12px", borderRadius: 7, border: "none", background: "var(--hf-danger-text)", color: "var(--hf-text-on-solid)", fontSize: 12, fontWeight: 700, cursor: "pointer", opacity: (note.trim().length < 5 || busy) ? 0.6 : 1 }}>Confirm rejection</button>
        <button onClick={onCancel} style={{ padding: "7px 12px", borderRadius: 7, border: "1px solid var(--hf-border)", background: "none", color: "var(--hf-text-muted)", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>Cancel</button>
      </div>
    </div>
  )
}

function PendingRow({ item }: { item: PendingAdjustment }) {
  const qc = useQueryClient()
  const canDecide = usePermission("WAREHOUSING_ADJUST_APPROVE")
  const [rejecting, setRejecting] = useState(false)
  const done = () => { qc.invalidateQueries({ queryKey: ["whse-adjustment-requests"] }); qc.invalidateQueries({ queryKey: ["whse-inventory"] }) }
  const approve = useMutation({ mutationFn: async () => apiClient.post(`/api/v1/warehousing/adjustment-requests/${item.id}/approve`, {}), onSuccess: done })
  const reject = useMutation({ mutationFn: async (note: string) => apiClient.post(`/api/v1/warehousing/adjustment-requests/${item.id}/reject`, { note }), onSuccess: done })
  const cancel = useMutation({ mutationFn: async () => apiClient.post(`/api/v1/warehousing/adjustment-requests/${item.id}/cancel`, {}), onSuccess: done })
  const sign = item.delta > 0 ? "+" : ""

  return (
    <div style={{ padding: "14px 16px", borderTop: "1px solid var(--hf-border-subtle)" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 12 }}>
        <div>
          <p style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", margin: 0 }}>
            {item.sku} @ {item.locationCode} <span style={{ fontWeight: 500, color: "var(--hf-text-muted)" }}>· {item.clientName}</span>
          </p>
          <p style={{ fontSize: 12.5, color: "var(--hf-text-secondary)", margin: "2px 0" }}>
            <strong style={{ color: item.delta < 0 ? "var(--hf-danger-text)" : WHSE_ACCENT_TEXT }}>{sign}{item.delta}</strong> (on hand now {item.qtyOnHand}) — {item.reason}
          </p>
          <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: 0 }}>
            Asked by {item.requestedByName || "unknown"} on {new Date(item.requestedAt).toLocaleString("en-ZA")}
          </p>
        </div>
        <div style={{ display: "flex", gap: 8, flexShrink: 0 }}>
          {canDecide && (
            <>
              <button onClick={() => approve.mutate()} disabled={approve.isPending}
                style={{ display: "flex", alignItems: "center", gap: 4, background: WHSE_ACCENT, color: "var(--hf-text-on-solid)", border: "none", borderRadius: 7, padding: "6px 10px", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
                <Check size={13} /> {approve.isPending ? "Applying…" : "Approve"}
              </button>
              <button onClick={() => setRejecting(true)} style={{ background: "none", border: "1px solid var(--hf-border)", color: "var(--hf-danger-text)", borderRadius: 7, padding: "6px 10px", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>Reject</button>
            </>
          )}
          <button onClick={() => cancel.mutate()} disabled={cancel.isPending} title="Only the person who asked can cancel"
            style={{ display: "flex", alignItems: "center", gap: 4, background: "none", border: "1px solid var(--hf-border)", color: "var(--hf-text-muted)", borderRadius: 7, padding: "6px 10px", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>
            <X size={13} /> Cancel
          </button>
        </div>
      </div>
      {(approve.isError || cancel.isError) && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: "8px 0 0" }}>{errMsg(approve.error ?? cancel.error, "Could not complete this step")}</p>}
      {rejecting && <RejectBox busy={reject.isPending} error={reject.isError ? errMsg(reject.error, "Could not reject") : undefined}
        onCancel={() => setRejecting(false)} onConfirm={note => reject.mutate(note)} />}
    </div>
  )
}

export default function WhseApprovalsTab() {
  const { data: pending = [], isLoading } = useQuery<PendingAdjustment[]>({
    queryKey: ["whse-adjustment-requests"],
    queryFn: async () => (await apiClient.get("/api/v1/warehousing/adjustment-requests/pending")).data,
  })

  return (
    <div>
      <ThresholdCard />
      <div style={{ ...cardStyle, padding: 0, overflow: "hidden" }}>
        <p style={{ fontSize: 14, fontWeight: 800, color: "var(--hf-text)", margin: 0, padding: "16px 16px 12px" }}>
          Stock adjustments waiting for approval {pending.length > 0 && <span style={{ color: "var(--hf-text-faint)", fontWeight: 600 }}>({pending.length})</span>}
        </p>
        {isLoading ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13, padding: "0 16px 16px", margin: 0 }}>Loading…</p>
          : pending.length === 0 ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13, padding: "0 16px 16px", margin: 0 }}>Nothing is waiting.</p>
          : pending.map(p => <PendingRow key={p.id} item={p} />)}
      </div>
      <TariffRuleCard />
      <TariffRequestsCard />
      <p style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>Client invoices waiting for approval are on each client's Billing tab.</p>
    </div>
  )
}
