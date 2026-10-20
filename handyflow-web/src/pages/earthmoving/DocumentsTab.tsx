// src/pages/earthmoving/DocumentsTab.tsx
//
// Documents for a machine (photos, manuals, insurance, warranties) or an operator (certificates). Files are checked by the server (real
// file type, 10 MB, malware hook), stored privately, and kept as numbered versions: a new file is a new version and the old one stays.
// Insurance, warranties and certificates need an expiry date and an administrator; the server decides what each person may see, so a
// person without access simply sees fewer rows. Expiring documents raise an alert 30 and 7 days ahead and again on the day.
import { useState } from "react"
import { FileText } from "lucide-react"
import { apiMessage, fmtDate, fmtDateTime } from "./shared/format"
import { Overlay, ModalHead, ModalFoot, ErrBanner, lbl, inputStyle } from "./shared/Modal"
import { DOCUMENT_CATEGORY_LABEL, DOCUMENT_CATEGORIES, SENSITIVE_DOCUMENT_CATEGORIES, MAX_DOCUMENT_BYTES } from "./shared/constants"
import { downloadDocument } from "./shared/api"
import { useEarthPerms } from "./shared/useEarthPerms"
import {
  useAssets, useAssignableUsers, useDocuments, useExpiringDocuments, useDocumentVersions,
  useUploadDocument, useAddDocumentVersion, useUpdateDocument, useArchiveDocument,
} from "./shared/hooks"
import type { EarthDocument } from "./shared/types"

type Kind = "ASSET" | "OPERATOR"

const btn = (primary = false): React.CSSProperties => ({
  padding: "6px 12px", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer",
  border: primary ? "none" : "1px solid var(--hf-border)",
  background: primary ? "var(--hf-primary)" : "var(--hf-surface)",
  color: primary ? "var(--hf-text-on-solid)" : "var(--hf-text-secondary)",
})
const card: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 10, padding: "12px 16px", background: "var(--hf-surface)" }
const today = () => new Date().toISOString().slice(0, 10)
const size = (n: number) => (n >= 1024 * 1024 ? `${(n / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(n / 1024))} KB`)

/** Days until the date (negative once passed). */
const daysTo = (iso: string) => Math.round((new Date(iso + "T00:00:00").getTime() - new Date(today() + "T00:00:00").getTime()) / 86_400_000)
function Expiry({ on }: { on: string | null }) {
  if (!on) return <span style={{ color: "var(--hf-text-muted)" }}>No expiry</span>
  const d = daysTo(on)
  const color = d < 0 ? "var(--hf-danger)" : d <= 30 ? "var(--hf-warning)" : "var(--hf-text-secondary)"
  const note = d < 0 ? `expired ${-d} day${d === -1 ? "" : "s"} ago` : d === 0 ? "expires today" : `in ${d} day${d === 1 ? "" : "s"}`
  return <span style={{ color, fontWeight: d <= 30 ? 600 : 400 }}>{fmtDate(on)} · {note}</span>
}

function problem(file: File | null, category: string, expiresOn: string): string {
  if (!file) return "Choose a file."
  if (file.size === 0) return "That file is empty."
  if (file.size > MAX_DOCUMENT_BYTES) return "That file is larger than 10 MB."
  if (SENSITIVE_DOCUMENT_CATEGORIES.includes(category) && !expiresOn) return "This kind of document needs an expiry date."
  return ""
}

function UploadDialog({ kind, ownerId, onClose }: { kind: Kind; ownerId: string; onClose: () => void }) {
  const categories = DOCUMENT_CATEGORIES[kind]
  const [category, setCategory] = useState(categories[0])
  const [title, setTitle] = useState("")
  const [description, setDescription] = useState("")
  const [expiresOn, setExpiresOn] = useState("")
  const [file, setFile] = useState<File | null>(null)
  const [err, setErr] = useState("")
  const upload = useUploadDocument()

  const submit = () => {
    const p = title.trim() === "" ? "Give the document a title." : problem(file, category, expiresOn)
    if (p) { setErr(p); return }
    setErr("")
    upload.mutate({ kind, ownerId, category, title: title.trim(), description: description.trim() || null, expiresOn: expiresOn || null, file: file as File },
      { onSuccess: onClose, onError: (e: unknown) => setErr(apiMessage(e, "Could not add the document")) })
  }

  return (
    <Overlay onClose={onClose} width={520}>
      <ModalHead title="Add a document" onClose={onClose} />
      {err && <ErrBanner msg={err} />}
      <label style={lbl} htmlFor="doc-cat">Kind of document</label>
      <select id="doc-cat" style={inputStyle()} value={category} onChange={e => setCategory(e.target.value)}>
        {categories.map(c => <option key={c} value={c}>{DOCUMENT_CATEGORY_LABEL[c]}</option>)}
      </select>
      <label style={{ ...lbl, marginTop: 12 }} htmlFor="doc-title">Title</label>
      <input id="doc-title" style={inputStyle()} value={title} onChange={e => setTitle(e.target.value)} />
      <label style={{ ...lbl, marginTop: 12 }} htmlFor="doc-desc">Description (optional)</label>
      <input id="doc-desc" style={inputStyle()} value={description} onChange={e => setDescription(e.target.value)} />
      <label style={{ ...lbl, marginTop: 12 }} htmlFor="doc-exp">
        Expiry date{SENSITIVE_DOCUMENT_CATEGORIES.includes(category) ? "" : " (optional)"}
      </label>
      <input id="doc-exp" type="date" style={inputStyle()} value={expiresOn} onChange={e => setExpiresOn(e.target.value)} />
      <label style={{ ...lbl, marginTop: 12 }} htmlFor="doc-file">File (PDF, image, Word or Excel, up to 10 MB)</label>
      <input id="doc-file" type="file" onChange={e => setFile(e.target.files?.[0] ?? null)} />
      <ModalFoot onCancel={onClose} onSubmit={submit} loading={upload.isPending} label="Add document" />
    </Overlay>
  )
}

function VersionDialog({ doc, onClose }: { doc: EarthDocument; onClose: () => void }) {
  const [note, setNote] = useState("")
  const [expiresOn, setExpiresOn] = useState(doc.expiresOn ?? "")
  const [file, setFile] = useState<File | null>(null)
  const [err, setErr] = useState("")
  const add = useAddDocumentVersion()
  const submit = () => {
    const p = problem(file, doc.category, expiresOn)
    if (p) { setErr(p); return }
    setErr("")
    add.mutate({ id: doc.id, note: note.trim() || null, expiresOn: expiresOn || null, file: file as File },
      { onSuccess: onClose, onError: (e: unknown) => setErr(apiMessage(e, "Could not add the version")) })
  }
  return (
    <Overlay onClose={onClose} width={520}>
      <ModalHead title={`New version of ${doc.title}`} subtitle={`Replaces version ${doc.currentVersion} as the current one; the old file is kept.`} onClose={onClose} />
      {err && <ErrBanner msg={err} />}
      <label style={lbl} htmlFor="ver-file">File</label>
      <input id="ver-file" type="file" onChange={e => setFile(e.target.files?.[0] ?? null)} />
      <label style={{ ...lbl, marginTop: 12 }} htmlFor="ver-exp">Expiry date (change it when this is a renewal)</label>
      <input id="ver-exp" type="date" style={inputStyle()} value={expiresOn} onChange={e => setExpiresOn(e.target.value)} />
      <label style={{ ...lbl, marginTop: 12 }} htmlFor="ver-note">Note (optional)</label>
      <input id="ver-note" style={inputStyle()} value={note} onChange={e => setNote(e.target.value)} />
      <ModalFoot onCancel={onClose} onSubmit={submit} loading={add.isPending} label="Add version" />
    </Overlay>
  )
}

function EditDialog({ doc, onClose }: { doc: EarthDocument; onClose: () => void }) {
  const [title, setTitle] = useState(doc.title)
  const [description, setDescription] = useState(doc.description ?? "")
  const [expiresOn, setExpiresOn] = useState(doc.expiresOn ?? "")
  const [err, setErr] = useState("")
  const update = useUpdateDocument()
  const submit = () => {
    if (title.trim() === "") { setErr("Give the document a title."); return }
    if (SENSITIVE_DOCUMENT_CATEGORIES.includes(doc.category) && !expiresOn) { setErr("This kind of document needs an expiry date."); return }
    setErr("")
    update.mutate({ id: doc.id, title: title.trim(), description: description.trim() || null, expiresOn: expiresOn || null },
      { onSuccess: onClose, onError: (e: unknown) => setErr(apiMessage(e, "Could not save the changes")) })
  }
  return (
    <Overlay onClose={onClose} width={480}>
      <ModalHead title="Edit document details" onClose={onClose} />
      {err && <ErrBanner msg={err} />}
      <label style={lbl} htmlFor="ed-title">Title</label>
      <input id="ed-title" style={inputStyle()} value={title} onChange={e => setTitle(e.target.value)} />
      <label style={{ ...lbl, marginTop: 12 }} htmlFor="ed-desc">Description</label>
      <input id="ed-desc" style={inputStyle()} value={description} onChange={e => setDescription(e.target.value)} />
      <label style={{ ...lbl, marginTop: 12 }} htmlFor="ed-exp">Expiry date</label>
      <input id="ed-exp" type="date" style={inputStyle()} value={expiresOn} onChange={e => setExpiresOn(e.target.value)} />
      <ModalFoot onCancel={onClose} onSubmit={submit} loading={update.isPending} label="Save" />
    </Overlay>
  )
}

function ArchiveDialog({ doc, onClose }: { doc: EarthDocument; onClose: () => void }) {
  const [reason, setReason] = useState("")
  const [err, setErr] = useState("")
  const archive = useArchiveDocument()
  const submit = () => {
    if (reason.trim().length < 3) { setErr("Say why it is being archived."); return }
    setErr("")
    archive.mutate({ id: doc.id, reason: reason.trim() }, { onSuccess: onClose, onError: (e: unknown) => setErr(apiMessage(e, "Could not archive the document")) })
  }
  return (
    <Overlay onClose={onClose} width={460}>
      <ModalHead title={`Archive ${doc.title}`} subtitle="It stays on record with all its versions but can no longer be changed, and its expiry alerts close." onClose={onClose} />
      {err && <ErrBanner msg={err} />}
      <label style={lbl} htmlFor="arch-reason">Reason</label>
      <input id="arch-reason" style={inputStyle()} value={reason} onChange={e => setReason(e.target.value)} />
      <ModalFoot onCancel={onClose} onSubmit={submit} loading={archive.isPending} label="Archive" />
    </Overlay>
  )
}

function VersionsDialog({ doc, onClose, onError }: { doc: EarthDocument; onClose: () => void; onError: (m: string) => void }) {
  const { data = [], isLoading } = useDocumentVersions(doc.id)
  const get = (v: number, name: string) => downloadDocument(doc.id, v, name).catch((e: unknown) => onError(apiMessage(e, "Could not download the file")))
  return (
    <Overlay onClose={onClose} width={620}>
      <ModalHead title={`Versions of ${doc.title}`} onClose={onClose} />
      {isLoading ? <p>Loading…</p> : (
        <table style={{ width: "100%", fontSize: 13, borderCollapse: "collapse" }}>
          <thead><tr style={{ textAlign: "left", color: "var(--hf-text-muted)" }}><th>Version</th><th>File</th><th>Added</th><th>Note</th><th /></tr></thead>
          <tbody>
            {data.map(v => (
              <tr key={v.id} style={{ borderTop: "1px solid var(--hf-border)" }}>
                <td>{v.versionNo}{v.versionNo === doc.currentVersion ? " (current)" : ""}</td>
                <td>{v.fileName} · {size(v.sizeBytes)}</td>
                <td>{fmtDateTime(v.uploadedAt)}</td>
                <td>{v.note ?? ""}</td>
                <td><button style={btn()} onClick={() => get(v.versionNo, v.fileName)}>Download</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Overlay>
  )
}

export default function DocumentsTab() {
  const { canWrite, canAdmin } = useEarthPerms()
  const [kind, setKind] = useState<Kind>("ASSET")
  const [ownerId, setOwnerId] = useState("")
  const [showArchived, setShowArchived] = useState(false)
  const [dialog, setDialog] = useState<{ type: "upload" | "version" | "edit" | "archive" | "versions"; doc?: EarthDocument } | null>(null)
  const [err, setErr] = useState("")

  const assets = useAssets()
  const users = useAssignableUsers()
  const docs = useDocuments(kind, ownerId, showArchived)
  const expiring = useExpiringDocuments(60)

  const owners: { id: string; name: string }[] =
    kind === "ASSET" ? (assets.data ?? []).map(a => ({ id: a.id, name: `${a.fleetNumber ? a.fleetNumber + " · " : ""}${a.name}` })) : (users.data ?? [])
  const ownerName = (id: string) => owners.find(o => o.id === id)?.name ?? ""
  const list = docs.data ?? []
  const mayChange = (d: EarthDocument) => (SENSITIVE_DOCUMENT_CATEGORIES.includes(d.category) ? canAdmin : canWrite)

  const download = (d: EarthDocument) => {
    setErr("")
    downloadDocument(d.id, null, d.current?.fileName ?? d.title).catch((e: unknown) => setErr(apiMessage(e, "Could not download the file")))
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
        <FileText size={18} aria-hidden />
        <h2 style={{ margin: 0, fontSize: 18 }}>Documents</h2>
      </div>

      {(expiring.data ?? []).length > 0 && (
        <section style={card} aria-label="Expiring documents">
          <strong style={{ fontSize: 14 }}>Expiring in the next 60 days or already expired</strong>
          <ul style={{ margin: "8px 0 0", paddingLeft: 18, fontSize: 13 }}>
            {(expiring.data ?? []).map(d => (
              <li key={d.id}>{d.title} · {DOCUMENT_CATEGORY_LABEL[d.category] ?? d.category} · <Expiry on={d.expiresOn} /></li>
            ))}
          </ul>
        </section>
      )}

      <div style={{ display: "flex", gap: 12, flexWrap: "wrap", alignItems: "flex-end" }}>
        <div>
          <label style={lbl} htmlFor="doc-kind">Documents for</label>
          <select id="doc-kind" style={inputStyle()} value={kind} onChange={e => { setKind(e.target.value as Kind); setOwnerId("") }}>
            <option value="ASSET">A machine</option>
            <option value="OPERATOR">An operator</option>
          </select>
        </div>
        <div style={{ minWidth: 240 }}>
          <label style={lbl} htmlFor="doc-owner">{kind === "ASSET" ? "Machine" : "Operator"}</label>
          <select id="doc-owner" style={inputStyle()} value={ownerId} onChange={e => setOwnerId(e.target.value)}>
            <option value="">Choose…</option>
            {owners.map(o => <option key={o.id} value={o.id}>{o.name}</option>)}
          </select>
        </div>
        <label style={{ fontSize: 13, display: "flex", gap: 6, alignItems: "center" }}>
          <input type="checkbox" checked={showArchived} onChange={e => setShowArchived(e.target.checked)} /> Show archived
        </label>
        {ownerId && (kind === "ASSET" ? canWrite : canAdmin) && (
          <button style={btn(true)} onClick={() => setDialog({ type: "upload" })}>Add document</button>
        )}
      </div>

      {err && <ErrBanner msg={err} />}

      {!ownerId ? (
        <p style={{ color: "var(--hf-text-muted)" }}>Choose a {kind === "ASSET" ? "machine" : "operator"} to see its documents.</p>
      ) : docs.isLoading ? (
        <p>Loading…</p>
      ) : list.length === 0 ? (
        <p style={{ color: "var(--hf-text-muted)" }}>No documents for {ownerName(ownerId) || "this one"} yet.</p>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {list.map(d => (
            <div key={d.id} style={{ ...card, opacity: d.status === "ARCHIVED" ? 0.7 : 1 }}>
              <div style={{ display: "flex", justifyContent: "space-between", gap: 12, flexWrap: "wrap" }}>
                <div>
                  <strong>{d.title}</strong>{" "}
                  <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
                    {DOCUMENT_CATEGORY_LABEL[d.category] ?? d.category} · version {d.currentVersion}{d.status === "ARCHIVED" ? " · Archived" : ""}
                  </span>
                  {d.description && <div style={{ fontSize: 13 }}>{d.description}</div>}
                  <div style={{ fontSize: 13 }}><Expiry on={d.expiresOn} /></div>
                  {d.current && <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{d.current.fileName} · {size(d.current.sizeBytes)} · added {fmtDate(d.current.uploadedAt)}</div>}
                  {d.archivedReason && <div style={{ fontSize: 12 }}>Archived: {d.archivedReason}</div>}
                </div>
                <div style={{ display: "flex", gap: 6, alignItems: "flex-start", flexWrap: "wrap" }}>
                  <button style={btn()} onClick={() => download(d)}>Download</button>
                  <button style={btn()} onClick={() => setDialog({ type: "versions", doc: d })}>Versions</button>
                  {d.status !== "ARCHIVED" && mayChange(d) && (
                    <>
                      <button style={btn()} onClick={() => setDialog({ type: "version", doc: d })}>New version</button>
                      <button style={btn()} onClick={() => setDialog({ type: "edit", doc: d })}>Edit</button>
                    </>
                  )}
                  {d.status !== "ARCHIVED" && canAdmin && <button style={btn()} onClick={() => setDialog({ type: "archive", doc: d })}>Archive</button>}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {dialog?.type === "upload" && <UploadDialog kind={kind} ownerId={ownerId} onClose={() => setDialog(null)} />}
      {dialog?.type === "version" && dialog.doc && <VersionDialog doc={dialog.doc} onClose={() => setDialog(null)} />}
      {dialog?.type === "edit" && dialog.doc && <EditDialog doc={dialog.doc} onClose={() => setDialog(null)} />}
      {dialog?.type === "archive" && dialog.doc && <ArchiveDialog doc={dialog.doc} onClose={() => setDialog(null)} />}
      {dialog?.type === "versions" && dialog.doc && <VersionsDialog doc={dialog.doc} onClose={() => setDialog(null)} onError={setErr} />}
    </div>
  )
}
