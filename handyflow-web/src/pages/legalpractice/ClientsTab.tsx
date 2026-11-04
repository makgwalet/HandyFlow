// The firm's clients. Opening one shows their matters, invoices and trust balance.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, Empty, ErrorLine, Input, Pager, Pick, Pill, Row, SectionTitle, card, day, errorText, fail, money, muted, pageOf, td, th } from "./kit"
import { BASE, type LClient } from "./lookups"
import { CLIENT_TYPES, INVOICE_STATUS, MATTER_STATUS, clientProblem, clientRequest, emptyClient, labelOf, type ClientForm } from "./legal.logic"

export default function ClientsTab() {
  const qc = useQueryClient()
  const canWrite = usePermission("LEGALPRACTICE_MANAGE") || usePermission("LEGALPRACTICE_ADMIN")
  const [page, setPage] = useState(0), [editing, setEditing] = useState<LClient | "new" | null>(null), [open, setOpen] = useState<LClient | null>(null)
  const { data, isLoading, isError } = useQuery({ queryKey: ["lp-clients", page], queryFn: async () => pageOf<LClient>(await apiClient.get(`${BASE}/clients`, { params: { page, size: 25 } })) })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["lp-clients"] }); qc.invalidateQueries({ queryKey: ["lp-clients-all"] }) }
  const toggle = useMutation({
    mutationFn: (c: LClient) => apiClient.post(`${BASE}/clients/${c.id}/${c.status === "ACTIVE" ? "deactivate" : "reactivate"}`),
    onSuccess: refresh, onError: e => { void fail(e, "That did not work") },
  })
  const rows = data?.content ?? []
  return (
    <div>
      <SectionTitle right={canWrite && <Btn kind="primary" onClick={() => setEditing("new")}>Add client</Btn>}>Clients</SectionTitle>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {isError ? <Empty>Could not load the clients. Please try again.</Empty> : isLoading ? <Empty>Loading…</Empty> : rows.length === 0 ? <Empty>No clients yet.</Empty> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Client</th><th style={th}>Contact</th><th style={{ ...th, textAlign: "right" }}>Trust balance</th><th style={th}>Status</th><th /></tr></thead>
            <tbody>{rows.map(c => (
              <tr key={c.id} onClick={() => setOpen(c)} style={{ cursor: "pointer", opacity: c.status === "ACTIVE" ? 1 : 0.6 }}>
                <td style={td}><b>{c.name}</b><div style={muted}>{labelOf(CLIENT_TYPES, c.clientType)}{c.idOrRegistrationNumber ? ` · ${c.idOrRegistrationNumber}` : ""}</div></td>
                <td style={td}>{c.email ?? "—"}<div style={muted}>{c.phone}</div></td>
                <td style={{ ...td, textAlign: "right" }}>{money(c.trustBalance)}</td>
                <td style={td}><Pill tone={c.status === "ACTIVE" ? "success" : "neutral"}>{c.status === "ACTIVE" ? "Active" : "Inactive"}</Pill></td>
                <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }} onClick={e => e.stopPropagation()}>{canWrite && <><Btn small onClick={() => setEditing(c)}>Edit</Btn>{" "}<Btn small disabled={toggle.isPending} onClick={() => toggle.mutate(c)}>{c.status === "ACTIVE" ? "Deactivate" : "Reactivate"}</Btn></>}</td>
              </tr>))}</tbody>
          </table>)}
      </div>
      <Pager page={page} pages={data?.totalPages ?? 0} onPage={setPage} />
      {open && <ClientDetail client={open} onClose={() => setOpen(null)} />}
      {editing && <ClientDialog client={editing === "new" ? null : editing} onClose={() => setEditing(null)} onSaved={refresh} />}
    </div>
  )
}

interface M { id: string; matterNumber: string; matterName: string; status: string; openedDate: string | null }
interface Inv { id: string; invoiceNumber: string; issueDate: string | null; totalAmount: number; outstandingBalance: number; status: string }
function ClientDetail({ client: c, onClose }: { client: LClient; onClose: () => void }) {
  const matters = useQuery({ queryKey: ["lp-client-matters", c.id], queryFn: async () => pageOf<M>(await apiClient.get(`${BASE}/matters`, { params: { clientId: c.id, size: 100 } })) })
  const invoices = useQuery({ queryKey: ["lp-client-invoices", c.id], queryFn: async () => pageOf<Inv>(await apiClient.get(`${BASE}/invoices`, { params: { clientId: c.id, size: 100 } })) })
  return (
    <Dialog title={c.name} onClose={onClose} width={680}>
      <Row k="Type">{labelOf(CLIENT_TYPES, c.clientType)}</Row>
      <Row k="ID / registration">{c.idOrRegistrationNumber ?? ""}</Row>
      <Row k="Contact">{[c.email, c.phone].filter(Boolean).join(" · ")}</Row>
      <Row k="Trust balance"><b>{money(c.trustBalance)}</b></Row>
      {c.notes && <Row k="Notes">{c.notes}</Row>}
      <div style={{ fontSize: 13, fontWeight: 700, margin: "16px 0 6px" }}>Matters</div>
      {matters.isError ? <div style={muted}>Could not load the matters.</div> : (matters.data?.content ?? []).length === 0 ? <div style={muted}>{matters.isLoading ? "Loading…" : "No matters yet."}</div> : (
        <div style={{ overflowX: "auto" }}><table style={{ width: "100%", borderCollapse: "collapse" }}><tbody>{matters.data!.content.map(m => (
          <tr key={m.id}><td style={td}><b>{m.matterNumber}</b></td><td style={td}>{m.matterName}</td><td style={td}>{day(m.openedDate)}</td><td style={td}><Pill tone={(MATTER_STATUS[m.status] ?? { tone: "neutral" }).tone}>{(MATTER_STATUS[m.status] ?? { label: m.status }).label}</Pill></td></tr>))}</tbody></table></div>)}
      <div style={{ fontSize: 13, fontWeight: 700, margin: "16px 0 6px" }}>Invoices</div>
      {invoices.isError ? <div style={muted}>Could not load the invoices.</div> : (invoices.data?.content ?? []).length === 0 ? <div style={muted}>{invoices.isLoading ? "Loading…" : "No invoices yet."}</div> : (
        <div style={{ overflowX: "auto" }}><table style={{ width: "100%", borderCollapse: "collapse" }}><tbody>{invoices.data!.content.map(i => (
          <tr key={i.id}><td style={td}><b>{i.invoiceNumber}</b></td><td style={td}>{day(i.issueDate)}</td><td style={{ ...td, textAlign: "right" }}>{money(i.totalAmount)}</td><td style={{ ...td, textAlign: "right" }}>{money(i.outstandingBalance)} owing</td><td style={td}><Pill tone={(INVOICE_STATUS[i.status] ?? { tone: "neutral" }).tone}>{(INVOICE_STATUS[i.status] ?? { label: i.status }).label}</Pill></td></tr>))}</tbody></table></div>)}
    </Dialog>
  )
}

function ClientDialog({ client, onClose, onSaved }: { client: LClient | null; onClose: () => void; onSaved: () => void }) {
  const [f, setF] = useState<ClientForm>(() => client ? { name: client.name, email: client.email ?? "", phone: client.phone ?? "", clientType: client.clientType, idOrRegistrationNumber: client.idOrRegistrationNumber ?? "", notes: client.notes ?? "" } : emptyClient())
  const [err, setErr] = useState("")
  const set = (k: keyof ClientForm) => (v: string) => setF(s => ({ ...s, [k]: v }))
  const problem = clientProblem(f)
  const save = useMutation({
    mutationFn: () => client ? apiClient.put(`${BASE}/clients/${client.id}`, clientRequest(f)) : apiClient.post(`${BASE}/clients`, clientRequest(f)),
    onSuccess: () => { toast.success(client ? "Client saved" : "Client added"); onSaved(); onClose() },
    onError: async e => setErr(await errorText(e, "Could not save the client")),
  })
  return (
    <Dialog title={client ? `Edit ${client.name}` : "Add a client"} onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Save"}</Btn></>}>
      <Input label="Name" value={f.name} onChange={set("name")} autoFocus maxLength={255} />
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 12px" }}>
        <Pick label="Type" value={f.clientType} onChange={set("clientType")} options={CLIENT_TYPES} />
        <Input label="ID / registration number" value={f.idOrRegistrationNumber} onChange={set("idOrRegistrationNumber")} />
        <Input label="Email" type="email" value={f.email} onChange={set("email")} />
        <Input label="Phone" value={f.phone} onChange={set("phone")} />
      </div>
      <Input label="Notes" value={f.notes} onChange={set("notes")} />
      {problem && f.name.trim() && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
