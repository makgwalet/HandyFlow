// The firm's clients (the companies whose sites it maintains).
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, Empty, ErrorLine, Input, Pager, Pill, SectionTitle, card, errorText, fail, muted, pageOf, td, th } from "./kit"
import { BASE, type FClient } from "./lookups"
import { clientBody, clientProblem, emptyClient, type ClientForm } from "./fm.logic"

export default function ClientsTab() {
  const qc = useQueryClient()
  const canWrite = usePermission("FACILITIESMANAGEMENT_MANAGE") || usePermission("FACILITIESMANAGEMENT_ADMIN")
  const [page, setPage] = useState(0), [editing, setEditing] = useState<FClient | "new" | null>(null)
  const { data, isLoading, isError } = useQuery({ queryKey: ["fm-clients", page], queryFn: async () => pageOf<FClient>(await apiClient.get(`${BASE}/clients`, { params: { page, size: 25 } })) })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["fm-clients"] }); qc.invalidateQueries({ queryKey: ["fm-clients-all"] }) }
  const toggle = useMutation({
    mutationFn: (c: FClient) => apiClient.post(`${BASE}/clients/${c.id}/${c.status === "ACTIVE" ? "deactivate" : "reactivate"}`),
    onSuccess: refresh, onError: e => { void fail(e, "That did not work") },
  })
  const rows = data?.content ?? []
  return (
    <div>
      <SectionTitle right={canWrite && <Btn kind="primary" onClick={() => setEditing("new")}>Add client</Btn>}>Clients</SectionTitle>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {isError ? <Empty>Could not load the clients. Please try again.</Empty> : isLoading ? <Empty>Loading…</Empty> : rows.length === 0 ? <Empty>No clients yet.</Empty> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Client</th><th style={th}>Contact</th><th style={th}>VAT</th><th style={th}>Status</th><th /></tr></thead>
            <tbody>{rows.map(c => (
              <tr key={c.id} style={{ opacity: c.status === "ACTIVE" ? 1 : 0.6 }}>
                <td style={td}><b>{c.tradingName}</b><div style={muted}>{[c.clientCode, c.registrationNumber].filter(Boolean).join(" · ")}</div></td>
                <td style={td}>{c.contactName ?? "—"}<div style={muted}>{[c.contactEmail, c.contactPhone].filter(Boolean).join(" · ")}</div></td>
                <td style={td}>{c.vatNumber ?? "—"}</td>
                <td style={td}><Pill tone={c.status === "ACTIVE" ? "success" : "neutral"}>{c.status === "ACTIVE" ? "Active" : "Inactive"}</Pill></td>
                <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}>{canWrite && <><Btn small onClick={() => setEditing(c)}>Edit</Btn>{" "}<Btn small disabled={toggle.isPending} onClick={() => toggle.mutate(c)}>{c.status === "ACTIVE" ? "Deactivate" : "Reactivate"}</Btn></>}</td>
              </tr>))}</tbody>
          </table>)}
      </div>
      <Pager page={page} pages={data?.totalPages ?? 0} onPage={setPage} />
      {editing && <ClientDialog client={editing === "new" ? null : editing} onClose={() => setEditing(null)} onSaved={refresh} />}
    </div>
  )
}

function ClientDialog({ client, onClose, onSaved }: { client: FClient | null; onClose: () => void; onSaved: () => void }) {
  const [f, setF] = useState<ClientForm>(() => client ? { tradingName: client.tradingName, registrationNumber: client.registrationNumber ?? "", contactName: client.contactName ?? "", contactEmail: client.contactEmail ?? "", contactPhone: client.contactPhone ?? "", address: client.address ?? "", vatNumber: client.vatNumber ?? "" } : emptyClient())
  const [err, setErr] = useState("")
  const set = (k: keyof ClientForm) => (v: string) => setF(s => ({ ...s, [k]: v }))
  const problem = clientProblem(f)
  const save = useMutation({
    mutationFn: () => client ? apiClient.put(`${BASE}/clients/${client.id}`, clientBody(f)) : apiClient.post(`${BASE}/clients`, clientBody(f)),
    onSuccess: () => { toast.success(client ? "Client saved" : "Client added"); onSaved(); onClose() },
    onError: async e => setErr(await errorText(e, "Could not save the client")),
  })
  return (
    <Dialog title={client ? `Edit ${client.tradingName}` : "Add a client"} onClose={onClose} width={600}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Save"}</Btn></>}>
      <Input label="Trading name" value={f.tradingName} onChange={set("tradingName")} autoFocus maxLength={255} />
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 12px" }}>
        <Input label="Registration number" value={f.registrationNumber} onChange={set("registrationNumber")} />
        <Input label="VAT number" value={f.vatNumber} onChange={set("vatNumber")} hint="10 digits" />
        <Input label="Contact person" value={f.contactName} onChange={set("contactName")} />
        <Input label="Contact phone" value={f.contactPhone} onChange={set("contactPhone")} />
        <Input label="Contact email" type="email" value={f.contactEmail} onChange={set("contactEmail")} />
      </div>
      <Input label="Postal address" value={f.address} onChange={set("address")} />
      {problem && f.tradingName.trim() && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
