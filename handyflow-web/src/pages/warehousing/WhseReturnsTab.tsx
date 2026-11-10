// src/pages/warehousing/WhseReturnsTab.tsx
//
// Customer returns (RMAs). A return is raised for a client, optionally against a shipped order (the quantity that can come back is capped at
// what that order shipped, less other returns), then received line by line: goods fit to restock, goods held for inspection and goods
// damaged, each put away somewhere. Closing a return with a difference between expected and received needs a note.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { ArrowLeft, Plus } from "lucide-react"
import { apiClient } from "../../api/client"
import { btnDanger, btnGhost, btnPrimary, card, Chip, ErrorLine, fmtQty, inputStyle, labelStyle, Modal } from "./whseUi"
import { buildReturnReceivePayload, emptyReturnReceive, ReturnLineForm, ReturnReceiveForm, Tracking, validateNewReturn, validateReturnReceive } from "./receiving"

interface ReturnRow { id: string; rmaNumber: string; clientName: string; clientId: string; orderReference: string | null; status: string; reason: string; closeNote: string | null; createdAt: string }
interface LineRow { id: string; itemId: string; sku: string; description: string | null; expectedQty: number; restocked: number; held: number; damaged: number; received: number; maxReturnable: number }
interface Detail { header: ReturnRow; lines: LineRow[] }
interface Client { id: string; tradingName: string }
interface Item { id: string; sku: string; lotTracked?: boolean; expiryTracked?: boolean }
interface Loc { id: string; code: string; active: boolean }

const STATUS_STYLE: Record<string, { bg: string; fg: string }> = {
  EXPECTED: { bg: "var(--hf-info-soft)", fg: "var(--hf-info-text)" },
  RECEIVING: { bg: "var(--hf-warning-soft-strong)", fg: "var(--hf-warning-text-deep)" },
  CLOSED: { bg: "var(--hf-success-soft)", fg: "var(--hf-success-text-strong)" },
  CANCELLED: { bg: "var(--hf-surface-muted)", fg: "var(--hf-text-muted)" },
}
const StatusChip = ({ s }: { s: string }) => <Chip bg={(STATUS_STYLE[s] ?? STATUS_STYLE.CANCELLED).bg} fg={(STATUS_STYLE[s] ?? STATUS_STYLE.CANCELLED).fg}>{s}</Chip>

function NewReturnModal({ onClose, onCreated }: { onClose: () => void; onCreated: (id: string) => void }) {
  const qc = useQueryClient()
  const [clientId, setClientId] = useState("")
  const [reason, setReason] = useState("")
  const [lines, setLines] = useState<ReturnLineForm[]>([{ itemId: "", qty: "" }])
  const { data: clients } = useQuery<{ content: Client[] }>({ queryKey: ["whse-clients-all"], queryFn: async () => (await apiClient.get("/api/v1/warehousing/clients?size=200")).data })
  const { data: items = [] } = useQuery<Item[]>({ queryKey: ["whse-items", clientId], enabled: !!clientId, queryFn: async () => (await apiClient.get(`/api/v1/warehousing/clients/${clientId}/items`)).data })
  const problem = !clientId ? "Choose the client." : validateNewReturn(reason, lines)
  const create = useMutation({
    mutationFn: async () => (await apiClient.post("/api/v1/warehousing/returns", {
      clientId, reason: reason.trim(),
      lines: lines.filter(l => l.itemId).map(l => ({ itemId: l.itemId, expectedQty: Number(l.qty.replace(",", ".")) })),
    })).data as { id: string },
    onSuccess: d => { qc.invalidateQueries({ queryKey: ["whse-returns"] }); onCreated(d.id) },
  })
  const setLine = (i: number, k: keyof ReturnLineForm, v: string) => setLines(ls => ls.map((l, j) => (j === i ? { ...l, [k]: v } : l)))
  return (
    <Modal title="New return" onClose={onClose} width={480}>
      <div style={{ display: "grid", gap: 10 }}>
        <div><label style={labelStyle}>Client *</label>
          <select aria-label="Client" style={{ ...inputStyle, width: "100%" }} value={clientId} onChange={e => { setClientId(e.target.value); setLines([{ itemId: "", qty: "" }]) }}>
            <option value="">Choose…</option>{(clients?.content ?? []).map(c => <option key={c.id} value={c.id}>{c.tradingName}</option>)}
          </select></div>
        <div><label style={labelStyle}>Why are the goods coming back? *</label><input aria-label="Return reason" style={{ ...inputStyle, width: "100%" }} value={reason} onChange={e => setReason(e.target.value)} /></div>
        {lines.map((l, i) => (
          <div key={i} style={{ display: "flex", gap: 8 }}>
            <select aria-label={`Item ${i + 1}`} style={{ ...inputStyle, flex: 1 }} value={l.itemId} onChange={e => setLine(i, "itemId", e.target.value)} disabled={!clientId}>
              <option value="">Item…</option>{items.map(it => <option key={it.id} value={it.id}>{it.sku}</option>)}
            </select>
            <input aria-label={`Quantity ${i + 1}`} inputMode="decimal" placeholder="Qty" style={{ ...inputStyle, width: 80 }} value={l.qty} onChange={e => setLine(i, "qty", e.target.value)} />
          </div>
        ))}
        <button onClick={() => setLines(ls => [...ls, { itemId: "", qty: "" }])} style={{ ...btnGhost, justifySelf: "start" }}><Plus size={13} /> Add line</button>
      </div>
      {problem && (reason || lines.some(l => l.itemId)) && <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: "10px 0 0" }}>{problem}</p>}
      <ErrorLine error={create.error} fallback="Could not create the return" />
      <button onClick={() => create.mutate()} disabled={!!problem || create.isPending} style={{ ...btnPrimary, width: "100%", justifyContent: "center", marginTop: 14, opacity: problem ? 0.5 : 1 }}>
        {create.isPending ? "Creating…" : "Create return"}
      </button>
    </Modal>
  )
}

function ReceiveLine({ returnId, line, item, locations, onDone }: { returnId: string; line: LineRow; item?: Item; locations: Loc[]; onDone: () => void }) {
  const [f, setF] = useState<ReturnReceiveForm>(emptyReturnReceive)
  const t: Tracking = { lotTracked: !!item?.lotTracked, expiryTracked: !!item?.expiryTracked }
  const set = (k: keyof ReturnReceiveForm, v: string) => setF(s => ({ ...s, [k]: v }))
  const problem = validateReturnReceive(f, t)
  const receive = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/warehousing/returns/${returnId}/lines/${line.id}/receive`, buildReturnReceivePayload(f, t)),
    onSuccess: () => { setF(emptyReturnReceive); onDone() },
  })
  const q = (k: keyof ReturnReceiveForm, label: string) => <div style={{ width: 84 }}><label style={labelStyle}>{label}</label><input aria-label={label} inputMode="decimal" style={{ ...inputStyle, width: "100%" }} value={f[k]} onChange={e => set(k, e.target.value)} /></div>
  return (
    <div style={{ marginTop: 10, display: "grid", gap: 8 }}>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "flex-end" }}>
        {q("qtyRestock", "Restock")}{q("qtyHeld", "Hold")}{q("qtyDamaged", "Damaged")}
        <div style={{ flex: 1, minWidth: 140 }}><label style={labelStyle}>Put away at</label>
          <select aria-label="Put away at" style={{ ...inputStyle, width: "100%" }} value={f.locationId} onChange={e => set("locationId", e.target.value)}>
            <option value="">Choose…</option>{locations.map(l => <option key={l.id} value={l.id}>{l.code}</option>)}
          </select></div>
        {t.lotTracked && <div style={{ width: 120 }}><label style={labelStyle}>Lot *</label><input aria-label="Lot" style={{ ...inputStyle, width: "100%" }} value={f.lotNumber} onChange={e => set("lotNumber", e.target.value)} /></div>}
      </div>
      <input aria-label="What is wrong" placeholder="What is wrong (held or damaged)" style={{ ...inputStyle, width: "100%" }} value={f.reason} onChange={e => set("reason", e.target.value)} />
      <div style={{ display: "flex", gap: 10, alignItems: "center" }}>
        <button onClick={() => receive.mutate()} disabled={!!problem || receive.isPending} style={{ ...btnPrimary, opacity: problem ? 0.5 : 1 }}>{receive.isPending ? "Receiving…" : "Receive"}</button>
        {problem && (f.qtyRestock || f.qtyHeld || f.qtyDamaged) && <span style={{ fontSize: 11.5, color: "var(--hf-text-faint)" }}>{problem}</span>}
      </div>
      <ErrorLine error={receive.error} fallback="Could not receive this return" />
    </div>
  )
}

function ReturnDetail({ id, onBack }: { id: string; onBack: () => void }) {
  const qc = useQueryClient()
  const [open, setOpen] = useState<string | null>(null)
  const [note, setNote] = useState("")
  const { data } = useQuery<Detail>({ queryKey: ["whse-return", id], queryFn: async () => (await apiClient.get(`/api/v1/warehousing/returns/${id}`)).data })
  const clientId = data?.header.clientId
  const { data: items = [] } = useQuery<Item[]>({ queryKey: ["whse-items", clientId], enabled: !!clientId, queryFn: async () => (await apiClient.get(`/api/v1/warehousing/clients/${clientId}/items`)).data })
  const { data: locations = [] } = useQuery<Loc[]>({ queryKey: ["whse-locations"], queryFn: async () => (await apiClient.get("/api/v1/warehousing/locations")).data })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["whse-return", id] }); qc.invalidateQueries({ queryKey: ["whse-returns"] }); qc.invalidateQueries({ queryKey: ["whse-inspection-queue"] }); setOpen(null) }
  const close = useMutation({ mutationFn: async () => apiClient.post(`/api/v1/warehousing/returns/${id}/close`, { note: note.trim() || null }), onSuccess: () => { setNote(""); refresh() } })
  const cancel = useMutation({ mutationFn: async () => apiClient.post(`/api/v1/warehousing/returns/${id}/cancel`), onSuccess: refresh })
  if (!data) return <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>Loading…</p>
  const h = data.header
  const finished = h.status === "CLOSED" || h.status === "CANCELLED"
  return (
    <div>
      <button onClick={onBack} style={{ display: "flex", alignItems: "center", gap: 6, background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-muted)", fontSize: 13, marginBottom: 14, padding: 0 }}><ArrowLeft size={15} /> All returns</button>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 10, flexWrap: "wrap", marginBottom: 16 }}>
        <div>
          <h3 style={{ fontSize: 16, fontWeight: 800, margin: "0 0 4px", color: "var(--hf-text)" }}>{h.rmaNumber} <StatusChip s={h.status} /></h3>
          <p style={{ fontSize: 12.5, color: "var(--hf-text-faint)", margin: 0 }}>{h.clientName}{h.orderReference ? ` · against ${h.orderReference}` : ""} · {h.reason}</p>
        </div>
        {h.status === "EXPECTED" && <button onClick={() => { if (confirm("Cancel this return?")) cancel.mutate() }} style={btnDanger}>Cancel return</button>}
      </div>
      <ErrorLine error={cancel.error} />
      <div style={{ ...card, overflow: "hidden" }}>
        {data.lines.map((l, i) => (
          <div key={l.id} style={{ padding: "13px 16px", borderTop: i === 0 ? "none" : "1px solid var(--hf-border-subtle)" }}>
            <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
              <div>
                <p style={{ fontSize: 13, fontWeight: 700, margin: 0, color: "var(--hf-text)" }}>{l.sku}</p>
                <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: 0 }}>
                  Expected {fmtQty(l.expectedQty)} · received {fmtQty(l.received)} ({fmtQty(l.restocked)} restocked, {fmtQty(l.held)} held, {fmtQty(l.damaged)} damaged) · up to {fmtQty(l.maxReturnable)} can still come back
                </p>
              </div>
              {!finished && <button onClick={() => setOpen(open === l.id ? null : l.id)} style={btnGhost}>{open === l.id ? "Close" : "Receive"}</button>}
            </div>
            {open === l.id && <ReceiveLine returnId={id} line={l} item={items.find(x => x.id === l.itemId)} locations={locations.filter(x => x.active)} onDone={refresh} />}
          </div>
        ))}
      </div>
      {finished ? (h.closeNote && <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", marginTop: 12 }}>Closing note: {h.closeNote}</p>) : (
        <div style={{ marginTop: 16 }}>
          <label style={labelStyle}>Closing note (needed when received differs from expected)</label>
          <input aria-label="Closing note" style={{ ...inputStyle, width: "100%" }} value={note} onChange={e => setNote(e.target.value)} />
          <button onClick={() => close.mutate()} disabled={close.isPending} style={{ ...btnPrimary, marginTop: 10}}>{close.isPending ? "Closing…" : "Close return"}</button>
          <ErrorLine error={close.error} fallback="Could not close the return" />
        </div>
      )}
    </div>
  )
}

export default function WhseReturnsTab() {
  const [selected, setSelected] = useState<string | null>(null)
  const [showNew, setShowNew] = useState(false)
  const [status, setStatus] = useState("")
  const { data: rows = [], isLoading } = useQuery<ReturnRow[]>({
    queryKey: ["whse-returns", status],
    queryFn: async () => (await apiClient.get(`/api/v1/warehousing/returns${status ? `?status=${status}` : ""}`)).data,
  })
  if (selected) return <ReturnDetail id={selected} onBack={() => setSelected(null)} />
  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 10, alignItems: "center", marginBottom: 14, flexWrap: "wrap" }}>
        <select aria-label="Status" style={inputStyle} value={status} onChange={e => setStatus(e.target.value)}>
          <option value="">All returns</option><option value="EXPECTED">Expected</option><option value="RECEIVING">Receiving</option><option value="CLOSED">Closed</option><option value="CANCELLED">Cancelled</option>
        </select>
        <button onClick={() => setShowNew(true)} style={btnPrimary}><Plus size={14} /> New return</button>
      </div>
      {isLoading ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>Loading…</p> : rows.length === 0
        ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>No returns.</p>
        : (
          <div style={{ ...card, overflow: "hidden" }}>
            {rows.map((r, i) => (
              <button key={r.id} onClick={() => setSelected(r.id)} style={{ display: "flex", width: "100%", textAlign: "left", justifyContent: "space-between", gap: 10, alignItems: "center", padding: "12px 16px", border: "none", borderTop: i === 0 ? "none" : "1px solid var(--hf-border-subtle)", background: "none", cursor: "pointer", color: "var(--hf-text)" }}>
                <div>
                  <p style={{ fontSize: 13, fontWeight: 700, margin: 0 }}>{r.rmaNumber} <span style={{ fontWeight: 400, color: "var(--hf-text-muted)" }}>· {r.clientName}</span></p>
                  <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: 0 }}>{r.reason}{r.orderReference ? ` · ${r.orderReference}` : ""}</p>
                </div>
                <StatusChip s={r.status} />
              </button>
            ))}
          </div>
        )}
      {showNew && <NewReturnModal onClose={() => setShowNew(false)} onCreated={id => { setShowNew(false); setSelected(id) }} />}
    </div>
  )
}
