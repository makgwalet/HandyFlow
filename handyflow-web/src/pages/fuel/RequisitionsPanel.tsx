import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { btnGhost, btnPrimary, card, Chip, ErrorLine, inputStyle, labelStyle, Modal, ReasonModal } from "../warehousing/whseUi"
import { fmtL, fmtR } from "./orderOps"
import { FUELS, STATUS_LABEL, actionsFor, canSubmit, emptyReq, fromRequisition, toRequisitionRequest, validateConvert, validateRequisition, type ReqForm, type Requisition } from "./requisitionOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const msg = (e: any, fb: string) => e?.response?.data?.message ?? fb
const th: React.CSSProperties = { textAlign: "left", padding: "10px 12px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)", whiteSpace: "nowrap" }
const td: React.CSSProperties = { padding: "10px 12px", fontSize: 13, color: "var(--hf-text)" }
const tone = (s: string) => s === "APPROVED" || s === "CONVERTED" ? { bg: "var(--hf-success-soft-strong)", fg: "var(--hf-success-text-strong)" } : s === "CANCELLED" ? { bg: "var(--hf-danger-soft)", fg: "var(--hf-danger-text)" } : s === "PENDING_APPROVAL" ? { bg: "var(--hf-warning-soft)", fg: "var(--hf-warning-text)" } : { bg: "var(--hf-surface-alt)", fg: "var(--hf-text)" }

/** Requests to buy fuel. Approval is decided by the company's approval rules; an approved requisition becomes a draft purchase order. */
export default function RequisitionsPanel() {
  const canManage = usePermissions(["FUEL_MANAGE", "FUEL_PURCHASING_MANAGE"])
  const qc = useQueryClient()
  const [editing, setEditing] = useState<{ r?: Requisition } | null>(null)
  const [cancelling, setCancelling] = useState<Requisition | null>(null)
  const [converting, setConverting] = useState<Requisition | null>(null)
  const [err, setErr] = useState("")
  const q = useQuery<Requisition[]>({ queryKey: ["fuel-requisitions"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/requisitions")) })
  const suppliers = useQuery<any[]>({ queryKey: ["fuel-requisition-suppliers"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/suppliers")) })
  const approved = (Array.isArray(suppliers.data) ? suppliers.data : []).filter(s => !s.approvalStatus || s.approvalStatus === "APPROVED")
  const refresh = () => { qc.invalidateQueries({ queryKey: ["fuel-requisitions"] }); qc.invalidateQueries({ queryKey: ["fuel-purchase-orders"] }); setEditing(null); setCancelling(null); setConverting(null); setErr("") }
  const submit = useMutation({ mutationFn: (id: string) => apiClient.post(`/api/v1/fuel/requisitions/${id}/submit`), onSuccess: refresh, onError: (e: any) => setErr(msg(e, "Could not submit the requisition")) })
  const cancel = useMutation({ mutationFn: (a: { id: string; reason: string }) => apiClient.post(`/api/v1/fuel/requisitions/${a.id}/cancel`, { reason: a.reason }), onSuccess: refresh })
  const rows = Array.isArray(q.data) ? q.data : []
  return (
    <div style={{ marginBottom: 22 }}>
      <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 10 }}>
        <div style={{ flex: 1 }}>
          <div style={{ fontWeight: 700, fontSize: 14 }}>Requisitions</div>
          <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "2px 0 0" }}>Ask to buy fuel before anyone orders it. Once approved, raise a purchase order from it.</p>
        </div>
        {canManage && <button style={btnPrimary} onClick={() => setEditing({})}>New requisition</button>}
      </div>
      {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{err}</p>}
      {q.isLoading ? <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p> : q.isError ? <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>Could not load requisitions.</p> : rows.length === 0 ? <p style={{ color: "var(--hf-text-muted)", fontSize: 13 }}>No requisitions yet.</p> : (
        <div style={{ ...card, overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Requisition</th><th style={th}>Fuel</th><th style={th}>Litres</th><th style={th}>Estimate</th><th style={th}>Needed by</th><th style={th}>Why</th><th style={th}>Status</th><th style={th}></th></tr></thead>
            <tbody>
              {rows.map(r => (
                <tr key={r.id} style={{ borderTop: "1px solid var(--hf-border)" }}>
                  <td style={td}><strong>{r.reqNumber}</strong></td><td style={td}>{r.fuelType}</td><td style={td}>{fmtL(r.litres)}</td>
                  <td style={td}>{r.estimatedValue == null ? "–" : fmtR(r.estimatedValue)}</td><td style={td}>{r.neededBy ?? "–"}</td>
                  <td style={{ ...td, maxWidth: 220 }}>{r.reason}</td>
                  <td style={td}>
                    <Chip bg={tone(r.status).bg} fg={tone(r.status).fg}>{STATUS_LABEL[r.status] ?? r.status}</Chip>
                    {r.status === "DRAFT" && r.approvalNote && <div style={{ fontSize: 11, color: "var(--hf-danger-text)", marginTop: 3 }}>{r.approvalNote}</div>}
                    {r.status === "CONVERTED" && r.purchaseOrderNumber && <div style={{ fontSize: 11, color: "var(--hf-text-muted)", marginTop: 3 }}>{r.purchaseOrderNumber}</div>}
                    {r.status === "CANCELLED" && r.cancelReason && <div style={{ fontSize: 11, color: "var(--hf-text-muted)", marginTop: 3 }}>{r.cancelReason}</div>}
                  </td>
                  <td style={td}>{canManage && <span style={{ display: "flex", gap: 6 }}>
                    {actionsFor(r).includes("edit") && <button style={btnGhost} onClick={() => setEditing({ r })} aria-label={`Edit ${r.reqNumber}`}>Edit</button>}
                    {actionsFor(r).includes("submit") && <button style={btnPrimary} disabled={!canSubmit(r) || submit.isPending} title={canSubmit(r) ? undefined : "Add an estimated price first"} onClick={() => submit.mutate(r.id)} aria-label={`Submit ${r.reqNumber}`}>Submit</button>}
                    {actionsFor(r).includes("convert") && <button style={btnPrimary} onClick={() => setConverting(r)} aria-label={`Raise order from ${r.reqNumber}`}>Raise purchase order</button>}
                    {actionsFor(r).includes("cancel") && <button style={btnGhost} onClick={() => setCancelling(r)} aria-label={`Cancel ${r.reqNumber}`}>Cancel</button>}
                  </span>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {editing && <RequisitionForm requisition={editing.r} suppliers={approved} onClose={() => setEditing(null)} onSaved={refresh} />}
      {converting && <ConvertDialog requisition={converting} suppliers={approved} onClose={() => setConverting(null)} onDone={refresh} />}
      {cancelling && <ReasonModal title={`Cancel ${cancelling.reqNumber}`} intro="The requisition is kept, marked as cancelled." confirmLabel="Cancel requisition" pending={cancel.isPending} error={cancel.error}
        onConfirm={reason => cancel.mutate({ id: cancelling.id, reason })} onClose={() => setCancelling(null)} />}
    </div>
  )
}

function RequisitionForm({ requisition, suppliers, onClose, onSaved }: { requisition?: Requisition; suppliers: any[]; onClose: () => void; onSaved: () => void }) {
  const [f, setF] = useState<ReqForm>(requisition ? fromRequisition(requisition) : emptyReq())
  const [problem, setProblem] = useState("")
  const save = useMutation({
    mutationFn: (x: ReqForm) => requisition ? apiClient.put(`/api/v1/fuel/requisitions/${requisition.id}`, toRequisitionRequest(x)) : apiClient.post("/api/v1/fuel/requisitions", toRequisitionRequest(x)),
    onSuccess: onSaved,
  })
  const set = (k: keyof ReqForm) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => { setProblem(""); setF(p => ({ ...p, [k]: e.target.value })) }
  return (
    <Modal title={requisition ? `Edit ${requisition.reqNumber}` : "New requisition"} onClose={onClose}>
      <div style={{ display: "grid", gap: 8 }}>
        <div><label style={labelStyle} htmlFor="rq-fuel">Fuel *</label><select id="rq-fuel" style={{ ...inputStyle, width: "100%" }} value={f.fuelType} onChange={set("fuelType")}>{FUELS.map(x => <option key={x} value={x}>{x}</option>)}</select></div>
        <div><label style={labelStyle} htmlFor="rq-litres">Litres *</label><input id="rq-litres" type="number" inputMode="decimal" style={{ ...inputStyle, width: "100%" }} value={f.litres} onChange={set("litres")} /></div>
        <div><label style={labelStyle} htmlFor="rq-price">Estimated price per litre (needed to submit)</label><input id="rq-price" type="number" inputMode="decimal" style={{ ...inputStyle, width: "100%" }} value={f.estimatedPrice} onChange={set("estimatedPrice")} /></div>
        <div><label style={labelStyle} htmlFor="rq-by">Needed by</label><input id="rq-by" type="date" style={{ ...inputStyle, width: "100%" }} value={f.neededBy} onChange={set("neededBy")} /></div>
        <div><label style={labelStyle} htmlFor="rq-sup">Preferred supplier</label>
          <select id="rq-sup" style={{ ...inputStyle, width: "100%" }} value={f.supplierId} onChange={set("supplierId")}><option value="">Decide later</option>{suppliers.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select></div>
        <div><label style={labelStyle} htmlFor="rq-why">Why is it needed? *</label><textarea id="rq-why" style={{ ...inputStyle, width: "100%", minHeight: 60 }} maxLength={300} value={f.reason} onChange={set("reason")} /></div>
        {problem && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: 0 }}>{problem}</p>}
        <ErrorLine error={save.error} />
        <div style={{ display: "flex", gap: 8 }}>
          <button style={btnGhost} onClick={onClose}>Cancel</button>
          <button style={btnPrimary} disabled={save.isPending} onClick={() => { const p = validateRequisition(f); if (p) return setProblem(p); save.mutate(f) }}>Save</button>
        </div>
      </div>
    </Modal>
  )
}

function ConvertDialog({ requisition: r, suppliers, onClose, onDone }: { requisition: Requisition; suppliers: any[]; onClose: () => void; onDone: () => void }) {
  const [supplierId, setSupplierId] = useState(r.supplierId ?? ""); const [price, setPrice] = useState(r.estimatedPrice == null ? "" : String(r.estimatedPrice)); const [expected, setExpected] = useState(r.neededBy ?? "")
  const [problem, setProblem] = useState("")
  const go = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/fuel/requisitions/${r.id}/convert`, { supplierId: supplierId || null, pricePerLitre: price.trim() ? Number(price) : null, expectedDate: expected || null }),
    onSuccess: onDone,
  })
  return (
    <Modal title={`Raise a purchase order from ${r.reqNumber}`} onClose={onClose}>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 10px" }}>This makes a draft order for {fmtL(r.litres)} of {r.fuelType}. You place it from Purchase orders, where it is approved as usual.</p>
      <div style={{ display: "grid", gap: 8 }}>
        <div><label style={labelStyle} htmlFor="cv-sup">Supplier *</label>
          <select id="cv-sup" style={{ ...inputStyle, width: "100%" }} value={supplierId} onChange={e => { setProblem(""); setSupplierId(e.target.value) }}><option value="">Choose…</option>{suppliers.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select></div>
        <div><label style={labelStyle} htmlFor="cv-price">Price per litre *</label><input id="cv-price" type="number" inputMode="decimal" style={{ ...inputStyle, width: "100%" }} value={price} onChange={e => { setProblem(""); setPrice(e.target.value) }} /></div>
        <div><label style={labelStyle} htmlFor="cv-by">Expected delivery</label><input id="cv-by" type="date" style={{ ...inputStyle, width: "100%" }} value={expected} onChange={e => setExpected(e.target.value)} /></div>
        {problem && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: 0 }}>{problem}</p>}
        <ErrorLine error={go.error} />
        <div style={{ display: "flex", gap: 8 }}>
          <button style={btnGhost} onClick={onClose}>Back</button>
          <button style={btnPrimary} disabled={go.isPending} onClick={() => { const p = validateConvert(r, supplierId, price); if (p) return setProblem(p); go.mutate() }}>Raise purchase order</button>
        </div>
      </div>
    </Modal>
  )
}
