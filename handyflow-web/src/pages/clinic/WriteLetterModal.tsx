// Write a general letter (prescription letter, fitness letter, letter to an employer or school, ...) for a patient, with or without
// a visit, optionally addressed to a person or company, from a template or from scratch, and download it as a PDF. A copy is kept in the patient's documents.
import { useEffect, useRef, useState } from "react"
import ModalShell from "./ModalShell"
import LetterTemplatePicker from "./LetterTemplatePicker"
import SaveAsTemplate from "./SaveAsTemplate"
import { apiClient } from "../../api/client"
import { fmtDay } from "./briefing"
import { BORDER, GRAY, lbl, sinp } from "./patientFile.shared"
import { primaryBtn, smallBtn } from "./OverviewCard"
import { MERGE_FIELDS, MERGE_LABEL, insertAt, mergeToken, type Kind } from "./letterView"

interface Visit { id: string; consultedAt: string; chiefComplaint?: string }
interface Practitioner { id: string; fullName?: string; firstName?: string; lastName?: string; active?: boolean }
const pname = (p: Practitioner) => p.fullName ?? `${p.firstName ?? ""} ${p.lastName ?? ""}`.trim()

export default function WriteLetterModal({ patientId, visits, onClose }: { patientId: string; visits: Visit[]; onClose: () => void }) {
  const [visitId, setVisitId] = useState(visits[0]?.id ?? "")
  const [toName, setToName] = useState("")
  const [toCompany, setToCompany] = useState("")
  const [signedBy, setSignedBy] = useState("")
  const [people, setPeople] = useState<Practitioner[]>([])
  const [kind, setKind] = useState<Kind>("GENERAL_LETTER")
  const [title, setTitle] = useState("")
  const [body, setBody] = useState("")
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")
  const area = useRef<HTMLTextAreaElement>(null)
  // Who the letter is signed off by: the visit's doctor or the practice unless one is chosen. A failed lookup just leaves the default.
  useEffect(() => {
    let live = true
    ;(async () => {
      try {
        const r: any = await apiClient.get("/api/v1/clinic/practitioners/list")
        const d = r?.data?.data ?? r?.data
        if (live) setPeople((Array.isArray(d) ? d : (d?.content ?? [])).filter((x: Practitioner) => x.active !== false))
      } catch { if (live) setPeople([]) }
    })()
    return () => { live = false }
  }, [])
  const ready = title.trim() !== "" && body.trim() !== "" && !busy

  const go = async () => {
    setBusy(true); setError("")
    try {
      const payload: Record<string, string> = { title: title.trim(), body }
      if (visitId) payload.consultationId = visitId
      if (signedBy) payload.signedByPractitionerId = signedBy
      if (toName.trim()) payload.recipientName = toName.trim()
      if (toCompany.trim()) payload.recipientCompany = toCompany.trim()
      const res = await apiClient.post(`/api/v1/clinic/patients/${patientId}/letter`, payload, { responseType: "blob" } as any)
      const link = document.createElement("a")
      link.href = URL.createObjectURL(new Blob([res.data], { type: "application/pdf" })); link.download = `letter-${patientId}.pdf`; link.click()
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
        <div><label style={lbl} htmlFor="wl-visit">Visit</label>
          <select id="wl-visit" style={sinp} value={visitId} onChange={e => setVisitId(e.target.value)}>
            <option value="">No visit (diagnosis and doctor stay blank)</option>
            {visits.map(v => <option key={v.id} value={v.id}>{fmtDay(v.consultedAt)}{v.chiefComplaint ? ` · ${v.chiefComplaint}` : ""}</option>)}
          </select></div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
          <div><label style={lbl} htmlFor="wl-to-name">Addressed to (name)</label><input id="wl-to-name" style={sinp} value={toName} onChange={e => setToName(e.target.value)} placeholder="e.g. The HR Manager" /></div>
          <div><label style={lbl} htmlFor="wl-to-company">Company</label><input id="wl-to-company" style={sinp} value={toCompany} onChange={e => setToCompany(e.target.value)} placeholder="e.g. Acme (Pty) Ltd" /></div>
        </div>
        {people.length > 0 && <div><label style={lbl} htmlFor="wl-signer">Signed off by</label>
          <select id="wl-signer" style={sinp} value={signedBy} onChange={e => setSignedBy(e.target.value)}>
            <option value="">{visitId ? "The doctor of the visit" : "The practice (no named doctor)"}</option>
            {people.map(p => <option key={p.id} value={p.id}>{pname(p)}</option>)}
          </select>
          {signedBy && <div style={{ fontSize: 11, color: GRAY, marginTop: 4 }}>The letter will carry this practitioner's name and HPCSA number. A copy is kept in the patient's documents with your name as the person who issued it.</div>}
        </div>}
        <div><label style={lbl} htmlFor="wl-kind">Kind of letter</label>
          <select id="wl-kind" style={sinp} value={kind} onChange={e => setKind(e.target.value as Kind)}>
            <option value="GENERAL_LETTER">General letter</option><option value="PRESCRIPTION_LETTER">Prescription letter</option>
          </select></div>
        <LetterTemplatePicker key={`${kind}-${visitId}-${toName}-${toCompany}-${signedBy}`} kind={kind} consultationId={visitId || undefined} patientId={patientId} recipientName={toName} recipientCompany={toCompany} signedBy={signedBy} onApply={t => { setTitle(t.title ?? ""); setBody(t.body ?? "") }} />
        <div><label style={lbl} htmlFor="wl-title">Title</label><input id="wl-title" style={sinp} value={title} onChange={e => setTitle(e.target.value)} /></div>
        <div><label style={lbl} htmlFor="wl-body">Text</label>
          <textarea id="wl-body" ref={area} rows={9} style={{ ...sinp, resize: "vertical" }} value={body} onChange={e => setBody(e.target.value)} />
          <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginTop: 6 }} aria-label="Insert a merge field">
            {MERGE_FIELDS.map(f => <button key={f} type="button" title={MERGE_LABEL[f]} onClick={() => putField(f)}
              style={{ padding: "2px 8px", borderRadius: 20, border: `1px solid ${BORDER}`, background: "transparent", fontSize: 11, color: GRAY, cursor: "pointer" }}>{mergeToken(f)}</button>)}
          </div>
          <div style={{ fontSize: 11, color: GRAY, marginTop: 4 }}>{"Merge fields (hover for what each one is) are filled in when the letter is written, so typing {{patient.name}} in a letter becomes the patient's name."}</div>
        </div>
        <SaveAsTemplate kind={kind} payload={() => ({ title: title.trim() || undefined, body: body.trim() || undefined })} />
        {error && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>{error}</div>}
      </div>
    </ModalShell>
  )
}
