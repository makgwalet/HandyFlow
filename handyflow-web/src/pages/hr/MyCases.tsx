// An employee's own matters: grievances they raised, and discipline or incapacity cases once they have been given notice or an outcome.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { useEmployeeOptions } from "./er.hooks"
import { CATEGORIES, TYPE_LABEL, fmtDate, fmtInstant, fmtTime, isFuture, todaySast, words } from "./er.logic"
import { card, errorText, input, plain, primary, td, th, unwrap, type Mine } from "./er.shared"
import { Chip, Facts, Field, Notice, PersonSelect, Section, StatusChip } from "./ErParts"

const BASE = "/api/v1/hr/employee-relations"

export default function MyCases() {
  const [openId, setOpenId] = useState<string | null>(null)
  const [lodging, setLodging] = useState(false)
  const q = useQuery<Mine[]>({ queryKey: ["er-mine"], queryFn: async () => unwrap<Mine[]>(await apiClient.get(`${BASE}/mine`)) ?? [] })

  if (openId) return <MyCase id={openId} back={() => setOpenId(null)} />
  const rows = q.data ?? []
  return (
    <div>
      <div style={{ display: "flex", marginBottom: 14, alignItems: "center" }}>
        <p style={{ margin: 0, fontSize: 13, color: "var(--hf-text-muted)", flex: 1 }}>Grievances you have raised, and any matter against you once you have been given notice or an outcome.</p>
        <button onClick={() => setLodging(l => !l)} style={primary}>{lodging ? "Close form" : "Lodge a grievance"}</button>
      </div>
      {lodging && <LodgeGrievance done={id => { setLodging(false); setOpenId(id) }} cancel={() => setLodging(false)} />}
      {q.isError && <Notice kind="error">{errorText(q.error, "Your cases could not be loaded.")}</Notice>}
      <div style={card}>
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr><th style={th}>Case</th><th style={th}>Kind</th><th style={th}>Status</th><th style={th}>Outcome</th></tr></thead>
          <tbody>
            {q.isLoading && <tr><td style={td} colSpan={4}>Loading…</td></tr>}
            {!q.isLoading && rows.length === 0 && <tr><td style={td} colSpan={4}>You have no cases.</td></tr>}
            {rows.map(r => (
              <tr key={r.id}>
                <td style={td}>
                  <button onClick={() => setOpenId(r.id)} style={{ background: "none", border: "none", padding: 0, cursor: "pointer", color: "var(--hf-primary)", fontWeight: 700 }}>{r.number}</button>
                  <div style={{ color: "var(--hf-text-muted)" }}>{r.title}</div>
                </td>
                <td style={td}>{TYPE_LABEL[r.type] ?? r.type}</td>
                <td style={td}><StatusChip status={r.status} /></td>
                <td style={td}>{words(r.decision?.outcome)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function LodgeGrievance({ done, cancel }: { done: (id: string) => void; cancel: () => void }) {
  const qc = useQueryClient()
  const emps = useEmployeeOptions()
  const [category, setCategory] = useState("")
  const [description, setDescription] = useState("")
  const [incidentDate, setIncidentDate] = useState(todaySast())
  const [respondent, setRespondent] = useState("")
  const [tried, setTried] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const problem = !category ? "Choose what the grievance is about." : !description.trim() ? "Describe your grievance." : !incidentDate ? "Say when it happened."
    : isFuture(incidentDate) ? "The date cannot be in the future." : null

  const m = useMutation({
    mutationFn: async () => unwrap<Mine>(await apiClient.post(`${BASE}/mine/grievances`, { category, description: description.trim(), incidentDate, respondentId: respondent || null })),
    onSuccess: d => { qc.invalidateQueries({ queryKey: ["er-mine"] }); done(d.id) },
    onError: e => setError(errorText(e, "Your grievance could not be lodged.")),
  })
  const canPickPerson = (emps.data ?? []).length > 0

  return (
    <form aria-label="Lodge a grievance" onSubmit={e => { e.preventDefault(); setTried(true); setError(null); if (!problem) m.mutate() }}
      style={{ border: "1.5px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 16, background: "var(--hf-surface)" }}>
      <h3 style={{ margin: "0 0 12px", fontSize: 15 }}>Lodge a grievance</h3>
      <Field id="gr-cat" text="What is it about?">
        <select id="gr-cat" value={category} onChange={e => setCategory(e.target.value)} style={input}>
          <option value="">Choose…</option>{CATEGORIES.GRIEVANCE.map(c => <option key={c} value={c}>{words(c)}</option>)}
        </select>
      </Field>
      <Field id="gr-date" text="When did it happen?"><input id="gr-date" type="date" value={incidentDate} max={todaySast()} onChange={e => setIncidentDate(e.target.value)} style={input} /></Field>
      {canPickPerson && <Field id="gr-who" text="Is it about a particular person?"><PersonSelect id="gr-who" value={respondent} onChange={setRespondent} options={emps.data ?? []} empty="No" /></Field>}
      <Field id="gr-desc" text="Describe what happened"><textarea id="gr-desc" rows={5} value={description} onChange={e => setDescription(e.target.value)} style={input} /></Field>
      <p style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>HR will pick this up and tell you who is handling it. Harassment, discrimination and victimisation grievances are kept confidential.</p>
      {tried && problem && <Notice kind="error">{problem}</Notice>}
      {error && <Notice kind="error">{error}</Notice>}
      <button type="submit" disabled={m.isPending} style={primary}>{m.isPending ? "Sending…" : "Lodge grievance"}</button>{" "}
      <button type="button" style={plain} onClick={cancel}>Cancel</button>
    </form>
  )
}

function MyCase({ id, back }: { id: string; back: () => void }) {
  const qc = useQueryClient()
  const [appealing, setAppealing] = useState(false)
  const [grounds, setGrounds] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState<string | null>(null)
  const q = useQuery<Mine>({ queryKey: ["er-mine", id], queryFn: async () => unwrap<Mine>(await apiClient.get(`${BASE}/mine/${id}`)) })
  const after = (d: Mine, text: string) => { qc.setQueryData(["er-mine", id], d); qc.invalidateQueries({ queryKey: ["er-mine"] }); setError(null); setSaved(text); setAppealing(false) }
  const ack = useMutation({
    mutationFn: async () => unwrap<Mine>(await apiClient.post(`${BASE}/mine/${id}/acknowledge`, {})),
    onSuccess: d => after(d, "Thank you. We have recorded that you received this."), onError: e => setError(errorText(e, "That could not be saved.")),
  })
  const appeal = useMutation({
    mutationFn: async () => unwrap<Mine>(await apiClient.post(`${BASE}/mine/${id}/appeal`, { grounds: grounds.trim() })),
    onSuccess: d => after(d, "Your appeal has been recorded."), onError: e => setError(errorText(e, "Your appeal could not be recorded.")),
  })

  if (q.isLoading) return <div style={{ padding: 24, color: "var(--hf-text-faint)" }}>Loading…</div>
  if (q.isError || !q.data) return <div><button onClick={back} style={plain}>Back</button><Notice kind="error">{errorText(q.error, "This case could not be opened.")}</Notice></div>
  const c = q.data, h = c.hearing, d = c.decision, a = c.appeal
  const grievance = c.type === "GRIEVANCE"

  return (
    <div>
      <button onClick={back} style={{ ...plain, marginBottom: 12 }}>← Back</button>
      <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap", marginBottom: 12 }}>
        <h2 style={{ margin: 0, fontSize: 20 }}>{c.number} · {c.title}</h2>
        <Chip text={TYPE_LABEL[c.type] ?? c.type} color="var(--hf-text-secondary)" bg="var(--hf-surface-sunken)" />
        <StatusChip status={c.status} />
      </div>
      {saved && <Notice kind="ok">{saved}</Notice>}
      {error && <Notice kind="error">{error}</Notice>}

      <Section title="What was recorded">
        <Facts rows={[["Category", words(c.category)], ["Reported", fmtDate(c.reportedOn)], ["Description", c.description]]} />
      </Section>
      {h?.date && (
        <Section title={c.type === "INCAPACITY" ? "Your meeting" : "Your hearing"}>
          <Facts rows={[["Notice given", fmtInstant(h.noticeIssuedAt)], ["Date and time", `${fmtDate(h.date)} at ${fmtTime(h.time)}`], ["Venue", h.venue],
            ["You may bring", "A colleague or a union representative."]]} />
        </Section>
      )}
      {c.charges.length > 0 && (
        <Section title="The charges">
          <ol style={{ margin: 0, paddingLeft: 18, fontSize: 13 }}>{c.charges.map(x => <li key={x.id}>{x.charge}{x.finding ? ` – ${words(x.finding)}` : ""}</li>)}</ol>
        </Section>
      )}
      {d?.outcome && (
        <Section title={grievance ? "The response to your grievance" : "The outcome"}>
          <Facts rows={[["Outcome", words(d.outcome)], ["Date", fmtDate(d.decidedOn)], ["Reasons", d.note],
            ["Warning valid until", d.warningValidUntil ? fmtDate(d.warningValidUntil) : null],
            ["CCMA referral deadline", d.ccmaDeadline ? fmtDate(d.ccmaDeadline) : null]]} />
          {d.outcome === "DISMISSAL" && d.ccmaDeadline && (
            <p style={{ fontSize: 13, margin: "10px 0 0" }}>If you disagree with a dismissal, you can refer a dispute to the CCMA within 30 days of the dismissal.</p>
          )}
        </Section>
      )}
      {a?.lodgedOn && (
        <Section title={grievance ? "Your next level" : "Your appeal"}>
          <Facts rows={[["Lodged", fmtDate(a.lodgedOn)], ["Grounds", a.grounds], ["Result", a.outcome ? words(a.outcome) : "Waiting for a decision"], ["Reasons", a.note]]} />
        </Section>
      )}

      <div style={{ display: "flex", gap: 8, margin: "12px 0 16px", flexWrap: "wrap" }}>
        {c.canAcknowledge && <button onClick={() => ack.mutate()} disabled={ack.isPending} style={primary}>I have received this</button>}
        {c.canAppeal && <button onClick={() => setAppealing(x => !x)} style={plain}>{grievance ? "Take it to the next level" : "Appeal"}</button>}
      </div>
      {c.canAcknowledge && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: -8 }}>Confirming receipt does not mean you agree with it.</p>}
      {appealing && (
        <form aria-label="Appeal" onSubmit={e => { e.preventDefault(); if (grounds.trim()) appeal.mutate() }}
          style={{ border: "1.5px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 16 }}>
          <Field id="ap-grounds" text="Why do you disagree?"><textarea id="ap-grounds" rows={4} value={grounds} onChange={e => setGrounds(e.target.value)} style={input} /></Field>
          <button type="submit" disabled={!grounds.trim() || appeal.isPending} style={primary}>{appeal.isPending ? "Sending…" : "Send"}</button>
        </form>
      )}

      <Section title="What has happened">
        <ol style={{ margin: 0, paddingLeft: 18, fontSize: 13 }}>
          {c.events.map((e, i) => <li key={`${e.action}-${i}`} style={{ marginBottom: 4 }}><strong>{words(e.action)}</strong>{e.note ? ` – ${e.note}` : ""} <span style={{ color: "var(--hf-text-faint)" }}>· {fmtInstant(e.doneAt)}</span></li>)}
        </ol>
      </Section>
    </div>
  )
}
