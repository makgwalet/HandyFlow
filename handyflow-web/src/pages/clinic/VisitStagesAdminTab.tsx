// Which stages of a consultation a visit type must have filled in before it can be signed (CLINIC-DEC-010 and 012, patch 0194).
// A practice may set its own; without one the platform's setting applies; without that, Symptoms and Diagnosis.
import { useEffect, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { BORDER, GRAY, lbl, sinp } from "./patientFile.shared"
import { primaryBtn, smallBtn } from "./OverviewCard"
import { STAGES, VISIT_TYPES, fromApi, lessThanDefault, sameSelection, sourceNote, toRequest, visitTypeLabel, type Selection } from "./stagesView"

const unwrap = (r: any) => r.data?.data ?? r.data

export default function VisitStagesAdminTab() {
  const qc = useQueryClient()
  const canEdit = usePermission("CLINIC_CONTENT_ADMIN")
  const [visitType, setVisitType] = useState<string>("CONSULTATION")
  const [sel, setSel] = useState<Selection | null>(null)
  const [error, setError] = useState("")
  const [note, setNote] = useState("")

  const q = useQuery({
    queryKey: ["clinic-visit-stages-admin", visitType], retry: false,
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/clinic/visit-types/${encodeURIComponent(visitType)}/stages`)),
  })
  const server: Selection | null = q.data ? fromApi(q.data) : null
  useEffect(() => { setSel(server); setError(""); setNote("") /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [q.data, visitType])

  const refresh = () => {
    qc.invalidateQueries({ queryKey: ["clinic-visit-stages-admin"] })
    qc.invalidateQueries({ queryKey: ["clinic-visit-stages"] })   // the consultation screens read this one
  }
  const save = useMutation({
    mutationFn: () => apiClient.put(`/api/v1/clinic/visit-types/${encodeURIComponent(visitType)}/stages`, toRequest(sel!)),
    onSuccess: () => { setNote("Saved. New consultations of this type use it straight away."); setError(""); refresh() },
    onError: (e: any) => { setNote(""); setError(e?.response?.data?.message ?? "Could not save.") },
  })
  const clear = useMutation({
    mutationFn: () => apiClient.delete(`/api/v1/clinic/visit-types/${encodeURIComponent(visitType)}/stages`),
    onSuccess: () => { setNote("This practice's own setting is removed."); setError(""); refresh() },
    onError: (e: any) => { setNote(""); setError(e?.response?.data?.message ?? "Could not remove it.") },
  })

  const changed = !!sel && !!server && !sameSelection(sel, server)
  const warn = sel ? lessThanDefault(sel) : null
  const source: string | undefined = q.data?.source

  return (
    <div>
      <div style={{ fontWeight: 700 }}>Required consultation stages</div>
      <div style={{ fontSize: 12, color: GRAY, marginBottom: 12 }}>A consultation cannot be signed until the stages ticked here are filled in. Which stages a visit type needs is a clinical decision: agree it with your clinical lead before changing it.</div>
      <div style={{ maxWidth: 360, marginBottom: 12 }}>
        <label style={lbl} htmlFor="vs-type">Visit type</label>
        <select id="vs-type" style={sinp} value={visitType} onChange={e => setVisitType(e.target.value)}>
          {VISIT_TYPES.map(t => <option key={t} value={t}>{visitTypeLabel(t)}</option>)}
        </select>
      </div>
      {q.isLoading && <div style={{ fontSize: 13, color: GRAY }}>Loading…</div>}
      {q.isError && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>The setting could not be loaded.</div>}
      {sel && (
        <div style={{ border: `1px solid ${BORDER}`, borderRadius: 10, padding: 14, maxWidth: 480 }}>
          <div style={{ fontSize: 12, color: GRAY, marginBottom: 8 }}>{sourceNote(source)}</div>
          {STAGES.map(s => (
            <label key={s.id} style={{ display: "block", fontSize: 14, margin: "6px 0" }}>
              <input type="checkbox" checked={sel[s.id] === true} disabled={!canEdit} onChange={e => setSel({ ...sel, [s.id]: e.target.checked })} /> {s.label} is required
            </label>
          ))}
          {warn && <div role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text)", marginTop: 8 }}>{warn}</div>}
          {canEdit
            ? <div style={{ display: "flex", gap: 8, marginTop: 12, flexWrap: "wrap" }}>
                <button type="button" style={{ ...primaryBtn, opacity: changed && !save.isPending ? 1 : 0.6 }} disabled={!changed || save.isPending} onClick={() => save.mutate()}>Save for this practice</button>
                {source === "TENANT" && <button type="button" style={smallBtn} disabled={clear.isPending} onClick={() => clear.mutate()}>Go back to the default</button>}
              </div>
            : <div style={{ fontSize: 12, color: GRAY, marginTop: 10 }}>You can see this setting but not change it.</div>}
        </div>
      )}
      {note && <div role="status" style={{ fontSize: 13, marginTop: 8 }}>{note}</div>}
      {error && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)", marginTop: 8 }}>{error}</div>}
    </div>
  )
}
