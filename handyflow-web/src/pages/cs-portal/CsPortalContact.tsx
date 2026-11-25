// src/pages/cs-portal/CsPortalContact.tsx
// The client's side of contact with the practice: write to them, and hand over documents they have asked for.
// The practice is always shown as "The practice"; no staff names reach this screen.
import { useEffect, useState } from "react"
import { csPortalApi, type CsPortalQuery, type CsPortalQueryDetail, type CsPortalRequest } from "../../api/csPortal.api"
import { bodyProblem, fileProblem, queryStatusLabel, requestStatusLabel } from "../complianceservices/contactRules"
import { color, space } from "../accountant-portal/portal-theme"
import { Chip, Title, day, empty, errText, inputStyle, panel, btn, td, sub } from "../fm-portal/portalKit"

export function CsPortalQueries({ clientId }: { clientId: string }) {
  const [list, setList] = useState<CsPortalQuery[] | null>(null)
  const [detail, setDetail] = useState<CsPortalQueryDetail | null>(null)
  const [writing, setWriting] = useState(false)
  const [subject, setSubject] = useState("")
  const [body, setBody] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const load = () => csPortalApi.queries(clientId).then(setList).catch(e => { setList([]); setError(errText(e, "Could not load your messages")) })
  useEffect(() => { void load() }, [clientId]) // eslint-disable-line react-hooks/exhaustive-deps

  const run = async (work: () => Promise<CsPortalQueryDetail>) => {
    setBusy(true); setError("")
    try { setDetail(await work()); setBody(""); setSubject(""); setWriting(false); void load() }
    catch (e) { setError(errText(e, "That did not go through")) } finally { setBusy(false) }
  }
  const raise = () => {
    if (subject.trim() === "") { setError("Give your question a subject"); return }
    const p = bodyProblem(body); if (p) { setError(p); return }
    void run(() => csPortalApi.raise({ clientId, subject: subject.trim(), body: body.trim() }))
  }
  const reply = () => {
    const p = bodyProblem(body); if (p || !detail) { setError(p); return }
    void run(() => csPortalApi.reply(detail.query.id, body.trim()))
  }

  if (detail) {
    const closed = detail.query.status === "CLOSED"
    return (
      <>
        <Title text={detail.query.subject} note={queryStatusLabel(detail.query.status)} />
        <div style={{ ...panel, padding: space(4), display: "grid", gap: space(3) }}>
          {detail.messages.map((m, i) => (
            <div key={i} style={{ padding: "10px 14px", borderRadius: 10, background: m.fromClient ? color.canvas : "var(--hf-success-soft)", whiteSpace: "pre-wrap", fontSize: 13.5 }}>
              <div style={sub}>{m.author} · {day(m.createdAt)}</div>{m.body}
            </div>
          ))}
          {!closed && (
            <>
              <textarea aria-label="Your reply" rows={3} style={inputStyle} value={body} onChange={e => setBody(e.target.value)} />
              <div style={{ display: "flex", gap: space(2) }}><button style={btn(true)} disabled={busy} onClick={reply}>Send reply</button></div>
            </>
          )}
          {closed && <div style={sub}>This question is closed. You can ask a new one if you need to.</div>}
          {error && <div role="alert" style={{ fontSize: 13, color: color.redText }}>{error}</div>}
          <div><button style={btn()} onClick={() => { setDetail(null); setError("") }}>Back to your messages</button></div>
        </div>
      </>
    )
  }
  return (
    <>
      <Title text="Messages" note="Ask your practice a question. They will answer here." />
      {!writing && <div style={{ marginBottom: space(4) }}><button style={btn(true)} onClick={() => setWriting(true)}>Ask a question</button></div>}
      {writing && (
        <div style={{ ...panel, padding: space(4), display: "grid", gap: space(3), marginBottom: space(4) }}>
          <label htmlFor="q-subject" style={sub}>Subject</label>
          <input id="q-subject" style={inputStyle} value={subject} onChange={e => setSubject(e.target.value)} />
          <label htmlFor="q-body" style={sub}>Your question</label>
          <textarea id="q-body" rows={4} style={inputStyle} value={body} onChange={e => setBody(e.target.value)} />
          {error && <div role="alert" style={{ fontSize: 13, color: color.redText }}>{error}</div>}
          <div style={{ display: "flex", gap: space(2) }}><button style={btn(true)} disabled={busy} onClick={raise}>Send</button><button style={btn()} onClick={() => { setWriting(false); setError("") }}>Cancel</button></div>
        </div>
      )}
      {!writing && error && <div role="alert" style={{ marginBottom: space(3), fontSize: 13, color: color.redText }}>{error}</div>}
      <div style={panel}>
        {list === null ? <div style={empty}>Loading…</div> : list.length === 0 ? <div style={empty}>You have not asked anything yet.</div> : list.map(q => (
          <div key={q.id} style={{ ...td, display: "flex", gap: space(3), alignItems: "center" }}>
            <button onClick={() => csPortalApi.query(q.id).then(setDetail).catch(e => setError(errText(e, "Could not open that message")))} style={{ all: "unset", cursor: "pointer", fontWeight: 700 }}>{q.subject}</button>
            <Chip tone={q.status === "ANSWERED" ? "success" : q.status === "OPEN" ? "warning" : "neutral"}>{queryStatusLabel(q.status)}</Chip>
            <span style={{ ...sub, marginLeft: "auto" }}>{day(q.lastMessageAt)}</span>
          </div>
        ))}
      </div>
    </>
  )
}

export function CsPortalRequests({ clientId }: { clientId: string }) {
  const [list, setList] = useState<CsPortalRequest[] | null>(null)
  const [error, setError] = useState("")
  const [busyId, setBusyId] = useState("")
  const load = () => csPortalApi.requests(clientId).then(setList).catch(e => { setList([]); setError(errText(e, "Could not load what your practice has asked for")) })
  useEffect(() => { void load() }, [clientId]) // eslint-disable-line react-hooks/exhaustive-deps

  const pick = async (r: CsPortalRequest, file: File | undefined) => {
    if (!file) return
    const problem = fileProblem(file.name, file.size)
    if (problem) { setError(problem); return }
    setBusyId(r.id); setError("")
    try { await csPortalApi.upload(r.id, file); await load() }
    catch (e) { setError(errText(e, "The file did not go through")) } finally { setBusyId("") }
  }
  return (
    <>
      <Title text="Documents requested" note="Documents your practice has asked you for. Your file goes to them to look at; it is not accepted until they have checked it." />
      {error && <div role="alert" style={{ marginBottom: space(3), fontSize: 13, color: color.redText }}>{error}</div>}
      <div style={panel}>
        {list === null ? <div style={empty}>Loading…</div> : list.length === 0 ? <div style={empty}>Nothing has been asked of you.</div> : list.map(r => (
          <div key={r.id} style={{ ...td, display: "grid", gap: 6 }}>
            <div style={{ display: "flex", gap: space(3), alignItems: "center", flexWrap: "wrap" }}>
              <strong>{r.title}</strong>
              <Chip tone={r.status === "RECEIVED" ? "success" : r.late ? "danger" : "warning"}>{r.status === "OPEN" && r.late ? "Late" : requestStatusLabel(r.status)}</Chip>
              {r.dueDate && <span style={sub}>Needed by {day(r.dueDate)}</span>}
            </div>
            {r.description && <div style={sub}>{r.description}</div>}
            <label style={{ fontSize: 13 }}>
              <span style={sub}>{r.status === "RECEIVED" ? "Send another file" : "Choose a file"} (PDF, JPG, PNG, XLSX, DOCX or CSV, up to 10 MB) </span>
              <input type="file" aria-label={`File for ${r.title}`} disabled={busyId === r.id} onChange={e => void pick(r, e.target.files?.[0])} />
            </label>
          </div>
        ))}
      </div>
    </>
  )
}
