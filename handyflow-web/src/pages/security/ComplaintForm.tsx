// src/pages/security/ComplaintForm.tsx
//
// Log or edit a complaint. Used from the Complaints list, a complaint's own page and the guard's Complaints tab
// (which fixes the guard). Guard and site lists load only when the guard is not fixed.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { todayIso } from "./guard360.logic"
import { CATEGORIES, COMPLAINANT_TYPES, SEVERITIES, complaintFormError, type ComplaintDetail } from "./complaints.logic"

const input: React.CSSProperties = { padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text-primary)", width: "100%", boxSizing: "border-box" }
const btn: React.CSSProperties = { display: "inline-flex", alignItems: "center", gap: 6, padding: "8px 12px", border: "1px solid var(--hf-border)", borderRadius: 9, background: "var(--hf-surface)", color: "var(--hf-text-secondary)", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const primary: React.CSSProperties = { ...btn, background: "var(--hf-accent)", color: "var(--hf-text-on-solid)", border: "none" }
const errText = (e: any) => e?.response?.data?.message ?? "That did not work. Please try again."
const list = async (url: string) => { const r = await apiClient.get(url); const p = r.data?.data ?? r.data; return (p?.content ?? p) as any[] }

export default function ComplaintForm({ fixedGuardId, existing, onClose, onSaved }: {
  fixedGuardId?: string; existing?: ComplaintDetail; onClose: () => void; onSaved?: (id: string) => void
}) {
  const qc = useQueryClient()
  const e = existing
  const [f, setF] = useState({
    guardId: fixedGuardId ?? e?.summary.guardId ?? "", siteId: e?.summary.siteId ?? "", occurredOn: e?.summary.occurredOn ?? todayIso(),
    category: e?.summary.category ?? "MISCONDUCT", severity: e?.summary.severity ?? "MEDIUM", description: e?.description ?? "",
    complainantType: e?.complainantType ?? "CLIENT", complainantName: e?.complainantName ?? "", complainantContact: e?.complainantContact ?? "", witnesses: e?.witnesses ?? "",
  })
  const [err, setErr] = useState("")
  const { data: guards = [] } = useQuery({ queryKey: ["guards-pick"], queryFn: () => list("/api/v1/security/guards?size=100"), enabled: !fixedGuardId && !e })
  const { data: sites = [] } = useQuery({ queryKey: ["sites-list"], queryFn: () => list("/api/v1/security/sites?size=100") })

  const save = useMutation({
    mutationFn: () => {
      const body = { ...f, siteId: f.siteId || null, complainantName: f.complainantName.trim() || null, complainantContact: f.complainantContact.trim() || null, witnesses: f.witnesses.trim() || null }
      return e ? apiClient.put(`/api/v1/security/complaints/${e.summary.id}`, body) : apiClient.post("/api/v1/security/complaints", body)
    },
    onSuccess: r => {
      qc.invalidateQueries({ queryKey: ["complaints"] }); qc.invalidateQueries({ queryKey: ["complaint"] }); qc.invalidateQueries({ queryKey: ["guard-overview"] })
      const d = r.data?.data ?? r.data
      onSaved?.(d?.summary?.id); onClose()
    },
    onError: x => setErr(errText(x)),
  })
  const submit = () => { const m = complaintFormError(f, todayIso()); if (m) setErr(m); else { setErr(""); save.mutate() } }
  const set = (k: keyof typeof f) => (ev: React.ChangeEvent<any>) => setF({ ...f, [k]: ev.target.value })

  return (
    <div style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, display: "grid", gap: 10 }}>
      <div style={{ fontWeight: 800 }}>{e ? `Edit ${e.summary.complaintNumber}` : "Log a complaint"}</div>
      {!fixedGuardId && !e && (
        <label style={{ fontSize: 12 }}>Guard
          <select value={f.guardId} onChange={set("guardId")} style={input}>
            <option value="">Choose a guard</option>
            {guards.map((g: any) => <option key={g.id} value={g.id}>{g.fullName ?? `${g.firstName} ${g.lastName}`}</option>)}
          </select></label>
      )}
      <div style={{ display: "grid", gap: 10, gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))" }}>
        <label style={{ fontSize: 12 }}>Date it happened<input type="date" value={f.occurredOn} onChange={set("occurredOn")} style={input} /></label>
        <label style={{ fontSize: 12 }}>Category
          <select value={f.category} onChange={set("category")} style={input}>{CATEGORIES.map(c => <option key={c.value} value={c.value}>{c.label}</option>)}</select></label>
        <label style={{ fontSize: 12 }}>Severity
          <select value={f.severity} onChange={set("severity")} style={input}>{SEVERITIES.map(c => <option key={c.value} value={c.value}>{c.label}</option>)}</select></label>
        <label style={{ fontSize: 12 }}>Site
          <select value={f.siteId} onChange={set("siteId")} style={input}><option value="">Not site-specific</option>{sites.map((s: any) => <option key={s.id} value={s.id}>{s.name}</option>)}</select></label>
      </div>
      <label style={{ fontSize: 12 }}>What happened
        <textarea value={f.description} onChange={set("description")} rows={4} style={{ ...input, resize: "vertical" }} /></label>
      <div style={{ display: "grid", gap: 10, gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))" }}>
        <label style={{ fontSize: 12 }}>Complainant
          <select value={f.complainantType} onChange={set("complainantType")} style={input}>{COMPLAINANT_TYPES.map(c => <option key={c.value} value={c.value}>{c.label}</option>)}</select></label>
        <label style={{ fontSize: 12 }}>Complainant name<input value={f.complainantName} onChange={set("complainantName")} style={input} /></label>
        <label style={{ fontSize: 12 }}>Contact<input value={f.complainantContact} onChange={set("complainantContact")} style={input} /></label>
      </div>
      <label style={{ fontSize: 12 }}>Witnesses
        <input value={f.witnesses} onChange={set("witnesses")} placeholder="Names and how to reach them" style={input} /></label>
      {err && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 13 }}>{err}</div>}
      <div style={{ display: "flex", gap: 8 }}>
        <button onClick={submit} disabled={save.isPending} style={primary}>{e ? "Save changes" : "Log complaint"}</button>
        <button onClick={onClose} style={btn}>Cancel</button>
      </div>
    </div>
  )
}
