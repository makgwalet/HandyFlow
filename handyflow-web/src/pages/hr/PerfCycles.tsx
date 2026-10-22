// Review cycles (HR): the list, the form, and one cycle with its reviews.
import { useState } from "react"
import { fmtDate } from "./er.logic"
import { useEmployeeOptions } from "./er.hooks"
import { usePerfList, usePerfOne, useSend } from "./perf.hooks"
import { cycleProblem, type CycleForm } from "./perf.logic"
import { card, errorText, input, plain, primary, td, th, type CycleDetail, type CycleRow } from "./perf.shared"
import { CycleStatus, Field, Notice, PersonSelect } from "./PerfParts"
import { FormFrame } from "./PerfForms"
import { ReviewList } from "./PerfReviews"

export default function CycleScreen({ openReview }: { openReview: (id: string) => void }) {
  const q = usePerfList<CycleRow>(["cycles"], "/cycles")
  const [openId, setOpenId] = useState<string | null>(null)
  const [creating, setCreating] = useState(false)
  if (openId) return <CycleView id={openId} back={() => setOpenId(null)} openReview={openReview} />
  const rows = q.data ?? []
  return (
    <div>
      <div style={{ display: "flex", marginBottom: 14 }}>
        <div style={{ flex: 1, fontSize: 13, color: "var(--hf-text-muted)" }}>A cycle is one round of reviews for a period.</div>
        <button onClick={() => setCreating(c => !c)} style={primary}>{creating ? "Close form" : "New cycle"}</button>
      </div>
      {creating && <CycleFormView done={() => setCreating(false)} />}
      {q.isError && <Notice kind="error">{errorText(q.error, "The cycles could not be loaded.")}</Notice>}
      {!q.isLoading && rows.length === 0 && <div style={{ ...card, padding: 16, fontSize: 13 }}>No cycles yet.</div>}
      {rows.length > 0 && (
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
          <thead><tr><th style={th}>Cycle</th><th style={th}>Period</th><th style={th}>Status</th><th style={th}>Reviews</th><th style={th}>Waiting</th><th style={th}>Released</th></tr></thead>
          <tbody>{rows.map(c => (
            <tr key={c.id}>
              <td style={td}><button onClick={() => setOpenId(c.id)} style={{ ...plain, border: "none", padding: 0, fontWeight: 600 }}>{c.name}</button></td>
              <td style={td}>{fmtDate(c.periodStart)} – {fmtDate(c.periodEnd)}</td>
              <td style={td}><CycleStatus status={c.status} /></td>
              <td style={td}>{c.total}</td>
              <td style={td}>{c.awaitingSelf + c.awaitingManager}</td>
              <td style={td}>{c.released}</td>
            </tr>))}
          </tbody>
        </table>
      )}
    </div>
  )
}

function CycleFormView({ cycle, done }: { cycle?: CycleRow; done: () => void }) {
  const [f, setF] = useState<CycleForm>({
    name: cycle?.name ?? "", periodStart: cycle?.periodStart ?? "", periodEnd: cycle?.periodEnd ?? "",
    selfDue: cycle?.selfDue ?? "", managerDue: cycle?.managerDue ?? "", include360: cycle?.include360 ?? false,
  })
  const s = useSend<CycleForm, CycleRow>(cycle ? "put" : "post", cycle ? `/cycles/${cycle.id}` : "/cycles", "The cycle could not be saved.", done)
  const date = (key: "periodStart" | "periodEnd" | "selfDue" | "managerDue", text: string) => (
    <Field id={`pf-c-${key}`} text={text}><input id={`pf-c-${key}`} type="date" value={f[key]} onChange={e => setF({ ...f, [key]: e.target.value })} style={input} /></Field>
  )
  return (
    <FormFrame title={cycle ? "Change the cycle" : "New cycle"} label={cycle ? "Save changes" : "Create cycle"} problem={cycleProblem(f)} pending={s.pending} error={s.error} submit={() => s.send(f)} onCancel={done}>
      <Field id="pf-c-name" text="Name"><input id="pf-c-name" value={f.name} onChange={e => setF({ ...f, name: e.target.value })} style={input} placeholder="Annual review 2026" /></Field>
      {date("periodStart", "Period starts")}{date("periodEnd", "Period ends")}{date("selfDue", "Self-reviews due")}{date("managerDue", "Manager reviews due")}
      <label style={{ display: "flex", gap: 8, fontSize: 13, marginBottom: 12 }}>
        <input type="checkbox" checked={f.include360} onChange={e => setF({ ...f, include360: e.target.checked })} />Allow 360° feedback in this cycle
      </label>
    </FormFrame>
  )
}

function CycleView({ id, back, openReview }: { id: string; back: () => void; openReview: (id: string) => void }) {
  const q = usePerfOne<CycleDetail>(["cycle", id], `/cycles/${id}`)
  const [form, setForm] = useState<"edit" | "add" | null>(null)
  const launch = useSend<object, CycleDetail>("post", `/cycles/${id}/launch`, "The cycle could not be launched.")
  const close = useSend<object, CycleDetail>("post", `/cycles/${id}/close`, "The cycle could not be closed.")
  const del = useSend<object, null>("delete", `/cycles/${id}`, "The cycle could not be deleted.", back)
  if (q.isError || !q.data) return <div><button onClick={back} style={plain}>Back</button>{q.isError && <Notice kind="error">{errorText(q.error, "The cycle could not be opened.")}</Notice>}</div>
  const d = q.data, c = d.row
  const can = (a: string) => d.actions.includes(a)
  const err = launch.error ?? close.error ?? del.error
  return (
    <div>
      <button onClick={back} style={plain}>Back to cycles</button>
      <div style={{ display: "flex", gap: 10, alignItems: "center", margin: "12px 0", flexWrap: "wrap" }}>
        <h3 style={{ margin: 0 }}>{c.name}</h3><CycleStatus status={c.status} />
        <span style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>{fmtDate(c.periodStart)} – {fmtDate(c.periodEnd)} · self-reviews due {fmtDate(c.selfDue)} · managers due {fmtDate(c.managerDue)}</span>
      </div>
      {d.withoutReviewer > 0 && <Notice kind="error">{d.withoutReviewer} review{d.withoutReviewer === 1 ? " has" : "s have"} no reviewer, usually because the manager has left. Open each one and choose a reviewer.</Notice>}
      {err && <Notice kind="error">{err}</Notice>}
      <div style={{ display: "flex", gap: 8, marginBottom: 14, flexWrap: "wrap" }}>
        {can("EDIT") && <button onClick={() => setForm("edit")} style={plain}>Change</button>}
        {can("LAUNCH") && <button onClick={() => launch.send({})} disabled={launch.pending} style={primary}>Launch cycle</button>}
        {can("ADD_REVIEW") && <button onClick={() => setForm("add")} style={plain}>Add an employee</button>}
        {can("CLOSE") && <button onClick={() => close.send({})} disabled={close.pending} style={plain}>Close cycle</button>}
        {can("DELETE") && <button onClick={() => del.send({})} disabled={del.pending} style={plain}>Delete draft</button>}
      </div>
      {form === "edit" && <CycleFormView cycle={c} done={() => setForm(null)} />}
      {form === "add" && <AddReview cycleId={id} done={() => setForm(null)} />}
      {c.status === "DRAFT" ? <div style={{ ...card, padding: 16, fontSize: 13 }}>Launching makes a review for every employee who worked in the period.</div>
        : <ReviewList scope="all" cycleId={id} open={openReview} empty="No reviews in this cycle." />}
    </div>
  )
}

function AddReview({ cycleId, done }: { cycleId: string; done: () => void }) {
  const people = useEmployeeOptions()
  const [employeeId, setEmployeeId] = useState("")
  const s = useSend<{ employeeId: string }, unknown>("post", `/cycles/${cycleId}/reviews`, "The review could not be added.", done)
  return (
    <FormFrame title="Add an employee to the cycle" label="Add review" problem={employeeId ? null : "Choose the employee."} pending={s.pending} error={s.error} submit={() => s.send({ employeeId })} onCancel={done}>
      <Field id="pf-add-emp" text="Employee"><PersonSelect id="pf-add-emp" value={employeeId} onChange={setEmployeeId} options={people.data ?? []} /></Field>
    </FormFrame>
  )
}
