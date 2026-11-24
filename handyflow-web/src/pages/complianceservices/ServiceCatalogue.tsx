// src/pages/complianceservices/ServiceCatalogue.tsx
//
// The practice's service catalogue. Every service is written by a person, reviewed by another, and approved with a
// start date. An approved version is never edited: a change is a new version, so work already sold keeps its version.
// There are no prices here. A service only says which pricing model and billing trigger it uses.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission, usePermissions } from "../../hooks/usePermission"
import { useAuth } from "../../hooks/useAuth"
import { RESPONSIBLE_ROLES, STATUS_LABELS, MIN_REVIEW_REASON, approveNeedsReason, approveBlocker, describeSpan } from "./obligationRules"
import { labelOf } from "./profileRules"
import { CATEGORIES, PRICING_MODELS, BILLING_TRIGGERS, ITEM_KINDS, ITEM_HEADINGS, missingForReview, saveProblem, itemProblem, toBody, type ServiceDraft, type ServiceItem } from "./serviceRules"

interface Version {
  id: string; versionNumber: number; reviewStatus: string; name: string | null; category: string | null; description: string | null
  applicabilityNote: string | null; exclusions: string | null; estimatedHours: number | null; slaDays: number | null; responsibleRole: string | null
  requiresReview: boolean; pricingModel: string | null; billingTrigger: string | null; changeSummary: string | null; authoredBy: string | null
  submittedBy: string | null; reviewedBy: string | null; reviewNote: string | null; reviewExceptionReason: string | null
  effectiveFrom: string | null; effectiveTo: string | null; inForce: boolean; items: ServiceItem[]
}
interface Detail { id: string; code: string; versions: Version[] }
interface Summary { id: string; code: string; name: string | null; category: string | null; inForceVersion: number | null; latestVersion: number; latestStatus: string | null; pricingModel: string | null; estimatedHours: number | null }

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

const toDraft = (v: Version): ServiceDraft => ({
  name: v.name ?? "", category: v.category ?? "", description: v.description ?? "", applicabilityNote: v.applicabilityNote ?? "", exclusions: v.exclusions ?? "",
  estimatedHours: v.estimatedHours?.toString() ?? "", slaDays: v.slaDays?.toString() ?? "", responsibleRole: v.responsibleRole ?? "", requiresReview: v.requiresReview,
  pricingModel: v.pricingModel ?? "", billingTrigger: v.billingTrigger ?? "", changeSummary: v.changeSummary ?? "", items: v.items,
})

export default function ServiceCatalogue() {
  const qc = useQueryClient()
  const canManage = usePermissions(["COMPLIANCE_SERVICES_MANAGE", "COMPLIANCE_SERVICES_ADMIN"])
  const canApprove = usePermission("COMPLIANCE_SERVICES_ADMIN")
  const { user } = useAuth()
  const base = "/api/v1/compliance-services/service-catalogue"
  const [selected, setSelected] = useState<string | null>(null)
  const [newCode, setNewCode] = useState("")
  const [error, setError] = useState("")

  const { data: list = [] } = useQuery<Summary[]>({ queryKey: ["cs-services"], queryFn: async () => unwrap<Summary[]>(await apiClient.get(base)) })
  const create = useMutation({
    mutationFn: async (code: string) => unwrap<Detail>(await apiClient.post(base, { code, version: null })),
    onSuccess: (d: Detail) => { setError(""); setNewCode(""); setSelected(d.id); qc.invalidateQueries({ queryKey: ["cs-services"] }) },
    onError: (e: unknown) => setError(errText(e)),
  })

  return (
    <div>
      <div style={{ fontSize: 13, color: "var(--hf-text-muted)", marginBottom: 14 }}>
        The services your practice sells. Each is written by a person, reviewed by another, and can be sold only from the date an approved version starts. Prices are set separately.
      </div>
      {error && <div role="alert" style={{ ...bad, fontSize: 12, marginBottom: 8 }}>{error}</div>}
      <div style={{ display: "grid", gap: 8 }}>
        {list.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>No services yet.</div>}
        {list.map(s => (
          <div key={s.id}>
            <button onClick={() => setSelected(selected === s.id ? null : s.id)} style={{ ...card, width: "100%", textAlign: "left", background: "transparent", cursor: "pointer", display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
              <span style={{ fontWeight: 700, fontSize: 13, minWidth: 140, color: "var(--hf-text)" }}>{s.code}</span>
              <span style={{ fontSize: 13, color: "var(--hf-text)" }}>{s.name ?? "Untitled"}</span>
              <span style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>{s.category ? labelOf(CATEGORIES, s.category) : ""}</span>
              <span style={{ flex: 1 }} />
              <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
                {s.inForceVersion ? `Version ${s.inForceVersion} on sale` : "Not on sale"}
                {s.latestStatus && s.latestStatus !== "APPROVED" ? ` · version ${s.latestVersion} ${STATUS_LABELS[s.latestStatus].toLowerCase()}` : ""}
              </span>
            </button>
            {selected === s.id && <ServiceDetail id={s.id} canManage={canManage} canApprove={canApprove} userId={user?.userId} />}
          </div>
        ))}
      </div>
      {canManage && (
        <div style={{ display: "flex", gap: 8, marginTop: 14, maxWidth: 420 }}>
          <input style={input} aria-label="New service code" placeholder="New service code, e.g. CS-CIPC-AR" value={newCode} onChange={e => setNewCode(e.target.value)} />
          <button style={{ ...button, opacity: !newCode.trim() || create.isPending ? 0.5 : 1 }} disabled={!newCode.trim() || create.isPending} onClick={() => create.mutate(newCode)}>Add</button>
        </div>
      )}
    </div>
  )
}

function ServiceDetail({ id, canManage, canApprove, userId }: { id: string; canManage: boolean; canApprove: boolean; userId: string | null | undefined }) {
  const qc = useQueryClient()
  const base = `/api/v1/compliance-services/service-catalogue/${id}`
  const { data } = useQuery<Detail>({ queryKey: ["cs-service", id], queryFn: async () => unwrap<Detail>(await apiClient.get(base)) })
  const [draft, setDraft] = useState<ServiceDraft | null>(null)
  const [draftFor, setDraftFor] = useState<string | null>(null)
  const [error, setError] = useState("")
  const [from, setFrom] = useState("")
  const [reason, setReason] = useState("")
  const [sendBack, setSendBack] = useState("")

  const done = (d: Detail) => { setError(""); setDraft(null); setDraftFor(null); setReason(""); setSendBack(""); qc.setQueryData(["cs-service", id], d); qc.invalidateQueries({ queryKey: ["cs-services"] }) }
  const fail = (e: unknown) => setError(errText(e))
  const save = useMutation({ mutationFn: async (v: { versionId: string; d: ServiceDraft }) => unwrap<Detail>(await apiClient.put(`${base}/versions/${v.versionId}`, toBody(v.d))), onSuccess: done, onError: fail })
  const post = useMutation({ mutationFn: async (v: { path: string; payload?: object }) => unwrap<Detail>(await apiClient.post(`${base}${v.path}`, v.payload ?? {})), onSuccess: done, onError: fail })

  if (!data) return <div style={{ padding: 12, color: "var(--hf-text-faint)" }}>Loading...</div>
  const versions = [...data.versions].sort((a, b) => b.versionNumber - a.versionNumber)
  const open = versions.find(v => v.reviewStatus !== "APPROVED")
  const hasOpenEnded = versions.some(v => v.reviewStatus === "APPROVED" && !v.effectiveTo)

  return (
    <div style={{ margin: "8px 0 14px 14px", display: "grid", gap: 10 }}>
      {error && <div role="alert" style={{ ...bad, fontSize: 12 }}>{error}</div>}
      {versions.map(v => {
        const editing = draftFor === v.id && draft
        const needsReason = approveNeedsReason(v, userId)
        const approveProblem = approveBlocker(needsReason, reason, from)
        return (
          <div key={v.id} style={card}>
            <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
              <strong style={{ fontSize: 13 }}>Version {v.versionNumber}</strong>
              <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{STATUS_LABELS[v.reviewStatus]}{v.inForce ? " · on sale today" : ""}</span>
              {v.reviewStatus === "APPROVED" && <span style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>{describeSpan(v.effectiveFrom, v.effectiveTo)}</span>}
              <span style={{ flex: 1 }} />
              {canManage && v.reviewStatus === "DRAFT" && !editing && <button style={ghost} onClick={() => { setDraft(toDraft(v)); setDraftFor(v.id) }}>Edit</button>}
              {canManage && v.reviewStatus === "DRAFT" && !editing && <button style={ghost} disabled={post.isPending} onClick={() => post.mutate({ path: `/versions/${v.id}/submit` })}>Send for review</button>}
            </div>
            {v.reviewNote && v.reviewStatus === "DRAFT" && <div style={{ fontSize: 12, color: "var(--hf-warning-text, #8a5a00)", marginTop: 6 }}>Sent back: {v.reviewNote}</div>}

            {!editing && (
              <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 8, display: "grid", gap: 3 }}>
                <div><strong>{v.name ?? "Untitled"}</strong>{v.category ? ` · ${labelOf(CATEGORIES, v.category)}` : ""}</div>
                <div>
                  {labelOf(PRICING_MODELS, v.pricingModel)} · billed {labelOf(BILLING_TRIGGERS, v.billingTrigger).toLowerCase()} · {v.estimatedHours ?? "?"} hours
                  {v.slaDays ? ` · ${v.slaDays} days turnaround` : ""} · {labelOf(RESPONSIBLE_ROLES, v.responsibleRole)}{v.requiresReview ? " · reviewed before delivery" : ""}
                </div>
                {ITEM_KINDS.map(k => {
                  const rows = v.items.filter(i => i.kind === k[0])
                  return rows.length > 0 ? <div key={k[0]}>{ITEM_HEADINGS[k[0]]}: {rows.map(i => `${i.text}${i.mandatory ? "" : " (optional)"}`).join("; ")}</div> : null
                })}
                {v.applicabilityNote && <div>Suits: {v.applicabilityNote}</div>}
                {v.exclusions && <div>Not included: {v.exclusions}</div>}
                {v.changeSummary && <div>Changed: {v.changeSummary}</div>}
                {v.reviewExceptionReason && <div>Approved by the person who wrote it. Reason: {v.reviewExceptionReason}</div>}
              </div>
            )}

            {editing && draft && <Editor d={draft} onChange={setDraft} versionNumber={v.versionNumber}
              onSave={() => save.mutate({ versionId: v.id, d: draft })} onCancel={() => { setDraft(null); setDraftFor(null) }} saving={save.isPending} />}

            {canApprove && v.reviewStatus === "IN_REVIEW" && (
              <div style={{ display: "grid", gap: 8, marginTop: 10 }}>
                <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 8 }}>
                  <div><label style={lab}>On sale from</label><input style={input} type="date" aria-label="On sale from" value={from} onChange={e => setFrom(e.target.value)} /></div>
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
        {canManage && !open && versions.length > 0 && <button style={ghost} disabled={post.isPending} onClick={() => post.mutate({ path: "/versions" })}>Start a new version</button>}
        {canApprove && hasOpenEnded && <button style={ghost} disabled={post.isPending} onClick={() => post.mutate({ path: "/retire", payload: {} })}>Stop selling from today</button>}
      </div>
    </div>
  )
}

function Editor({ d, onChange, versionNumber, onSave, onCancel, saving }: { d: ServiceDraft; onChange: (d: ServiceDraft) => void; versionNumber: number; onSave: () => void; onCancel: () => void; saving: boolean }) {
  const set = (k: keyof ServiceDraft) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => onChange({ ...d, [k]: e.target.value })
  const sel = (k: keyof ServiceDraft, label: string, opts: [string, string][]) => (
    <select style={input} aria-label={label} value={d[k] as string} onChange={set(k)}><option value="">—</option>{opts.map(o => <option key={o[0]} value={o[0]}>{o[1]}</option>)}</select>
  )
  const problem = saveProblem(d)
  const missing = missingForReview(d, versionNumber)
  const setItem = (i: number, it: ServiceItem) => onChange({ ...d, items: d.items.map((x, j) => (j === i ? it : x)) })

  return (
    <div style={{ display: "grid", gap: 10, marginTop: 10 }}>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: 10 }}>
        <div><label style={lab}>Name</label><input style={input} aria-label="Name" value={d.name} onChange={set("name")} /></div>
        <div><label style={lab}>Category</label>{sel("category", "Category", CATEGORIES)}</div>
        <div><label style={lab}>Pricing model</label>{sel("pricingModel", "Pricing model", PRICING_MODELS)}</div>
        <div><label style={lab}>Billed</label>{sel("billingTrigger", "Billing trigger", BILLING_TRIGGERS)}</div>
        <div><label style={lab}>Responsible</label>{sel("responsibleRole", "Responsible", RESPONSIBLE_ROLES)}</div>
        <div><label style={lab}>Estimated hours</label><input style={input} aria-label="Estimated hours" inputMode="decimal" value={d.estimatedHours} onChange={set("estimatedHours")} /></div>
        <div><label style={lab}>Turnaround (days)</label><input style={input} aria-label="Turnaround" inputMode="numeric" value={d.slaDays} onChange={set("slaDays")} /></div>
        <label style={{ fontSize: 12, display: "flex", alignItems: "center", gap: 6, alignSelf: "end", paddingBottom: 8 }}>
          <input type="checkbox" checked={d.requiresReview} onChange={e => onChange({ ...d, requiresReview: e.target.checked })} /> A second person reviews it before delivery
        </label>
      </div>
      <div><label style={lab}>Description</label><textarea style={input} rows={2} value={d.description} onChange={set("description")} /></div>
      <div><label style={lab}>Who it suits</label><textarea style={input} rows={2} value={d.applicabilityNote} onChange={set("applicabilityNote")} /></div>
      <div><label style={lab}>What is not included (for example government fees, or anything only an accredited body can issue)</label><textarea style={input} rows={2} value={d.exclusions} onChange={set("exclusions")} /></div>
      {versionNumber > 1 && <div><label style={lab}>What changed in this version</label><textarea style={input} rows={2} value={d.changeSummary} onChange={set("changeSummary")} /></div>}

      <div>
        <label style={lab}>Deliverables, checklist steps and documents needed</label>
        {d.items.map((it, i) => (
          <div key={i} style={{ display: "flex", gap: 6, marginBottom: 6, flexWrap: "wrap" }}>
            <select style={{ ...input, width: 200 }} aria-label="Item kind" value={it.kind} onChange={e => setItem(i, { ...it, kind: e.target.value })}>{ITEM_KINDS.map(k => <option key={k[0]} value={k[0]}>{k[1]}</option>)}</select>
            <input style={{ ...input, flex: 1, minWidth: 200 }} aria-label="Item text" placeholder="Describe it" value={it.text} onChange={e => setItem(i, { ...it, text: e.target.value })} />
            <label style={{ fontSize: 12, display: "flex", alignItems: "center", gap: 4 }}>
              <input type="checkbox" checked={it.mandatory} onChange={e => setItem(i, { ...it, mandatory: e.target.checked })} /> Required
            </label>
            <button style={ghost} onClick={() => onChange({ ...d, items: d.items.filter((_, j) => j !== i) })}>Remove</button>
            {it.text && itemProblem(it) && <span style={bad}>{itemProblem(it)}</span>}
          </div>
        ))}
        <button style={ghost} onClick={() => onChange({ ...d, items: [...d.items, { kind: "DELIVERABLE", text: "", mandatory: true }] })}>Add item</button>
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
