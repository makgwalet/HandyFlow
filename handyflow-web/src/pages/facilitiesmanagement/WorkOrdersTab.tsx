// Work orders: the list, and one job card with everything that can be done to it.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { filenameFrom, saveBlob } from "../../api/recipientAccess.client"
import { toast } from "../../store/toast.store"
import { Btn, Empty, Pager, Pill, ReasonDialog, SectionTitle, card, day, errorText, fail, inp, money, muted, pageOf, td, th, when } from "./kit"
import { BASE, useLookups, useWho, type Lookups } from "./lookups"
import { PRIORITIES, PRIORITY_TONE, WO_STATUS, canCostLines, isTerminal, labelOf, slaNote, woActions, type Tone } from "./fm.logic"
import { ApproveDialog, AssignDialog, CompleteDialog, CostDialog, CreateDialog, HoldDialog, RaiseDialog, RejectDialog } from "./WorkOrderDialogs"
import FmEvidencePanel from "./FmEvidencePanel"

export interface WorkOrder {
  id: string; workOrderNumber: string; clientId: string; siteId: string; assetId: string | null; category: string; priority: string; status: string; description: string; reportedBy: string | null
  technicianId: string | null; technicianName: string | null; vendorId: string | null; vendorName: string | null; scheduledDate: string | null; completedAt: string | null; completionNotes: string | null
  holdReason: string | null; cost: number | null; invoiced: boolean; cancellationReason: string | null; createdAt: string; safetyImpact: boolean; rejectionReason: string | null
  requiresVerification: boolean; reworkCount: number; lastReworkReason: string | null; downtimeMinutes: number | null; failureCause: string | null; internalCost: number | null
  coverage: string | null; responseDueAt: string | null; resolutionDueAt: string | null; respondedAt: string | null; responseSla: string | null; resolutionSla: string | null; slaPausedAt: string | null
}
interface CostLine { id: string; costType: string; description: string; quantity: number; unitCost: number; totalCost: number; unitCharge: number | null; totalCharge: number; billable: boolean }

export const StatusPill = ({ status }: { status: string }) => { const s = WO_STATUS[status] ?? { label: status, tone: "neutral" as Tone }; return <Pill tone={s.tone}>{s.label}</Pill> }

export default function WorkOrdersTab() {
  const lookups = useLookups(), who = useWho()
  const [page, setPage] = useState(0), [status, setStatus] = useState(""), [clientId, setClientId] = useState("")
  const [open, setOpen] = useState<string | null>(null), [dialog, setDialog] = useState<"raise" | "create" | null>(null)
  const { data, isLoading, isError } = useQuery({
    queryKey: ["fm-wo-list", page, status, clientId],
    queryFn: async () => pageOf<WorkOrder>(await apiClient.get(`${BASE}/work-orders`, { params: { page, size: 25, status: status || undefined, clientId: clientId || undefined } })),
  })
  if (open) return <WorkOrderView id={open} lookups={lookups} onBack={() => setOpen(null)} />
  const rows = data?.content ?? []
  const now = new Date()
  return (
    <div>
      <SectionTitle right={<>
        {(who.assign) && <Btn onClick={() => setDialog("raise")}>Log a fault report</Btn>}
        {who.assign && <Btn kind="primary" onClick={() => setDialog("create")}>New work order</Btn>}
      </>}>Work orders</SectionTitle>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 10 }}>
        <select aria-label="Status" value={status} onChange={e => { setStatus(e.target.value); setPage(0) }} style={{ ...inp, width: "auto" }}>
          <option value="">All statuses</option>{Object.entries(WO_STATUS).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
        </select>
        <select aria-label="Client" value={clientId} onChange={e => { setClientId(e.target.value); setPage(0) }} style={{ ...inp, width: "auto" }}>
          <option value="">All clients</option>{lookups.clients.map(c => <option key={c.id} value={c.id}>{c.tradingName}</option>)}
        </select>
      </div>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {isError ? <Empty>Could not load the work orders. Please try again.</Empty> : isLoading ? <Empty>Loading…</Empty> : rows.length === 0 ? <Empty>No work orders match.</Empty> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Job</th><th style={th}>Where</th><th style={th}>Priority</th><th style={th}>Assigned to</th><th style={th}>Due</th><th style={th}>Status</th></tr></thead>
            <tbody>{rows.map(w => {
              const sla = slaNote(w.resolutionDueAt, w.resolutionSla, isTerminal(w.status) || w.status === "PENDING_VERIFICATION", now)
              return (
                <tr key={w.id} onClick={() => setOpen(w.id)} style={{ cursor: "pointer" }}>
                  <td style={td}><b>{w.workOrderNumber}</b>{w.safetyImpact && <> <Pill tone="danger">Safety</Pill></>}<div style={{ ...muted, maxWidth: 320 }}>{w.description}</div></td>
                  <td style={td}>{lookups.clientName(w.clientId)}<div style={muted}>{lookups.siteName(w.siteId)}</div></td>
                  <td style={td}><Pill tone={PRIORITY_TONE[w.priority] ?? "neutral"}>{labelOf(PRIORITIES, w.priority)}</Pill></td>
                  <td style={td}>{w.technicianName || w.vendorName || <span style={muted}>Nobody yet</span>}</td>
                  <td style={td}>{day(w.scheduledDate)}{sla && <div><Pill tone={sla.tone}>{sla.text}</Pill></div>}</td>
                  <td style={td}><StatusPill status={w.status} /></td>
                </tr>)})}</tbody>
          </table>)}
      </div>
      <Pager page={page} pages={data?.totalPages ?? 0} onPage={setPage} />
      {dialog === "raise" && <RaiseDialog lookups={lookups} onClose={() => setDialog(null)} onDone={id => id && setOpen(id)} />}
      {dialog === "create" && <CreateDialog lookups={lookups} onClose={() => setDialog(null)} onDone={id => id && setOpen(id)} />}
    </div>
  )
}

type Pending = "approve" | "reject" | "assign" | "hold" | "complete" | "cancel" | "rework" | "cost" | null

function WorkOrderView({ id, lookups, onBack }: { id: string; lookups: Lookups; onBack: () => void }) {
  const qc = useQueryClient(), who = useWho()
  const [pending, setPending] = useState<Pending>(null)
  const { data: w, isLoading, isError } = useQuery({ queryKey: ["fm-wo", id], queryFn: async () => (await apiClient.get<WorkOrder>(`${BASE}/work-orders/${id}`)).data })
  const costs = useQuery({ queryKey: ["fm-costs", id], queryFn: async () => (await apiClient.get<CostLine[]>(`${BASE}/work-orders/${id}/cost-lines`)).data })
  const act = useMutation({
    mutationFn: (path: "start" | "verify") => apiClient.post(`${BASE}/work-orders/${id}/${path}`),
    onSuccess: (_r, p) => { toast.success(p === "start" ? "Work started" : "Work verified"); qc.invalidateQueries({ queryKey: ["fm-wo"] }); qc.invalidateQueries({ queryKey: ["fm-wo-list"] }) },
    onError: e => { void fail(e, "That did not work") },
  })
  const removeCost = useMutation({
    mutationFn: (lineId: string) => apiClient.delete(`${BASE}/work-orders/${id}/cost-lines/${lineId}`),
    onSuccess: () => { toast.success("Cost line removed"); qc.invalidateQueries({ queryKey: ["fm-costs", id] }) },
    onError: e => { void fail(e, "Could not remove that cost line") },
  })
  const card_ = useMutation({
    mutationFn: async () => { const r = await apiClient.get(`${BASE}/work-orders/${id}/job-card.pdf`, { responseType: "blob" }); saveBlob(r.data as Blob, filenameFrom(r.headers?.["content-disposition"]) || "job-card.pdf") },
    onError: async e => { toast.error(await errorText(e, "Could not get the job card")) },
  })
  const back = <div style={{ marginBottom: 8 }}><Btn small kind="ghost" onClick={onBack}>← All work orders</Btn></div>
  if (isError) return <div>{back}<Empty>Could not load that work order.</Empty></div>
  if (isLoading || !w) return <div>{back}<Empty>Loading…</Empty></div>
  const actions = woActions(w.status, w.invoiced, who)
  const done = (p: Pending) => () => setPending(p)
  const lines = Array.isArray(costs.data) ? costs.data : []
  const totalCost = lines.reduce((s, l) => s + l.totalCost, 0), totalCharge = lines.reduce((s, l) => s + (l.billable ? l.totalCharge : 0), 0)
  const now = new Date()
  const resp = slaNote(w.responseDueAt, w.responseSla, !!w.respondedAt, now), reso = slaNote(w.resolutionDueAt, w.resolutionSla, isTerminal(w.status) || w.status === "PENDING_VERIFICATION", now)
  return (
    <div>
      {back}
      <SectionTitle right={<>
        <Btn disabled={card_.isPending} onClick={() => card_.mutate()}>Job card (PDF)</Btn>
        {actions.includes("approve") && <Btn kind="primary" onClick={done("approve")}>Approve</Btn>}
        {actions.includes("reject") && <Btn onClick={done("reject")}>Decline</Btn>}
        {actions.includes("assign") && <Btn onClick={done("assign")}>{w.technicianId || w.vendorId ? "Reassign" : "Assign"}</Btn>}
        {actions.includes("start") && <Btn kind="primary" disabled={act.isPending} onClick={() => act.mutate("start")}>Start</Btn>}
        {actions.includes("hold") && <Btn onClick={done("hold")}>Put on hold</Btn>}
        {actions.includes("complete") && <Btn kind="primary" onClick={done("complete")}>Complete</Btn>}
        {actions.includes("verify") && <Btn kind="primary" disabled={act.isPending} onClick={() => act.mutate("verify")}>Verify</Btn>}
        {actions.includes("rework") && <Btn onClick={done("rework")}>Send back</Btn>}
        {actions.includes("cancel") && <Btn onClick={done("cancel")}>Cancel</Btn>}
      </>}>{w.workOrderNumber} <StatusPill status={w.status} /></SectionTitle>
      <div style={{ ...card, display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(190px, 1fr))", gap: 14, marginBottom: 14 }}>
        <Fact k="Client" v={lookups.clientName(w.clientId)} />
        <Fact k="Site" v={lookups.siteName(w.siteId)} />
        <Fact k="Priority" v={labelOf(PRIORITIES, w.priority)} />
        <Fact k="Assigned to" v={w.technicianName || w.vendorName || "Nobody yet"} />
        <Fact k="Scheduled" v={day(w.scheduledDate)} />
        <Fact k="Logged" v={when(w.createdAt)} />
        {w.reportedBy && <Fact k="Reported by" v={w.reportedBy} />}
        {w.coverage && <Fact k="Coverage" v={labelOf([], w.coverage)} />}
        {(resp || reso) && <div><div style={factKey}>Service targets</div>{resp && <div style={{ marginBottom: 3 }}>Response <Pill tone={resp.tone}>{resp.text}</Pill></div>}{reso && <div>Fix <Pill tone={reso.tone}>{reso.text}</Pill></div>}{w.slaPausedAt && <div style={muted}>Clock paused</div>}</div>}
      </div>
      <div style={{ ...card, marginBottom: 14, fontSize: 13, lineHeight: 1.55 }}>
        <div>{w.description}</div>
        {w.safetyImpact && <div style={{ marginTop: 6 }}><Pill tone="danger">Safety risk</Pill></div>}
        {w.holdReason && w.status === "ON_HOLD" && <div style={{ marginTop: 6 }}><b>On hold:</b> {w.holdReason}</div>}
        {w.rejectionReason && <div style={{ marginTop: 6 }}><b>Declined:</b> {w.rejectionReason}</div>}
        {w.cancellationReason && <div style={{ marginTop: 6 }}><b>Cancelled:</b> {w.cancellationReason}</div>}
        {w.reworkCount > 0 && <div style={{ marginTop: 6 }}><b>Sent back {w.reworkCount} time{w.reworkCount === 1 ? "" : "s"}:</b> {w.lastReworkReason}</div>}
        {w.completionNotes && <div style={{ marginTop: 6 }}><b>Done:</b> {w.completionNotes}{w.completedAt ? ` (${when(w.completedAt)})` : ""}</div>}
        {(w.downtimeMinutes != null || w.failureCause) && <div style={{ marginTop: 6 }} ><span style={muted}>{[w.failureCause && `Cause: ${w.failureCause}`, w.downtimeMinutes != null && `Downtime: ${w.downtimeMinutes} min`].filter(Boolean).join(" · ")}</span></div>}
        {w.cost != null && <div style={{ marginTop: 6 }}><b>Charged:</b> {money(w.cost)}{w.invoiced ? " · invoiced" : ""}</div>}
      </div>

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", margin: "0 0 6px" }}>
        <div style={{ fontSize: 13, fontWeight: 700 }}>Cost lines</div>
        {who.execute && canCostLines(w.status) && <Btn small onClick={done("cost")}>Add a cost line</Btn>}
      </div>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {costs.isError ? <div style={{ ...muted, padding: 14 }}>Could not load the cost lines.</div> : lines.length === 0 ? <div style={{ ...muted, padding: 14 }}>{costs.isLoading ? "Loading…" : "No cost lines yet."}</div> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>What</th><th style={{ ...th, textAlign: "right" }}>Qty</th><th style={{ ...th, textAlign: "right" }}>Cost</th><th style={{ ...th, textAlign: "right" }}>Charged</th><th /></tr></thead>
            <tbody>{lines.map(l => (
              <tr key={l.id}>
                <td style={td}><b>{l.description}</b><div style={muted}>{labelOf([], l.costType)}{l.billable ? "" : " · not billed"}</div></td>
                <td style={{ ...td, textAlign: "right" }}>{l.quantity}</td>
                <td style={{ ...td, textAlign: "right" }}>{money(l.totalCost)}</td>
                <td style={{ ...td, textAlign: "right" }}>{l.billable ? money(l.totalCharge) : "—"}</td>
                <td style={{ ...td, textAlign: "right" }}>{who.execute && canCostLines(w.status) && <Btn small disabled={removeCost.isPending} onClick={() => removeCost.mutate(l.id)}>Remove</Btn>}</td>
              </tr>))}
              <tr><td style={td}><b>Total</b></td><td style={td} /><td style={{ ...td, textAlign: "right" }}><b>{money(totalCost)}</b></td><td style={{ ...td, textAlign: "right" }}><b>{money(totalCharge)}</b></td><td style={td} /></tr></tbody>
          </table>)}
      </div>

      <FmEvidencePanel owner="work-orders" id={w.id} canWrite={who.execute && !isTerminal(w.status)} />

      {pending === "approve" && <ApproveDialog id={w.id} priority={w.priority} onClose={() => setPending(null)} />}
      {pending === "reject" && <RejectDialog id={w.id} onClose={() => setPending(null)} />}
      {pending === "assign" && <AssignDialog id={w.id} techId={w.technicianId} vendorId={w.vendorId} onClose={() => setPending(null)} />}
      {pending === "hold" && <HoldDialog id={w.id} canPauseSla={who.manage} onClose={() => setPending(null)} />}
      {pending === "complete" && <CompleteDialog id={w.id} createdAt={w.createdAt} needsCheck={w.requiresVerification} onClose={() => setPending(null)} />}
      {pending === "cost" && <CostDialog id={w.id} onClose={() => setPending(null)} />}
      {pending === "rework" && (
        <ReasonDialog title="Send this work back" label="What needs fixing?" confirm="Send back" onClose={() => setPending(null)}
          onSubmit={async t => { await apiClient.post(`${BASE}/work-orders/${w.id}/rework`, { reason: t.trim() }); toast.success("Sent back for rework"); qc.invalidateQueries({ queryKey: ["fm-wo"] }); qc.invalidateQueries({ queryKey: ["fm-wo-list"] }) }} />)}
      {pending === "cancel" && (
        <ReasonDialog title="Cancel this work order?" label="Why is it being cancelled?" confirm="Cancel the work order" danger required={false} onClose={() => setPending(null)}
          onSubmit={async t => { await apiClient.post(`${BASE}/work-orders/${w.id}/cancel`, { reason: t.trim() || null }); toast.success("Work order cancelled"); qc.invalidateQueries({ queryKey: ["fm-wo"] }); qc.invalidateQueries({ queryKey: ["fm-wo-list"] }) }} />)}
    </div>
  )
}
const factKey: React.CSSProperties = { fontSize: 11, fontWeight: 600, color: "var(--hf-text-muted)", textTransform: "uppercase", letterSpacing: ".03em", marginBottom: 3 }
const Fact = ({ k, v }: { k: string; v: string }) => <div style={{ minWidth: 0 }}><div style={factKey}>{k}</div><div style={{ fontSize: 13, wordBreak: "break-word" }}>{v || "—"}</div></div>
