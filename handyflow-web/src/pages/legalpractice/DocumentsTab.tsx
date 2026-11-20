// Matter documents with version history. Every upload is a numbered version that is kept for good; a person can check a document out so
// nobody else adds a version while they work; a document under legal hold cannot be archived. Every download and change is logged.
import { useRef, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { filenameFrom, saveBlob } from "../../api/recipientAccess.client"
import { usePermissions } from "../../hooks/usePermission"
import { useAuthStore } from "../../store/auth.store"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, Empty, ErrorLine, Input, Pager, Pick, Pill, SectionTitle, card, day, errorText, fail, muted, pageOf, td, th } from "./kit"
import { BASE } from "./lookups"
import type { Matter } from "./MattersTab"
import {
  DOC_ACCEPT, DOC_CATEGORIES, fileSize, docActions, docBadges, docCategoryLabel, docEventText, docFileProblem, docReasonProblem, docUploadProblem, emptyDocUpload, shortHash, titleFromFile,
  type DocDetail, type DocSummary, type DocUploadForm, type DocVersion,
} from "./legal.logic"

const URL = `${BASE}/documents`
const unwrap = <T,>(r: { data?: { data?: T } & T }): T => (r.data?.data ?? r.data) as T

export default function DocumentsTab() {
  const [search, setSearch] = useState(""), [category, setCategory] = useState(""), [archived, setArchived] = useState(false), [page, setPage] = useState(0)
  const [uploading, setUploading] = useState(false), [openId, setOpenId] = useState<string | null>(null)
  const canWrite = usePermissions(["LEGALPRACTICE_DOCUMENT_WRITE", "LEGALPRACTICE_ADMIN"])
  const list = useQuery({
    queryKey: ["lp-docs", search, category, archived, page],
    queryFn: async () => pageOf<DocSummary>(await apiClient.get(URL, { params: { search: search.trim() || undefined, category: category || undefined, includeArchived: archived, page, size: 20 } })),
  })
  const rows = list.data?.content ?? []
  return (
    <div>
      <SectionTitle right={canWrite && <Btn kind="primary" onClick={() => setUploading(true)}>Add a document</Btn>}>Documents</SectionTitle>
      <div style={{ ...muted, marginBottom: 10 }}>Matter documents are kept in versions. Nothing is deleted: a new version is added and the earlier ones stay. Every download and change is recorded.</div>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "flex-end", marginBottom: 8 }}>
        <div style={{ minWidth: 200, flex: "1 1 200px" }}><Input label="Search by title" value={search} onChange={v => { setSearch(v); setPage(0) }} /></div>
        <div style={{ minWidth: 180 }}><Pick label="Kind" value={category} onChange={v => { setCategory(v); setPage(0) }} blank="All kinds" options={DOC_CATEGORIES} /></div>
        <label style={{ fontSize: 13, display: "inline-flex", gap: 6, alignItems: "center", marginBottom: 14 }}>
          <input type="checkbox" checked={archived} onChange={e => { setArchived(e.target.checked); setPage(0) }} /> Include archived
        </label>
      </div>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {list.isError ? <Empty>Could not load the documents.</Empty> : list.isLoading ? <Empty>Loading…</Empty> : rows.length === 0 ? <Empty>No documents found.</Empty> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Document</th><th style={th}>Matter</th><th style={th}>Version</th><th style={th}>Updated</th><th style={th} /></tr></thead>
            <tbody>{rows.map(d => (
              <tr key={d.id}>
                <td style={td}><b>{d.title}</b><div style={muted}>{docCategoryLabel(d.category)}</div>
                  <div style={{ display: "flex", gap: 4, flexWrap: "wrap", marginTop: 3 }}>{docBadges(d).map(b => <Pill key={b.label} tone={b.tone}>{b.label}</Pill>)}</div></td>
                <td style={td}>{d.matterLabel}</td>
                <td style={td}>{d.currentVersion}</td>
                <td style={td}>{day(d.updatedAt)}</td>
                <td style={{ ...td, textAlign: "right" }}><Btn small onClick={() => setOpenId(d.id)}>Open</Btn></td>
              </tr>))}</tbody>
          </table>)}
      </div>
      <Pager page={page} pages={list.data?.totalPages ?? 0} onPage={setPage} />
      {uploading && <UploadDialog onClose={() => setUploading(false)} />}
      {openId && <DetailDialog id={openId} onClose={() => setOpenId(null)} />}
    </div>
  )
}

// ---- add a document ---------------------------------------------------------------------------------------------------------

function UploadDialog({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient(), input = useRef<HTMLInputElement>(null)
  const [f, setF] = useState<DocUploadForm>(emptyDocUpload()), [file, setFile] = useState<File | null>(null), [err, setErr] = useState("")
  const set = (k: keyof DocUploadForm) => (v: string) => setF(s => ({ ...s, [k]: v }))
  const matters = useQuery({ queryKey: ["lp-doc-matters"], queryFn: async () => pageOf<Matter>(await apiClient.get(`${BASE}/matters`, { params: { size: 500 } })) })
  const problem = docUploadProblem(f, file)
  const save = useMutation({
    mutationFn: () => {
      const fd = new FormData()
      fd.append("file", file!, file!.name); fd.append("title", f.title.trim()); fd.append("category", f.category)
      if (f.note.trim()) fd.append("note", f.note.trim())
      return apiClient.post(`${URL}/matters/${f.matterId}`, fd)
    },
    onSuccess: () => { toast.success("Document added"); qc.invalidateQueries({ queryKey: ["lp-docs"] }); onClose() },
    onError: async e => setErr(await errorText(e, "Could not add the document")),
  })
  return (
    <Dialog title="Add a document" onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Uploading…" : "Add"}</Btn></>}>
      <Pick label="Matter" value={f.matterId} onChange={set("matterId")} blank={matters.isLoading ? "Loading…" : "Choose a matter"} options={(matters.data?.content ?? []).map(m => [m.id, `${m.matterNumber} ${m.matterName}`] as const)} />
      <div style={{ marginBottom: 12 }}>
        <input ref={input} type="file" accept={DOC_ACCEPT} onChange={e => { const x = e.target.files?.[0] ?? null; setFile(x); if (x && !f.title.trim()) set("title")(titleFromFile(x.name)) }} />
        {file && <div style={muted}>{file.name} · {fileSize(file.size)}</div>}
        {file && docFileProblem(file) && <div style={{ ...muted, color: "var(--hf-danger-text)" }}>{docFileProblem(file)}</div>}
      </div>
      <Input label="Title" value={f.title} onChange={set("title")} />
      <Pick label="Kind" value={f.category} onChange={set("category")} options={DOC_CATEGORIES} />
      <Input label="Note on this version (optional)" value={f.note} onChange={set("note")} />
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

// ---- one document -------------------------------------------------------------------------------------------------------------

type Step = null | "version" | "checkin" | "edit" | { reason: "hold" | "release" | "archive" | "override" }

function DetailDialog({ id, onClose }: { id: string; onClose: () => void }) {
  const qc = useQueryClient()
  const me = useAuthStore(s => s.user?.userId)
  const write = usePermissions(["LEGALPRACTICE_DOCUMENT_WRITE", "LEGALPRACTICE_ADMIN"])
  const hold = usePermissions(["LEGALPRACTICE_DOCUMENT_HOLD", "LEGALPRACTICE_ADMIN"])
  const override = usePermissions(["LEGALPRACTICE_DOCUMENT_OVERRIDE", "LEGALPRACTICE_ADMIN"])
  const [step, setStep] = useState<Step>(null), [err, setErr] = useState("")
  const q = useQuery({ queryKey: ["lp-doc", id], queryFn: async () => unwrap<DocDetail>(await apiClient.get(`${URL}/${id}`)) })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["lp-doc", id] }); qc.invalidateQueries({ queryKey: ["lp-docs"] }) }
  const d = q.data
  const a = d ? docActions(d.document, { write, hold, override }, me) : null
  const act = (path: string, ok: string) => async () => { setErr(""); try { await apiClient.post(`${URL}/${id}/${path}`); toast.success(ok); refresh() } catch (e) { setErr(await errorText(e, "That did not work")) } }
  const download = useMutation({
    mutationFn: async (v: DocVersion) => {
      const r = await apiClient.get(`${URL}/${id}/download`, { params: { version: v.versionNo }, responseType: "blob" })
      saveBlob(r.data as Blob, filenameFrom(r.headers?.["content-disposition"]) || v.fileName)
      refresh()
    },
    onError: e => { void fail(e, "Could not download that file") },
  })
  const verify = useMutation({
    mutationFn: async (v: DocVersion) => unwrap<{ matches: boolean }>(await apiClient.post(`${URL}/${id}/verify`, null, { params: { version: v.versionNo } })),
    onSuccess: r => { if (r.matches) toast.success("The file matches the hash taken when it was received"); else toast.error("THE FILE DOES NOT MATCH the hash taken when it was received. Tell the person responsible for records."); refresh() },
    onError: e => { void fail(e, "Could not check that file") },
  })
  return (
    <Dialog title={d ? d.document.title : "Document"} width={760} onClose={onClose}>
      {q.isError ? <Empty>Could not load this document.</Empty> : !d || !a ? <Empty>Loading…</Empty> : (
        <div>
          <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginBottom: 6 }}>
            <Pill tone="neutral">{docCategoryLabel(d.document.category)}</Pill>
            <Pill tone="info">Version {d.document.currentVersion}</Pill>
            {docBadges(d.document).map(b => <Pill key={b.label} tone={b.tone}>{b.label}</Pill>)}
          </div>
          <div style={muted}>{d.document.matterLabel} · added by {d.createdByName ?? "—"} on {day(d.createdAt)}</div>
          {d.document.legalHold && <div style={{ ...muted, marginTop: 6 }}>Legal hold since {day(d.holdSetAt)} ({d.holdSetByName ?? "—"}): {d.holdReason}</div>}
          {d.document.status === "ARCHIVED" && <div style={{ ...muted, marginTop: 6 }}>Archived: {d.archivedReason}</div>}
          {d.document.checkedOutByName && <div style={{ ...muted, marginTop: 6 }}>Checked out by {d.document.checkedOutByName} since {day(d.document.checkedOutAt)}. Others cannot add a version until it is checked in.</div>}
          {err && <ErrorLine text={err} />}

          <div style={{ display: "flex", gap: 6, flexWrap: "wrap", margin: "12px 0" }}>
            {a.canCheckOut && <Btn small onClick={act("checkout", "Checked out")}>Check out</Btn>}
            {a.canCheckIn && <Btn small kind="primary" onClick={() => setStep("checkin")}>Check in a new version</Btn>}
            {a.canCancelCheckout && <Btn small onClick={act("cancel-checkout", "Check-out cancelled")}>Cancel check-out</Btn>}
            {a.canAddVersion && !a.canCheckIn && <Btn small onClick={() => setStep("version")}>Add a new version</Btn>}
            {a.canOverride && <Btn small onClick={() => setStep({ reason: "override" })}>Release their check-out</Btn>}
            {a.canEdit && <Btn small onClick={() => setStep("edit")}>Change title or kind</Btn>}
            {a.canHold && <Btn small onClick={() => setStep({ reason: "hold" })}>Place under legal hold</Btn>}
            {a.canReleaseHold && <Btn small onClick={() => setStep({ reason: "release" })}>Release legal hold</Btn>}
            {a.canArchive && <Btn small onClick={() => setStep({ reason: "archive" })}>Archive</Btn>}
            {a.canRestore && <Btn small onClick={act("restore", "Restored")}>Restore</Btn>}
          </div>
          {a.archiveBlockedBy && write && <div style={{ ...muted, marginBottom: 8 }}>This cannot be archived. {a.archiveBlockedBy}</div>}

          <div style={{ fontWeight: 700, margin: "6px 0" }}>Versions</div>
          <div style={{ ...card, padding: 0, overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr><th style={th}>Version</th><th style={th}>File</th><th style={th}>Added</th><th style={th} /></tr></thead>
              <tbody>{d.versions.map(v => (
                <tr key={v.versionNo}>
                  <td style={td}>{v.versionNo}{v.versionNo === d.document.currentVersion ? <div style={muted}>Current</div> : null}</td>
                  <td style={td}><b style={{ wordBreak: "break-word" }}>{v.fileName}</b><div style={muted}>{fileSize(v.sizeBytes)} · {shortHash(v.sha256)}</div>{v.changeNote && <div style={muted}>{v.changeNote}</div>}</td>
                  <td style={td}>{day(v.uploadedAt)}<div style={muted}>{v.uploadedByName ?? ""}</div></td>
                  <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}>
                    <Btn small disabled={download.isPending} onClick={() => download.mutate(v)}>Download</Btn>{" "}
                    <Btn small disabled={verify.isPending} onClick={() => verify.mutate(v)}>Check file</Btn>
                  </td>
                </tr>))}</tbody>
            </table>
          </div>

          <div style={{ fontWeight: 700, margin: "14px 0 6px" }}>History</div>
          <div style={{ ...card, padding: 10, maxHeight: 220, overflowY: "auto" }}>
            {d.events.map((e, i) => <div key={i} style={{ fontSize: 13, padding: "3px 0" }}><span style={muted}>{day(e.occurredAt)}</span> {docEventText(e)}</div>)}
          </div>

          {(step === "version" || step === "checkin") && <VersionDialog id={id} checkIn={step === "checkin"} onClose={() => setStep(null)} onDone={() => { setStep(null); refresh() }} />}
          {step === "edit" && <EditDialog doc={d.document} onClose={() => setStep(null)} onDone={() => { setStep(null); refresh() }} />}
          {step && typeof step === "object" && <ReasonDialog kind={step.reason} id={id} onClose={() => setStep(null)} onDone={() => { setStep(null); refresh() }} />}
        </div>)}
    </Dialog>
  )
}

function VersionDialog({ id, checkIn, onClose, onDone }: { id: string; checkIn: boolean; onClose: () => void; onDone: () => void }) {
  const [file, setFile] = useState<File | null>(null), [note, setNote] = useState(""), [err, setErr] = useState("")
  const problem = docFileProblem(file)
  const save = useMutation({
    mutationFn: () => {
      const fd = new FormData()
      fd.append("file", file!, file!.name)
      if (note.trim()) fd.append("note", note.trim())
      return apiClient.post(`${URL}/${id}/${checkIn ? "checkin" : "versions"}`, fd)
    },
    onSuccess: () => { toast.success(checkIn ? "Checked in" : "Version added"); onDone() },
    onError: async e => setErr(await errorText(e, "Could not add the version")),
  })
  return (
    <Dialog title={checkIn ? "Check in a new version" : "Add a new version"} onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Uploading…" : checkIn ? "Check in" : "Add"}</Btn></>}>
      <div style={muted}>The earlier versions are kept.{checkIn ? " This releases your check-out." : ""}</div>
      <div style={{ margin: "10px 0" }}>
        <input type="file" accept={DOC_ACCEPT} onChange={e => setFile(e.target.files?.[0] ?? null)} />
        {file && <div style={muted}>{file.name} · {fileSize(file.size)}</div>}
        {file && problem && <div style={{ ...muted, color: "var(--hf-danger-text)" }}>{problem}</div>}
      </div>
      <Input label="What changed (optional)" value={note} onChange={setNote} />
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

function EditDialog({ doc, onClose, onDone }: { doc: DocSummary; onClose: () => void; onDone: () => void }) {
  const [title, setTitle] = useState(doc.title), [category, setCategory] = useState(doc.category), [err, setErr] = useState("")
  const problem = title.trim().length < 2 ? "Give the document a title" : null
  const save = useMutation({
    mutationFn: () => apiClient.put(`${URL}/${doc.id}`, { title: title.trim(), category }),
    onSuccess: () => { toast.success("Saved"); onDone() },
    onError: async e => setErr(await errorText(e, "Could not save")),
  })
  return (
    <Dialog title="Change title or kind" onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Save"}</Btn></>}>
      <Input label="Title" value={title} onChange={setTitle} />
      <Pick label="Kind" value={category} onChange={setCategory} options={DOC_CATEGORIES} />
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

const REASONS = {
  hold: { title: "Place under legal hold", path: "hold", what: "placing it under hold", ok: "Placed under legal hold", intro: "While it is under hold it cannot be archived. Everyone can still read it and add versions. The reason is kept permanently." },
  release: { title: "Release legal hold", path: "release-hold", what: "releasing the hold", ok: "Legal hold released", intro: "The document can be archived again. The reason is kept permanently." },
  archive: { title: "Archive this document", path: "archive", what: "archiving it", ok: "Archived", intro: "An archived document is read-only. It is not deleted and can be restored." },
  override: { title: "Release the check-out", path: "override-checkout", what: "releasing their check-out", ok: "Check-out released", intro: "The person who had it will no longer be able to check it in. This is recorded with your reason." },
} as const

function ReasonDialog({ kind, id, onClose, onDone }: { kind: keyof typeof REASONS; id: string; onClose: () => void; onDone: () => void }) {
  const r = REASONS[kind]
  const [reason, setReason] = useState(""), [err, setErr] = useState("")
  const problem = docReasonProblem(reason, r.what)
  const save = useMutation({
    mutationFn: () => apiClient.post(`${URL}/${id}/${r.path}`, { reason: reason.trim() }),
    onSuccess: () => { toast.success(r.ok); onDone() },
    onError: async e => setErr(await errorText(e, "That did not work")),
  })
  return (
    <Dialog title={r.title} onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Confirm"}</Btn></>}>
      <div style={muted}>{r.intro}</div>
      <div style={{ marginTop: 10 }}><Input label="Why" value={reason} onChange={setReason} /></div>
      {problem && reason.trim() && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
