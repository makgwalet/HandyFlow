// The forms behind a work order: raise a request, create a job, assign, hold, complete, add a cost line.
import { useMemo, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, ErrorLine, Input, Pick, money, muted, errorText, pageOf } from "./kit"
import { BASE, useAssignees, type Lookups } from "./lookups"
import {
  CATEGORIES, COST_TYPES, PRIORITIES, REQUEST_CATEGORIES, assignBody, assignProblem, completeBody, completeProblem, costBody, costProblem, costTotals,
  emptyComplete, emptyCost, emptyOrder, emptyRequest, orderBody, orderProblem, requestBody, requestProblem,
  type CompleteForm, type CostForm, type OrderForm, type RequestForm,
} from "./fm.logic"

const refresh = (qc: ReturnType<typeof useQueryClient>) => { qc.invalidateQueries({ queryKey: ["fm-wo"] }); qc.invalidateQueries({ queryKey: ["fm-wo-list"] }); qc.invalidateQueries({ queryKey: ["fm-costs"] }) }

interface AssetRow { id: string; name: string; assetTag: string | null; siteId: string }
function useSiteAssets(siteId: string) {
  return useQuery({ enabled: !!siteId, queryKey: ["fm-assets-site", siteId], queryFn: async () => pageOf<AssetRow>(await apiClient.get(`${BASE}/assets`, { params: { siteId, size: 200 } })).content })
}
/** Client, site and (optionally) asset pickers that narrow each other. */
export function Where({ lookups, f, set }: { lookups: Lookups; f: { clientId: string; siteId: string; assetId: string }; set: (k: "clientId" | "siteId" | "assetId") => (v: string) => void }) {
  const sites = useMemo(() => lookups.sites.filter(s => s.clientId === f.clientId && s.status !== "CLOSED"), [lookups.sites, f.clientId])
  const assets = useSiteAssets(f.siteId)
  return (
    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 12px" }}>
      <Pick label="Client" value={f.clientId} onChange={v => { set("clientId")(v); set("siteId")(""); set("assetId")("") }} options={lookups.clients.filter(c => c.status === "ACTIVE").map(c => [c.id, c.tradingName] as const)} blank="Choose a client" />
      <Pick label="Site" value={f.siteId} onChange={v => { set("siteId")(v); set("assetId")("") }} options={sites.map(s => [s.id, s.name] as const)} blank={f.clientId ? "Choose a site" : "Choose a client first"} />
      <Pick label="Asset (optional)" value={f.assetId} onChange={set("assetId")} options={(assets.data ?? []).map(a => [a.id, a.assetTag ? `${a.assetTag} · ${a.name}` : a.name] as const)} blank="Not for one asset" />
    </div>
  )
}

export function RaiseDialog({ lookups, onClose, onDone }: { lookups: Lookups; onClose: () => void; onDone: (id: string) => void }) {
  const qc = useQueryClient()
  const [f, setF] = useState<RequestForm>(emptyRequest()), [err, setErr] = useState("")
  const set = (k: keyof RequestForm) => (v: string) => setF(s => ({ ...s, [k]: v }))
  const problem = requestProblem(f)
  const save = useMutation({
    mutationFn: async () => (await apiClient.post<{ id: string }>(`${BASE}/work-orders/requests`, requestBody(f))).data,
    onSuccess: r => { toast.success("Request logged"); refresh(qc); onDone(r?.id); onClose() },
    onError: async e => setErr(await errorText(e, "Could not log the request")),
  })
  return (
    <Dialog title="Log a fault report" onClose={onClose} width={640}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Log request"}</Btn></>}>
      <div style={{ ...muted, marginBottom: 10 }}>A request has to be approved before it can be assigned.</div>
      <Where lookups={lookups} f={f} set={set} />
      <Input label="What is wrong?" value={f.description} onChange={set("description")} autoFocus />
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 12px" }}>
        <Pick label="Kind" value={f.category} onChange={set("category")} options={REQUEST_CATEGORIES} />
        <Pick label="Priority" value={f.priority} onChange={set("priority")} options={PRIORITIES} />
        <Input label="Reported by" value={f.reportedBy} onChange={set("reportedBy")} />
      </div>
      <label style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 13, margin: "6px 0" }}>
        <input type="checkbox" checked={f.safetyImpact} onChange={e => setF(s => ({ ...s, safetyImpact: e.target.checked }))} /> This is a safety risk
      </label>
      {problem && f.description.trim() === "" && f.siteId && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

export function CreateDialog({ lookups, onClose, onDone }: { lookups: Lookups; onClose: () => void; onDone: (id: string) => void }) {
  const qc = useQueryClient()
  const [f, setF] = useState<OrderForm>(emptyOrder()), [err, setErr] = useState("")
  const set = (k: keyof OrderForm) => (v: string) => setF(s => ({ ...s, [k]: v }))
  const problem = orderProblem(f)
  const save = useMutation({
    mutationFn: async () => (await apiClient.post<{ id: string }>(`${BASE}/work-orders`, orderBody(f))).data,
    onSuccess: r => { toast.success("Work order created"); refresh(qc); onDone(r?.id); onClose() },
    onError: async e => setErr(await errorText(e, "Could not create the work order")),
  })
  return (
    <Dialog title="New work order" onClose={onClose} width={640}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Create"}</Btn></>}>
      <Where lookups={lookups} f={f} set={set} />
      <Input label="What needs doing?" value={f.description} onChange={set("description")} autoFocus />
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 12px" }}>
        <Pick label="Kind" value={f.category} onChange={set("category")} options={CATEGORIES} />
        <Pick label="Priority" value={f.priority} onChange={set("priority")} options={PRIORITIES} />
        <Input label="Scheduled for" type="date" value={f.scheduledDate} onChange={set("scheduledDate")} />
        <Input label="Reported by" value={f.reportedBy} onChange={set("reportedBy")} />
      </div>
      <label style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 13, margin: "6px 0" }}>
        <input type="checkbox" checked={f.requiresVerification} onChange={e => setF(s => ({ ...s, requiresVerification: e.target.checked }))} /> Someone must check the work before it can be billed
      </label>
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

export function ApproveDialog({ id, priority, onClose }: { id: string; priority: string; onClose: () => void }) {
  const qc = useQueryClient()
  const [p, setP] = useState(priority), [date, setDate] = useState(""), [check, setCheck] = useState(false), [err, setErr] = useState("")
  const save = useMutation({
    mutationFn: () => apiClient.post(`${BASE}/work-orders/${id}/approve`, { priority: p, scheduledDate: date || null, requiresVerification: check }),
    onSuccess: () => { toast.success("Request approved"); refresh(qc); onClose() },
    onError: async e => setErr(await errorText(e, "Could not approve that request")),
  })
  return (
    <Dialog title="Approve this request" onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Approving…" : "Approve"}</Btn></>}>
      <div style={{ ...muted, marginBottom: 10 }}>You cannot approve a request you logged yourself, unless you are an administrator.</div>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 12px" }}>
        <Pick label="Priority" value={p} onChange={setP} options={PRIORITIES} />
        <Input label="Scheduled for" type="date" value={date} onChange={setDate} />
      </div>
      <label style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 13, margin: "6px 0" }}>
        <input type="checkbox" checked={check} onChange={e => setCheck(e.target.checked)} /> Someone must check the work before it can be billed
      </label>
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

export function RejectDialog({ id, onClose }: { id: string; onClose: () => void }) {
  const qc = useQueryClient()
  const [reason, setReason] = useState(""), [msg, setMsg] = useState(""), [err, setErr] = useState("")
  const save = useMutation({
    mutationFn: () => apiClient.post(`${BASE}/work-orders/${id}/reject`, { reason: reason.trim(), clientMessage: msg.trim() || null }),
    onSuccess: () => { toast.success("Request declined"); refresh(qc); onClose() },
    onError: async e => setErr(await errorText(e, "Could not decline that request")),
  })
  return (
    <Dialog title="Decline this request" onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="danger" disabled={!reason.trim() || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Declining…" : "Decline"}</Btn></>}>
      <Input label="Reason (for your team)" value={reason} onChange={setReason} maxLength={1000} autoFocus />
      <Input label="Message to the client (optional)" hint="Shown to the client in their portal" value={msg} onChange={setMsg} maxLength={1000} />
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

export function AssignDialog({ id, techId, vendorId, onClose }: { id: string; techId: string | null; vendorId: string | null; onClose: () => void }) {
  const qc = useQueryClient()
  const { techs, vendors, loading } = useAssignees(true)
  const [t, setT] = useState(techId ?? ""), [v, setV] = useState(vendorId ?? ""), [err, setErr] = useState("")
  const problem = assignProblem(t, v)
  const save = useMutation({
    mutationFn: () => apiClient.post(`${BASE}/work-orders/${id}/assign`, assignBody(t, v, techs.map(x => ({ id: x.id, name: x.name })), vendors.map(x => ({ id: x.id, name: x.companyName })))),
    onSuccess: () => { toast.success("Work assigned"); refresh(qc); onClose() },
    onError: async e => setErr(await errorText(e, "Could not assign the work")),
  })
  return (
    <Dialog title="Assign this work" onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Assigning…" : "Assign"}</Btn></>}>
      <Pick label="Technician" value={t} onChange={x => { setT(x); if (x) setV("") }} options={techs.map(x => [x.id, x.specialization ? `${x.name} (${x.specialization})` : x.name] as const)} blank={loading ? "Loading…" : "Not a technician"} />
      <Pick label="Or a vendor" value={v} onChange={x => { setV(x); if (x) setT("") }} options={vendors.map(x => [x.id, x.companyName] as const)} blank={loading ? "Loading…" : "Not a vendor"} />
      <div style={muted}>A vendor whose paperwork is out of date is refused when you assign.</div>
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

export function HoldDialog({ id, canPauseSla, onClose }: { id: string; canPauseSla: boolean; onClose: () => void }) {
  const qc = useQueryClient()
  const [reason, setReason] = useState(""), [pause, setPause] = useState(false), [err, setErr] = useState("")
  const save = useMutation({
    mutationFn: () => apiClient.post(`${BASE}/work-orders/${id}/hold`, { reason: reason.trim(), pauseSla: pause }),
    onSuccess: () => { toast.success("Work put on hold"); refresh(qc); onClose() },
    onError: async e => setErr(await errorText(e, "Could not put the work on hold")),
  })
  return (
    <Dialog title="Put this work on hold" onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!reason.trim() || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Put on hold"}</Btn></>}>
      <Input label="Why?" value={reason} onChange={setReason} autoFocus />
      {canPauseSla && <label style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 13, margin: "6px 0" }}><input type="checkbox" checked={pause} onChange={e => setPause(e.target.checked)} /> Stop the SLA clock while it is on hold (recorded in the audit log)</label>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

export function CompleteDialog({ id, createdAt, needsCheck, onClose }: { id: string; createdAt: string | null; needsCheck: boolean; onClose: () => void }) {
  const qc = useQueryClient()
  const [f, setF] = useState<CompleteForm>(emptyComplete()), [err, setErr] = useState("")
  const set = (k: keyof CompleteForm) => (v: string) => setF(s => ({ ...s, [k]: v }))
  const problem = completeProblem(f, createdAt, new Date())
  const save = useMutation({
    mutationFn: () => apiClient.post(`${BASE}/work-orders/${id}/complete`, completeBody(f)),
    onSuccess: () => { toast.success(needsCheck ? "Sent for checking" : "Work completed"); refresh(qc); onClose() },
    onError: async e => setErr(await errorText(e, "Could not complete the work")),
  })
  return (
    <Dialog title="Complete this work" onClose={onClose} width={600}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : needsCheck ? "Send for checking" : "Complete"}</Btn></>}>
      {needsCheck && <div style={{ ...muted, marginBottom: 10 }}>This work must be checked before it can be billed.</div>}
      <Input label="What was done?" value={f.completionNotes} onChange={set("completionNotes")} autoFocus />
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 12px" }}>
        <Input label="Cost charged (R)" type="number" min="0" step="0.01" value={f.cost} onChange={set("cost")} hint="Leave empty if the cost lines cover it" />
        <Input label="Finished at" type="datetime-local" value={f.completedAt} onChange={set("completedAt")} hint="Leave empty for now" />
        <Input label="Downtime (minutes)" type="number" min="0" step="1" value={f.downtimeMinutes} onChange={set("downtimeMinutes")} />
        <Input label="Cause of failure" value={f.failureCause} onChange={set("failureCause")} />
      </div>
      {problem && f.completionNotes.trim() && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

export function CostDialog({ id, onClose }: { id: string; onClose: () => void }) {
  const qc = useQueryClient()
  const [f, setF] = useState<CostForm>(emptyCost()), [err, setErr] = useState("")
  const set = (k: keyof CostForm) => (v: string) => setF(s => ({ ...s, [k]: v }))
  const problem = costProblem(f), tot = costTotals(f)
  const save = useMutation({
    mutationFn: () => apiClient.post(`${BASE}/work-orders/${id}/cost-lines`, costBody(f)),
    onSuccess: () => { toast.success("Cost line added"); refresh(qc); onClose() },
    onError: async e => setErr(await errorText(e, "Could not add the cost line")),
  })
  return (
    <Dialog title="Add a cost line" onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Adding…" : "Add"}</Btn></>}>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 12px" }}>
        <Pick label="Kind" value={f.costType} onChange={set("costType")} options={COST_TYPES} />
        <Input label="Description" value={f.description} onChange={set("description")} autoFocus />
        <Input label="Quantity" type="number" min="0" step="any" value={f.quantity} onChange={set("quantity")} />
        <Input label="Cost each (R)" type="number" min="0" step="0.01" value={f.unitCost} onChange={set("unitCost")} />
        <Input label="Charge each (R)" type="number" min="0" step="0.01" value={f.unitCharge} onChange={set("unitCharge")} hint="Leave empty to charge what it cost" />
      </div>
      <label style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 13, margin: "6px 0" }}>
        <input type="checkbox" checked={f.billable} onChange={e => setF(s => ({ ...s, billable: e.target.checked }))} /> Bill this to the client
      </label>
      <div style={muted}>Cost {money(tot.cost)} · Charged {money(tot.charge)}</div>
      {problem && f.description.trim() && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
