// src/pages/complianceservices/ClientAssessmentsTab.tsx
//
// Judging a client's obligations on one date. Documents are checked by the system, but only a person can say a
// control is really in place. Completing freezes the result, the weights and the date.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission, usePermissions } from "../../hooks/usePermission"
import { RefreshCw } from "lucide-react"
import { isProblem } from "./caseRules"
import { CONTROLS, WORKFLOWS, statusLabel, statusTone, controlLabel, judgementProblem, weightsProblem, scoreText, changeText, type Change, type JudgementDraft } from "./assessmentRules"

interface Evidence { documentType: string; mandatory: boolean; result: string; detail: string | null; expiresOn: string | null }
interface Item {
  obligationId: string; code: string; name: string | null; versionNumber: number; riskLevel: string | null; applicability: string; evidenceStatus: string
  control: string; workflow: string; status: string; displayStatus: string; note: string | null; evidence: Evidence[]
}
interface Weights { low: number; medium: number; high: number; critical: number; isDefault: boolean }
interface Assessment {
  id: string; status: string; asOf: string; weights: Weights; score: { percent: number | null; scored: number; unscored: number; provisional: boolean }
  summaryNote: string | null; items: Item[]; completionBlockers: string[]; comparedWith: string | null; changes: Change[]
}
interface Summary { id: string; status: string; asOf: string; scorePercent: number | null; scoredCount: number | null; unscoredCount: number | null }

const ACCENT = "var(--hf-success-solid-strong)"
const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T
const errText = (e: unknown): string => {
  const x = e as { response?: { data?: { message?: string } }; message?: string }
  return x?.response?.data?.message ?? x?.message ?? "Something went wrong"
}
const input: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)", boxSizing: "border-box" }
const button: React.CSSProperties = { padding: "8px 14px", border: "none", borderRadius: 8, background: ACCENT, color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const ghost: React.CSSProperties = { padding: "5px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "transparent", color: "var(--hf-text-muted)", fontSize: 12, cursor: "pointer" }
const TONE: Record<string, string> = { good: "var(--hf-success-text, #1e7e34)", warn: "var(--hf-warning-text, #8a5a00)", bad: "var(--hf-danger-text, #c0392b)", neutral: "var(--hf-text-muted)" }

export default function ClientAssessmentsTab({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["COMPLIANCE_SERVICES_MANAGE", "COMPLIANCE_SERVICES_ADMIN"])
  const canAdmin = usePermission("COMPLIANCE_SERVICES_ADMIN")
  const base = `/api/v1/compliance-services/clients/${clientId}/assessments`
  const [selected, setSelected] = useState<string | null>(null)
  const [open, setOpen] = useState<string | null>(null)
  const [j, setJ] = useState<JudgementDraft>({ control: "UNKNOWN", workflow: "NONE", note: "" })
  const [summary, setSummary] = useState("")
  const [error, setError] = useState("")
  const [editWeights, setEditWeights] = useState(false)
  const [w, setW] = useState({ low: 1, medium: 2, high: 4, critical: 8 })

  const { data: list = [] } = useQuery<Summary[]>({ queryKey: ["cs-assessments", clientId], queryFn: async () => unwrap<Summary[]>(await apiClient.get(base)) })
  const current = selected ?? list.find(a => a.status === "DRAFT")?.id ?? list[0]?.id ?? null
  const { data: a } = useQuery<Assessment>({ queryKey: ["cs-assessment", clientId, current], enabled: !!current, queryFn: async () => unwrap<Assessment>(await apiClient.get(`${base}/${current}`)) })
  const { data: weights } = useQuery<Weights>({ queryKey: ["cs-assessment-weights"], queryFn: async () => unwrap<Weights>(await apiClient.get("/api/v1/compliance-services/assessment-weights")) })

  const got = (r: Assessment) => { setError(""); setOpen(null); setSelected(r.id); qc.setQueryData(["cs-assessment", clientId, r.id], r); qc.invalidateQueries({ queryKey: ["cs-assessments", clientId] }) }
  const fail = (e: unknown) => setError(errText(e))
  const start = useMutation({ mutationFn: async () => unwrap<Assessment>(await apiClient.post(base, {})), onSuccess: got, onError: fail })
  const recheck = useMutation({ mutationFn: async () => unwrap<Assessment>(await apiClient.post(`${base}/${a?.id}/recheck`, {})), onSuccess: got, onError: fail })
  const judge = useMutation({
    mutationFn: async (id: string) => unwrap<Assessment>(await apiClient.put(`${base}/${a?.id}/items/${id}`, { control: j.control, workflow: j.workflow, note: j.note || null })),
    onSuccess: got, onError: fail,
  })
  const [caseFor, setCaseFor] = useState<string | null>(null)
  const [caseNote, setCaseNote] = useState("")
  const openCase = useMutation({
    mutationFn: async (obligationId: string) => unwrap<{ summary: { title: string } }>(await apiClient.post(`/api/v1/compliance-services/clients/${clientId}/cases`, { assessmentId: a?.id, obligationId })),
    onSuccess: r => { setError(""); setCaseNote(`Case opened: ${r.summary.title}. Find it on the Cases tab.`); qc.invalidateQueries({ queryKey: ["cs-cases", clientId] }); qc.invalidateQueries({ queryKey: ["cs-work-queue"] }) },
    onError: fail,
  })
  const complete = useMutation({ mutationFn: async () => unwrap<Assessment>(await apiClient.post(`${base}/${a?.id}/complete`, { summaryNote: summary || null })), onSuccess: got, onError: fail })
  const saveWeights = useMutation({
    mutationFn: async () => unwrap<Weights>(await apiClient.put("/api/v1/compliance-services/assessment-weights", w)),
    onSuccess: () => { setError(""); setEditWeights(false); qc.invalidateQueries({ queryKey: ["cs-assessment-weights"] }) }, onError: fail,
  })

  const draft = a?.status === "DRAFT"
  const wProblem = weightsProblem(w)

  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", gap: 12, flexWrap: "wrap", marginBottom: 12 }}>
        <select style={{ ...input, maxWidth: 280 }} aria-label="Assessment" value={current ?? ""} onChange={e => setSelected(e.target.value || null)} disabled={list.length === 0}>
          {list.length === 0 && <option value="">No assessments yet</option>}
          {list.map(s => <option key={s.id} value={s.id}>{s.asOf} · {s.status === "DRAFT" ? "in progress" : s.scorePercent === null ? "completed" : `${s.scorePercent.toFixed(1)}%`}</option>)}
        </select>
        <span style={{ flex: 1 }} />
        {canManage && !list.some(s => s.status === "DRAFT") && <button style={button} disabled={start.isPending} onClick={() => start.mutate()}>{start.isPending ? "Working..." : "Start assessment"}</button>}
        {canManage && draft && <button style={{ ...ghost, display: "inline-flex", alignItems: "center", gap: 6 }} disabled={recheck.isPending} onClick={() => recheck.mutate()}><RefreshCw size={12} /> Check documents again</button>}
      </div>
      {error && <div role="alert" style={{ fontSize: 12, color: TONE.bad, marginBottom: 8 }}>{error}</div>}

      {a && (
        <>
          <div style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: "10px 12px", marginBottom: 12 }}>
            <div style={{ fontSize: 14, fontWeight: 600 }}>{scoreText(a.score.percent, a.score.scored, a.score.unscored, a.score.provisional)}</div>
            <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 2 }}>
              As at {a.asOf} · weights low {a.weights.low}, medium {a.weights.medium}, high {a.weights.high}, critical {a.weights.critical}
            </div>
            {a.summaryNote && <div style={{ fontSize: 12, marginTop: 6 }}>{a.summaryNote}</div>}
          </div>

          {a.changes.length > 0 && (
            <div style={{ marginBottom: 12 }}>
              <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 4 }}>Since the last completed assessment</div>
              <div style={{ fontSize: 12, color: "var(--hf-text-muted)", display: "grid", gap: 2 }}>{a.changes.map(c => <div key={c.code}>{changeText(c)}</div>)}</div>
            </div>
          )}

          <div style={{ display: "grid", gap: 8 }}>
            {a.items.map(i => {
              const problem = judgementProblem(j)
              return (
                <div key={i.obligationId} style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: "10px 12px" }}>
                  <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
                    <strong style={{ fontSize: 13, minWidth: 140 }}>{i.code}</strong>
                    <span style={{ fontSize: 13 }}>{i.name}</span>
                    {i.riskLevel && <span style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{i.riskLevel.toLowerCase()} risk</span>}
                    <span style={{ flex: 1 }} />
                    <span style={{ fontSize: 12, fontWeight: 600, color: TONE[statusTone(i.displayStatus)] }}>{statusLabel(i.displayStatus)}</span>
                    {draft && canManage && i.applicability === "APPLICABLE" && (
                      <button style={ghost} onClick={() => { setOpen(open === i.obligationId ? null : i.obligationId); setJ({ control: i.control, workflow: i.workflow, note: i.note ?? "" }) }}>Judge</button>
                    )}
                    {canManage && isProblem(i.status) && (
                      <button style={ghost} disabled={openCase.isPending} onClick={() => { setCaseFor(i.obligationId); setCaseNote(""); openCase.mutate(i.obligationId) }}>Open case</button>
                    )}
                  </div>
                  {caseFor === i.obligationId && caseNote && <div style={{ fontSize: 12, color: "var(--hf-success-text, #1e7e34)", marginTop: 4 }}>{caseNote}</div>}
                  <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 4 }}>
                    {i.applicability !== "APPLICABLE" ? (i.applicability === "PENDING" ? "Not yet decided whether this applies" : "Does not apply to this client") : `Control: ${controlLabel(i.control)}`}
                  </div>
                  {i.evidence.map(e => (
                    <div key={e.documentType} style={{ fontSize: 12, color: e.result === "MET" ? "var(--hf-text-muted)" : TONE.bad, marginTop: 2 }}>
                      {e.documentType}{e.mandatory ? "" : " (optional)"}: {e.detail ?? e.result.toLowerCase()}
                    </div>
                  ))}
                  {i.note && <div style={{ fontSize: 12, marginTop: 4 }}>Note: {i.note}</div>}

                  {open === i.obligationId && (
                    <div style={{ display: "grid", gap: 8, marginTop: 10 }}>
                      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: 8 }}>
                        <select style={input} aria-label="Control" value={j.control} onChange={e => setJ({ ...j, control: e.target.value })}>{CONTROLS.map(c => <option key={c[0]} value={c[0]}>{c[1]}</option>)}</select>
                        <select style={input} aria-label="Under way" value={j.workflow} onChange={e => setJ({ ...j, workflow: e.target.value })}>{WORKFLOWS.map(c => <option key={c[0]} value={c[0]}>{c[1]}</option>)}</select>
                      </div>
                      <textarea style={input} rows={2} placeholder="Note: what is missing, or what is being done" value={j.note} onChange={e => setJ({ ...j, note: e.target.value })} />
                      {problem && <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{problem}</div>}
                      <div><button style={{ ...button, opacity: problem || judge.isPending ? 0.5 : 1 }} disabled={!!problem || judge.isPending} onClick={() => judge.mutate(i.obligationId)}>Save judgement</button></div>
                    </div>
                  )}
                </div>
              )
            })}
          </div>

          {draft && canManage && (
            <div style={{ marginTop: 14, display: "grid", gap: 8 }}>
              {a.completionBlockers.length > 0 && <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>Still to judge, or explain: {a.completionBlockers.join(", ")}</div>}
              <textarea style={input} rows={2} placeholder="Summary for the record (optional)" value={summary} onChange={e => setSummary(e.target.value)} />
              <div><button style={{ ...button, opacity: a.completionBlockers.length > 0 || complete.isPending ? 0.5 : 1 }} disabled={a.completionBlockers.length > 0 || complete.isPending} onClick={() => complete.mutate()}>Complete assessment</button></div>
            </div>
          )}
        </>
      )}

      {canAdmin && weights && (
        <div style={{ marginTop: 20, borderTop: "1px solid var(--hf-border)", paddingTop: 12 }}>
          <div style={{ display: "flex", gap: 10, alignItems: "center" }}>
            <span style={{ fontSize: 12, color: "var(--hf-text-muted)", flex: 1 }}>
              Scoring weights: low {weights.low}, medium {weights.medium}, high {weights.high}, critical {weights.critical}{weights.isDefault ? " (defaults)" : ""}. Changes apply to assessments started afterwards.
            </span>
            <button style={ghost} onClick={() => { setEditWeights(!editWeights); setW({ low: weights.low, medium: weights.medium, high: weights.high, critical: weights.critical }) }}>Change weights</button>
          </div>
          {editWeights && (
            <div style={{ display: "flex", gap: 8, marginTop: 8, flexWrap: "wrap", alignItems: "center" }}>
              {(["low", "medium", "high", "critical"] as const).map(k => (
                <label key={k} style={{ fontSize: 12, display: "grid", gap: 2 }}>{k}
                  <input style={{ ...input, width: 80 }} type="number" value={w[k]} onChange={e => setW({ ...w, [k]: Number(e.target.value) })} />
                </label>
              ))}
              <button style={{ ...button, opacity: wProblem || saveWeights.isPending ? 0.5 : 1 }} disabled={!!wProblem || saveWeights.isPending} onClick={() => saveWeights.mutate()}>Save weights</button>
              {wProblem && <span style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{wProblem}</span>}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
