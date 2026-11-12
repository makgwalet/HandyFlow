// Phase 7a: fuel brought back on a truck after a failed delivery. A person confirms which tank it went into. This books no stock movement.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { Modal, btnPrimary, btnGhost, card, inputStyle, labelStyle } from "../warehousing/whseUi"
import { fmtL } from "./orderOps"
import { validateReturn, loadLabel, type OutstandingReturn } from "./returnedFuelOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const msg = (e: any, fb: string) => e?.response?.data?.message ?? fb
const th: React.CSSProperties = { textAlign: "left", padding: "10px 12px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)", whiteSpace: "nowrap" }
const td: React.CSSProperties = { padding: "10px 12px", fontSize: 13, color: "var(--hf-text)" }

export default function ReturnedFuelTab() {
  const canManage = usePermissions(["FUEL_MANAGE", "FUEL_TRANSPORT_MANAGE"])
  const qc = useQueryClient()
  const [pick, setPick] = useState<OutstandingReturn | null>(null)
  const q = useQuery<OutstandingReturn[]>({ queryKey: ["fuel-load-returns"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/load-returns/outstanding")) })
  const rows = q.data ?? []
  return (
    <div>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 12px" }}>
        Fuel that came back on the truck after a failed delivery. Confirm which tank it was put into so it is not lost track of. This does not change any tank's stock: a tank is only reduced when a delivery completes, so this fuel is already counted in the tank's balance.
      </p>
      {q.isLoading ? <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p> : q.isError ? <p role="alert" style={{ color: "var(--hf-danger-text)" }}>Could not load returned fuel.</p> : rows.length === 0 ? (
        <p style={{ color: "var(--hf-text-muted)" }}>Nothing waiting. Every litre brought back has been confirmed into a tank.</p>
      ) : (
        <div style={{ ...card, overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Load</th><th style={th}>Brought back</th><th style={th}>Confirmed</th><th style={th}>Still to confirm</th><th style={th}>Last attempt</th><th style={th} /></tr></thead>
            <tbody>{rows.map(r => (
              <tr key={r.deliveryId} style={{ borderTop: "1px solid var(--hf-border)" }}>
                <td style={td}>{loadLabel(r)}</td><td style={td}>{fmtL(r.declaredReturned)}</td><td style={td}>{fmtL(r.confirmedBack)}</td><td style={{ ...td, fontWeight: 700 }}>{fmtL(r.outstanding)}</td>
                <td style={td}>{new Date(r.lastAttemptAt).toLocaleString("en-ZA")}</td>
                <td style={td}>{canManage && <button style={btnGhost} onClick={() => setPick(r)}>Confirm into a tank</button>}</td>
              </tr>))}</tbody>
          </table>
        </div>
      )}
      {pick && <ConfirmDialog row={pick} onDone={() => qc.invalidateQueries({ queryKey: ["fuel-load-returns"] })} onClose={() => setPick(null)} />}
    </div>
  )
}

function ConfirmDialog({ row, onDone, onClose }: { row: OutstandingReturn; onDone: () => void; onClose: () => void }) {
  const [tankId, setTankId] = useState(""); const [litres, setLitres] = useState(String(row.outstanding)); const [note, setNote] = useState(""); const [err, setErr] = useState("")
  const [rid] = useState(() => (globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`))
  const tanks = useQuery<any[]>({ queryKey: ["fuel-return-tanks"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/tanks")) })
  const save = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/fuel/deliveries/${row.deliveryId}/returns`, { tankId, litres: Number(litres), note: note.trim() || null, clientRequestId: rid }),
    onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not record the return")),
  })
  return (
    <Modal title="Confirm fuel into a tank" onClose={onClose}>
      <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: "0 0 10px" }}>{loadLabel(row)}. {fmtL(row.outstanding)} still to confirm.</p>
      <label style={labelStyle} htmlFor="rf-tank">Tank *</label>
      <select id="rf-tank" value={tankId} onChange={e => setTankId(e.target.value)} style={inputStyle}><option value="">Choose…</option>{(Array.isArray(tanks.data) ? tanks.data : []).map(t => <option key={t.id} value={t.id}>{t.name}</option>)}</select>
      <label style={{ ...labelStyle, marginTop: 10 }} htmlFor="rf-litres">Litres put into the tank *</label>
      <input id="rf-litres" inputMode="decimal" value={litres} onChange={e => setLitres(e.target.value)} style={inputStyle} />
      <label style={{ ...labelStyle, marginTop: 10 }} htmlFor="rf-note">Note</label>
      <input id="rf-note" value={note} maxLength={500} onChange={e => setNote(e.target.value)} style={inputStyle} />
      {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: "8px 0 0" }}>{err}</p>}
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
        <button style={btnGhost} onClick={onClose}>Cancel</button>
        <button style={btnPrimary} disabled={save.isPending} onClick={() => { const m = validateReturn(tankId, litres, row.outstanding); if (m) return setErr(m); setErr(""); save.mutate() }}>Confirm</button>
      </div>
    </Modal>
  )
}
