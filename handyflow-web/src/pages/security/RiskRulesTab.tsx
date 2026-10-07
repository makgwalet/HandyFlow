// src/pages/security/RiskRulesTab.tsx
//
// The thresholds behind the risk recommendations (Workforce > Risk rules). Anyone with security access can read them;
// changing them needs SECURITY_ADMIN. The rules only produce recommendations for a person to review.
import { useEffect, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { riskSettingsError, type RiskSettings } from "./performance.logic"

const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, maxWidth: 720 }
const input: React.CSSProperties = { padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text-primary)", width: 90, boxSizing: "border-box" }
const primary: React.CSSProperties = { display: "inline-flex", alignItems: "center", padding: "8px 14px", border: "none", borderRadius: 9, background: "var(--hf-accent)", color: "var(--hf-text-on-solid)", fontSize: 13, fontWeight: 600, cursor: "pointer" }

export default function RiskRulesTab() {
  const qc = useQueryClient()
  const canEdit = usePermission("SECURITY_ADMIN")
  const { data, isLoading, error } = useQuery<RiskSettings>({
    queryKey: ["risk-settings"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/security/risk-settings"); return r.data?.data ?? r.data },
  })
  const [f, setF] = useState<RiskSettings | null>(null)
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null)
  useEffect(() => { if (data) setF(data) }, [data])

  const save = useMutation({
    mutationFn: () => apiClient.put("/api/v1/security/risk-settings", f),
    onSuccess: () => { setMsg({ ok: true, text: "Risk rules saved." }); qc.invalidateQueries({ queryKey: ["risk-settings"] }); qc.invalidateQueries({ queryKey: ["guard-performance"] }) },
    onError: (e: any) => setMsg({ ok: false, text: e?.response?.data?.message ?? "That did not work. Please try again." }),
  })
  if (isLoading || !f) return <div style={{ color: "var(--hf-text-muted)" }}>Loading risk rules...</div>
  if (error) return <div role="alert" style={{ color: "var(--hf-danger-text)" }}>Risk rules could not be loaded.</div>

  const num = (k: keyof RiskSettings, label: string, suffix?: string) => (
    <label style={{ fontSize: 13, display: "flex", alignItems: "center", gap: 8, justifyContent: "space-between", flexWrap: "wrap" }}>
      <span>{label}</span>
      <span><input type="number" min={1} aria-label={label} disabled={!canEdit} value={f[k] as number} onChange={e => setF({ ...f, [k]: Number(e.target.value) })} style={input} /> {suffix}</span>
    </label>
  )
  const submit = () => { const m = riskSettingsError(f); if (m) setMsg({ ok: false, text: m }); else { setMsg(null); save.mutate() } }

  return (
    <div style={card}>
      <div style={{ fontWeight: 800, marginBottom: 4 }}>Risk rules</div>
      <div style={{ fontSize: 13, color: "var(--hf-text-muted)", marginBottom: 14 }}>
        When these are reached, the guard's Performance tab recommends a review. Nothing happens automatically: a person decides.
        {!data?.customised && " These are the standard defaults."}
        {!canEdit && " Changing them needs an administrator."}
      </div>
      <div style={{ display: "grid", gap: 12 }}>
        {num("windowDays", "Count complaints over the last", "days")}
        {num("reviewAt", "Recommend a supervisor review at", "complaint(s)")}
        {num("warningAt", "Recommend a warning review at", "complaints")}
        {num("investigationAt", "Recommend a formal investigation at", "complaints")}
        {num("misconductAt", "Recommend disciplinary action at", "confirmed misconduct findings")}
        {num("misconductWindowDays", "Count misconduct findings over the last", "days")}
        <label style={{ fontSize: 13, display: "flex", gap: 8, alignItems: "center" }}>
          <input type="checkbox" disabled={!canEdit} checked={f.suspensionReviewOnCritical} onChange={e => setF({ ...f, suspensionReviewOnCritical: e.target.checked })} />
          Recommend a suspension review after a critical incident</label>
      </div>
      <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 12 }}>Complaints that were withdrawn or found not substantiated are not counted.</div>
      {msg && <div role={msg.ok ? "status" : "alert"} style={{ marginTop: 10, fontSize: 13, color: msg.ok ? "var(--hf-success-text-strong)" : "var(--hf-danger-text)" }}>{msg.text}</div>}
      {canEdit && <div style={{ marginTop: 14 }}><button onClick={submit} disabled={save.isPending} style={primary}>Save risk rules</button></div>}
      {data?.updatedByName && <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 8 }}>Last changed by {data.updatedByName}.</div>}
    </div>
  )
}
