// Stage 4B: licences, calibrations, inspections and certificates. An expired tanker, driver or company licence blocks load allocation.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { Modal, btnPrimary, btnGhost, card, inputStyle, labelStyle, Chip, ReasonModal } from "../warehousing/whseUi"
import { sizeText } from "./evidenceCapture"
import { TYPES, SUBJECTS, ACTIVITIES, activityLabel, coverageText, evidenceLabel, validateComplianceFile, validateEvidenceRef, subjectNeedsPick, validateItem, validateRenew, whenLabel, byUrgency, type ComplianceItem, type ComplianceEvidence, type Coverage } from "./complianceOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const msg = (e: any, fb: string) => e?.response?.data?.message ?? fb
const th: React.CSSProperties = { textAlign: "left", padding: "10px 12px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-muted)", whiteSpace: "nowrap" }
const td: React.CSSProperties = { padding: "10px 12px", fontSize: 13, color: "var(--hf-text)" }
const tone = (s: string) => s === "EXPIRED" ? { bg: "var(--hf-danger-soft)", fg: "var(--hf-danger-text)" } : s === "EXPIRING" ? { bg: "var(--hf-warning-soft)", fg: "var(--hf-warning-text)" } : { bg: "var(--hf-success-soft-strong)", fg: "var(--hf-success-text-strong)" }
type Dialog = { kind: "edit"; item?: ComplianceItem } | { kind: "evidence"; item: ComplianceItem } | { kind: "renew"; item: ComplianceItem } | null

export default function ComplianceTab() {
  const canManage = usePermissions(["FUEL_MANAGE", "FUEL_COMPLIANCE_MANAGE"])
  const qc = useQueryClient()
  const [dialog, setDialog] = useState<Dialog>(null)
  const [err, setErr] = useState("")
  const list = useQuery<ComplianceItem[]>({ queryKey: ["fuel-compliance"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/compliance")) })
  const coverage = useQuery<Coverage>({ queryKey: ["fuel-compliance-coverage"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/compliance/coverage")) })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["fuel-compliance"] }); qc.invalidateQueries({ queryKey: ["fuel-compliance-coverage"] }) }
  const cov = Array.isArray(coverage.data?.entities) ? coverage.data!.entities : []
  const remove = useMutation({ mutationFn: (id: string) => apiClient.delete(`/api/v1/fuel/compliance/${id}`), onSuccess: () => { setErr(""); refresh() }, onError: (e: any) => setErr(msg(e, "Could not remove it")) })
  const rows = [...(list.data ?? [])].sort(byUrgency)
  const expired = rows.filter(r => r.status === "EXPIRED").length
  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 14 }}>
        <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: 0, flex: 1 }}>
          Anything that expires: licences, calibrations, roadworthy and inspection certificates, insurance. A tanker or driver with an expired item cannot be allocated to a load, and neither can anyone while the company's licence has expired. Account admins are emailed a reminder 30 days and 7 days before an item expires, and once when it has expired.
        </p>
        {canManage && <button style={btnPrimary} onClick={() => setDialog({ kind: "edit" })}>Add item</button>}
      </div>
      {(cov.length > 0 || (coverage.data?.sites ?? []).length > 0) && (
        <div style={{ ...card, padding: 12, marginBottom: 14 }}>
          <p style={{ margin: "0 0 6px", fontSize: 12.5, fontWeight: 700 }}>Licences by legal entity</p>
          {cov.map(c => (
            <div key={c.legalEntityId} style={{ fontSize: 12.5, padding: "3px 0" }}>
              <strong>{c.name}</strong>: {coverageText(c)}{c.activities.length > 0 && <span style={{ color: "var(--hf-text-muted)" }}> · {c.activities.map(activityLabel).join(", ")}</span>}
            </div>
          ))}
          {(coverage.data?.sites ?? []).length > 0 && <>
            <p style={{ margin: "10px 0 6px", fontSize: 12.5, fontWeight: 700 }}>Licences by site</p>
            {coverage.data!.sites!.map(c => (
              <div key={c.depotId} style={{ fontSize: 12.5, padding: "3px 0" }}>
                <strong>{c.name}</strong>{c.legalEntityName ? ` (${c.legalEntityName})` : ""}: {coverageText(c)}{c.activities.length > 0 && <span style={{ color: "var(--hf-text-muted)" }}> · {c.activities.map(activityLabel).join(", ")}</span>}
              </div>
            ))}
          </>}
          {(coverage.data?.unlinkedLicences ?? 0) > 0 && <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 4 }}>{coverage.data!.unlinkedLicences} licence{coverage.data!.unlinkedLicences === 1 ? " is" : "s are"} not linked to a legal entity.</div>}
          <div style={{ fontSize: 11, color: "var(--hf-text-muted)", marginTop: 4 }}>For information only. The system does not know which licences your business is required to hold.</div>
        </div>
      )}
      {expired > 0 && <p role="status" style={{ color: "var(--hf-danger-text)", fontSize: 13, fontWeight: 600 }}>{expired} expired: affected loads cannot be allocated until renewed.</p>}
      {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{err}</p>}
      {list.isLoading ? <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p> : rows.length === 0 ? <p style={{ color: "var(--hf-text-muted)" }}>Nothing recorded yet.</p> : (
        <div style={{ ...card, overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Item</th><th style={th}>For</th><th style={th}>Reference</th><th style={th}>Expires</th><th style={th}>Status</th><th style={th}></th></tr></thead>
            <tbody>
              {rows.map(i => (
                <tr key={i.id} style={{ borderTop: "1px solid var(--hf-border)" }}>
                  <td style={td}>{i.title}<div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>{i.itemType.toLowerCase()}</div></td>
                  <td style={td}>{i.subjectType === "COMPANY" ? "Company" : `${i.subjectType.toLowerCase()}: ${i.subjectLabel ?? ""}`}
                    {(i.legalEntityName || i.depotName || i.licensedActivity) && <div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>{[i.legalEntityName, i.depotName ? `Site: ${i.depotName}` : null, i.licensedActivity ? activityLabel(i.licensedActivity) : null, i.issuingAuthority].filter(Boolean).join(" · ")}</div>}</td>
                  <td style={td}>{i.referenceNo ?? "–"}</td>
                  <td style={td}>{i.expiresOn}<div style={{ fontSize: 11, color: "var(--hf-text-muted)" }}>{whenLabel(i)}</div></td>
                  <td style={td}><Chip bg={tone(i.status).bg} fg={tone(i.status).fg}>{i.status === "VALID" ? "Valid" : i.status === "EXPIRING" ? "Expiring" : "Expired"}</Chip></td>
                  <td style={td}><span style={{ display: "flex", gap: 6 }}>
                    <button style={btnGhost} onClick={() => setDialog({ kind: "evidence", item: i })}>{`Evidence (${i.evidenceCount ?? 0})`}</button>
                    {canManage && <>
                    <button style={btnPrimary} onClick={() => setDialog({ kind: "renew", item: i })}>Renew</button>
                    <button style={btnGhost} onClick={() => setDialog({ kind: "edit", item: i })}>Edit</button>
                    <button style={btnGhost} onClick={() => { if (window.confirm(`Remove ${i.title}?`)) remove.mutate(i.id) }}>Remove</button>
                  </>}</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {dialog?.kind === "edit" && <EditDialog item={dialog.item} onDone={refresh} onClose={() => setDialog(null)} />}
      {dialog?.kind === "evidence" && <EvidenceDialog item={dialog.item} canManage={canManage} onDone={refresh} onClose={() => setDialog(null)} />}
      {dialog?.kind === "renew" && <RenewDialog item={dialog.item} onDone={refresh} onClose={() => setDialog(null)} />}
    </div>
  )
}

function EditDialog({ item, onDone, onClose }: { item?: ComplianceItem; onDone: () => void; onClose: () => void }) {
  const [f, setF] = useState({ itemType: item?.itemType ?? "CERTIFICATE", title: item?.title ?? "", referenceNo: item?.referenceNo ?? "", subjectType: item?.subjectType ?? "TANKER", subjectId: item?.subjectId ?? "", subjectLabel: item?.subjectLabel ?? "", issuedOn: item?.issuedOn ?? "", expiresOn: item?.expiresOn ?? "", notes: item?.notes ?? "", legalEntityId: item?.legalEntityId ?? "", licensedActivity: item?.licensedActivity ?? "", issuingAuthority: item?.issuingAuthority ?? "", depotId: item?.depotId ?? "" })
  const entities = useQuery<{ id: string; name: string }[]>({ queryKey: ["fuel-legal-entities"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/legal-entities")) })
  const sites = useQuery<{ id: string; name: string }[]>({ queryKey: ["fuel-compliance-sites"], queryFn: async () => unwrap(await apiClient.get("/api/v1/fuel/depots")) })
  const [err, setErr] = useState("")
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setF(s => ({ ...s, [k]: e.target.value }))
  const pick = f.subjectType
  const options = useQuery<{ id: string; label: string }[]>({
    queryKey: ["fuel-compliance-subjects", pick], enabled: subjectNeedsPick(pick) && !item,
    queryFn: async () => {
      if (pick === "TANKER") return (unwrap(await apiClient.get("/api/v1/fuel/tankers")) as any[]).map(t => ({ id: t.id, label: t.registration }))
      if (pick === "DRIVER") return (unwrap(await apiClient.get("/api/v1/fuel/drivers")) as any[]).map(d => ({ id: d.id, label: d.name }))
      if (pick === "TANK") return (unwrap(await apiClient.get("/api/v1/fuel/tanks")) as any[]).map(t => ({ id: t.id, label: t.name }))
      return (unwrap(await apiClient.get("/api/v1/fuel/depots")) as any[]).map(d => ({ id: d.id, label: d.name }))
    },
  })
  const save = useMutation({
    mutationFn: () => {
      const body = { itemType: f.itemType, title: f.title, referenceNo: f.referenceNo || null, subjectType: f.subjectType, subjectId: f.subjectId || null, subjectLabel: f.subjectLabel || null, issuedOn: f.issuedOn || null, expiresOn: f.expiresOn, notes: f.notes || null, legalEntityId: f.legalEntityId || null, licensedActivity: f.licensedActivity || null, issuingAuthority: f.issuingAuthority.trim() || null, depotId: f.depotId || null }
      return item ? apiClient.put(`/api/v1/fuel/compliance/${item.id}`, body) : apiClient.post("/api/v1/fuel/compliance", body)
    },
    onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not save")),
  })
  return (
    <Modal title={item ? "Edit item" : "Add compliance item"} onClose={onClose}>
      <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
          <div><label style={labelStyle}>Type</label><select aria-label="Type" value={f.itemType} onChange={set("itemType")} style={inputStyle}>{TYPES.map(x => <option key={x} value={x}>{x.toLowerCase()}</option>)}</select></div>
          <div><label style={labelStyle}>For</label><select aria-label="For" value={f.subjectType} disabled={!!item} onChange={e => setF(s => ({ ...s, subjectType: e.target.value, subjectId: "" }))} style={inputStyle}>{SUBJECTS.map(x => <option key={x} value={x}>{x.toLowerCase()}</option>)}</select></div>
        </div>
        {subjectNeedsPick(pick) && !item && (
          <div><label style={labelStyle}>Which {pick.toLowerCase()} *</label>
            <select aria-label="Subject" value={f.subjectId} onChange={set("subjectId")} style={inputStyle}><option value="">Choose…</option>{(options.data ?? []).map(o => <option key={o.id} value={o.id}>{o.label}</option>)}</select></div>
        )}
        <div><label style={labelStyle}>Title *</label><input aria-label="Title" value={f.title} onChange={set("title")} placeholder="e.g. Roadworthy certificate" style={inputStyle} /></div>
        <div><label style={labelStyle}>Reference / certificate number</label><input value={f.referenceNo} onChange={set("referenceNo")} style={inputStyle} /></div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
          <div><label style={labelStyle}>Issued</label><input aria-label="Issued" type="date" value={f.issuedOn} onChange={set("issuedOn")} style={inputStyle} /></div>
          <div><label style={labelStyle}>Expires *</label><input aria-label="Expires" type="date" value={f.expiresOn} onChange={set("expiresOn")} style={inputStyle} /></div>
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
          <div><label style={labelStyle} htmlFor="ci-entity">Legal entity</label>
            <select id="ci-entity" value={f.legalEntityId} onChange={set("legalEntityId")} style={inputStyle}><option value="">Not linked</option>{(Array.isArray(entities.data) ? entities.data : []).map(e => <option key={e.id} value={e.id}>{e.name}</option>)}</select></div>
          <div><label style={labelStyle} htmlFor="ci-activity">Licensed activity</label>
            <select id="ci-activity" value={f.licensedActivity} onChange={set("licensedActivity")} style={inputStyle}><option value="">Not stated</option>{ACTIVITIES.map(a => <option key={a} value={a}>{activityLabel(a)}</option>)}</select></div>
        </div>
        <div><label style={labelStyle} htmlFor="ci-depot">Depot (site)</label>
          <select id="ci-depot" value={f.depotId} onChange={set("depotId")} style={inputStyle}><option value="">Not tied to a depot</option>{(Array.isArray(sites.data) ? sites.data : []).map(d => <option key={d.id} value={d.id}>{d.name}</option>)}</select></div>
        <div><label style={labelStyle} htmlFor="ci-authority">Issuing authority</label><input id="ci-authority" value={f.issuingAuthority} onChange={set("issuingAuthority")} style={inputStyle} /></div>
        {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: 0 }}>{err}</p>}
        <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
          <button style={btnGhost} onClick={onClose}>Cancel</button>
          <button style={btnPrimary} disabled={save.isPending} onClick={() => { const v = validateItem(f.title, f.subjectType, f.subjectId, f.expiresOn, f.issuedOn); if (v) return setErr(v); setErr(""); save.mutate() }}>Save</button>
        </div>
      </div>
    </Modal>
  )
}

function EvidenceDialog({ item, canManage, onDone, onClose }: { item: ComplianceItem; canManage: boolean; onDone: () => void; onClose: () => void }) {
  const qc = useQueryClient()
  const canVerify = usePermissions(["FUEL_COMPLIANCE_VERIFY"])
  const [picked, setPicked] = useState<File | null>(null); const [fileNote, setFileNote] = useState("")
  const q = useQuery<ComplianceEvidence[]>({ queryKey: ["fuel-compliance-evidence", item.id], queryFn: async () => unwrap(await apiClient.get(`/api/v1/fuel/compliance/${item.id}/evidence`)) })
  const [ref, setRef] = useState(""); const [note, setNote] = useState(""); const [err, setErr] = useState(""); const [replacing, setReplacing] = useState<string | null>(null)
  const done = () => { setErr(""); setReplacing(null); qc.invalidateQueries({ queryKey: ["fuel-compliance-evidence", item.id] }); onDone() }
  const add = useMutation({ mutationFn: () => apiClient.post(`/api/v1/fuel/compliance/${item.id}/evidence`, { fileRef: ref.trim(), note: note.trim() || null }), onSuccess: () => { setRef(""); setNote(""); done() }, onError: (e: any) => setErr(msg(e, "Could not add the evidence")) })
  const upload = useMutation({
    mutationFn: () => { const f = new FormData(); f.append("file", picked!); if (fileNote.trim()) f.append("note", fileNote.trim()); return apiClient.post(`/api/v1/fuel/compliance/${item.id}/evidence/file`, f) },
    onSuccess: () => { setPicked(null); setFileNote(""); done() }, onError: (e: any) => setErr(msg(e, "Could not upload the file")),
  })
  const verify = useMutation({ mutationFn: (id: string) => apiClient.post(`/api/v1/fuel/compliance/evidence/${id}/verify`), onSuccess: done, onError: (e: any) => setErr(msg(e, "Could not verify it")) })
  const view = async (id: string) => {
    try {
      const r = await apiClient.get(`/api/v1/fuel/compliance/evidence/${id}/file`, { responseType: "blob" })
      const url = URL.createObjectURL(r.data as Blob); window.open(url, "_blank", "noopener"); setTimeout(() => URL.revokeObjectURL(url), 60_000)
    } catch (e: any) { setErr(msg(e, "Could not open the file")) }
  }
  const replace = useMutation({ mutationFn: (a: { id: string; reason: string }) => apiClient.post(`/api/v1/fuel/compliance/evidence/${a.id}/supersede`, { reason: a.reason }), onSuccess: done })
  const list = Array.isArray(q.data) ? q.data : []
  return (
    <Modal title={`Evidence: ${item.title}`} onClose={onClose}>
      <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", marginTop: 0 }}>Upload a scan or photo of the licence or certificate (JPEG, PNG, WebP or PDF, up to 5 MB), or record a link to it. Evidence is never edited or deleted: a wrong one is replaced with a reason and kept on record. A second person can verify it; you cannot verify your own.</p>
      {list.length === 0 && <p style={{ fontSize: 13 }}>{q.isLoading ? "Loading…" : "No evidence recorded."}</p>}
      {list.map(e => (
        <div key={e.id} style={{ display: "flex", gap: 8, alignItems: "center", padding: "6px 0", borderTop: "1px solid var(--hf-border)", fontSize: 12.5 }}>
          <span style={{ flex: 1, wordBreak: "break-all", textDecoration: e.status === "SUPERSEDED" ? "line-through" : undefined }}>{evidenceLabel(e)}{e.fileStored && e.fileSizeBytes ? ` (${sizeText(e.fileSizeBytes)})` : ""}{e.note ? ` · ${e.note}` : ""}{e.status === "SUPERSEDED" ? ` · Replaced: ${e.supersededReason}` : ""}</span>
          {e.status === "ACTIVE" && (e.verifiedAt ? <Chip bg="var(--hf-success-soft-strong)" fg="var(--hf-success-text-strong)">Verified</Chip> : <Chip bg="var(--hf-warning-soft)" fg="var(--hf-warning-text)">Not verified</Chip>)}
          {e.fileStored && <button style={btnGhost} onClick={() => view(e.id)}>View</button>}
          {canVerify && e.status === "ACTIVE" && !e.verifiedAt && <button style={btnGhost} disabled={verify.isPending} onClick={() => verify.mutate(e.id)}>Verify</button>}
          {canManage && e.status === "ACTIVE" && <button style={btnGhost} onClick={() => setReplacing(e.id)}>Replace</button>}
        </div>
      ))}
      {canManage && (
        <div style={{ marginTop: 10 }}>
          <label style={labelStyle} htmlFor="ev-file">Scan or photo</label>
          <input id="ev-file" type="file" accept="image/jpeg,image/png,image/webp,application/pdf" onChange={e => { setPicked(e.target.files?.[0] ?? null); setErr("") }} />
          <input aria-label="File note" placeholder="Note (optional)" value={fileNote} onChange={e => setFileNote(e.target.value)} style={{ ...inputStyle, marginTop: 6 }} />
          <div style={{ display: "flex", justifyContent: "flex-end", margin: "8px 0 12px" }}>
            <button style={btnPrimary} disabled={upload.isPending} onClick={() => { const v = validateComplianceFile(picked); if (v) return setErr(v); setErr(""); upload.mutate() }}>Upload file</button>
          </div>
          <label style={labelStyle} htmlFor="ev-ref">File link or reference *</label>
          <input id="ev-ref" value={ref} onChange={e => setRef(e.target.value)} style={inputStyle} />
          <label style={{ ...labelStyle, marginTop: 8 }} htmlFor="ev-note">Note</label>
          <input id="ev-note" value={note} onChange={e => setNote(e.target.value)} style={inputStyle} />
          <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 10 }}>
            <button style={btnPrimary} disabled={add.isPending} onClick={() => { const v = validateEvidenceRef(ref); if (v) return setErr(v); setErr(""); add.mutate() }}>Add evidence</button>
          </div>
        </div>
      )}
      {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{err}</p>}
      {replacing && <ReasonModal title="Replace evidence" confirmLabel="Replace" pending={replace.isPending} error={replace.error} onConfirm={reason => replace.mutate({ id: replacing, reason })} onClose={() => setReplacing(null)} />}
    </Modal>
  )
}

function RenewDialog({ item, onDone, onClose }: { item: ComplianceItem; onDone: () => void; onClose: () => void }) {
  const [next, setNext] = useState(""); const [note, setNote] = useState(""); const [err, setErr] = useState("")
  const renew = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/fuel/compliance/${item.id}/renew`, { newExpiresOn: next, note: note || null }),
    onSuccess: () => { onDone(); onClose() }, onError: (e: any) => setErr(msg(e, "Could not renew")),
  })
  return (
    <Modal title={`Renew ${item.title}`} onClose={onClose}>
      <p style={{ fontSize: 13, marginTop: 0 }}>Currently expires {item.expiresOn} ({whenLabel(item)}).</p>
      <label style={labelStyle}>New expiry date *</label>
      <input aria-label="New expiry" type="date" value={next} onChange={e => setNext(e.target.value)} style={{ ...inputStyle, width: "100%" }} />
      <label style={{ ...labelStyle, marginTop: 10 }}>Note</label>
      <input value={note} onChange={e => setNote(e.target.value)} style={{ ...inputStyle, width: "100%" }} />
      {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{err}</p>}
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginTop: 14 }}>
        <button style={btnGhost} onClick={onClose}>Cancel</button>
        <button style={btnPrimary} disabled={renew.isPending} onClick={() => { const v = validateRenew(item.expiresOn, next); if (v) return setErr(v); setErr(""); renew.mutate() }}>Renew</button>
      </div>
    </Modal>
  )
}
