// src/pages/warehousing/WhseTariffApprovals.tsx
//
// Second-person control for storage-tariff and minimum-charge changes (shown on the Approvals tab):
//  - the rule itself (off by default), switchable by WAREHOUSING_ADMIN with a recorded reason;
//  - changes waiting for a decision, approved or rejected by someone holding WAREHOUSING_TARIFF_APPROVE.
// The server refuses the person who asked while anyone else could approve, and refuses to apply a change whose
// start date has already passed, so a late approval never re-prices a day that was billed.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Check, X } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { WHSE_ACCENT } from "./constants"

interface PendingTariff {
  id: string; kind: "TARIFF" | "TERMS"; clientName: string | null; sku: string | null; monthlyRate: number | null
  minimumStorageCharge: number | null; freeDays?: number | null; locationFeePerMonth?: number | null; effectiveFrom: string; note: string | null; requestedByName: string | null; requestedAt: string
}

const cardStyle: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 14, padding: 20, marginBottom: 20 }
const inputStyle: React.CSSProperties = { width: "100%", padding: "9px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, boxSizing: "border-box", fontFamily: "inherit" }
const labelStyle: React.CSSProperties = { fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 5, display: "block" }
const money = (n: number) => new Intl.NumberFormat("en-ZA", { style: "currency", currency: "ZAR" }).format(n)
const errMsg = (e: unknown, fallback: string) => (e as any)?.response?.data?.message ?? fallback

export function TariffRuleCard() {
  const qc = useQueryClient()
  const canEdit = usePermission("WAREHOUSING_ADMIN")
  const { data } = useQuery<{ required: boolean }>({
    queryKey: ["whse-tariff-rule"],
    queryFn: async () => (await apiClient.get("/api/v1/warehousing/tariff-approval-rule")).data,
  })
  const [reason, setReason] = useState("")
  const on = !!data?.required
  const save = useMutation({
    mutationFn: async () => apiClient.put("/api/v1/warehousing/tariff-approval-rule", { required: !on, reason: reason.trim() }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["whse-tariff-rule"] }); setReason("") },
  })
  const blocked = reason.trim().length < 5 || save.isPending

  return (
    <div style={cardStyle}>
      <p style={{ fontSize: 14, fontWeight: 800, color: "var(--hf-text)", margin: "0 0 4px" }}>Tariff and minimum-charge approval</p>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 14px" }}>
        {on ? "On: a new rate or minimum is only asked for, and a second person applies it."
            : "Off: a warehouse administrator's new rate or minimum applies at once."}
      </p>
      {canEdit ? (
        <div style={{ display: "grid", gap: 12, maxWidth: 420 }}>
          <div><label htmlFor="whse-tariff-rule-reason" style={labelStyle}>Reason for the change *</label>
            <input id="whse-tariff-rule-reason" style={inputStyle} value={reason} onChange={e => setReason(e.target.value)} placeholder="e.g. client contracts now need sign-off (5+ characters, audited)" /></div>
          {save.isError && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: 0 }}>{errMsg(save.error, "Could not save")}</p>}
          <button onClick={() => save.mutate()} disabled={blocked}
            style={{ padding: 10, borderRadius: 8, border: "none", background: WHSE_ACCENT, color: "var(--hf-text-on-solid)", fontSize: 13, fontWeight: 700, cursor: "pointer", opacity: blocked ? 0.6 : 1 }}>
            {save.isPending ? "Saving…" : on ? "Stop requiring approval" : "Require approval"}
          </button>
        </div>
      ) : <p style={{ fontSize: 12, color: "var(--hf-text-faint)", margin: 0 }}>Only a warehouse administrator can switch this rule.</p>}
    </div>
  )
}

function Row({ item }: { item: PendingTariff }) {
  const qc = useQueryClient()
  const canDecide = usePermission("WAREHOUSING_TARIFF_APPROVE")
  const [rejecting, setRejecting] = useState(false)
  const [note, setNote] = useState("")
  const done = () => {
    for (const k of ["whse-tariff-requests", "whse-tariffs", "whse-terms"]) qc.invalidateQueries({ queryKey: [k] })
  }
  const base = `/api/v1/warehousing/tariff-requests/${item.id}`
  const approve = useMutation({ mutationFn: async () => apiClient.post(`${base}/approve`, {}), onSuccess: done })
  const reject = useMutation({ mutationFn: async () => apiClient.post(`${base}/reject`, { note: note.trim() }), onSuccess: done })
  const cancel = useMutation({ mutationFn: async () => apiClient.post(`${base}/cancel`, {}), onSuccess: done })
  const what = item.kind === "TARIFF"
    ? `${money(item.monthlyRate ?? 0)} per unit per month · ${item.sku ? `item ${item.sku}` : item.clientName ? "all of the client's stock" : "warehouse default"}`
    : [
        item.minimumStorageCharge != null ? `Minimum ${money(item.minimumStorageCharge)} per 30 days` : null,
        item.freeDays != null ? `${item.freeDays} free days` : null,
        item.locationFeePerMonth != null ? `${money(item.locationFeePerMonth)} per pallet or bin per month` : null,
      ].filter(Boolean).join(" · ")

  return (
    <div style={{ padding: "14px 16px", borderTop: "1px solid var(--hf-border-subtle)" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 12 }}>
        <div>
          <p style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", margin: 0 }}>
            {what} <span style={{ fontWeight: 500, color: "var(--hf-text-muted)" }}>· {item.clientName ?? "All clients"}</span>
          </p>
          <p style={{ fontSize: 12.5, color: "var(--hf-text-secondary)", margin: "2px 0" }}>Starts {item.effectiveFrom}{item.note ? ` — ${item.note}` : ""}</p>
          <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: 0 }}>Asked by {item.requestedByName || "unknown"} on {new Date(item.requestedAt).toLocaleString("en-ZA")}</p>
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
      {rejecting && (
        <div style={{ marginTop: 10, display: "grid", gap: 8 }}>
          <input style={inputStyle} value={note} onChange={e => setNote(e.target.value)} placeholder="Reason for rejecting (at least 5 characters)" />
          {reject.isError && <p style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: 0 }}>{errMsg(reject.error, "Could not reject")}</p>}
          <div style={{ display: "flex", gap: 8 }}>
            <button onClick={() => reject.mutate()} disabled={note.trim().length < 5 || reject.isPending}
              style={{ padding: "7px 12px", borderRadius: 7, border: "none", background: "var(--hf-danger-text)", color: "var(--hf-text-on-solid)", fontSize: 12, fontWeight: 700, cursor: "pointer", opacity: (note.trim().length < 5 || reject.isPending) ? 0.6 : 1 }}>Confirm rejection</button>
            <button onClick={() => setRejecting(false)} style={{ padding: "7px 12px", borderRadius: 7, border: "1px solid var(--hf-border)", background: "none", color: "var(--hf-text-muted)", fontSize: 12, fontWeight: 600, cursor: "pointer" }}>Cancel</button>
          </div>
        </div>
      )}
    </div>
  )
}

export function TariffRequestsCard() {
  const { data, isLoading } = useQuery<PendingTariff[]>({
    queryKey: ["whse-tariff-requests"],
    queryFn: async () => (await apiClient.get("/api/v1/warehousing/tariff-requests/pending")).data,
  })
  const pending = Array.isArray(data) ? data : []
  return (
    <div style={{ ...cardStyle, padding: 0, overflow: "hidden" }}>
      <p style={{ fontSize: 14, fontWeight: 800, color: "var(--hf-text)", margin: 0, padding: "16px 16px 12px" }}>
        Rates and minimums waiting for approval {pending.length > 0 && <span style={{ color: "var(--hf-text-faint)", fontWeight: 600 }}>({pending.length})</span>}
      </p>
      {isLoading ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13, padding: "0 16px 16px", margin: 0 }}>Loading…</p>
        : pending.length === 0 ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13, padding: "0 16px 16px", margin: 0 }}>No rate or minimum changes are waiting.</p>
        : pending.map(p => <Row key={p.id} item={p} />)}
    </div>
  )
}
