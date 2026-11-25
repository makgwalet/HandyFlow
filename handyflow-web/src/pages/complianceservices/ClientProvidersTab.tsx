// src/pages/complianceservices/ClientProvidersTab.tsx
//
// Work given to outside providers for this client. Work starts only after a conflict declaration is recorded and the provider holds a checked,
// in-date accreditation (or a reason for none is recorded).
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import ProviderAssignmentList from "./ProviderAssignmentList"
import { assignmentProblem, type Assignment, type ProviderSummary } from "./providerRules"

const B = "/api/v1/compliance-services"
const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T
const errText = (e: unknown): string => {
  const x = e as { response?: { data?: { message?: string } }; message?: string }
  return x?.response?.data?.message ?? x?.message ?? "Something went wrong"
}
const input: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)", boxSizing: "border-box" }
const solid: React.CSSProperties = { padding: "8px 14px", border: "none", borderRadius: 8, background: "var(--hf-success-solid-strong)", color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer" }

export default function ClientProvidersTab({ clientId }: { clientId: string }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["COMPLIANCE_SERVICES_MANAGE", "COMPLIANCE_SERVICES_PROVIDERS_MANAGE", "COMPLIANCE_SERVICES_ADMIN"])
  const [adding, setAdding] = useState(false)
  const [f, setF] = useState({ providerId: "", title: "", description: "", dueDate: "", accreditationRequired: true, waiverNote: "" })
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const { data = [], isLoading } = useQuery<Assignment[]>({ queryKey: ["cs-prov-assign", "client", clientId], queryFn: async () => unwrap<Assignment[]>(await apiClient.get(`${B}/clients/${clientId}/provider-assignments`)) })
  const { data: providers = [] } = useQuery<ProviderSummary[]>({ queryKey: ["cs-providers"], queryFn: async () => unwrap<ProviderSummary[]>(await apiClient.get(`${B}/providers`)), enabled: canManage })
  const set = <K extends keyof typeof f>(k: K, v: (typeof f)[K]) => setF(x => ({ ...x, [k]: v }))

  const save = async () => {
    const p = assignmentProblem(f); if (p) { setError(p); return }
    setBusy(true); setError("")
    try {
      await apiClient.post(`${B}/clients/${clientId}/provider-assignments`, { providerId: f.providerId, title: f.title.trim(), description: f.description.trim() || null, dueDate: f.dueDate || null, accreditationRequired: f.accreditationRequired, waiverNote: f.accreditationRequired ? null : f.waiverNote.trim() })
      setAdding(false); setF({ providerId: "", title: "", description: "", dueDate: "", accreditationRequired: true, waiverNote: "" })
      qc.invalidateQueries({ queryKey: ["cs-prov-assign"] })
    } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }

  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Work given to outside providers for this client. Work cannot start until a conflict declaration is recorded and the provider has a checked, in-date accreditation.</div>
      {canManage && !adding && <div><button style={solid} onClick={() => setAdding(true)}>Give work to a provider</button></div>}
      {adding && (
        <div style={{ display: "grid", gap: 8, background: "var(--hf-surface-muted)", borderRadius: 10, padding: 12 }}>
          <select aria-label="Provider" style={input} value={f.providerId} onChange={e => set("providerId", e.target.value)}>
            <option value="">Choose a provider</option>{providers.filter(p => p.status === "ACTIVE").map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
          </select>
          <input aria-label="What the work is" style={input} placeholder="What the work is" value={f.title} onChange={e => set("title", e.target.value)} />
          <textarea aria-label="Details" style={{ ...input, minHeight: 50 }} placeholder="Details (optional)" value={f.description} onChange={e => set("description", e.target.value)} />
          <input aria-label="Due date" type="date" style={input} value={f.dueDate} onChange={e => set("dueDate", e.target.value)} />
          <label style={{ display: "flex", gap: 6, alignItems: "center", fontSize: 13 }}><input type="checkbox" checked={!f.accreditationRequired} onChange={e => set("accreditationRequired", !e.target.checked)} /> This work does not need an accredited provider</label>
          {!f.accreditationRequired && <textarea aria-label="Why no accreditation is needed" style={{ ...input, minHeight: 50 }} placeholder="Why no accreditation is needed" value={f.waiverNote} onChange={e => set("waiverNote", e.target.value)} />}
          {error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{error}</div>}
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={save}>Save</button><button style={{ ...solid, background: "transparent", color: "var(--hf-text-muted)", border: "1px solid var(--hf-border)" }} onClick={() => { setAdding(false); setError("") }}>Close</button></div>
        </div>
      )}
      {isLoading ? <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Loading...</div> : <ProviderAssignmentList items={data} empty="No work has been given to providers for this client." />}
    </div>
  )
}
