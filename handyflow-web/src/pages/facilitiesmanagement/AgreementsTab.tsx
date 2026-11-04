// Service agreements: what a client pays (retainer or time and materials), which sites and kinds of work are covered,
// and the response and fix times promised for each priority.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, Empty, ErrorLine, Input, Pager, Pick, Pill, SectionTitle, card, day, errorText, fail, money, muted, pageOf, td, th } from "./kit"
import { BASE, useLookups, type Lookups } from "./lookups"
import { AGREEMENT_CATEGORIES, BILLING_TYPES, CLOCKS, PRIORITIES, agreementBody, agreementProblem, agreementState, coverageText, emptyAgreement, emptyTarget, labelOf, minutesText, type AgreementForm } from "./fm.logic"

interface Target { priority: string; responseMinutes: number; resolutionMinutes: number; clock: string }
export interface Agreement { id: string; clientId: string; name: string | null; billingType: string; monthlyFee: number | null; hourlyRate: number | null; partsMarkupPercent: number | null; startDate: string; endDate: string | null; status: string; siteIds: string[]; categories: string[]; slaTargets: Target[] }

export default function AgreementsTab() {
  const qc = useQueryClient(), lookups = useLookups()
  const canWrite = usePermission("FACILITIESMANAGEMENT_MANAGE") || usePermission("FACILITIESMANAGEMENT_ADMIN") || usePermission("FACILITIESMANAGEMENT_CONTRACT_MANAGE")
  const [clientId, setClientId] = useState(""), [page, setPage] = useState(0), [editing, setEditing] = useState<Agreement | "new" | null>(null), [ending, setEnding] = useState<Agreement | null>(null), [checking, setChecking] = useState(false)
  const { data, isLoading, isError } = useQuery({ enabled: !!clientId, queryKey: ["fm-agreements", clientId, page], queryFn: async () => pageOf<Agreement>(await apiClient.get(`${BASE}/service-agreements`, { params: { clientId, page, size: 25 } })) })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["fm-agreements"] }); qc.invalidateQueries({ queryKey: ["fm-agreements-active"] }) }
  const end = useMutation({
    mutationFn: (a: Agreement) => apiClient.post(`${BASE}/service-agreements/${a.id}/end`),
    onSuccess: () => { toast.success("Agreement ended"); setEnding(null); refresh() }, onError: e => { setEnding(null); void fail(e, "Could not end the agreement") },
  })
  const rows = data?.content ?? [], now = new Date()
  return (
    <div>
      <SectionTitle right={<>{clientId && <Btn onClick={() => setChecking(true)}>Which terms apply?</Btn>}{canWrite && <Btn kind="primary" onClick={() => setEditing("new")}>New agreement</Btn>}</>}>Service agreements</SectionTitle>
      <div style={{ maxWidth: 360, marginBottom: 12 }}><Pick label="Client" value={clientId} onChange={v => { setClientId(v); setPage(0) }} blank="Choose a client…" options={lookups.clients.filter(c => c.status === "ACTIVE").map(c => [c.id, c.tradingName] as const)} /></div>
      {!clientId ? <div style={card}><Empty>Choose a client to see their agreements.</Empty></div> : (
        <div style={{ ...card, padding: 0, overflowX: "auto" }}>
          {isError ? <Empty>Could not load the agreements. Please try again.</Empty> : isLoading ? <Empty>Loading…</Empty> : rows.length === 0 ? <Empty>This client has no agreements yet.</Empty> : (
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr><th style={th}>Agreement</th><th style={th}>Charges</th><th style={th}>Covers</th><th style={th}>Promised times</th><th style={th}>Status</th><th /></tr></thead>
              <tbody>{rows.map(a => {
                const st = agreementState(a.status, a.endDate, now)
                return (
                  <tr key={a.id} style={{ opacity: a.status === "ENDED" ? 0.6 : 1 }}>
                    <td style={td}><b>{a.name || "Untitled"}</b><div style={muted}>{day(a.startDate)} → {a.endDate ? day(a.endDate) : "no end date"}</div></td>
                    <td style={td}>{labelOf(BILLING_TYPES, a.billingType)}<div style={muted}>{[a.billingType === "RETAINER" && a.monthlyFee != null ? `${money(a.monthlyFee)} / month` : null, a.hourlyRate != null ? `${money(a.hourlyRate)} / hour` : null, a.partsMarkupPercent != null ? `${a.partsMarkupPercent}% on parts` : null].filter(Boolean).join(" · ") || "No rates set"}</div></td>
                    <td style={{ ...td, maxWidth: 240 }}><span style={muted}>{coverageText(a.siteIds, a.categories, lookups.siteName)}</span></td>
                    <td style={td}>{a.slaTargets.length === 0 ? <span style={muted}>None set</span> : a.slaTargets.map(x => <div key={x.priority} style={{ fontSize: 12 }}><b>{labelOf(PRIORITIES, x.priority)}</b> · reply {minutesText(x.responseMinutes)}, fix {minutesText(x.resolutionMinutes)}</div>)}</td>
                    <td style={td}><Pill tone={st.tone}>{st.text}</Pill></td>
                    <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}>{canWrite && a.status === "ACTIVE" && <><Btn small onClick={() => setEditing(a)}>Edit</Btn>{" "}<Btn small onClick={() => setEnding(a)}>End</Btn></>}</td>
                  </tr>)
              })}</tbody>
            </table>)}
        </div>)}
      <Pager page={page} pages={data?.totalPages ?? 0} onPage={setPage} />
      {editing && <AgreementDialog agreement={editing === "new" ? null : editing} clientId={clientId} lookups={lookups} onClose={() => setEditing(null)} onSaved={(cid) => { setClientId(cid); refresh() }} />}
      {ending && <Dialog title="End this agreement?" onClose={() => setEnding(null)} footer={<><Btn onClick={() => setEnding(null)}>Keep it</Btn><Btn kind="danger" disabled={end.isPending} onClick={() => end.mutate(ending)}>{end.isPending ? "Ending…" : "End agreement"}</Btn></>}>
        <div style={{ lineHeight: 1.5 }}>“{ending.name || "Untitled"}” stops applying to new work. Invoices already raised are not affected. This cannot be undone.</div></Dialog>}
      {checking && <ResolveDialog clientId={clientId} lookups={lookups} onClose={() => setChecking(false)} />}
    </div>
  )
}

function AgreementDialog({ agreement, clientId, lookups, onClose, onSaved }: { agreement: Agreement | null; clientId: string; lookups: Lookups; onClose: () => void; onSaved: (clientId: string) => void }) {
  const [f, setF] = useState<AgreementForm>(() => agreement ? {
    clientId: agreement.clientId, name: agreement.name ?? "", billingType: agreement.billingType, monthlyFee: agreement.monthlyFee?.toString() ?? "", hourlyRate: agreement.hourlyRate?.toString() ?? "",
    partsMarkupPercent: agreement.partsMarkupPercent?.toString() ?? "", startDate: agreement.startDate, endDate: agreement.endDate ?? "", siteIds: agreement.siteIds, categories: agreement.categories,
    targets: agreement.slaTargets.map(x => ({ priority: x.priority, responseMinutes: String(x.responseMinutes), resolutionMinutes: String(x.resolutionMinutes), clock: x.clock })),
  } : { ...emptyAgreement(), clientId })
  const [err, setErr] = useState("")
  const creating = !agreement, problem = agreementProblem(f, creating)
  const set = <K extends keyof AgreementForm>(k: K) => (v: AgreementForm[K]) => setF(s => ({ ...s, [k]: v }))
  const toggle = (k: "siteIds" | "categories", v: string) => setF(s => ({ ...s, [k]: s[k].includes(v) ? s[k].filter(x => x !== v) : [...s[k], v] }))
  const sites = lookups.sites.filter(s => s.clientId === f.clientId && s.status === "ACTIVE")
  const patchTarget = (i: number, p: Partial<AgreementForm["targets"][number]>) => setF(s => ({ ...s, targets: s.targets.map((x, j) => j === i ? { ...x, ...p } : x) }))
  const save = useMutation({
    mutationFn: () => creating ? apiClient.post(`${BASE}/service-agreements`, agreementBody(f, true)) : apiClient.put(`${BASE}/service-agreements/${agreement.id}`, agreementBody(f, false)),
    onSuccess: () => { toast.success(creating ? "Agreement created" : "Agreement saved"); onSaved(f.clientId); onClose() },
    onError: async e => setErr(await errorText(e, "Could not save the agreement")),
  })
  return (
    <Dialog title={creating ? "New service agreement" : `Edit ${agreement.name || "agreement"}`} onClose={onClose} width={680}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Save"}</Btn></>}>
      {creating && <Pick label="Client" value={f.clientId} onChange={v => setF(s => ({ ...s, clientId: v, siteIds: [] }))} blank="Choose a client…" options={lookups.clients.filter(c => c.status === "ACTIVE").map(c => [c.id, c.tradingName] as const)} />}
      <Input label="Name" value={f.name} onChange={set("name")} maxLength={200} hint="For example “Head office – maintenance”" autoFocus />
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: "0 12px" }}>
        {creating ? <Pick label="How it is billed" value={f.billingType} onChange={set("billingType")} options={BILLING_TYPES} /> : <Input label="How it is billed" value={labelOf(BILLING_TYPES, f.billingType)} onChange={() => undefined} readOnly />}
        <Input label="Monthly fee (R)" type="number" min="0" step="0.01" value={f.monthlyFee} onChange={set("monthlyFee")} hint={f.billingType === "RETAINER" ? "Required for a retainer" : "Only used by retainers"} />
        <Input label="Hourly rate (R)" type="number" min="0" step="0.01" value={f.hourlyRate} onChange={set("hourlyRate")} />
        <Input label="Mark-up on parts (%)" type="number" min="0" max="1000" step="0.1" value={f.partsMarkupPercent} onChange={set("partsMarkupPercent")} />
        {creating ? <Input label="Starts" type="date" value={f.startDate} onChange={set("startDate")} /> : <Input label="Starts" value={f.startDate} onChange={() => undefined} readOnly />}
        <Input label="Ends" type="date" value={f.endDate} onChange={set("endDate")} hint="Leave empty for no end date" />
      </div>
      <Checks title="Sites covered" note="Tick none to cover every site of this client." options={sites.map(s => [s.id, s.name] as const)} picked={f.siteIds} onToggle={v => toggle("siteIds", v)} empty={f.clientId ? "This client has no active sites." : "Choose a client first."} />
      <Checks title="Kinds of work covered" note="Tick none to cover all kinds of work." options={AGREEMENT_CATEGORIES} picked={f.categories} onToggle={v => toggle("categories", v)} />
      <div style={{ margin: "14px 0 6px", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <div style={{ fontSize: 13, fontWeight: 700 }}>Promised times</div>
        <Btn small disabled={f.targets.length >= PRIORITIES.length} onClick={() => setF(s => ({ ...s, targets: [...s.targets, emptyTarget(s.targets.map(x => x.priority))] }))}>Add a priority</Btn>
      </div>
      {f.targets.length === 0 ? <div style={muted}>No promised times. Work orders will carry no response or fix deadline.</div> : f.targets.map((x, i) => (
        <div key={i} style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))", gap: "0 10px", alignItems: "end", borderTop: "1px solid var(--hf-border-subtle, #e5e5e5)", paddingTop: 8, marginTop: 6 }}>
          <Pick label="Priority" value={x.priority} onChange={v => patchTarget(i, { priority: v })} options={PRIORITIES} />
          <Input label="Reply within (min)" type="number" min="1" value={x.responseMinutes} onChange={v => patchTarget(i, { responseMinutes: v })} hint={minutesText(Number(x.responseMinutes) || null)} />
          <Input label="Fixed within (min)" type="number" min="1" value={x.resolutionMinutes} onChange={v => patchTarget(i, { resolutionMinutes: v })} hint={minutesText(Number(x.resolutionMinutes) || null)} />
          <Pick label="Clock runs" value={x.clock} onChange={v => patchTarget(i, { clock: v })} options={CLOCKS} />
          <div style={{ marginBottom: 12 }}><Btn small onClick={() => setF(s => ({ ...s, targets: s.targets.filter((_, j) => j !== i) }))}>Remove this priority</Btn></div>
        </div>))}
      {!creating && <div style={{ ...muted, marginTop: 8 }}>The server refuses an agreement that overlaps another active one for the same sites and kinds of work.</div>}
      {problem && (f.name.trim() || f.clientId) && <div style={{ ...muted, marginTop: 8 }}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

function Checks({ title, note, options, picked, onToggle, empty }: { title: string; note: string; options: readonly (readonly [string, string])[]; picked: string[]; onToggle: (v: string) => void; empty?: string }) {
  return (
    <div style={{ margin: "10px 0" }}>
      <div style={{ fontSize: 13, fontWeight: 700 }}>{title}</div><div style={{ ...muted, marginBottom: 4 }}>{note}</div>
      {options.length === 0 ? <div style={muted}>{empty}</div> : <div style={{ display: "flex", flexWrap: "wrap", gap: "4px 16px" }}>
        {options.map(([v, l]) => <label key={v} style={{ display: "flex", gap: 6, alignItems: "center", fontSize: 13 }}><input type="checkbox" checked={picked.includes(v)} onChange={() => onToggle(v)} />{l}</label>)}
      </div>}
    </div>
  )
}

interface Resolved { agreementFound: boolean; agreementName: string | null; billingType: string | null; coverage: string | null; labourRate: number | null; partsMarkupPercent: number | null; slaTargets: Target[] }
/** Asks the server which agreement would apply to a job, so what people expect and what the server does can be compared. */
function ResolveDialog({ clientId, lookups, onClose }: { clientId: string; lookups: Lookups; onClose: () => void }) {
  const [siteId, setSiteId] = useState(""), [category, setCategory] = useState("CORRECTIVE"), [date, setDate] = useState(new Date().toISOString().slice(0, 10))
  const q = useQuery({ enabled: !!siteId && !!date, queryKey: ["fm-agreement-resolve", clientId, siteId, category, date], queryFn: async () => (await apiClient.get<Resolved>(`${BASE}/service-agreements/resolve`, { params: { clientId, siteId, category, date } })).data })
  const r = q.data
  return (
    <Dialog title="Which terms apply?" onClose={onClose} width={560} footer={<Btn onClick={onClose}>Close</Btn>}>
      <div style={{ ...muted, marginBottom: 10 }}>Pick a site, kind of work and date to see which agreement the system would use for that job.</div>
      <Pick label="Site" value={siteId} onChange={setSiteId} blank="Choose a site…" options={lookups.sites.filter(s => s.clientId === clientId).map(s => [s.id, s.name] as const)} />
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 12px" }}>
        <Pick label="Kind of work" value={category} onChange={setCategory} options={AGREEMENT_CATEGORIES} />
        <Input label="On" type="date" value={date} onChange={setDate} />
      </div>
      {q.isError && <ErrorLine text="Could not check that." />}
      {q.isFetching && <div style={muted}>Checking…</div>}
      {r && !q.isFetching && (!r.agreementFound ? <div style={{ ...card, marginTop: 8 }}>No agreement covers that job. It would be charged without agreed rates or promised times.</div> : (
        <div style={{ ...card, marginTop: 8 }}>
          <b>{r.agreementName || "Untitled"}</b> · {labelOf(BILLING_TYPES, r.billingType)}
          <div style={muted}>{[r.labourRate != null ? `${money(r.labourRate)} / hour` : null, r.partsMarkupPercent != null ? `${r.partsMarkupPercent}% on parts` : null].filter(Boolean).join(" · ") || "No rates set"}</div>
          {r.slaTargets.map(x => <div key={x.priority} style={{ fontSize: 12, marginTop: 4 }}><b>{labelOf(PRIORITIES, x.priority)}</b> · reply {minutesText(x.responseMinutes)}, fix {minutesText(x.resolutionMinutes)} ({labelOf(CLOCKS, x.clock).toLowerCase()})</div>)}
        </div>))}
    </Dialog>
  )
}
