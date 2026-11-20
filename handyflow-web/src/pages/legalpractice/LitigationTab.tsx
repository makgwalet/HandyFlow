// Court cases on matters: the stage a case has reached, its hearings (each is also a court date on the matter's calendar) and the orders the court
// made. A concluded case is read-only until it is reopened. Nothing is deleted; every change is written to the case's history.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, Empty, ErrorLine, Input, Pager, Pick, Pill, SectionTitle, card, day, errorText, muted, pageOf, td, th } from "./kit"
import { BASE } from "./lookups"
import type { Matter } from "./MattersTab"
import {
  LIT_COURT_LEVELS, LIT_HEARING_TYPES, LIT_OPEN_STAGES, LIT_OUTCOMES, LIT_ROLES, LIT_STAGES, emptyLitCase, isoDay, litActions, litCaseProblem, litCaseRequest, litCaseToForm, litCompliedProblem,
  litConcludeProblem, litCourtLabel, litDaysAway, litDaysText, litEventText, litHearingActions, litHearingLabel, litHearingProblem, litHearingTone, litHeldProblem, litOrderProblem,
  litOutcomeLabel, litOutcomeTone, litPostponeProblem, litReopenProblem, litRoleLabel, litStageLabel, litStageProblem, litStageTone, litTime,
  type LitCase, type LitCaseForm, type LitCaseSummary, type LitHearing, type LitOrder, type LitRollRow,
} from "./legal.logic"

const URL = `${BASE}/litigation`
const unwrap = <T,>(r: { data?: { data?: T } & T }): T => (r.data?.data ?? r.data) as T
const addDays = (iso: string, n: number) => { const d = new Date(iso + "T12:00:00"); d.setDate(d.getDate() + n); return isoDay(d) }

export default function LitigationTab() {
  const [view, setView] = useState<"cases" | "roll">("cases")
  const [openId, setOpenId] = useState<string | null>(null), [opening, setOpening] = useState(false)
  const write = usePermissions(["LEGALPRACTICE_LITIGATION_MANAGE", "LEGALPRACTICE_ADMIN"])
  return (
    <div>
      <SectionTitle right={write && <Btn kind="primary" onClick={() => setOpening(true)}>Open a case</Btn>}>Litigation</SectionTitle>
      <div style={{ display: "flex", gap: 6, marginBottom: 10 }}>
        <Btn small kind={view === "cases" ? "primary" : undefined} onClick={() => setView("cases")}>Cases</Btn>
        <Btn small kind={view === "roll" ? "primary" : undefined} onClick={() => setView("roll")}>Court roll</Btn>
      </div>
      {view === "cases" ? <CaseList onOpen={setOpenId} /> : <Roll onOpen={setOpenId} />}
      {opening && <CaseFormDialog creating onClose={() => setOpening(false)} onDone={id => { setOpening(false); setOpenId(id) }} />}
      {openId && <CaseDialog id={openId} onClose={() => setOpenId(null)} />}
    </div>
  )
}

// ---- the list --------------------------------------------------------------------------------------------------------------

function CaseList({ onOpen }: { onOpen: (id: string) => void }) {
  const [search, setSearch] = useState(""), [stage, setStage] = useState(""), [concluded, setConcluded] = useState(false), [page, setPage] = useState(0)
  const today = isoDay(new Date())
  const list = useQuery({
    queryKey: ["lp-lit", search, stage, concluded, page],
    queryFn: async () => pageOf<LitCaseSummary>(await apiClient.get(URL, { params: { search: search.trim() || undefined, stage: stage || undefined, includeConcluded: concluded, page, size: 20 } })),
  })
  const rows = list.data?.content ?? []
  return (
    <div>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "flex-end", marginBottom: 8 }}>
        <div style={{ minWidth: 220, flex: "1 1 220px" }}><Input label="Search by case number, court or other side" value={search} onChange={v => { setSearch(v); setPage(0) }} /></div>
        <div style={{ minWidth: 170 }}><Pick label="Stage" value={stage} onChange={v => { setStage(v); setPage(0) }} blank="Any stage" options={LIT_OPEN_STAGES} /></div>
        <label style={{ fontSize: 13, display: "inline-flex", gap: 6, alignItems: "center", marginBottom: 14 }}>
          <input type="checkbox" checked={concluded} onChange={e => { setConcluded(e.target.checked); setPage(0) }} /> Include concluded
        </label>
      </div>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {list.isError ? <Empty>Could not load the cases.</Empty> : list.isLoading ? <Empty>Loading…</Empty> : rows.length === 0 ? <Empty>No court cases found.</Empty> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Case</th><th style={th}>Matter</th><th style={th}>Stage</th><th style={th}>Next hearing</th><th style={th} /></tr></thead>
            <tbody>{rows.map(c => (
              <tr key={c.id}>
                <td style={td}><b>{c.caseNumber ?? "No case number yet"}</b><div style={muted}>{c.courtName} · {litRoleLabel(c.ourRole)}{c.opposingParty ? ` v ${c.opposingParty}` : ""}</div></td>
                <td style={td}>{c.matterLabel}</td>
                <td style={td}><Pill tone={litStageTone(c.stage)}>{litStageLabel(c.stage)}</Pill>{c.outcome && <div style={{ marginTop: 3 }}><Pill tone={litOutcomeTone(c.outcome)}>{litOutcomeLabel(c.outcome)}</Pill></div>}</td>
                <td style={td}>{c.nextHearing ? <>{day(c.nextHearing)}<div style={muted}>{litDaysText(litDaysAway(c.nextHearing, today))}</div></> : "—"}</td>
                <td style={{ ...td, textAlign: "right" }}><Btn small onClick={() => onOpen(c.id)}>Open</Btn></td>
              </tr>))}</tbody>
          </table>)}
      </div>
      <Pager page={page} pages={list.data?.totalPages ?? 0} onPage={setPage} />
    </div>
  )
}

function Roll({ onOpen }: { onOpen: (id: string) => void }) {
  const today = isoDay(new Date())
  const [from, setFrom] = useState(today), [to, setTo] = useState(addDays(today, 30))
  const valid = !!from && !!to && to >= from
  const roll = useQuery({
    queryKey: ["lp-lit-roll", from, to], enabled: valid,
    queryFn: async () => unwrap<LitRollRow[]>(await apiClient.get(`${URL}/roll`, { params: { from, to } })),
  })
  const rows = roll.data ?? []
  return (
    <div>
      <div style={{ ...muted, marginBottom: 8 }}>Hearings that are still scheduled, across the firm, in date order. Postponed hearings show on their new date.</div>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 8 }}>
        <div style={{ width: 170 }}><Input label="From" type="date" value={from} onChange={setFrom} /></div>
        <div style={{ width: 170 }}><Input label="To" type="date" value={to} onChange={setTo} /></div>
      </div>
      {!valid && <ErrorLine text="Choose a start date and an end date after it" />}
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {!valid ? <Empty>Choose a date range.</Empty> : roll.isError ? <Empty>Could not load the court roll.</Empty> : roll.isLoading ? <Empty>Loading…</Empty> : rows.length === 0 ? <Empty>No hearings in this range.</Empty> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Date</th><th style={th}>Hearing</th><th style={th}>Matter</th><th style={th}>Court</th><th style={th} /></tr></thead>
            <tbody>{rows.map(r => (
              <tr key={r.hearingId}>
                <td style={td}><b>{day(r.hearingDate)}</b>{r.hearingTime ? ` ${litTime(r.hearingTime)}` : ""}<div style={muted}>{litDaysText(litDaysAway(r.hearingDate, today))}</div>
                  {r.warnings.map(w => <div key={w} style={{ ...muted, color: "var(--hf-warning-text, inherit)" }}>{w}</div>)}</td>
                <td style={td}>{litHearingLabel(r.hearingType)}{r.postponements > 0 && <div style={muted}>Postponed {r.postponements} {r.postponements === 1 ? "time" : "times"}</div>}{r.appearing && <div style={muted}>{r.appearing}</div>}</td>
                <td style={td}>{r.matterLabel}<div style={muted}>{r.caseNumber ?? ""}</div></td>
                <td style={td}>{r.courtName}{r.courtRoom && <div style={muted}>{r.courtRoom}</div>}</td>
                <td style={{ ...td, textAlign: "right" }}><Btn small onClick={() => onOpen(r.caseId)}>Open case</Btn></td>
              </tr>))}</tbody>
          </table>)}
      </div>
    </div>
  )
}

// ---- open or edit a case ---------------------------------------------------------------------------------------------------

function CaseFormDialog({ creating, current, onClose, onDone }: { creating: boolean; current?: LitCase; onClose: () => void; onDone: (id: string) => void }) {
  const qc = useQueryClient(), today = isoDay(new Date())
  const [f, setF] = useState<LitCaseForm>(current ? litCaseToForm(current) : emptyLitCase()), [err, setErr] = useState("")
  const set = (k: keyof LitCaseForm) => (v: string) => setF(s => ({ ...s, [k]: v }))
  const matters = useQuery({ enabled: creating, queryKey: ["lp-lit-matters"], queryFn: async () => pageOf<Matter>(await apiClient.get(`${BASE}/matters`, { params: { size: 500 } })) })
  const problem = litCaseProblem(f, creating, today)
  const save = useMutation({
    mutationFn: async () => unwrap<LitCase>(creating ? await apiClient.post(URL, litCaseRequest(f, true)) : await apiClient.put(`${URL}/${current!.id}`, litCaseRequest(f, false))),
    onSuccess: c => { toast.success(creating ? "Case opened" : "Saved"); qc.invalidateQueries({ queryKey: ["lp-lit"] }); qc.invalidateQueries({ queryKey: ["lp-lit-case", c.id] }); onDone(c.id) },
    onError: async e => setErr(await errorText(e, "Could not save the case")),
  })
  return (
    <Dialog title={creating ? "Open a court case" : "Change the case details"} width={640} onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : creating ? "Open case" : "Save"}</Btn></>}>
      {creating && <Pick label="Matter" value={f.matterId} onChange={set("matterId")} blank={matters.isLoading ? "Loading…" : "Choose a matter"} options={(matters.data?.content ?? []).map(m => [m.id, `${m.matterNumber} ${m.matterName}`] as const)} />}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: "0 12px" }}>
        <Pick label="Kind of court" value={f.courtLevel} onChange={set("courtLevel")} options={LIT_COURT_LEVELS} />
        <Input label="Court (division or town)" value={f.courtName} onChange={set("courtName")} />
        <Input label="Case number" value={f.caseNumber} onChange={set("caseNumber")} hint="Needed once the case has been issued" />
        <Pick label="Our client is the" value={f.ourRole} onChange={set("ourRole")} options={LIT_ROLES} />
        {creating && <Pick label="Stage" value={f.stage} onChange={set("stage")} options={LIT_OPEN_STAGES} />}
        <Input label="Judge" value={f.judge} onChange={set("judge")} />
        <Input label="Date issued" type="date" value={f.issuedOn} onChange={set("issuedOn")} max={today} />
        <Input label="Date served" type="date" value={f.servedOn} onChange={set("servedOn")} max={today} />
        <Input label="Other side" value={f.opposingParty} onChange={set("opposingParty")} />
        <Input label="Their attorneys" value={f.opposingAttorney} onChange={set("opposingAttorney")} />
        <Input label="Their contact details" value={f.opposingAttorneyContact} onChange={set("opposingAttorneyContact")} />
      </div>
      <Input label="Notes" value={f.notes} onChange={set("notes")} />
      {problem && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

// ---- one case --------------------------------------------------------------------------------------------------------------

type Step = null | "edit" | "stage" | "conclude" | "reopen" | "hearing" | "order" | { postpone: LitHearing } | { held: LitHearing } | { cancel: LitHearing } | { complied: LitOrder }

function CaseDialog({ id, onClose }: { id: string; onClose: () => void }) {
  const qc = useQueryClient()
  const write = usePermissions(["LEGALPRACTICE_LITIGATION_MANAGE", "LEGALPRACTICE_ADMIN"]), conclude = usePermissions(["LEGALPRACTICE_LITIGATION_CONCLUDE", "LEGALPRACTICE_ADMIN"])
  const [step, setStep] = useState<Step>(null), [warnings, setWarnings] = useState<string[]>([])
  const q = useQuery({ queryKey: ["lp-lit-case", id], queryFn: async () => unwrap<LitCase>(await apiClient.get(`${URL}/${id}`)) })
  const c = q.data
  const a = c ? litActions(c, { write, conclude }) : null
  /** Every change returns the whole case again, with any warnings about the dates that were just set. */
  const changed = (r: LitCase) => { qc.setQueryData(["lp-lit-case", id], r); qc.invalidateQueries({ queryKey: ["lp-lit"] }); qc.invalidateQueries({ queryKey: ["lp-lit-roll"] }); setWarnings(r.warnings ?? []); setStep(null) }
  return (
    <Dialog title={c ? `${c.caseNumber ?? "Court case"} · ${c.matterLabel}` : "Court case"} width={820} onClose={onClose}>
      {q.isError ? <Empty>Could not load this case.</Empty> : !c || !a ? <Empty>Loading…</Empty> : (
        <div>
          <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginBottom: 6 }}>
            <Pill tone={litStageTone(c.stage)}>{litStageLabel(c.stage)}</Pill>
            {c.outcome && <Pill tone={litOutcomeTone(c.outcome)}>{litOutcomeLabel(c.outcome)}</Pill>}
            <Pill tone="neutral">{litCourtLabel(c.courtLevel)}</Pill>
          </div>
          <div style={muted}>{c.courtName} · our client is the {litRoleLabel(c.ourRole).toLowerCase()}{c.opposingParty ? ` v ${c.opposingParty}` : ""}</div>
          {(c.opposingAttorney || c.judge) && <div style={muted}>{c.opposingAttorney ? `Their attorneys: ${c.opposingAttorney}${c.opposingAttorneyContact ? ` (${c.opposingAttorneyContact})` : ""}` : ""}{c.opposingAttorney && c.judge ? " · " : ""}{c.judge ? `Judge: ${c.judge}` : ""}</div>}
          {(c.issuedOn || c.servedOn) && <div style={muted}>{c.issuedOn ? `Issued ${day(c.issuedOn)}` : ""}{c.issuedOn && c.servedOn ? " · " : ""}{c.servedOn ? `Served ${day(c.servedOn)}` : ""}</div>}
          {c.notes && <div style={{ ...muted, marginTop: 4 }}>{c.notes}</div>}
          {!c.open && <div style={{ ...muted, marginTop: 6 }}>Concluded on {day(c.concludedOn)}: {c.outcomeNote}</div>}
          {[...new Set([...(c.warnings ?? []), ...warnings])].map(w => <div key={w} style={{ ...muted, marginTop: 6, color: "var(--hf-warning-text, inherit)" }}>⚠ {w}</div>)}

          <div style={{ display: "flex", gap: 6, flexWrap: "wrap", margin: "12px 0" }}>
            {a.canSchedule && <Btn small kind="primary" onClick={() => setStep("hearing")}>Set a hearing</Btn>}
            {a.canOrder && <Btn small onClick={() => setStep("order")}>Record a court order</Btn>}
            {a.canStage && <Btn small onClick={() => setStep("stage")}>Change stage</Btn>}
            {a.canEdit && <Btn small onClick={() => setStep("edit")}>Change details</Btn>}
            {a.canConclude && <Btn small onClick={() => setStep("conclude")}>Conclude the case</Btn>}
            {a.canReopen && <Btn small onClick={() => setStep("reopen")}>Reopen the case</Btn>}
          </div>

          <div style={{ fontWeight: 700, margin: "6px 0" }}>Hearings</div>
          <div style={{ ...card, padding: 0, overflowX: "auto" }}>
            {c.hearings.length === 0 ? <Empty>No hearings yet.</Empty> : (
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead><tr><th style={th}>Date</th><th style={th}>Hearing</th><th style={th}>Status</th><th style={th} /></tr></thead>
                <tbody>{c.hearings.map(h => {
                  const ha = litHearingActions(h, c.open, write)
                  return (
                    <tr key={h.id}>
                      <td style={td}><b>{day(h.hearingDate)}</b>{h.hearingTime ? ` ${litTime(h.hearingTime)}` : ""}{h.postponements > 0 && <div style={muted}>Postponed {h.postponements} {h.postponements === 1 ? "time" : "times"}</div>}</td>
                      <td style={td}>{litHearingLabel(h.hearingType)}<div style={muted}>{[h.courtRoom, h.appearing].filter(Boolean).join(" · ")}</div></td>
                      <td style={td}><Pill tone={litHearingTone(h.status)}>{h.status === "SCHEDULED" ? "Scheduled" : h.status === "HELD" ? "Held" : "Cancelled"}</Pill>{(h.outcome || h.cancelReason) && <div style={muted}>{h.outcome ?? h.cancelReason}</div>}</td>
                      <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}>{ha.canChange && <>
                        <Btn small onClick={() => setStep({ held: h })}>Held</Btn>{" "}<Btn small onClick={() => setStep({ postpone: h })}>Postpone</Btn>{" "}<Btn small onClick={() => setStep({ cancel: h })}>Cancel</Btn></>}</td>
                    </tr>)
                })}</tbody>
              </table>)}
          </div>

          <div style={{ fontWeight: 700, margin: "14px 0 6px" }}>Court orders</div>
          <div style={{ ...card, padding: 0, overflowX: "auto" }}>
            {c.orders.length === 0 ? <Empty>No orders recorded.</Empty> : (
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead><tr><th style={th}>Order</th><th style={th}>Comply by</th><th style={th} /></tr></thead>
                <tbody>{c.orders.map(o => (
                  <tr key={o.id}>
                    <td style={td}>{o.summary}<div style={muted}>Made {day(o.orderDate)}</div></td>
                    <td style={td}>{o.complianceDue ? day(o.complianceDue) : "—"}{o.compliedOn ? <div style={muted}>Complied {day(o.compliedOn)}{o.compliedNote ? `: ${o.compliedNote}` : ""}</div> : o.outstanding ? <div style={muted}>Outstanding</div> : null}</td>
                    <td style={{ ...td, textAlign: "right" }}>{write && c.open && o.outstanding && <Btn small onClick={() => setStep({ complied: o })}>Mark complied</Btn>}</td>
                  </tr>))}</tbody>
              </table>)}
          </div>

          <div style={{ fontWeight: 700, margin: "14px 0 6px" }}>History</div>
          <div style={{ ...card, padding: 10, maxHeight: 200, overflowY: "auto" }}>
            {c.events.map((e, i) => <div key={i} style={{ fontSize: 13, padding: "3px 0" }}><span style={muted}>{day(e.occurredAt)}</span> {litEventText(e)}</div>)}
          </div>

          {step === "edit" && <CaseFormDialog creating={false} current={c} onClose={() => setStep(null)} onDone={() => setStep(null)} />}
          {step === "stage" && <StageDialog c={c} id={id} onClose={() => setStep(null)} onDone={changed} />}
          {step === "conclude" && <ConcludeDialog c={c} id={id} onClose={() => setStep(null)} onDone={changed} />}
          {step === "reopen" && <ReopenDialog id={id} onClose={() => setStep(null)} onDone={changed} />}
          {step === "hearing" && <HearingDialog id={id} onClose={() => setStep(null)} onDone={changed} />}
          {step === "order" && <OrderDialog c={c} onClose={() => setStep(null)} onDone={changed} />}
          {step && typeof step === "object" && "postpone" in step && <PostponeDialog h={step.postpone} onClose={() => setStep(null)} onDone={changed} />}
          {step && typeof step === "object" && "held" in step && <HeldDialog h={step.held} onClose={() => setStep(null)} onDone={changed} />}
          {step && typeof step === "object" && "cancel" in step && <CancelDialog h={step.cancel} onClose={() => setStep(null)} onDone={changed} />}
          {step && typeof step === "object" && "complied" in step && <CompliedDialog o={step.complied} onClose={() => setStep(null)} onDone={changed} />}
        </div>)}
    </Dialog>
  )
}

// ---- small dialogs, one per change -------------------------------------------------------------------------------------------

function Action({ title, ok, confirm = "Save", path, body, problem, onClose, onDone, children }: {
  title: string; ok: string; confirm?: string; path: string; body: () => unknown; problem: string | null; onClose: () => void; onDone: (c: LitCase) => void; children: React.ReactNode
}) {
  const [err, setErr] = useState("")
  const save = useMutation({
    mutationFn: async () => unwrap<LitCase>(await apiClient.post(`${URL}/${path}`, body())),
    onSuccess: c => { toast.success(ok); onDone(c) },
    onError: async e => setErr(await errorText(e, "That did not work")),
  })
  return (
    <Dialog title={title} onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : confirm}</Btn></>}>
      {children}
      {problem && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
type Done = (c: LitCase) => void

function StageDialog({ c, id, onClose, onDone }: { c: LitCase; id: string; onClose: () => void; onDone: Done }) {
  const [stage, setStage] = useState(""), [note, setNote] = useState("")
  const problem = litStageProblem(c.stage, stage, note)
  const back = !!stage && LIT_STAGES.findIndex(s => s[0] === stage) < LIT_STAGES.findIndex(s => s[0] === c.stage)
  return (
    <Action title="Change stage" ok="Stage changed" path={`${id}/stage`} body={() => ({ stage, note: note.trim() || null })} problem={stage ? problem : null} onClose={onClose} onDone={onDone}>
      <div style={muted}>Now at: {litStageLabel(c.stage)}. To end the case, use Conclude the case.</div>
      <Pick label="Move to" value={stage} onChange={setStage} blank="Choose a stage" options={LIT_OPEN_STAGES.filter(s => s[0] !== c.stage)} />
      <Input label={back ? "Why is it moving back?" : "Note (optional)"} value={note} onChange={setNote} />
    </Action>
  )
}

function ConcludeDialog({ c, id, onClose, onDone }: { c: LitCase; id: string; onClose: () => void; onDone: Done }) {
  const today = isoDay(new Date())
  const [outcome, setOutcome] = useState(""), [on, setOn] = useState(today), [note, setNote] = useState("")
  const scheduled = c.hearings.filter(h => h.status === "SCHEDULED").length
  return (
    <Action title="Conclude the case" ok="Case concluded" confirm="Conclude" path={`${id}/conclude`} body={() => ({ outcome, concludedOn: on, note: note.trim() })}
      problem={scheduled > 0 ? `${scheduled} ${scheduled === 1 ? "hearing is" : "hearings are"} still scheduled. Mark ${scheduled === 1 ? "it" : "them"} as held or cancel ${scheduled === 1 ? "it" : "them"} first` : litConcludeProblem(outcome, on, note, c.issuedOn, today)}
      onClose={onClose} onDone={onDone}>
      <div style={muted}>A concluded case cannot be changed until it is reopened. The outcome is kept in the history.</div>
      <Pick label="Outcome" value={outcome} onChange={setOutcome} blank="Choose the outcome" options={LIT_OUTCOMES} />
      <Input label="Date the case ended" type="date" value={on} onChange={setOn} max={today} />
      <Input label="How it ended" value={note} onChange={setNote} />
    </Action>
  )
}

function ReopenDialog({ id, onClose, onDone }: { id: string; onClose: () => void; onDone: Done }) {
  const [stage, setStage] = useState("APPEAL"), [reason, setReason] = useState("")
  return (
    <Action title="Reopen the case" ok="Case reopened" confirm="Reopen" path={`${id}/reopen`} body={() => ({ stage, reason: reason.trim() })} problem={litReopenProblem(stage, reason)} onClose={onClose} onDone={onDone}>
      <div style={muted}>The outcome is cleared. The reason is kept in the history.</div>
      <Pick label="Reopen at" value={stage} onChange={setStage} options={LIT_OPEN_STAGES} />
      <Input label="Why is it being reopened?" value={reason} onChange={setReason} />
    </Action>
  )
}

function HearingDialog({ id, onClose, onDone }: { id: string; onClose: () => void; onDone: Done }) {
  const [type, setType] = useState("TRIAL"), [date, setDate] = useState(""), [time, setTime] = useState(""), [room, setRoom] = useState(""), [appearing, setAppearing] = useState("")
  return (
    <Action title="Set a hearing" ok="Hearing set" confirm="Set hearing" path={`${id}/hearings`}
      body={() => ({ hearingType: type, hearingDate: date, hearingTime: time || null, courtRoom: room.trim() || null, appearing: appearing.trim() || null })} problem={litHearingProblem(type, date)} onClose={onClose} onDone={onDone}>
      <div style={muted}>This is also added to the matter's calendar as a court date, so it appears in reminders and the deadlines screens.</div>
      <Pick label="Kind of hearing" value={type} onChange={setType} options={LIT_HEARING_TYPES} />
      <Input label="Date" type="date" value={date} onChange={setDate} />
      <Input label="Time (optional)" type="time" value={time} onChange={setTime} />
      <Input label="Court room (optional)" value={room} onChange={setRoom} />
      <Input label="Who is appearing (optional)" value={appearing} onChange={setAppearing} />
    </Action>
  )
}

function PostponeDialog({ h, onClose, onDone }: { h: LitHearing; onClose: () => void; onDone: Done }) {
  const [date, setDate] = useState(""), [time, setTime] = useState(litTime(h.hearingTime)), [reason, setReason] = useState("")
  return (
    <Action title="Postpone the hearing" ok="Hearing postponed" confirm="Postpone" path={`hearings/${h.id}/postpone`} body={() => ({ hearingDate: date, hearingTime: time || null, reason: reason.trim() })}
      problem={litPostponeProblem(h, date, time, reason)} onClose={onClose} onDone={onDone}>
      <div style={muted}>Now set for {day(h.hearingDate)}. The same calendar entry is moved to the new date.</div>
      <Input label="New date" type="date" value={date} onChange={setDate} />
      <Input label="New time (optional)" type="time" value={time} onChange={setTime} />
      <Input label="Why was it postponed?" value={reason} onChange={setReason} />
    </Action>
  )
}

function HeldDialog({ h, onClose, onDone }: { h: LitHearing; onClose: () => void; onDone: Done }) {
  const today = isoDay(new Date()), [outcome, setOutcome] = useState("")
  return (
    <Action title="Record the hearing as held" ok="Hearing recorded" confirm="Record" path={`hearings/${h.id}/held`} body={() => ({ outcome: outcome.trim() })} problem={litHeldProblem(h.hearingDate, outcome, today)} onClose={onClose} onDone={onDone}>
      <div style={muted}>{litHearingLabel(h.hearingType)} of {day(h.hearingDate)}. The calendar entry is marked done.</div>
      <Input label="What happened?" value={outcome} onChange={setOutcome} />
    </Action>
  )
}

function CancelDialog({ h, onClose, onDone }: { h: LitHearing; onClose: () => void; onDone: Done }) {
  const [reason, setReason] = useState("")
  return (
    <Action title="Cancel the hearing" ok="Hearing cancelled" confirm="Cancel hearing" path={`hearings/${h.id}/cancel`} body={() => ({ reason: reason.trim() })}
      problem={reason.trim().length < 10 ? "Say why it was cancelled, in at least 10 characters" : null} onClose={onClose} onDone={onDone}>
      <div style={muted}>{litHearingLabel(h.hearingType)} of {day(h.hearingDate)}. A cancelled hearing cannot be brought back. Set a new one instead.</div>
      <Input label="Why was it cancelled?" value={reason} onChange={setReason} />
    </Action>
  )
}

function OrderDialog({ c, onClose, onDone }: { c: LitCase; onClose: () => void; onDone: Done }) {
  const today = isoDay(new Date())
  const [date, setDate] = useState(today), [summary, setSummary] = useState(""), [due, setDue] = useState(""), [hearingId, setHearingId] = useState("")
  return (
    <Action title="Record a court order" ok="Order recorded" confirm="Record" path={`${c.id}/orders`} body={() => ({ hearingId: hearingId || null, orderDate: date, summary: summary.trim(), complianceDue: due || null })}
      problem={litOrderProblem(date, summary, due, today)} onClose={onClose} onDone={onDone}>
      <div style={muted}>The order is kept as written. If it has a date to comply by, that is added to the matter's calendar as a filing deadline.</div>
      <Input label="Date of the order" type="date" value={date} onChange={setDate} max={today} />
      <Input label="What the court ordered" value={summary} onChange={setSummary} />
      <Input label="Comply by (optional)" type="date" value={due} onChange={setDue} />
      <Pick label="Made at hearing (optional)" value={hearingId} onChange={setHearingId} blank="Not linked to a hearing" options={c.hearings.map(h => [h.id, `${litHearingLabel(h.hearingType)} · ${day(h.hearingDate)}`] as const)} />
    </Action>
  )
}

function CompliedDialog({ o, onClose, onDone }: { o: LitOrder; onClose: () => void; onDone: Done }) {
  const today = isoDay(new Date()), [on, setOn] = useState(today), [note, setNote] = useState("")
  return (
    <Action title="Mark the order as complied with" ok="Compliance recorded" confirm="Record" path={`orders/${o.id}/complied`} body={() => ({ compliedOn: on, note: note.trim() || null })} problem={litCompliedProblem(o.orderDate, on, today)} onClose={onClose} onDone={onDone}>
      <div style={muted}>{o.summary}. This can only be recorded once. The calendar deadline is marked done.</div>
      <Input label="Date complied with" type="date" value={on} onChange={setOn} max={today} />
      <Input label="Note (optional)" value={note} onChange={setNote} />
    </Action>
  )
}
