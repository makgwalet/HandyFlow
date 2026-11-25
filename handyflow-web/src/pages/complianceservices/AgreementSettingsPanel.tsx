// Whether the person who drafted an agreement may also activate it. Off until an administrator switches it on.
import { useEffect, useRef, useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { settingsProblem, type AgreementSettings } from "./agreementSettingsRules"

const U = "/api/v1/compliance-services/agreement-settings"
const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T
const errText = (e: unknown): string => {
  const x = e as { response?: { data?: { message?: string } }; message?: string }
  return x?.response?.data?.message ?? x?.message ?? "Something went wrong"
}
const input: React.CSSProperties = { width: 140, padding: "6px 8px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)" }
const solid: React.CSSProperties = { padding: "7px 12px", border: "none", borderRadius: 8, background: "var(--hf-success-solid-strong)", color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer" }

export default function AgreementSettingsPanel() {
  const qc = useQueryClient()
  const admin = usePermissions(["COMPLIANCE_SERVICES_ADMIN"])
  const [on, setOn] = useState(false)
  const [min, setMin] = useState("")
  const [error, setError] = useState("")
  const [saved, setSaved] = useState(false)
  const loaded = useRef(false)
  const { data } = useQuery<AgreementSettings>({ queryKey: ["cs-agreement-settings"], queryFn: async () => unwrap<AgreementSettings>(await apiClient.get(U)) })
  useEffect(() => { if (data && !loaded.current && typeof data.requireSecondPerson === "boolean") { loaded.current = true; setOn(data.requireSecondPerson); setMin(data.minValue == null ? "" : String(data.minValue)) } }, [data])

  const save = async () => {
    const p = settingsProblem(on, min); if (p) { setError(p); return }
    setError(""); setSaved(false)
    try {
      await apiClient.put(U, { requireSecondPerson: on, minValue: on && min.trim() ? Number(min.replace(/\s/g, "").replace(",", ".")) : null })
      setSaved(true); qc.invalidateQueries({ queryKey: ["cs-agreement-settings"] })
    } catch (e) { setError(errText(e)) }
  }

  return (
    <div style={{ display: "grid", gap: 6, background: "var(--hf-surface-muted)", borderRadius: 10, padding: 10, fontSize: 13 }}>
      <label style={{ display: "flex", gap: 6, alignItems: "center" }}>
        <input type="checkbox" aria-label="A different person must activate an agreement" disabled={!admin} checked={on} onChange={e => { setOn(e.target.checked); setSaved(false) }} />
        A different person must activate an agreement than the one who drafted it
      </label>
      {on && (
        <label style={{ display: "flex", gap: 6, alignItems: "center", color: "var(--hf-text-muted)" }}>
          Only from a quoted total of (leave empty for every agreement)
          <input aria-label="Quoted total from" style={input} inputMode="decimal" disabled={!admin} value={min} onChange={e => { setMin(e.target.value); setSaved(false) }} />
        </label>
      )}
      {error && <div role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{error}</div>}
      {saved && <div role="status" style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Saved.</div>}
      {admin && <div><button style={solid} onClick={save}>Save</button></div>}
    </div>
  )
}
