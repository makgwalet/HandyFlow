// src/pages/warehousing/WhseStockTab.tsx
//
// Stock across every client, and the movement ledger. Both read the cross-client operations endpoints (/operations/inventory and
// /operations/movements) and can be cut to one warehouse, one client and a search or type. Read only: stock is adjusted from the client's own
// inventory screen, where the approval rules apply.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { card, fmtQty, inputStyle, Chip } from "./whseUi"
import WhseWarehouseSelect, { useWarehouses } from "./WhseWarehouseSelect"
import { pickWarehouse, showSelector } from "./warehouses"
import { hasNext, listParams, movementText, MOVEMENT_TYPES, rangeText, signedQty, statusText, STOCK_STATUSES } from "./stockView"

interface Paged<T> { content: T[]; total: number; page: number; size: number }
interface StockRow {
  inventoryId: string; clientName: string; sku: string; description: string | null; uom: string | null; locationCode: string; zone: string | null
  status: string; qtyOnHand: number; qtyAllocated: number; available: number; lotNumber: string | null; expiryDate: string | null
}
interface MovementRow {
  id: string; at: string; clientName: string; sku: string; locationCode: string; movementType: string; status: string | null
  qtyChange: number; qtyBefore: number | null; qtyAfter: number | null; referenceNumber: string | null; notes: string | null
}
interface ClientLite { id: string; tradingName: string }

const SIZE = 50

function Pager({ p, onPage }: { p: { total: number; page: number; size: number }; onPage: (n: number) => void }) {
  return (
    <div style={{ display: "flex", gap: 10, alignItems: "center", justifyContent: "flex-end", marginTop: 12, fontSize: 12.5, color: "var(--hf-text-muted)" }}>
      <span>{rangeText(p)}</span>
      <button type="button" disabled={p.page === 0} onClick={() => onPage(p.page - 1)} style={{ ...inputStyle, cursor: p.page === 0 ? "default" : "pointer" }}>Previous</button>
      <button type="button" disabled={!hasNext(p)} onClick={() => onPage(p.page + 1)} style={{ ...inputStyle, cursor: hasNext(p) ? "pointer" : "default" }}>Next</button>
    </div>
  )
}

function Filters({ clients, clientId, setClientId, children }: { clients: ClientLite[]; clientId: string; setClientId: (v: string) => void; children?: React.ReactNode }) {
  return (
    <>
      <select aria-label="Client" style={inputStyle} value={clientId} onChange={e => setClientId(e.target.value)}>
        <option value="">All clients</option>
        {clients.map(c => <option key={c.id} value={c.id}>{c.tradingName}</option>)}
      </select>
      {children}
    </>
  )
}

function StockView({ warehouseId, clients }: { warehouseId: string | undefined; clients: ClientLite[] }) {
  const [clientId, setClientId] = useState("")
  const [status, setStatus] = useState("")
  const [q, setQ] = useState("")
  const [includeEmpty, setIncludeEmpty] = useState(false)
  const [page, setPage] = useState(0)
  const params = listParams({ clientId, status, q, includeEmpty, warehouseId, page, size: SIZE })
  const { data, isLoading } = useQuery<Paged<StockRow>>({
    queryKey: ["whse-stock-list", params],
    queryFn: async () => (await apiClient.get("/api/v1/warehousing/operations/inventory", { params })).data,
  })
  const reset = <T,>(set: (v: T) => void) => (v: T) => { set(v); setPage(0) }
  const rows = data?.content ?? []
  return (
    <div>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "center", marginBottom: 14 }}>
        <Filters clients={clients} clientId={clientId} setClientId={reset(setClientId)}>
          <select aria-label="Stock status" style={inputStyle} value={status} onChange={e => reset(setStatus)(e.target.value)}>
            <option value="">Any status</option>
            {STOCK_STATUSES.map(s => <option key={s.value} value={s.value}>{s.label}</option>)}
          </select>
          <input aria-label="Search stock" placeholder="Search SKU or description" style={{ ...inputStyle, minWidth: 200 }} value={q} onChange={e => reset(setQ)(e.target.value)} />
          <label style={{ fontSize: 12.5, color: "var(--hf-text-muted)", display: "inline-flex", gap: 6, alignItems: "center" }}>
            <input type="checkbox" checked={includeEmpty} onChange={e => reset(setIncludeEmpty)(e.target.checked)} /> Show empty positions
          </label>
        </Filters>
      </div>
      {isLoading ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>Loading…</p> : rows.length === 0
        ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>No stock matches these filters.</p>
        : (
          <div style={{ ...card, overflow: "hidden" }}>
            {rows.map((r, i) => (
              <div key={r.inventoryId} style={{ display: "flex", justifyContent: "space-between", gap: 10, alignItems: "center", padding: "11px 14px", borderTop: i === 0 ? "none" : "1px solid var(--hf-border-subtle)", flexWrap: "wrap" }}>
                <div>
                  <p style={{ margin: 0, fontSize: 13, fontWeight: 700, color: "var(--hf-text)" }}>{r.sku}{r.description ? ` · ${r.description}` : ""}</p>
                  <p style={{ margin: "2px 0 0", fontSize: 11.5, color: "var(--hf-text-faint)" }}>
                    {r.clientName} · {r.locationCode}{r.zone ? ` (${r.zone})` : ""}{r.lotNumber ? ` · lot ${r.lotNumber}` : ""}{r.expiryDate ? ` · expires ${r.expiryDate}` : ""}
                  </p>
                </div>
                <div style={{ display: "flex", gap: 10, alignItems: "center", fontSize: 12.5 }}>
                  {r.status !== "AVAILABLE" && <Chip bg="var(--hf-warning-bg, #fef3c7)" fg="var(--hf-warning-text, #92400e)">{statusText(r.status)}</Chip>}
                  <span title="On hand">{fmtQty(r.qtyOnHand)} on hand</span>
                  <span title="Allocated to orders" style={{ color: "var(--hf-text-muted)" }}>{fmtQty(r.qtyAllocated)} allocated</span>
                  <span title="Available to promise" style={{ fontWeight: 700 }}>{fmtQty(r.available)} available</span>
                </div>
              </div>
            ))}
          </div>
        )}
      {data && <Pager p={data} onPage={setPage} />}
    </div>
  )
}

function MovementsView({ warehouseId, clients }: { warehouseId: string | undefined; clients: ClientLite[] }) {
  const [clientId, setClientId] = useState("")
  const [type, setType] = useState("")
  const [from, setFrom] = useState("")
  const [to, setTo] = useState("")
  const [page, setPage] = useState(0)
  const params = listParams({ clientId, type, from, to, warehouseId, page, size: SIZE })
  const { data, isLoading } = useQuery<Paged<MovementRow>>({
    queryKey: ["whse-movement-list", params],
    queryFn: async () => (await apiClient.get("/api/v1/warehousing/operations/movements", { params })).data,
  })
  const reset = <T,>(set: (v: T) => void) => (v: T) => { set(v); setPage(0) }
  const rows = data?.content ?? []
  return (
    <div>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "center", marginBottom: 14 }}>
        <Filters clients={clients} clientId={clientId} setClientId={reset(setClientId)}>
          <select aria-label="Movement type" style={inputStyle} value={type} onChange={e => reset(setType)(e.target.value)}>
            <option value="">Any movement</option>
            {MOVEMENT_TYPES.map(m => <option key={m.value} value={m.value}>{m.label}</option>)}
          </select>
          <label style={{ fontSize: 12.5, color: "var(--hf-text-muted)", display: "inline-flex", gap: 6, alignItems: "center" }}>
            From <input type="date" aria-label="From date" style={inputStyle} value={from} onChange={e => reset(setFrom)(e.target.value)} />
          </label>
          <label style={{ fontSize: 12.5, color: "var(--hf-text-muted)", display: "inline-flex", gap: 6, alignItems: "center" }}>
            To <input type="date" aria-label="To date" style={inputStyle} value={to} onChange={e => reset(setTo)(e.target.value)} />
          </label>
        </Filters>
      </div>
      {isLoading ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>Loading…</p> : rows.length === 0
        ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>No movements match these filters.</p>
        : (
          <div style={{ ...card, overflow: "hidden" }}>
            {rows.map((m, i) => (
              <div key={m.id} style={{ display: "flex", justifyContent: "space-between", gap: 10, alignItems: "center", padding: "11px 14px", borderTop: i === 0 ? "none" : "1px solid var(--hf-border-subtle)", flexWrap: "wrap" }}>
                <div>
                  <p style={{ margin: 0, fontSize: 13, fontWeight: 700, color: "var(--hf-text)" }}>{movementText(m.movementType)} · {m.sku}</p>
                  <p style={{ margin: "2px 0 0", fontSize: 11.5, color: "var(--hf-text-faint)" }}>
                    {new Date(m.at).toLocaleString("en-ZA")} · {m.clientName} · {m.locationCode}{m.referenceNumber ? ` · ${m.referenceNumber}` : ""}{m.notes ? ` · ${m.notes}` : ""}
                  </p>
                </div>
                <div style={{ fontSize: 12.5, textAlign: "right" }}>
                  <span style={{ fontWeight: 700 }}>{signedQty(m.qtyChange)}</span>
                  {m.qtyBefore != null && m.qtyAfter != null && <span style={{ color: "var(--hf-text-muted)" }}> ({fmtQty(m.qtyBefore)} → {fmtQty(m.qtyAfter)})</span>}
                </div>
              </div>
            ))}
          </div>
        )}
      {data && <Pager p={data} onPage={setPage} />}
    </div>
  )
}

export default function WhseStockTab() {
  const [view, setView] = useState<"stock" | "movements">("stock")
  const [chosenWh, setChosenWh] = useState<string | undefined>(undefined)
  const warehouses = useWarehouses()
  const warehouseId = showSelector(warehouses) ? pickWarehouse(warehouses, chosenWh) : undefined
  const { data: clients = [] } = useQuery<ClientLite[]>({ queryKey: ["whse-clients-all"], queryFn: async () => (await apiClient.get("/api/v1/warehousing/clients/all")).data })
  const clientList = Array.isArray(clients) ? clients : []
  const tab = (id: "stock" | "movements", text: string) => (
    <button type="button" role="tab" aria-selected={view === id} onClick={() => setView(id)}
      style={{ ...inputStyle, cursor: "pointer", fontWeight: view === id ? 700 : 500, background: view === id ? "var(--hf-surface-alt, var(--hf-surface))" : "var(--hf-surface)" }}>{text}</button>
  )
  return (
    <div>
      <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", marginBottom: 14 }} role="tablist" aria-label="Stock views">
        {tab("stock", "Stock on hand")}
        {tab("movements", "Movements")}
      </div>
      <WhseWarehouseSelect value={chosenWh} onChange={setChosenWh} />
      {view === "stock" ? <StockView warehouseId={warehouseId} clients={clientList} /> : <MovementsView warehouseId={warehouseId} clients={clientList} />}
    </div>
  )
}
