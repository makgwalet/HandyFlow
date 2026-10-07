// src/pages/security/ReadinessRequirementsCard.tsx
//
// Which screenings and guard-file documents count towards a guard's deployment readiness (Workforce > Risk rules).
// Anyone with security access can read them; changing them needs SECURITY_ADMIN. A PSiRA registration is always required.
import { useEffect, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { readinessSettingsError, toggleValue, type ReadinessSettings } from "./performance.logic"

const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, maxWidth: 720, marginTop: 16 }
const primary: React.CSSProperties = { display: "inline-flex", alignItems: "center", padding: "8px 14px", border: "none", borderRadius: 9, background: "var(--hf-accent)", color: "var(--hf-text-on-solid)", fontSize: 13, fontWeight: 600, cursor: "pointer" }

export default function ReadinessRequirementsCard() {
  const qc = useQueryClient()
  const canEdit = usePermission("SECURITY_ADMIN")
  const { data, isLoading, error } = useQuery<ReadinessSettings>({
    queryKey: ["readiness-settings"],
    queryFn: async () => { const r = await apiClient.get("/api/v1/security/readiness-settings"); return r.data?.data ?? r.data },
  })
  const [screening, setScreening] = useState<string[]>([])
  const [documents, setDocuments] = useState<string[]>([])
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null)
  useEffect(() => { if (data) { setScreening(data.requiredScreening ?? []); setDocuments(data.requiredDocuments ?? []) } }, [data])

  const save = useMutation({
    mutationFn: () => apiClient.put("/api/v1/security/readiness-settings", { requiredScreening: screening, requiredDocuments: documents }),
    onSuccess: () => {
      setMsg({ ok: true, text: "Readiness requirements saved." })
      qc.invalidateQueries({ queryKey: ["readiness-settings"] })
      qc.invalidateQueries({ queryKey: ["guard-overview"] })
      qc.invalidateQueries({ queryKey: ["guard-performance"] })
    },
    onError: (e: any) => setMsg({ ok: false, text: e?.response?.data?.message ?? "That did not work. Please try again." }),
  })
  if (isLoading) return <div style={{ color: "var(--hf-text-muted)", marginTop: 16 }}>Loading readiness requirements...</div>
  if (error || !data) return null

  const group = (title: string, options: ReadinessSettings["screeningOptions"], chosen: string[], set: (v: string[]) => void) => (
    <fieldset style={{ border: "none", padding: 0, margin: 0 }}>
      <legend style={{ fontSize: 13, fontWeight: 700, marginBottom: 6 }}>{title}</legend>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(220px, 1fr))", gap: 6 }}>
        {(options ?? []).map(o => (
          <label key={o.value} style={{ fontSize: 13, display: "flex", gap: 8, alignItems: "center" }}>
            <input type="checkbox" disabled={!canEdit} checked={chosen.includes(o.value)} onChange={() => set(toggleValue(chosen, o.value))} />
            {o.label}
          </label>
        ))}
      </div>
    </fieldset>
  )
  const submit = () => {
    const m = readinessSettingsError(screening, documents)
    if (m) setMsg({ ok: false, text: m }); else { setMsg(null); save.mutate() }
  }

  return (
    <div style={card}>
      <div style={{ fontWeight: 800, marginBottom: 4 }}>Deployment readiness requirements</div>
      <div style={{ fontSize: 13, color: "var(--hf-text-muted)", marginBottom: 14 }}>
        A guard is ready to deploy when everything ticked here is on file, in date and (for screenings) backed by an evidence file.
        A valid PSiRA registration is always required.
        {!data.customised && " These are the standard defaults."}
        {!canEdit && " Changing them needs an administrator."}
      </div>
      <div style={{ display: "grid", gap: 14 }}>
        {group("Screenings", data.screeningOptions, screening, setScreening)}
        {group("Documents in the guard file", data.documentOptions, documents, setDocuments)}
      </div>
      {msg && <div role={msg.ok ? "status" : "alert"} style={{ marginTop: 10, fontSize: 13, color: msg.ok ? "var(--hf-success-text-strong)" : "var(--hf-danger-text)" }}>{msg.text}</div>}
      {canEdit && <div style={{ marginTop: 14 }}><button onClick={submit} disabled={save.isPending} style={primary}>Save readiness requirements</button></div>}
      {data.updatedByName && <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 8 }}>Last changed by {data.updatedByName}.</div>}
    </div>
  )
}
