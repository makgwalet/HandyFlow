// The people and businesses this brokerage places cover for. Opening one shows their policies and what they owe in commission.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { errorText } from "../../api/projectDocs"
import { usePermission } from "../../hooks/usePermission"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, Empty, ErrorLine, Pill, SectionTitle, card, day, fail, muted, th, td } from "../projects/tabs/pmKit"
import { ASSET_TYPES, LINES, clientProblem, clientRequest, emptyClient, labelOf, type ClientForm } from "../insurance/insurance.logic"
import { ExpiryNote, Input, Pager, Pick, Row, StatusPill, money } from "../insurance/parts"
import { BASE, pageOf, useLookups, type BClient } from "./lookups"

const TYPES = [["COMMERCIAL", "Business"], ["INDIVIDUAL", "Individual"]] as const

export default function ClientsTab() {
  const qc = useQueryClient()
  const canWrite = usePermission("INSURANCEBROKERAGE_MANAGE") || usePermission("INSURANCEBROKERAGE_ADMIN")
  const [page, setPage] = useState(0), [editing, setEditing] = useState<BClient | "new" | null>(null), [open, setOpen] = useState<BClient | null>(null)
  const { data, isLoading, isError } = useQuery({ queryKey: ["ib-clients", page], queryFn: async () => pageOf<BClient>(await apiClient.get(`${BASE}/clients`, { params: { page, size: 24 } })) })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["ib-clients"] }); qc.invalidateQueries({ queryKey: ["ib-clients-all"] }) }
  const toggle = useMutation({
    mutationFn: (c: BClient) => apiClient.post(`${BASE}/clients/${c.id}/${c.active ? "deactivate" : "reactivate"}`),
    onSuccess: refresh, onError: e => { void fail(e, "That did not work") },
  })
  const rows = data?.content ?? []
  return (
    <div>
      <SectionTitle right={canWrite && <Btn kind="primary" onClick={() => setEditing("new")}>Add client</Btn>}>Clients</SectionTitle>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {isError ? <Empty>Could not load the clients. Please try again.</Empty> : isLoading ? <Empty>Loading…</Empty> : rows.length === 0 ? <Empty>No clients yet.</Empty> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Client</th><th style={th}>Contact</th><th style={{ ...th, textAlign: "right" }}>Usual commission</th><th style={th}>Status</th><th /></tr></thead>
            <tbody>{rows.map(c => (
              <tr key={c.id} onClick={() => setOpen(c)} style={{ cursor: "pointer", opacity: c.active ? 1 : 0.6 }}>
                <td style={td}><b>{c.clientName}</b><div style={muted}>{labelOf(TYPES, c.clientType)}{c.registrationOrIdNumber ? ` · ${c.registrationOrIdNumber}` : ""}</div></td>
                <td style={td}>{c.contactName ?? "—"}<div style={muted}>{[c.contactEmail, c.contactPhone].filter(Boolean).join(" · ")}</div></td>
                <td style={{ ...td, textAlign: "right" }}>{c.defaultCommissionRatePct != null ? `${c.defaultCommissionRatePct}%` : "—"}</td>
                <td style={td}><Pill tone={c.active ? "success" : "neutral"}>{c.active ? "Active" : "Inactive"}</Pill></td>
                <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }} onClick={e => e.stopPropagation()}>{canWrite && <>
                  <Btn small onClick={() => setEditing(c)}>Edit</Btn>{" "}
                  <Btn small disabled={toggle.isPending} onClick={() => toggle.mutate(c)}>{c.active ? "Deactivate" : "Reactivate"}</Btn></>}</td>
              </tr>))}</tbody>
          </table>)}
      </div>
      <Pager page={page} pages={data?.totalPages ?? 0} onPage={setPage} />
      {open && <ClientDetail client={open} onClose={() => setOpen(null)} />}
      {editing && <ClientDialog client={editing === "new" ? null : editing} onClose={() => setEditing(null)} onSaved={refresh} />}
    </div>
  )
}

interface P { id: string; policyNumber: string | null; quoteReference: string | null; insurerId: string; lineOfBusiness: string; assetType: string | null; assetReference: string | null; startDate: string | null; expiryDate: string | null; premiumAmount: number | null; status: string }
interface Inv { id: string; invoiceNumber: string; invoiceDate: string; dueDate: string | null; total: number; balance: number; status: string }

function ClientDetail({ client: c, onClose }: { client: BClient; onClose: () => void }) {
  const { insurerName } = useLookups()
  const policies = useQuery({ queryKey: ["ib-client-policies", c.id], queryFn: async () => pageOf<P>(await apiClient.get(`${BASE}/policies/clients/${c.id}`, { params: { size: 50 } })) })
  const invoices = useQuery({ queryKey: ["ib-client-invoices", c.id], queryFn: async () => pageOf<Inv>(await apiClient.get(`${BASE}/clients/${c.id}/commission-invoices`, { params: { size: 50 } })) })
  return (
    <Dialog title={c.clientName} onClose={onClose} width={720}>
      <Row k="Type">{labelOf(TYPES, c.clientType)}</Row>
      <Row k="Registration / ID">{c.registrationOrIdNumber ?? ""}</Row>
      <Row k="Contact">{[c.contactName, c.contactEmail, c.contactPhone].filter(Boolean).join(" · ")}</Row>
      <Row k="Address">{c.address ?? ""}</Row>
      {c.notes && <Row k="Notes">{c.notes}</Row>}
      <div style={{ fontSize: 13, fontWeight: 700, margin: "16px 0 6px" }}>Policies</div>
      {policies.isError ? <div style={muted}>Could not load the policies.</div> : (policies.data?.content ?? []).length === 0 ? <div style={muted}>{policies.isLoading ? "Loading…" : "No policies yet."}</div> : (
        <div style={{ overflowX: "auto" }}><table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr><th style={th}>Policy</th><th style={th}>Insurer</th><th style={th}>Term</th><th style={th}>Status</th></tr></thead>
          <tbody>{policies.data!.content.map(p => (
            <tr key={p.id}>
              <td style={td}><b>{p.policyNumber ?? p.quoteReference ?? "Quote"}</b><div style={muted}>{labelOf(LINES, p.lineOfBusiness)}{p.assetReference ? ` · ${p.assetReference}` : p.assetType ? ` · ${labelOf(ASSET_TYPES, p.assetType)}` : ""}</div></td>
              <td style={td}>{insurerName(p.insurerId)}</td>
              <td style={td}>{p.startDate ? `${day(p.startDate)} – ${day(p.expiryDate)}` : "Dates not set"}<ExpiryNote status={p.status} expiry={p.expiryDate} /></td>
              <td style={td}><StatusPill status={p.status} /></td>
            </tr>))}</tbody></table></div>)}
      <div style={{ fontSize: 13, fontWeight: 700, margin: "16px 0 6px" }}>Commission invoices</div>
      {invoices.isError ? <div style={muted}>Could not load the invoices.</div> : (invoices.data?.content ?? []).length === 0 ? <div style={muted}>{invoices.isLoading ? "Loading…" : "None yet. An invoice is raised when a policy is activated or renewed."}</div> : (
        <div style={{ overflowX: "auto" }}><table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr><th style={th}>Invoice</th><th style={th}>Date</th><th style={{ ...th, textAlign: "right" }}>Total</th><th style={{ ...th, textAlign: "right" }}>Balance</th></tr></thead>
          <tbody>{invoices.data!.content.map(i => (
            <tr key={i.id}><td style={td}>{i.invoiceNumber}</td><td style={td}>{day(i.invoiceDate)}</td><td style={{ ...td, textAlign: "right" }}>{money(i.total)}</td><td style={{ ...td, textAlign: "right" }}>{money(i.balance)}</td></tr>))}</tbody></table></div>)}
    </Dialog>
  )
}

function ClientDialog({ client, onClose, onSaved }: { client: BClient | null; onClose: () => void; onSaved: () => void }) {
  const [f, setF] = useState<ClientForm>(() => client ? {
    clientName: client.clientName, clientType: client.clientType, registrationOrIdNumber: client.registrationOrIdNumber ?? "", contactName: client.contactName ?? "", contactEmail: client.contactEmail ?? "",
    contactPhone: client.contactPhone ?? "", address: client.address ?? "", defaultCommissionRatePct: client.defaultCommissionRatePct != null ? String(client.defaultCommissionRatePct) : "", notes: client.notes ?? "",
  } : emptyClient())
  const [err, setErr] = useState("")
  const set = (k: keyof ClientForm) => (v: string) => setF(s => ({ ...s, [k]: v }))
  const problem = clientProblem(f)
  const save = useMutation({
    mutationFn: () => client ? apiClient.put(`${BASE}/clients/${client.id}`, clientRequest(f)) : apiClient.post(`${BASE}/clients`, clientRequest(f)),
    onSuccess: () => { toast.success(client ? "Client saved" : "Client added"); onSaved(); onClose() },
    onError: async e => setErr(await errorText(e, "Could not save the client")),
  })
  return (
    <Dialog title={client ? `Edit ${client.clientName}` : "Add a client"} onClose={onClose} width={600}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Save"}</Btn></>}>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 12px" }}>
        <Input label="Name" value={f.clientName} onChange={set("clientName")} autoFocus maxLength={255} />
        <Pick label="Type" value={f.clientType} onChange={set("clientType")} options={TYPES} />
        <Input label="Registration / ID number" value={f.registrationOrIdNumber} onChange={set("registrationOrIdNumber")} />
        <Input label="Usual commission (%)" type="number" min="0" max="100" step="0.01" value={f.defaultCommissionRatePct} onChange={set("defaultCommissionRatePct")} hint="Filled into new quotes for this client" />
        <Input label="Contact person" value={f.contactName} onChange={set("contactName")} />
        <Input label="Contact email" type="email" value={f.contactEmail} onChange={set("contactEmail")} />
        <Input label="Contact phone" value={f.contactPhone} onChange={set("contactPhone")} />
        <Input label="Address" value={f.address} onChange={set("address")} />
      </div>
      <Input label="Notes" value={f.notes} onChange={set("notes")} />
      {problem && f.clientName.trim() && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
