// G02: invoice attempts that need a person. Shown above the queue only when there is something to do.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { btnPrimary, btnGhost, card, inputStyle } from "../warehousing/whseUi"
import { attemptAdvice, attemptHeadline, validateReleaseNote, type InvoiceAttempt } from "./invoiceAttemptOps"

const unwrap = (r: any) => r.data?.data ?? r.data

export default function InvoiceAttentionPanel() {
  const canManage = usePermissions(["FUEL_MANAGE"])
  const qc = useQueryClient()
  const [notes, setNotes] = useState<Record<string, string>>({})
  const [err, setErr] = useState("")
  const list = useQuery<InvoiceAttempt[]>({ queryKey: ["fuel-invoice-attempts"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/invoicing/attempts?attentionOnly=true")) })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["fuel-invoice-attempts"] }); qc.invalidateQueries({ queryKey: ["fuel-invoice-queue"] }) }
  const relink = useMutation({ mutationFn: (id: string) => apiClient.post(`/api/v1/fuel/invoicing/attempts/${id}/relink`, {}), onSuccess: () => { setErr(""); refresh() }, onError: (e: any) => setErr(e?.response?.data?.message ?? "Could not link the deliveries") })
  const release = useMutation({ mutationFn: (v: { id: string; note: string }) => apiClient.post(`/api/v1/fuel/invoicing/attempts/${v.id}/release`, { note: v.note }), onSuccess: () => { setErr(""); refresh() }, onError: (e: any) => setErr(e?.response?.data?.message ?? "Could not release the deliveries") })

  const rows = list.data ?? []
  if (rows.length === 0) return null
  return (
    <section aria-label="Invoice attempts needing attention" style={{ ...card, border: "1px solid var(--hf-danger-text)", padding: 12, marginBottom: 14 }}>
      <strong style={{ color: "var(--hf-danger-text)" }}>Needs attention ({rows.length})</strong>
      {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{err}</p>}
      {rows.map(a => (
        <div key={a.id} style={{ borderTop: "1px solid var(--hf-border)", marginTop: 8, paddingTop: 8, fontSize: 13 }}>
          <div>{a.customerName ?? "Customer"}: {attemptHeadline(a)}</div>
          <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{a.receiptNumbers.join(", ")}{a.error ? ` · ${a.error}` : ""}</div>
          <div style={{ fontSize: 12, margin: "4px 0" }}>{attemptAdvice(a)}</div>
          {canManage && a.status === "LINK_FAILED" && <button style={btnPrimary} disabled={relink.isPending} onClick={() => relink.mutate(a.id)}>Link to {a.invoiceNumber ?? "invoice"}</button>}
          {canManage && a.status === "STARTED" && (
            <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
              <input aria-label={`What you checked for ${a.id}`} style={{ ...inputStyle, maxWidth: 320 }} placeholder="What did you check in Invoicing?" value={notes[a.id] ?? ""} onChange={e => setNotes(n => ({ ...n, [a.id]: e.target.value }))} />
              <button style={btnGhost} disabled={release.isPending} onClick={() => { const v = validateReleaseNote(notes[a.id] ?? ""); if (v) return setErr(v); release.mutate({ id: a.id, note: notes[a.id] }) }}>Release to queue</button>
            </div>
          )}
        </div>
      ))}
    </section>
  )
}
