// src/pages/invoicing/RefundApprovals.tsx
//
// Refunds that are waiting for a second person. A refund pays money out of the business, so the person who asked for
// it cannot approve it when someone else could (the server decides and says so). Shown only to people who hold the
// refund permission, and only when something is waiting.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { fmtDate, fmtR } from "./billing.logic"
import { apiMessage } from "./queries"
import { Btn, Dialog, Field, Notice, fieldStyle, panel, sectionTitle } from "./ui"

export interface PendingRefund {
  id: string; refundNumber: string; receiptId: string; receiptNumber: string; amount: number | string
  reason: string; requestedBy: string | null; requestedByName: string | null; createdAt: string
}

export const requestedByText = (r: Pick<PendingRefund, "requestedByName">) => r.requestedByName?.trim() ? r.requestedByName.trim() : "A colleague"

export default function RefundApprovals() {
  const allowed = usePermission("INVOICE_REFUND")
  const qc = useQueryClient()
  const [declining, setDeclining] = useState<PendingRefund | null>(null)
  const [reason, setReason] = useState("")
  const [err, setErr] = useState("")
  const [done, setDone] = useState("")

  const { data } = useQuery<PendingRefund[]>({
    queryKey: ["pending-refunds"], enabled: allowed,
    queryFn: async () => (await apiClient.get("/api/v1/invoicing/refunds/pending")).data?.data ?? [],
  })
  const refresh = () => {
    qc.invalidateQueries({ queryKey: ["pending-refunds"] }); qc.invalidateQueries({ queryKey: ["customer-account"] }); qc.invalidateQueries({ queryKey: ["invoice-credit"] })
  }
  const approve = useMutation({
    mutationFn: (r: PendingRefund) => apiClient.post(`/api/v1/invoicing/refunds/${r.id}/approve`, {}),
    onSuccess: (_d, r) => { setErr(""); setDone(`Refund ${r.refundNumber} approved and paid.`); refresh() },
    onError: (e: unknown) => { setDone(""); setErr(apiMessage(e, "The refund could not be approved.")) },
  })
  const decline = useMutation({
    mutationFn: (r: PendingRefund) => apiClient.post(`/api/v1/invoicing/refunds/${r.id}/reject`, { reason: reason.trim() }),
    onSuccess: (_d, r) => { setErr(""); setDone(`Refund ${r.refundNumber} declined. The credit is available again.`); setDeclining(null); setReason(""); refresh() },
    onError: (e: unknown) => setErr(apiMessage(e, "The refund could not be declined.")),
  })

  const rows = data ?? []
  if (!allowed || (rows.length === 0 && !done)) return null
  return (
    <section aria-label="Refunds waiting for approval" style={{ ...panel, padding: 16, marginBottom: 16 }}>
      <div style={{ ...sectionTitle, marginBottom: 10 }}>Refunds waiting for approval</div>
      {done && <Notice tone="ok">{done}</Notice>}
      {err && !declining && <Notice tone="bad">{err}</Notice>}
      <div style={{ display: "grid", gap: 8, marginTop: done || err ? 10 : 0 }}>
        {rows.map(r => (
          <div key={r.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, flexWrap: "wrap", padding: "10px 12px", border: "1px solid var(--hf-border-subtle)", borderRadius: 8 }}>
            <div style={{ fontSize: 13, color: "var(--hf-text-secondary)", minWidth: 220 }}>
              <strong style={{ color: "var(--hf-text-primary)" }}>{fmtR(Number(r.amount))}</strong> from credit {r.receiptNumber}
              <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{requestedByText(r)} asked on {fmtDate(r.createdAt)}: {r.reason}</div>
            </div>
            <div style={{ display: "flex", gap: 8 }}>
              <Btn small variant="success" disabled={approve.isPending} onClick={() => approve.mutate(r)} aria-label={`Approve refund ${r.refundNumber}`}>Approve</Btn>
              <Btn small variant="danger" onClick={() => { setErr(""); setReason(""); setDeclining(r) }} aria-label={`Decline refund ${r.refundNumber}`}>Decline</Btn>
            </div>
          </div>
        ))}
      </div>
      {declining && (
        <Dialog title="Decline refund" subtitle={`${declining.refundNumber} · ${fmtR(Number(declining.amount))}`} onClose={() => setDeclining(null)}
          footer={<><Btn onClick={() => setDeclining(null)}>Cancel</Btn>
            <Btn variant="danger" disabled={decline.isPending || reason.trim().length < 3} onClick={() => decline.mutate(declining)}>{decline.isPending ? "Saving..." : "Decline refund"}</Btn></>}>
          <Field label="Reason">{id => <textarea id={id} rows={3} value={reason} onChange={e => setReason(e.target.value)} style={fieldStyle} />}</Field>
          {err && <Notice tone="bad">{err}</Notice>}
        </Dialog>
      )}
    </section>
  )
}
