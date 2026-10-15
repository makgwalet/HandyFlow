// src/pages/earthmoving/HireTab.tsx
//
// Hire charges: one billing period of one deployment, counted in days, weeks, months, hours or kilometres and priced from
// the rate on the hire contract, else the machine's own rate (or a rate agreed for that charge, with a reason).
// A kilometre charge that depends on a disputed reading is held back until the dispute is closed. Pending charges for one
// customer can be put on a single DRAFT invoice in Invoicing. Nothing here issues an invoice: the draft is reviewed and
// issued there. A charge that is on an invoice cannot be voided; if the draft is deleted or cancelled the charge can be
// reopened, with a reason.
import { useState } from "react"
import { Receipt } from "lucide-react"
import { apiMessage, fmtCurrency2dp, fmtDate } from "./shared/format"
import { Overlay, ModalHead, ModalFoot, ErrBanner, lbl, inputStyle } from "./shared/Modal"
import { useEarthPerms } from "./shared/useEarthPerms"
import { HIRE_RATE_SOURCE_LABEL } from "./shared/constants"
import {
  useHireCharges, useAssets, useAssetDeployments, useContractRates, usePreviewHireCharge, useCreateHireCharge, useHireChargeAction, useDraftInvoice, useEarthSettings,
} from "./shared/hooks"
import type { HireCharge } from "./shared/types"

const btn = (primary = false): React.CSSProperties => ({
  padding: "6px 12px", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer",
  border: primary ? "none" : "1px solid var(--hf-border)",
  background: primary ? "var(--hf-primary)" : "var(--hf-surface)",
  color: primary ? "var(--hf-text-on-solid)" : "var(--hf-text-secondary)",
})
const card: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 10, padding: "14px 18px", background: "var(--hf-surface)" }

const STATUS_LABEL: Record<string, string> = { PENDING: "Not invoiced", INVOICED: "On an invoice", VOIDED: "Voided" }
const FILTERS = [["PENDING", "Not invoiced"], ["INVOICED", "On an invoice"], ["VOIDED", "Voided"], ["", "All"]] as const
const basisWord = (b: string) => (b === "DAILY" ? "days" : b === "WEEKLY" ? "weeks" : b === "MONTHLY" ? "months" : b === "KILOMETRE" ? "km" : "hours")
const BASIS_UNIT: Record<string, string> = { DAILY: "day", WEEKLY: "week", MONTHLY: "month", HOURLY: "hour", KILOMETRE: "kilometre" }
const EMPTY = { assetId: "", deploymentId: "", basis: "DAILY", periodFrom: "", periodTo: "", vatRate: "", unitRate: "", rateReason: "", includedKm: "" }

export default function HireTab() {
  const perms = useEarthPerms()
  const [status, setStatus] = useState("PENDING")
  const [selected, setSelected] = useState<string[]>([])
  const [notice, setNotice] = useState("")
  const [err, setErr] = useState("")
  const [creating, setCreating] = useState(false)
  const [form, setForm] = useState(EMPTY)
  const [preview, setPreview] = useState<HireCharge | null>(null)
  const [acting, setActing] = useState<{ charge: HireCharge; kind: "void" | "reopen" } | null>(null)
  const [reason, setReason] = useState("")

  const { data: charges = [], isLoading } = useHireCharges(status)
  const { data: assets = [] } = useAssets()
  const { data: deployments = [] } = useAssetDeployments(form.assetId)
  const { data: held } = useContractRates(form.deploymentId)
  const { data: settings } = useEarthSettings()
  const vatRegistered = settings?.vatRegistered !== false
  const companyVat = settings?.vatRate ?? 15
  const previewM = usePreviewHireCharge()
  const create = useCreateHireCharge(() => { setCreating(false); setPreview(null) })
  const action = useHireChargeAction(() => { setActing(null); setReason("") })
  const draft = useDraftInvoice(r => {
    setSelected([])
    setNotice(`Draft invoice ${r.invoiceNumber} created for ${r.charges} charge${r.charges === 1 ? "" : "s"}, total ${fmtCurrency2dp(r.total)}. Review and issue it in Invoicing.`)
  })

  const body = () => ({
    deploymentId: form.deploymentId, basis: form.basis, periodFrom: form.periodFrom, periodTo: form.periodTo,
    vatRate: form.vatRate.trim() === "" ? null : Number(form.vatRate),
    unitRate: form.unitRate.trim() === "" ? null : Number(form.unitRate),
    rateReason: form.unitRate.trim() === "" ? null : form.rateReason.trim(),
    includedKm: form.basis === "KILOMETRE" && form.includedKm.trim() !== "" ? Number(form.includedKm) : null,
  })
  const km = form.basis === "KILOMETRE"
  const rateGiven = Number(form.unitRate) > 0 && form.rateReason.trim().length >= 5
  // Without a typed rate the server uses the contract's, else the machine's; it says so if there is none to use.
  const rateOk = form.unitRate.trim() === "" || rateGiven
  const contractRate = held?.rates?.find(r => r.basis === form.basis)
  const formReady = !!form.deploymentId && !!form.periodFrom && !!form.periodTo && rateOk

  const openCreate = () => { setForm(EMPTY); setPreview(null); setErr(""); setCreating(true) }
  const change = (patch: Partial<typeof EMPTY>) => { setForm(f => ({ ...f, ...patch })); setPreview(null) }

  const doPreview = () => {
    setErr("")
    previewM.mutate(body(), { onSuccess: setPreview, onError: (e: unknown) => setErr(apiMessage(e, "Could not work out the charge")) })
  }
  const doCreate = () => {
    setErr("")
    create.mutate(body(), { onError: (e: unknown) => setErr(apiMessage(e, "Could not save the charge")) })
  }
  const doAction = () => {
    if (!acting?.charge.id) return
    setErr("")
    action.mutate({ id: acting.charge.id, kind: acting.kind, reason: reason.trim() }, { onError: (e: unknown) => setErr(apiMessage(e, "Could not change the charge")) })
  }

  const pending = charges.filter(c => c.status === "PENDING" && c.id)
  const chosen = pending.filter(c => selected.includes(c.id as string))
  const customers = new Set(chosen.map(c => c.customerId))
  const mixed = customers.size > 1
  const toggle = (id: string) => setSelected(s => (s.includes(id) ? s.filter(x => x !== id) : [...s, id]))
  const chosenTotal = chosen.reduce((t, c) => t + c.total, 0)

  const submitDraft = () => {
    setNotice(""); setErr("")
    draft.mutate({ chargeIds: chosen.map(c => c.id), title: null, dueDate: null }, { onError: (e: unknown) => setErr(apiMessage(e, "Could not create the draft invoice")) })
  }

  const renderCharge = (c: HireCharge) => (
    <div key={c.id ?? c.description} style={{ ...card, display: "flex", gap: 12, alignItems: "flex-start", opacity: c.status === "VOIDED" ? 0.6 : 1 }}>
      {perms.canAdmin && c.status === "PENDING" && c.id && (
        <input type="checkbox" aria-label={`Select ${c.machine} ${c.periodFrom}`} checked={selected.includes(c.id)} disabled={!!c.attention} onChange={() => toggle(c.id as string)} style={{ marginTop: 4 }} />
      )}
      <div style={{ flex: 1 }}>
        <div style={{ fontWeight: 700, color: "var(--hf-text)" }}>{c.machine} · {c.customerName ?? "No customer linked"}</div>
        <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 2 }}>
          {c.siteName ?? "No site"} · {fmtDate(c.periodFrom)} to {fmtDate(c.periodTo)}
        </div>
        <div style={{ fontSize: 12, color: "var(--hf-text-secondary)", marginTop: 6 }}>
          {c.quantity} {basisWord(c.basis)} at {fmtCurrency2dp(c.unitRate)} = {fmtCurrency2dp(c.amount)}
          {" "}+ VAT {fmtCurrency2dp(c.vatAmount)} = <strong>{fmtCurrency2dp(c.total)}</strong>
        </div>
        <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 4 }}>
          {STATUS_LABEL[c.status] ?? c.status}{c.invoiceNumber ? ` · ${c.invoiceNumber}` : ""}
          {c.rateSource ? ` · ${HIRE_RATE_SOURCE_LABEL[c.rateSource] ?? c.rateSource}` : ""}{c.note ? ` · ${c.note}` : ""}
          {c.recalculatedAt ? ` · recalculated ${fmtDate(c.recalculatedAt)} after a reading changed` : ""}
        </div>
        {c.attention && (
          <div role="alert" style={{ fontSize: 12, marginTop: 4, fontWeight: 700, color: "var(--hf-warning-text)" }}>
            On hold, cannot be invoiced yet: {c.attention}
          </div>
        )}
      </div>
      {perms.canAdmin && c.id && (
        <div style={{ display: "flex", gap: 8 }}>
          {c.status === "PENDING" && <button style={btn()} onClick={() => { setErr(""); setReason(""); setActing({ charge: c, kind: "void" }) }}>Void</button>}
          {c.status === "INVOICED" && <button style={btn()} onClick={() => { setErr(""); setReason(""); setActing({ charge: c, kind: "reopen" }) }}>Reopen</button>}
        </div>
      )}
    </div>
  )

  const renderCreate = () => (
    <Overlay onClose={() => setCreating(false)} width={560}>
      <ModalHead title="New hire charge" onClose={() => setCreating(false)} />
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14, padding: "4px 0 10px" }}>
        <div><label htmlFor="hc-asset" style={lbl}>Machine *</label>
          <select id="hc-asset" value={form.assetId} onChange={e => change({ assetId: e.target.value, deploymentId: "" })} style={inputStyle()}>
            <option value="">Choose…</option>{assets.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}</select></div>
        <div><label htmlFor="hc-deployment" style={lbl}>Deployment *</label>
          <select id="hc-deployment" value={form.deploymentId} onChange={e => change({ deploymentId: e.target.value })} style={inputStyle()} disabled={!form.assetId}>
            <option value="">Choose…</option>
            {deployments.map(d => <option key={d.id} value={d.id}>{d.siteName ?? "No site"} · {d.clientName ?? "No client"} · {fmtDate(d.deployedAt)}</option>)}</select></div>
        <div><label htmlFor="hc-basis" style={lbl}>Charge by</label>
          <select id="hc-basis" value={form.basis} onChange={e => change({ basis: e.target.value })} style={inputStyle()}>
            <option value="DAILY">Day</option><option value="WEEKLY">Week (part of a week counts as a week)</option>
            <option value="MONTHLY">Month (part of a month counts as a month)</option>
            <option value="HOURLY">Hour (from operator logs)</option><option value="KILOMETRE">Kilometre (from odometer readings)</option></select></div>
        <div><label htmlFor="hc-vat" style={lbl}>VAT %</label>
          <input id="hc-vat" value={vatRegistered ? form.vatRate : ""} disabled={!vatRegistered}
            placeholder={vatRegistered ? `Company rate (${companyVat}%)` : "Not registered for VAT"}
            onChange={e => change({ vatRate: e.target.value })} style={inputStyle()} /></div>
        <div><label htmlFor="hc-from" style={lbl}>From *</label>
          <input id="hc-from" type="date" value={form.periodFrom} onChange={e => change({ periodFrom: e.target.value })} style={inputStyle()} /></div>
        <div><label htmlFor="hc-to" style={lbl}>To *</label>
          <input id="hc-to" type="date" value={form.periodTo} onChange={e => change({ periodTo: e.target.value })} style={inputStyle()} /></div>
        <div><label htmlFor="hc-rate" style={lbl}>{`Agreed rate per ${BASIS_UNIT[form.basis] ?? "unit"} (leave blank to use the contract's or machine's rate)`}</label>
          <input id="hc-rate" type="number" min="0" value={form.unitRate} onChange={e => change({ unitRate: e.target.value })} style={inputStyle()} />
          {form.unitRate.trim() === "" && form.deploymentId && (
            <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 4 }}>
              {contractRate
                ? `The contract's rate: ${fmtCurrency2dp(contractRate.unitRate)} per ${BASIS_UNIT[form.basis]}${contractRate.includedKm != null ? `, ${contractRate.includedKm} km included` : ""}.`
                : form.basis === "DAILY" || form.basis === "HOURLY" ? "The contract has no rate for this, so the machine's own rate is used."
                : `The contract has no ${BASIS_UNIT[form.basis]} rate. Agree one here, or add it to the contract.`}
            </div>
          )}</div>
        {km && (
          <div><label htmlFor="hc-incl" style={lbl}>Kilometres included (not charged; blank uses the contract's)</label>
            <input id="hc-incl" type="number" min="0" value={form.includedKm} onChange={e => change({ includedKm: e.target.value })} style={inputStyle()} /></div>
        )}
        {form.unitRate.trim() !== "" && (
          <div><label htmlFor="hc-rate-why" style={lbl}>Why this rate? * (at least 5 characters)</label>
            <input id="hc-rate-why" value={form.rateReason} onChange={e => change({ rateReason: e.target.value })} style={inputStyle()} /></div>
        )}
      </div>
      {preview && (
        <div role="status" style={{ ...card, marginBottom: 10, fontSize: 13 }}>
          {preview.description}<br />
          {preview.quantity} {basisWord(preview.basis)} at {fmtCurrency2dp(preview.unitRate)} = {fmtCurrency2dp(preview.amount)}
          {" "}+ VAT {fmtCurrency2dp(preview.vatAmount)} = <strong>{fmtCurrency2dp(preview.total)}</strong>
        </div>
      )}
      {err && <ErrBanner msg={err} />}
      <div style={{ display: "flex", gap: 8, justifyContent: "flex-end", marginBottom: 8 }}>
        <button style={btn()} disabled={!formReady || previewM.isPending} onClick={doPreview}>Work out the charge</button>
      </div>
      <ModalFoot onCancel={() => setCreating(false)} onSubmit={doCreate} loading={create.isPending} label="Save charge" disabled={!preview} />
    </Overlay>
  )

  const renderAction = () => acting && (
    <Overlay onClose={() => setActing(null)} width={460}>
      <ModalHead title={acting.kind === "void" ? "Void this charge" : "Reopen this charge"} onClose={() => setActing(null)} />
      <div style={{ fontSize: 13, color: "var(--hf-text-secondary)", marginBottom: 10 }}>
        {acting.kind === "void"
          ? "A voided charge is never invoiced and its days become free to charge again."
          : "Only possible once the draft invoice has been deleted or cancelled in Invoicing."}
      </div>
      <label htmlFor="hc-reason" style={lbl}>Reason * (at least 5 characters)</label>
      <input id="hc-reason" value={reason} onChange={e => setReason(e.target.value)} style={inputStyle()} />
      {err && <ErrBanner msg={err} />}
      <ModalFoot onCancel={() => setActing(null)} onSubmit={doAction} loading={action.isPending}
        label={acting.kind === "void" ? "Void charge" : "Reopen charge"} disabled={reason.trim().length < 5} />
    </Overlay>
  )

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14, gap: 12, flexWrap: "wrap" }}>
        <div style={{ display: "flex", gap: 8 }}>
          {FILTERS.map(([v, label]) => <button key={label} style={btn(status === v)} onClick={() => { setStatus(v); setSelected([]) }}>{label}</button>)}
        </div>
        {perms.canAdmin && <button style={btn(true)} onClick={openCreate}>New hire charge</button>}
      </div>
      {notice && <div role="status" style={{ ...card, marginBottom: 12, color: "var(--hf-success-text-strong)" }}>{notice}</div>}
      {!creating && !acting && err && <ErrBanner msg={err} />}
      {perms.canAdmin && chosen.length > 0 && (
        <div style={{ ...card, marginBottom: 12, display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12 }}>
          <div style={{ fontSize: 13 }}>
            {chosen.length} selected · {fmtCurrency2dp(chosenTotal)} with VAT
            {mixed && <div style={{ color: "var(--hf-danger-text)" }}>These belong to different customers. Pick one customer's charges for each invoice.</div>}
          </div>
          <button style={btn(true)} disabled={mixed || draft.isPending} onClick={submitDraft}>Create draft invoice</button>
        </div>
      )}
      {isLoading ? <div style={{ color: "var(--hf-text-muted)" }}>Loading…</div>
        : charges.length === 0 ? (
          <div style={{ textAlign: "center", padding: "50px 20px", border: "1px dashed var(--hf-border)", borderRadius: 12, color: "var(--hf-text-faint)" }}>
            <Receipt size={34} style={{ marginBottom: 10, opacity: 0.4 }} />
            <div style={{ fontWeight: 600 }}>No hire charges here</div>
            <div style={{ fontSize: 13, marginTop: 4 }}>Raise a charge for a deployment, then put the ones for one customer on a draft invoice.</div>
          </div>
        ) : <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>{charges.map(renderCharge)}</div>}
      {creating && renderCreate()}
      {renderAction()}
    </div>
  )
}
