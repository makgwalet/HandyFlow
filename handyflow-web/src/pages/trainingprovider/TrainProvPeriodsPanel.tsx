// Reporting periods. Overdue and due-soon are worked out by the server from each due day. A submission is a staff record.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import {
  MIN_REASON, REPORT_NOTE, STATES, acknowledgeProblem, computedDue, emptyPeriod, label, periodBody, periodLine, periodProblem, reasonProblem, stateTone, submitProblem,
  type Obligation, type Period, type PeriodForm,
} from "./reportRules"
import { Badge, Notice } from "./tpUi"
import { card, errText, fmtDate, ghost, input, muted, solid, todayIso, unwrap } from "./tpStyle"
import { RPT } from "./tpApi"

type Mode = { kind: "submit" | "ack" | "waive" | "void"; id: string } | null

function NewPeriod({ obligations, onSaved, onCancel }: { obligations: Obligation[]; onSaved: () => void; onCancel: () => void }) {
  const [f, setF] = useState<PeriodForm>(emptyPeriod)
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const days = obligations.find(o => o.id === f.obligationId)?.dueDaysAfterEnd ?? null
  const set = (k: keyof PeriodForm) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setF({ ...f, [k]: e.target.value })
  const rule = days !== null && f.periodEnd ? computedDue(f.periodEnd, days) : null
  const save = async () => {
    const p = periodProblem(f, days); if (p) { setError(p); return }
    setBusy(true); setError("")
    try { await apiClient.post(`${RPT}/report-periods`, periodBody(f, days)); onSaved() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  const differs = !!f.dueOn && rule !== null && f.dueOn !== rule
  return (
    <div style={{ ...card, display: "grid", gap: 8, background: "var(--hf-surface-muted)" }}>
      <select aria-label="Obligation" style={input} value={f.obligationId} onChange={set("obligationId")}>
        <option value="">Choose the obligation</option>{obligations.map(o => <option key={o.id} value={o.id}>{o.obligationNo} · {o.name} ({o.authorityName})</option>)}
      </select>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 8 }}>
        <input aria-label="Period start" type="date" style={input} value={f.periodStart} onChange={set("periodStart")} />
        <input aria-label="Period end" type="date" style={input} value={f.periodEnd} onChange={set("periodEnd")} />
        <input aria-label="Due day" type="date" style={input} value={f.dueOn} onChange={set("dueOn")} />
      </div>
      <div style={muted}>{rule ? `By the obligation's rule this falls due on ${rule}. Leave the due day empty to use that.` : "Choose the obligation and the end of the period to see when it falls due."}</div>
      {differs && <input aria-label="Reason for a different due day" style={input} placeholder={`Why is the due day different? (at least ${MIN_REASON} characters)`} value={f.overrideReason} onChange={set("overrideReason")} />}
      <Notice text={error} />
      <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={save}>Add period</button><button style={ghost} onClick={onCancel}>Cancel</button></div>
    </div>
  )
}

export default function TrainProvPeriodsPanel() {
  const qc = useQueryClient()
  const canManage = usePermissions(["TRAININGPROVIDER_MANAGE", "TRAININGPROVIDER_ADMIN"])
  const [state, setState] = useState("")
  const [status, setStatus] = useState("")
  const [adding, setAdding] = useState(false)
  const [mode, setMode] = useState<Mode>(null)
  const [day, setDay] = useState(todayIso())
  const [ref, setRef] = useState("")
  const [text, setText] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const { data: obligations = [] } = useQuery<Obligation[]>({
    queryKey: ["tp-obligations", false],
    queryFn: async () => unwrap<Obligation[]>(await apiClient.get(`${RPT}/report-obligations`, { params: { status: "ACTIVE" } })),
  })
  const { data = [], isLoading } = useQuery<Period[]>({
    queryKey: ["tp-report-periods", state, status],
    queryFn: async () => unwrap<Period[]>(await apiClient.get(`${RPT}/report-periods`, { params: { state: state || undefined, status: status || undefined } })),
  })
  const reset = () => { setMode(null); setRef(""); setText(""); setError(""); setDay(todayIso()) }
  const changed = () => { void qc.invalidateQueries({ queryKey: ["tp-report-periods"] }) }
  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true); setError("")
    try { await fn(); reset(); changed() } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  const submit = (p: Period) => {
    const pr = submitProblem(day, p.periodStart, todayIso()); if (pr) { setError(pr); return }
    void run(() => apiClient.post(`${RPT}/report-periods/${p.id}/submit`, { submittedOn: day, submissionRef: ref.trim() || null, note: text.trim() || null }))
  }
  const acknowledge = (p: Period) => {
    const pr = acknowledgeProblem(day, p.submittedOn, todayIso()); if (pr) { setError(pr); return }
    void run(() => apiClient.post(`${RPT}/report-periods/${p.id}/acknowledge`, { acknowledgedOn: day, acknowledgementRef: ref.trim() || null }))
  }
  const withReason = (p: Period, kind: "waive" | "void") => {
    const pr = reasonProblem(text); if (pr) { setError(pr); return }
    void run(() => apiClient.post(`${RPT}/report-periods/${p.id}/${kind}`, { reason: text.trim() }))
  }
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={muted}>{REPORT_NOTE}</div>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        <select aria-label="Period state filter" style={{ ...input, width: "auto" }} value={state} onChange={e => setState(e.target.value)}>
          <option value="">All states</option>{Object.entries(STATES).map(([k, l]) => <option key={k} value={k}>{l}</option>)}
        </select>
        <select aria-label="Period status filter" style={{ ...input, width: "auto" }} value={status} onChange={e => setStatus(e.target.value)}>
          <option value="">All records</option><option value="OPEN">Open</option><option value="SUBMITTED">Submitted</option><option value="WAIVED">Waived</option><option value="VOID">Void</option>
        </select>
        {canManage && !adding && <button style={solid} onClick={() => setAdding(true)}>Add a period</button>}
      </div>
      {adding && <NewPeriod obligations={obligations} onCancel={() => setAdding(false)} onSaved={() => { setAdding(false); changed() }} />}
      {isLoading ? <div style={muted}>Loading…</div> : data.length === 0 ? <div style={muted}>No periods found.</div> : data.map(p => (
        <div key={p.id} style={card}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
            <div>
              <strong>{p.obligationName ?? "Report"} · {fmtDate(p.periodStart)} to {fmtDate(p.periodEnd)}</strong>
              <div style={muted}>{p.periodNo} · {p.authorityName ?? "Authority"}{p.programmeTitle ? ` · ${p.programmeTitle}` : ""}</div>
            </div>
            <Badge text={label(STATES, p.state)} tone={stateTone(p.state)} />
          </div>
          <div style={muted}>{periodLine(p)}</div>
          {p.dueOverrideReason && <div style={muted}>Due day changed: {p.dueOverrideReason}</div>}
          {p.note && <div style={muted}>{p.note}</div>}
          {canManage && !mode && p.status !== "VOID" && (
            <div style={{ display: "flex", gap: 8, marginTop: 8, flexWrap: "wrap" }}>
              {p.status === "OPEN" && <button style={ghost} onClick={() => setMode({ kind: "submit", id: p.id })}>Record as submitted</button>}
              {p.status === "SUBMITTED" && !p.acknowledgedOn && <button style={ghost} onClick={() => setMode({ kind: "ack", id: p.id })}>Record acknowledgement</button>}
              {p.status === "OPEN" && <button style={ghost} onClick={() => setMode({ kind: "waive", id: p.id })}>Waive</button>}
              <button style={ghost} onClick={() => setMode({ kind: "void", id: p.id })}>Void</button>
            </div>
          )}
          {mode?.id === p.id && (mode.kind === "submit" || mode.kind === "ack") && (
            <div style={{ display: "grid", gap: 6, marginTop: 8 }}>
              <input aria-label={mode.kind === "submit" ? "Day submitted" : "Day acknowledged"} type="date" style={input} value={day} onChange={e => setDay(e.target.value)} />
              <input aria-label="Reference" style={input} placeholder="Reference (optional)" value={ref} onChange={e => setRef(e.target.value)} />
              {mode.kind === "submit" && <input aria-label="Submission note" style={input} placeholder="Note (optional)" value={text} onChange={e => setText(e.target.value)} />}
              <Notice text={error} />
              <div style={{ display: "flex", gap: 8 }}>
                <button style={solid} disabled={busy} onClick={() => (mode.kind === "submit" ? submit(p) : acknowledge(p))}>{mode.kind === "submit" ? "Save as submitted" : "Save acknowledgement"}</button>
                <button style={ghost} onClick={reset}>Cancel</button>
              </div>
            </div>
          )}
          {mode?.id === p.id && (mode.kind === "waive" || mode.kind === "void") && (
            <div style={{ display: "grid", gap: 6, marginTop: 8 }}>
              <input aria-label={mode.kind === "waive" ? "Reason for waiving" : "Reason for voiding the period"} style={input}
                placeholder={mode.kind === "waive" ? "Why is this period waived?" : "Why is this record being voided?"} value={text} onChange={e => setText(e.target.value)} />
              <Notice text={error} />
              <div style={{ display: "flex", gap: 8 }}>
                <button style={solid} disabled={busy} onClick={() => withReason(p, mode.kind as "waive" | "void")}>{mode.kind === "waive" ? "Waive the period" : "Void the record"}</button>
                <button style={ghost} onClick={reset}>Cancel</button>
              </div>
            </div>
          )}
        </div>
      ))}
    </div>
  )
}
