// src/pages/customers/CampaignsPanel.tsx
// Campaigns: a named outreach to a list of people, with a journey of timed steps the server runs every morning. The server decides; this sends and shows.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { errorMessage, unwrapList } from "./crmApi"
import {
  ACTIONS, EMPTY_CAMPAIGN, EMPTY_STEP, MAX_CAMPAIGNS, MAX_STEPS, MEMBER_LABEL, MEMBER_STATUSES, NEXT_STATUS, STATUS_LABEL,
  addBody, addProblem, addedText, campaignBody, campaignProblem, canPickUser, peopleLocked, progressText, rate, runSummary, stepsBody, stepsLocked, stepsProblem, textLabel, toCampaignForm, toStepForm,
  type Action, type Campaign, type CampaignForm, type CampaignStatus, type JourneyRun, type JourneyStep, type MemberStatus, type Person, type StepForm,
} from "./campaigns.logic"
import { Loading, ErrorNote } from "./CrmKit"

const field: React.CSSProperties = { padding: "6px 9px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13 }
const ghost: React.CSSProperties = { ...field, cursor: "pointer" }
const small: React.CSSProperties = { ...ghost, padding: "3px 8px", fontSize: 12 }
const box: React.CSSProperties = { display: "grid", gap: 8, maxWidth: 600, marginTop: 8, padding: 10, border: "1px solid var(--hf-border)", borderRadius: 10 }
interface UserRow { id: string; firstName?: string; lastName?: string; email?: string }
interface SegmentRow { id: string; name: string; count: number }
interface Found { id: string; name: string }
const userName = (u: UserRow) => [u.firstName, u.lastName].filter(Boolean).join(" ") || u.email || "User"
const when = (iso: string) => { const d = new Date(iso); return Number.isNaN(d.getTime()) ? iso : d.toLocaleString("en-ZA", { dateStyle: "medium", timeStyle: "short" }) }
const dates = (c: Campaign) => (c.startsOn || c.endsOn ? `${c.startsOn ?? "…"} to ${c.endsOn ?? "…"}` : "No dates set")

export default function CampaignsPanel() {
  const qc = useQueryClient(), canEdit = usePermission("CUSTOMER_UPDATE")
  const [error, setError] = useState<string | null>(null), [note, setNote] = useState<string | null>(null)
  const [edit, setEdit] = useState<string | "new" | null>(null), [form, setForm] = useState<CampaignForm>(EMPTY_CAMPAIGN), [open, setOpen] = useState<string | null>(null)
  const list = useQuery({ queryKey: ["crm", "campaigns"], queryFn: async () => unwrapList<Campaign>(await apiClient.get("/api/v1/crm/campaigns")) })
  const fail = (e: unknown) => { setNote(null); setError(errorMessage(e, "Something went wrong. Try again.")) }
  const refresh = () => qc.invalidateQueries({ queryKey: ["crm", "campaigns"] })
  const save = useMutation({
    mutationFn: () => edit === "new" ? apiClient.post("/api/v1/crm/campaigns", campaignBody(form)) : apiClient.put(`/api/v1/crm/campaigns/${edit}`, campaignBody(form)),
    onSuccess: () => { setError(null); setEdit(null); refresh() }, onError: fail,
  })
  const move = useMutation({ mutationFn: (v: { id: string; to: CampaignStatus }) => apiClient.put(`/api/v1/crm/campaigns/${v.id}/status`, { status: v.to }), onSuccess: () => { setError(null); setNote(null); refresh() }, onError: fail })
  const del = useMutation({ mutationFn: (id: string) => apiClient.delete(`/api/v1/crm/campaigns/${id}`), onSuccess: (_r, id) => { setError(null); if (open === id) setOpen(null); refresh() }, onError: fail })

  const rows = list.data ?? [], problem = edit ? campaignProblem(form) : null, busy = save.isPending || move.isPending || del.isPending
  const current = rows.find(c => c.id === open) ?? null

  return (
    <div style={{ display: "grid", gap: 22, fontSize: 13 }}>
      {error && <div role="alert" style={{ color: "var(--hf-danger-text)" }}>{error}</div>}
      {note && <div role="status" style={{ color: "var(--hf-success-text)" }}>{note}</div>}
      <section aria-label="Campaigns">
        <h3 style={{ margin: "0 0 4px", fontSize: 15 }}>Campaigns</h3>
        <p style={{ margin: "0 0 8px", color: "var(--hf-text-muted)" }}>A campaign is an outreach to a list of people. Its journey runs every morning for each person: a task, a tag, a new owner or a message to the owner, a set number of days after they were added. A person leaves the journey when they respond, convert, or their lead is won or lost.</p>
        {list.isLoading && <Loading />}{list.isError && <ErrorNote>Couldn't load the campaigns.</ErrorNote>}
        {list.data && rows.length === 0 && <p style={{ color: "var(--hf-text-muted)" }}>No campaigns yet.</p>}
        <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gap: 8 }}>
          {rows.map(c => (
            <li key={c.id} aria-label={c.name} style={{ display: "grid", gap: 4, padding: "6px 0", borderBottom: "1px solid var(--hf-border)" }}>
              <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
                <strong style={{ minWidth: 150 }}>{c.name}</strong><span>{STATUS_LABEL[c.status]}</span><span style={{ color: "var(--hf-text-muted)" }}>{dates(c)}</span>
                <span style={{ color: "var(--hf-text-muted)" }}>{c.steps} {c.steps === 1 ? "step" : "steps"}</span>
              </div>
              {c.goal && <div style={{ color: "var(--hf-text-muted)" }}>Goal: {c.goal}</div>}
              <div style={{ display: "flex", gap: 14, flexWrap: "wrap" }}>
                <span>{c.total} {c.total === 1 ? "person" : "people"}</span><span>{c.contacted} contacted</span>
                <span>{c.responded} responded ({rate(c.responseRate)})</span><span>{c.converted} converted ({rate(c.conversionRate)})</span>
              </div>
              <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                <button type="button" style={small} aria-label={`${open === c.id ? "Close" : "Open"} ${c.name}`} onClick={() => { setOpen(open === c.id ? null : c.id); setNote(null); setError(null) }}>{open === c.id ? "Close" : "Open"}</button>
                {canEdit && NEXT_STATUS[c.status].map(n => <button key={n.to} type="button" style={small} aria-label={`${n.label} ${c.name}`} disabled={busy} onClick={() => move.mutate({ id: c.id, to: n.to })}>{n.label}</button>)}
                {canEdit && c.status !== "COMPLETED" && <button type="button" style={small} aria-label={`Edit ${c.name}`} onClick={() => { setForm(toCampaignForm(c)); setEdit(c.id); setError(null) }}>Edit</button>}
                {canEdit && c.status !== "ACTIVE" && <button type="button" style={small} aria-label={`Delete ${c.name}`} disabled={busy} onClick={() => del.mutate(c.id)}>Delete</button>}
              </div>
            </li>))}
        </ul>
        {canEdit && !edit && <button type="button" style={{ ...ghost, marginTop: 8 }} disabled={rows.length >= MAX_CAMPAIGNS} onClick={() => { setForm(EMPTY_CAMPAIGN); setEdit("new"); setError(null) }}>Add campaign</button>}
        {rows.length >= MAX_CAMPAIGNS && <p style={{ color: "var(--hf-text-muted)" }}>You have reached the limit of {MAX_CAMPAIGNS} campaigns.</p>}
        {canEdit && edit && (
          <form aria-label="Campaign" onSubmit={e => { e.preventDefault(); if (!problem) save.mutate() }} style={box}>
            <label>Name <input aria-label="Campaign name" style={{ ...field, width: "100%" }} value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></label>
            <label>Goal <input aria-label="Goal" placeholder="What should this achieve?" style={{ ...field, width: "100%" }} value={form.goal} onChange={e => setForm({ ...form, goal: e.target.value })} /></label>
            <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
              <label>Starts <input aria-label="Starts on" type="date" style={field} value={form.startsOn} onChange={e => setForm({ ...form, startsOn: e.target.value })} /></label>
              <label>Ends <input aria-label="Ends on" type="date" style={field} value={form.endsOn} onChange={e => setForm({ ...form, endsOn: e.target.value })} /></label>
            </div>
            {form.name.trim() && problem && <span style={{ color: "var(--hf-danger-text)" }}>{problem}</span>}
            <div style={{ display: "flex", gap: 6 }}><button type="submit" style={{ ...ghost, fontWeight: 700 }} disabled={!!problem || busy}>{save.isPending ? "Saving…" : "Save campaign"}</button><button type="button" style={ghost} onClick={() => { setEdit(null); setError(null) }}>Cancel</button></div>
          </form>)}
      </section>
      {current && <CampaignDetail key={current.id} campaign={current} canEdit={canEdit} onChanged={refresh} onError={fail} onNote={setNote} />}
    </div>
  )
}

function CampaignDetail({ campaign, canEdit, onChanged, onError, onNote }: { campaign: Campaign; canEdit: boolean; onChanged: () => void; onError: (e: unknown) => void; onNote: (s: string | null) => void }) {
  const qc = useQueryClient(), id = campaign.id
  const [steps, setSteps] = useState<StepForm[] | null>(null)
  const [segmentId, setSegmentId] = useState(""), [term, setTerm] = useState(""), [found, setFound] = useState<Found[] | null>(null), [picked, setPicked] = useState<Found[]>([]), [searchError, setSearchError] = useState<string | null>(null)
  const stepRows = useQuery({ queryKey: ["crm", "campaign-steps", id], queryFn: async () => unwrapList<JourneyStep>(await apiClient.get(`/api/v1/crm/campaigns/${id}/steps`)) })
  const people = useQuery({ queryKey: ["crm", "campaign-people", id], queryFn: async () => unwrapList<Person>(await apiClient.get(`/api/v1/crm/campaigns/${id}/people`)) })
  const runs = useQuery({ queryKey: ["crm", "campaign-runs", id], retry: false, queryFn: async () => unwrapList<JourneyRun>(await apiClient.get(`/api/v1/crm/campaigns/${id}/runs`, { params: { limit: 50 } })) })
  const segments = useQuery({ queryKey: ["crm", "segments"], retry: false, queryFn: async () => unwrapList<SegmentRow>(await apiClient.get("/api/v1/crm/segments")) })
  const users = useQuery({ queryKey: ["crm", "task-users"], retry: false, queryFn: async () => unwrapList<UserRow>(await apiClient.get("/api/v1/identity/users", { params: { size: 200 } })) })
  const reload = () => { for (const k of ["campaign-steps", "campaign-people", "campaign-runs"]) qc.invalidateQueries({ queryKey: ["crm", k, id] }); onChanged() }

  const saveSteps = useMutation({ mutationFn: () => apiClient.put(`/api/v1/crm/campaigns/${id}/steps`, stepsBody(steps ?? [])), onSuccess: () => { setSteps(null); onNote("Steps saved."); reload() }, onError: onError })
  const add = useMutation({
    mutationFn: async () => unwrapOne<{ added: number; alreadyIn: number; skipped: number }>(await apiClient.post(`/api/v1/crm/campaigns/${id}/people`, addBody(segmentId, picked.map(p => p.id)))),
    onSuccess: r => { setSegmentId(""); setPicked([]); setFound(null); setTerm(""); onNote(r ? addedText(r) : "People added."); reload() }, onError: onError,
  })
  const setStatus = useMutation({ mutationFn: (v: { m: string; status: MemberStatus }) => apiClient.put(`/api/v1/crm/campaigns/${id}/people/${v.m}/status`, { status: v.status }), onSuccess: reload, onError: onError })
  const remove = useMutation({ mutationFn: (m: string) => apiClient.delete(`/api/v1/crm/campaigns/${id}/people/${m}`), onSuccess: reload, onError: onError })

  const search = async () => {
    if (term.trim().length < 2) { setSearchError("Type at least 2 letters of the customer's name."); return }
    setSearchError(null)
    try { setFound(unwrapList<Found>(await apiClient.get("/api/v1/crm/customers", { params: { search: term.trim(), size: 8 } }))) } catch { setSearchError("Customer search failed. Try again.") }
  }
  const locked = stepsLocked(campaign.status), sp = steps ? stepsProblem(steps) : null, ap = addProblem(segmentId, picked.map(p => p.id))
  const names = new Map((users.data ?? []).map(u => [u.id, userName(u)]))
  const shown = stepRows.data ?? [], nSteps = shown.length, rs = runSummary(runs.data ?? [])
  const setStep = (i: number, patch: Partial<StepForm>) => setSteps((steps ?? []).map((s, j) => (j === i ? { ...s, ...patch } : s)))
  const sentence = (s: JourneyStep) => s.action === "ASSIGN_OWNER" && s.user && names.get(s.user) ? s.description.replace(/make .* the owner$/, `make ${names.get(s.user)} the owner`) : s.description

  return (
    <div style={{ display: "grid", gap: 22 }} aria-label={`${campaign.name} details`}>
      <section aria-label="Journey">
        <h3 style={{ margin: "0 0 4px", fontSize: 15 }}>Journey: {campaign.name}</h3>
        {locked && <p style={{ margin: "0 0 6px", color: "var(--hf-text-muted)" }}>{campaign.status === "ACTIVE" ? "Pause the campaign to change its steps." : "A finished campaign cannot be changed."}</p>}
        {stepRows.isLoading && <Loading />}{stepRows.isError && <ErrorNote>Couldn't load the steps.</ErrorNote>}
        {stepRows.data && nSteps === 0 && !steps && <p style={{ color: "var(--hf-text-muted)" }}>No steps yet. Without steps the campaign only keeps a list.</p>}
        {!steps && <ol style={{ margin: 0, paddingLeft: 20, display: "grid", gap: 4 }}>{shown.map(s => <li key={s.position}>{sentence(s)}</li>)}</ol>}
        {canEdit && !locked && !steps && stepRows.data && <button type="button" style={{ ...ghost, marginTop: 8 }} onClick={() => setSteps(shown.map(toStepForm))}>Edit steps</button>}
        {canEdit && steps && (
          <form aria-label="Steps" onSubmit={e => { e.preventDefault(); if (!sp) saveSteps.mutate() }} style={box}>
            {steps.map((s, i) => (
              <div key={i} role="group" aria-label={`Step ${i + 1}`} style={{ display: "flex", gap: 6, flexWrap: "wrap", alignItems: "center" }}>
                <span>After</span><input aria-label={`Step ${i + 1} day`} inputMode="numeric" style={{ ...field, width: 60 }} value={s.day} onChange={e => setStep(i, { day: e.target.value })} /><span>days</span>
                <select aria-label={`Step ${i + 1} action`} style={field} value={s.action} onChange={e => setStep(i, { action: e.target.value as Action | "", text: "", user: "" })}>
                  <option value="">Choose what it does…</option>{ACTIONS.map(a => <option key={a.id} value={a.id}>{a.label}</option>)}</select>
                {textLabel(s.action) && <input aria-label={`Step ${i + 1} ${textLabel(s.action)}`} placeholder={textLabel(s.action) ?? ""} style={{ ...field, minWidth: 180 }} value={s.text} onChange={e => setStep(i, { text: e.target.value })} />}
                {canPickUser(s.action) && <select aria-label={`Step ${i + 1} person`} style={field} value={s.user} onChange={e => setStep(i, { user: e.target.value })}>
                  <option value="">{s.action === "ASSIGN_OWNER" ? "New owner…" : "The lead's owner"}</option>{(users.data ?? []).map(u => <option key={u.id} value={u.id}>{userName(u)}</option>)}</select>}
                <button type="button" style={small} aria-label={`Remove step ${i + 1}`} onClick={() => setSteps(steps.filter((_, j) => j !== i))}>Remove</button>
              </div>))}
            <div><button type="button" style={small} disabled={steps.length >= MAX_STEPS} onClick={() => setSteps([...steps, { ...EMPTY_STEP }])}>Add step</button></div>
            {sp && <span style={{ color: "var(--hf-danger-text)" }}>{sp}</span>}
            <div style={{ display: "flex", gap: 6 }}><button type="submit" style={{ ...ghost, fontWeight: 700 }} disabled={!!sp || saveSteps.isPending}>{saveSteps.isPending ? "Saving…" : "Save steps"}</button><button type="button" style={ghost} onClick={() => setSteps(null)}>Cancel</button></div>
          </form>)}
      </section>

      <section aria-label="People">
        <h3 style={{ margin: "0 0 4px", fontSize: 15 }}>People</h3>
        {people.isLoading && <Loading />}{people.isError && <ErrorNote>Couldn't load the people.</ErrorNote>}
        {people.data && people.data.length === 0 && <p style={{ color: "var(--hf-text-muted)" }}>Nobody has been added yet.</p>}
        <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gap: 4 }}>
          {(people.data ?? []).map(p => (
            <li key={p.id} aria-label={p.name} style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
              <strong style={{ minWidth: 150 }}>{p.name}</strong><span style={{ minWidth: 90 }}>{MEMBER_LABEL[p.status]}</span><span style={{ color: "var(--hf-text-muted)" }}>{progressText(p, nSteps)}</span>
              {canEdit && !peopleLocked(campaign.status) && <span style={{ display: "flex", gap: 6 }}>
                <select aria-label={`Status of ${p.name}`} style={{ ...field, padding: "3px 6px", fontSize: 12 }} value={p.status} onChange={e => setStatus.mutate({ m: p.id, status: e.target.value as MemberStatus })}>
                  {MEMBER_STATUSES.map(s => <option key={s} value={s}>{MEMBER_LABEL[s]}</option>)}</select>
                <button type="button" style={small} aria-label={`Remove ${p.name}`} disabled={remove.isPending} onClick={() => remove.mutate(p.id)}>Remove</button></span>}
            </li>))}
        </ul>
        {canEdit && !peopleLocked(campaign.status) && (
          <form aria-label="Add people" onSubmit={e => { e.preventDefault(); if (!ap) add.mutate() }} style={box}>
            <label>Everyone in a segment <select aria-label="Segment" style={field} value={segmentId} onChange={e => setSegmentId(e.target.value)}>
              <option value="">None</option>{(segments.data ?? []).map(s => <option key={s.id} value={s.id}>{s.name} ({s.count})</option>)}</select></label>
            <div style={{ display: "flex", gap: 6, flexWrap: "wrap", alignItems: "center" }}>
              <input aria-label="Search customers" placeholder="Or find people by name" style={{ ...field, minWidth: 220 }} value={term} onChange={e => setTerm(e.target.value)} />
              <button type="button" style={small} onClick={search}>Search</button>
            </div>
            {searchError && <span role="alert" style={{ color: "var(--hf-danger-text)" }}>{searchError}</span>}
            {found && found.length === 0 && <span style={{ color: "var(--hf-text-muted)" }}>Nobody found.</span>}
            {found && found.length > 0 && <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>{found.filter(f => !picked.some(p => p.id === f.id)).map(f =>
              <button key={f.id} type="button" style={small} aria-label={`Pick ${f.name}`} onClick={() => setPicked([...picked, f])}>+ {f.name}</button>)}</div>}
            {picked.length > 0 && <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>{picked.map(p =>
              <button key={p.id} type="button" style={small} aria-label={`Unpick ${p.name}`} onClick={() => setPicked(picked.filter(x => x.id !== p.id))}>{p.name} ✕</button>)}</div>}
            <div><button type="submit" style={{ ...ghost, fontWeight: 700 }} disabled={!!ap || add.isPending}>{add.isPending ? "Adding…" : "Add to campaign"}</button></div>
          </form>)}
      </section>

      <section aria-label="Journey log">
        <h3 style={{ margin: "0 0 4px", fontSize: 15 }}>What ran recently</h3>
        {runs.isLoading && <Loading />}{runs.isError && <ErrorNote>Couldn't load the log.</ErrorNote>}
        {runs.data && runs.data.length === 0 && <p style={{ color: "var(--hf-text-muted)" }}>No steps have run yet.</p>}
        {runs.data && runs.data.length > 0 && <p style={{ margin: "0 0 6px", color: "var(--hf-text-muted)" }}>{rs.done} done{rs.failed ? `, ${rs.failed} failed` : ""} in the last {runs.data.length}.</p>}
        <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "grid", gap: 4 }}>
          {(runs.data ?? []).map(r => (
            <li key={r.id} aria-label={`Step ${r.step} for ${r.customerName}`} style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
              <span style={{ color: "var(--hf-text-muted)", minWidth: 140 }}>{when(r.ranAt)}</span><strong>Step {r.step}</strong><span>{r.customerName}</span>
              <span style={{ color: r.outcome === "DONE" ? "var(--hf-success-text)" : "var(--hf-danger-text)" }}>{r.outcome === "DONE" ? "Done" : "Failed"}{r.detail ? `: ${r.detail}` : ""}</span>
            </li>))}
        </ul>
      </section>
    </div>
  )
}

const unwrapOne = <T,>(r: { data?: unknown }): T | null => { const d = r?.data as { data?: T } | undefined; return d?.data ?? null }
