// "Use a template": lists the clinic's templates of one kind and, when one is chosen, asks the server to fill its merge
// fields for this visit. It shows nothing without permission, with no templates, or when they cannot be loaded.
import { useEffect, useState } from "react"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { sinp } from "./patientFile.shared"
import type { Kind, Template } from "./letterView"

const unwrap = (r: any) => r?.data?.data ?? r?.data

/** A template is filled for a visit, or for a patient when there is no visit; the recipient fills {{recipient.*}}. */
export default function LetterTemplatePicker({ kind, consultationId, patientId, recipientName, recipientCompany, onApply }: {
  kind: Kind; consultationId?: string; patientId?: string; recipientName?: string; recipientCompany?: string; onApply: (t: Template) => void
}) {
  const canRead = usePermission("CLINIC_DOCUMENT_READ")
  const [list, setList] = useState<Template[]>([])
  const [error, setError] = useState("")
  useEffect(() => {
    if (!canRead) return
    let live = true
    apiClient.get("/api/v1/clinic/letter-templates", { params: { kind } })
      .then(r => { if (live) setList(unwrap(r) ?? []) }).catch(() => { if (live) setList([]) })
    return () => { live = false }
  }, [canRead, kind])
  if (!canRead || list.length === 0) return null
  const choose = async (id: string) => {
    if (!id) return
    setError("")
    const params: Record<string, string> = {}
    if (consultationId) params.consultationId = consultationId
    else if (patientId) params.patientId = patientId
    if (recipientName?.trim()) params.recipientName = recipientName.trim()
    if (recipientCompany?.trim()) params.recipientCompany = recipientCompany.trim()
    try { onApply(unwrap(await apiClient.get(`/api/v1/clinic/letter-templates/${id}/render`, { params }))) }
    catch (e: any) { setError(e?.response?.data?.message ?? "The template could not be applied.") }
  }
  return (
    <div>
      <label htmlFor="letter-template" style={{ display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-muted)", marginBottom: 4 }}>Use a template</label>
      <select id="letter-template" style={sinp} value="" onChange={e => choose(e.target.value)}>
        <option value="">Choose a template…</option>
        {list.map(t => <option key={t.id} value={t.id}>{t.name}</option>)}
      </select>
      {error && <div role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text)", marginTop: 4 }}>{error}</div>}
    </div>
  )
}
