import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { btnGhost, btnPrimary, card, inputStyle, labelStyle, Modal } from "../warehousing/whseUi"
import { emptyEntity, entityToForm, toEntityRequest, validateEntity, type EntityForm, type LegalEntity } from "./setupOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const msg = (e: any, fb: string) => e?.response?.data?.message ?? fb
const FIELDS: { key: keyof EntityForm; label: string }[] = [
  { key: "name", label: "Name *" }, { key: "registrationNo", label: "Registration number" }, { key: "taxNo", label: "Tax number" }, { key: "vatNo", label: "VAT number" }, { key: "address", label: "Address" },
]

export default function LegalEntitiesTab() {
  const canManage = usePermissions(["FUEL_CONFIGURATION_MANAGE"])
  const qc = useQueryClient()
  const q = useQuery<LegalEntity[]>({ queryKey: ["fuel-legal-entities"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/legal-entities")) })
  const [edit, setEdit] = useState<{ id: string | null; form: EntityForm } | null>(null)
  const [err, setErr] = useState("")
  const done = () => { setEdit(null); setErr(""); qc.invalidateQueries({ queryKey: ["fuel-legal-entities"] }); qc.invalidateQueries({ queryKey: ["fuel-profile"] }) }
  const save = useMutation({
    mutationFn: (a: { id: string | null; form: EntityForm }) => a.id ? apiClient.put(`/api/v1/fuel/legal-entities/${a.id}`, toEntityRequest(a.form)) : apiClient.post("/api/v1/fuel/legal-entities", toEntityRequest(a.form)),
    onSuccess: done, onError: (e: any) => setErr(msg(e, "Could not save the legal entity")),
  })
  const remove = useMutation({ mutationFn: (id: string) => apiClient.delete(`/api/v1/fuel/legal-entities/${id}`), onSuccess: done, onError: (e: any) => setErr(msg(e, "Could not remove the legal entity")) })
  const rows = q.data ?? []
  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", marginBottom: 10 }}>
        <p style={{ margin: 0, flex: 1, fontSize: 12.5, color: "var(--hf-text-muted)" }}>The companies that own your Fuel depots and operations.</p>
        {canManage && <button style={btnPrimary} onClick={() => { setErr(""); setEdit({ id: null, form: emptyEntity() }) }}>Add legal entity</button>}
      </div>
      {err && !edit && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{err}</p>}
      {q.isLoading ? <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p> : rows.length === 0
        ? <div style={{ ...card, padding: 16, fontSize: 13 }}>No legal entities yet.</div>
        : rows.map(e => (
          <div key={e.id} style={{ ...card, padding: 12, marginBottom: 8, display: "flex", gap: 10, alignItems: "center" }}>
            <div style={{ flex: 1 }}>
              <strong style={{ fontSize: 13 }}>{e.name}</strong>
              <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{[e.registrationNo && `Reg ${e.registrationNo}`, e.vatNo && `VAT ${e.vatNo}`, `${e.depotCount} depot${e.depotCount === 1 ? "" : "s"}`].filter(Boolean).join(" · ")}</div>
            </div>
            {canManage && <>
              <button style={btnGhost} onClick={() => { setErr(""); setEdit({ id: e.id, form: entityToForm(e) }) }}>Edit</button>
              <button style={btnGhost} disabled={remove.isPending} onClick={() => { setErr(""); remove.mutate(e.id) }} aria-label={`Remove ${e.name}`}>Remove</button>
            </>}
          </div>
        ))}
      {edit && (
        <Modal title={edit.id ? "Edit legal entity" : "Add legal entity"} onClose={() => setEdit(null)}>
          {FIELDS.map(f => (
            <div key={f.key} style={{ marginBottom: 10 }}>
              <label style={labelStyle} htmlFor={`le-${f.key}`}>{f.label}</label>
              <input id={`le-${f.key}`} style={{ ...inputStyle, width: "100%" }} value={edit.form[f.key]} onChange={ev => setEdit(s => s && ({ ...s, form: { ...s.form, [f.key]: ev.target.value } }))} />
            </div>
          ))}
          {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{err}</p>}
          <div style={{ display: "flex", gap: 8 }}>
            <button style={{ ...btnGhost, flex: 1 }} onClick={() => setEdit(null)}>Cancel</button>
            <button style={{ ...btnPrimary, flex: 1 }} disabled={save.isPending} onClick={() => { const v = validateEntity(edit.form); if (v) return setErr(v); setErr(""); save.mutate(edit) }}>Save</button>
          </div>
        </Modal>
      )}
    </div>
  )
}
