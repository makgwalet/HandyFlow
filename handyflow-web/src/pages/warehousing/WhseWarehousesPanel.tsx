// The business's warehouses: add one, rename it, make it the default, switch it off. A location belongs to one warehouse and stays there.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { btnGhost, btnPrimary, card, Chip, ErrorLine, fmtQty, inputStyle, labelStyle } from "./whseUi"
import { normaliseCode, warehouseProblem } from "./warehouses"

export interface Warehouse { id: string; code: string; name: string; address: string | null; active: boolean; isDefault: boolean; locations: number; stockOnHand: number }

export default function WhseWarehousesPanel() {
  const qc = useQueryClient()
  const [code, setCode] = useState("")
  const [name, setName] = useState("")
  const [address, setAddress] = useState("")
  const { data, isLoading } = useQuery<Warehouse[]>({ queryKey: ["whse-warehouses"], queryFn: async () => (await apiClient.get("/api/v1/warehousing/warehouses")).data })
  const list = Array.isArray(data) ? data : []
  const problem = warehouseProblem(code, name, list)
  const refresh = () => { qc.invalidateQueries({ queryKey: ["whse-warehouses"] }); qc.invalidateQueries({ queryKey: ["whse-locations"] }); qc.invalidateQueries({ queryKey: ["whse-floor-map"] }) }

  const create = useMutation({
    mutationFn: async () => apiClient.post("/api/v1/warehousing/warehouses", { code: normaliseCode(code), name: name.trim(), address: address.trim() || null }),
    onSuccess: () => { setCode(""); setName(""); setAddress(""); refresh() },
  })
  const act = useMutation({
    mutationFn: async (a: { id: string; what: "make-default" | "deactivate" | "activate" }) => apiClient.post(`/api/v1/warehousing/warehouses/${a.id}/${a.what}`),
    onSuccess: refresh,
  })

  return (
    <div style={{ maxWidth: 720, marginTop: 28 }}>
      <h3 style={{ fontSize: 15, fontWeight: 700, margin: "0 0 4px" }}>Warehouses</h3>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 12px" }}>Each location belongs to one warehouse and cannot be moved to another. Your package sets how many can be switched on.</p>

      <div style={{ ...card, marginBottom: 14 }}>
        {isLoading ? <p style={{ padding: 14, fontSize: 13, margin: 0 }}>Loading…</p> : list.length === 0 ? (
          <p style={{ padding: 14, fontSize: 13, color: "var(--hf-text-muted)", margin: 0 }}>No warehouse yet. The first one you add becomes the default.</p>
        ) : list.map((w, i) => (
          <div key={w.id} style={{ padding: 14, borderTop: i === 0 ? "none" : "1px solid var(--hf-border)", opacity: w.active ? 1 : 0.6 }}>
            <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
              <strong style={{ fontSize: 13.5 }}>{w.code}</strong><span style={{ fontSize: 13.5 }}>{w.name}</span>
              {w.isDefault && <Chip bg="var(--hf-info-soft)" fg="var(--hf-info-text)">Default</Chip>}
              {!w.active && <Chip bg="var(--hf-surface-muted)" fg="var(--hf-text-muted)">Switched off</Chip>}
            </div>
            <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "4px 0 8px" }}>{w.locations} location{w.locations === 1 ? "" : "s"} · {fmtQty(w.stockOnHand)} units on hand{w.address ? ` · ${w.address}` : ""}</p>
            <div style={{ display: "flex", gap: 8 }}>
              {!w.isDefault && w.active && <button style={btnGhost} onClick={() => act.mutate({ id: w.id, what: "make-default" })}>Make default</button>}
              {!w.isDefault && w.active && <button style={btnGhost} onClick={() => act.mutate({ id: w.id, what: "deactivate" })}>Switch off</button>}
              {!w.active && <button style={btnGhost} onClick={() => act.mutate({ id: w.id, what: "activate" })}>Switch on</button>}
            </div>
          </div>
        ))}
        <ErrorLine error={act.error} fallback="Could not change the warehouse" />
      </div>

      <div style={{ ...card, padding: 14 }}>
        <p style={{ fontSize: 13, fontWeight: 700, margin: "0 0 10px" }}>Add a warehouse</p>
        <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "flex-end" }}>
          <div><label style={labelStyle} htmlFor="wh-code">Code</label><input id="wh-code" style={{ ...inputStyle, width: 100 }} value={code} onChange={e => setCode(e.target.value)} maxLength={20} placeholder="CPT" /></div>
          <div><label style={labelStyle} htmlFor="wh-wname">Name</label><input id="wh-wname" style={{ ...inputStyle, width: 200 }} value={name} onChange={e => setName(e.target.value)} maxLength={120} /></div>
          <div style={{ flex: 1, minWidth: 200 }}><label style={labelStyle} htmlFor="wh-addr">Address</label><input id="wh-addr" style={{ ...inputStyle, width: "100%" }} value={address} onChange={e => setAddress(e.target.value)} maxLength={500} /></div>
          <button style={{ ...btnPrimary, opacity: problem ? 0.5 : 1 }} disabled={!!problem || create.isPending} onClick={() => create.mutate()}>{create.isPending ? "Adding…" : "Add warehouse"}</button>
        </div>
        {(code || name) && problem && <p style={{ fontSize: 12, color: "var(--hf-danger-text)", margin: "8px 0 0" }}>{problem}</p>}
        <ErrorLine error={create.error} fallback="Could not add the warehouse" />
      </div>
    </div>
  )
}
