// Stage 4A: standard and per-customer contract prices, with volume breaks and dates. Orders default their price from here.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { Modal, btnPrimary, btnGhost, card, inputStyle, labelStyle, Chip } from "../warehousing/whseUi"
import type { CustomerAccount } from "./customerOps"
import { validatePrice, scopeLabel, breakLabel, priceStatusLabel, type PriceRow } from "./priceOps"
import { fmtR } from "./orderOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const msg = (e: any, fb: string) => e?.response?.data?.message ?? fb
const th: React.CSSProperties = { textAlign: "left", padding: "10px 12px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)", whiteSpace: "nowrap" }
const td: React.CSSProperties = { padding: "10px 12px", fontSize: 13, color: "var(--hf-text)" }
const FUELS = ["DIESEL", "DIESEL_50PPM", "DIESEL_500PPM", "PETROL_95", "PETROL_93", "PARAFFIN"]
const today = () => new Date().toISOString().slice(0, 10)

export default function PricingTab() {
  const canManage = usePermissions(["FUEL_MANAGE", "FUEL_PRICING_MANAGE"])
  const qc = useQueryClient()
  const [adding, setAdding] = useState(false)
  const [err, setErr] = useState("")
  const list = useQuery<PriceRow[]>({ queryKey: ["fuel-prices"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/prices")) })
  const end = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/fuel/prices/${id}/end`),
    onSuccess: () => { setErr(""); qc.invalidateQueries({ queryKey: ["fuel-prices"] }) }, onError: (e: any) => setErr(msg(e, "Could not end the price")),
  })
  const rows = list.data ?? []
  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 14 }}>
        <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0, flex: 1 }}>
          A customer's contract price beats the standard price. Within a list the highest volume break reached wins. A quotation with no typed price uses this list.
        </p>
        {canManage && <button style={btnPrimary} onClick={() => setAdding(true)}>Add price</button>}
      </div>
      {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{err}</p>}
      {list.isLoading ? <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p> : rows.length === 0 ? <p style={{ color: "var(--hf-text-muted)" }}>No prices yet. Add a standard price for each fuel you sell.</p> : (
        <div style={{ ...card, overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Fuel</th><th style={th}>For</th><th style={th}>Volume</th><th style={th}>Price / L</th><th style={th}>From</th><th style={th}>To</th><th style={th}>Status</th><th style={th}></th></tr></thead>
            <tbody>
              {rows.map(r => (
                <tr key={r.id} style={{ borderTop: "1px solid var(--hf-border)" }}>
                  <td style={td}>{r.fuelType}</td><td style={td}>{scopeLabel(r)}</td><td style={td}>{breakLabel(r.minLitres)}</td>
                  <td style={td}>{fmtR(r.pricePerLitre)}</td><td style={td}>{r.validFrom}</td><td style={td}>{r.validTo ?? "open"}</td>
                  <td style={td} title={r.approvalReason ?? undefined}><Chip bg={r.live ? "var(--hf-success-soft-strong)" : "var(--hf-surface-alt)"} fg={r.live ? "var(--hf-success-text-strong)" : "var(--hf-text)"}>{priceStatusLabel(r)}</Chip>{r.changePct != null && <div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>{r.changePct > 0 ? "+" : ""}{r.changePct}% vs {fmtR(r.previousPricePerLitre ?? 0)}</div>}</td>
                  <td style={td}>{canManage && r.approvalStatus !== "PENDING_APPROVAL" && r.approvalStatus !== "REJECTED" && (r.validTo == null || r.validTo >= today()) && <button style={btnGhost} onClick={() => end.mutate(r.id)}>End today</button>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {adding && <PriceDialog onDone={() => qc.invalidateQueries({ queryKey: ["fuel-prices"] })} onClose={() => setAdding(false)} />}
    </div>
  )
}

function PriceDialog({ onDone, onClose }: { onDone: () => void; onClose: () => void }) {
  const [f, setF] = useState({ fuelType: "DIESEL", accountId: "", price: "", minLitres: "", validFrom: today(), validTo: "", note: "" })
  const [err, setErr] = useState("")
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setF(s => ({ ...s, [k]: e.target.value }))
  const accounts = useQuery<CustomerAccount[]>({ queryKey: ["fuel-customer-accounts"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/customer-accounts")) })
  const save = useMutation({
    mutationFn: () => apiClient.post("/api/v1/fuel/prices", {
      fuelType: f.fuelType, accountId: f.accountId || null, pricePerLitre: Number(f.price), minLitres: f.minLitres.trim() === "" ? null : Number(f.minLitres),
      validFrom: f.validFrom, validTo: f.validTo || null, note: f.note || null,
    }),
    onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not save the price")),
  })
  return (
    <Modal title="Add price" onClose={onClose}>
      <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
        <div><label style={labelStyle}>For</label>
          <select aria-label="Price for" value={f.accountId} onChange={set("accountId")} style={inputStyle}>
            <option value="">Standard list (everyone)</option>{(accounts.data ?? []).map(a => <option key={a.id} value={a.id}>Contract: {a.customerName}</option>)}
          </select></div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 10 }}>
          <div><label style={labelStyle}>Fuel *</label><select aria-label="Fuel type" value={f.fuelType} onChange={set("fuelType")} style={inputStyle}>{FUELS.map(x => <option key={x} value={x}>{x}</option>)}</select></div>
          <div><label style={labelStyle}>Price / litre (R) *</label><input aria-label="Price per litre" type="number" value={f.price} onChange={set("price")} style={inputStyle} /></div>
          <div><label style={labelStyle}>From (litres)</label><input aria-label="Volume break" type="number" placeholder="any" value={f.minLitres} onChange={set("minLitres")} style={inputStyle} /></div>
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
          <div><label style={labelStyle}>Starts *</label><input aria-label="Starts" type="date" value={f.validFrom} onChange={set("validFrom")} style={inputStyle} /></div>
          <div><label style={labelStyle}>Ends (optional)</label><input aria-label="Ends" type="date" value={f.validTo} onChange={set("validTo")} style={inputStyle} /></div>
        </div>
        <div><label style={labelStyle}>Note</label><input value={f.note} onChange={set("note")} style={inputStyle} /></div>
        {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: 0 }}>{err}</p>}
        <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 4 }}>
          <button style={btnGhost} onClick={onClose}>Cancel</button>
          <button style={btnPrimary} disabled={save.isPending} onClick={() => { const v = validatePrice(f.fuelType, f.price, f.minLitres, f.validFrom, f.validTo); if (v) return setErr(v); setErr(""); save.mutate() }}>Save price</button>
        </div>
      </div>
    </Modal>
  )
}
