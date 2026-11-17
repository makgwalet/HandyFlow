// Phase 7c (B06b): supply contracts that group and report. A contract holds no prices; it links to the account's existing price entries.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { Modal, btnPrimary, btnGhost, card, inputStyle, labelStyle, Chip } from "../warehousing/whseUi"
import { fmtR } from "./orderOps"
import { emptyContract, fromContract, validateContract, toContractRequest, progressText, daysLeftText, stepText, linkBody, STATUS_LABEL, type Contract, type ContractForm } from "./contractOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const msg = (e: any, fb: string) => e?.response?.data?.message ?? fb
const th: React.CSSProperties = { textAlign: "left", padding: "10px 12px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)", whiteSpace: "nowrap" }
const td: React.CSSProperties = { padding: "10px 12px", fontSize: 13, color: "var(--hf-text)" }

export default function ContractsTab() {
  const canManage = usePermissions(["FUEL_MANAGE", "FUEL_CUSTOMERS_MANAGE", "FUEL_PRICING_MANAGE"])
  const qc = useQueryClient()
  const [dialog, setDialog] = useState<{ contract?: Contract } | null>(null)
  const [cancelling, setCancelling] = useState<Contract | null>(null)
  const [open, setOpen] = useState<string | null>(null)
  const [err, setErr] = useState("")
  const q = useQuery<Contract[]>({ queryKey: ["fuel-contracts"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/contracts")) })
  const refresh = () => qc.invalidateQueries({ queryKey: ["fuel-contracts"] })
  const rows = q.data ?? []
  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 12, flexWrap: "wrap" }}>
        <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0, flex: 1, minWidth: 260 }}>
          A contract ties a customer to dates, an optional volume commitment and terms, and refers to that customer's prices in the price list. It holds no prices of its own, so price approvals and margin checks still apply. Litres are counted from the customer's delivered loads.
        </p>
        {canManage && <button style={btnPrimary} onClick={() => setDialog({})}>New contract</button>}
      </div>
      {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12.5 }}>{err}</p>}
      {q.isLoading ? <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p> : q.isError ? <p role="alert" style={{ color: "var(--hf-danger-text)" }}>Could not load contracts.</p> : rows.length === 0 ? (
        <p style={{ color: "var(--hf-text-muted)" }}>No contracts yet.</p>
      ) : (
        <div style={{ ...card, overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Contract</th><th style={th}>Customer</th><th style={th}>Period</th><th style={th}>Status</th><th style={th}>Delivered</th><th style={th}>Prices</th><th style={th} /></tr></thead>
            <tbody>{rows.map(c => (
              <ContractRows key={c.id} c={c} open={open === c.id} canManage={canManage} onToggle={() => setOpen(open === c.id ? null : c.id)}
                onEdit={() => setDialog({ contract: c })} onCancel={() => setCancelling(c)} onChanged={refresh} onError={setErr} />
            ))}</tbody>
          </table>
        </div>
      )}
      {dialog && <ContractDialog contract={dialog.contract} onDone={refresh} onClose={() => setDialog(null)} />}
      {cancelling && <CancelDialog contract={cancelling} onDone={refresh} onClose={() => setCancelling(null)} />}
    </div>
  )
}

function ContractRows({ c, open, canManage, onToggle, onEdit, onCancel, onChanged, onError }: { c: Contract; open: boolean; canManage: boolean; onToggle: () => void; onEdit: () => void; onCancel: () => void; onChanged: () => void; onError: (m: string) => void }) {
  const tone = c.status === "ACTIVE" ? ["var(--hf-success-soft-strong)", "var(--hf-success-text-strong)"] : c.status === "CANCELLED" ? ["var(--hf-danger-soft)", "var(--hf-danger-text)"] : ["var(--hf-warning-soft)", "var(--hf-warning-text)"]
  return (
    <>
      <tr style={{ borderTop: "1px solid var(--hf-border)" }}>
        <td style={td}><button onClick={onToggle} aria-expanded={open} style={{ background: "none", border: "none", padding: 0, color: "inherit", fontWeight: 700, cursor: "pointer" }}>{c.contractNumber}</button><div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>{c.title}</div></td>
        <td style={td}>{c.customerName ?? "-"}</td>
        <td style={td}>{c.startDate} to {c.endDate}<div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>{daysLeftText(c)}</div></td>
        <td style={td}><Chip bg={tone[0]} fg={tone[1]}>{STATUS_LABEL[c.status]}</Chip></td>
        <td style={td}>{progressText(c)}{c.fuelType && <div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>{c.fuelType} only</div>}</td>
        <td style={td}>{c.linkedPrices}</td>
        <td style={td}>{canManage && c.status !== "CANCELLED" && <><button style={btnGhost} onClick={onEdit}>Edit</button> <button style={btnGhost} onClick={onCancel}>Cancel</button></>}</td>
      </tr>
      {open && (
        <tr><td colSpan={7} style={{ ...td, background: "var(--hf-surface-muted)" }}>
          {c.terms && <p style={{ margin: "0 0 8px", fontSize: 12.5, whiteSpace: "pre-wrap" }}><strong>Terms: </strong>{c.terms}</p>}
          {c.cancelledReason && <p style={{ margin: "0 0 8px", fontSize: 12.5 }}><strong>Cancelled: </strong>{c.cancelledReason}</p>}
          <PricePanel c={c} canManage={canManage} onChanged={onChanged} onError={onError} />
        </td></tr>
      )}
    </>
  )
}

function PricePanel({ c, canManage, onChanged, onError }: { c: Contract; canManage: boolean; onChanged: () => void; onError: (m: string) => void }) {
  const [pick, setPick] = useState("")
  const all = useQuery<any[]>({ queryKey: ["fuel-contract-prices"], enabled: canManage && c.status !== "CANCELLED", queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/prices")) })
  const linkedIds = new Set(c.prices.map(p => p.priceId))
  const options = (Array.isArray(all.data) ? all.data : []).filter(p => p.accountId === c.accountId && !linkedIds.has(p.id) && (!c.fuelType || p.fuelType?.toUpperCase() === c.fuelType.toUpperCase()))
  const [from, setFrom] = useState("")
  const link = useMutation({ mutationFn: (id: string) => apiClient.put(`/api/v1/fuel/contracts/${c.id}/prices/${id}`, linkBody(from) ?? {}), onSuccess: () => { onError(""); setPick(""); setFrom(""); onChanged() }, onError: (e: any) => onError(msg(e, "Could not link the price")) })
  const unlink = useMutation({ mutationFn: (id: string) => apiClient.delete(`/api/v1/fuel/contracts/${c.id}/prices/${id}`), onSuccess: () => { onError(""); onChanged() }, onError: (e: any) => onError(msg(e, "Could not unlink the price")) })
  return (
    <div>
      <p style={{ margin: "0 0 6px", fontSize: 12.5, fontWeight: 700 }}>Prices this contract refers to</p>
      {c.prices.length === 0 ? <p style={{ margin: "0 0 6px", fontSize: 12.5, color: "var(--hf-text-muted)" }}>None linked.</p> : c.prices.map(p => (
        <div key={p.priceId} style={{ fontSize: 12.5, padding: "3px 0" }}>
          {p.fuelType} {fmtR(p.pricePerLitre)}/L{p.minLitres > 0 ? ` from ${p.minLitres.toLocaleString("en-ZA")} L` : ""}{stepText(p) ? ` · ${stepText(p)}` : ""} · {p.validFrom} to {p.validTo ?? "open"} · {p.approvalStatus === "APPROVED" ? (p.live ? "live" : "not live") : p.approvalStatus.toLowerCase().replace("_", " ")}{p.inEffect ? <strong> · in effect now</strong> : null}
          {canManage && c.status !== "CANCELLED" && <> <button style={btnGhost} onClick={() => unlink.mutate(p.priceId)}>Unlink</button></>}
        </div>
      ))}
      {canManage && c.status !== "CANCELLED" && (
        <div style={{ display: "flex", gap: 8, marginTop: 8, alignItems: "center" }}>
          <select aria-label="Link a price" value={pick} onChange={e => setPick(e.target.value)} style={{ ...inputStyle, width: "auto" }}>
            <option value="">Link one of this customer's prices…</option>
            {options.map(p => <option key={p.id} value={p.id}>{p.fuelType} {fmtR(p.pricePerLitre)}/L · {p.validFrom}</option>)}
          </select>
          <input aria-label="From litres" inputMode="decimal" placeholder="From litres (optional)" value={from} onChange={e => setFrom(e.target.value)} style={{ ...inputStyle, width: 170 }} />
          <button style={btnGhost} disabled={!pick || link.isPending} onClick={() => link.mutate(pick)}>Link</button>
        </div>
      )}
      <p style={{ margin: "8px 0 0", fontSize: 11, color: "var(--hf-text-muted)" }}>Prices are added and approved under Price lists. Linking records which prices belong to this contract. For volume discounts, link several prices of one fuel and set "From litres" on each: the step for the litres delivered on this contract so far applies to new orders, and only approved, live prices count. Existing orders keep their price.</p>
    </div>
  )
}

function ContractDialog({ contract, onDone, onClose }: { contract?: Contract; onDone: () => void; onClose: () => void }) {
  const adding = !contract
  const [f, setF] = useState<ContractForm>(contract ? fromContract(contract) : emptyContract()); const [err, setErr] = useState("")
  const accounts = useQuery<any[]>({ queryKey: ["fuel-contract-accounts"], enabled: adding, queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/customer-accounts")) })
  const set = (k: keyof ContractForm) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => setF(s => ({ ...s, [k]: e.target.value }))
  const save = useMutation({
    mutationFn: () => adding ? apiClient.post("/api/v1/fuel/contracts", toContractRequest(f, true)) : apiClient.put(`/api/v1/fuel/contracts/${contract!.id}`, toContractRequest(f, false)),
    onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not save the contract")),
  })
  return (
    <Modal title={adding ? "New contract" : `Edit contract ${contract!.contractNumber}`} onClose={onClose}>
      <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
        {adding && <div><label style={labelStyle} htmlFor="ct-acc">Customer account *</label>
          <select id="ct-acc" value={f.accountId} onChange={set("accountId")} style={inputStyle}><option value="">Choose…</option>{(Array.isArray(accounts.data) ? accounts.data : []).map(a => <option key={a.id} value={a.id}>{a.customerName}</option>)}</select></div>}
        {adding && <div><label style={labelStyle} htmlFor="ct-no">Contract number *</label><input id="ct-no" value={f.contractNumber} maxLength={40} onChange={set("contractNumber")} style={inputStyle} /></div>}
        <div><label style={labelStyle} htmlFor="ct-title">Title *</label><input id="ct-title" value={f.title} maxLength={200} onChange={set("title")} style={inputStyle} /></div>
        <div style={{ display: "flex", gap: 10 }}>
          <div style={{ flex: 1 }}><label style={labelStyle} htmlFor="ct-start">Starts *</label><input id="ct-start" type="date" value={f.startDate} onChange={set("startDate")} style={inputStyle} /></div>
          <div style={{ flex: 1 }}><label style={labelStyle} htmlFor="ct-end">Ends *</label><input id="ct-end" type="date" value={f.endDate} onChange={set("endDate")} style={inputStyle} /></div>
        </div>
        <div style={{ display: "flex", gap: 10 }}>
          <div style={{ flex: 1 }}><label style={labelStyle} htmlFor="ct-fuel">Fuel type</label><input id="ct-fuel" value={f.fuelType} placeholder="All fuels" onChange={set("fuelType")} style={inputStyle} /></div>
          <div style={{ flex: 1 }}><label style={labelStyle} htmlFor="ct-lit">Committed litres</label><input id="ct-lit" inputMode="decimal" value={f.committedLitres} placeholder="No commitment" onChange={set("committedLitres")} style={inputStyle} /></div>
        </div>
        <div><label style={labelStyle} htmlFor="ct-terms">Terms</label><textarea id="ct-terms" rows={4} value={f.terms} maxLength={2000} onChange={set("terms")} style={{ ...inputStyle, resize: "vertical" }} /></div>
      </div>
      {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: "8px 0 0" }}>{err}</p>}
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
        <button style={btnGhost} onClick={onClose}>Cancel</button>
        <button style={btnPrimary} disabled={save.isPending} onClick={() => { const m = validateContract(f, adding); if (m) return setErr(m); setErr(""); save.mutate() }}>Save</button>
      </div>
    </Modal>
  )
}

function CancelDialog({ contract, onDone, onClose }: { contract: Contract; onDone: () => void; onClose: () => void }) {
  const [reason, setReason] = useState(""); const [err, setErr] = useState("")
  const save = useMutation({ mutationFn: () => apiClient.post(`/api/v1/fuel/contracts/${contract.id}/cancel`, { reason: reason.trim() }), onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not cancel the contract")) })
  return (
    <Modal title={`Cancel contract ${contract.contractNumber}`} onClose={onClose}>
      <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: "0 0 10px" }}>A cancelled contract stays on record but cannot be changed. Its prices stay in the price list.</p>
      <label style={labelStyle} htmlFor="cc-reason">Reason *</label>
      <input id="cc-reason" value={reason} maxLength={300} onChange={e => setReason(e.target.value)} style={inputStyle} />
      {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: "8px 0 0" }}>{err}</p>}
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
        <button style={btnGhost} onClick={onClose}>Keep contract</button>
        <button style={btnPrimary} disabled={save.isPending} onClick={() => { if (!reason.trim()) return setErr("Give a reason for cancelling the contract."); setErr(""); save.mutate() }}>Cancel contract</button>
      </div>
    </Modal>
  )
}
