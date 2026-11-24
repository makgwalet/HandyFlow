// src/pages/complianceservices/ObligationCatalogue.tsx
//
// The obligation catalogue. Every obligation is written by a person, reviewed by another, and approved with a
// start date. An approved version is never edited: a change is a new version, so old work keeps pointing at the
// version it used.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission, usePermissions } from "../../hooks/usePermission"
import { useAuth } from "../../hooks/useAuth"
import {
  FREQUENCIES, RISK_LEVELS, RESPONSIBLE_ROLES, DUE_RULES, CONDITION_FIELDS, STATUS_LABELS, MIN_REVIEW_REASON,
  describeDueRule, dueRuleProblem, describeCondition, operatorsFor, conditionProblem, conditionsClash,
  missingForReview, approveNeedsReason, approveBlocker, describeSpan, type Condition,
} from "./obligationRules"
import { labelOf } from "./profileRules"

interface EvidenceItem { documentType: string; mandatory: boolean }
interface Version {
  id: string; versionNumber: number; reviewStatus: string; name: string | null; description: string | null; legalSource: string | null
  regulator: string | null; jurisdiction: string; frequency: string | null; dueRuleType: string; dueMonth: number | null; dueDay: number | null
  dueOffsetDays: number | null; dueOffsetMonths: number | null; validityMonths: number | null; riskLevel: string | null
  responsibleRole: string | null; guidance: string | null; changeSummary: string | null; authoredBy: string | null; submittedBy: string | null
  reviewedBy: string | null; reviewNote: string | null; reviewExceptionReason: string | null; effectiveFrom: string | null
  effectiveTo: string | null; inForce: boolean; conditions: Condition[]; evidence: EvidenceItem[]
}
interface Detail { id: string; code: string; versions: Version[] }
interface Summary { id: string; code: string; name: string | null; inForceVersion: number | null; latestVersion: number; latestStatus: string | null; riskLevel: string | null; frequency: string | null; regulator: string | null }
type Draft = {
  name: string; description: string; legalSource: string; regulator: string; frequency: string; dueRuleType: string
  dueMonth: string; dueDay: string; dueOffsetDays: string; dueOffsetMonths: string; validityMonths: string
  riskLevel: string; responsibleRole: string; guidance: string; changeSummary: string; conditions: Condition[]; evidence: EvidenceItem[]
}

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

const toDraft = (v: Version): Draft => ({
  name: v.name ?? "", description: v.description ?? "", legalSource: v.legalSource ?? "", regulator: v.regulator ?? "", frequency: v.frequency ?? "",
  dueRuleType: v.dueRuleType, dueMonth: v.dueMonth?.toString() ?? "", dueDay: v.dueDay?.toString() ?? "", dueOffsetDays: v.dueOffsetDays?.toString() ?? "",
  dueOffsetMonths: v.dueOffsetMonths?.toString() ?? "", validityMonths: v.validityMonths?.toString() ?? "", riskLevel: v.riskLevel ?? "",
  responsibleRole: v.responsibleRole ?? "", guidance: v.guidance ?? "", changeSummary: v.changeSummary ?? "", conditions: v.conditions, evidence: v.evidence,
})
const num = (s: string) => (s.trim() === "" ? null : Number(s))
const body = (d: Draft) => ({
  ...d, dueMonth: num(d.dueMonth), dueDay: num(d.dueDay), dueOffsetDays: num(d.dueOffsetDays), dueOffsetMonths: num(d.dueOffsetMonths), validityMonths: num(d.validityMonths),
  frequency: d.frequency || null, riskLevel: d.riskLevel || null, responsibleRole: d.responsibleRole || null,
  conditions: d.conditions.map(c => ({ field: c.field, operator: c.operator, value: c.value })),
})

export default function ObligationCatalogue() {
  const qc = useQueryClient()
  const canManage = usePermissions(["COMPLIANCE_SERVICES_MANAGE", "COMPLIANCE_SERVICES_ADMIN"])
  const canApprove = usePermission("COMPLIANCE_SERVICES_ADMIN")
  const { user } = useAuth()
  const base = "/api/v1/compliance-services/obligations"
  const [selected, setSelected] = useState<string | null>(null)
  const [newCode, setNewCode] = useState("")
  const [error, setError] = useState("")

  const { data: list = [] } = useQuery<Summary[]>({ queryKey: ["cs-obligations"], queryFn: async () => unwrap<Summary[]>(await apiClient.get(base)) })
  const create = useMutation({
    mutationFn: async (code: string) => unwrap<Detail>(await apiClient.post(base, { code, version: null })),
    onSuccess: (d: Detail) => { setError(""); setNewCode(""); setSelected(d.id); qc.invalidateQueries({ queryKey: ["cs-obligations"] }) },
    onError: (e: unknown) => setError(errText(e)),
  })

  return (
    <div>
      <div style={{ fontSize: 13, color: "var(--hf-text-muted)", marginBottom: 14 }}>
        Nothing here is built in. Each obligation is written by a person, reviewed by another, and applies only from the date an approved version starts.
      </div>
      {error && <div role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text, #c0392b)", marginBottom: 8 }}>{error}</div>}
      <div style={{ display: "grid", gap: 8 }}>
        {list.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>No obligations yet.</div>}
        {list.map(o => (
          <div key={o.id}>
            <button onClick={() => setSelected(selected === o.id ? null : o.id)} style={{ ...card, width: "100%", textAlign: "left", background: "transparent", cursor: "pointer", display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
              <span style={{ fontWeight: 700, fontSize: 13, minWidth: 140 }}>{o.code}</span>
              <span style={{ fontSize: 13, color: "var(--hf-text)" }}>{o.name ?? "Untitled"}</span>
              <span style={{ flex: 1 }} />
              <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
                {o.inForceVersion ? `Version ${o.inForceVersion} in force` : "Not in force"}
                {o.latestStatus && o.latestStatus !== "APPROVED" ? ` · version ${o.latestVersion} ${STATUS_LABELS[o.latestStatus].toLowerCase()}` : ""}
              </span>
            </button>
            {selected === o.id && <ObligationDetail id={o.id} canManage={canManage} canApprove={canApprove} userId={user?.userId} />}
          </div>
        ))}
      </div>
      {canManage && (
        <div style={{ display: "flex", gap: 8, marginTop: 14, maxWidth: 420 }}>
          <input style={input} placeholder="New obligation code, e.g. CIPC-ANNUAL-RETURN" value={newCode} onChange={e => setNewCode(e.target.value)} />
          <button style={{ ...button, opacity: !newCode.trim() || create.isPending ? 0.5 : 1 }} disabled={!newCode.trim() || create.isPending} onClick={() => create.mutate(newCode)}>Add</button>
        </div>
      )}
    </div>
  )
}

function ObligationDetail({ id, canManage, canApprove, userId }: { id: string; canManage: boolean; canApprove: boolean; userId: string | null | undefined }) {
  const qc = useQueryClient()
  const base = `/api/v1/compliance-services/obligations/${id}`
  const { data } = useQuery<Detail>({ queryKey: ["cs-obligation", id], queryFn: async () => unwrap<Detail>(await apiClient.get(base)) })
  const [draft, setDraft] = useState<Draft | null>(null)
  const [draftFor, setDraftFor] = useState<string | null>(null)
  const [error, setError] = useState("")
  const [from, setFrom] = useState("")
  const [reason, setReason] = useState("")
  const [sendBack, setSendBack] = useState("")

  const done = (d: Detail) => { setError(""); setDraft(null); setDraftFor(null); setReason(""); setSendBack(""); qc.setQueryData(["cs-obligation", id], d); qc.invalidateQueries({ queryKey: ["cs-obligations"] }) }
  const fail = (e: unknown) => setError(errText(e))
  const save = useMutation({ mutationFn: async (v: { versionId: string; d: Draft }) => unwrap<Detail>(await apiClient.put(`${base}/versions/${v.versionId}`, body(v.d))), onSuccess: done, onError: fail })
  const post = useMutation({ mutationFn: async (v: { path: string; payload?: object }) => unwrap<Detail>(await apiClient.post(`${base}${v.path}`, v.payload ?? {})), onSuccess: done, onError: fail })

  if (!data) return <div style={{ padding: 12, color: "var(--hf-text-faint)" }}>Loading...</div>
  const versions = [...data.versions].sort((a, b) => b.versionNumber - a.versionNumber)
  const open = versions.find(v => v.reviewStatus !== "APPROVED")
  const hasOpenEnded = versions.some(v => v.reviewStatus === "APPROVED" && !v.effectiveTo)

  return (
    <div style={{ margin: "8px 0 14px 14px", display: "grid", gap: 10 }}>
      {error && <div role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text, #c0392b)" }}>{error}</div>}
      {versions.map(v => {
        const editing = draftFor === v.id && draft
        const needsReason = approveNeedsReason(v, userId)
        const approveProblem = approveBlocker(needsReason, reason, from)
        return (
          <div key={v.id} style={card}>
            <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
              <strong style={{ fontSize: 13 }}>Version {v.versionNumber}</strong>
              <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{STATUS_LABELS[v.reviewStatus]}{v.inForce ? " · in force today" : ""}</span>
              {v.reviewStatus === "APPROVED" && <span style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>{describeSpan(v.effectiveFrom, v.effectiveTo)}</span>}
              <span style={{ flex: 1 }} />
              {canManage && v.reviewStatus === "DRAFT" && !editing && <button style={ghost} onClick={() => { setDraft(toDraft(v)); setDraftFor(v.id) }}>Edit</button>}
              {canManage && v.reviewStatus === "DRAFT" && !editing && <button style={ghost} disabled={post.isPending} onClick={() => post.mutate({ path: `/versions/${v.id}/submit` })}>Send for review</button>}
            </div>
            {v.reviewNote && v.reviewStatus === "DRAFT" && <div style={{ fontSize: 12, color: "var(--hf-warning-text, #8a5a00)", marginTop: 6 }}>Sent back: {v.reviewNote}</div>}

            {!editing && (
              <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 8, display: "grid", gap: 3 }}>
                <div><strong>{v.name ?? "Untitled"}</strong>{v.regulator ? ` · ${v.regulator}` : ""}{v.legalSource ? ` · ${v.legalSource}` : ""}</div>
                <div>{labelOf(FREQUENCIES, v.frequency)} · {describeDueRule(v)} · Risk {labelOf(RISK_LEVELS, v.riskLevel).toLowerCase()} · {labelOf(RESPONSIBLE_ROLES, v.responsibleRole)}</div>
                {v.conditions.length > 0 ? <div>Applies when: {v.conditions.map(describeCondition).join("; ")}</div> : <div>No conditions: applies to every client it is assigned to.</div>}
                {v.evidence.length > 0 && <div>Evidence: {v.evidence.map(e => `${e.documentType}${e.mandatory ? "" : " (optional)"}`).join(", ")}</div>}
                {v.changeSummary && <div>Changed: {v.changeSummary}</div>}
                {v.reviewExceptionReason && <div>Approved by the person who wrote it. Reason: {v.reviewExceptionReason}</div>}
              </div>
            )}

            {editing && draft && <Editor d={draft} onChange={setDraft} versionNumber={v.versionNumber}
              onSave={() => save.mutate({ versionId: v.id, d: draft })} onCancel={() => { setDraft(null); setDraftFor(null) }} saving={save.isPending} />}

            {canApprove && v.reviewStatus === "IN_REVIEW" && (
              <div style={{ display: "grid", gap: 8, marginTop: 10 }}>
                <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 8 }}>
                  <div><label style={lab}>Takes effect on</label><input style={input} type="date" value={from} onChange={e => setFrom(e.target.value)} /></div>
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
        {canApprove && hasOpenEnded && <button style={ghost} disabled={post.isPending} onClick={() => post.mutate({ path: "/retire", payload: {} })}>Retire from today</button>}
      </div>
    </div>
  )
}

function Editor({ d, onChange, versionNumber, onSave, onCancel, saving }: { d: Draft; onChange: (d: Draft) => void; versionNumber: number; onSave: () => void; onCancel: () => void; saving: boolean }) {
  const set = (k: keyof Draft) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => onChange({ ...d, [k]: e.target.value })
  const sel = (k: keyof Draft, opts: [string, string][], blank = true) => (
    <select style={input} value={d[k] as string} onChange={set(k)}>{blank && <option value="">—</option>}{opts.map(o => <option key={o[0]} value={o[0]}>{o[1]}</option>)}</select>
  )
  const rule = { dueRuleType: d.dueRuleType, dueMonth: num(d.dueMonth), dueDay: num(d.dueDay), dueOffsetDays: num(d.dueOffsetDays), dueOffsetMonths: num(d.dueOffsetMonths) }
  const ruleProblem = dueRuleProblem(rule)
  const clash = conditionsClash(d.conditions)
  const condProblems = d.conditions.map(conditionProblem)
  const blocked = !!ruleProblem || !!clash || condProblems.some(Boolean) || d.evidence.some(e => !e.documentType.trim())
  const missing = missingForReview(d, versionNumber)
  const setCond = (i: number, c: Condition) => onChange({ ...d, conditions: d.conditions.map((x, j) => (j === i ? c : x)) })

  return (
    <div style={{ display: "grid", gap: 10, marginTop: 10 }}>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: 10 }}>
        <div><label style={lab}>Name</label><input style={input} value={d.name} onChange={set("name")} /></div>
        <div><label style={lab}>Legal source (Act and section)</label><input style={input} value={d.legalSource} onChange={set("legalSource")} /></div>
        <div><label style={lab}>Regulator</label><input style={input} value={d.regulator} onChange={set("regulator")} /></div>
        <div><label style={lab}>How often</label>{sel("frequency", FREQUENCIES)}</div>
        <div><label style={lab}>Risk</label>{sel("riskLevel", RISK_LEVELS)}</div>
        <div><label style={lab}>Responsible</label>{sel("responsibleRole", RESPONSIBLE_ROLES)}</div>
        <div><label style={lab}>Valid for (months)</label><input style={input} inputMode="numeric" value={d.validityMonths} onChange={set("validityMonths")} /></div>
        <div><label style={lab}>Due date rule</label>{sel("dueRuleType", DUE_RULES, false)}</div>
        {d.dueRuleType === "ANNUAL_FIXED" && <><div><label style={lab}>Month (1-12)</label><input style={input} inputMode="numeric" value={d.dueMonth} onChange={set("dueMonth")} /></div>
          <div><label style={lab}>Day</label><input style={input} inputMode="numeric" value={d.dueDay} onChange={set("dueDay")} /></div></>}
        {d.dueRuleType === "MONTHLY_DAY" && <div><label style={lab}>Day of month</label><input style={input} inputMode="numeric" value={d.dueDay} onChange={set("dueDay")} /></div>}
        {(d.dueRuleType === "ANNUAL_AFTER_FYE" || d.dueRuleType === "ANNIVERSARY") && <div><label style={lab}>Months</label><input style={input} inputMode="numeric" value={d.dueOffsetMonths} onChange={set("dueOffsetMonths")} /></div>}
        {["QUARTER_END_PLUS", "AFTER_EVENT", "BEFORE_EXPIRY"].includes(d.dueRuleType) && <div><label style={lab}>Days</label><input style={input} inputMode="numeric" value={d.dueOffsetDays} onChange={set("dueOffsetDays")} /></div>}
      </div>
      {ruleProblem && <div style={{ fontSize: 11, color: "var(--hf-danger-text, #c0392b)" }}>{ruleProblem}</div>}
      <div><label style={lab}>Description</label><textarea style={input} rows={2} value={d.description} onChange={set("description")} /></div>
      <div><label style={lab}>Guidance for the consultant</label><textarea style={input} rows={2} value={d.guidance} onChange={set("guidance")} /></div>
      {versionNumber > 1 && <div><label style={lab}>What changed in this version</label><textarea style={input} rows={2} value={d.changeSummary} onChange={set("changeSummary")} /></div>}

      <div>
        <label style={lab}>Applies to a client when all of these are true (none means every client it is assigned to)</label>
        {d.conditions.map((c, i) => (
          <div key={i} style={{ display: "flex", gap: 6, marginBottom: 6, flexWrap: "wrap" }}>
            <select style={{ ...input, width: 150 }} value={c.field} onChange={e => setCond(i, { field: e.target.value, operator: operatorsFor(e.target.value)[0], value: null })}>
              {CONDITION_FIELDS.map(f => <option key={f[0]} value={f[0]}>{f[1]}</option>)}
            </select>
            <select style={{ ...input, width: 110 }} value={c.operator} onChange={e => setCond(i, { ...c, operator: e.target.value })}>
              {operatorsFor(c.field).map(o => <option key={o} value={o}>{o === "IN" ? "is" : o === "GTE" ? "at least" : o === "LTE" ? "at most" : o === "IS_TRUE" ? "yes" : "no"}</option>)}
            </select>
            {c.field !== "VAT_REGISTERED" && <input style={{ ...input, flex: 1, minWidth: 160 }} placeholder={c.field === "ENTITY_TYPE" ? "PTY_LTD, NPC" : c.field === "TURNOVER_BAND" ? "UNDER_1M, 1M_TO_10M" : "value"} value={c.value ?? ""} onChange={e => setCond(i, { ...c, value: e.target.value })} />}
            <button style={ghost} onClick={() => onChange({ ...d, conditions: d.conditions.filter((_, j) => j !== i) })}>Remove</button>
            {condProblems[i] && <span style={{ fontSize: 11, color: "var(--hf-danger-text, #c0392b)" }}>{condProblems[i]}</span>}
          </div>
        ))}
        {clash && <div style={{ fontSize: 11, color: "var(--hf-danger-text, #c0392b)" }}>{clash}</div>}
        <button style={ghost} onClick={() => onChange({ ...d, conditions: [...d.conditions, { field: "ENTITY_TYPE", operator: "IN", value: "" }] })}>Add condition</button>
      </div>

      <div>
        <label style={lab}>Evidence that shows it is met</label>
        {d.evidence.map((e, i) => (
          <div key={i} style={{ display: "flex", gap: 6, marginBottom: 6 }}>
            <input style={{ ...input, flex: 1 }} placeholder="Document type" value={e.documentType} onChange={ev => onChange({ ...d, evidence: d.evidence.map((x, j) => (j === i ? { ...x, documentType: ev.target.value } : x)) })} />
            <label style={{ fontSize: 12, display: "flex", alignItems: "center", gap: 4 }}>
              <input type="checkbox" checked={e.mandatory} onChange={ev => onChange({ ...d, evidence: d.evidence.map((x, j) => (j === i ? { ...x, mandatory: ev.target.checked } : x)) })} /> Required
            </label>
            <button style={ghost} onClick={() => onChange({ ...d, evidence: d.evidence.filter((_, j) => j !== i) })}>Remove</button>
          </div>
        ))}
        <button style={ghost} onClick={() => onChange({ ...d, evidence: [...d.evidence, { documentType: "", mandatory: true }] })}>Add evidence</button>
      </div>

      {missing.length > 0 && <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>Before it can go for review it needs: {missing.join(", ")}.</div>}
      <div style={{ display: "flex", gap: 8 }}>
        <button style={{ ...button, opacity: blocked || saving ? 0.5 : 1 }} disabled={blocked || saving} onClick={onSave}>{saving ? "Saving..." : "Save draft"}</button>
        <button style={ghost} onClick={onCancel}>Cancel</button>
      </div>
    </div>
  )
}
