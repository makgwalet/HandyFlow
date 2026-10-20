// src/pages/earthmoving/DisposalsTab.tsx
//
// Taking a machine out of the fleet. A request says why (sold, scrapped, written off, transferred), carries evidence kept in the Document
// Service, is approved by an administrator other than the person who asked, and is completed by an administrator, which is the only thing that
// retires the machine. The server blocks it while the machine is on a deployment or hire, or has hire charges not yet invoiced, and checks again
// at every step; the screen shows those blockers so nobody is surprised.
import { useState } from "react"
import { Archive } from "lucide-react"
import { apiMessage, fmtDateTime } from "./shared/format"
import { Overlay, ModalHead, ModalFoot, ErrBanner, lbl, inputStyle } from "./shared/Modal"
import { DISPOSAL_REASON_LABEL, DISPOSAL_STATUS_LABEL, DISPOSAL_NEEDS_COUNTERPARTY, MAX_DOCUMENT_BYTES } from "./shared/constants"
import { downloadDocument } from "./shared/api"
import { useEarthPerms } from "./shared/useEarthPerms"
import { useAssets, useDisposals, useRequestDisposal, useDecideDisposal, useCompleteDisposal, useDocuments, useUploadDocument } from "./shared/hooks"
import { useAuthStore } from "../../store/auth.store"
import type { Disposal } from "./shared/types"

const btn = (primary = false): React.CSSProperties => ({
  padding: "6px 12px", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer",
  border: primary ? "none" : "1px solid var(--hf-border)",
  background: primary ? "var(--hf-primary)" : "var(--hf-surface)",
  color: primary ? "var(--hf-text-on-solid)" : "var(--hf-text-secondary)",
})
const card: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 10, padding: "12px 16px", background: "var(--hf-surface)" }
const money = (n: number | null) => (n == null ? "" : new Intl.NumberFormat("en-ZA", { style: "currency", currency: "ZAR" }).format(n))

function RequestDialog({ onClose }: { onClose: () => void }) {
  const assets = useAssets()
  const request = useRequestDisposal()
  const [assetId, setAssetId] = useState("")
  const [reason, setReason] = useState("SOLD")
  const [details, setDetails] = useState("")
  const [counterparty, setCounterparty] = useState("")
  const [proceeds, setProceeds] = useState("")
  const [err, setErr] = useState("")
  const needsWho = DISPOSAL_NEEDS_COUNTERPARTY.includes(reason)
  const usable = (assets.data ?? []).filter(a => a.status !== "RETIRED")

  const submit = () => {
    if (!assetId) { setErr("Choose the machine."); return }
    if (details.trim().length < 5) { setErr("Say why this machine is leaving the fleet."); return }
    if (needsWho && !counterparty.trim()) { setErr(`Say who the machine is ${reason === "SOLD" ? "sold" : "transferred"} to.`); return }
    if (proceeds && Number(proceeds) < 0) { setErr("The amount cannot be negative."); return }
    setErr("")
    request.mutate({ assetId, reason, details: details.trim(), counterparty: counterparty.trim() || null, proceeds: proceeds ? Number(proceeds) : null },
      { onSuccess: onClose, onError: (e: unknown) => setErr(apiMessage(e, "Could not create the request")) })
  }

  return (
    <Overlay onClose={onClose} width={520}>
      <ModalHead title="Dispose of a machine" subtitle="An administrator must approve it, and you will need to attach evidence first." onClose={onClose} />
      {err && <ErrBanner msg={err} />}
      <label style={lbl} htmlFor="dp-asset">Machine</label>
      <select id="dp-asset" style={inputStyle()} value={assetId} onChange={e => setAssetId(e.target.value)}>
        <option value="">Choose…</option>
        {usable.map(a => <option key={a.id} value={a.id}>{a.fleetNumber ? `${a.fleetNumber} · ` : ""}{a.name}</option>)}
      </select>
      <label style={{ ...lbl, marginTop: 12 }} htmlFor="dp-reason">Reason</label>
      <select id="dp-reason" style={inputStyle()} value={reason} onChange={e => setReason(e.target.value)}>
        {Object.entries(DISPOSAL_REASON_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
      </select>
      <label style={{ ...lbl, marginTop: 12 }} htmlFor="dp-details">Details</label>
      <textarea id="dp-details" style={{ ...inputStyle(), minHeight: 70 }} value={details} onChange={e => setDetails(e.target.value)} />
      <label style={{ ...lbl, marginTop: 12 }} htmlFor="dp-who">{reason === "TRANSFERRED" ? "Transferred to" : "Buyer"}{needsWho ? "" : " (optional)"}</label>
      <input id="dp-who" style={inputStyle()} value={counterparty} onChange={e => setCounterparty(e.target.value)} />
      <label style={{ ...lbl, marginTop: 12 }} htmlFor="dp-amount">Amount received (optional)</label>
      <input id="dp-amount" type="number" min="0" step="0.01" style={inputStyle()} value={proceeds} onChange={e => setProceeds(e.target.value)} />
      <ModalFoot onCancel={onClose} onSubmit={submit} loading={request.isPending} label="Submit request" />
    </Overlay>
  )
}

function NoteDialog({ d, action, onClose }: { d: Disposal; action: "approve" | "reject" | "cancel"; onClose: () => void }) {
  const decide = useDecideDisposal()
  const [note, setNote] = useState("")
  const [err, setErr] = useState("")
  const required = action !== "approve"
  const title = action === "approve" ? "Approve disposal" : action === "reject" ? "Reject disposal" : "Cancel disposal request"
  const submit = () => {
    if (required && note.trim() === "") { setErr(`Give a reason for ${action === "reject" ? "rejecting" : "cancelling"} this request.`); return }
    setErr("")
    decide.mutate({ id: d.id, action, note: note.trim() || null }, { onSuccess: onClose, onError: (e: unknown) => setErr(apiMessage(e, "Could not save")) })
  }
  return (
    <Overlay onClose={onClose} width={460}>
      <ModalHead title={title} subtitle={d.assetName ?? undefined} onClose={onClose} />
      {err && <ErrBanner msg={err} />}
      <label style={lbl} htmlFor="dn-note">{required ? "Reason" : "Note (optional)"}</label>
      <textarea id="dn-note" style={{ ...inputStyle(), minHeight: 70 }} value={note} onChange={e => setNote(e.target.value)} />
      <ModalFoot onCancel={onClose} onSubmit={submit} loading={decide.isPending} label={title} />
    </Overlay>
  )
}

function EvidenceDialog({ d, onClose }: { d: Disposal; onClose: () => void }) {
  const upload = useUploadDocument()
  const [title, setTitle] = useState("")
  const [file, setFile] = useState<File | null>(null)
  const [err, setErr] = useState("")
  const submit = () => {
    if (title.trim() === "") { setErr("Give the document a title."); return }
    if (!file) { setErr("Choose a file."); return }
    if (file.size === 0) { setErr("That file is empty."); return }
    if (file.size > MAX_DOCUMENT_BYTES) { setErr("That file is larger than 10 MB."); return }
    setErr("")
    upload.mutate({ kind: "DISPOSAL", ownerId: d.id, category: "DISPOSAL_EVIDENCE", title: title.trim(), description: null, expiresOn: null, file },
      { onSuccess: onClose, onError: (e: unknown) => setErr(apiMessage(e, "Could not add the evidence")) })
  }
  return (
    <Overlay onClose={onClose} width={480}>
      <ModalHead title="Attach evidence" subtitle="Sale agreement, scrap certificate, transfer papers, photos." onClose={onClose} />
      {err && <ErrBanner msg={err} />}
      <label style={lbl} htmlFor="ev-title">Title</label>
      <input id="ev-title" style={inputStyle()} value={title} onChange={e => setTitle(e.target.value)} />
      <label style={{ ...lbl, marginTop: 12 }} htmlFor="ev-file">File (PDF, image, Word or Excel, up to 10 MB)</label>
      <input id="ev-file" type="file" onChange={e => setFile(e.target.files?.[0] ?? null)} />
      <ModalFoot onCancel={onClose} onSubmit={submit} loading={upload.isPending} label="Attach" />
    </Overlay>
  )
}

function Evidence({ d, onError }: { d: Disposal; onError: (m: string) => void }) {
  const { data = [] } = useDocuments("DISPOSAL", d.id, false)
  if (data.length === 0) return <div style={{ fontSize: 13, color: "var(--hf-warning)" }}>No evidence attached yet.</div>
  return (
    <ul style={{ margin: "4px 0 0", paddingLeft: 18, fontSize: 13 }}>
      {data.map(x => (
        <li key={x.id}>
          {x.title}{" "}
          <button style={{ ...btn(), padding: "1px 8px", fontSize: 12 }}
            onClick={() => downloadDocument(x.id, null, x.current?.fileName ?? x.title).catch((e: unknown) => onError(apiMessage(e, "Could not download the file")))}>
            Download
          </button>
        </li>
      ))}
    </ul>
  )
}

export default function DisposalsTab() {
  const { canWrite, canAdmin } = useEarthPerms()
  const me = useAuthStore(s => s.user?.userId)
  const { data = [], isLoading } = useDisposals()
  const complete = useCompleteDisposal()
  const [dialog, setDialog] = useState<{ type: "request" | "evidence" | "approve" | "reject" | "cancel"; d?: Disposal } | null>(null)
  const [err, setErr] = useState("")

  const run = (d: Disposal) => {
    setErr("")
    complete.mutate(d.id, { onError: (e: unknown) => setErr(apiMessage(e, "Could not complete the disposal")) })
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8, justifyContent: "space-between", flexWrap: "wrap" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <Archive size={18} aria-hidden />
          <h2 style={{ margin: 0, fontSize: 18 }}>Disposals</h2>
        </div>
        {canWrite && <button style={btn(true)} onClick={() => setDialog({ type: "request" })}>Dispose of a machine</button>}
      </div>
      <p style={{ margin: 0, fontSize: 13, color: "var(--hf-text-muted)" }}>
        A machine leaves the fleet only through an approved request with evidence. The person who asks cannot approve it, and a machine on a deployment or hire,
        or with hire charges not yet invoiced, cannot be disposed of.
      </p>

      {err && <ErrBanner msg={err} />}

      {isLoading ? <p>Loading…</p> : data.length === 0 ? (
        <p style={{ color: "var(--hf-text-muted)" }}>No disposal requests yet.</p>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          {data.map(d => {
            const live = d.status === "REQUESTED" || d.status === "APPROVED"
            const mine = d.requestedBy === me
            return (
              <div key={d.id} style={card}>
                <div style={{ display: "flex", justifyContent: "space-between", gap: 12, flexWrap: "wrap" }}>
                  <div>
                    <strong>{d.fleetNumber ? `${d.fleetNumber} · ` : ""}{d.assetName ?? "Machine"}</strong>{" "}
                    <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
                      {DISPOSAL_REASON_LABEL[d.reason] ?? d.reason} · {DISPOSAL_STATUS_LABEL[d.status] ?? d.status}
                    </span>
                    <div style={{ fontSize: 13 }}>{d.details}</div>
                    {d.counterparty && <div style={{ fontSize: 13 }}>To: {d.counterparty}{d.proceeds != null ? ` · ${money(d.proceeds)}` : ""}</div>}
                    <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Requested {fmtDateTime(d.requestedAt)}</div>
                    {d.decisionNote && <div style={{ fontSize: 12 }}>Decision: {d.decisionNote}</div>}
                    {d.cancelReason && <div style={{ fontSize: 12 }}>Cancelled: {d.cancelReason}</div>}
                    {d.completedAt && <div style={{ fontSize: 12 }}>Machine retired {fmtDateTime(d.completedAt)}</div>}
                    <Evidence d={d} onError={setErr} />
                    {live && d.blockers.length > 0 && (
                      <ul role="alert" style={{ margin: "6px 0 0", paddingLeft: 18, fontSize: 13, color: "var(--hf-danger)" }}>
                        {d.blockers.map(b => <li key={b}>{b}</li>)}
                      </ul>
                    )}
                  </div>
                  <div style={{ display: "flex", gap: 6, alignItems: "flex-start", flexWrap: "wrap" }}>
                    {live && canWrite && <button style={btn()} onClick={() => setDialog({ type: "evidence", d })}>Attach evidence</button>}
                    {d.status === "REQUESTED" && canAdmin && !mine && (
                      <>
                        <button style={btn(true)} onClick={() => setDialog({ type: "approve", d })}>Approve</button>
                        <button style={btn()} onClick={() => setDialog({ type: "reject", d })}>Reject</button>
                      </>
                    )}
                    {d.status === "APPROVED" && canAdmin && (
                      <button style={btn(true)} disabled={complete.isPending || d.blockers.length > 0} onClick={() => run(d)}>Complete and retire</button>
                    )}
                    {live && ((canWrite && mine) || canAdmin) && <button style={btn()} onClick={() => setDialog({ type: "cancel", d })}>Cancel request</button>}
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {dialog?.type === "request" && <RequestDialog onClose={() => setDialog(null)} />}
      {dialog?.type === "evidence" && dialog.d && <EvidenceDialog d={dialog.d} onClose={() => setDialog(null)} />}
      {(dialog?.type === "approve" || dialog?.type === "reject" || dialog?.type === "cancel") && dialog.d && (
        <NoteDialog d={dialog.d} action={dialog.type} onClose={() => setDialog(null)} />
      )}
    </div>
  )
}
