// The small forms on a review: write the self-review, rate, adjust the final rating, change the reviewer, leave out, acknowledge, release.
import { useState } from "react"
import { useEmployeeOptions } from "./er.hooks"
import { useSend } from "./perf.hooks"
import { calibrationProblem, managerProblem, reasonProblem, selfProblem } from "./perf.logic"
import type { ReviewDetail } from "./perf.shared"
import { input, plain, primary } from "./perf.shared"
import { Field, Notice, PersonSelect, RatingSelect } from "./PerfParts"

/** The frame every form shares: a title, the fields, the checks, the server's answer. */
export function FormFrame({ title, label, problem, pending, error, submit, onCancel, children }: {
  title: string; label: string; problem: string | null; pending: boolean; error: string | null; submit: () => void; onCancel?: () => void; children: React.ReactNode
}) {
  return (
    <form aria-label={title} onSubmit={e => { e.preventDefault(); if (!problem) submit() }}
      style={{ border: "1.5px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 16, background: "var(--hf-surface)" }}>
      <h4 style={{ margin: "0 0 12px", fontSize: 14 }}>{title}</h4>
      {children}
      {problem && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 10px" }}>{problem}</p>}
      {error && <Notice kind="error">{error}</Notice>}
      <button type="submit" disabled={!!problem || pending} style={primary}>{pending ? "Saving…" : label}</button>
      {onCancel && <>{" "}<button type="button" onClick={onCancel} style={plain}>Cancel</button></>}
    </form>
  )
}

type Done = (d: ReviewDetail) => void

export function SelfForm({ id, done }: { id: string; done: Done }) {
  const [rating, setRating] = useState<number | null>(null)
  const [comment, setComment] = useState("")
  const s = useSend<{ rating: number | null; comment: string }, ReviewDetail>("post", `/reviews/${id}/self`, "Your self-review could not be saved.", done)
  return (
    <FormFrame title="Write your self-review" label="Submit self-review" problem={selfProblem(rating, comment)} pending={s.pending} error={s.error} submit={() => s.send({ rating, comment })}>
      <Field id="pf-self-rating" text="How would you rate your own performance?"><RatingSelect id="pf-self-rating" value={rating} onChange={setRating} /></Field>
      <Field id="pf-self-comment" text="Your comments" hint="What went well, what was hard, what you want to do next. Your manager reads this before they rate.">
        <textarea id="pf-self-comment" rows={6} value={comment} onChange={e => setComment(e.target.value)} style={input} />
      </Field>
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 0 }}>You cannot change it after you submit.</p>
    </FormFrame>
  )
}

export function ManagerForm({ id, done }: { id: string; done: Done }) {
  const [rating, setRating] = useState<number | null>(null)
  const [comment, setComment] = useState("")
  const s = useSend<{ rating: number | null; comment: string }, ReviewDetail>("post", `/reviews/${id}/manager`, "The review could not be saved.", done)
  return (
    <FormFrame title="Rate this employee" label="Submit rating" problem={managerProblem(rating, comment)} pending={s.pending} error={s.error} submit={() => s.send({ rating, comment })}>
      <Field id="pf-mgr-rating" text="Rating"><RatingSelect id="pf-mgr-rating" value={rating} onChange={setRating} /></Field>
      <Field id="pf-mgr-comment" text="Your comments" hint="Say why, with examples. A rating of 1, 2 or 5 needs the reasons in detail. The employee will read this once HR releases the result.">
        <textarea id="pf-mgr-comment" rows={6} value={comment} onChange={e => setComment(e.target.value)} style={input} />
      </Field>
    </FormFrame>
  )
}

export function CalibrateForm({ id, current, done, cancel }: { id: string; current: number | null; done: Done; cancel: () => void }) {
  const [rating, setRating] = useState<number | null>(null)
  const [reason, setReason] = useState("")
  const s = useSend<{ rating: number | null; reason: string }, ReviewDetail>("post", `/reviews/${id}/calibrate`, "The rating could not be changed.", d => { done(d); cancel() })
  return (
    <FormFrame title="Change the final rating" label="Change rating" problem={calibrationProblem(rating, reason, current)} pending={s.pending} error={s.error}
      submit={() => s.send({ rating, reason })} onCancel={cancel}>
      <Field id="pf-cal-rating" text="Final rating"><RatingSelect id="pf-cal-rating" value={rating} onChange={setRating} /></Field>
      <Field id="pf-cal-reason" text="Why is it being changed?" hint="The manager's rating is kept. The employee sees only the final rating.">
        <textarea id="pf-cal-reason" rows={3} value={reason} onChange={e => setReason(e.target.value)} style={input} />
      </Field>
    </FormFrame>
  )
}

export function ReviewerForm({ id, done, cancel }: { id: string; done: Done; cancel: () => void }) {
  const people = useEmployeeOptions()
  const [reviewerId, setReviewerId] = useState("")
  const [reason, setReason] = useState("")
  const s = useSend<{ reviewerId: string; reason: string }, ReviewDetail>("post", `/reviews/${id}/reviewer`, "The reviewer could not be changed.", d => { done(d); cancel() })
  const problem = !reviewerId ? "Choose the reviewer." : reasonProblem("The reason for changing the reviewer", reason)
  return (
    <FormFrame title="Change the reviewer" label="Change reviewer" problem={problem} pending={s.pending} error={s.error} submit={() => s.send({ reviewerId, reason })} onCancel={cancel}>
      <Field id="pf-rev-who" text="New reviewer"><PersonSelect id="pf-rev-who" value={reviewerId} onChange={setReviewerId} options={people.data ?? []} /></Field>
      <Field id="pf-rev-reason" text="Why?"><input id="pf-rev-reason" value={reason} onChange={e => setReason(e.target.value)} style={input} /></Field>
    </FormFrame>
  )
}

export function ExcludeForm({ id, done, cancel }: { id: string; done: Done; cancel: () => void }) {
  const [reason, setReason] = useState("")
  const s = useSend<{ reason: string }, ReviewDetail>("post", `/reviews/${id}/exclude`, "The review could not be left out.", d => { done(d); cancel() })
  return (
    <FormFrame title="Leave this review out of the cycle" label="Leave out" problem={reasonProblem("The reason for leaving this review out", reason)} pending={s.pending} error={s.error}
      submit={() => s.send({ reason })} onCancel={cancel}>
      <Field id="pf-ex-reason" text="Why?" hint="For example long leave, or the person left."><input id="pf-ex-reason" value={reason} onChange={e => setReason(e.target.value)} style={input} /></Field>
    </FormFrame>
  )
}

export function AcknowledgeForm({ id, done }: { id: string; done: Done }) {
  const [comment, setComment] = useState("")
  const s = useSend<{ comment: string }, ReviewDetail>("post", `/reviews/${id}/acknowledge`, "That could not be saved.", done)
  return (
    <FormFrame title="Acknowledge your result" label="I have read my result" problem={comment.length > 4000 ? "Your reply is too long (4000 characters at most)." : null}
      pending={s.pending} error={s.error} submit={() => s.send({ comment })}>
      <Field id="pf-ack" text="Your reply (optional)" hint="Acknowledging means you have read it. It does not mean you agree. Say here if you disagree.">
        <textarea id="pf-ack" rows={3} value={comment} onChange={e => setComment(e.target.value)} style={input} />
      </Field>
    </FormFrame>
  )
}

export function ShareButton({ id, done }: { id: string; done: Done }) {
  const s = useSend<Record<string, never>, ReviewDetail>("post", `/reviews/${id}/share`, "The result could not be released.", done)
  return (
    <>
      <button onClick={() => s.send({})} disabled={s.pending} style={primary}>{s.pending ? "Releasing…" : "Release result to employee"}</button>
      {s.error && <Notice kind="error">{s.error}</Notice>}
    </>
  )
}
