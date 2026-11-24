// src/pages/complianceservices/PriceBook.tsx
//
// The practice's price book. Each version is written by a person, reviewed by another, and approved with a start
// date. An approved version is never edited: a change is a new version, so a quote keeps the prices it was given.
// Prices are ex VAT and hold no government fees. The "Try a price" panel only works a number out; it quotes nothing.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission, usePermissions } from "../../hooks/usePermission"
import { useAuth } from "../../hooks/useAuth"
import { STATUS_LABELS, MIN_REVIEW_REASON, approveNeedsReason, approveBlocker, describeSpan } from "./obligationRules"
import { labelOf } from "./profileRules"
import { PRICING_MODELS } from "./serviceRules"
import {
  TAX_TREATMENTS, UNIT_NAMES, APPROVER_LABELS, emptyLine, formatMoney, normaliseGrade, lineGaps, saveProblem, missingForReview, toBody,
  inputsNeeded, calcProblem, num, type BookDraft, type Disb, type Extra,
} from "./pricingRules"

interface Line { serviceCode: string; serviceName: string | null; pricingModel: string; baseFee: number | null; unitRate: number | null; hourlyGrade: string | null; minFee: number | null; urgentSurchargePct: number | null; taxTreatment: string; notes: string | null; gaps: string[] }
interface Rate { grade: string; hourlyRate: number; costRate: number | null }
interface Version {
  id: string; versionNumber: number; reviewStatus: string; currency: string; label: string | null; changeSummary: string | null; authoredBy: string | null
  submittedBy: string | null; reviewedBy: string | null; reviewNote: string | null; reviewExceptionReason: string | null
  effectiveFrom: string | null; effectiveTo: string | null; inForce: boolean; lines: Line[]; rates: Rate[]
}
interface ServiceOption { code: string; name: string | null }
interface Part { label: string; amount: number }
interface Calc {
  serviceCode: string; priceBookVersion: number; parts: Part[]; professionalBeforeDiscount: number; discount: number; professional: number
  disbursements: number; vat: number; vatRatePct: number; total: number; discountApprover: string
}
interface Margin { priceBookVersion: number; cost: number; recommendedPrice: number; roundedPrice: number; marginAtRounded: number | null; marginAtProposed: number | null }

const ACCENT = "var(--hf-success-solid-strong)"
const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T
const errText = (e: unknown): string => {
  const x = e as { response?: { data?: { message?: string } }; message?: string }
  return x?.response?.data?.message ?? x?.message ?? "Something went wrong"
}
const input: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)", boxSizing: "border-box" }
const button: React.CSSProperties = { padding: "8px 14px", border: "none", borderRadius: 8, background: ACCENT, color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const ghost: React.CSSProperties = { padding: "6px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "transparent", color: "var(--hf-text-muted)", fontSize: 12, cursor: "pointer" }
const card: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 10, padding: "10px 12px" }
const lab: React.CSSProperties = { display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-muted)", marginBottom: 4 }
const bad: React.CSSProperties = { fontSize: 11, color: "var(--hf-danger-text, #c0392b)" }
const grid: React.CSSProperties = { display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))", gap: 8 }

const toDraft = (v: Version): BookDraft => ({
  label: v.label ?? "", changeSummary: v.changeSummary ?? "",
  lines: v.lines.map(l => ({
    serviceCode: l.serviceCode, pricingModel: l.pricingModel, baseFee: l.baseFee?.toString() ?? "", unitRate: l.unitRate?.toString() ?? "", hourlyGrade: l.hourlyGrade ?? "",
    minFee: l.minFee?.toString() ?? "", urgentSurchargePct: l.urgentSurchargePct?.toString() ?? "", taxTreatment: l.taxTreatment, notes: l.notes ?? "",
  })),
  rates: v.rates.map(r => ({ grade: r.grade, hourlyRate: r.hourlyRate.toString(), costRate: r.costRate?.toString() ?? "" })),
})

function describeLine(l: Line): string {
  const bits: string[] = []
  if (l.baseFee != null) bits.push(`${formatMoney(l.baseFee)}${l.pricingModel === "RETAINER" ? " a month" : ""}`)
  if (l.unitRate != null) bits.push(`${formatMoney(l.unitRate)} ${UNIT_NAMES[l.pricingModel] ? `per ${UNIT_NAMES[l.pricingModel].replace(/s$/, "")}` : "per unit"}`)
  if (l.hourlyGrade) bits.push(`hours at the ${l.hourlyGrade.toLowerCase().replace(/_/g, " ")} rate`)
  if (l.minFee != null) bits.push(`minimum ${formatMoney(l.minFee)}`)
  if (l.urgentSurchargePct != null) bits.push(`urgent +${l.urgentSurchargePct}%`)
  if (l.taxTreatment !== "STANDARD") bits.push(labelOf(TAX_TREATMENTS, l.taxTreatment).toLowerCase())
  return bits.join(" · ")
}

export default function PriceBook() {
  const qc = useQueryClient()
  const canManage = usePermissions(["COMPLIANCE_SERVICES_MANAGE", "COMPLIANCE_SERVICES_ADMIN"])
  const canApprove = usePermission("COMPLIANCE_SERVICES_ADMIN")
  const { user } = useAuth()
  const base = "/api/v1/compliance-services/price-book"
  const [draft, setDraft] = useState<BookDraft | null>(null)
  const [draftFor, setDraftFor] = useState<string | null>(null)
  const [error, setError] = useState("")
  const [from, setFrom] = useState("")
  const [reason, setReason] = useState("")
  const [sendBack, setSendBack] = useState("")

  const { data: versions = [] } = useQuery<Version[]>({ queryKey: ["cs-pricebook"], queryFn: async () => unwrap<Version[]>(await apiClient.get(base)) })
  const { data: services = [] } = useQuery<ServiceOption[]>({
    queryKey: ["cs-service-options"],
    queryFn: async () => unwrap<ServiceOption[]>(await apiClient.get("/api/v1/compliance-services/service-catalogue")),
  })

  const done = (d: Version[]) => { setError(""); setDraft(null); setDraftFor(null); setReason(""); setSendBack(""); qc.setQueryData(["cs-pricebook"], d) }
  const fail = (e: unknown) => setError(errText(e))
  const save = useMutation({ mutationFn: async (v: { versionId: string; d: BookDraft }) => unwrap<Version[]>(await apiClient.put(`${base}/versions/${v.versionId}`, toBody(v.d, canApprove))), onSuccess: done, onError: fail })
  const post = useMutation({ mutationFn: async (v: { path: string; payload?: object }) => unwrap<Version[]>(await apiClient.post(`${base}${v.path}`, v.payload ?? {})), onSuccess: done, onError: fail })

  const sorted = [...versions].sort((a, b) => b.versionNumber - a.versionNumber)
  const open = sorted.find(v => v.reviewStatus !== "APPROVED")
  const hasOpenEnded = sorted.some(v => v.reviewStatus === "APPROVED" && !v.effectiveTo)

  return (
    <div style={{ display: "grid", gap: 14 }}>
      <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>
        What the practice charges. Each version is written by one person, approved by another, and can be quoted from only once it has a start date. Prices are before VAT. Government fees are not in the book: add them to a quote at cost.
      </div>
      {error && <div role="alert" style={{ ...bad, fontSize: 12 }}>{error}</div>}
      {sorted.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>No price book yet.</div>}

      {sorted.map(v => {
        const editing = draftFor === v.id && draft
        const needsReason = approveNeedsReason(v, user?.userId)
        const approveProblem = approveBlocker(needsReason, reason, from)
        return (
          <div key={v.id} style={card}>
            <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
              <strong style={{ fontSize: 13 }}>Price book version {v.versionNumber}{v.label ? ` · ${v.label}` : ""}</strong>
              <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{STATUS_LABELS[v.reviewStatus]}{v.inForce ? " · in force today" : ""}</span>
              {v.reviewStatus === "APPROVED" && <span style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>{describeSpan(v.effectiveFrom, v.effectiveTo)}</span>}
              <span style={{ flex: 1 }} />
              {canManage && v.reviewStatus === "DRAFT" && !editing && <button style={ghost} onClick={() => { setDraft(toDraft(v)); setDraftFor(v.id) }}>Edit</button>}
              {canManage && v.reviewStatus === "DRAFT" && !editing && <button style={ghost} disabled={post.isPending} onClick={() => post.mutate({ path: `/versions/${v.id}/submit` })}>Send for review</button>}
            </div>
            {v.reviewNote && v.reviewStatus === "DRAFT" && <div style={{ fontSize: 12, color: "var(--hf-warning-text, #8a5a00)", marginTop: 6 }}>Sent back: {v.reviewNote}</div>}

            {!editing && (
              <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 8, display: "grid", gap: 3 }}>
                {v.lines.length === 0 && <div>No services priced yet.</div>}
                {v.lines.map(l => (
                  <div key={l.serviceCode}>
                    <strong>{l.serviceCode}</strong>{l.serviceName ? ` ${l.serviceName}` : ""} · {labelOf(PRICING_MODELS, l.pricingModel)} · {describeLine(l)}
                    {l.gaps.length > 0 && <span style={bad}> · needs {l.gaps.join(", ")}</span>}
                  </div>
                ))}
                {v.rates.length > 0 && (
                  <div style={{ marginTop: 4 }}>Hourly rates: {v.rates.map(r => `${r.grade.toLowerCase().replace(/_/g, " ")} ${formatMoney(r.hourlyRate)}${r.costRate != null ? ` (costs ${formatMoney(r.costRate)})` : ""}`).join("; ")}</div>
                )}
                {v.changeSummary && <div>Changed: {v.changeSummary}</div>}
                {v.reviewExceptionReason && <div>Approved by the person who wrote it. Reason: {v.reviewExceptionReason}</div>}
              </div>
            )}

            {editing && draft && <Editor d={draft} onChange={setDraft} versionNumber={v.versionNumber} services={services} admin={canApprove}
              onSave={() => save.mutate({ versionId: v.id, d: draft })} onCancel={() => { setDraft(null); setDraftFor(null) }} saving={save.isPending} />}

            {canApprove && v.reviewStatus === "IN_REVIEW" && (
              <div style={{ display: "grid", gap: 8, marginTop: 10 }}>
                <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 8 }}>
                  <div><label style={lab}>Prices apply from</label><input style={input} type="date" aria-label="Prices apply from" value={from} onChange={e => setFrom(e.target.value)} /></div>
                  {needsReason && <div><label style={lab}>You wrote or sent this. Why can no one else approve it?</label><input style={input} placeholder="Reason, at least 15 characters" value={reason} onChange={e => setReason(e.target.value)} /></div>}
                </div>
                {approveProblem && (from || reason) && <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{approveProblem}</div>}
                <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                  <button style={{ ...button, opacity: approveProblem || post.isPending ? 0.5 : 1 }} disabled={!!approveProblem || post.isPending}
                    onClick={() => post.mutate({ path: `/versions/${v.id}/approve`, payload: { effectiveFrom: from, exceptionReason: needsReason ? reason : null, note: null } })}>Approve</button>
                  <input style={{ ...input, maxWidth: 260 }} placeholder="What needs to change" value={sendBack} onChange={e => setSendBack(e.target.value)} />
                  <button style={ghost} disabled={sendBack.trim().length < MIN_REVIEW_REASON || post.isPending}
                    onClick={() => post.mutate({ path: `/versions/${v.id}/reject`, payload: { reason: sendBack } })}>Send back</button>
                </div>
              </div>
            )}
            {!canApprove && v.reviewStatus === "IN_REVIEW" && <div style={{ fontSize: 12, color: "var(--hf-text-faint)", marginTop: 8 }}>Waiting for an administrator to review it.</div>}
          </div>
        )
      })}

      <div style={{ display: "flex", gap: 8 }}>
        {canManage && !open && <button style={ghost} disabled={post.isPending} onClick={() => post.mutate({ path: "/versions" })}>{sorted.length === 0 ? "Start the price book" : "Start a new version"}</button>}
        {canApprove && hasOpenEnded && <button style={ghost} disabled={post.isPending} onClick={() => post.mutate({ path: "/retire", payload: {} })}>Stop quoting from today</button>}
      </div>

      <TryAPrice versions={versions} admin={canApprove} />
    </div>
  )
}

function Editor({ d, onChange, versionNumber, services, admin, onSave, onCancel, saving }: {
  d: BookDraft; onChange: (d: BookDraft) => void; versionNumber: number; services: ServiceOption[]; admin: boolean; onSave: () => void; onCancel: () => void; saving: boolean
}) {
  const problem = saveProblem(d)
  const missing = missingForReview(d, versionNumber)
  const grades = d.rates.map(r => normaliseGrade(r.grade)).filter(Boolean)
  const setLine = (i: number, k: string, value: string) => onChange({ ...d, lines: d.lines.map((l, j) => (j === i ? { ...l, [k]: value } : l)) })
  const setRate = (i: number, k: string, value: string) => onChange({ ...d, rates: d.rates.map((r, j) => (j === i ? { ...r, [k]: value } : r)) })

  return (
    <div style={{ display: "grid", gap: 10, marginTop: 10 }}>
      <div style={grid}>
        <div><label style={lab}>Label</label><input style={input} aria-label="Label" placeholder="e.g. 2027 prices" value={d.label} onChange={e => onChange({ ...d, label: e.target.value })} /></div>
      </div>
      {versionNumber > 1 && <div><label style={lab}>What changed in this version</label><textarea style={input} rows={2} aria-label="What changed" value={d.changeSummary} onChange={e => onChange({ ...d, changeSummary: e.target.value })} /></div>}

      <div>
        <label style={lab}>Hourly rates by grade{admin ? " (cost rate is what an hour costs the practice)" : ""}</label>
        {d.rates.map((r, i) => (
          <div key={i} style={{ display: "flex", gap: 6, marginBottom: 6, flexWrap: "wrap" }}>
            <input style={{ ...input, width: 180 }} aria-label="Grade" placeholder="Grade, e.g. Senior" value={r.grade} onChange={e => setRate(i, "grade", e.target.value)} />
            <input style={{ ...input, width: 140 }} aria-label="Hourly rate" inputMode="decimal" placeholder="Hourly rate" value={r.hourlyRate} onChange={e => setRate(i, "hourlyRate", e.target.value)} />
            {admin && <input style={{ ...input, width: 140 }} aria-label="Cost rate" inputMode="decimal" placeholder="Cost rate" value={r.costRate} onChange={e => setRate(i, "costRate", e.target.value)} />}
            <button style={ghost} onClick={() => onChange({ ...d, rates: d.rates.filter((_, j) => j !== i) })}>Remove</button>
          </div>
        ))}
        <button style={ghost} onClick={() => onChange({ ...d, rates: [...d.rates, { grade: "", hourlyRate: "", costRate: "" }] })}>Add a grade</button>
      </div>

      <div>
        <label style={lab}>Services and their prices (before VAT)</label>
        {d.lines.map((l, i) => {
          const gaps = lineGaps(l)
          return (
            <div key={i} style={{ ...card, marginBottom: 8, display: "grid", gap: 8 }}>
              <div style={grid}>
                <select style={input} aria-label="Service" value={l.serviceCode} onChange={e => setLine(i, "serviceCode", e.target.value)}>
                  <option value="">Choose a service</option>
                  {services.map(s => <option key={s.code} value={s.code}>{s.code}{s.name ? ` · ${s.name}` : ""}</option>)}
                  {l.serviceCode && !services.some(s => s.code === l.serviceCode) && <option value={l.serviceCode}>{l.serviceCode}</option>}
                </select>
                <select style={input} aria-label="Pricing model" value={l.pricingModel} onChange={e => setLine(i, "pricingModel", e.target.value)}>
                  {PRICING_MODELS.map(o => <option key={o[0]} value={o[0]}>{o[1]}</option>)}
                </select>
                <input style={input} aria-label="Base fee" inputMode="decimal" placeholder={l.pricingModel === "RETAINER" ? "Monthly fee" : "Base fee"} value={l.baseFee} onChange={e => setLine(i, "baseFee", e.target.value)} />
                <input style={input} aria-label="Rate per unit" inputMode="decimal" placeholder="Rate per unit" value={l.unitRate} onChange={e => setLine(i, "unitRate", e.target.value)} />
                <select style={input} aria-label="Hourly grade" value={normaliseGrade(l.hourlyGrade)} onChange={e => setLine(i, "hourlyGrade", e.target.value)}>
                  <option value="">No hourly grade</option>
                  {grades.map(g => <option key={g} value={g}>{g}</option>)}
                </select>
                <input style={input} aria-label="Minimum fee" inputMode="decimal" placeholder="Minimum fee" value={l.minFee} onChange={e => setLine(i, "minFee", e.target.value)} />
                <input style={input} aria-label="Urgent surcharge percent" inputMode="decimal" placeholder="Urgent surcharge %" value={l.urgentSurchargePct} onChange={e => setLine(i, "urgentSurchargePct", e.target.value)} />
                <select style={input} aria-label="VAT treatment" value={l.taxTreatment} onChange={e => setLine(i, "taxTreatment", e.target.value)}>
                  {TAX_TREATMENTS.map(o => <option key={o[0]} value={o[0]}>{o[1]}</option>)}
                </select>
              </div>
              <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
                <input style={{ ...input, flex: 1, minWidth: 200 }} aria-label="Notes" placeholder="Notes (optional)" value={l.notes} onChange={e => setLine(i, "notes", e.target.value)} />
                <button style={ghost} onClick={() => onChange({ ...d, lines: d.lines.filter((_, j) => j !== i) })}>Remove</button>
              </div>
              {gaps.length > 0 && <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>This model needs {gaps.join(", ")}.</div>}
            </div>
          )
        })}
        <button style={ghost} onClick={() => onChange({ ...d, lines: [...d.lines, emptyLine()] })}>Add a service</button>
      </div>

      {problem && <div style={bad}>{problem}</div>}
      {missing.length > 0 && <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>Before it can go for review it needs: {missing.join(", ")}.</div>}
      <div style={{ display: "flex", gap: 8 }}>
        <button style={{ ...button, opacity: problem || saving ? 0.5 : 1 }} disabled={!!problem || saving} onClick={onSave}>{saving ? "Saving..." : "Save draft"}</button>
        <button style={ghost} onClick={onCancel}>Cancel</button>
      </div>
    </div>
  )
}

/** Works a price out from the book in force on a date. It saves nothing and sends nothing to a client. */
function TryAPrice({ versions, admin }: { versions: Version[]; admin: boolean }) {
  const inForce = versions.find(v => v.inForce)
  const [code, setCode] = useState("")
  const [asOf, setAsOf] = useState("")
  const [f, setF] = useState({ units: "", hours: "", complexityPct: "", discountPct: "", urgent: false })
  const [extras, setExtras] = useState<Extra[]>([])
  const [disb, setDisb] = useState<Disb[]>([])
  const [result, setResult] = useState<Calc | null>(null)
  const [error, setError] = useState("")
  const [m, setM] = useState({ grade: "", hours: "", external: "", overhead: "", margin: "", step: "", proposed: "" })
  const [margin, setMargin] = useState<Margin | null>(null)

  const line = inForce?.lines.find(l => l.serviceCode === code)
  const needs = line ? inputsNeeded(line.pricingModel, !!line.hourlyGrade) : { units: false, hours: false }
  const problem = !code ? "Choose a service" : calcProblem({ ...f, extras, disbursements: disb }, needs, line?.pricingModel ?? "")

  const calc = useMutation({
    mutationFn: async () => unwrap<Calc>(await apiClient.post("/api/v1/compliance-services/pricing/calculate", {
      serviceCode: code, asOf: asOf || null, units: num(f.units), hours: num(f.hours), complexityPct: num(f.complexityPct), urgent: f.urgent, discountPct: num(f.discountPct),
      extras: extras.map(e => ({ description: e.description.trim(), quantity: num(e.quantity), unitPrice: num(e.unitPrice) })),
      disbursements: disb.map(x => ({ description: x.description.trim(), amount: num(x.amount), vatable: x.vatable })),
    })),
    onSuccess: (r: Calc) => { setError(""); setResult(r) },
    onError: (e: unknown) => { setResult(null); setError(errText(e)) },
  })
  const marginCalc = useMutation({
    mutationFn: async () => unwrap<Margin>(await apiClient.post("/api/v1/compliance-services/pricing/margin", {
      grade: m.grade, hours: num(m.hours), externalCosts: num(m.external), overheadPct: num(m.overhead), targetMarginPct: num(m.margin), roundStep: num(m.step), proposedPrice: num(m.proposed), asOf: asOf || null,
    })),
    onSuccess: (r: Margin) => { setError(""); setMargin(r) },
    onError: (e: unknown) => { setMargin(null); setError(errText(e)) },
  })

  const field = (label: string, key: "units" | "hours" | "complexityPct" | "discountPct", hint?: string) => (
    <div><label style={lab}>{label}</label><input style={input} aria-label={label} inputMode="decimal" placeholder={hint} value={f[key]} onChange={e => setF({ ...f, [key]: e.target.value })} /></div>
  )

  return (
    <div style={{ ...card, display: "grid", gap: 10 }}>
      <strong style={{ fontSize: 13 }}>Try a price</strong>
      <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
        {inForce ? `Uses price book version ${inForce.versionNumber}, in force today. This works out a number for a person to check; it does not send a quote.` : "There is no approved price book in force yet."}
      </div>
      {inForce && (
        <>
          <div style={grid}>
            <div>
              <label style={lab}>Service</label>
              <select style={input} aria-label="Service to price" value={code} onChange={e => { setCode(e.target.value); setResult(null) }}>
                <option value="">Choose a service</option>
                {inForce.lines.map(l => <option key={l.serviceCode} value={l.serviceCode}>{l.serviceCode}{l.serviceName ? ` · ${l.serviceName}` : ""}</option>)}
              </select>
            </div>
            <div><label style={lab}>Price on date</label><input style={input} type="date" aria-label="Price on date" value={asOf} onChange={e => setAsOf(e.target.value)} /></div>
            {needs.units && field(line && UNIT_NAMES[line.pricingModel] ? `Number of ${UNIT_NAMES[line.pricingModel]}` : "Units", "units")}
            {needs.hours && field("Hours", "hours")}
            {field("Complexity %", "complexityPct", "0")}
            {field("Discount %", "discountPct", "0")}
            <label style={{ fontSize: 12, display: "flex", alignItems: "center", gap: 6, alignSelf: "end", paddingBottom: 8 }}>
              <input type="checkbox" checked={f.urgent} onChange={e => setF({ ...f, urgent: e.target.checked })} /> Urgent
            </label>
          </div>

          <div>
            <label style={lab}>Extras (priced work beyond the service)</label>
            {extras.map((x, i) => (
              <div key={i} style={{ display: "flex", gap: 6, marginBottom: 6, flexWrap: "wrap" }}>
                <input style={{ ...input, flex: 1, minWidth: 160 }} aria-label="Extra description" placeholder="Description" value={x.description} onChange={e => setExtras(extras.map((y, j) => (j === i ? { ...y, description: e.target.value } : y)))} />
                <input style={{ ...input, width: 90 }} aria-label="Extra quantity" placeholder="Qty" value={x.quantity} onChange={e => setExtras(extras.map((y, j) => (j === i ? { ...y, quantity: e.target.value } : y)))} />
                <input style={{ ...input, width: 120 }} aria-label="Extra price" placeholder="Price each" value={x.unitPrice} onChange={e => setExtras(extras.map((y, j) => (j === i ? { ...y, unitPrice: e.target.value } : y)))} />
                <button style={ghost} onClick={() => setExtras(extras.filter((_, j) => j !== i))}>Remove</button>
              </div>
            ))}
            <button style={ghost} onClick={() => setExtras([...extras, { description: "", quantity: "1", unitPrice: "" }])}>Add an extra</button>
          </div>

          <div>
            <label style={lab}>Disbursements at cost (government fees, travel). Never discounted.</label>
            {disb.map((x, i) => (
              <div key={i} style={{ display: "flex", gap: 6, marginBottom: 6, flexWrap: "wrap", alignItems: "center" }}>
                <input style={{ ...input, flex: 1, minWidth: 160 }} aria-label="Disbursement description" placeholder="Description" value={x.description} onChange={e => setDisb(disb.map((y, j) => (j === i ? { ...y, description: e.target.value } : y)))} />
                <input style={{ ...input, width: 120 }} aria-label="Disbursement amount" placeholder="Amount" value={x.amount} onChange={e => setDisb(disb.map((y, j) => (j === i ? { ...y, amount: e.target.value } : y)))} />
                <label style={{ fontSize: 12, display: "flex", alignItems: "center", gap: 4 }}>
                  <input type="checkbox" checked={x.vatable} onChange={e => setDisb(disb.map((y, j) => (j === i ? { ...y, vatable: e.target.checked } : y)))} /> Carries VAT
                </label>
                <button style={ghost} onClick={() => setDisb(disb.filter((_, j) => j !== i))}>Remove</button>
              </div>
            ))}
            <button style={ghost} onClick={() => setDisb([...disb, { description: "", amount: "", vatable: false }])}>Add a disbursement</button>
          </div>

          {problem && code && <div style={bad}>{problem}</div>}
          {error && <div role="alert" style={bad}>{error}</div>}
          <div><button style={{ ...button, opacity: problem || calc.isPending ? 0.5 : 1 }} disabled={!!problem || calc.isPending} onClick={() => calc.mutate()}>Work out the price</button></div>

          {result && (
            <div style={{ fontSize: 13, display: "grid", gap: 3 }} aria-label="Price result">
              {result.parts.map((p, i) => <div key={i} style={{ display: "flex", justifyContent: "space-between", gap: 12 }}><span>{p.label}</span><span>{formatMoney(p.amount)}</span></div>)}
              {result.discount > 0 && <div style={{ display: "flex", justifyContent: "space-between", gap: 12 }}><span>Discount</span><span>-{formatMoney(result.discount)}</span></div>}
              <div style={{ display: "flex", justifyContent: "space-between", gap: 12 }}><span>Professional fees</span><span>{formatMoney(result.professional)}</span></div>
              {result.disbursements > 0 && <div style={{ display: "flex", justifyContent: "space-between", gap: 12 }}><span>Disbursements at cost</span><span>{formatMoney(result.disbursements)}</span></div>}
              <div style={{ display: "flex", justifyContent: "space-between", gap: 12 }}><span>VAT at {result.vatRatePct}%</span><span>{formatMoney(result.vat)}</span></div>
              <div style={{ display: "flex", justifyContent: "space-between", gap: 12, fontWeight: 700 }}><span>Total</span><span>{formatMoney(result.total)}</span></div>
              {result.discount > 0 && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{APPROVER_LABELS[result.discountApprover] ?? result.discountApprover} (proposed limits, not yet approved policy).</div>}
              <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>Price book version {result.priceBookVersion}.</div>
            </div>
          )}

          {admin && (
            <div style={{ borderTop: "1px solid var(--hf-border)", paddingTop: 10, display: "grid", gap: 8 }}>
              <strong style={{ fontSize: 13 }}>Margin check (administrators only)</strong>
              <div style={grid}>
                <div><label style={lab}>Grade</label>
                  <select style={input} aria-label="Margin grade" value={m.grade} onChange={e => setM({ ...m, grade: e.target.value })}>
                    <option value="">Choose a grade</option>{inForce.rates.map(r => <option key={r.grade} value={r.grade}>{r.grade}</option>)}
                  </select>
                </div>
                {([["hours", "Hours of work"], ["external", "External costs"], ["overhead", "Overhead % of labour"], ["margin", "Target margin %"], ["step", "Round up to"], ["proposed", "Proposed price"]] as const).map(([k, label]) => (
                  <div key={k}><label style={lab}>{label}</label><input style={input} aria-label={label} inputMode="decimal" value={m[k]} onChange={e => setM({ ...m, [k]: e.target.value })} /></div>
                ))}
              </div>
              <div><button style={{ ...ghost, opacity: !m.grade || !m.hours.trim() || marginCalc.isPending ? 0.5 : 1 }} disabled={!m.grade || !m.hours.trim() || marginCalc.isPending} onClick={() => marginCalc.mutate()}>Work out the margin</button></div>
              {margin && (
                <div style={{ fontSize: 13, display: "grid", gap: 3 }} aria-label="Margin result">
                  <div>Cost to the practice: {formatMoney(margin.cost)}</div>
                  <div>Price for the target margin: {formatMoney(margin.recommendedPrice)}{m.step.trim() ? `, rounded up to ${formatMoney(margin.roundedPrice)}` : ""}</div>
                  <div>Margin at {m.step.trim() ? "the rounded price" : "that price"}: {margin.marginAtRounded == null ? "—" : `${margin.marginAtRounded}%`}</div>
                  {margin.marginAtProposed != null && <div>Margin at your proposed price: {margin.marginAtProposed}%</div>}
                </div>
              )}
            </div>
          )}
        </>
      )}
    </div>
  )
}
