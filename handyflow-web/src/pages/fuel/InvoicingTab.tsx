// Stage 2C: delivered, not invoiced. Pick deliveries for one customer and create a draft invoice in Invoicing.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { btnPrimary, card, Chip } from "../warehousing/whseUi"
import { fmtL, fmtR, groupByCustomer, type QueueLine } from "./orderOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const th: React.CSSProperties = { textAlign: "left", padding: "8px 12px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)" }
const td: React.CSSProperties = { padding: "8px 12px", fontSize: 13, color: "var(--hf-text)" }

export default function InvoicingTab() {
  const canManage = usePermissions(["FUEL_MANAGE"])
  const qc = useQueryClient()
  const [picked, setPicked] = useState<Record<string, boolean>>({})
  const [err, setErr] = useState(""); const [done, setDone] = useState("")
  const queue = useQuery<QueueLine[]>({ queryKey: ["fuel-invoice-queue"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/invoicing/queue")) })
  const create = useMutation({
    mutationFn: (ids: string[]) => apiClient.post("/api/v1/fuel/invoicing/invoice", { deliveryIds: ids }),
    onSuccess: (r: any) => { const d = unwrap(r); setErr(""); setPicked({}); setDone(`Draft invoice ${d.invoiceNumber} created for ${d.deliveries} deliver${d.deliveries === 1 ? "y" : "ies"}. Issue it in Invoicing.`); qc.invalidateQueries({ queryKey: ["fuel-invoice-queue"] }) },
    onError: (e: any) => { setDone(""); setErr(e?.response?.data?.message ?? "Could not create the invoice"); qc.invalidateQueries({ queryKey: ["fuel-invoice-queue"] }) },
  })
  const groups = groupByCustomer(queue.data ?? [])
  const total = (queue.data ?? []).reduce((s, l) => s + Number(l.totalAmount ?? 0), 0)

  return (
    <div>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 12px" }}>
        Fuel that has been delivered but not yet invoiced{queue.data?.length ? `: ${queue.data.length} load${queue.data.length === 1 ? "" : "s"}, ${fmtR(total)}` : ""}. Choose loads for one customer to create a draft invoice. A failed attempt stays here with the reason.
      </p>
      {done && <p role="status" style={{ color: "var(--hf-success-text-strong)", fontSize: 13 }}>{done}</p>}
      {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>{err}</p>}
      {queue.isLoading ? <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p> : groups.length === 0 ? <p style={{ color: "var(--hf-text-muted)" }}>Nothing waiting: every delivered load has been invoiced.</p> : groups.map(g => {
        const ids = g.lines.filter(l => picked[l.deliveryId]).map(l => l.deliveryId)
        return (
          <div key={g.customerId ?? "none"} style={{ ...card, marginBottom: 14, overflowX: "auto" }}>
            <div style={{ display: "flex", alignItems: "center", padding: "10px 12px", gap: 10 }}>
              <strong style={{ flex: 1 }}>{g.name} · {fmtR(g.total)}</strong>
              {canManage && g.customerId && <button style={btnPrimary} disabled={ids.length === 0 || create.isPending} onClick={() => create.mutate(ids)}>Create draft invoice ({ids.length})</button>}
            </div>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr><th style={th}></th><th style={th}>Receipt</th><th style={th}>Fuel</th><th style={th}>Litres</th><th style={th}>Amount</th><th style={th}>State</th></tr></thead>
              <tbody>
                {g.lines.map(l => (
                  <tr key={l.deliveryId} style={{ borderTop: "1px solid var(--hf-border)" }}>
                    <td style={td}>{g.customerId && canManage && <input type="checkbox" aria-label={`Select ${l.receiptNumber}`} checked={!!picked[l.deliveryId]} onChange={e => setPicked(p => ({ ...p, [l.deliveryId]: e.target.checked }))} />}</td>
                    <td style={td}>{l.receiptNumber}</td><td style={td}>{l.fuelType}</td><td style={td}>{fmtL(l.litresDelivered)}</td><td style={td}>{fmtR(l.totalAmount)}</td>
                    <td style={td}>{l.invoiceState === "FAILED" ? <Chip bg="var(--hf-danger-soft)" fg="var(--hf-danger-text)">Failed: {l.invoiceError}</Chip> : "Not invoiced"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )
      })}
    </div>
  )
}
