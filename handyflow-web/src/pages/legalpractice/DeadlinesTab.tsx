// Deadlines: a calculator, the rules (how each kind of period is counted) and the court calendar (days that are not working days).
// Whoever keeps the rules decides every date they produce, so changing them needs its own permission.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, Empty, ErrorLine, Input, Pick, Pill, SectionTitle, card, day, errorText, fail, muted, td, th } from "./kit"
import { BASE } from "./lookups"
import { ComputeDialog } from "./DeadlinePanel"
import { unwrap, useRules } from "./deadlineApi"
import {
  CALENDAR_KINDS, COURTS, DATE_TYPES, DEADLINE_DIRECTIONS, DEADLINE_ROLLOVERS, DEADLINE_UNITS, KIND_TONE, calendarProblem, calendarYears, describeRule, emptyRule, labelOf, ruleProblem, ruleRequest, ruleToForm,
  type CalendarDay, type DeadlineRule, type RuleForm,
} from "./legal.logic"

type Section = "rules" | "calendar"

export default function DeadlinesTab() {
  const canKeep = usePermissions(["LEGALPRACTICE_DEADLINE_RULES", "LEGALPRACTICE_ADMIN"])
  const [section, setSection] = useState<Section>("rules"), [calculating, setCalculating] = useState(false)
  const sections: [Section, string][] = [["rules", "Rules"], ["calendar", "Court calendar"]]
  return (
    <div>
      <SectionTitle right={<Btn kind="primary" onClick={() => setCalculating(true)}>Deadline calculator</Btn>}>Deadlines</SectionTitle>
      <div style={{ ...muted, marginBottom: 10 }}>A rule says how long a period is and how it is counted. Pick a rule and the date it starts from, and the date is worked out with every skipped weekend, holiday and recess day listed. On a matter, use Work out a deadline on the Key dates tab to put the result in the diary.</div>
      <div role="tablist" style={{ display: "flex", gap: 6, marginBottom: 12 }}>
        {sections.map(([id, label]) => (
          <button key={id} role="tab" aria-selected={section === id} onClick={() => setSection(id)}
            style={{ padding: "6px 14px", borderRadius: 8, border: `1px solid ${section === id ? "var(--hf-primary)" : "var(--hf-border)"}`, background: section === id ? "var(--hf-primary)" : "var(--hf-surface)", color: section === id ? "var(--hf-text-on-solid)" : "var(--hf-text)", fontWeight: 600, fontSize: 13, cursor: "pointer" }}>{label}</button>))}
      </div>
      {section === "rules" ? <RulesSection canKeep={canKeep} /> : <CalendarSection canKeep={canKeep} />}
      {calculating && <ComputeDialog onClose={() => setCalculating(false)} />}
    </div>
  )
}

function RulesSection({ canKeep }: { canKeep: boolean }) {
  const qc = useQueryClient()
  const rules = useRules(true)
  const [editing, setEditing] = useState<DeadlineRule | "new" | null>(null)
  const refresh = () => qc.invalidateQueries({ queryKey: ["lp-deadline-rules"] })
  const toggle = useMutation({
    mutationFn: (r: DeadlineRule) => apiClient.post(`${BASE}/deadlines/rules/${r.id}/${r.active ? "deactivate" : "activate"}`),
    onSuccess: refresh, onError: e => { void fail(e, "That did not work") },
  })
  const install = useMutation({
    mutationFn: async () => unwrap<{ added: number; alreadyThere: number }>(await apiClient.post(`${BASE}/deadlines/rules/install-templates`)),
    onSuccess: r => { toast.success(r.added ? `${r.added} template ${r.added === 1 ? "rule" : "rules"} added` : "All the template rules are already there"); refresh() },
    onError: e => { void fail(e, "Could not install the templates") },
  })
  const rows = rules.data ?? []
  return (
    <div>
      {canKeep && <div style={{ display: "flex", gap: 8, justifyContent: "flex-end", marginBottom: 8 }}>
        <Btn disabled={install.isPending} onClick={() => install.mutate()}>{install.isPending ? "Installing…" : "Install template rules"}</Btn>
        <Btn onClick={() => setEditing("new")}>Add a rule</Btn>
      </div>}
      <div style={{ ...muted, marginBottom: 8 }}>The template rules are starting points, not legal advice. A lawyer at the firm must confirm each one against the current rules before relying on it. Installing only adds rules you do not have yet; it never changes one you edited.</div>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {rules.isError ? <Empty>Could not load the rules.</Empty> : rules.isLoading ? <Empty>Loading…</Empty> : rows.length === 0 ? <Empty>No rules yet.{canKeep ? " Install the template rules, or add your own." : ""}</Empty> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Rule</th><th style={th}>Period</th><th style={th}>Creates</th><th style={th} /></tr></thead>
            <tbody>{rows.map(r => (
              <tr key={r.id} style={r.active ? undefined : { opacity: 0.6 }}>
                <td style={td}><b>{r.name}</b> {!r.active && <Pill tone="neutral">Off</Pill>}<div style={muted}>{r.code}{r.court ? ` · ${labelOf(COURTS, r.court)}` : ""}</div>{r.sourceReference && <div style={muted}>{r.sourceReference}</div>}</td>
                <td style={td}>{describeRule(r)}<div style={muted}>From: {r.triggerLabel}</div></td>
                <td style={td}>{labelOf(DATE_TYPES, r.keyDateType)}</td>
                <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}>{canKeep && <><Btn small onClick={() => setEditing(r)}>Edit</Btn>{" "}<Btn small disabled={toggle.isPending} onClick={() => toggle.mutate(r)}>{r.active ? "Switch off" : "Switch on"}</Btn></>}</td>
              </tr>))}</tbody>
          </table>)}
      </div>
      {editing && <RuleDialog item={editing === "new" ? null : editing} onClose={() => setEditing(null)} onSaved={refresh} />}
    </div>
  )
}

function RuleDialog({ item, onClose, onSaved }: { item: DeadlineRule | null; onClose: () => void; onSaved: () => void }) {
  const [f, setF] = useState<RuleForm>(() => item ? ruleToForm(item) : emptyRule()), [err, setErr] = useState("")
  const set = (k: keyof RuleForm) => (v: string) => setF(s => ({ ...s, [k]: v }))
  const problem = ruleProblem(f)
  const save = useMutation({
    mutationFn: () => item ? apiClient.put(`${BASE}/deadlines/rules/${item.id}`, ruleRequest(f)) : apiClient.post(`${BASE}/deadlines/rules`, ruleRequest(f)),
    onSuccess: () => { toast.success(item ? "Rule saved" : "Rule added"); onSaved(); onClose() },
    onError: async e => setErr(await errorText(e, "Could not save the rule")),
  })
  const countsDays = f.unit === "COURT_DAYS" || f.unit === "CALENDAR_DAYS"
  return (
    <Dialog title={item ? `Edit rule: ${item.name}` : "Add a rule"} onClose={onClose} width={680}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Save"}</Btn></>}>
      {item && <div style={{ ...muted, marginBottom: 8 }}>Dates already worked out keep the rule as it was. Only new dates, and corrections that ask for the current rule, use these changes.</div>}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 2fr", gap: "0 12px" }}>
        <Input label="Code" value={f.code} onChange={set("code")} hint="Short and unique, for example HC_PLEA" />
        <Input label="Name" value={f.name} onChange={set("name")} />
      </div>
      <Input label="The date it starts from" value={f.triggerLabel} onChange={set("triggerLabel")} hint="In words the person will recognise, for example: Date the summons was served" />
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 2fr", gap: "0 12px" }}>
        <Input label="Length" type="number" min={1} value={f.amount} onChange={set("amount")} />
        <Pick label="Counted in" value={f.unit} onChange={set("unit")} options={DEADLINE_UNITS} />
        <Pick label="Direction" value={f.direction} onChange={set("direction")} options={DEADLINE_DIRECTIONS} />
      </div>
      <Pick label="If the last day is not a working day" value={f.rollover} onChange={set("rollover")} options={DEADLINE_ROLLOVERS} />
      {f.unit === "COURT_DAYS" && f.rollover !== "NONE" && <div style={{ ...muted, marginTop: -6, marginBottom: 10 }}>Court days always end on a court day, so this setting makes no difference here.</div>}
      {countsDays
        ? <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 12px" }}>
            <Input label="Recess starts (MM-DD, optional)" value={f.recessStart} onChange={set("recessStart")} placeholder="12-16" />
            <Input label="Recess ends (MM-DD)" value={f.recessEnd} onChange={set("recessEnd")} placeholder="01-15" hint="Days in this window are not counted, every year." />
          </div>
        : <div style={{ ...muted, marginBottom: 10 }}>Months and years are not shortened by a recess window.</div>}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 12px" }}>
        <Pick label="Creates this kind of key date" value={f.keyDateType} onChange={set("keyDateType")} options={DATE_TYPES} />
        <Pick label="Court" value={f.court} onChange={set("court")} options={COURTS} blank="Not specific" />
      </div>
      <Input label="Where the rule comes from" value={f.sourceReference} onChange={set("sourceReference")} hint="For example: Uniform Rule 22(1)" />
      <Input label="Notes" value={f.description} onChange={set("description")} />
      {problem && (f.code.trim() || f.name.trim()) && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

function CalendarSection({ canKeep }: { canKeep: boolean }) {
  const qc = useQueryClient()
  const years = calendarYears(), [year, setYear] = useState(years[1]), [adding, setAdding] = useState(false)
  const days = useQuery({ queryKey: ["lp-deadline-calendar", year], queryFn: async () => unwrap<CalendarDay[]>(await apiClient.get(`${BASE}/deadlines/calendar`, { params: { year } })) ?? [] })
  const refresh = () => qc.invalidateQueries({ queryKey: ["lp-deadline-calendar"] })
  const remove = useMutation({ mutationFn: (id: string) => apiClient.delete(`${BASE}/deadlines/calendar/${id}`), onSuccess: () => { toast.success("Day removed"); refresh() }, onError: e => { void fail(e, "Could not remove the day") } })
  const rows = days.data ?? []
  return (
    <div>
      <div style={{ display: "flex", gap: 12, alignItems: "flex-end", justifyContent: "space-between", flexWrap: "wrap" }}>
        <div style={{ width: 160 }}><Pick label="Year" value={year} onChange={setYear} options={years.map(y => [y, y] as const)} /></div>
        {canKeep && <div style={{ marginBottom: 12 }}><Btn onClick={() => setAdding(true)}>Add a day</Btn></div>}
      </div>
      <div style={{ ...muted, marginBottom: 8 }}>The public holidays set by law (including the Monday after a holiday that falls on a Sunday) are worked out automatically. Add only a day proclaimed for one year, such as an election day, or a day the courts were closed. Dates already worked out are not changed when a day is added or removed; correct the ones that matter.</div>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {days.isError ? <Empty>Could not load the calendar.</Empty> : days.isLoading ? <Empty>Loading…</Empty> : rows.length === 0 ? <Empty>No days to show.</Empty> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Date</th><th style={th}>Day</th><th style={th}>Kind</th><th style={th}>Added by</th><th style={th} /></tr></thead>
            <tbody>{rows.map(d => (
              <tr key={`${d.date}-${d.kind}-${d.id ?? "law"}`}>
                <td style={td}><b>{day(d.date)}</b><div style={muted}>{new Date(d.date + "T00:00:00").toLocaleDateString("en-ZA", { weekday: "long" })}</div></td>
                <td style={td}>{d.name}</td>
                <td style={td}><Pill tone={KIND_TONE[d.kind] ?? "neutral"}>{labelOf(CALENDAR_KINDS, d.kind)}</Pill></td>
                <td style={td}>{d.source === "STATUTORY" ? "The law" : "The firm"}</td>
                <td style={{ ...td, textAlign: "right" }}>{canKeep && d.id && <Btn small disabled={remove.isPending} onClick={() => remove.mutate(d.id as string)}>Remove</Btn>}</td>
              </tr>))}</tbody>
          </table>)}
      </div>
      {adding && <AddDayDialog onClose={() => setAdding(false)} onSaved={refresh} />}
    </div>
  )
}

function AddDayDialog({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const [date, setDate] = useState(""), [kind, setKind] = useState("PUBLIC_HOLIDAY"), [reason, setReason] = useState(""), [err, setErr] = useState("")
  const problem = calendarProblem(date, reason)
  const save = useMutation({
    mutationFn: () => apiClient.post(`${BASE}/deadlines/calendar`, { date, kind, reason: reason.trim() }),
    onSuccess: () => { toast.success("Day added"); onSaved(); onClose() },
    onError: async e => setErr(await errorText(e, "Could not add the day")),
  })
  return (
    <Dialog title="Add a day to the court calendar" onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Add"}</Btn></>}>
      <Input label="Date" type="date" value={date} onChange={setDate} />
      <Pick label="Kind" value={kind} onChange={setKind} options={CALENDAR_KINDS} />
      <div style={{ ...muted, marginTop: -6, marginBottom: 10 }}>{kind === "PUBLIC_HOLIDAY" ? "A proclaimed public holiday is skipped by court days and by the Sunday-and-holiday rule." : "A court closure is skipped by court days and the court-day rule, but not by the Sunday-and-holiday rule."}</div>
      <Input label="Reason" value={reason} onChange={setReason} hint="For example: Local government elections, proclaimed by the President" />
      {problem && reason.trim() && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
