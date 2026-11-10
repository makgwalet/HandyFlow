// src/pages/warehousing/WhseLotsTab.tsx
//
// Lots and expiry. "Expiring" lists stock that is expired or runs out of date within the chosen days, soonest first. "Lots" lists every lot with
// what is on hand and available, and lets a manager block a lot (a recall or quality hold: it is never allocated, cannot ship and takes no
// more receipts) or release it. Blocking shows the open orders that still hold stock of the lot, which need their lots re-allocated.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { Ban, ShieldCheck } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { btnDanger, btnGhost, card, Chip, fmtQty, inputStyle, ReasonModal } from "./whseUi"
import { daysLabel, EXPIRY_STYLE } from "./receiving"

interface LotRow {
  id: string; clientId: string; itemId: string; sku: string; lotNumber: string; expiryDate: string | null; manufacturedDate: string | null
  daysToExpiry: number | null; expiryStatus: string; onHand: number; available: number; blocked: boolean; blockedReason: string | null
}
interface ExpiryRow {
  inventoryId: string; clientName: string; sku: string; locationCode: string; stockStatus: string; lotNumber: string; expiryDate: string
  daysToExpiry: number; expiryStatus: string; qtyOnHand: number; qtyAllocated: number
}
interface AffectedOrder { orderId: string; orderReference: string | null; qty: number }

function ExpiryChip({ status }: { status: string }) {
  const s = EXPIRY_STYLE[status] ?? EXPIRY_STYLE.NO_EXPIRY
  return <Chip bg={s.bg} fg={s.fg}>{s.label}</Chip>
}

function ExpiringView() {
  const [days, setDays] = useState(30)
  const { data: rows = [], isLoading } = useQuery<ExpiryRow[]>({
    queryKey: ["whse-expiry-report", days],
    queryFn: async () => (await apiClient.get(`/api/v1/warehousing/lots/expiry-report?days=${days}`)).data,
  })
  return (
    <div>
      <div style={{ display: "flex", gap: 10, alignItems: "center", marginBottom: 14 }}>
        <label htmlFor="whse-expiry-days" style={{ fontSize: 12.5, color: "var(--hf-text-muted)" }}>Expiring within</label>
        <select id="whse-expiry-days" style={inputStyle} value={days} onChange={e => setDays(Number(e.target.value))}>
          {[7, 14, 30, 60, 90, 180].map(d => <option key={d} value={d}>{d} days</option>)}
        </select>
        <span style={{ fontSize: 12.5, color: "var(--hf-text-faint)" }}>{rows.length} position{rows.length === 1 ? "" : "s"}</span>
      </div>
      {isLoading ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>Loading…</p> : rows.length === 0
        ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>Nothing is expired or expiring in this window.</p>
        : (
          <div style={{ ...card, overflow: "hidden" }}>
            {rows.map((r, i) => (
              <div key={r.inventoryId} style={{ display: "flex", justifyContent: "space-between", gap: 10, alignItems: "center", padding: "11px 14px", borderTop: i === 0 ? "none" : "1px solid var(--hf-border-subtle)", flexWrap: "wrap" }}>
                <div>
                  <p style={{ fontSize: 13, fontWeight: 700, margin: 0, color: "var(--hf-text)" }}>{r.sku} <span style={{ fontWeight: 400, color: "var(--hf-text-muted)" }}>· lot {r.lotNumber}</span></p>
                  <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: 0 }}>
                    {r.clientName} · {r.locationCode} · {fmtQty(r.qtyOnHand)} on hand{r.qtyAllocated > 0 ? `, ${fmtQty(r.qtyAllocated)} allocated` : ""}{r.stockStatus !== "AVAILABLE" ? ` · ${r.stockStatus.toLowerCase()}` : ""}
                  </p>
                </div>
                <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                  <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{r.expiryDate} · {daysLabel(r.daysToExpiry)}</span>
                  <ExpiryChip status={r.expiryStatus} />
                </div>
              </div>
            ))}
          </div>
        )}
    </div>
  )
}

function LotsView() {
  const qc = useQueryClient()
  const canBlock = usePermission("WAREHOUSING_MANAGE") || usePermission("WAREHOUSING_ADMIN")
  const [action, setAction] = useState<{ lot: LotRow; kind: "block" | "unblock" } | null>(null)
  const [affected, setAffected] = useState<{ lot: string; orders: AffectedOrder[] } | null>(null)

  const { data: lots = [], isLoading } = useQuery<LotRow[]>({ queryKey: ["whse-lots"], queryFn: async () => (await apiClient.get("/api/v1/warehousing/lots")).data })
  const run = useMutation({
    mutationFn: async ({ lot, kind, reason }: { lot: LotRow; kind: "block" | "unblock"; reason: string }) =>
      (await apiClient.post(`/api/v1/warehousing/lots/${lot.id}/${kind}`, { reason })).data as AffectedOrder[] | null,
    onSuccess: (data, v) => {
      qc.invalidateQueries({ queryKey: ["whse-lots"] }); qc.invalidateQueries({ queryKey: ["whse-expiry-report"] }); qc.invalidateQueries({ queryKey: ["whse-floor-map"] })
      setAffected(v.kind === "block" ? { lot: v.lot.lotNumber, orders: data ?? [] } : null)
      setAction(null)
    },
  })

  return (
    <div>
      {affected && (
        <div role="status" style={{ ...card, padding: 14, marginBottom: 14, borderColor: "var(--hf-warning-border)" }}>
          <p style={{ fontSize: 13, fontWeight: 700, margin: "0 0 4px", color: "var(--hf-text)" }}>Lot {affected.lot} is blocked.</p>
          {affected.orders.length === 0
            ? <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0 }}>No open order holds stock of this lot.</p>
            : <>
                <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 4px" }}>These open orders hold stock of it. Open each and re-allocate its lots before it can ship:</p>
                {affected.orders.map(o => <p key={o.orderId} style={{ fontSize: 12.5, margin: 0, color: "var(--hf-text)" }}>{o.orderReference ?? o.orderId.slice(0, 8)} · {fmtQty(o.qty)}</p>)}
              </>}
          <button onClick={() => setAffected(null)} style={{ ...btnGhost, marginTop: 10 }}>Dismiss</button>
        </div>
      )}
      {isLoading ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>Loading…</p> : lots.length === 0
        ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>No lots yet. Mark an item as lot tracked, then receive it with a lot number.</p>
        : (
          <div style={{ ...card, overflow: "hidden" }}>
            {lots.map((l, i) => (
              <div key={l.id} style={{ padding: "12px 14px", borderTop: i === 0 ? "none" : "1px solid var(--hf-border-subtle)", display: "flex", justifyContent: "space-between", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
                <div>
                  <p style={{ fontSize: 13, fontWeight: 700, margin: 0, color: "var(--hf-text)" }}>
                    {l.sku} <span style={{ fontWeight: 400, color: "var(--hf-text-muted)" }}>· lot {l.lotNumber}</span> {l.blocked && <Chip bg="var(--hf-danger-soft)" fg="var(--hf-danger-text)">BLOCKED</Chip>}
                  </p>
                  <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: 0 }}>
                    {fmtQty(l.onHand)} on hand · {fmtQty(l.available)} available{l.expiryDate ? ` · exp ${l.expiryDate}${l.daysToExpiry != null ? ` (${daysLabel(l.daysToExpiry)})` : ""}` : ""}
                  </p>
                  {l.blocked && l.blockedReason && <p style={{ fontSize: 11.5, color: "var(--hf-danger-text)", margin: "2px 0 0" }}>{l.blockedReason}</p>}
                </div>
                <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                  <ExpiryChip status={l.expiryStatus} />
                  {canBlock && (l.blocked
                    ? <button onClick={() => setAction({ lot: l, kind: "unblock" })} style={btnGhost}><ShieldCheck size={13} /> Release</button>
                    : <button onClick={() => setAction({ lot: l, kind: "block" })} style={btnDanger}><Ban size={13} /> Block</button>)}
                </div>
              </div>
            ))}
          </div>
        )}
      {action && (
        <ReasonModal
          title={action.kind === "block" ? `Block lot ${action.lot.lotNumber}` : `Release lot ${action.lot.lotNumber}`}
          intro={action.kind === "block"
            ? "A blocked lot is never allocated, cannot be shipped and takes no more receipts. Use it for a recall or a quality hold."
            : "The lot can be allocated, shipped and received into again."}
          confirmLabel={action.kind === "block" ? "Block lot" : "Release lot"}
          pending={run.isPending} error={run.error}
          onConfirm={reason => run.mutate({ lot: action.lot, kind: action.kind, reason })}
          onClose={() => { setAction(null); run.reset() }} />
      )}
    </div>
  )
}

export default function WhseLotsTab() {
  const [view, setView] = useState<"expiring" | "lots">("expiring")
  return (
    <div>
      <div role="tablist" aria-label="Lots view" style={{ display: "inline-flex", border: "1px solid var(--hf-border)", borderRadius: 8, overflow: "hidden", marginBottom: 14 }}>
        {(["expiring", "lots"] as const).map(v => (
          <button key={v} role="tab" aria-selected={view === v} onClick={() => setView(v)}
            style={{ padding: "7px 16px", border: "none", fontSize: 12.5, fontWeight: 600, cursor: "pointer", background: view === v ? "var(--hf-accent-solid-strong)" : "var(--hf-surface)", color: view === v ? "var(--hf-text-on-solid)" : "var(--hf-text-muted)" }}>
            {v === "expiring" ? "Expiring" : "All lots"}
          </button>
        ))}
      </div>
      {view === "expiring" ? <ExpiringView /> : <LotsView />}
    </div>
  )
}
