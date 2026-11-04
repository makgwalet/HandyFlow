// Matters: the list, and (once one is opened) its time, expenses, key dates and invoicing.
import { useMemo, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, Empty, ErrorLine, Input, Pager, Pick, Pill, SectionTitle, card, day, errorText, fail, inp, money, muted, pageOf, td, th } from "./kit"
import EvidencePanel from "./EvidencePanel"
import { BASE, useLookups, type Lookups } from "./lookups"
import { BILLING_TYPES, MATTER_STATUS, MATTER_TYPES, canLogWork, emptyMatter, labelOf, matterActions, matterProblem, matterRequest, type MatterForm } from "./legal.logic"
import MatterWork from "./MatterWork"

export interface Matter { id: string; clientId: string; attorneyId: string; matterNumber: string; matterType: string; matterName: string; description: string | null; billingType: string; fixedFeeAmount: number | null; status: string; openedDate: string | null; closedDate: string | null; notes: string | null }

export default function MattersTab() {
  const canWrite = usePermission("LEGALPRACTICE_MANAGE") || usePermission("LEGALPRACTICE_ADMIN")
  const lookups = useLookups()
  const [clientId, setClientId] = useState(""), [status, setStatus] = useState(""), [search, setSearch] = useState(""), [page, setPage] = useState(0)
  const [openId, setOpenId] = useState<string | null>(null), [editing, setEditing] = useState<Matter | "new" | null>(null)
  const { data, isLoading, isError } = useQuery({ queryKey: ["lp-matters", clientId, page], queryFn: async () => pageOf<Matter>(await apiClient.get(`${BASE}/matters`, { params: { clientId: clientId || undefined, page, size: 25 } })) })
  const rows = useMemo(() => {
    const q = search.trim().toLowerCase()
    return (data?.content ?? []).filter(m => (!status || m.status === status) && (!q || m.matterNumber.toLowerCase().includes(q) || m.matterName.toLowerCase().includes(q)))
  }, [data, status, search])
  const open = (data?.content ?? []).find(m => m.id === openId) ?? null

  if (open) return <MatterView matter={open} lookups={lookups} canWrite={canWrite} onBack={() => setOpenId(null)} onEdit={() => setEditing(open)} editing={editing} onCloseEdit={() => setEditing(null)} />
  return (
    <div>
      <SectionTitle right={canWrite && <Btn kind="primary" onClick={() => setEditing("new")}>New matter</Btn>}>Matters</SectionTitle>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 12 }}>
        <input aria-label="Search matters" placeholder="Search number or name" value={search} onChange={e => setSearch(e.target.value)} style={{ ...inp, width: 220 }} />
        <select aria-label="Client" value={clientId} onChange={e => { setClientId(e.target.value); setPage(0) }} style={{ ...inp, width: 220 }}>
          <option value="">All clients</option>{lookups.clients.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
        <select aria-label="Status" value={status} onChange={e => setStatus(e.target.value)} style={{ ...inp, width: 140 }}>
          <option value="">Any status</option>{Object.entries(MATTER_STATUS).map(([v, s]) => <option key={v} value={v}>{s.label}</option>)}
        </select>
      </div>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {isError ? <Empty>Could not load the matters. Please try again.</Empty> : isLoading ? <Empty>Loading…</Empty> : rows.length === 0 ? <Empty>No matters match.</Empty> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Matter</th><th style={th}>Client</th><th style={th}>Attorney</th><th style={th}>Billing</th><th style={th}>Opened</th><th style={th}>Status</th></tr></thead>
            <tbody>{rows.map(m => {
              const st = MATTER_STATUS[m.status] ?? { label: m.status, tone: "neutral" as const }
              return (
                <tr key={m.id} onClick={() => setOpenId(m.id)} style={{ cursor: "pointer" }}>
                  <td style={td}><b>{m.matterNumber}</b><div style={muted}>{m.matterName}</div></td>
                  <td style={td}>{lookups.clientName(m.clientId)}</td>
                  <td style={td}>{lookups.attorneyName(m.attorneyId)}</td>
                  <td style={td}>{labelOf(BILLING_TYPES, m.billingType)}{m.billingType === "FIXED_FEE" && m.fixedFeeAmount != null && <div style={muted}>{money(m.fixedFeeAmount)}</div>}</td>
                  <td style={td}>{day(m.openedDate)}</td>
                  <td style={td}><Pill tone={st.tone}>{st.label}</Pill></td>
                </tr>)
            })}</tbody>
          </table>)}
      </div>
      {(status || search) && (data?.totalPages ?? 0) > 1 && <div style={{ ...muted, marginTop: 8 }}>Searching and the status filter apply to this page only.</div>}
      <Pager page={page} pages={data?.totalPages ?? 0} onPage={setPage} />
      {editing === "new" && <MatterDialog matter={null} lookups={lookups} onClose={() => setEditing(null)} />}
    </div>
  )
}

function MatterView({ matter: m, lookups, canWrite, onBack, onEdit, editing, onCloseEdit }: { matter: Matter; lookups: Lookups; canWrite: boolean; onBack: () => void; onEdit: () => void; editing: Matter | "new" | null; onCloseEdit: () => void }) {
  const qc = useQueryClient()
  const [confirm, setConfirm] = useState<"close" | "archive" | null>(null)
  const actions = canWrite ? matterActions(m.status) : []
  const st = MATTER_STATUS[m.status] ?? { label: m.status, tone: "neutral" as const }
  const move = useMutation({
    mutationFn: (path: "hold" | "reopen" | "close" | "archive") => apiClient.post(`${BASE}/matters/${m.id}/${path}`, path === "close" ? {} : undefined),
    onSuccess: (_r, path) => { toast.success({ hold: "Matter put on hold", reopen: "Matter reopened", close: "Matter closed", archive: "Matter archived" }[path]); qc.invalidateQueries({ queryKey: ["lp-matters"] }); setConfirm(null) },
    onError: e => { void fail(e, "That did not work"); setConfirm(null) },
  })
  return (
    <div>
      <div style={{ marginBottom: 8 }}><Btn small kind="ghost" onClick={onBack}>← All matters</Btn></div>
      <SectionTitle right={<>
        {actions.includes("edit") && <Btn onClick={onEdit}>Edit</Btn>}
        {actions.includes("hold") && <Btn disabled={move.isPending} onClick={() => move.mutate("hold")}>Put on hold</Btn>}
        {actions.includes("reopen") && <Btn disabled={move.isPending} onClick={() => move.mutate("reopen")}>Reopen</Btn>}
        {actions.includes("close") && <Btn onClick={() => setConfirm("close")}>Close</Btn>}
        {actions.includes("archive") && <Btn onClick={() => setConfirm("archive")}>Archive</Btn>}
      </>}>{m.matterNumber} · {m.matterName} <Pill tone={st.tone}>{st.label}</Pill></SectionTitle>
      <div style={{ ...card, display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 14, marginBottom: 14 }}>
        <Fact k="Client" v={lookups.clientName(m.clientId)} />
        <Fact k="Responsible attorney" v={lookups.attorneyName(m.attorneyId)} />
        <Fact k="Type" v={labelOf(MATTER_TYPES, m.matterType)} />
        <Fact k="Billing" v={`${labelOf(BILLING_TYPES, m.billingType)}${m.billingType === "FIXED_FEE" && m.fixedFeeAmount != null ? ` · ${money(m.fixedFeeAmount)}` : ""}`} />
        <Fact k="Opened" v={day(m.openedDate)} />
        {m.closedDate && <Fact k="Closed" v={day(m.closedDate)} />}
        {m.description && <Fact k="Description" v={m.description} />}
        {m.notes && <Fact k="Notes" v={m.notes} />}
      </div>
      <MatterWork matter={m} lookups={lookups} canWrite={canWrite && canLogWork(m.status)} canInvoice={canWrite} />
      <EvidencePanel owner="matters" id={m.id} canWrite={canWrite && m.status !== "ARCHIVED"} />
      {editing && editing !== "new" && <MatterDialog matter={editing} lookups={lookups} onClose={onCloseEdit} />}
      {confirm && (
        <Dialog title={confirm === "close" ? "Close this matter?" : "Archive this matter?"} onClose={() => setConfirm(null)}
          footer={<><Btn onClick={() => setConfirm(null)}>Not yet</Btn><Btn kind="primary" disabled={move.isPending} onClick={() => move.mutate(confirm)}>{move.isPending ? "Working…" : confirm === "close" ? "Close matter" : "Archive"}</Btn></>}>
          <div style={{ lineHeight: 1.5, fontSize: 13 }}>{confirm === "close" ? "No more time, expenses or key dates can be added while it is closed. It can be reopened." : "Archiving files the matter away. Bill any outstanding work first."}</div>
        </Dialog>)}
    </div>
  )
}
const Fact = ({ k, v }: { k: string; v: string }) => <div style={{ minWidth: 0 }}><div style={{ fontSize: 11, fontWeight: 600, color: "var(--hf-text-muted)", textTransform: "uppercase", letterSpacing: ".03em", marginBottom: 3 }}>{k}</div><div style={{ fontSize: 13, overflowWrap: "anywhere" }}>{v}</div></div>

function MatterDialog({ matter, lookups, onClose }: { matter: Matter | null; lookups: Lookups; onClose: () => void }) {
  const creating = !matter, qc = useQueryClient()
  const [f, setF] = useState<MatterForm>(() => matter ? { clientId: matter.clientId, attorneyId: matter.attorneyId, matterNumber: matter.matterNumber, matterType: matter.matterType, matterName: matter.matterName, description: matter.description ?? "", billingType: matter.billingType, fixedFeeAmount: matter.fixedFeeAmount != null ? String(matter.fixedFeeAmount) : "", openedDate: matter.openedDate ?? "", notes: matter.notes ?? "" } : emptyMatter())
  const [err, setErr] = useState("")
  const set = (k: keyof MatterForm) => (v: string) => setF(s => ({ ...s, [k]: v }))
  const problem = matterProblem(f, creating)
  const save = useMutation({
    mutationFn: () => creating ? apiClient.post(`${BASE}/matters`, matterRequest(f, true)) : apiClient.put(`${BASE}/matters/${matter!.id}`, matterRequest(f, false)),
    onSuccess: () => { toast.success(creating ? "Matter opened" : "Matter saved"); qc.invalidateQueries({ queryKey: ["lp-matters"] }); onClose() },
    onError: async e => setErr(await errorText(e, "Could not save the matter")),
  })
  const attorneys = lookups.attorneys.filter(a => a.active || a.id === f.attorneyId).map(a => [a.id, a.name] as const)
  return (
    <Dialog title={creating ? "New matter" : `Edit ${matter!.matterNumber}`} onClose={onClose} width={640}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Save"}</Btn></>}>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 12px" }}>
        {creating ? <Pick label="Client" value={f.clientId} onChange={set("clientId")} options={lookups.clients.filter(c => c.status === "ACTIVE").map(c => [c.id, c.name] as const)} blank="Choose a client" /> : <div />}
        <Pick label="Responsible attorney" value={f.attorneyId} onChange={set("attorneyId")} options={attorneys} blank="Choose an attorney" />
        {creating && <Input label="Matter number" value={f.matterNumber} onChange={set("matterNumber")} maxLength={100} />}
        {creating && <Pick label="Type" value={f.matterType} onChange={set("matterType")} options={MATTER_TYPES} />}
        <Pick label="Billing" value={f.billingType} onChange={set("billingType")} options={BILLING_TYPES} />
        {f.billingType === "FIXED_FEE" && <Input label="Fixed fee (R)" type="number" min="0" step="0.01" value={f.fixedFeeAmount} onChange={set("fixedFeeAmount")} />}
        {creating && <Input label="Opened" type="date" value={f.openedDate} onChange={set("openedDate")} hint="Leave empty for today" />}
      </div>
      <Input label="Matter name" value={f.matterName} onChange={set("matterName")} maxLength={255} />
      <Input label="Description" value={f.description} onChange={set("description")} />
      <Input label="Notes" value={f.notes} onChange={set("notes")} />
      {problem && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
