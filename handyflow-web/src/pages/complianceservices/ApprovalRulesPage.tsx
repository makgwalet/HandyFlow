// src/pages/complianceservices/ApprovalRulesPage.tsx
//
// Rules that make a quote need a more senior approver, on top of the discount limits. A rule can only raise who approves, never lower it.
// One administrator proposes a rule and a different one activates it. Rules are retired with a reason, never deleted. No limits are supplied.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { APPROVERS, approverLabel, emptyForm, formProblem, noteProblem, RISKS, statusLabel, toBody, when, type ApprovalRule, type RuleForm } from "./approvalRuleRules"

const B = "/api/v1/compliance-services/approval-rules"
const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T
const errText = (e: unknown): string => {
  const x = e as { response?: { data?: { message?: string } }; message?: string }
  return x?.response?.data?.message ?? x?.message ?? "Something went wrong"
}
const input: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)", boxSizing: "border-box" }
const solid: React.CSSProperties = { padding: "8px 14px", border: "none", borderRadius: 8, background: "var(--hf-success-solid-strong)", color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const ghost: React.CSSProperties = { padding: "6px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "transparent", color: "var(--hf-text-muted)", fontSize: 12, cursor: "pointer" }
const card: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 10, padding: 12 }

export default function ApprovalRulesPage() {
  const qc = useQueryClient()
  const isAdmin = usePermissions(["COMPLIANCE_SERVICES_ADMIN"])
  const [adding, setAdding] = useState(false)
  const [form, setForm] = useState<RuleForm>(emptyForm)
  const [retiring, setRetiring] = useState<string | null>(null)
  const [note, setNote] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const { data = [], isLoading } = useQuery<ApprovalRule[]>({ queryKey: ["cs-approval-rules"], queryFn: async () => unwrap<ApprovalRule[]>(await apiClient.get(B)) })
  const { data: clients = [] } = useQuery<{ id: string; name: string }[]>({
    queryKey: ["cs-rule-clients"], enabled: isAdmin,
    queryFn: async () => { const d = unwrap<{ id: string; name: string }[] | { content?: { id: string; name: string }[] }>(await apiClient.get("/api/v1/compliance-services/clients?size=200")); return Array.isArray(d) ? d : d?.content ?? [] },
  })
  const set = <K extends keyof RuleForm>(k: K, v: RuleForm[K]) => setForm(f => ({ ...f, [k]: v }))
  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true); setError("")
    try { await fn(); setAdding(false); setForm(emptyForm); setRetiring(null); setNote(""); qc.invalidateQueries({ queryKey: ["cs-approval-rules"] }) }
    catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  const save = () => { const p = formProblem(form); if (p) { setError(p); return } run(() => apiClient.post(B, toBody(form))) }
  const retire = (id: string) => { const p = noteProblem(note); if (p) { setError(p); return } run(() => apiClient.post(`${B}/${id}/retire`, { note: note.trim() })) }

  return (
    <div style={{ display: "grid", gap: 12 }}>
      <div><h2 style={{ margin: 0, fontSize: 18 }}>Approval rules</h2>
        <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Rules that make a quote need a more senior approver, on top of the discount limits. A rule can only raise who approves, never lower it. A rule you propose takes effect when a different administrator activates it. No limits are set for you: the practice decides its own.</div></div>
      {isAdmin && !adding && <div><button style={solid} onClick={() => { setAdding(true); setError("") }}>Propose a rule</button></div>}
      {adding && (
        <div style={{ ...card, display: "grid", gap: 8, background: "var(--hf-surface-muted)" }}>
          <input aria-label="Rule name" style={input} placeholder="Rule name" value={form.name} onChange={e => set("name", e.target.value)} />
          <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>The rule applies when everything you fill in below is true. Fill in at least one.</div>
          <input aria-label="Professional fees at or above" style={input} inputMode="decimal" placeholder="Professional fees at or above (R, before VAT)" value={form.minFees} onChange={e => set("minFees", e.target.value)} />
          <input aria-label="Service code" style={input} placeholder="Quote includes this service code" value={form.serviceCode} onChange={e => set("serviceCode", e.target.value)} />
          <select aria-label="Client risk rating" style={input} value={form.riskRating} onChange={e => set("riskRating", e.target.value)}>
            <option value="">Any client risk rating</option>{Object.entries(RISKS).map(([k, l]) => <option key={k} value={k}>{l}</option>)}
          </select>
          <select aria-label="Client" style={input} value={form.clientId} onChange={e => set("clientId", e.target.value)}>
            <option value="">Any client</option>{clients.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
          <select aria-label="Who must approve" style={input} value={form.requiredApprover} onChange={e => set("requiredApprover", e.target.value)}>
            {Object.entries(APPROVERS).map(([k, l]) => <option key={k} value={k}>{l}</option>)}
          </select>
          <textarea aria-label="Why" style={{ ...input, minHeight: 50 }} placeholder="Why the rule is needed (at least 10 characters)" value={form.reason} onChange={e => set("reason", e.target.value)} />
          {error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{error}</div>}
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={save}>Propose</button><button style={ghost} onClick={() => { setAdding(false); setError("") }}>Close</button></div>
        </div>
      )}
      {!adding && error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{error}</div>}
      {isLoading && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Loading...</div>}
      {!isLoading && data.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>No approval rules have been set. Quotes need approval only because of their discounts.</div>}
      {data.map(r => (
        <div key={r.id} style={{ ...card, display: "grid", gap: 4, fontSize: 13, opacity: r.status === "RETIRED" ? 0.6 : 1 }}>
          <div><strong>{r.name}</strong> · {statusLabel(r.status)}</div>
          <div>When {when(r)}, a {approverLabel(r.requiredApprover).toLowerCase()} must approve the quote.</div>
          <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Why: {r.reason}</div>
          {r.retireNote && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Retired: {r.retireNote}</div>}
          {isAdmin && r.status === "PROPOSED" && <div><button style={solid} disabled={busy} onClick={() => run(() => apiClient.post(`${B}/${r.id}/activate`, {}))}>Activate</button> <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>It must be a different administrator from the one who proposed it.</span></div>}
          {isAdmin && r.status !== "RETIRED" && retiring !== r.id && <div><button style={ghost} onClick={() => { setRetiring(r.id); setError("") }}>Retire</button></div>}
          {retiring === r.id && (
            <div style={{ display: "grid", gap: 6 }}>
              <textarea aria-label="Retire reason" style={{ ...input, minHeight: 50 }} placeholder="Why (at least 10 characters)" value={note} onChange={e => setNote(e.target.value)} />
              {error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{error}</div>}
              <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={() => retire(r.id)}>Retire the rule</button><button style={ghost} onClick={() => { setRetiring(null); setError("") }}>Close</button></div>
            </div>
          )}
        </div>
      ))}
    </div>
  )
}
