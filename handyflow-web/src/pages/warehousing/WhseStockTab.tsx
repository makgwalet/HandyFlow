// src/pages/warehousing/WhseStockTab.tsx
//
// Stock across every client, the movement ledger and stock alerts. Stock and movements read the cross-client operations endpoints (/operations/inventory
// and /operations/movements) and can be cut to one warehouse, one client and a search or type; they are read only (stock is adjusted from the client's own
// inventory screen, where the approval rules apply). Alerts come from /stock-alerts, where managers acknowledge them, resolve them with a note and set a
// warehouse's own minimum and maximum.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { btnGhost, btnPrimary, card, Chip, ErrorLine, fmtQty, inputStyle, labelStyle, Modal, ReasonModal } from "./whseUi"
import WhseWarehouseSelect, { useWarehouses } from "./WhseWarehouseSelect"
import { pickWarehouse, showSelector } from "./warehouses"
import { usePermission } from "../../hooks/usePermission"
import { alertKindText, alertTone, ALERT_KINDS, hasNext, levelsText, listParams, movementText, MOVEMENT_TYPES, rangeText, recommendationText, signedQty, statusText, STOCK_STATUSES, stockBreakdown, thresholdProblem, type AlertRow } from "./stockView"

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

const TONE: Record<string, { bg: string; fg: string }> = {
  bad: { bg: "var(--hf-danger-bg, #fee2e2)", fg: "var(--hf-danger-text, #991b1b)" },
  warn: { bg: "var(--hf-warning-bg, #fef3c7)", fg: "var(--hf-warning-text, #92400e)" },
  info: { bg: "var(--hf-info-bg, #dbeafe)", fg: "var(--hf-info-text, #1e40af)" },
}

function LevelsModal({ alert, onClose }: { alert: AlertRow; onClose: () => void }) {
  const qc = useQueryClient()
  const [min, setMin] = useState(alert.minSource === "WAREHOUSE" && alert.minQty != null ? String(alert.minQty) : "")
  const [max, setMax] = useState(alert.maxSource === "WAREHOUSE" && alert.maxQty != null ? String(alert.maxQty) : "")
  const problem = thresholdProblem(min, max)
  const save = useMutation({
    mutationFn: async () => apiClient.put("/api/v1/warehousing/stock-alerts/thresholds", { warehouseId: alert.warehouseId, itemId: alert.itemId, minQty: min.trim() === "" ? null : Number(min), maxQty: max.trim() === "" ? null : Number(max) }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["whse-alerts"] }); onClose() },
  })
  return (
    <Modal title={`Levels for ${alert.sku} in ${alert.warehouseCode}`} onClose={onClose}>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 12px" }}>These apply to this warehouse only. A level left blank uses the item's own default.</p>
      <div style={{ display: "flex", gap: 10 }}>
        <div style={{ flex: 1 }}><label style={labelStyle} htmlFor="lv-min">Minimum</label><input id="lv-min" style={{ ...inputStyle, width: "100%" }} inputMode="decimal" value={min} onChange={e => setMin(e.target.value)} /></div>
        <div style={{ flex: 1 }}><label style={labelStyle} htmlFor="lv-max">Maximum</label><input id="lv-max" style={{ ...inputStyle, width: "100%" }} inputMode="decimal" value={max} onChange={e => setMax(e.target.value)} /></div>
      </div>
      {(min !== "" || max !== "") && problem && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: "8px 0 0" }}>{problem}</p>}
      <ErrorLine error={save.error} />
      <div style={{ display: "flex", gap: 8, marginTop: 14 }}>
        <button onClick={onClose} style={{ ...btnGhost, flex: 1, justifyContent: "center" }}>Back</button>
        <button onClick={() => save.mutate()} disabled={!!problem || save.isPending} style={{ ...btnPrimary, flex: 1, justifyContent: "center", opacity: problem ? 0.5 : 1 }}>{save.isPending ? "Saving…" : "Save levels"}</button>
      </div>
    </Modal>
  )
}

function AlertsView({ warehouseId, clients }: { warehouseId: string | undefined; clients: ClientLite[] }) {
  const qc = useQueryClient()
  const canAct = usePermission("WAREHOUSING_MANAGE") || usePermission("WAREHOUSING_ADMIN")
  const [clientId, setClientId] = useState("")
  const [kind, setKind] = useState("")
  const [showResolved, setShowResolved] = useState(false)
  const [resolving, setResolving] = useState<AlertRow | null>(null)
  const [editing, setEditing] = useState<AlertRow | null>(null)
  const params = listParams({ clientId, kind, warehouseId, status: showResolved ? "RESOLVED" : "" })
  const { data, isLoading } = useQuery<AlertRow[]>({
    queryKey: ["whse-alerts", params],
    queryFn: async () => (await apiClient.get("/api/v1/warehousing/stock-alerts", { params })).data,
  })
  const refresh = () => qc.invalidateQueries({ queryKey: ["whse-alerts"] })
  const ack = useMutation({ mutationFn: async (id: string) => apiClient.post(`/api/v1/warehousing/stock-alerts/${id}/acknowledge`), onSuccess: refresh })
  const resolve = useMutation({
    mutationFn: async (v: { id: string; note: string }) => apiClient.post(`/api/v1/warehousing/stock-alerts/${v.id}/resolve`, { note: v.note }),
    onSuccess: () => { setResolving(null); refresh() },
  })
  const rows = Array.isArray(data) ? data : []
  return (
    <div>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "center", marginBottom: 14 }}>
        <Filters clients={clients} clientId={clientId} setClientId={setClientId}>
          <select aria-label="Alert type" style={inputStyle} value={kind} onChange={e => setKind(e.target.value)}>
            <option value="">Any alert</option>
            {ALERT_KINDS.map(k => <option key={k.value} value={k.value}>{k.label}</option>)}
          </select>
          <label style={{ fontSize: 12.5, color: "var(--hf-text-muted)", display: "inline-flex", gap: 6, alignItems: "center" }}>
            <input type="checkbox" checked={showResolved} onChange={e => setShowResolved(e.target.checked)} /> Show resolved
          </label>
        </Filters>
      </div>
      {isLoading ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>Loading…</p> : rows.length === 0
        ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>{showResolved ? "No resolved alerts." : "No stock alerts. Levels come from each item's own minimum and maximum, or from a warehouse's own levels."}</p>
        : (
          <div style={{ ...card, overflow: "hidden" }}>
            {rows.map((a, i) => {
              const tone = TONE[alertTone(a.kind)]
              const rec = recommendationText(a)
              return (
                <div key={a.id} style={{ padding: "11px 14px", borderTop: i === 0 ? "none" : "1px solid var(--hf-border-subtle)" }}>
                  <div style={{ display: "flex", justifyContent: "space-between", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
                    <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
                      <Chip bg={tone.bg} fg={tone.fg}>{alertKindText(a.kind)}</Chip>
                      <span style={{ fontSize: 13, fontWeight: 700 }}>{a.sku}{a.description ? ` · ${a.description}` : ""}</span>
                      <span style={{ fontSize: 11.5, color: "var(--hf-text-faint)" }}>{a.warehouseCode} · {a.clientName}</span>
                      {a.status === "ACKNOWLEDGED" && <Chip bg="var(--hf-surface-alt, #f1f5f9)" fg="var(--hf-text-muted)">Acknowledged</Chip>}
                    </div>
                    {canAct && a.status !== "RESOLVED" && (
                      <div style={{ display: "flex", gap: 6 }}>
                        <button type="button" style={btnGhost} onClick={() => setEditing(a)}>Set levels</button>
                        {a.status === "OPEN" && <button type="button" style={btnGhost} disabled={ack.isPending} onClick={() => ack.mutate(a.id)}>Acknowledge</button>}
                        <button type="button" style={btnGhost} onClick={() => setResolving(a)}>Resolve</button>
                      </div>
                    )}
                  </div>
                  <p style={{ margin: "4px 0 0", fontSize: 12, color: "var(--hf-text-muted)" }}>{stockBreakdown(a)}{levelsText(a) ? ` · ${levelsText(a)}` : ""}</p>
                  {rec && <p style={{ margin: "2px 0 0", fontSize: 12, fontWeight: 600 }}>{rec}</p>}
                  {a.status === "RESOLVED" && <p style={{ margin: "2px 0 0", fontSize: 12, color: "var(--hf-text-faint)" }}>{a.resolution === "MANUAL" ? `Resolved: ${a.resolutionNote}` : a.resolution === "CLEARED" ? "Cleared: the stock recovered." : "Replaced by a different alert."}</p>}
                </div>
              )
            })}
          </div>
        )}
      {resolving && <ReasonModal title={`Resolve ${resolving.sku} in ${resolving.warehouseCode}`} intro="It will not be raised again until the stock has recovered and fallen again." confirmLabel="Resolve" pending={resolve.isPending} error={resolve.error}
        onConfirm={note => resolve.mutate({ id: resolving.id, note })} onClose={() => setResolving(null)} />}
      {editing && <LevelsModal alert={editing} onClose={() => setEditing(null)} />}
    </div>
  )
}

export default function WhseStockTab() {
  const [view, setView] = useState<"stock" | "movements" | "alerts">("stock")
  const [chosenWh, setChosenWh] = useState<string | undefined>(undefined)
  const warehouses = useWarehouses()
  const warehouseId = showSelector(warehouses) ? pickWarehouse(warehouses, chosenWh) : undefined
  const { data: clients = [] } = useQuery<ClientLite[]>({ queryKey: ["whse-clients-all"], queryFn: async () => (await apiClient.get("/api/v1/warehousing/clients/all")).data })
  const clientList = Array.isArray(clients) ? clients : []
  const tab = (id: "stock" | "movements" | "alerts", text: string) => (
    <button type="button" role="tab" aria-selected={view === id} onClick={() => setView(id)}
      style={{ ...inputStyle, cursor: "pointer", fontWeight: view === id ? 700 : 500, background: view === id ? "var(--hf-surface-alt, var(--hf-surface))" : "var(--hf-surface)" }}>{text}</button>
  )
  return (
    <div>
      <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", marginBottom: 14 }} role="tablist" aria-label="Stock views">
        {tab("stock", "Stock on hand")}
        {tab("movements", "Movements")}
        {tab("alerts", "Alerts")}
      </div>
      <WhseWarehouseSelect value={chosenWh} onChange={setChosenWh} />
      {view === "stock" ? <StockView warehouseId={warehouseId} clients={clientList} /> : view === "movements" ? <MovementsView warehouseId={warehouseId} clients={clientList} /> : <AlertsView warehouseId={warehouseId} clients={clientList} />}
    </div>
  )
}
