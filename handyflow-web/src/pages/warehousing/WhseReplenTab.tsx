// src/pages/warehousing/WhseReplenTab.tsx
//
// Replenishment levels: for an item at a pick face, a minimum and a maximum. When what is available at the face falls below the minimum,
// replenishment tasks (Tasks > Replenish) move stock from the reserve up to the maximum.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { RefreshCw, Trash2 } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { btnGhost, btnPrimary, card, Chip, ErrorLine, fmtQty, inputStyle, labelStyle } from "./whseUi"
import { parseQty } from "./receiving"

export interface RuleRow {
  id: string; clientId: string; clientName: string; itemId: string; sku: string; itemDescription: string; locationId: string; locationCode: string
  minQty: number; maxQty: number; active: boolean; faceAvailable: number; inbound: number; reserveAvailable: number
  status: "OK" | "FILLING" | "BELOW_MIN" | "NO_RESERVE" | "OFF"
}
interface Client { id: string; tradingName: string }
interface Item { id: string; sku: string; description: string }
interface Location { id: string; code: string; active: boolean; locationType?: string }

const STATUS: Record<RuleRow["status"], { bg: string; fg: string; label: string }> = {
  OK: { bg: "var(--hf-success-soft)", fg: "var(--hf-success-text-strong)", label: "OK" },
  FILLING: { bg: "var(--hf-info-soft)", fg: "var(--hf-info-text)", label: "Being refilled" },
  BELOW_MIN: { bg: "var(--hf-warning-soft-strong)", fg: "var(--hf-warning-text-deep)", label: "Below minimum" },
  NO_RESERVE: { bg: "var(--hf-danger-soft)", fg: "var(--hf-danger-text)", label: "No reserve stock" },
  OFF: { bg: "var(--hf-surface-muted)", fg: "var(--hf-text-muted)", label: "Off" },
}

/** The levels typed in the form. Null when fine, else what is wrong. Whole-number levels are not forced: items can be sold by weight or length. */
export function levelsProblem(min: string, max: string): string | null {
  const lo = parseQty(min), hi = parseQty(max)
  if (lo == null || hi == null || min.trim() === "" || max.trim() === "") return "Enter a minimum and a maximum."
  if (lo < 0) return "The minimum cannot be negative."
  if (hi <= lo) return "The maximum must be more than the minimum."
  return null
}

function AddRule({ onDone }: { onDone: () => void }) {
  const [clientId, setClientId] = useState("")
  const [itemId, setItemId] = useState("")
  const [locationId, setLocationId] = useState("")
  const [min, setMin] = useState("")
  const [max, setMax] = useState("")
  const { data: clients = [] } = useQuery<Client[]>({ queryKey: ["whse-clients"], queryFn: async () => (await apiClient.get("/api/v1/warehousing/clients")).data })
  const { data: items = [] } = useQuery<Item[]>({ queryKey: ["whse-items", clientId], enabled: !!clientId, queryFn: async () => (await apiClient.get(`/api/v1/warehousing/clients/${clientId}/items`)).data })
  const { data: locations = [] } = useQuery<Location[]>({ queryKey: ["whse-locations"], queryFn: async () => (await apiClient.get("/api/v1/warehousing/locations")).data })
  const faces = locations.filter(l => l.active && l.locationType !== "DOCK" && l.locationType !== "STAGING")
  const problem = !clientId || !itemId || !locationId ? "Choose a client, an item and a pick face." : levelsProblem(min, max)
  const save = useMutation({
    mutationFn: async () => apiClient.put("/api/v1/warehousing/replenishment/rules", { clientId, itemId, locationId, minQty: parseQty(min), maxQty: parseQty(max), active: true }),
    onSuccess: () => { setItemId(""); setMin(""); setMax(""); onDone() },
  })
  return (
    <div style={{ ...card, padding: 14, marginBottom: 16 }}>
      <p style={{ fontSize: 13, fontWeight: 700, margin: "0 0 10px" }}>Set levels for a pick face</p>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "flex-end" }}>
        <div><label style={labelStyle} htmlFor="rp-client">Client</label>
          <select id="rp-client" style={inputStyle} value={clientId} onChange={e => { setClientId(e.target.value); setItemId("") }}>
            <option value="">Choose…</option>{clients.map(c => <option key={c.id} value={c.id}>{c.tradingName}</option>)}</select></div>
        <div><label style={labelStyle} htmlFor="rp-item">Item</label>
          <select id="rp-item" style={inputStyle} value={itemId} disabled={!clientId} onChange={e => setItemId(e.target.value)}>
            <option value="">Choose…</option>{items.map(i => <option key={i.id} value={i.id}>{i.sku} · {i.description}</option>)}</select></div>
        <div><label style={labelStyle} htmlFor="rp-loc">Pick face</label>
          <select id="rp-loc" style={inputStyle} value={locationId} onChange={e => setLocationId(e.target.value)}>
            <option value="">Choose…</option>{faces.map(l => <option key={l.id} value={l.id}>{l.code}</option>)}</select></div>
        <div><label style={labelStyle} htmlFor="rp-min">Minimum</label><input id="rp-min" inputMode="decimal" style={{ ...inputStyle, width: 90 }} value={min} onChange={e => setMin(e.target.value)} /></div>
        <div><label style={labelStyle} htmlFor="rp-max">Maximum</label><input id="rp-max" inputMode="decimal" style={{ ...inputStyle, width: 90 }} value={max} onChange={e => setMax(e.target.value)} /></div>
        <button style={btnPrimary} disabled={problem != null || save.isPending} title={problem ?? undefined} onClick={() => save.mutate()}>Save levels</button>
      </div>
      <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: "8px 0 0" }}>Saving again for the same item and face changes its levels. These are pick-face levels, separate from the reorder levels on the item.</p>
      <ErrorLine error={save.error} fallback="Could not save the levels" />
    </div>
  )
}

export default function WhseReplenTab() {
  const qc = useQueryClient()
  const canWork = usePermission("WAREHOUSING_MANAGE") || usePermission("WAREHOUSING_ADMIN")
  const { data: rules = [], isLoading } = useQuery<RuleRow[]>({ queryKey: ["whse-replen-rules"], queryFn: async () => (await apiClient.get("/api/v1/warehousing/replenishment/rules")).data })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["whse-replen-rules"] }); qc.invalidateQueries({ queryKey: ["whse-tasks"] }) }
  const sync = useMutation({ mutationFn: async () => (await apiClient.post("/api/v1/warehousing/replenishment/sync")).data as { created: number }, onSuccess: refresh })
  const toggle = useMutation({
    mutationFn: async (r: RuleRow) => apiClient.put("/api/v1/warehousing/replenishment/rules", { clientId: r.clientId, itemId: r.itemId, locationId: r.locationId, minQty: r.minQty, maxQty: r.maxQty, active: !r.active }),
    onSuccess: refresh,
  })
  const del = useMutation({ mutationFn: async (id: string) => apiClient.delete(`/api/v1/warehousing/replenishment/rules/${id}`), onSuccess: refresh })
  const below = rules.filter(r => r.status === "BELOW_MIN" || r.status === "NO_RESERVE").length

  return (
    <div>
      {canWork && <AddRule onDone={refresh} />}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12, flexWrap: "wrap", gap: 8 }}>
        <p style={{ fontSize: 13, color: "var(--hf-text-faint)", margin: 0 }}>{rules.length} rule{rules.length === 1 ? "" : "s"}{below > 0 ? ` · ${below} below minimum` : ""}</p>
        {canWork && <button style={btnGhost} disabled={sync.isPending} onClick={() => sync.mutate()}><RefreshCw size={13} /> {sync.isPending ? "Checking…" : "Create replenishment tasks now"}</button>}
      </div>
      {sync.data && <p style={{ fontSize: 12.5, margin: "0 0 10px", color: "var(--hf-text-muted)" }}>{sync.data.created === 0 ? "Nothing needed replenishing." : `${sync.data.created} replenishment task${sync.data.created === 1 ? "" : "s"} created. Find them under Tasks > Replenish.`}</p>}
      <ErrorLine error={sync.error ?? toggle.error ?? del.error} fallback="That could not be done" />
      {isLoading ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>Loading…</p> : rules.length === 0
        ? <p style={{ color: "var(--hf-text-faint)", fontSize: 13 }}>No levels set yet. Give a pick face a minimum and a maximum and it will be refilled from the reserve.</p>
        : (
          <div style={{ ...card, overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12.5 }}>
              <thead><tr style={{ textAlign: "left", color: "var(--hf-text-faint)" }}>
                {["Pick face", "Item", "Client", "Available", "Min", "Max", "On its way", "Reserve", "Status", ""].map(h => <th key={h} style={{ padding: "8px 12px", fontWeight: 600 }}>{h}</th>)}
              </tr></thead>
              <tbody>
                {rules.map(r => {
                  const st = STATUS[r.status]
                  return (
                    <tr key={r.id} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                      <td style={{ padding: "8px 12px", fontWeight: 600 }}>{r.locationCode}</td>
                      <td style={{ padding: "8px 12px" }}>{r.sku}</td>
                      <td style={{ padding: "8px 12px", color: "var(--hf-text-muted)" }}>{r.clientName}</td>
                      <td style={{ padding: "8px 12px" }}>{fmtQty(r.faceAvailable)}</td>
                      <td style={{ padding: "8px 12px" }}>{fmtQty(r.minQty)}</td>
                      <td style={{ padding: "8px 12px" }}>{fmtQty(r.maxQty)}</td>
                      <td style={{ padding: "8px 12px" }}>{r.inbound > 0 ? fmtQty(r.inbound) : "–"}</td>
                      <td style={{ padding: "8px 12px" }}>{fmtQty(r.reserveAvailable)}</td>
                      <td style={{ padding: "8px 12px" }}><Chip bg={st.bg} fg={st.fg}>{st.label}</Chip></td>
                      <td style={{ padding: "8px 12px", whiteSpace: "nowrap" }}>
                        {canWork && <>
                          <button style={{ ...btnGhost, padding: "4px 10px" }} onClick={() => toggle.mutate(r)} disabled={toggle.isPending}>{r.active ? "Switch off" : "Switch on"}</button>
                          <button aria-label={`Delete rule for ${r.sku} at ${r.locationCode}`} style={{ background: "none", border: "none", cursor: "pointer", marginLeft: 6 }} onClick={() => del.mutate(r.id)}><Trash2 size={13} /></button>
                        </>}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
    </div>
  )
}
