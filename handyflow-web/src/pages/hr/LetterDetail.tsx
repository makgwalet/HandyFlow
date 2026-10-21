// One letter, for HR: the wording (gaps highlighted), what can be done to it now, and its history.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { fmtDate, fmtInstant, words } from "./er.logic"
import { ACTION_LABEL, draftProblem, issueProblem, segments, voidProblem } from "./letters.logic"
import { downloadLetter, errorText, input, plain, primary, unwrap, type LetterDetail as Detail } from "./letters.shared"
import { Chip, Field, Notice, Section } from "./ErParts"
import { LetterStatus } from "./LetterParts"

const BASE = "/api/v1/hr/letters"

export default function LetterDetail({ id, back }: { id: string; back: () => void }) {
  const qc = useQueryClient()
  const q = useQuery<Detail>({ queryKey: ["letter", id], queryFn: async () => unwrap<Detail>(await apiClient.get(`${BASE}/${id}`)) })
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState<string | null>(null)
  const [voiding, setVoiding] = useState(false)
  const [reason, setReason] = useState("")

  const after = (d: Detail, text: string) => {
    qc.setQueryData(["letter", id], d)
    qc.invalidateQueries({ queryKey: ["letters"] })
    setError(null); setSaved(text); setVoiding(false)
  }
  const issue = useMutation({
    mutationFn: async () => unwrap<Detail>(await apiClient.post(`${BASE}/${id}/issue`, {})),
    onSuccess: d => after(d, "The letter was issued and the PDF is on the employee's file."), onError: e => { setSaved(null); setError(errorText(e, "The letter could not be issued.")) },
  })
  const voidIt = useMutation({
    mutationFn: async () => unwrap<Detail>(await apiClient.post(`${BASE}/${id}/void`, { reason: reason.trim() })),
    onSuccess: d => after(d, "The letter was voided."), onError: e => { setSaved(null); setError(errorText(e, "The letter could not be voided.")) },
  })

  if (q.isLoading) return <div style={{ padding: 24, color: "var(--hf-text-faint)" }}>Loading the letter…</div>
  if (q.isError || !q.data) return (
    <div><button onClick={back} style={plain}>Back</button><Notice kind="error">{errorText(q.error, "This letter could not be opened. It may not exist, or you may not be allowed to see it.")}</Notice></div>
  )
  const d = q.data, r = d.row
  const canEdit = d.actions.includes("EDIT")
  const download = async () => { const m = await downloadLetter(`${BASE}/${id}/pdf`, d.fileName ?? `${r.number}.pdf`); setError(m); if (!m) setSaved(null) }

  return (
    <div>
      <button onClick={back} style={{ ...plain, marginBottom: 12 }}>← Back</button>
      <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap", marginBottom: 4 }}>
        <h2 style={{ margin: 0, fontSize: 20 }}>{r.number} · {r.subject}</h2>
        <LetterStatus status={r.status} />
        {r.requiresAck && <Chip text={r.acknowledgedAt ? "Receipt confirmed" : "Awaiting confirmation"} color={r.acknowledgedAt ? "var(--hf-success-text-strong)" : "var(--hf-warning-text-strong)"} bg={r.acknowledgedAt ? "var(--hf-success-soft-strong)" : "var(--hf-warning-soft)"} />}
      </div>
      <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: "4px 0 14px" }}>
        To {r.employeeName} · {r.templateName} (revision {d.templateRevision}){r.caseNumber ? ` · case ${r.caseNumber}` : ""}{r.issuedOn ? ` · issued ${fmtDate(r.issuedOn)}` : ""}
      </p>
      {saved && <Notice kind="ok">{saved}</Notice>}
      {error && <Notice kind="error">{error}</Notice>}

      {canEdit ? <DraftEditor key={`${d.events.length}`} detail={d} id={id} onSaved={x => after(x, "Saved.")} onError={setError} />
        : <Section title="The letter"><Body text={d.body} /></Section>}

      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", margin: "4px 0 16px" }} aria-label="Actions">
        {d.actions.includes("ISSUE") && (
          <button onClick={() => { setError(null); issue.mutate() }} disabled={issue.isPending || !!issueProblem(r.subject, d.body)} style={primary}
            title={issueProblem(r.subject, d.body) ?? undefined}>{issue.isPending ? "Issuing…" : ACTION_LABEL.ISSUE}</button>
        )}
        {d.actions.includes("DOWNLOAD") && <button onClick={download} style={plain}>{ACTION_LABEL.DOWNLOAD}</button>}
        {d.actions.includes("VOID") && <button onClick={() => setVoiding(v => !v)} style={plain}>{ACTION_LABEL.VOID}</button>}
      </div>
      {d.actions.includes("ISSUE") && issueProblem(r.subject, d.body) && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: -8 }}>{issueProblem(r.subject, d.body)}</p>}

      {voiding && (
        <form aria-label="Void the letter" onSubmit={e => { e.preventDefault(); if (!voidProblem(reason)) voidIt.mutate() }}
          style={{ border: "1.5px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 16 }}>
          <Field id="lt-void" text="Why is this letter being voided?"><textarea id="lt-void" rows={2} value={reason} onChange={e => setReason(e.target.value)} style={input} /></Field>
          <p style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 0 }}>The letter and its PDF are kept. A voided letter is no longer shown to the employee.</p>
          <button type="submit" disabled={!!voidProblem(reason) || voidIt.isPending} style={{ ...primary, background: "var(--hf-danger-text)" }}>Void the letter</button>
        </form>
      )}
      {r.status === "VOID" && d.voidReason && <Notice kind="error">Voided: {d.voidReason}</Notice>}

      {d.fileSha256 && <p style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>File fingerprint (SHA-256): {d.fileSha256}</p>}
      <Section title="History">
        <ol style={{ margin: 0, paddingLeft: 18, fontSize: 13 }}>
          {d.events.map((e, i) => (
            <li key={`${e.action}-${i}`} style={{ marginBottom: 4 }}>
              <strong>{words(e.action)}</strong>{e.note ? ` – ${e.note}` : ""}
              <span style={{ color: "var(--hf-text-faint)" }}> · {fmtInstant(e.doneAt)}{e.doneBy ? ` · ${e.doneBy}` : ""}</span>
            </li>
          ))}
        </ol>
        <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 8 }}>The history cannot be edited or deleted.</div>
      </Section>
    </div>
  )
}

/** The letter's text with gaps highlighted. */
function Body({ text }: { text: string }) {
  return (
    <div style={{ whiteSpace: "pre-wrap", fontSize: 13, lineHeight: 1.5 }}>
      {segments(text).map((s, i) => s.gap
        ? <mark key={i} style={{ background: "var(--hf-warning-soft)", color: "var(--hf-warning-text-strong)", padding: "0 3px", borderRadius: 3 }}>{s.text}</mark>
        : <span key={i}>{s.text}</span>)}
    </div>
  )
}

function DraftEditor({ detail, id, onSaved, onError }: { detail: Detail; id: string; onSaved: (d: Detail) => void; onError: (m: string | null) => void }) {
  const [subject, setSubject] = useState(detail.row.subject)
  const [body, setBody] = useState(detail.body)
  const problem = draftProblem(subject, body)
  const gaps = [...new Set([...segments(subject).filter(s => s.gap), ...segments(body).filter(s => s.gap)].map(s => s.text))]
  const save = useMutation({
    mutationFn: async () => unwrap<Detail>(await apiClient.put(`${BASE}/${id}/draft`, { subject: subject.trim(), body })),
    onSuccess: onSaved, onError: e => onError(errorText(e, "The wording could not be saved.")),
  })
  const changed = subject !== detail.row.subject || body !== detail.body
  return (
    <Section title="Wording">
      {gaps.length > 0 && (
        <Notice kind="error">Gaps to fill in before this can be issued: {gaps.join(", ")}. Type over each gap, or delete the line it is on.</Notice>
      )}
      <Field id="lt-subject" text="Subject"><input id="lt-subject" value={subject} onChange={e => setSubject(e.target.value)} style={input} /></Field>
      <Field id="lt-body" text="Letter"><textarea id="lt-body" rows={16} value={body} onChange={e => setBody(e.target.value)} style={{ ...input, fontFamily: "inherit", lineHeight: 1.5 }} /></Field>
      {problem && <p style={{ fontSize: 12, color: "var(--hf-danger-text)" }}>{problem}</p>}
      <button onClick={() => { onError(null); save.mutate() }} disabled={!!problem || !changed || save.isPending} style={primary}>{save.isPending ? "Saving…" : "Save wording"}</button>
      {changed && <span style={{ fontSize: 12, color: "var(--hf-text-muted)", marginLeft: 10 }}>Save before issuing.</span>}
    </Section>
  )
}
