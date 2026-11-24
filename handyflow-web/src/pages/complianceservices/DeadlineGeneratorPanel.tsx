// src/pages/complianceservices/DeadlineGeneratorPanel.tsx
//
// Works out dated deadlines from the due rules of the obligations that apply to this client. Some rules need a key date
// (financial year end month, date registered); rules that cannot be worked out are listed, never guessed.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { MONTHS, keyDatesProblem, resultText, type Result } from "./deadlineRules"

interface KeyDates { financialYearEndMonth: number | null; registeredOn: string | null }
interface Generation extends Result { from: string; to: string }

const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T
const errText = (e: unknown): string => {
  const x = e as { response?: { data?: { message?: string } }; message?: string }
  return x?.response?.data?.message ?? x?.message ?? "Something went wrong"
}
const input: React.CSSProperties = { padding: "7px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)" }
const button: React.CSSProperties = { padding: "7px 14px", border: "none", borderRadius: 8, background: "var(--hf-success-solid-strong)", color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const ghost: React.CSSProperties = { padding: "7px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "transparent", color: "var(--hf-text-muted)", fontSize: 13, cursor: "pointer" }

export default function DeadlineGeneratorPanel({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["COMPLIANCE_SERVICES_MANAGE", "COMPLIANCE_SERVICES_ADMIN"])
  const base = `/api/v1/compliance-services/clients/${clientId}`
  const [editMonth, setMonth] = useState<string | null>(null)
  const [editRegisteredOn, setRegisteredOn] = useState<string | null>(null)
  const [result, setResult] = useState<Generation | null>(null)
  const [error, setError] = useState("")
  const today = new Date().toISOString().slice(0, 10)

  const { data: keyDates } = useQuery<KeyDates>({ queryKey: ["cs-key-dates", clientId], queryFn: async () => unwrap<KeyDates>(await apiClient.get(`${base}/key-dates`)) })
  const month = editMonth ?? (keyDates?.financialYearEndMonth ? String(keyDates.financialYearEndMonth) : "")
  const registeredOn = editRegisteredOn ?? keyDates?.registeredOn ?? ""

  const save = useMutation({
    mutationFn: async () => unwrap<KeyDates>(await apiClient.put(`${base}/key-dates`, { financialYearEndMonth: month ? Number(month) : null, registeredOn: registeredOn || null })),
    onSuccess: () => { setError(""); setMonth(null); setRegisteredOn(null); qc.invalidateQueries({ queryKey: ["cs-key-dates", clientId] }) }, onError: (e: unknown) => setError(errText(e)),
  })
  const generate = useMutation({
    mutationFn: async () => unwrap<Generation>(await apiClient.post(`${base}/deadlines/generate`, {})),
    onSuccess: r => { setError(""); setResult(r); qc.invalidateQueries({ queryKey: ["cs-deadlines", clientId] }) }, onError: (e: unknown) => setError(errText(e)),
  })
  const problem = keyDatesProblem(month, registeredOn, today)

  return (
    <div style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: "12px 14px", marginBottom: 18 }}>
      <div style={{ fontSize: 13, fontWeight: 700, marginBottom: 8 }}>Dates from obligations</div>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "flex-end" }}>
        <label style={{ fontSize: 12, display: "grid", gap: 3 }}>Financial year ends in
          <select style={input} aria-label="Financial year end month" value={month} disabled={!canManage} onChange={e => setMonth(e.target.value)}>
            <option value="">Not recorded</option>
            {MONTHS.map((m, i) => <option key={m} value={i + 1}>{m}</option>)}
          </select>
        </label>
        <label style={{ fontSize: 12, display: "grid", gap: 3 }}>Registered on
          <input style={input} type="date" aria-label="Registered on" value={registeredOn} disabled={!canManage} onChange={e => setRegisteredOn(e.target.value)} />
        </label>
        {canManage && <button style={{ ...ghost, opacity: problem || save.isPending ? 0.5 : 1 }} disabled={!!problem || save.isPending} onClick={() => save.mutate()}>Save key dates</button>}
        {canManage && <button style={button} disabled={generate.isPending} onClick={() => generate.mutate()}>{generate.isPending ? "Working..." : "Work out deadlines"}</button>}
      </div>
      {problem && <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 6 }}>{problem}</div>}
      {error && <div role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text, #c0392b)", marginTop: 8 }}>{error}</div>}
      {result && (
        <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 8 }}>
          <div>{resultText(result)} (to {result.to}).</div>
          {result.skipped.map(s => <div key={s.code}>{s.code}: {s.reason}</div>)}
        </div>
      )}
    </div>
  )
}
