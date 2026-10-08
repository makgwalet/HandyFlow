// Documents: one register of everything held for the patient. Files uploaded from outside, sick notes and referral letters
// when they are issued, and the documents produced from the record on request (visit summary, prescription, lab report).
import { useRef, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Download, FileText, Trash2, Upload } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { fmtDay } from "./briefing"
import ModalShell from "./ModalShell"
import ReferralLetterModal from "./ReferralLetterModal"
import { SickNoteModal } from "./QuickActionsCard"
import type { Consultation, Patient } from "./patientFile.shared"
import { BORDER, Empty, GRAY, LIGHT, RED_TEXT, lbl, sinp } from "./patientFile.shared"
import { primaryBtn, smallBtn } from "./OverviewCard"
import { UPLOAD_TYPES, kindCounts, sizeLabel, sourceLabel, typeLabel, uploadProblem, type RegisterItem } from "./docsView"

const unwrap = (r: any) => r?.data?.data ?? r?.data
const todayZA = () => new Date(Date.now() + 2 * 3600_000).toISOString().slice(0, 10)

async function saveFile(path: string, fallbackName: string) {
  const res = await apiClient.get(path, { responseType: "blob" } as any)
  const type = (res.headers as any)?.["content-type"] ?? res.data?.type ?? "application/octet-stream"
  const link = document.createElement("a")
  link.href = URL.createObjectURL(new Blob([res.data], { type }))
  link.download = fallbackName; link.click()
  setTimeout(() => URL.revokeObjectURL(link.href), 60_000)
}

function UploadModal({ patientId, consultations, onClose }: { patientId: string; consultations: Consultation[]; onClose: () => void }) {
  const qc = useQueryClient()
  const fileRef = useRef<HTMLInputElement>(null)
  const [f, setF] = useState({ type: "", title: "", date: "", notes: "", consultationId: "" })
  const [file, setFile] = useState<File | null>(null)
  const problem = uploadProblem({ file, type: f.type, title: f.title, date: f.date }, todayZA())
  const save = useMutation({
    mutationFn: () => {
      const body = new FormData()
      body.append("file", file as File); body.append("type", f.type); body.append("title", f.title.trim()); body.append("date", f.date)
      if (f.notes.trim()) body.append("notes", f.notes.trim())
      if (f.consultationId) body.append("consultationId", f.consultationId)
      return apiClient.post(`/api/v1/clinic/patients/${patientId}/documents`, body, { headers: { "Content-Type": "multipart/form-data" } })
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["pf-documents", patientId] }); onClose() },
  })
  const err = (save.error as any)?.response?.data?.message ?? (save.isError ? "The document could not be uploaded." : null)
  return (
    <ModalShell title="Upload a document" onClose={onClose} width={480}
      footer={<><button type="button" style={smallBtn} onClick={onClose}>Cancel</button>
        <button type="button" style={{ ...primaryBtn, opacity: problem || save.isPending ? 0.6 : 1 }} disabled={!!problem || save.isPending} onClick={() => save.mutate()}>
          {save.isPending ? "Uploading…" : "Upload"}</button></>}>
      <div style={{ fontSize: 12, color: GRAY, marginBottom: 6 }}>For a letter, outside report, scan, photo or paper notes from an earlier visit. PDF, JPEG or PNG, up to 10 MB.</div>
      <label style={lbl} htmlFor="doc-file">File</label>
      <input id="doc-file" ref={fileRef} type="file" accept="application/pdf,image/jpeg,image/png" style={sinp}
        onChange={e => { const x = e.target.files?.[0] ?? null; setFile(x); if (x && !f.title) setF(v => ({ ...v, title: x.name.replace(/\.[^.]+$/, "") })) }} />
      <label style={{ ...lbl, marginTop: 10 }} htmlFor="doc-type">Kind of document</label>
      <select id="doc-type" style={sinp} value={f.type} onChange={e => setF(v => ({ ...v, type: e.target.value }))}>
        <option value="">Choose…</option>{UPLOAD_TYPES.map(t => <option key={t} value={t}>{typeLabel(t)}</option>)}
      </select>
      <label style={{ ...lbl, marginTop: 10 }} htmlFor="doc-title">Title</label>
      <input id="doc-title" style={sinp} value={f.title} onChange={e => setF(v => ({ ...v, title: e.target.value }))} />
      <label style={{ ...lbl, marginTop: 10 }} htmlFor="doc-date">Date on the document</label>
      <input id="doc-date" type="date" max={todayZA()} style={sinp} value={f.date} onChange={e => setF(v => ({ ...v, date: e.target.value }))} />
      {consultations.length > 0 && <>
        <label style={{ ...lbl, marginTop: 10 }} htmlFor="doc-visit">Belongs to a visit (optional)</label>
        <select id="doc-visit" style={sinp} value={f.consultationId} onChange={e => setF(v => ({ ...v, consultationId: e.target.value }))}>
          <option value="">Not linked to a visit</option>
          {consultations.map(c => <option key={c.id} value={c.id}>{fmtDay(c.consultedAt)}{c.chiefComplaint ? ` · ${c.chiefComplaint}` : ""}</option>)}
        </select></>}
      <label style={{ ...lbl, marginTop: 10 }} htmlFor="doc-notes">Notes (optional)</label>
      <input id="doc-notes" style={sinp} value={f.notes} onChange={e => setF(v => ({ ...v, notes: e.target.value }))} />
      {(file || f.title || f.date) && problem && <div style={{ fontSize: 12, color: GRAY, marginTop: 8 }}>{problem}</div>}
      {err && <div role="alert" style={{ fontSize: 13, color: RED_TEXT, marginTop: 8 }}>{err}</div>}
    </ModalShell>
  )
}

function RemoveModal({ patientId, item, onClose }: { patientId: string; item: RegisterItem; onClose: () => void }) {
  const qc = useQueryClient()
  const [reason, setReason] = useState("")
  const remove = useMutation({
    mutationFn: () => apiClient.delete(`/api/v1/clinic/patients/${patientId}/documents/${item.id}`, { params: { reason: reason.trim() } }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["pf-documents", patientId] }); onClose() },
  })
  const err = (remove.error as any)?.response?.data?.message ?? (remove.isError ? "The document could not be removed." : null)
  return (
    <ModalShell title="Remove document" onClose={onClose} width={420}
      footer={<><button type="button" style={smallBtn} onClick={onClose}>Cancel</button>
        <button type="button" style={{ ...primaryBtn, opacity: reason.trim() && !remove.isPending ? 1 : 0.6 }} disabled={!reason.trim() || remove.isPending} onClick={() => remove.mutate()}>Remove</button></>}>
      <div style={{ fontSize: 13, color: "var(--hf-text)" }}>“{item.title}” will no longer be listed. The file is kept and the reason is recorded.</div>
      <label style={{ ...lbl, marginTop: 10 }} htmlFor="doc-reason">Reason</label>
      <input id="doc-reason" style={sinp} value={reason} onChange={e => setReason(e.target.value)} />
      {err && <div role="alert" style={{ fontSize: 13, color: RED_TEXT, marginTop: 8 }}>{err}</div>}
    </ModalShell>
  )
}

export default function DocumentsTab({ patient, consultations }: { patient: Patient; consultations: Consultation[] }) {
  const qc = useQueryClient()
  const canRead = usePermission("CLINIC_DOCUMENT_READ")
  const canUpload = usePermission("CLINIC_DOCUMENT_CREATE")
  const canDownload = usePermission("CLINIC_DOCUMENT_DOWNLOAD")
  const canRemove = usePermission("CLINIC_DOCUMENT_VOID")
  const canSick = usePermission("CLINIC_SICK_NOTE_SIGN")
  const canRef = usePermission("CLINIC_REFERRAL_SIGN")
  const key = ["pf-documents", patient.id]
  const { data: items = [], isLoading, isError } = useQuery<RegisterItem[]>({ queryKey: key, enabled: canRead, retry: false,
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/clinic/patients/${patient.id}/documents`))?.items ?? [] })
  const [kind, setKind] = useState("")
  const [modal, setModal] = useState<"upload" | "sick" | "referral" | null>(null)
  const [removing, setRemoving] = useState<RegisterItem | null>(null)
  const [dlError, setDlError] = useState("")
  const latest = consultations[0]
  const shown = kind ? items.filter(i => i.docType === kind) : items
  const reload = () => qc.invalidateQueries({ queryKey: key })

  const download = async (i: RegisterItem) => {
    setDlError("")
    try { await saveFile(i.downloadPath, i.fileName || `${i.docType.toLowerCase()}-${i.id}.pdf`) } catch { setDlError("The document could not be downloaded.") }
  }

  if (!canRead) return <div style={{ fontSize: 13, color: GRAY }}>You do not have permission to view this patient's documents.</div>

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, flexWrap: "wrap", marginBottom: 14 }}>
        <div style={{ fontSize: 15, fontWeight: 700, color: "var(--hf-text)" }}>{items.length} document{items.length !== 1 ? "s" : ""}</div>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          {canSick && latest && <button type="button" style={smallBtn} onClick={() => setModal("sick")}>Issue sick note</button>}
          {canRef && latest && <button type="button" style={smallBtn} onClick={() => setModal("referral")}>Write referral</button>}
          {canUpload && <button type="button" style={primaryBtn} onClick={() => setModal("upload")}><Upload size={13} /> Upload document</button>}
        </div>
      </div>

      {items.length > 0 && (
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginBottom: 12 }}>
          {[{ type: "", count: items.length }, ...kindCounts(items)].map(k => {
            const on = kind === k.type
            return <button key={k.type || "all"} type="button" aria-pressed={on} onClick={() => setKind(k.type)}
              style={{ padding: "4px 10px", borderRadius: 20, fontSize: 12, fontWeight: 600, cursor: "pointer", border: `1px solid ${BORDER}`,
                background: on ? "var(--hf-accent-soft)" : "transparent", color: on ? "var(--hf-accent-text)" : GRAY }}>
              {k.type ? typeLabel(k.type) : "All"} ({k.count})</button>
          })}
        </div>)}
      {dlError && <div role="alert" style={{ fontSize: 13, color: RED_TEXT, marginBottom: 8 }}>{dlError}</div>}

      {isLoading ? <div style={{ color: GRAY, fontSize: 13 }}>Loading documents…</div>
        : isError ? <div role="alert" style={{ color: RED_TEXT, fontSize: 13 }}>The documents could not be loaded.</div>
        : items.length === 0 ? <Empty icon={FileText} msg="No documents yet"><div style={{ fontSize: 13, color: GRAY, marginTop: 4 }}>Upload a letter or scan, or issue a sick note. Visit summaries, prescriptions and lab reports appear here as they are created.</div></Empty>
        : (
          <div style={{ border: `1px solid ${BORDER}`, borderRadius: 12, overflow: "hidden" }}>
            {shown.map((i, n) => (
              <div key={`${i.origin}-${i.docType}-${i.id}`} style={{ display: "flex", alignItems: "center", gap: 12, padding: "10px 14px", flexWrap: "wrap",
                borderTop: n ? `1px solid ${BORDER}` : undefined, background: n % 2 ? LIGHT : "transparent" }}>
                <div style={{ width: 84, fontSize: 12, color: GRAY }}>{i.date ? fmtDay(`${i.date}T12:00:00+02:00`) : "—"}</div>
                <div style={{ flex: 1, minWidth: 180 }}>
                  <div style={{ fontSize: 13, fontWeight: 600, color: "var(--hf-text)" }}>{i.title}</div>
                  <div style={{ fontSize: 12, color: GRAY }}>
                    {typeLabel(i.docType)} · {sourceLabel(i)}{i.addedBy ? ` by ${i.addedBy}` : ""}{i.sizeBytes ? ` · ${sizeLabel(i.sizeBytes)}` : ""}{i.notes ? ` · ${i.notes}` : ""}
                  </div>
                </div>
                {canDownload && <button type="button" style={smallBtn} aria-label={`Download ${i.title}`} onClick={() => download(i)}><Download size={12} /> Download</button>}
                {canRemove && i.removable && <button type="button" style={smallBtn} aria-label={`Remove ${i.title}`} onClick={() => setRemoving(i)}><Trash2 size={12} /></button>}
              </div>))}
            {shown.length === 0 && <div style={{ padding: 14, fontSize: 13, color: GRAY }}>Nothing of this kind.</div>}
          </div>)}

      {modal === "upload" && <UploadModal patientId={patient.id} consultations={consultations} onClose={() => setModal(null)} />}
      {modal === "sick" && latest && <SickNoteModal patientId={patient.id} list={consultations.map(c => ({ id: c.id, consultedAt: c.consultedAt, chiefComplaint: c.chiefComplaint }))} onClose={() => { setModal(null); reload() }} />}
      {modal === "referral" && latest && <ReferralLetterModal consultationId={latest.id} onClose={() => { setModal(null); reload() }} />}
      {removing && <RemoveModal patientId={patient.id} item={removing} onClose={() => setRemoving(null)} />}
    </div>
  )
}
