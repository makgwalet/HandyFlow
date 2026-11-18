// src/pages/supply-chain/ReplenishmentTab.tsx — what to reorder, with cover, lead time and a draft requisition from the ticked items.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { PackageSearch } from "lucide-react"
import { TH, TD, ErrBox, Spinner, EmptyState, Banner, fmtR } from "./scm.shared"
import { asList, errorText, replenishmentPicks, replenishmentProblem, URGENCY_LABEL, urgencyTone } from "./scmOps"
import { StatCard, Pill, grid } from "./scmUi"

interface Item { catalogueItemId: string; itemName: string; available: number; onOrder: number; requested: number; reorderLevel: number; dailyDemand: number; daysCover: number | null; leadTimeDays: number; suggestedQty: number; urgency: string; reason: string; suggestedUnitCost: number | null; priceSource: string | null; contractNumber: string | null }
const BASE = "/api/v1/supply-chain/replenishment"

export function ReplenishmentTab() {
  const qc = useQueryClient()
  const canRaise = usePermissions(["SCM_REQUISITION_CREATE", "SCM_ADMIN"])
  const [sel, setSel] = useState<Record<string, boolean>>({})
  const [edit, setEdit] = useState<Record<string, string>>({})
  const [err, setErr] = useState(""); const [done, setDone] = useState("")

  const q = useQuery({ queryKey: ["scm-replenishment"], queryFn: () => apiClient.get(BASE).then(asList<Item>) })
  const raise = useMutation({
    mutationFn: (items: { catalogueItemId: string; qty: number }[]) => apiClient.post(`${BASE}/requisition`, { title: "Stock replenishment", businessReason: "Recommended by replenishment planning", items }),
    onSuccess: () => { setDone("Draft requisition raised. Submit it from Requisitions."); setSel({}); setErr(""); qc.invalidateQueries({ queryKey: ["scm-replenishment"] }) },
    onError: e => { setDone(""); setErr(errorText(e)) },
  })

  const items = q.data ?? []
  const picks = replenishmentPicks(items, sel, edit)
  const count = (u: string) => items.filter(i => i.urgency === u).length
  const cost = picks.reduce((s, p) => s + p.qty * (items.find(i => i.catalogueItemId === p.catalogueItemId)?.suggestedUnitCost ?? 0), 0)
  const submit = () => { const p = replenishmentProblem(picks); if (p) { setErr(p); return } setErr(""); raise.mutate(picks) }

  return (
    <div style={{ padding: 20, display: "grid", gap: 14 }}>
      <div style={grid(180)}>
        <StatCard label="Out of stock" value={count("STOCKOUT")} tone={count("STOCKOUT") ? "bad" : "neutral"} icon={PackageSearch} />
        <StatCard label="Critical" value={count("CRITICAL")} tone={count("CRITICAL") ? "bad" : "neutral"} />
        <StatCard label="To reorder" value={count("REORDER")} tone={count("REORDER") ? "warn" : "neutral"} />
        <StatCard label="Selected" value={picks.length} sub={`Est. ${fmtR(cost)}`} />
      </div>
      {done && <Banner variant="info">{done}</Banner>}
      {err && <ErrBox msg={err} />}

      {q.isLoading ? <Spinner /> : q.isError ? <ErrBox msg={errorText(q.error, "Could not load recommendations")} /> : items.length === 0 ? (
        <EmptyState icon={PackageSearch} title="Nothing to reorder" sub="Stock, open orders and recent use are all in balance." />
      ) : (
        <div style={{ overflowX: "auto", background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12 }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr>{canRaise && <th style={TH} />}<th style={TH}>Item</th><th style={TH}>Urgency</th><th style={TH}>Available</th><th style={TH}>On order</th><th style={TH}>Cover</th><th style={TH}>Lead time</th><th style={TH}>Order qty</th><th style={TH}>Price</th></tr></thead>
            <tbody>{items.map(i => (
              <tr key={i.catalogueItemId} style={{ borderTop: "1px solid var(--hf-border)" }}>
                {canRaise && <td style={TD}><input type="checkbox" aria-label={`Select ${i.itemName}`} checked={!!sel[i.catalogueItemId]} onChange={e => setSel({ ...sel, [i.catalogueItemId]: e.target.checked })} /></td>}
                <td style={TD}><strong>{i.itemName}</strong><div style={{ fontSize: 12 }}>{i.reason}</div></td>
                <td style={TD}><Pill tone={urgencyTone(i.urgency)}>{URGENCY_LABEL[i.urgency] ?? i.urgency}</Pill></td>
                <td style={TD}>{i.available}</td><td style={TD}>{i.onOrder}</td>
                <td style={TD}>{i.daysCover == null ? "—" : `${Math.round(i.daysCover)} d`}</td>
                <td style={TD}>{i.leadTimeDays} d</td>
                <td style={TD}>{canRaise ? <input aria-label={`Quantity for ${i.itemName}`} inputMode="decimal" style={{ width: 80, padding: 6, border: "1.5px solid var(--hf-border)", borderRadius: 6, background: "var(--hf-surface)", color: "var(--hf-text)" }}
                  value={edit[i.catalogueItemId] ?? String(i.suggestedQty)} onChange={e => setEdit({ ...edit, [i.catalogueItemId]: e.target.value })} /> : i.suggestedQty}</td>
                <td style={TD}>{i.suggestedUnitCost != null ? fmtR(i.suggestedUnitCost) : "—"}{i.contractNumber && <div style={{ fontSize: 11 }}>Contract {i.contractNumber}</div>}</td>
              </tr>))}</tbody>
          </table>
        </div>
      )}
      {canRaise && items.length > 0 && <div><button onClick={submit} disabled={raise.isPending} style={{ padding: "9px 16px", background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, fontWeight: 600, cursor: "pointer" }}>Raise draft requisition</button></div>}
    </div>
  )
}
