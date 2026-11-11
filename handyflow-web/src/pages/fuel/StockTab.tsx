// Stage 1A: stock position (physical / reserved / safety / available to promise),
// the append-only ledger, depots, transfers and reservations.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { Modal, ErrorLine, btnPrimary, btnGhost, card, inputStyle, labelStyle, Chip } from "../warehousing/whseUi"
import {
  fmtL, movementLabel, mismatches, validateAdjust, validateTransfer, validateReserve,
  type Availability, type TankLine,
} from "./stockOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const page = (r: any): any[] => { const d = unwrap(r); return Array.isArray(d) ? d : (d?.content ?? []) }
const when = (iso: string) => new Date(iso).toLocaleString("en-ZA", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" })

type View = "position" | "ledger" | "depots" | "moves"
type Dialog =
  | { kind: "adjust"; tank: TankLine } | { kind: "place"; tank: TankLine } | { kind: "reserve"; tank?: TankLine }
  | { kind: "transfer" } | { kind: "depot"; depot?: any } | null

export default function StockTab() {
  const [view, setView] = useState<View>("position")
  const [dialog, setDialog] = useState<Dialog>(null)
  const canManage = usePermissions(["FUEL_MANAGE"])
  const canAdjust = usePermissions(["FUEL_STOCK_ADJUST"])

  const availability = useQuery<Availability>({ queryKey: ["fuel-availability"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/availability")) })
  const bad = mismatches(availability.data)

  const tabs: [View, string][] = [["position", "Stock position"], ["ledger", "Ledger"], ["depots", "Depots"], ["moves", "Reservations & transfers"]]

  return (
    <div>
      <div style={{ display: "flex", gap: 6, marginBottom: 16, flexWrap: "wrap", alignItems: "center" }}>
        {tabs.map(([id, label]) => (
          <button key={id} onClick={() => setView(id)} style={{ ...btnGhost, ...(view === id ? { background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", borderColor: "var(--hf-primary)" } : {}) }}>{label}</button>
        ))}
        <span style={{ flex: 1 }} />
        {canManage && <button style={btnGhost} onClick={() => setDialog({ kind: "reserve" })}>Reserve fuel</button>}
        {canManage && <button style={btnPrimary} onClick={() => setDialog({ kind: "transfer" })}>Transfer between tanks</button>}
      </div>

      {bad.length > 0 && (
        <div role="alert" style={{ ...card, borderColor: "var(--hf-danger-border)", background: "var(--hf-danger-soft)", color: "var(--hf-danger-text)", padding: 12, fontSize: 13, marginBottom: 14 }}>
          The ledger does not add up to the tank balance for: {bad.map(t => t.tankName).join(", ")}. Do not adjust blindly: investigate the ledger first.
        </div>
      )}

      {view === "position" && <Position data={availability.data} loading={availability.isLoading} canManage={canManage} canAdjust={canAdjust} onDialog={setDialog} />}
      {view === "ledger" && <Ledger tanks={availability.data?.tanks ?? []} />}
      {view === "depots" && <Depots canManage={canManage} onDialog={setDialog} />}
      {view === "moves" && <Moves canManage={canManage} tanks={availability.data?.tanks ?? []} />}

      {dialog?.kind === "adjust" && <AdjustDialog tank={dialog.tank} onClose={() => setDialog(null)} />}
      {dialog?.kind === "place" && <PlaceDialog tank={dialog.tank} onClose={() => setDialog(null)} />}
      {dialog?.kind === "reserve" && <ReserveDialog tanks={availability.data?.tanks ?? []} initial={dialog.tank} onClose={() => setDialog(null)} />}
      {dialog?.kind === "transfer" && <TransferDialog tanks={availability.data?.tanks ?? []} onClose={() => setDialog(null)} />}
      {dialog?.kind === "depot" && <DepotDialog depot={dialog.depot} onClose={() => setDialog(null)} />}
    </div>
  )
}

const th: React.CSSProperties = { textAlign: "left", padding: "10px 12px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)", whiteSpace: "nowrap" }
const td: React.CSSProperties = { padding: "10px 12px", fontSize: 13, color: "var(--hf-text)" }

function Position({ data, loading, canManage, canAdjust, onDialog }: { data?: Availability; loading: boolean; canManage: boolean; canAdjust: boolean; onDialog: (d: Dialog) => void }) {
  if (loading) return <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p>
  if (!data || data.tanks.length === 0) return <p style={{ color: "var(--hf-text-muted)" }}>No tanks yet. Add one under Tanks.</p>
  return (
    <div>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 10px" }}>
        Available to promise = physical stock - reserved - safety stock. Reserving fuel does not change what is in the tank; it stops it being promised twice.
      </p>
      <div style={{ ...card, overflowX: "auto" }}>
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr style={{ background: "var(--hf-surface-muted)" }}>
            {["Tank", "Fuel", "Physical", "Reserved", "Safety", "Available to promise", "Ledger", ""].map(h => <th key={h} style={th}>{h}</th>)}
          </tr></thead>
          <tbody>
            {data.tanks.map(t => (
              <tr key={t.tankId} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                <td style={{ ...td, fontWeight: 600 }}>{t.tankName}</td>
                <td style={td}>{t.fuelType}</td>
                <td style={td}>{fmtL(t.physical)}</td>
                <td style={td}>{fmtL(t.reserved)}</td>
                <td style={td}>{fmtL(t.safetyStock)}</td>
                <td style={{ ...td, fontWeight: 700 }}>{fmtL(t.availableToPromise)}</td>
                <td style={td}>{t.ledgerMatches ? <Chip bg="var(--hf-success-soft-strong)" fg="var(--hf-success-text-strong)">Matches</Chip> : <Chip bg="var(--hf-danger-soft)" fg="var(--hf-danger-text)">Off by {fmtL(t.physical - t.ledgerBalance)}</Chip>}</td>
                <td style={{ ...td, whiteSpace: "nowrap" }}>
                  {canManage && <button style={{ ...btnGhost, padding: "4px 10px" }} onClick={() => onDialog({ kind: "place", tank: t })}>Depot & safety</button>}{" "}
                  {canManage && <button style={{ ...btnGhost, padding: "4px 10px" }} onClick={() => onDialog({ kind: "reserve", tank: t })}>Reserve</button>}{" "}
                  {canAdjust && <button style={{ ...btnGhost, padding: "4px 10px" }} onClick={() => onDialog({ kind: "adjust", tank: t })}>Adjust</button>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {data.depots.length > 0 && (
        <div style={{ ...card, overflowX: "auto", marginTop: 16 }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr style={{ background: "var(--hf-surface-muted)" }}>{["Depot", "Fuel", "Physical", "Reserved", "Safety", "Available to promise"].map(h => <th key={h} style={th}>{h}</th>)}</tr></thead>
            <tbody>{data.depots.map(d => (
              <tr key={d.depotId + d.fuelType} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                <td style={{ ...td, fontWeight: 600 }}>{d.depotName}</td><td style={td}>{d.fuelType}</td><td style={td}>{fmtL(d.physical)}</td>
                <td style={td}>{fmtL(d.reserved)}</td><td style={td}>{fmtL(d.safetyStock)}</td><td style={{ ...td, fontWeight: 700 }}>{fmtL(d.availableToPromise)}</td>
              </tr>))}</tbody>
          </table>
        </div>
      )}
    </div>
  )
}

function Ledger({ tanks }: { tanks: TankLine[] }) {
  const [tankId, setTankId] = useState("")
  const q = useQuery<any[]>({ queryKey: ["fuel-ledger", tankId], queryFn: async () => page(await apiClient.get(`/api/v1/fuel/ledger?size=100${tankId ? `&tankId=${tankId}` : ""}`)) })
  const names = Object.fromEntries(tanks.map(t => [t.tankId, t.tankName]))
  return (
    <div>
      <label style={labelStyle} htmlFor="ledger-tank">Tank</label>
      <select id="ledger-tank" style={{ ...inputStyle, marginBottom: 12 }} value={tankId} onChange={e => setTankId(e.target.value)}>
        <option value="">All tanks</option>{tanks.map(t => <option key={t.tankId} value={t.tankId}>{t.tankName}</option>)}
      </select>
      <div style={{ ...card, overflowX: "auto" }}>
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr style={{ background: "var(--hf-surface-muted)" }}>{["When", "Tank", "Type", "Litres", "Balance after", "Reason"].map(h => <th key={h} style={th}>{h}</th>)}</tr></thead>
          <tbody>
            {(q.data ?? []).map(m => (
              <tr key={m.id} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                <td style={td}>{when(m.createdAt)}</td><td style={td}>{names[m.tankId] ?? "–"}</td><td style={td}>{movementLabel(m.movementType)}</td>
                <td style={{ ...td, fontWeight: 700, color: m.litresDelta < 0 ? "var(--hf-danger-text)" : "var(--hf-accent-text)" }}>{m.litresDelta > 0 ? "+" : ""}{fmtL(m.litresDelta)}</td>
                <td style={td}>{fmtL(m.balanceAfter)}</td><td style={{ ...td, color: "var(--hf-text-muted)" }}>{m.reason ?? ""}</td>
              </tr>))}
            {q.data && q.data.length === 0 && <tr><td colSpan={6} style={{ ...td, color: "var(--hf-text-muted)" }}>No movements yet.</td></tr>}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function Depots({ canManage, onDialog }: { canManage: boolean; onDialog: (d: Dialog) => void }) {
  const qc = useQueryClient()
  const q = useQuery<any[]>({ queryKey: ["fuel-depots"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/depots")) })
  const off = useMutation({ mutationFn: (id: string) => apiClient.post(`/api/v1/fuel/depots/${id}/deactivate`), onSuccess: () => qc.invalidateQueries({ queryKey: ["fuel-depots"] }) })
  return (
    <div>
      {canManage && <button style={{ ...btnPrimary, marginBottom: 12 }} onClick={() => onDialog({ kind: "depot" })}>Add depot</button>}
      <ErrorLine error={off.error} fallback="Could not remove the depot" />
      <div style={{ ...card, overflowX: "auto" }}>
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr style={{ background: "var(--hf-surface-muted)" }}>{["Code", "Name", "Address", "Tanks", ""].map(h => <th key={h} style={th}>{h}</th>)}</tr></thead>
          <tbody>
            {(q.data ?? []).map(d => (
              <tr key={d.id} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                <td style={{ ...td, fontWeight: 700 }}>{d.code}</td><td style={td}>{d.name}</td><td style={{ ...td, color: "var(--hf-text-muted)" }}>{d.address ?? ""}</td><td style={td}>{d.tankCount}</td>
                <td style={td}>{canManage && <>
                  <button style={{ ...btnGhost, padding: "4px 10px" }} onClick={() => onDialog({ kind: "depot", depot: d })}>Edit</button>{" "}
                  <button style={{ ...btnGhost, padding: "4px 10px" }} onClick={() => { if (confirm(`Remove depot ${d.name}?`)) off.mutate(d.id) }}>Remove</button></>}</td>
              </tr>))}
            {q.data && q.data.length === 0 && <tr><td colSpan={5} style={{ ...td, color: "var(--hf-text-muted)" }}>No depots yet. A single-site business can skip this.</td></tr>}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function Moves({ canManage, tanks }: { canManage: boolean; tanks: TankLine[] }) {
  const qc = useQueryClient()
  const names = Object.fromEntries(tanks.map(t => [t.tankId, t.tankName]))
  const res = useQuery<any[]>({ queryKey: ["fuel-reservations"], queryFn: async () => page(await apiClient.get("/api/v1/fuel/reservations?size=100")) })
  const tr = useQuery<any[]>({ queryKey: ["fuel-transfers"], queryFn: async () => page(await apiClient.get("/api/v1/fuel/transfers?size=100")) })
  const release = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/fuel/reservations/${id}/release`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["fuel-reservations"] }); qc.invalidateQueries({ queryKey: ["fuel-availability"] }) },
  })
  return (
    <div style={{ display: "grid", gap: 18 }}>
      <div>
        <h4 style={{ margin: "0 0 8px", fontSize: 14 }}>Reservations</h4>
        <ErrorLine error={release.error} fallback="Could not release" />
        <div style={{ ...card, overflowX: "auto" }}><table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr style={{ background: "var(--hf-surface-muted)" }}>{["Created", "Tank", "Litres", "Status", "Expires", "Note", ""].map(h => <th key={h} style={th}>{h}</th>)}</tr></thead>
          <tbody>
            {(res.data ?? []).map(r => (
              <tr key={r.id} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                <td style={td}>{when(r.createdAt)}</td><td style={td}>{names[r.tankId] ?? "–"}</td><td style={{ ...td, fontWeight: 700 }}>{fmtL(r.litres)}</td>
                <td style={td}>{r.status}</td><td style={td}>{r.expiresAt ? when(r.expiresAt) : "–"}</td><td style={{ ...td, color: "var(--hf-text-muted)" }}>{r.note ?? ""}</td>
                <td style={td}>{canManage && r.status === "ACTIVE" && <button style={{ ...btnGhost, padding: "4px 10px" }} onClick={() => release.mutate(r.id)}>Release</button>}</td>
              </tr>))}
            {res.data && res.data.length === 0 && <tr><td colSpan={7} style={{ ...td, color: "var(--hf-text-muted)" }}>Nothing reserved.</td></tr>}
          </tbody></table></div>
      </div>
      <div>
        <h4 style={{ margin: "0 0 8px", fontSize: 14 }}>Transfers</h4>
        <div style={{ ...card, overflowX: "auto" }}><table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr style={{ background: "var(--hf-surface-muted)" }}>{["When", "From", "To", "Litres", "Reason"].map(h => <th key={h} style={th}>{h}</th>)}</tr></thead>
          <tbody>
            {(tr.data ?? []).map(t => (
              <tr key={t.id} style={{ borderTop: "1px solid var(--hf-border-subtle)" }}>
                <td style={td}>{when(t.createdAt)}</td><td style={td}>{names[t.fromTankId] ?? "–"}</td><td style={td}>{names[t.toTankId] ?? "–"}</td>
                <td style={{ ...td, fontWeight: 700 }}>{fmtL(t.litres)}</td><td style={{ ...td, color: "var(--hf-text-muted)" }}>{t.reason}</td>
              </tr>))}
            {tr.data && tr.data.length === 0 && <tr><td colSpan={5} style={{ ...td, color: "var(--hf-text-muted)" }}>No transfers yet.</td></tr>}
          </tbody></table></div>
      </div>
    </div>
  )
}

/** Shared footer: message, error and the two buttons. */
function Footer({ problem, error, pending, label, onSubmit, onClose }: { problem: string | null; error: unknown; pending: boolean; label: string; onSubmit: () => void; onClose: () => void }) {
  return (<>
    {problem && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "10px 0 0" }}>{problem}</p>}
    <ErrorLine error={error} />
    <div style={{ display: "flex", gap: 8, marginTop: 14 }}>
      <button onClick={onClose} style={{ ...btnGhost, flex: 1, justifyContent: "center" }}>Back</button>
      <button onClick={onSubmit} disabled={!!problem || pending} style={{ ...btnPrimary, flex: 1, justifyContent: "center", opacity: problem ? 0.5 : 1 }}>{pending ? "Working…" : label}</button>
    </div>
  </>)
}

function useDone(keys: string[][], onClose: () => void) {
  const qc = useQueryClient()
  return () => { keys.forEach(k => qc.invalidateQueries({ queryKey: k })); onClose() }
}

function AdjustDialog({ tank, onClose }: { tank: TankLine; onClose: () => void }) {
  const [litres, setLitres] = useState(""); const [reason, setReason] = useState("")
  const done = useDone([["fuel-availability"], ["fuel-ledger"], ["tanks"]], onClose)
  const m = useMutation({ mutationFn: () => apiClient.post(`/api/v1/fuel/tanks/${tank.tankId}/adjust`, { litres: Number(litres), reason: reason.trim() }), onSuccess: done })
  const problem = validateAdjust(litres, reason, tank)
  return (
    <Modal title={`Adjust stock: ${tank.tankName}`} onClose={onClose}>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 10px" }}>Currently {fmtL(tank.physical)}. Use + for fuel found, - for fuel lost. Every adjustment is a permanent ledger line.</p>
      <label style={labelStyle} htmlFor="adj-l">Litres (+/-)</label>
      <input id="adj-l" style={{ ...inputStyle, width: "100%" }} inputMode="decimal" value={litres} onChange={e => setLitres(e.target.value)} />
      <label style={{ ...labelStyle, marginTop: 10 }} htmlFor="adj-r">Reason *</label>
      <textarea id="adj-r" style={{ ...inputStyle, width: "100%", minHeight: 60 }} value={reason} maxLength={500} onChange={e => setReason(e.target.value)} />
      <Footer problem={problem} error={m.error} pending={m.isPending} label="Post adjustment" onSubmit={() => m.mutate()} onClose={onClose} />
    </Modal>
  )
}

function PlaceDialog({ tank, onClose }: { tank: TankLine; onClose: () => void }) {
  const depots = useQuery<any[]>({ queryKey: ["fuel-depots"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/depots")) })
  const [depotId, setDepotId] = useState(tank.depotId ?? ""); const [safety, setSafety] = useState(String(tank.safetyStock))
  const done = useDone([["fuel-availability"], ["fuel-depots"], ["tanks"]], onClose)
  const m = useMutation({ mutationFn: () => apiClient.put(`/api/v1/fuel/tanks/${tank.tankId}/placement`, { depotId: depotId || null, safetyStockLitres: Number(safety) }), onSuccess: done })
  const n = Number(safety)
  const problem = safety.trim() === "" || Number.isNaN(n) || n < 0 ? "Safety stock must be 0 or more." : null
  return (
    <Modal title={`Depot and safety stock: ${tank.tankName}`} onClose={onClose}>
      <label style={labelStyle} htmlFor="pl-d">Depot</label>
      <select id="pl-d" style={{ ...inputStyle, width: "100%" }} value={depotId} onChange={e => setDepotId(e.target.value)}>
        <option value="">No depot</option>{(depots.data ?? []).map(d => <option key={d.id} value={d.id}>{d.code} - {d.name}</option>)}
      </select>
      <label style={{ ...labelStyle, marginTop: 10 }} htmlFor="pl-s">Safety stock (litres kept back from promises)</label>
      <input id="pl-s" style={{ ...inputStyle, width: "100%" }} inputMode="decimal" value={safety} onChange={e => setSafety(e.target.value)} />
      <Footer problem={problem} error={m.error} pending={m.isPending} label="Save" onSubmit={() => m.mutate()} onClose={onClose} />
    </Modal>
  )
}

function ReserveDialog({ tanks, initial, onClose }: { tanks: TankLine[]; initial?: TankLine; onClose: () => void }) {
  const [tankId, setTankId] = useState(initial?.tankId ?? ""); const [litres, setLitres] = useState(""); const [note, setNote] = useState("")
  const done = useDone([["fuel-availability"], ["fuel-reservations"]], onClose)
  const m = useMutation({ mutationFn: () => apiClient.post("/api/v1/fuel/reservations", { tankId, litres: Number(litres), note: note.trim() || null }), onSuccess: done })
  const problem = validateReserve(tanks.find(t => t.tankId === tankId), litres)
  return (
    <Modal title="Reserve fuel" onClose={onClose}>
      <label style={labelStyle} htmlFor="rs-t">Tank</label>
      <select id="rs-t" style={{ ...inputStyle, width: "100%" }} value={tankId} onChange={e => setTankId(e.target.value)}>
        <option value="">Choose…</option>{tanks.map(t => <option key={t.tankId} value={t.tankId}>{t.tankName}: {fmtL(t.availableToPromise)} available</option>)}
      </select>
      <label style={{ ...labelStyle, marginTop: 10 }} htmlFor="rs-l">Litres</label>
      <input id="rs-l" style={{ ...inputStyle, width: "100%" }} inputMode="decimal" value={litres} onChange={e => setLitres(e.target.value)} />
      <label style={{ ...labelStyle, marginTop: 10 }} htmlFor="rs-n">Note (who or what it is for)</label>
      <input id="rs-n" style={{ ...inputStyle, width: "100%" }} value={note} maxLength={300} onChange={e => setNote(e.target.value)} />
      <Footer problem={problem} error={m.error} pending={m.isPending} label="Reserve" onSubmit={() => m.mutate()} onClose={onClose} />
    </Modal>
  )
}

function TransferDialog({ tanks, onClose }: { tanks: TankLine[]; onClose: () => void }) {
  const [from, setFrom] = useState(""); const [to, setTo] = useState(""); const [litres, setLitres] = useState(""); const [reason, setReason] = useState("")
  const done = useDone([["fuel-availability"], ["fuel-transfers"], ["fuel-ledger"], ["tanks"]], onClose)
  const m = useMutation({ mutationFn: () => apiClient.post("/api/v1/fuel/transfers", { fromTankId: from, toTankId: to, litres: Number(litres), reason: reason.trim() }), onSuccess: done })
  const problem = validateTransfer(tanks.find(t => t.tankId === from), tanks.find(t => t.tankId === to), litres, reason)
  const opts = <>{<option value="">Choose…</option>}{tanks.map(t => <option key={t.tankId} value={t.tankId}>{t.tankName} ({t.fuelType}, {fmtL(t.physical)})</option>)}</>
  return (
    <Modal title="Transfer between tanks" onClose={onClose}>
      <label style={labelStyle} htmlFor="tf-f">From</label><select id="tf-f" style={{ ...inputStyle, width: "100%" }} value={from} onChange={e => setFrom(e.target.value)}>{opts}</select>
      <label style={{ ...labelStyle, marginTop: 10 }} htmlFor="tf-t">To</label><select id="tf-t" style={{ ...inputStyle, width: "100%" }} value={to} onChange={e => setTo(e.target.value)}>{opts}</select>
      <label style={{ ...labelStyle, marginTop: 10 }} htmlFor="tf-l">Litres</label><input id="tf-l" style={{ ...inputStyle, width: "100%" }} inputMode="decimal" value={litres} onChange={e => setLitres(e.target.value)} />
      <label style={{ ...labelStyle, marginTop: 10 }} htmlFor="tf-r">Reason *</label><input id="tf-r" style={{ ...inputStyle, width: "100%" }} value={reason} maxLength={500} onChange={e => setReason(e.target.value)} />
      <Footer problem={problem} error={m.error} pending={m.isPending} label="Transfer" onSubmit={() => m.mutate()} onClose={onClose} />
    </Modal>
  )
}

function DepotDialog({ depot, onClose }: { depot?: any; onClose: () => void }) {
  const [name, setName] = useState(depot?.name ?? ""); const [code, setCode] = useState(depot?.code ?? ""); const [address, setAddress] = useState(depot?.address ?? "")
  const done = useDone([["fuel-depots"], ["fuel-availability"]], onClose)
  const body = { name: name.trim(), code: code.trim(), address: address.trim() || null }
  const m = useMutation({ mutationFn: () => depot ? apiClient.put(`/api/v1/fuel/depots/${depot.id}`, body) : apiClient.post("/api/v1/fuel/depots", body), onSuccess: done })
  const problem = !name.trim() ? "Enter a name." : !/^[A-Za-z0-9._-]+$/.test(code.trim()) ? "Code: letters, digits, dot, dash or underscore (up to 20)." : null
  return (
    <Modal title={depot ? "Edit depot" : "Add depot"} onClose={onClose}>
      <label style={labelStyle} htmlFor="dp-n">Name</label><input id="dp-n" style={{ ...inputStyle, width: "100%" }} value={name} maxLength={120} onChange={e => setName(e.target.value)} />
      <label style={{ ...labelStyle, marginTop: 10 }} htmlFor="dp-c">Code</label><input id="dp-c" style={{ ...inputStyle, width: "100%" }} value={code} maxLength={20} onChange={e => setCode(e.target.value)} />
      <label style={{ ...labelStyle, marginTop: 10 }} htmlFor="dp-a">Address</label><input id="dp-a" style={{ ...inputStyle, width: "100%" }} value={address} maxLength={300} onChange={e => setAddress(e.target.value)} />
      <Footer problem={problem} error={m.error} pending={m.isPending} label="Save" onSubmit={() => m.mutate()} onClose={onClose} />
    </Modal>
  )
}
