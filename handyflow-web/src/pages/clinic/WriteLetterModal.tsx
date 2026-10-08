// Write a general letter (prescription letter, fitness letter, appointment letter, ...) for one visit, from a template or
// from scratch, and download it as a PDF. A copy is kept in the patient's documents.
import { useRef, useState } from "react"
import ModalShell from "./ModalShell"
import LetterTemplatePicker from "./LetterTemplatePicker"
import SaveAsTemplate from "./SaveAsTemplate"
import { apiClient } from "../../api/client"
import { fmtDay } from "./briefing"
import { BORDER, GRAY, lbl, sinp } from "./patientFile.shared"
import { primaryBtn, smallBtn } from "./OverviewCard"
import { MERGE_FIELDS, insertAt, mergeToken, type Kind } from "./letterView"

interface Visit { id: string; consultedAt: string; chiefComplaint?: string }

export default function WriteLetterModal({ visits, onClose }: { visits: Visit[]; onClose: () => void }) {
  const [visitId, setVisitId] = useState(visits[0]?.id ?? "")
  const [kind, setKind] = useState<Kind>("GENERAL_LETTER")
  const [title, setTitle] = useState("")
  const [body, setBody] = useState("")
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")
  const area = useRef<HTMLTextAreaElement>(null)
  const ready = !!visitId && title.trim() !== "" && body.trim() !== "" && !busy

  const go = async () => {
    setBusy(true); setError("")
    try {
      const res = await apiClient.post(`/api/v1/clinic/consultations/${visitId}/letter`, { title: title.trim(), body }, { responseType: "blob" } as any)
      const link = document.createElement("a")
      link.href = URL.createObjectURL(new Blob([res.data], { type: "application/pdf" })); link.download = `letter-${visitId}.pdf`; link.click()
      setTimeout(() => URL.revokeObjectURL(link.href), 60_000)
      onClose()
    } catch (e: any) { setError("Could not write the letter. " + (typeof e?.response?.data?.message === "string" ? e.response.data.message : "Check the title and text.")) }
    finally { setBusy(false) }
  }
  const putField = (f: string) => {
    const r = insertAt(body, mergeToken(f), area.current?.selectionStart)
    setBody(r.text)
    setTimeout(() => { area.current?.focus(); area.current?.setSelectionRange(r.cursor, r.cursor) }, 0)
  }

  return (
    <ModalShell title="Write a letter" onClose={onClose} width={620}
      footer={<><button type="button" style={smallBtn} onClick={onClose}>Cancel</button>
        <button type="button" disabled={!ready} onClick={go} style={{ ...primaryBtn, padding: "9px 18px", fontSize: 14, opacity: ready ? 1 : 0.6 }}>{busy ? "Writing…" : "Download letter"}</button></>}>
      <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
        {visits.length > 1 && <div><label style={lbl} htmlFor="wl-visit">Visit</label>
          <select id="wl-visit" style={sinp} value={visitId} onChange={e => setVisitId(e.target.value)}>
            {visits.map(v => <option key={v.id} value={v.id}>{fmtDay(v.consultedAt)}{v.chiefComplaint ? ` · ${v.chiefComplaint}` : ""}</option>)}
          </select></div>}
        <div><label style={lbl} htmlFor="wl-kind">Kind of letter</label>
          <select id="wl-kind" style={sinp} value={kind} onChange={e => setKind(e.target.value as Kind)}>
            <option value="GENERAL_LETTER">General letter</option><option value="PRESCRIPTION_LETTER">Prescription letter</option>
          </select></div>
        {visitId && <LetterTemplatePicker key={kind} kind={kind} consultationId={visitId} onApply={t => { setTitle(t.title ?? ""); setBody(t.body ?? "") }} />}
        <div><label style={lbl} htmlFor="wl-title">Title</label><input id="wl-title" style={sinp} value={title} onChange={e => setTitle(e.target.value)} /></div>
        <div><label style={lbl} htmlFor="wl-body">Text</label>
          <textarea id="wl-body" ref={area} rows={9} style={{ ...sinp, resize: "vertical" }} value={body} onChange={e => setBody(e.target.value)} />
          <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginTop: 6 }} aria-label="Insert a merge field">
            {MERGE_FIELDS.map(f => <button key={f} type="button" onClick={() => putField(f)}
              style={{ padding: "2px 8px", borderRadius: 20, border: `1px solid ${BORDER}`, background: "transparent", fontSize: 11, color: GRAY, cursor: "pointer" }}>{mergeToken(f)}</button>)}
          </div>
          <div style={{ fontSize: 11, color: GRAY, marginTop: 4 }}>Merge fields are filled in when a template is applied. Fields you type yourself must be filled in before the letter can be written.</div>
        </div>
        <SaveAsTemplate kind={kind} payload={() => ({ title: title.trim() || undefined, body: body.trim() || undefined })} />
        {error && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</div>}
      </div>
    </ModalShell>
  )
}
