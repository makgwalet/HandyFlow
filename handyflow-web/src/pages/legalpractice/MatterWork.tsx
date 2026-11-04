// What is recorded against one matter: time, expenses (disbursements) and key dates, plus raising an invoice from the unbilled work.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, Empty, ErrorLine, Input, Pick, Pill, card, day, errorText, fail, money, muted, pageOf, td, th } from "./kit"
import { BASE, type Lookups } from "./lookups"
import type { Matter } from "./MattersTab"
import {
  DATE_TYPES, KEYDATE_STATUS, WORK_STATUS, billable, canChangeWork, disbProblem, disbRequest, emptyDisb, emptyKeyDate, emptyTime, generateProblem, keyDateNote, keyDateProblem, keyDateRequest,
  labelOf, timeProblem, timeRequest, type DisbForm, type KeyDateForm, type TimeForm,
} from "./legal.logic"

interface Time { id: string; attorneyId: string; entryDate: string | null; hours: number; hourlyRate: number; lineTotal: number; description: string; billable: boolean; status: string; invoiceId: string | null }
interface Disb { id: string; disbursementDate: string | null; description: string; amount: number; paidFromTrust: boolean; status: string; invoiceId: string | null }
interface KeyDate { id: string; dateType: string; dueDate: string; description: string; acknowledged: boolean; status: string; notes: string | null }
type Tab = "time" | "expenses" | "dates"

export default function MatterWork({ matter, lookups, canWrite, canInvoice }: { matter: Matter; lookups: Lookups; canWrite: boolean; canInvoice: boolean }) {
  const [tab, setTab] = useState<Tab>("time"), [invoicing, setInvoicing] = useState(false)
  const tabs: [Tab, string][] = [["time", "Time"], ["expenses", "Expenses"], ["dates", "Key dates"]]
  return (
    <div>
      <div style={{ display: "flex", gap: 6, alignItems: "center", marginBottom: 12, flexWrap: "wrap" }}>
        <div role="tablist" style={{ display: "flex", gap: 6 }}>
          {tabs.map(([id, label]) => (
            <button key={id} role="tab" aria-selected={tab === id} onClick={() => setTab(id)}
              style={{ padding: "6px 14px", borderRadius: 8, border: `1px solid ${tab === id ? "var(--hf-primary)" : "var(--hf-border)"}`, background: tab === id ? "var(--hf-primary)" : "var(--hf-surface)", color: tab === id ? "var(--hf-text-on-solid)" : "var(--hf-text)", fontWeight: 600, fontSize: 13, cursor: "pointer" }}>{label}</button>))}
        </div>
        {canInvoice && matter.status !== "ARCHIVED" && <div style={{ marginLeft: "auto" }}><Btn kind="primary" onClick={() => setInvoicing(true)}>Raise invoice</Btn></div>}
      </div>
      {tab === "time" && <TimePanel matter={matter} lookups={lookups} canWrite={canWrite} />}
      {tab === "expenses" && <ExpensePanel matter={matter} canWrite={canWrite} />}
      {tab === "dates" && <KeyDatePanel matter={matter} canWrite={canWrite} />}
      {invoicing && <InvoiceDialog matter={matter} onClose={() => setInvoicing(false)} />}
    </div>
  )
}

const WorkPill = ({ status }: { status: string }) => { const s = WORK_STATUS[status] ?? { label: status, tone: "neutral" as const }; return <Pill tone={s.tone}>{s.label}</Pill> }

function TimePanel({ matter, lookups, canWrite }: { matter: Matter; lookups: Lookups; canWrite: boolean }) {
  const qc = useQueryClient()
  const [page, setPage] = useState(0), [editing, setEditing] = useState<Time | "new" | null>(null), [writeOff, setWriteOff] = useState<Time | null>(null)
  const { data, isLoading, isError } = useQuery({ queryKey: ["lp-time", matter.id, page], queryFn: async () => pageOf<Time>(await apiClient.get(`${BASE}/matters/${matter.id}/time-entries`, { params: { page, size: 25 } })) })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["lp-time"] }); qc.invalidateQueries({ queryKey: ["lp-unbilled-time"] }) }
  const wo = useMutation({ mutationFn: (e: Time) => apiClient.post(`${BASE}/matters/${matter.id}/time-entries/${e.id}/write-off`), onSuccess: () => { toast.success("Time written off"); refresh(); setWriteOff(null) }, onError: e => { void fail(e, "Could not write it off"); setWriteOff(null) } })
  const rows = data?.content ?? []
  const unbilled = rows.filter(r => r.status === "UNBILLED").reduce((s, r) => s + r.lineTotal, 0)
  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8, gap: 8, flexWrap: "wrap" }}>
        <div style={muted}>{rows.length > 0 && <>Unbilled{(data?.totalPages ?? 0) > 1 ? " on this page" : ""}: <b>{money(unbilled)}</b></>}</div>
        {canWrite && <Btn onClick={() => setEditing("new")}>Log time</Btn>}
      </div>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {isError ? <Empty>Could not load the time entries.</Empty> : isLoading ? <Empty>Loading…</Empty> : rows.length === 0 ? <Empty>No time logged yet.</Empty> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Date</th><th style={th}>Work</th><th style={th}>By</th><th style={{ ...th, textAlign: "right" }}>Hours</th><th style={{ ...th, textAlign: "right" }}>Amount</th><th style={th}>Status</th><th /></tr></thead>
            <tbody>{rows.map(e => (
              <tr key={e.id}>
                <td style={td}>{day(e.entryDate)}</td><td style={td}>{e.description}</td><td style={td}>{lookups.attorneyName(e.attorneyId)}</td>
                <td style={{ ...td, textAlign: "right" }}>{e.hours}</td>
                <td style={{ ...td, textAlign: "right" }}>{e.billable ? money(e.lineTotal) : "No charge"}<div style={muted}>{money(e.hourlyRate)}/h</div></td>
                <td style={td}><WorkPill status={e.status} /></td>
                <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}>{canWrite && canChangeWork(e.status) && <><Btn small onClick={() => setEditing(e)}>Edit</Btn>{" "}<Btn small onClick={() => setWriteOff(e)}>Write off</Btn></>}</td>
              </tr>))}</tbody>
          </table>)}
      </div>
      {(data?.totalPages ?? 0) > 1 && <div style={{ display: "flex", justifyContent: "space-between", marginTop: 10 }}><Btn small disabled={page === 0} onClick={() => setPage(page - 1)}>← Newer</Btn><span style={muted}>Page {page + 1} of {data?.totalPages}</span><Btn small disabled={page + 1 >= (data?.totalPages ?? 1)} onClick={() => setPage(page + 1)}>Older →</Btn></div>}
      {editing && <TimeDialog matter={matter} entry={editing === "new" ? null : editing} lookups={lookups} onClose={() => setEditing(null)} onSaved={refresh} />}
      {writeOff && (
        <Dialog title="Write off this time?" onClose={() => setWriteOff(null)} footer={<><Btn onClick={() => setWriteOff(null)}>Keep it</Btn><Btn kind="danger" disabled={wo.isPending} onClick={() => wo.mutate(writeOff)}>{wo.isPending ? "Working…" : "Write off"}</Btn></>}>
          <div style={{ lineHeight: 1.5, fontSize: 13 }}>{writeOff.hours} h ({money(writeOff.lineTotal)}) will no longer be billed. This cannot be undone.</div>
        </Dialog>)}
    </div>
  )
}

function TimeDialog({ matter, entry, lookups, onClose, onSaved }: { matter: Matter; entry: Time | null; lookups: Lookups; onClose: () => void; onSaved: () => void }) {
  const editing = !!entry
  const [f, setF] = useState<TimeForm>(() => entry ? { attorneyId: entry.attorneyId, entryDate: entry.entryDate ?? "", hours: String(entry.hours), hourlyRate: String(entry.hourlyRate), description: entry.description, billable: entry.billable } : emptyTime(matter.attorneyId))
  const [err, setErr] = useState("")
  const set = (k: keyof TimeForm) => (v: string) => setF(s => ({ ...s, [k]: v }))
  const rate = lookups.attorney(f.attorneyId)?.hourlyRate
  const problem = timeProblem(f, editing, rate)
  const save = useMutation({
    mutationFn: () => entry ? apiClient.put(`${BASE}/matters/${matter.id}/time-entries/${entry.id}`, timeRequest(f, true)) : apiClient.post(`${BASE}/matters/${matter.id}/time-entries`, timeRequest(f, false)),
    onSuccess: () => { toast.success(entry ? "Time saved" : "Time logged"); onSaved(); onClose() },
    onError: async e => setErr(await errorText(e, "Could not save the time")),
  })
  return (
    <Dialog title={entry ? "Edit time" : "Log time"} onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Save"}</Btn></>}>
      {!editing && <Pick label="Done by" value={f.attorneyId} onChange={set("attorneyId")} options={lookups.attorneys.filter(a => a.active).map(a => [a.id, a.name] as const)} blank="Choose an attorney" />}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: "0 12px" }}>
        <Input label="Date" type="date" value={f.entryDate} onChange={set("entryDate")} hint="Empty = today" />
        <Input label="Hours" type="number" min="0" step="0.05" value={f.hours} onChange={set("hours")} />
        <Input label="Rate (R/h)" type="number" min="0" step="0.01" value={f.hourlyRate} onChange={set("hourlyRate")} hint={!editing && rate != null ? `Empty = ${money(rate)}` : undefined} />
      </div>
      <Input label="What was done" value={f.description} onChange={set("description")} />
      {!editing && <label style={{ display: "flex", gap: 6, alignItems: "center", fontSize: 13 }}><input type="checkbox" checked={f.billable} onChange={e => setF(s => ({ ...s, billable: e.target.checked }))} /> Billable to the client</label>}
      {problem && f.description.trim() && <div style={{ ...muted, marginTop: 6 }}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

function ExpensePanel({ matter, canWrite }: { matter: Matter; canWrite: boolean }) {
  const qc = useQueryClient()
  const [page, setPage] = useState(0), [editing, setEditing] = useState<Disb | "new" | null>(null), [writeOff, setWriteOff] = useState<Disb | null>(null)
  const { data, isLoading, isError } = useQuery({ queryKey: ["lp-disb", matter.id, page], queryFn: async () => pageOf<Disb>(await apiClient.get(`${BASE}/matters/${matter.id}/disbursements`, { params: { page, size: 25 } })) })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["lp-disb"] }); qc.invalidateQueries({ queryKey: ["lp-unbilled-disb"] }) }
  const wo = useMutation({ mutationFn: (d: Disb) => apiClient.post(`${BASE}/matters/${matter.id}/disbursements/${d.id}/write-off`), onSuccess: () => { toast.success("Expense written off"); refresh(); setWriteOff(null) }, onError: e => { void fail(e, "Could not write it off"); setWriteOff(null) } })
  const rows = data?.content ?? []
  return (
    <div>
      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 8 }}>{canWrite && <Btn onClick={() => setEditing("new")}>Add expense</Btn>}</div>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {isError ? <Empty>Could not load the expenses.</Empty> : isLoading ? <Empty>Loading…</Empty> : rows.length === 0 ? <Empty>No expenses recorded.</Empty> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Date</th><th style={th}>Expense</th><th style={{ ...th, textAlign: "right" }}>Amount</th><th style={th}>Paid from</th><th style={th}>Status</th><th /></tr></thead>
            <tbody>{rows.map(d => (
              <tr key={d.id}>
                <td style={td}>{day(d.disbursementDate)}</td><td style={td}>{d.description}</td><td style={{ ...td, textAlign: "right" }}>{money(d.amount)}</td>
                <td style={td}>{d.paidFromTrust ? "Trust account" : "Firm"}</td><td style={td}><WorkPill status={d.status} /></td>
                <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}>{canWrite && canChangeWork(d.status) && <><Btn small onClick={() => setEditing(d)}>Edit</Btn>{" "}<Btn small onClick={() => setWriteOff(d)}>Write off</Btn></>}</td>
              </tr>))}</tbody>
          </table>)}
      </div>
      {(data?.totalPages ?? 0) > 1 && <div style={{ display: "flex", justifyContent: "space-between", marginTop: 10 }}><Btn small disabled={page === 0} onClick={() => setPage(page - 1)}>← Newer</Btn><span style={muted}>Page {page + 1} of {data?.totalPages}</span><Btn small disabled={page + 1 >= (data?.totalPages ?? 1)} onClick={() => setPage(page + 1)}>Older →</Btn></div>}
      {editing && <DisbDialog matter={matter} item={editing === "new" ? null : editing} onClose={() => setEditing(null)} onSaved={refresh} />}
      {writeOff && (
        <Dialog title="Write off this expense?" onClose={() => setWriteOff(null)} footer={<><Btn onClick={() => setWriteOff(null)}>Keep it</Btn><Btn kind="danger" disabled={wo.isPending} onClick={() => wo.mutate(writeOff)}>{wo.isPending ? "Working…" : "Write off"}</Btn></>}>
          <div style={{ lineHeight: 1.5, fontSize: 13 }}>{writeOff.description} ({money(writeOff.amount)}) will not be billed to the client. This cannot be undone.</div>
        </Dialog>)}
    </div>
  )
}

function DisbDialog({ matter, item, onClose, onSaved }: { matter: Matter; item: Disb | null; onClose: () => void; onSaved: () => void }) {
  const [f, setF] = useState<DisbForm>(() => item ? { disbursementDate: item.disbursementDate ?? "", description: item.description, amount: String(item.amount), paidFromTrust: item.paidFromTrust } : emptyDisb())
  const [err, setErr] = useState("")
  const set = (k: keyof DisbForm) => (v: string) => setF(s => ({ ...s, [k]: v }))
  const problem = disbProblem(f)
  const save = useMutation({
    mutationFn: () => item ? apiClient.put(`${BASE}/matters/${matter.id}/disbursements/${item.id}`, disbRequest(f, true)) : apiClient.post(`${BASE}/matters/${matter.id}/disbursements`, disbRequest(f, false)),
    onSuccess: () => { toast.success(item ? "Expense saved" : "Expense added"); onSaved(); onClose() },
    onError: async e => setErr(await errorText(e, "Could not save the expense")),
  })
  return (
    <Dialog title={item ? "Edit expense" : "Add an expense"} onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Save"}</Btn></>}>
      <Input label="What for" value={f.description} onChange={set("description")} autoFocus />
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 12px" }}>
        <Input label="Amount (R)" type="number" min="0" step="0.01" value={f.amount} onChange={set("amount")} />
        <Input label="Date" type="date" value={f.disbursementDate} onChange={set("disbursementDate")} hint="Empty = today" />
      </div>
      {!item && <label style={{ display: "flex", gap: 6, alignItems: "center", fontSize: 13 }}><input type="checkbox" checked={f.paidFromTrust} onChange={e => setF(s => ({ ...s, paidFromTrust: e.target.checked }))} /> Already paid from the client's trust money</label>}
      {problem && f.description.trim() && <div style={{ ...muted, marginTop: 6 }}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

function KeyDatePanel({ matter, canWrite }: { matter: Matter; canWrite: boolean }) {
  const qc = useQueryClient()
  const [editing, setEditing] = useState<KeyDate | "new" | null>(null)
  const { data = [], isLoading, isError } = useQuery({ queryKey: ["lp-keydates", matter.id], queryFn: async () => { const r: any = await apiClient.get(`${BASE}/matters/${matter.id}/key-dates`); const d = r?.data?.data ?? r?.data; return (Array.isArray(d) ? d : []) as KeyDate[] } })
  const refresh = () => qc.invalidateQueries({ queryKey: ["lp-keydates"] })
  const act = useMutation({
    mutationFn: ({ k, path }: { k: KeyDate; path: "complete" | "mark-missed" | "acknowledge" }) => apiClient.post(`${BASE}/matters/${matter.id}/key-dates/${k.id}/${path}`),
    onSuccess: refresh, onError: e => { void fail(e, "That did not work") },
  })
  const rows = [...data].sort((a, b) => (a.status === "PENDING" ? 0 : 1) - (b.status === "PENDING" ? 0 : 1) || a.dueDate.localeCompare(b.dueDate))
  return (
    <div>
      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 8 }}>{canWrite && <Btn onClick={() => setEditing("new")}>Add key date</Btn>}</div>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {isError ? <Empty>Could not load the key dates.</Empty> : isLoading ? <Empty>Loading…</Empty> : rows.length === 0 ? <Empty>No key dates. Add court dates and filing or prescription deadlines so they are not missed.</Empty> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Due</th><th style={th}>What</th><th style={th}>Status</th><th /></tr></thead>
            <tbody>{rows.map(k => {
              const st = KEYDATE_STATUS[k.status] ?? { label: k.status, tone: "neutral" as const }, note = keyDateNote(k.status, k.dueDate)
              return (
                <tr key={k.id}>
                  <td style={td}><b>{day(k.dueDate)}</b>{note && <div style={{ marginTop: 3 }}><Pill tone={note.tone}>{note.text}</Pill></div>}</td>
                  <td style={td}>{k.description}<div style={muted}>{labelOf(DATE_TYPES, k.dateType)}{k.notes ? ` · ${k.notes}` : ""}</div></td>
                  <td style={td}><Pill tone={st.tone}>{st.label}</Pill>{k.status === "PENDING" && k.acknowledged && <div style={muted}>Seen</div>}</td>
                  <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}>{canWrite && k.status === "PENDING" && <>
                    {!k.acknowledged && <><Btn small disabled={act.isPending} onClick={() => act.mutate({ k, path: "acknowledge" })}>Seen</Btn>{" "}</>}
                    <Btn small disabled={act.isPending} onClick={() => act.mutate({ k, path: "complete" })}>Done</Btn>{" "}
                    <Btn small disabled={act.isPending} onClick={() => act.mutate({ k, path: "mark-missed" })}>Missed</Btn>{" "}
                    <Btn small onClick={() => setEditing(k)}>Edit</Btn></>}</td>
                </tr>)
            })}</tbody>
          </table>)}
      </div>
      {editing && <KeyDateDialog matter={matter} item={editing === "new" ? null : editing} onClose={() => setEditing(null)} onSaved={refresh} />}
    </div>
  )
}

function KeyDateDialog({ matter, item, onClose, onSaved }: { matter: Matter; item: KeyDate | null; onClose: () => void; onSaved: () => void }) {
  const [f, setF] = useState<KeyDateForm>(() => item ? { dateType: item.dateType, dueDate: item.dueDate, description: item.description, notes: item.notes ?? "" } : emptyKeyDate())
  const [err, setErr] = useState("")
  const set = (k: keyof KeyDateForm) => (v: string) => setF(s => ({ ...s, [k]: v }))
  const problem = keyDateProblem(f)
  const save = useMutation({
    mutationFn: () => item ? apiClient.put(`${BASE}/matters/${matter.id}/key-dates/${item.id}`, keyDateRequest(f)) : apiClient.post(`${BASE}/matters/${matter.id}/key-dates`, keyDateRequest(f)),
    onSuccess: () => { toast.success(item ? "Key date saved" : "Key date added"); onSaved(); onClose() },
    onError: async e => setErr(await errorText(e, "Could not save the key date")),
  })
  return (
    <Dialog title={item ? "Edit key date" : "Add a key date"} onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Save"}</Btn></>}>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 12px" }}>
        <Pick label="Kind" value={f.dateType} onChange={set("dateType")} options={DATE_TYPES} />
        <Input label="Date" type="date" value={f.dueDate} onChange={set("dueDate")} />
      </div>
      <Input label="What is due" value={f.description} onChange={set("description")} autoFocus />
      <Input label="Notes" value={f.notes} onChange={set("notes")} />
      {problem && f.description.trim() && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

/** Raise an invoice from the matter's unbilled time and expenses, or a fixed fee. */
function InvoiceDialog({ matter, onClose }: { matter: Matter; onClose: () => void }) {
  const qc = useQueryClient()
  const time = useQuery({ queryKey: ["lp-unbilled-time", matter.id], queryFn: async () => { const r: any = await apiClient.get(`${BASE}/matters/${matter.id}/time-entries/unbilled`); const d = r?.data?.data ?? r?.data; return (Array.isArray(d) ? d : []) as Time[] } })
  const disb = useQuery({ queryKey: ["lp-unbilled-disb", matter.id], queryFn: async () => { const r: any = await apiClient.get(`${BASE}/matters/${matter.id}/disbursements/unbilled`); const d = r?.data?.data ?? r?.data; return (Array.isArray(d) ? d : []) as Disb[] } })
  const [pickT, setPickT] = useState<Set<string>>(new Set()), [pickD, setPickD] = useState<Set<string>>(new Set()), [seeded, setSeeded] = useState(false)
  const [fee, setFee] = useState(matter.billingType === "FIXED_FEE" && matter.fixedFeeAmount != null ? String(matter.fixedFeeAmount) : "")
  const [due, setDue] = useState(""), [desc, setDesc] = useState(""), [notes, setNotes] = useState(""), [err, setErr] = useState("")
  // Hourly matters start with everything unbilled ticked; fixed fee matters start with nothing so the fee is what is billed.
  if (!seeded && time.data && disb.data) { setSeeded(true); if (matter.billingType !== "FIXED_FEE") { setPickT(new Set(time.data.filter(t => t.billable).map(t => t.id))); setPickD(new Set(disb.data.map(d => d.id))) } }
  const tItems = (time.data ?? []).filter(t => pickT.has(t.id)).map(t => ({ id: t.id, amount: t.lineTotal }))
  const dItems = (disb.data ?? []).filter(d => pickD.has(d.id)).map(d => ({ id: d.id, amount: d.amount }))
  const form = { dueDate: due, description: desc, notes, fixedFeeAmount: fee }
  const problem = generateProblem(tItems, dItems, form)
  const subtotal = billable(tItems, dItems, fee)
  const toggle = (set: Set<string>, id: string, setter: (s: Set<string>) => void) => { const n = new Set(set); n.has(id) ? n.delete(id) : n.add(id); setter(n) }
  const gen = useMutation({
    mutationFn: () => apiClient.post(`${BASE}/invoices/generate`, {
      clientId: matter.clientId, matterId: matter.id, timeEntryIds: [...pickT], disbursementIds: [...pickD],
      fixedFeeAmount: tItems.length + dItems.length === 0 ? Number(fee) : null, dueDate: due || null, description: desc.trim() || null, notes: notes.trim() || null,
    }),
    onSuccess: () => { toast.success("Invoice raised as a draft"); for (const k of ["lp-invoices", "lp-unbilled-time", "lp-unbilled-disb", "lp-time", "lp-disb", "lp-client-invoices"]) qc.invalidateQueries({ queryKey: [k] }); onClose() },
    onError: async e => setErr(await errorText(e, "Could not raise the invoice")),
  })
  const loading = time.isLoading || disb.isLoading
  const none = !loading && (time.data ?? []).length + (disb.data ?? []).length === 0
  return (
    <Dialog title={`Raise invoice for ${matter.matterNumber}`} onClose={onClose} width={680}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || gen.isPending} onClick={() => { setErr(""); gen.mutate() }}>{gen.isPending ? "Raising…" : "Raise invoice"}</Btn></>}>
      {(time.isError || disb.isError) && <ErrorLine text="Could not load the unbilled work." />}
      {loading ? <div style={muted}>Loading…</div> : none ? <div style={{ ...muted, marginBottom: 10 }}>Nothing unbilled on this matter.{matter.billingType === "FIXED_FEE" ? " You can still bill the fixed fee below." : ""}</div> : (<>
        {(time.data ?? []).length > 0 && <>
          <div style={{ fontSize: 13, fontWeight: 700, margin: "0 0 4px" }}>Time</div>
          {(time.data ?? []).map(t => (
            <label key={t.id} style={{ display: "flex", gap: 8, padding: "4px 0", fontSize: 13, alignItems: "baseline" }}>
              <input type="checkbox" checked={pickT.has(t.id)} onChange={() => toggle(pickT, t.id, setPickT)} />
              <span style={{ flex: 1 }}>{day(t.entryDate)} · {t.description}<span style={muted}> · {t.hours} h</span></span><b>{t.billable ? money(t.lineTotal) : "No charge"}</b>
            </label>))}</>}
        {(disb.data ?? []).length > 0 && <>
          <div style={{ fontSize: 13, fontWeight: 700, margin: "10px 0 4px" }}>Expenses</div>
          {(disb.data ?? []).map(d => (
            <label key={d.id} style={{ display: "flex", gap: 8, padding: "4px 0", fontSize: 13, alignItems: "baseline" }}>
              <input type="checkbox" checked={pickD.has(d.id)} onChange={() => toggle(pickD, d.id, setPickD)} />
              <span style={{ flex: 1 }}>{day(d.disbursementDate)} · {d.description}</span><b>{money(d.amount)}</b>
            </label>))}</>}
      </>)}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 12px", marginTop: 12 }}>
        {(matter.billingType === "FIXED_FEE" || none) && <Input label="Fixed fee (R)" type="number" min="0" step="0.01" value={fee} onChange={setFee} hint="Billed when no time or expenses are ticked" />}
        <Input label="Due date" type="date" value={due} onChange={setDue} hint="Optional" />
      </div>
      <Input label="Description" value={desc} onChange={setDesc} />
      <Input label="Notes" value={notes} onChange={setNotes} />
      <div style={{ fontSize: 13, marginTop: 4 }}>Before VAT: <b>{money(subtotal)}</b></div>
      {problem && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
