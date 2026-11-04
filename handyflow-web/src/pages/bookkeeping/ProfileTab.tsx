// The practice's own details: name, registration number and how clients reach it.
import { useEffect, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { errorOf, unwrap } from "./bookkeeping.logic"
import { danger, field, muted, primary } from "./ui"

interface Profile { id: string; practiceName: string; registrationNumber: string | null; contactEmail: string | null; contactPhone: string | null; notes: string | null }
const EMPTY = { practiceName: "", registrationNumber: "", contactEmail: "", contactPhone: "", notes: "" }
const looksLikeEmail = (s: string) => !s.trim() || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(s.trim())

export default function ProfileTab() {
  const qc = useQueryClient()
  const canEdit = usePermission("BOOKKEEPING_MANAGE") || usePermission("BOOKKEEPING_ADMIN")
  const [form, setForm] = useState(EMPTY), [error, setError] = useState<string | null>(null), [saved, setSaved] = useState(false)
  // A practice that has not saved a profile yet gets a 404; that means "empty form", not an error.
  const { data, isLoading } = useQuery<Profile | null>({
    queryKey: ["bk-profile"], retry: false,
    queryFn: async () => { try { return unwrap(await apiClient.get("/api/v1/bookkeeping/profile")) as Profile } catch (e: any) { if (e?.response?.status === 404) return null; throw e } },
  })
  useEffect(() => { if (data) setForm({ practiceName: data.practiceName, registrationNumber: data.registrationNumber ?? "", contactEmail: data.contactEmail ?? "", contactPhone: data.contactPhone ?? "", notes: data.notes ?? "" }) }, [data])
  const set = (k: keyof typeof EMPTY) => (e: { target: { value: string } }) => { setSaved(false); setForm(f => ({ ...f, [k]: e.target.value })) }
  const save = useMutation({
    mutationFn: () => apiClient.put("/api/v1/bookkeeping/profile", Object.fromEntries(Object.entries(form).map(([k, v]) => [k, v.trim() || null]))),
    onSuccess: () => { setError(null); setSaved(true); qc.invalidateQueries({ queryKey: ["bk-profile"] }) },
    onError: e => { setSaved(false); setError(errorOf(e)) },
  })
  const valid = form.practiceName.trim().length > 0 && looksLikeEmail(form.contactEmail)
  const row = (label: string, key: keyof typeof EMPTY, props: object = {}) => (
    <label style={{ display: "block", marginBottom: 12 }}>
      <span style={{ display: "block", fontSize: 12, fontWeight: 600, marginBottom: 4 }}>{label}</span>
      <input value={form[key]} onChange={set(key)} disabled={!canEdit} style={{ ...field, width: "100%", boxSizing: "border-box" }} {...props} />
    </label>
  )
  return (
    <div style={{ maxWidth: 560 }}>
      <h3 style={{ marginTop: 0 }}>Practice details</h3>
      {isLoading ? <p style={muted}>Loading…</p> : (
        <>
          {!data && <p style={muted}>No details saved yet.{canEdit ? " Fill these in so your clients know who the work is done by." : ""}</p>}
          {row("Practice name", "practiceName", { maxLength: 255, "aria-required": true })}
          {row("Registration number", "registrationNumber")}
          {row("Contact email", "contactEmail", { type: "email" })}
          {row("Contact phone", "contactPhone")}
          <label style={{ display: "block", marginBottom: 12 }}>
            <span style={{ display: "block", fontSize: 12, fontWeight: 600, marginBottom: 4 }}>Notes</span>
            <textarea rows={4} value={form.notes} onChange={set("notes")} disabled={!canEdit} style={{ ...field, width: "100%", boxSizing: "border-box", resize: "vertical" }} />
          </label>
          {!looksLikeEmail(form.contactEmail) && <div role="alert" style={danger}>That email address does not look right.</div>}
          {error && <div role="alert" style={danger}>{error}</div>}
          {saved && <div role="status" style={muted}>Saved.</div>}
          {canEdit && <button style={{ ...primary, marginTop: 8 }} disabled={!valid || save.isPending} onClick={() => save.mutate()}>{save.isPending ? "Saving…" : "Save details"}</button>}
        </>
      )}
    </div>
  )
}
