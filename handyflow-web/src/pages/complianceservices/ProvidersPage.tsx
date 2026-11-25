// src/pages/complianceservices/ProvidersPage.tsx
//
// Outside providers and their accreditations. An accreditation someone typed in is only "waiting to be checked"; it counts once a different person checks it
// and it has not expired. Nothing is deleted: providers are deactivated and accreditations retired, each with a reason.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import ProviderAssignmentList from "./ProviderAssignmentList"
import { accreditationStatus, expiryText, noteProblem, ratingText, type Assignment, type Performance, type ProviderDetail, type ProviderSummary } from "./providerRules"

const B = "/api/v1/compliance-services"
const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T
const errText = (e: unknown): string => {
  const x = e as { response?: { data?: { message?: string } }; message?: string }
  return x?.response?.data?.message ?? x?.message ?? "Something went wrong"
}
const input: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)", boxSizing: "border-box" }
const solid: React.CSSProperties = { padding: "6px 12px", border: "none", borderRadius: 8, background: "var(--hf-success-solid-strong)", color: "#fff", fontSize: 12, fontWeight: 600, cursor: "pointer" }
const ghost: React.CSSProperties = { padding: "6px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "transparent", color: "var(--hf-text-muted)", fontSize: 12, cursor: "pointer" }
const card: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 10, padding: 12 }

function Detail({ id, onBack }: { id: string; onBack: () => void }) {
  const qc = useQueryClient()
  const canManage = usePermissions(["COMPLIANCE_SERVICES_MANAGE", "COMPLIANCE_SERVICES_ADMIN"])
  const [mode, setMode] = useState<null | "claim" | "deactivate" | "services" | { check: string } | { retire: string }>(null)
  const [f, setF] = useState({ body: "", reference: "", issuedOn: "", expiresOn: "", note: "", flag: false, services: "" })
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const { data, refetch } = useQuery<ProviderDetail>({ queryKey: ["cs-provider", id], queryFn: async () => unwrap<ProviderDetail>(await apiClient.get(`${B}/providers/${id}`)) })
  const perf = useQuery<Performance>({ queryKey: ["cs-prov-perf", id], queryFn: async () => unwrap<Performance>(await apiClient.get(`${B}/providers/${id}/performance`)) })
  const work = useQuery<Assignment[]>({ queryKey: ["cs-prov-assign", "provider", id], queryFn: async () => unwrap<Assignment[]>(await apiClient.get(`${B}/providers/${id}/assignments`)) })
  const set = <K extends keyof typeof f>(k: K, v: (typeof f)[K]) => setF(x => ({ ...x, [k]: v }))
  const post = async (fn: () => Promise<unknown>) => {
    setBusy(true); setError("")
    try { await fn(); setMode(null); setF(x => ({ ...x, body: "", reference: "", issuedOn: "", expiresOn: "", note: "", flag: false })); await refetch(); qc.invalidateQueries({ queryKey: ["cs-providers"] }); qc.invalidateQueries({ queryKey: ["cs-work-queue"] }) }
    catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  if (!data) return <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Loading...</div>
  const p = data.provider
  const active = p.status === "ACTIVE"
  const needNote = (then: () => Promise<unknown>) => { const pr = noteProblem(f.note); if (pr) { setError(pr); return }; return post(then) }

  return (
    <div style={{ display: "grid", gap: 12 }}>
      <div><button style={ghost} onClick={onBack}>Back to providers</button></div>
      <div>
        <h2 style={{ margin: 0, fontSize: 18 }}>{p.name}{active ? "" : " (inactive)"}</h2>
        <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>{p.kind === "FIRM" ? "Firm" : "Individual"}{p.email ? ` · ${p.email}` : ""}{p.phone ? ` · ${p.phone}` : ""}{p.coverage ? ` · ${p.coverage}` : ""}</div>
        {data.inactiveReason && <div style={{ fontSize: 12 }}>Deactivated: {data.inactiveReason}</div>}
        <div style={{ fontSize: 13 }}>Services: {p.services.length ? p.services.join(", ") : "none recorded"}</div>
      </div>
      {perf.data && (
        <div style={{ display: "flex", gap: 10, flexWrap: "wrap", fontSize: 13 }}>
          <span style={card}>Finished: <strong>{perf.data.completed}</strong></span>
          <span style={card}>On time: <strong>{perf.data.onTime}</strong></span>
          <span style={card}>Rating: <strong>{ratingText(perf.data.averageRating, perf.data.completed)}</strong></span>
          <span style={card}>Open: <strong>{perf.data.open}</strong></span>
        </div>
      )}
      {canManage && (
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          <button style={solid} onClick={() => { setMode("claim"); setError("") }}>Record an accreditation</button>
          <button style={ghost} onClick={() => { setMode("services"); set("services", p.services.join(", ")); setError("") }}>Edit services</button>
          {active ? <button style={ghost} onClick={() => { setMode("deactivate"); setError("") }}>Deactivate</button> : <button style={ghost} disabled={busy} onClick={() => post(() => apiClient.post(`${B}/providers/${id}/reactivate`))}>Reactivate</button>}
        </div>
      )}
      {mode === "claim" && (
        <div style={{ ...card, display: "grid", gap: 8, background: "var(--hf-surface-muted)" }}>
          <input aria-label="Accrediting body" style={input} placeholder="Accrediting body" value={f.body} onChange={e => set("body", e.target.value)} />
          <input aria-label="Reference number" style={input} placeholder="Reference number" value={f.reference} onChange={e => set("reference", e.target.value)} />
          <label style={{ fontSize: 12 }}>Issued<input aria-label="Issued on" type="date" style={input} value={f.issuedOn} onChange={e => set("issuedOn", e.target.value)} /></label>
          <label style={{ fontSize: 12 }}>Expires<input aria-label="Expires on" type="date" style={input} value={f.expiresOn} onChange={e => set("expiresOn", e.target.value)} /></label>
          <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>It will show as waiting to be checked until someone else checks it.</div>
          <div><button style={solid} disabled={busy} onClick={() => { if (!f.body.trim() || !f.reference.trim()) { setError("Give the accrediting body and the reference"); return } post(() => apiClient.post(`${B}/providers/${id}/accreditations`, { body: f.body.trim(), reference: f.reference.trim(), issuedOn: f.issuedOn || null, expiresOn: f.expiresOn || null })) }}>Save</button></div>
        </div>
      )}
      {mode === "services" && (
        <div style={{ ...card, display: "grid", gap: 8, background: "var(--hf-surface-muted)" }}>
          <input aria-label="Service codes" style={input} placeholder="Service codes, separated by commas" value={f.services} onChange={e => set("services", e.target.value)} />
          <div><button style={solid} disabled={busy} onClick={() => post(() => apiClient.put(`${B}/providers/${id}/services`, { serviceCodes: f.services.split(",").map(s => s.trim()).filter(Boolean) }))}>Save</button></div>
        </div>
      )}
      {mode === "deactivate" && (
        <div style={{ ...card, display: "grid", gap: 8, background: "var(--hf-surface-muted)" }}>
          <textarea aria-label="Reason" style={{ ...input, minHeight: 50 }} placeholder="Reason (at least 10 characters)" value={f.note} onChange={e => set("note", e.target.value)} />
          <div><button style={solid} disabled={busy} onClick={() => needNote(() => apiClient.post(`${B}/providers/${id}/deactivate`, { reason: f.note.trim() }))}>Deactivate</button></div>
        </div>
      )}
      {error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{error}</div>}

      <h3 style={{ margin: 0, fontSize: 15 }}>Accreditations</h3>
      {data.accreditations.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>No accreditation has been recorded.</div>}
      {data.accreditations.map(a => (
        <div key={a.id} style={{ ...card, fontSize: 13, display: "grid", gap: 4 }}>
          <div><strong>{a.body}</strong> · {a.reference} · {accreditationStatus(a.status)}{a.expiresOn ? ` · ${expiryText(a.expiry)} (${a.expiresOn})` : ""}{a.usable ? " · counts for new work" : ""}</div>
          {a.checkNote && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Check note: {a.checkNote}</div>}
          {a.retireNote && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Retired: {a.retireNote}</div>}
          {canManage && a.status === "CLAIMED" && typeof mode !== "string" && mode && "check" in mode && mode.check === a.id && (
            <div style={{ display: "grid", gap: 6 }}>
              <label style={{ fontSize: 12, display: "flex", gap: 6 }}><input type="checkbox" checked={f.flag} onChange={e => set("flag", e.target.checked)} /> The check passed</label>
              <textarea aria-label="Check note" style={{ ...input, minHeight: 50 }} placeholder="What you checked, and where (at least 10 characters)" value={f.note} onChange={e => set("note", e.target.value)} />
              <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>It must be someone other than the person who recorded it.</div>
              <div><button style={solid} disabled={busy} onClick={() => needNote(() => apiClient.post(`${B}/provider-accreditations/${a.id}/check`, { accepted: f.flag, note: f.note.trim() }))}>Save the check</button></div>
            </div>
          )}
          {canManage && mode && typeof mode !== "string" && "retire" in mode && mode.retire === a.id && (
            <div style={{ display: "grid", gap: 6 }}>
              <textarea aria-label="Retire note" style={{ ...input, minHeight: 50 }} placeholder="Why (at least 10 characters)" value={f.note} onChange={e => set("note", e.target.value)} />
              <div><button style={solid} disabled={busy} onClick={() => needNote(() => apiClient.post(`${B}/provider-accreditations/${a.id}/retire`, { note: f.note.trim() }))}>Retire</button></div>
            </div>
          )}
          {canManage && (a.status === "CLAIMED" || a.status === "VERIFIED") && (
            <div style={{ display: "flex", gap: 8 }}>
              {a.status === "CLAIMED" && <button style={ghost} onClick={() => { setMode({ check: a.id }); set("flag", false); setError("") }}>Check it</button>}
              <button style={ghost} onClick={() => { setMode({ retire: a.id }); setError("") }}>Retire</button>
            </div>
          )}
        </div>
      ))}

      <h3 style={{ margin: 0, fontSize: 15 }}>Work given to this provider</h3>
      <ProviderAssignmentList items={work.data ?? []} showClient empty="No work has been given to this provider." />
    </div>
  )
}

export default function ProvidersPage() {
  const qc = useQueryClient()
  const canManage = usePermissions(["COMPLIANCE_SERVICES_MANAGE", "COMPLIANCE_SERVICES_ADMIN"])
  const [open, setOpen] = useState<string | null>(null)
  const [adding, setAdding] = useState(false)
  const [f, setF] = useState({ name: "", kind: "INDIVIDUAL", email: "", phone: "", coverage: "" })
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const { data = [], isLoading } = useQuery<ProviderSummary[]>({ queryKey: ["cs-providers"], queryFn: async () => unwrap<ProviderSummary[]>(await apiClient.get(`${B}/providers`)) })
  if (open) return <Detail id={open} onBack={() => setOpen(null)} />

  const save = async () => {
    if (!f.name.trim()) { setError("Give the provider's name"); return }
    setBusy(true); setError("")
    try {
      const d = unwrap<ProviderDetail>(await apiClient.post(`${B}/providers`, { name: f.name.trim(), kind: f.kind, email: f.email.trim() || null, phone: f.phone.trim() || null, coverage: f.coverage.trim() || null, notes: null }))
      setAdding(false); setF({ name: "", kind: "INDIVIDUAL", email: "", phone: "", coverage: "" }); qc.invalidateQueries({ queryKey: ["cs-providers"] }); if (d?.provider?.id) setOpen(d.provider.id)
    } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  return (
    <div style={{ display: "grid", gap: 12 }}>
      <div><h2 style={{ margin: 0, fontSize: 18 }}>Providers</h2>
        <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Outside people and firms who do work for clients. An accreditation counts only after someone other than the person who recorded it has checked it.</div></div>
      {canManage && !adding && <div><button style={solid} onClick={() => setAdding(true)}>Add a provider</button></div>}
      {adding && (
        <div style={{ ...card, display: "grid", gap: 8, background: "var(--hf-surface-muted)" }}>
          <input aria-label="Name" style={input} placeholder="Name" value={f.name} onChange={e => setF({ ...f, name: e.target.value })} />
          <select aria-label="Kind" style={input} value={f.kind} onChange={e => setF({ ...f, kind: e.target.value })}><option value="INDIVIDUAL">Individual</option><option value="FIRM">Firm</option></select>
          <input aria-label="Email" style={input} placeholder="Email" value={f.email} onChange={e => setF({ ...f, email: e.target.value })} />
          <input aria-label="Phone" style={input} placeholder="Phone" value={f.phone} onChange={e => setF({ ...f, phone: e.target.value })} />
          <input aria-label="Where they work" style={input} placeholder="Where they work" value={f.coverage} onChange={e => setF({ ...f, coverage: e.target.value })} />
          {error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{error}</div>}
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={save}>Save</button><button style={ghost} onClick={() => { setAdding(false); setError("") }}>Close</button></div>
        </div>
      )}
      {isLoading && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Loading...</div>}
      {!isLoading && data.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>No providers have been added yet.</div>}
      {data.map(p => (
        <button key={p.id} onClick={() => setOpen(p.id)} style={{ ...card, textAlign: "left", background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13, cursor: "pointer", opacity: p.status === "ACTIVE" ? 1 : 0.6 }}>
          <strong>{p.name}</strong>{p.status === "ACTIVE" ? "" : " (inactive)"} · {p.kind === "FIRM" ? "Firm" : "Individual"}
          <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
            {p.usableAccreditations} accreditation{p.usableAccreditations === 1 ? "" : "s"} that count{p.unchecked > 0 ? ` · ${p.unchecked} waiting to be checked` : ""}{p.nextExpiry ? ` · next expires ${p.nextExpiry}` : ""}
          </div>
        </button>
      ))}
    </div>
  )
}
