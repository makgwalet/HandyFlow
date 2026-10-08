// "Save as template" beside a letter dialog: keeps what has been typed as a reusable preset.
import { useState } from "react"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { sinp } from "./patientFile.shared"
import type { Kind } from "./letterView"

export default function SaveAsTemplate({ kind, payload }: { kind: Kind; payload: () => { title?: string; body?: string; specialty?: string; urgency?: string; unfitDays?: number | null } }) {
  const canCreate = usePermission("CLINIC_DOCUMENT_CREATE")
  const [open, setOpen] = useState(false)
  const [name, setName] = useState("")
  const [state, setState] = useState<"idle" | "busy" | "saved">("idle")
  const [error, setError] = useState("")
  if (!canCreate) return null
  const save = async () => {
    setState("busy"); setError("")
    try { await apiClient.post("/api/v1/clinic/letter-templates", { kind, name: name.trim(), ...payload() }); setState("saved"); setOpen(false); setName("") }
    catch (e: any) { setError(e?.response?.data?.message ?? "The template could not be saved."); setState("idle") }
  }
  if (!open) return (
    <div>
      <button type="button" onClick={() => { setOpen(true); setState("idle") }}
        style={{ background: "none", border: "none", padding: 0, fontSize: 12, fontWeight: 600, color: "var(--hf-primary)", cursor: "pointer" }}>Save as template</button>
      {state === "saved" && <span role="status" style={{ marginLeft: 8, fontSize: 12, color: "var(--hf-text-muted)" }}>Template saved</span>}
    </div>)
  return (
    <div style={{ display: "flex", gap: 6, alignItems: "center", flexWrap: "wrap" }}>
      <input aria-label="Template name" placeholder="Template name" style={{ ...sinp, width: 200 }} value={name} onChange={e => setName(e.target.value)} />
      <button type="button" disabled={!name.trim() || state === "busy"} onClick={save}
        style={{ padding: "7px 12px", borderRadius: 8, border: "none", background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", fontSize: 12, fontWeight: 700, cursor: "pointer", opacity: name.trim() ? 1 : 0.6 }}>Save</button>
      <button type="button" onClick={() => setOpen(false)} style={{ background: "none", border: "none", fontSize: 12, color: "var(--hf-text-muted)", cursor: "pointer" }}>Cancel</button>
      {error && <div role="alert" style={{ width: "100%", fontSize: 12, color: "var(--hf-danger-text)" }}>{error}</div>}
    </div>)
}
