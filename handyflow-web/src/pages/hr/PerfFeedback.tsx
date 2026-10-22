// 360 feedback: the summary on a review, asking colleagues, and answering requests.
import { useState } from "react"
import { fmtDate } from "./er.logic"
import { useEmployeeOptions } from "./er.hooks"
import { useSend, usePerfList } from "./perf.hooks"
import { RELATIONSHIPS, feedbackProblem } from "./perf.logic"
import { card, errorText, input, plain, primary, td, th, type FeedbackTask, type FeedbackView, type ReviewDetail, type SummaryView } from "./perf.shared"
import { Chip, Field, Notice, RatingSelect, Section } from "./PerfParts"
import { FormFrame } from "./PerfForms"

export function FeedbackSummary({ summary, requests, names }: { summary: SummaryView; requests: FeedbackView[]; names: boolean }) {
  return (
    <>
      {summary.shown ? (
        <div style={{ marginBottom: 12 }}>
          <div style={{ fontSize: 13, marginBottom: 8 }}><strong>Average {summary.average?.toFixed(1)}</strong> out of 5, from {summary.replies} replies.</div>
          <Quotes title="What people value" items={summary.strengths} />
          <Quotes title="What could be better" items={summary.improvements} />
          <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>Comments are listed in alphabetical order with no names, so nobody can be picked out.</div>
        </div>
      ) : (
        <p style={{ fontSize: 13, color: "var(--hf-text-muted)", marginTop: 0 }}>
          {summary.replies} of {summary.asked} replied. The summary is shown once at least {summary.minReplies} people have replied, so nobody can be picked out.
        </p>
      )}
      {requests.length > 0 && (
        <table style={{ width: "100%", borderCollapse: "collapse" }} aria-label="Feedback requests">
          <thead><tr><th style={th}>Asked</th><th style={th}>As</th><th style={th}>Status</th>{names && <th style={th}>Rating and comments (HR only)</th>}</tr></thead>
          <tbody>
            {requests.map(r => (
              <tr key={r.id}>
                <td style={td}>{r.providerName ?? "–"}</td>
                <td style={td}>{RELATIONSHIPS[r.relationship] ?? r.relationship}</td>
                <td style={td}>{r.status === "SUBMITTED" ? `Replied ${fmtDate(r.respondedAt)}` : r.status === "DECLINED" ? "Declined" : "Waiting"}</td>
                {names && <td style={td}>{r.rating ? <><strong>{r.rating}/5</strong>{r.strengths ? <div>{r.strengths}</div> : null}{r.improvements ? <div style={{ color: "var(--hf-text-muted)" }}>{r.improvements}</div> : null}</> : "–"}</td>}
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  )
}

function Quotes({ title, items }: { title: string; items: string[] }) {
  if (items.length === 0) return null
  return (
    <div style={{ marginBottom: 8 }}>
      <div style={{ fontSize: 12, fontWeight: 700, color: "var(--hf-text-secondary)" }}>{title}</div>
      <ul style={{ margin: "4px 0 0", paddingLeft: 18, fontSize: 13 }}>{items.map((t, i) => <li key={i}>{t}</li>)}</ul>
    </div>
  )
}

/** Choose colleagues to ask. The subject and the reviewer cannot be asked, so they are left out of the list. */
export function RequestFeedbackForm({ reviewId, leaveOut, done, cancel }: { reviewId: string; leaveOut: string[]; done: () => void; cancel: () => void }) {
  const people = useEmployeeOptions()
  const [chosen, setChosen] = useState<string[]>([])
  const [filter, setFilter] = useState("")
  const [relationship, setRelationship] = useState("PEER")
  const s = useSend<{ providerIds: string[]; relationship: string }, FeedbackView[]>("post", `/reviews/${reviewId}/feedback`, "The requests could not be sent.", () => { done(); cancel() })
  const options = (people.data ?? []).filter(p => !leaveOut.includes(p.id) && p.name.toLowerCase().includes(filter.trim().toLowerCase())).slice(0, 60)
  const toggle = (id: string) => setChosen(c => c.includes(id) ? c.filter(x => x !== id) : [...c, id])
  return (
    <FormFrame title="Ask colleagues for feedback" label={chosen.length ? `Ask ${chosen.length} ${chosen.length === 1 ? "person" : "people"}` : "Ask"} problem={chosen.length === 0 ? "Choose at least one person." : null}
      pending={s.pending} error={s.error} submit={() => s.send({ providerIds: chosen, relationship })} onCancel={cancel}>
      <Field id="pf-fb-rel" text="How do they work with this employee?">
        <select id="pf-fb-rel" value={relationship} onChange={e => setRelationship(e.target.value)} style={input}>
          {Object.entries(RELATIONSHIPS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
      </Field>
      <Field id="pf-fb-find" text="Find people"><input id="pf-fb-find" value={filter} onChange={e => setFilter(e.target.value)} style={input} placeholder="Type a name" /></Field>
      <div style={{ maxHeight: 180, overflow: "auto", border: "1px solid var(--hf-border-subtle)", borderRadius: 8, padding: 8, marginBottom: 12 }}>
        {options.length === 0 && <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>Nobody matches.</div>}
        {options.map(p => (
          <label key={p.id} style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 13, padding: "2px 0" }}>
            <input type="checkbox" checked={chosen.includes(p.id)} onChange={() => toggle(p.id)} /> {p.name}
          </label>
        ))}
      </div>
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 0 }}>
        The employee and their manager see a summary with no names, once three people have replied. HR can see who wrote what.
      </p>
    </FormFrame>
  )
}

/** Feedback the signed-in person has been asked to give. */
export default function FeedbackTasks() {
  const q = usePerfList<FeedbackTask>(["feedback", "mine"], "/feedback/mine")
  const [openId, setOpenId] = useState<string | null>(null)
  const tasks = q.data ?? []
  return (
    <div>
      <p style={{ margin: "0 0 14px", fontSize: 13, color: "var(--hf-text-muted)" }}>Colleagues who have asked you for feedback.</p>
      {q.isError && <Notice kind="error">{errorText(q.error, "Your requests could not be loaded.")}</Notice>}
      {q.isLoading && <div style={{ color: "var(--hf-text-faint)" }}>Loading…</div>}
      {!q.isLoading && tasks.length === 0 && <div style={{ ...card, padding: 16, fontSize: 13 }}>Nobody is waiting for your feedback.</div>}
      {tasks.map(t => (
        <Section key={t.id} title={`About ${t.aboutName}`}>
          <div style={{ fontSize: 13, marginBottom: 8 }}>
            {t.jobTitle ? `${t.jobTitle} · ` : ""}{t.cycleName} · please reply by {fmtDate(t.dueDate)} <Chip text={RELATIONSHIPS[t.relationship] ?? t.relationship} color="var(--hf-text-muted)" bg="var(--hf-surface-sunken)" />
          </div>
          {openId === t.id ? <GiveFeedback task={t} done={() => setOpenId(null)} /> : <button onClick={() => setOpenId(t.id)} style={primary}>Give feedback</button>}
        </Section>
      ))}
    </div>
  )
}

function GiveFeedback({ task, done }: { task: FeedbackTask; done: () => void }) {
  const [rating, setRating] = useState<number | null>(null)
  const [strengths, setStrengths] = useState("")
  const [improvements, setImprovements] = useState("")
  const [declining, setDeclining] = useState(false)
  const [reason, setReason] = useState("")
  const send = useSend<{ rating: number | null; strengths: string; improvements: string }, FeedbackTask>("post", `/feedback/${task.id}/submit`, "Your feedback could not be saved.", done)
  const decline = useSend<{ reason: string }, FeedbackTask>("post", `/feedback/${task.id}/decline`, "That could not be saved.", done)
  if (declining) {
    return (
      <FormFrame title="Decline to give feedback" label="Decline" problem={reason.length > 500 ? "The reason is too long (500 characters at most)." : null} pending={decline.pending}
        error={decline.error} submit={() => decline.send({ reason })} onCancel={() => setDeclining(false)}>
        <Field id="pf-fbd-reason" text="Reason (optional)"><input id="pf-fbd-reason" value={reason} onChange={e => setReason(e.target.value)} style={input} /></Field>
      </FormFrame>
    )
  }
  return (
    <FormFrame title={`Feedback about ${task.aboutName}`} label="Send feedback" problem={feedbackProblem(rating, strengths, improvements)} pending={send.pending} error={send.error}
      submit={() => send.send({ rating, strengths, improvements })} onCancel={done}>
      <Field id="pf-fb-rating" text="Overall"><RatingSelect id="pf-fb-rating" value={rating} onChange={setRating} /></Field>
      <Field id="pf-fb-strengths" text="What does this person do well?"><textarea id="pf-fb-strengths" rows={3} value={strengths} onChange={e => setStrengths(e.target.value)} style={input} /></Field>
      <Field id="pf-fb-improve" text="What could they do better?"><textarea id="pf-fb-improve" rows={3} value={improvements} onChange={e => setImprovements(e.target.value)} style={input} /></Field>
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 0 }}>
        {task.aboutName} and their manager will not see your name. HR can. Be specific and fair.{" "}
        <button type="button" onClick={() => setDeclining(true)} style={{ ...plain, padding: "2px 8px" }}>I would rather not</button>
      </p>
    </FormFrame>
  )
}

/** Exposed for the review screen. */
export function FeedbackSection({ detail, onAsk }: { detail: ReviewDetail; onAsk: () => void }) {
  if (!detail.include360) return null
  const names = detail.requests.some(r => r.rating !== null)
  return (
    <Section title="360 feedback">
      {detail.feedback && <FeedbackSummary summary={detail.feedback} requests={detail.requests} names={names} />}
      {detail.actions.includes("REQUEST_FEEDBACK") && <button onClick={onAsk} style={plain}>Ask colleagues</button>}
    </Section>
  )
}
