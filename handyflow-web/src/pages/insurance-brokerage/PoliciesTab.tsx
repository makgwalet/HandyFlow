// Policies placed for clients: quote -> bind (the insurer issues a number) -> activate (cover starts, commission invoice raised)
// -> lapse / reinstate / cancel / renew.
import { useMemo, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { errorText } from "../../api/projectDocs"
import { usePermission } from "../../hooks/usePermission"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, Empty, ErrorLine, SectionTitle, ReasonDialog, card, day, fail, inp, muted, th, td } from "../projects/tabs/pmKit"
import {
  ASSET_TYPES, EXPIRY_WINDOW, FREQUENCIES, LINES, STATUS_LABEL, actionsFor, brokerPolicyProblem, brokerPolicyRequest, daysToExpiry, emptyBrokerPolicy, labelOf, type BrokerPolicyForm,
} from "../insurance/insurance.logic"
import { ExpiryNote, Input, Pager, Pick, RenewDialog, RenewalHistory, Row, StatusPill, money } from "../insurance/parts"
import { BASE, pageOf, useLookups } from "./lookups"

interface Policy {
  id: string; clientId: string; insurerId: string; policyNumber: string | null; quoteReference: string | null; lineOfBusiness: string; assetType: string | null; assetReference: string | null
  sumInsured: number | null; premiumAmount: number | null; premiumFrequency: string | null; excessAmount: number | null; commissionRatePct: number | null; startDate: string | null; expiryDate: string | null
  status: string; boundAt: string | null; activatedAt: string | null; cancelledDate: string | null; cancelReason: string | null; renewalOfPolicyId: string | null; notes: string | null
}
const P = `${BASE}/policies`
const STATUSES = ["QUOTE", "BOUND", "ACTIVE", "LAPSED", "CANCELLED", "EXPIRED", "RENEWED"]
const label = (p: Policy) => p.policyNumber ?? p.quoteReference ?? "Quote"

export default function PoliciesTab() {
  const canWrite = usePermission("INSURANCEBROKERAGE_MANAGE") || usePermission("INSURANCEBROKERAGE_ADMIN")
  const lookups = useLookups()
  const [status, setStatus] = useState(""), [line, setLine] = useState(""), [q, setQ] = useState(""), [page, setPage] = useState(0), [due, setDue] = useState(false)
  const [openId, setOpenId] = useState<string | null>(null), [editing, setEditing] = useState<Policy | "new" | null>(null)
  const params = due ? { status: "ACTIVE", size: 200 } : { status: status || undefined, lineOfBusiness: line || undefined, q: q.trim() || undefined, page, size: 24 }
  const { data, isLoading, isError } = useQuery({ queryKey: ["ib-policies", params], queryFn: async () => pageOf<Policy>(await apiClient.get(P, { params })) })
  const rows = useMemo(() => {
    const all = data?.content ?? []
    return due ? all.filter(p => (daysToExpiry(p.expiryDate) ?? 999) <= EXPIRY_WINDOW).sort((a, b) => (a.expiryDate ?? "").localeCompare(b.expiryDate ?? "")) : all
  }, [data, due])
  const open = rows.find(p => p.id === openId) ?? null
  const capped = due && (data?.totalElements ?? 0) > (data?.content.length ?? 0)

  return (
    <div>
      <SectionTitle right={canWrite && <Btn kind="primary" onClick={() => setEditing("new")}>New quote</Btn>}>Policies</SectionTitle>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 12, alignItems: "center" }}>
        <input aria-label="Search policies" placeholder="Search number, quote or asset" value={q} disabled={due} onChange={e => { setQ(e.target.value); setPage(0) }} style={{ ...inp, width: 240 }} />
        <select aria-label="Status" value={status} disabled={due} onChange={e => { setStatus(e.target.value); setPage(0) }} style={{ ...inp, width: 150 }}>
          <option value="">Any status</option>{STATUSES.map(s => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}
        </select>
        <select aria-label="Type of cover" value={line} disabled={due} onChange={e => { setLine(e.target.value); setPage(0) }} style={{ ...inp, width: 150 }}>
          <option value="">Any cover</option>{LINES.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
        </select>
        <label style={{ display: "flex", gap: 6, alignItems: "center", fontSize: 13 }}>
          <input type="checkbox" checked={due} onChange={e => { setDue(e.target.checked); setPage(0) }} /> Renewals due in {EXPIRY_WINDOW} days
        </label>
      </div>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {isError ? <Empty>Could not load the policies. Please try again.</Empty> : isLoading ? <Empty>Loading…</Empty> : rows.length === 0 ? <Empty>{due ? "Nothing runs out in the next 60 days." : "No policies match."}</Empty> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Policy</th><th style={th}>Client</th><th style={th}>Insurer</th><th style={th}>Term</th><th style={{ ...th, textAlign: "right" }}>Premium</th><th style={th}>Status</th></tr></thead>
            <tbody>{rows.map(p => (
              <tr key={p.id} onClick={() => setOpenId(p.id)} style={{ cursor: "pointer" }}>
                <td style={td}><b>{label(p)}</b><div style={muted}>{labelOf(LINES, p.lineOfBusiness)}{p.assetReference ? ` · ${p.assetReference}` : ""}</div></td>
                <td style={td}>{lookups.clientName(p.clientId)}</td>
                <td style={td}>{lookups.insurerName(p.insurerId)}</td>
                <td style={td}>{p.startDate ? `${day(p.startDate)} – ${day(p.expiryDate)}` : "Dates not set"}<ExpiryNote status={p.status} expiry={p.expiryDate} /></td>
                <td style={{ ...td, textAlign: "right" }}>{money(p.premiumAmount)}{p.premiumFrequency && <div style={muted}>{labelOf(FREQUENCIES, p.premiumFrequency).toLowerCase()}</div>}</td>
                <td style={td}><StatusPill status={p.status} /></td>
              </tr>))}</tbody>
          </table>)}
      </div>
      {capped && <div style={{ ...muted, marginTop: 8 }}>Showing the first {data?.content.length} active policies only.</div>}
      {!due && <Pager page={page} pages={data?.totalPages ?? 0} onPage={setPage} />}
      {open && <Detail policy={open} canWrite={canWrite} lookups={lookups} onClose={() => setOpenId(null)} onEdit={() => { setEditing(open); setOpenId(null) }} onOpen={setOpenId} />}
      {editing && <PolicyDialog policy={editing === "new" ? null : editing} lookups={lookups} onClose={() => setEditing(null)} />}
    </div>
  )
}

function useRefresh() { const qc = useQueryClient(); return () => { for (const k of ["ib-policies", "ib-chain", "ib-client-policies", "ib-commission", "ib-client-invoices"]) qc.invalidateQueries({ queryKey: [k] }) } }

function Detail({ policy: p, canWrite, lookups, onClose, onEdit, onOpen }:
  { policy: Policy; canWrite: boolean; lookups: ReturnType<typeof useLookups>; onClose: () => void; onEdit: () => void; onOpen: (id: string) => void }) {
  const refresh = useRefresh()
  const [dialog, setDialog] = useState<"bind" | "cancel" | "renew" | "activate" | null>(null)
  const actions = canWrite ? actionsFor(p.status, true) : []
  const simple = useMutation({
    mutationFn: async (path: "lapse" | "reinstate") => (await apiClient.post(`${P}/${p.id}/${path}`)).data as Policy,
    onSuccess: (_n, path) => { toast.success(path === "lapse" ? "Marked as lapsed" : "Policy reinstated"); refresh() },
    onError: e => { void fail(e, "That did not work") },
  })
  const activate = useMutation({
    mutationFn: async () => (await apiClient.post(`${P}/${p.id}/activate`)).data,
    onSuccess: () => { toast.success("Policy activated — commission invoice raised"); refresh(); setDialog(null) },
    onError: e => { void fail(e, "Could not activate the policy") },
  })
  return (
    <Dialog title={<>{label(p)} <StatusPill status={p.status} /></>} onClose={onClose} width={660}
      footer={<>
        {actions.includes("edit") && <Btn onClick={onEdit}>Edit</Btn>}
        {actions.includes("bind") && <Btn kind="primary" onClick={() => setDialog("bind")}>Bind</Btn>}
        {actions.includes("activate") && <Btn kind="primary" onClick={() => setDialog("activate")}>Activate</Btn>}
        {actions.includes("lapse") && <Btn disabled={simple.isPending} onClick={() => simple.mutate("lapse")}>Mark lapsed</Btn>}
        {actions.includes("reinstate") && <Btn disabled={simple.isPending} onClick={() => simple.mutate("reinstate")}>Reinstate</Btn>}
        {actions.includes("renew") && <Btn kind="primary" onClick={() => setDialog("renew")}>Renew</Btn>}
        {actions.includes("cancel") && <Btn kind="danger" onClick={() => setDialog("cancel")}>Cancel</Btn>}
      </>}>
      <Row k="Client">{lookups.clientName(p.clientId)}</Row>
      <Row k="Insurer">{lookups.insurerName(p.insurerId)}</Row>
      <Row k="Quote reference">{p.quoteReference ?? ""}</Row>
      <Row k="Cover">{labelOf(LINES, p.lineOfBusiness)}</Row>
      <Row k="Asset">{[p.assetType && labelOf(ASSET_TYPES, p.assetType), p.assetReference].filter(Boolean).join(" · ")}</Row>
      <Row k="Term">{p.startDate ? <>{day(p.startDate)} – {day(p.expiryDate)}<ExpiryNote status={p.status} expiry={p.expiryDate} /></> : ""}</Row>
      <Row k="Premium">{p.premiumAmount != null ? `${money(p.premiumAmount)} ${p.premiumFrequency ? labelOf(FREQUENCIES, p.premiumFrequency).toLowerCase() : ""}` : ""}</Row>
      <Row k="Sum insured">{p.sumInsured != null ? money(p.sumInsured) : ""}</Row>
      <Row k="Excess">{p.excessAmount != null ? money(p.excessAmount) : ""}</Row>
      <Row k="Commission">{p.commissionRatePct != null ? `${p.commissionRatePct}%` : ""}</Row>
      {p.boundAt && <Row k="Bound">{day(p.boundAt)}</Row>}
      {p.activatedAt && <Row k="Cover started">{day(p.activatedAt)}</Row>}
      {p.status === "CANCELLED" && <Row k="Cancelled">{day(p.cancelledDate)}{p.cancelReason ? ` — ${p.cancelReason}` : ""}</Row>}
      {p.notes && <Row k="Notes">{p.notes}</Row>}
      {p.policyNumber && (<>
        <div style={{ fontSize: 13, fontWeight: 700, margin: "16px 0 6px" }}>Renewal history</div>
        <RenewalHistory url={`${P}/${p.id}/renewal-chain`} currentId={p.id} />
      </>)}
      {dialog === "bind" && <BindDialog onClose={() => setDialog(null)} onSubmit={async n => { await apiClient.post(`${P}/${p.id}/bind`, { policyNumber: n }); toast.success("Policy bound"); refresh() }} />}
      {dialog === "activate" && (
        <Dialog title="Start cover" onClose={() => setDialog(null)} footer={<><Btn onClick={() => setDialog(null)}>Not yet</Btn><Btn kind="primary" disabled={activate.isPending} onClick={() => activate.mutate()}>{activate.isPending ? "Starting…" : "Start cover"}</Btn></>}>
          <div style={{ lineHeight: 1.5, fontSize: 13 }}>This marks {label(p)} as in force and raises this term's commission invoice. The invoice cannot be undone from here.</div>
        </Dialog>)}
      {dialog === "cancel" && (
        <ReasonDialog title={`Cancel ${label(p)}`} confirm="Cancel policy" danger label="Reason" intro="A cancelled policy cannot be edited or renewed."
          onSubmit={async reason => { await apiClient.post(`${P}/${p.id}/cancel`, { reason }); toast.success("Policy cancelled"); refresh() }} onClose={() => setDialog(null)} />)}
      {dialog === "renew" && (
        <RenewDialog policy={p} onClose={() => setDialog(null)}
          onSubmit={async req => { const r = await apiClient.post(`${P}/${p.id}/renew`, req); toast.success("Policy renewed — new commission invoice raised"); refresh(); const id = (r.data?.data ?? r.data)?.id; if (id) onOpen(id) }} />)}
    </Dialog>
  )
}

function BindDialog({ onSubmit, onClose }: { onSubmit: (policyNumber: string) => Promise<unknown>; onClose: () => void }) {
  const [n, setN] = useState(""), [busy, setBusy] = useState(false), [err, setErr] = useState("")
  const go = async () => { setBusy(true); setErr(""); try { await onSubmit(n.trim()); onClose() } catch (e) { setErr(await errorText(e, "Could not bind the policy")) } finally { setBusy(false) } }
  return (
    <Dialog title="Bind the policy" onClose={onClose} footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!n.trim() || busy} onClick={go}>{busy ? "Binding…" : "Bind"}</Btn></>}>
      <div style={{ ...muted, marginBottom: 12, lineHeight: 1.5 }}>The insurer has accepted the risk. Enter the policy number they issued.</div>
      <Input label="Policy number" value={n} onChange={setN} autoFocus maxLength={100} />
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

function PolicyDialog({ policy, lookups, onClose }: { policy: Policy | null; lookups: ReturnType<typeof useLookups>; onClose: () => void }) {
  const creating = !policy
  const refresh = useRefresh()
  const [f, setF] = useState<BrokerPolicyForm>(() => policy ? {
    clientId: policy.clientId, insurerId: policy.insurerId, quoteReference: policy.quoteReference ?? "", lineOfBusiness: policy.lineOfBusiness, assetType: policy.assetType ?? "", assetReference: policy.assetReference ?? "",
    sumInsured: policy.sumInsured != null ? String(policy.sumInsured) : "", premiumAmount: policy.premiumAmount != null ? String(policy.premiumAmount) : "", premiumFrequency: policy.premiumFrequency ?? "MONTHLY",
    excessAmount: policy.excessAmount != null ? String(policy.excessAmount) : "", commissionRatePct: policy.commissionRatePct != null ? String(policy.commissionRatePct) : "",
    startDate: policy.startDate ?? "", expiryDate: policy.expiryDate ?? "", notes: policy.notes ?? "",
  } : emptyBrokerPolicy())
  const [err, setErr] = useState("")
  const set = (k: keyof BrokerPolicyForm) => (v: string) => setF(s => ({ ...s, [k]: v }))
  // Choosing a client fills in their usual commission, unless one has already been typed.
  const pickClient = (id: string) => setF(s => {
    const rate = lookups.clients.find(c => c.id === id)?.defaultCommissionRatePct
    return { ...s, clientId: id, commissionRatePct: s.commissionRatePct === "" && rate != null ? String(rate) : s.commissionRatePct }
  })
  const problem = brokerPolicyProblem(f, creating)
  const save = useMutation({
    mutationFn: () => creating ? apiClient.post(P, brokerPolicyRequest(f, true)) : apiClient.put(`${P}/${policy!.id}`, brokerPolicyRequest(f, false)),
    onSuccess: () => { toast.success(creating ? "Quote created" : "Policy saved"); refresh(); onClose() },
    onError: async e => setErr(await errorText(e, "Could not save the policy")),
  })
  const clientOptions = lookups.clients.filter(c => c.active || c.id === f.clientId).map(c => [c.id, c.clientName] as const)
  const insurerOptions = lookups.insurers.filter(i => i.active || i.id === f.insurerId).map(i => [i.id, i.name] as const)
  return (
    <Dialog title={creating ? "New quote" : `Edit ${label(policy!)}`} onClose={onClose} width={640}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Save"}</Btn></>}>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 12px" }}>
        {creating ? <Pick label="Client" value={f.clientId} onChange={pickClient} options={clientOptions} blank="Choose a client" /> : <Row k="Client">{lookups.clientName(f.clientId)}</Row>}
        <Pick label="Insurer" value={f.insurerId} onChange={set("insurerId")} options={insurerOptions} blank="Choose an insurer" />
        {creating && <Input label="Quote reference" value={f.quoteReference} onChange={set("quoteReference")} />}
        <Pick label="Type of cover" value={f.lineOfBusiness} onChange={set("lineOfBusiness")} options={LINES} />
        <Pick label="Asset type" value={f.assetType} onChange={set("assetType")} options={ASSET_TYPES} blank="Not specified" />
        <Input label="Asset (registration, address…)" value={f.assetReference} onChange={set("assetReference")} />
        <Input label="Premium (R)" type="number" min="0" step="0.01" value={f.premiumAmount} onChange={set("premiumAmount")} />
        <Pick label="Paid" value={f.premiumFrequency} onChange={set("premiumFrequency")} options={FREQUENCIES} />
        <Input label="Sum insured (R)" type="number" min="0" step="0.01" value={f.sumInsured} onChange={set("sumInsured")} />
        <Input label="Excess (R)" type="number" min="0" step="0.01" value={f.excessAmount} onChange={set("excessAmount")} />
        <Input label="Commission (%)" type="number" min="0" max="100" step="0.01" value={f.commissionRatePct} onChange={set("commissionRatePct")} />
        <div />
        <Input label="Starts" type="date" value={f.startDate} onChange={set("startDate")} />
        <Input label="Ends" type="date" value={f.expiryDate} onChange={set("expiryDate")} />
      </div>
      <Input label="Notes" value={f.notes} onChange={set("notes")} />
      {problem && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
