// The business's own insurance policies on its assets: find one, add one, correct it, lapse / reinstate / cancel it, and renew it.
import { useMemo, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, Empty, ErrorLine, ReasonDialog, SectionTitle, card, day, fail, inp, muted, th, td } from "../projects/tabs/pmKit"
import {
  ASSET_TYPES, FREQUENCIES, LINES, STATUS_LABEL, actionsFor, daysToExpiry, emptyPolicy, labelOf, policyProblem, policyRequest, EXPIRY_WINDOW, type PolicyForm,
} from "./insurance.logic"
import { ExpiryNote, Input, Pager, Pick, RenewDialog, RenewalHistory, Row, StatusPill, money, pageOf } from "./parts"
import { errorText } from "../../api/projectDocs"

interface Policy {
  id: string; policyNumber: string; insurerName: string; lineOfBusiness: string; assetType: string | null; assetReference: string | null; sumInsured: number | null
  premiumAmount: number; premiumFrequency: string; excessAmount: number | null; brokerOrInsurerContact: string | null; startDate: string; expiryDate: string
  status: string; renewalOfPolicyId: string | null; cancelledDate: string | null; cancelReason: string | null; notes: string | null
}
const BASE = "/api/v1/insurance/policies"
const STATUSES = ["ACTIVE", "LAPSED", "CANCELLED", "EXPIRED", "RENEWED"]

export default function PoliciesTab() {
  const canWrite = usePermission("INSURANCE_MANAGE") || usePermission("INSURANCE_ADMIN")
  const [status, setStatus] = useState(""), [line, setLine] = useState(""), [search, setSearch] = useState(""), [page, setPage] = useState(0), [due, setDue] = useState(false)
  const [open, setOpen] = useState<Policy | null>(null), [editing, setEditing] = useState<Policy | "new" | null>(null)
  const params = due ? { status: "ACTIVE", size: 200 } : { status: status || undefined, lineOfBusiness: line || undefined, search: search.trim() || undefined, page, size: 20 }
  const { data, isLoading, isError } = useQuery({ queryKey: ["ins-policies", params], queryFn: async () => pageOf<Policy>(await apiClient.get(BASE, { params })) })
  const rows = useMemo(() => {
    const all = data?.content ?? []
    return due ? all.filter(p => (daysToExpiry(p.expiryDate) ?? 999) <= EXPIRY_WINDOW).sort((a, b) => a.expiryDate.localeCompare(b.expiryDate)) : all
  }, [data, due])
  const capped = due && (data?.totalElements ?? 0) > (data?.content.length ?? 0)

  return (
    <div>
      <SectionTitle right={canWrite && <Btn kind="primary" onClick={() => setEditing("new")}>Add policy</Btn>}>Policies</SectionTitle>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 12, alignItems: "center" }}>
        <input aria-label="Search policies" placeholder="Search number, insurer or asset" value={search} disabled={due} onChange={e => { setSearch(e.target.value); setPage(0) }} style={{ ...inp, width: 240 }} />
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
            <thead><tr><th style={th}>Policy</th><th style={th}>Insurer</th><th style={th}>Cover</th><th style={th}>Term</th><th style={{ ...th, textAlign: "right" }}>Premium</th><th style={th}>Status</th></tr></thead>
            <tbody>{rows.map(p => (
              <tr key={p.id} onClick={() => setOpen(p)} style={{ cursor: "pointer" }}>
                <td style={td}><b>{p.policyNumber}</b>{p.assetReference && <div style={muted}>{p.assetReference}</div>}</td>
                <td style={td}>{p.insurerName}</td>
                <td style={td}>{labelOf(LINES, p.lineOfBusiness)}</td>
                <td style={td}>{day(p.startDate)} – {day(p.expiryDate)}<ExpiryNote status={p.status} expiry={p.expiryDate} /></td>
                <td style={{ ...td, textAlign: "right" }}>{money(p.premiumAmount)}<div style={muted}>{labelOf(FREQUENCIES, p.premiumFrequency).toLowerCase()}</div></td>
                <td style={td}><StatusPill status={p.status} /></td>
              </tr>))}</tbody>
          </table>)}
      </div>
      {capped && <div style={{ ...muted, marginTop: 8 }}>Showing the first {data?.content.length} active policies only.</div>}
      {!due && <Pager page={page} pages={data?.totalPages ?? 0} onPage={setPage} />}
      {open && <Detail policy={open} canWrite={canWrite} onClose={() => setOpen(null)} onEdit={() => { setEditing(open); setOpen(null) }} onChanged={p => setOpen(p)} />}
      {editing && <PolicyDialog policy={editing === "new" ? null : editing} onClose={() => setEditing(null)} />}
    </div>
  )
}

function useRefresh() { const qc = useQueryClient(); return () => { qc.invalidateQueries({ queryKey: ["ins-policies"] }); qc.invalidateQueries({ queryKey: ["ins-chain"] }) } }

function Detail({ policy: p, canWrite, onClose, onEdit, onChanged }: { policy: Policy; canWrite: boolean; onClose: () => void; onEdit: () => void; onChanged: (p: Policy) => void }) {
  const refresh = useRefresh()
  const [dialog, setDialog] = useState<"cancel" | "renew" | null>(null)
  const actions = canWrite ? actionsFor(p.status, false) : []
  const act = useMutation({
    mutationFn: async (path: "lapse" | "reinstate") => (await apiClient.post(`${BASE}/${p.id}/${path}`)).data as Policy,
    onSuccess: (np, path) => { toast.success(path === "lapse" ? "Marked as lapsed" : "Policy reinstated"); refresh(); onChanged(np) },
    onError: e => { void fail(e, "That did not work") },
  })
  return (
    <Dialog title={<>{p.policyNumber} <StatusPill status={p.status} /></>} onClose={onClose} width={640}
      footer={<>
        {actions.includes("edit") && <Btn onClick={onEdit}>Edit</Btn>}
        {actions.includes("lapse") && <Btn disabled={act.isPending} onClick={() => act.mutate("lapse")}>Mark lapsed</Btn>}
        {actions.includes("reinstate") && <Btn disabled={act.isPending} onClick={() => act.mutate("reinstate")}>Reinstate</Btn>}
        {actions.includes("renew") && <Btn kind="primary" onClick={() => setDialog("renew")}>Renew</Btn>}
        {actions.includes("cancel") && <Btn kind="danger" onClick={() => setDialog("cancel")}>Cancel policy</Btn>}
      </>}>
      <Row k="Insurer">{p.insurerName}</Row>
      <Row k="Cover">{labelOf(LINES, p.lineOfBusiness)}</Row>
      <Row k="Asset">{[p.assetType && labelOf(ASSET_TYPES, p.assetType), p.assetReference].filter(Boolean).join(" · ")}</Row>
      <Row k="Term">{day(p.startDate)} – {day(p.expiryDate)}<ExpiryNote status={p.status} expiry={p.expiryDate} /></Row>
      <Row k="Premium">{money(p.premiumAmount)} {labelOf(FREQUENCIES, p.premiumFrequency).toLowerCase()}</Row>
      <Row k="Sum insured">{p.sumInsured != null ? money(p.sumInsured) : ""}</Row>
      <Row k="Excess">{p.excessAmount != null ? money(p.excessAmount) : ""}</Row>
      <Row k="Broker / contact">{p.brokerOrInsurerContact ?? ""}</Row>
      {p.status === "CANCELLED" && <Row k="Cancelled">{day(p.cancelledDate)}{p.cancelReason ? ` — ${p.cancelReason}` : ""}</Row>}
      {p.notes && <Row k="Notes">{p.notes}</Row>}
      <div style={{ fontSize: 13, fontWeight: 700, margin: "16px 0 6px" }}>Renewal history</div>
      <RenewalHistory url={`${BASE}/${p.id}/renewal-chain`} currentId={p.id} />
      {dialog === "cancel" && (
        <ReasonDialog title={`Cancel ${p.policyNumber}`} confirm="Cancel policy" danger required={false} label="Reason" intro="Cover ends today. A cancelled policy cannot be edited or renewed."
          onSubmit={async reason => { const r = await apiClient.post(`${BASE}/${p.id}/cancel`, { reason: reason || null }); toast.success("Policy cancelled"); refresh(); onChanged(r.data as Policy) }} onClose={() => setDialog(null)} />)}
      {dialog === "renew" && (
        <RenewDialog policy={p} onClose={() => setDialog(null)}
          onSubmit={async req => { const r = await apiClient.post(`${BASE}/${p.id}/renew`, req); toast.success("Policy renewed"); refresh(); onChanged(r.data as Policy) }} />)}
    </Dialog>
  )
}

function PolicyDialog({ policy, onClose }: { policy: Policy | null; onClose: () => void }) {
  const creating = !policy
  const refresh = useRefresh()
  const [f, setF] = useState<PolicyForm>(() => policy ? {
    policyNumber: policy.policyNumber, insurerName: policy.insurerName, lineOfBusiness: policy.lineOfBusiness, assetType: policy.assetType ?? "", assetReference: policy.assetReference ?? "",
    sumInsured: policy.sumInsured != null ? String(policy.sumInsured) : "", premiumAmount: String(policy.premiumAmount), premiumFrequency: policy.premiumFrequency,
    excessAmount: policy.excessAmount != null ? String(policy.excessAmount) : "", brokerOrInsurerContact: policy.brokerOrInsurerContact ?? "", startDate: policy.startDate, expiryDate: policy.expiryDate, notes: policy.notes ?? "",
  } : emptyPolicy())
  const [err, setErr] = useState("")
  const set = (k: keyof PolicyForm) => (v: string) => setF(s => ({ ...s, [k]: v }))
  const problem = policyProblem(f, creating)
  const save = useMutation({
    mutationFn: () => creating ? apiClient.post(BASE, policyRequest(f, true)) : apiClient.put(`${BASE}/${policy!.id}`, policyRequest(f, false)),
    onSuccess: () => { toast.success(creating ? "Policy added" : "Policy saved"); refresh(); onClose() },
    onError: async e => setErr(await errorText(e, "Could not save the policy")),
  })
  return (
    <Dialog title={creating ? "Add a policy" : `Edit ${policy!.policyNumber}`} onClose={onClose} width={620}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Save"}</Btn></>}>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 12px" }}>
        {creating ? <Input label="Policy number" value={f.policyNumber} onChange={set("policyNumber")} autoFocus maxLength={100} /> : <Row k="Policy number">{f.policyNumber}</Row>}
        <Input label="Insurer" value={f.insurerName} onChange={set("insurerName")} maxLength={200} />
        {creating ? <Pick label="Type of cover" value={f.lineOfBusiness} onChange={set("lineOfBusiness")} options={LINES} /> : <Row k="Cover">{labelOf(LINES, f.lineOfBusiness)}</Row>}
        <Pick label="Asset type" value={f.assetType} onChange={set("assetType")} options={ASSET_TYPES} blank="Not specified" />
        <Input label="Asset (registration, address…)" value={f.assetReference} onChange={set("assetReference")} />
        <Input label="Broker / insurer contact" value={f.brokerOrInsurerContact} onChange={set("brokerOrInsurerContact")} />
        <Input label="Premium (R)" type="number" min="0" step="0.01" value={f.premiumAmount} onChange={set("premiumAmount")} />
        <Pick label="Paid" value={f.premiumFrequency} onChange={set("premiumFrequency")} options={FREQUENCIES} />
        <Input label="Sum insured (R)" type="number" min="0" step="0.01" value={f.sumInsured} onChange={set("sumInsured")} />
        <Input label="Excess (R)" type="number" min="0" step="0.01" value={f.excessAmount} onChange={set("excessAmount")} />
        <Input label="Starts" type="date" value={f.startDate} onChange={set("startDate")} />
        <Input label="Ends" type="date" value={f.expiryDate} onChange={set("expiryDate")} />
      </div>
      <Input label="Notes" value={f.notes} onChange={set("notes")} />
      {problem && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
