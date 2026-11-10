// src/pages/warehousing/WhseDispatchTab.tsx
//
// Dispatch: loading, manifest, proof of delivery.
//  - A manifest puts packed orders on one vehicle and driver: Open -> Loading -> Dispatched. Dispatching ships every order on it
//    in one step (all or none). The manifest prints as a PDF with sign-off lines for the dispatcher and the driver.
//  - After shipping, each order gets a proof-of-delivery record (who received it, when) and can carry a signed POD file.
//    DeliveryPanel is also used on an order's own page for orders that were shipped without a manifest.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { ArrowLeft, Download, Plus, Truck, X, Check, Upload } from "lucide-react"
import { apiClient } from "../../api/client"
import { Chip, ErrorLine, Modal, ReasonModal, btnDanger, btnGhost, btnPrimary, card, inputStyle, labelStyle } from "./whseUi"

interface ManifestRow {
  id: string; manifestNumber: string; carrier: string | null; vehicleRegistration: string | null; driverName: string | null; notes: string | null
  status: "OPEN" | "LOADING" | "DISPATCHED" | "CANCELLED"; createdAt: string; loadingStartedAt: string | null; dispatchedAt: string | null
  cancelReason: string | null; orderCount: number
}
interface LineRow { sku: string; description: string | null; qty: number }
interface OrderRow {
  orderId: string; orderReference: string | null; clientName: string; shipToName: string | null; status: string
  deliveredAt: string | null; podReceivedBy: string | null; podNote: string | null; lines: LineRow[]
}
interface ManifestDetail { manifest: ManifestRow; orders: OrderRow[] }
interface ReadyOrder { orderId: string; orderReference: string | null; clientName: string; shipToName: string | null }

const STATUS_TEXT: Record<string, { label: string; bg: string; fg: string }> = {
  OPEN: { label: "OPEN", bg: "var(--hf-info-soft-strong)", fg: "var(--hf-info-text)" },
  LOADING: { label: "LOADING", bg: "var(--hf-warning-soft)", fg: "var(--hf-warning-text)" },
  DISPATCHED: { label: "DISPATCHED", bg: "var(--hf-success-soft-strong)", fg: "var(--hf-success-text-strong)" },
  CANCELLED: { label: "CANCELLED", bg: "var(--hf-surface-sunken)", fg: "var(--hf-text-muted)" },
}
const when = (iso: string | null) => (iso ? new Date(iso).toLocaleString("en-ZA") : "")
const units = (o: OrderRow) => o.lines.reduce((s, l) => s + l.qty, 0)
/** The current local date and time in the form a datetime-local input uses. */
const nowLocal = () => { const d = new Date(); d.setMinutes(d.getMinutes() - d.getTimezoneOffset()); return d.toISOString().slice(0, 16) }

async function download(url: string, filename: string) {
  const res = await apiClient.get(url, { responseType: "blob" })
  const href = window.URL.createObjectURL(new Blob([res.data]))
  const a = document.createElement("a")
  a.href = href; a.download = filename
  document.body.appendChild(a); a.click(); a.remove()
  window.URL.revokeObjectURL(href)
}

// ── proof of delivery ───────────────────────────────────────────────────────

export function DeliveryPanel({ orderId, delivered, receivedBy, deliveredAt, note, onDone }: {
  orderId: string; delivered: boolean; receivedBy?: string | null; deliveredAt?: string | null; note?: string | null; onDone?: () => void
}) {
  const qc = useQueryClient()
  const [open, setOpen] = useState(false)
  const [who, setWho] = useState("")
  const [at, setAt] = useState(nowLocal())
  const [text, setText] = useState("")
  const finished = () => { qc.invalidateQueries({ queryKey: ["whse-manifest"] }); qc.invalidateQueries({ queryKey: ["whse-order-delivery", orderId] }); onDone?.() }
  const record = useMutation({
    mutationFn: async () => apiClient.post(`/api/v1/warehousing/outbound-orders/${orderId}/delivery`,
      { receivedBy: who.trim(), deliveredAt: new Date(at).toISOString(), note: text.trim() || null }),
    onSuccess: () => { setOpen(false); finished() },
  })
  const upload = useMutation({
    mutationFn: async (file: File) => {
      const form = new FormData()
      form.append("file", file)
      return apiClient.post(`/api/v1/warehousing/outbound-orders/${orderId}/evidence?evidenceType=SIGNED_POD`, form, { headers: { "Content-Type": "multipart/form-data" } })
    },
  })

  return (
    <div>
      {delivered ? (
        <p style={{ fontSize: 12.5, color: "var(--hf-success-text-strong)", margin: 0 }}>
          <Check size={12} style={{ verticalAlign: "-2px" }} /> Delivered {when(deliveredAt ?? null)}, received by {receivedBy}{note ? ` · ${note}` : ""}
        </p>
      ) : (
        <button onClick={() => setOpen(true)} style={btnGhost}>Record delivery</button>
      )}
      <label style={{ ...btnGhost, marginLeft: delivered ? 0 : 8, marginTop: delivered ? 8 : 0, cursor: "pointer" }}>
        <Upload size={13} /> Attach signed POD
        <input type="file" accept="application/pdf,image/*" aria-label="Signed proof of delivery file" style={{ display: "none" }}
          onChange={e => { const f = e.target.files?.[0]; if (f) upload.mutate(f); e.target.value = "" }} />
      </label>
      {upload.isSuccess && <span style={{ fontSize: 12, color: "var(--hf-success-text-strong)", marginLeft: 8 }}>Signed POD attached</span>}
      <ErrorLine error={upload.error} fallback="Could not attach the file" />
      {open && (
        <Modal title="Record delivery" onClose={() => setOpen(false)}>
          <div style={{ display: "grid", gap: 12 }}>
            <div><label style={labelStyle} htmlFor="pod-who">Received by *</label>
              <input id="pod-who" style={{ ...inputStyle, width: "100%" }} value={who} onChange={e => setWho(e.target.value)} placeholder="Name of the person who signed" maxLength={120} /></div>
            <div><label style={labelStyle} htmlFor="pod-at">Delivered *</label>
              <input id="pod-at" type="datetime-local" style={{ ...inputStyle, width: "100%" }} value={at} max={nowLocal()} onChange={e => setAt(e.target.value)} /></div>
            <div><label style={labelStyle} htmlFor="pod-note">Note</label>
              <input id="pod-note" style={{ ...inputStyle, width: "100%" }} value={text} onChange={e => setText(e.target.value)} placeholder="e.g. left with security, 1 carton damaged" maxLength={300} /></div>
            <ErrorLine error={record.error} fallback="Could not record the delivery" />
            <div style={{ display: "flex", gap: 8 }}>
              <button onClick={() => setOpen(false)} style={{ ...btnGhost, flex: 1, justifyContent: "center" }}>Back</button>
              <button onClick={() => record.mutate()} disabled={who.trim().length < 2 || !at || record.isPending}
                style={{ ...btnPrimary, flex: 1, justifyContent: "center", opacity: who.trim().length < 2 ? 0.5 : 1 }}>{record.isPending ? "Saving…" : "Save delivery"}</button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  )
}

// ── one manifest ────────────────────────────────────────────────────────────

function ManifestView({ id, onBack }: { id: string; onBack: () => void }) {
  const qc = useQueryClient()
  const [adding, setAdding] = useState("")
  const [cancelling, setCancelling] = useState(false)
  const { data } = useQuery<ManifestDetail>({ queryKey: ["whse-manifest", id], queryFn: async () => (await apiClient.get(`/api/v1/warehousing/dispatch/manifests/${id}`)).data })
  const m = data?.manifest
  const orders = data?.orders ?? []
  const editable = m?.status === "OPEN" || m?.status === "LOADING"
  const { data: ready = [] } = useQuery<ReadyOrder[]>({
    queryKey: ["whse-ready-orders"], enabled: editable,
    queryFn: async () => (await apiClient.get("/api/v1/warehousing/dispatch/ready-orders")).data,
  })
  const refresh = () => { for (const k of ["whse-manifest", "whse-manifests", "whse-ready-orders"]) qc.invalidateQueries({ queryKey: [k] }) }
  const base = `/api/v1/warehousing/dispatch/manifests/${id}`
  const add = useMutation({ mutationFn: async (orderId: string) => apiClient.post(`${base}/orders`, { orderId }), onSuccess: () => { setAdding(""); refresh() } })
  const remove = useMutation({ mutationFn: async (orderId: string) => apiClient.delete(`${base}/orders/${orderId}`), onSuccess: refresh })
  const load = useMutation({ mutationFn: async () => apiClient.post(`${base}/start-loading`, {}), onSuccess: refresh })
  const dispatch = useMutation({ mutationFn: async () => apiClient.post(`${base}/dispatch`, {}), onSuccess: refresh })
  const cancel = useMutation({ mutationFn: async (reason: string) => apiClient.post(`${base}/cancel`, { reason }), onSuccess: () => { setCancelling(false); refresh() } })

  if (!m) return <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>Loading…</p>
  const st = STATUS_TEXT[m.status]
  const missing = !m.vehicleRegistration || !m.driverName

  return (
    <div>
      <button onClick={onBack} style={{ display: "flex", alignItems: "center", gap: 6, background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-muted)", fontSize: 13, marginBottom: 14, padding: 0 }}>
        <ArrowLeft size={15} /> All manifests
      </button>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", flexWrap: "wrap", gap: 10, marginBottom: 14 }}>
        <div>
          <h3 style={{ fontSize: 16, fontWeight: 800, color: "var(--hf-text)", margin: "0 0 4px" }}>{m.manifestNumber} <Chip bg={st.bg} fg={st.fg}>{st.label}</Chip></h3>
          <p style={{ fontSize: 12.5, color: "var(--hf-text-faint)", margin: 0 }}>
            {m.carrier ?? "No carrier"} · Vehicle {m.vehicleRegistration ?? "not set"} · Driver {m.driverName ?? "not set"}
          </p>
          {m.loadingStartedAt && <p style={{ fontSize: 12, color: "var(--hf-text-faint)", margin: 0 }}>Loading started {when(m.loadingStartedAt)}{m.dispatchedAt ? ` · dispatched ${when(m.dispatchedAt)}` : ""}</p>}
          {m.cancelReason && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: 0 }}>Cancelled: {m.cancelReason}</p>}
        </div>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          <button onClick={() => download(`${base}/pdf`, `manifest-${m.manifestNumber}.pdf`)} style={btnGhost}><Download size={13} /> Manifest PDF</button>
          {m.status === "OPEN" && <button onClick={() => load.mutate()} disabled={load.isPending} style={btnPrimary}><Truck size={13} /> Start loading</button>}
          {m.status === "LOADING" && (
            <button onClick={() => { if (confirm(`Dispatch ${orders.length} order(s)? Their stock leaves the warehouse.`)) dispatch.mutate() }} disabled={dispatch.isPending} style={btnPrimary}>
              <Truck size={13} /> {dispatch.isPending ? "Dispatching…" : "Dispatch"}
            </button>
          )}
          {editable && <button onClick={() => setCancelling(true)} style={btnDanger}><X size={13} /> Cancel manifest</button>}
        </div>
      </div>
      {m.status === "OPEN" && missing && <p style={{ fontSize: 12.5, color: "var(--hf-warning-text-deep)", margin: "0 0 10px" }}>Vehicle registration and driver are needed before loading can start.</p>}
      <ErrorLine error={load.error ?? dispatch.error ?? add.error ?? remove.error} fallback="That step could not be done" />

      {editable && (
        <div style={{ display: "flex", gap: 8, margin: "10px 0 16px", alignItems: "flex-end", flexWrap: "wrap" }}>
          <div><label style={labelStyle} htmlFor="mf-add">Add a packed order</label>
            <select id="mf-add" style={{ ...inputStyle, minWidth: 260 }} value={adding} onChange={e => setAdding(e.target.value)}>
              <option value="">{ready.length === 0 ? "No packed orders waiting" : "Choose an order…"}</option>
              {ready.map(r => <option key={r.orderId} value={r.orderId}>{r.orderReference ?? "Order"} · {r.clientName}{r.shipToName ? ` → ${r.shipToName}` : ""}</option>)}
            </select></div>
          <button onClick={() => add.mutate(adding)} disabled={!adding || add.isPending} style={{ ...btnPrimary, opacity: adding ? 1 : 0.5 }}><Plus size={13} /> Add</button>
        </div>
      )}

      {orders.length === 0 ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>No orders on this manifest yet.</p> : (
        <div style={card}>
          {orders.map((o, i) => (
            <div key={o.orderId} style={{ padding: "12px 16px", borderTop: i === 0 ? "none" : "1px solid var(--hf-border-subtle)" }}>
              <div style={{ display: "flex", justifyContent: "space-between", gap: 10 }}>
                <div>
                  <p style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", margin: 0 }}>{o.orderReference ?? "Order"} <span style={{ fontWeight: 500, color: "var(--hf-text-muted)" }}>· {o.clientName}{o.shipToName ? ` → ${o.shipToName}` : ""}</span></p>
                  <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: 0 }}>{o.lines.length} line(s) · {units(o)} unit(s)</p>
                </div>
                {editable && <button onClick={() => remove.mutate(o.orderId)} title="Take off the manifest" style={btnGhost}>Take off</button>}
              </div>
              {m.status === "DISPATCHED" && (
                <div style={{ marginTop: 10 }}>
                  <DeliveryPanel orderId={o.orderId} delivered={!!o.deliveredAt} receivedBy={o.podReceivedBy} deliveredAt={o.deliveredAt} note={o.podNote} />
                </div>
              )}
            </div>
          ))}
        </div>
      )}
      {cancelling && <ReasonModal title="Cancel this manifest" intro="The orders go back to packed and can be put on another manifest." confirmLabel="Cancel manifest"
        pending={cancel.isPending} error={cancel.error} onConfirm={r => cancel.mutate(r)} onClose={() => setCancelling(false)} />}
    </div>
  )
}

// ── list ────────────────────────────────────────────────────────────────────

export default function WhseDispatchTab() {
  const qc = useQueryClient()
  const [selected, setSelected] = useState<string | null>(null)
  const [creating, setCreating] = useState(false)
  const [form, setForm] = useState({ carrier: "", vehicleRegistration: "", driverName: "", notes: "" })
  const { data, isLoading } = useQuery<ManifestRow[]>({ queryKey: ["whse-manifests"], queryFn: async () => (await apiClient.get("/api/v1/warehousing/dispatch/manifests")).data })
  const manifests = Array.isArray(data) ? data : []
  const create = useMutation({
    mutationFn: async () => (await apiClient.post("/api/v1/warehousing/dispatch/manifests", {
      carrier: form.carrier.trim() || null, vehicleRegistration: form.vehicleRegistration.trim() || null,
      driverName: form.driverName.trim() || null, notes: form.notes.trim() || null })).data as { id: string },
    onSuccess: (r) => { qc.invalidateQueries({ queryKey: ["whse-manifests"] }); setCreating(false); setForm({ carrier: "", vehicleRegistration: "", driverName: "", notes: "" }); if (r?.id) setSelected(r.id) },
  })

  if (selected) return <ManifestView id={selected} onBack={() => setSelected(null)} />

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <p style={{ fontSize: 13, color: "var(--hf-text-faint)", margin: 0 }}>Put packed orders on a vehicle, load it, and dispatch. Orders on a manifest are shipped by dispatching the manifest.</p>
        <button onClick={() => setCreating(true)} style={btnPrimary}><Plus size={14} /> New manifest</button>
      </div>
      {isLoading ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>Loading…</p>
        : manifests.length === 0 ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>No manifests yet.</p> : (
        <div style={card}>
          {manifests.map((m, i) => {
            const st = STATUS_TEXT[m.status]
            return (
              <button key={m.id} onClick={() => setSelected(m.id)} style={{ display: "flex", width: "100%", textAlign: "left", justifyContent: "space-between", alignItems: "center", padding: "12px 16px", background: "none", border: "none", borderTop: i === 0 ? "none" : "1px solid var(--hf-border-subtle)", cursor: "pointer" }}>
                <div>
                  <p style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", margin: 0 }}>{m.manifestNumber} <Chip bg={st.bg} fg={st.fg}>{st.label}</Chip></p>
                  <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: 0 }}>{m.carrier ?? "No carrier"} · {m.vehicleRegistration ?? "no vehicle"} · {m.driverName ?? "no driver"}</p>
                </div>
                <span style={{ fontSize: 12.5, color: "var(--hf-text-muted)" }}>{m.orderCount} order{m.orderCount === 1 ? "" : "s"}</span>
              </button>
            )
          })}
        </div>
      )}
      {creating && (
        <Modal title="New manifest" onClose={() => setCreating(false)}>
          <div style={{ display: "grid", gap: 12 }}>
            <div><label style={labelStyle} htmlFor="mf-carrier">Carrier</label><input id="mf-carrier" style={{ ...inputStyle, width: "100%" }} value={form.carrier} onChange={e => setForm({ ...form, carrier: e.target.value })} maxLength={120} /></div>
            <div><label style={labelStyle} htmlFor="mf-vehicle">Vehicle registration</label><input id="mf-vehicle" style={{ ...inputStyle, width: "100%" }} value={form.vehicleRegistration} onChange={e => setForm({ ...form, vehicleRegistration: e.target.value })} maxLength={30} /></div>
            <div><label style={labelStyle} htmlFor="mf-driver">Driver</label><input id="mf-driver" style={{ ...inputStyle, width: "100%" }} value={form.driverName} onChange={e => setForm({ ...form, driverName: e.target.value })} maxLength={120} /></div>
            <div><label style={labelStyle} htmlFor="mf-notes">Notes</label><input id="mf-notes" style={{ ...inputStyle, width: "100%" }} value={form.notes} onChange={e => setForm({ ...form, notes: e.target.value })} maxLength={300} /></div>
            <p style={{ fontSize: 12, color: "var(--hf-text-faint)", margin: 0 }}>Vehicle and driver can be added later but are needed before loading starts.</p>
            <ErrorLine error={create.error} fallback="Could not create the manifest" />
            <div style={{ display: "flex", gap: 8 }}>
              <button onClick={() => setCreating(false)} style={{ ...btnGhost, flex: 1, justifyContent: "center" }}>Back</button>
              <button onClick={() => create.mutate()} disabled={create.isPending} style={{ ...btnPrimary, flex: 1, justifyContent: "center" }}>{create.isPending ? "Creating…" : "Create manifest"}</button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  )
}
