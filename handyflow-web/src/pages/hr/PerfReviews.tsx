// Reviews: the lists (mine, my team, a cycle's) and one review with everything the signed-in person may read and do.
import { useState } from "react"
import { fmtDate, fmtInstant, words } from "./er.logic"
import { usePerfList, usePerfOne } from "./perf.hooks"
import { ratingText } from "./perf.logic"
import { card, errorText, plain, td, th, type ReviewDetail as Detail, type ReviewRow } from "./perf.shared"
import { Facts, Notice, Prose, ReviewStatus, Section } from "./PerfParts"
import { AcknowledgeForm, CalibrateForm, ExcludeForm, ManagerForm, ReviewerForm, SelfForm, ShareButton } from "./PerfForms"
import { FeedbackSection, RequestFeedbackForm } from "./PerfFeedback"
import { GoalLine } from "./PerfGoals"

export function ReviewList({ scope, cycleId, open, empty }: { scope: "mine" | "team" | "all"; cycleId?: string; open: (id: string) => void; empty: string }) {
  const q = usePerfList<ReviewRow>(["reviews", scope, cycleId ?? ""], `/reviews?scope=${scope}${cycleId ? `&cycleId=${cycleId}` : ""}`)
  const rows = q.data ?? []
  return (
    <div style={card}>
      {q.isError && <Notice kind="error">{errorText(q.error, "The reviews could not be loaded.")}</Notice>}
      <table style={{ width: "100%", borderCollapse: "collapse" }}>
        <thead><tr><th style={th}>Employee</th><th style={th}>Cycle</th><th style={th}>Reviewer</th><th style={th}>Status</th><th style={th}>Rating</th><th style={th}>Due</th></tr></thead>
        <tbody>
          {q.isLoading && <tr><td style={td} colSpan={6}>Loading…</td></tr>}
          {!q.isLoading && rows.length === 0 && <tr><td style={td} colSpan={6}>{empty}</td></tr>}
          {rows.map(r => (
            <tr key={r.id}>
              <td style={td}>
                <button onClick={() => open(r.id)} style={{ background: "none", border: "none", padding: 0, cursor: "pointer", color: "var(--hf-primary)", fontWeight: 700, textAlign: "left" }}>{r.employeeName}</button>
                {r.jobTitle && <div style={{ color: "var(--hf-text-muted)" }}>{r.jobTitle}</div>}
              </td>
              <td style={td}>{r.cycleName}</td>
              <td style={td}>{r.reviewerName ?? <span style={{ color: "var(--hf-danger-text)" }}>None set</span>}</td>
              <td style={td}><ReviewStatus status={r.status} /></td>
              <td style={td}>{ratingText(r.rating)}</td>
              <td style={td}>{r.status === "AWAITING_SELF" ? `Self ${fmtDate(r.selfDue)}` : r.status === "AWAITING_MANAGER" ? `Manager ${fmtDate(r.managerDue)}` : "–"}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export default function ReviewScreen({ id, back }: { id: string; back: () => void }) {
  const q = usePerfOne<Detail>(["review", id], `/reviews/${id}`)
  const [saved, setSaved] = useState<string | null>(null)
  const [form, setForm] = useState<"calibrate" | "reviewer" | "exclude" | "ask" | null>(null)
  if (q.isLoading) return <div style={{ padding: 24, color: "var(--hf-text-faint)" }}>Loading the review…</div>
  if (q.isError || !q.data) {
    return <div><button onClick={back} style={plain}>Back</button><Notice kind="error">{errorText(q.error, "This review could not be opened. It may not exist, or you may not be allowed to see it.")}</Notice></div>
  }
  const d = q.data, r = d.row
  const can = (a: string) => d.actions.includes(a)
  const done = (msg: string) => () => { setSaved(msg); setForm(null) }
  const released = r.status === "SHARED" || r.status === "ACKNOWLEDGED"
  const waitingToBeShown = !released && d.managerRating === null && !can("MANAGER") && r.status !== "EXCLUDED" && d.events.length === 0

  return (
    <div>
      <button onClick={back} style={{ ...plain, marginBottom: 12 }}>← Back</button>
      <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap", marginBottom: 4 }}>
        <h2 style={{ margin: 0, fontSize: 20 }}>{r.employeeName}</h2>
        <ReviewStatus status={r.status} />
      </div>
      <p style={{ fontSize: 13, color: "var(--hf-text-muted)", margin: "4px 0 14px" }}>
        {r.cycleName} · {fmtDate(d.periodStart)} to {fmtDate(d.periodEnd)}{r.jobTitle ? ` · ${r.jobTitle}` : ""} · reviewer {r.reviewerName ?? "not set"}
      </p>
      {saved && <Notice kind="ok">{saved}</Notice>}
      {r.status === "EXCLUDED" && <Notice kind="error">Left out of this cycle: {d.excludedReason}</Notice>}

      {d.goals.length > 0 && (
        <Section title="Goals for this period">
          {d.goals.map(g => <GoalLine key={g.id} goal={g} />)}
        </Section>
      )}

      <Section title="Self-review">
        {d.selfRating !== null ? (
          <><Facts rows={[["Rating", ratingText(d.selfRating)], ["Written", d.selfAt ? fmtInstant(d.selfAt) : null]]} /><div style={{ marginTop: 8 }}><Prose text={d.selfComment} /></div></>
        ) : can("SELF") ? <SelfForm id={id} done={done("Your self-review was submitted.")} /> : <p style={{ margin: 0, fontSize: 13, color: "var(--hf-text-muted)" }}>Not written yet.</p>}
      </Section>

      <Section title="Manager review">
        {d.managerRating !== null ? (
          <><Facts rows={[["Rating", ratingText(d.managerRating)], ["Written", d.managerAt ? fmtInstant(d.managerAt) : null]]} /><div style={{ marginTop: 8 }}><Prose text={d.managerComment} /></div></>
        ) : can("MANAGER") ? <ManagerForm id={id} done={done("The rating was submitted.")} />
          : <p style={{ margin: 0, fontSize: 13, color: "var(--hf-text-muted)" }}>{waitingToBeShown ? "Your manager's review will appear here once HR releases the result." : "Not written yet."}</p>}
      </Section>

      {d.finalRating !== null && (
        <Section title="Final rating">
          <div style={{ fontSize: 15, fontWeight: 700 }}>{ratingText(d.finalRating)}</div>
          {d.calibrated && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", marginBottom: 0 }}>Changed from the manager's rating of {d.managerRating}: {d.calibrationReason}</p>}
        </Section>
      )}

      <FeedbackSection detail={d} onAsk={() => setForm("ask")} />
      {form === "ask" && <RequestFeedbackForm reviewId={id} leaveOut={[r.employeeId, r.reviewerId ?? ""]} done={() => setSaved("The requests were sent.")} cancel={() => setForm(null)} />}

      {r.status === "ACKNOWLEDGED" && (
        <Section title="Employee's reply">
          <p style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 0 }}>Acknowledged {fmtInstant(r.ackAt)}. Acknowledging means they read it, not that they agree.</p>
          <Prose text={d.employeeComment} />
        </Section>
      )}
      {can("ACKNOWLEDGE") && <AcknowledgeForm id={id} done={done("Thank you. Your acknowledgement was recorded.")} />}

      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", margin: "4px 0 16px" }} aria-label="Actions">
        {can("SHARE") && <ShareButton id={id} done={done("The result was released to the employee.")} />}
        {can("CALIBRATE") && <button onClick={() => setForm("calibrate")} style={plain}>Change final rating</button>}
        {can("REVIEWER") && <button onClick={() => setForm("reviewer")} style={plain}>Change reviewer</button>}
        {can("EXCLUDE") && <button onClick={() => setForm("exclude")} style={plain}>Leave out of cycle</button>}
      </div>
      {form === "calibrate" && <CalibrateForm id={id} current={d.finalRating} done={done("The final rating was changed.")} cancel={() => setForm(null)} />}
      {form === "reviewer" && <ReviewerForm id={id} done={done("The reviewer was changed.")} cancel={() => setForm(null)} />}
      {form === "exclude" && <ExcludeForm id={id} done={done("The review was left out of the cycle.")} cancel={() => setForm(null)} />}

      {d.events.length > 0 && (
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
      )}
    </div>
  )
}
