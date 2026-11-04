// A client's sites (the buildings and premises the work is done at).
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, Empty, ErrorLine, Input, Pager, Pick, Pill, SectionTitle, card, errorText, fail, inp, muted, pageOf, td, th } from "./kit"
import { BASE, useLookups, type FSite } from "./lookups"
import { SITE_TYPES, addressLine, emptySite, labelOf, siteBody, siteProblem, type SiteForm } from "./fm.logic"

export default function SitesTab() {
  const qc = useQueryClient(), lookups = useLookups()
  const canWrite = usePermission("FACILITIESMANAGEMENT_MANAGE") || usePermission("FACILITIESMANAGEMENT_ADMIN")
  const [page, setPage] = useState(0), [clientId, setClientId] = useState(""), [editing, setEditing] = useState<FSite | "new" | null>(null)
  const { data, isLoading, isError } = useQuery({ queryKey: ["fm-sites", page, clientId], queryFn: async () => pageOf<FSite>(await apiClient.get(`${BASE}/sites`, { params: { page, size: 25, clientId: clientId || undefined } })) })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["fm-sites"] }); qc.invalidateQueries({ queryKey: ["fm-sites-all"] }) }
  const toggle = useMutation({
    mutationFn: (s: FSite) => apiClient.post(`${BASE}/sites/${s.id}/${s.status === "CLOSED" ? "reopen" : "close"}`),
    onSuccess: refresh, onError: e => { void fail(e, "That did not work") },
  })
  const rows = data?.content ?? []
  return (
    <div>
      <SectionTitle right={canWrite && <Btn kind="primary" onClick={() => setEditing("new")}>Add site</Btn>}>Sites</SectionTitle>
      <div style={{ marginBottom: 10 }}>
        <select aria-label="Client" value={clientId} onChange={e => { setClientId(e.target.value); setPage(0) }} style={{ ...inp, width: "auto" }}>
          <option value="">All clients</option>{lookups.clients.map(c => <option key={c.id} value={c.id}>{c.tradingName}</option>)}
        </select>
      </div>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {isError ? <Empty>Could not load the sites. Please try again.</Empty> : isLoading ? <Empty>Loading…</Empty> : rows.length === 0 ? <Empty>No sites yet.</Empty> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Site</th><th style={th}>Client</th><th style={th}>Type</th><th style={th}>Status</th><th /></tr></thead>
            <tbody>{rows.map(s => (
              <tr key={s.id} style={{ opacity: s.status === "CLOSED" ? 0.6 : 1 }}>
                <td style={td}><b>{s.name}</b><div style={muted}>{addressLine(s.address)}</div></td>
                <td style={td}>{lookups.clientName(s.clientId)}</td>
                <td style={td}>{labelOf(SITE_TYPES, s.siteType)}</td>
                <td style={td}><Pill tone={s.status === "CLOSED" ? "neutral" : "success"}>{s.status === "CLOSED" ? "Closed" : "Open"}</Pill></td>
                <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}>{canWrite && <><Btn small onClick={() => setEditing(s)}>Edit</Btn>{" "}<Btn small disabled={toggle.isPending} onClick={() => toggle.mutate(s)}>{s.status === "CLOSED" ? "Reopen" : "Close"}</Btn></>}</td>
              </tr>))}</tbody>
          </table>)}
      </div>
      <Pager page={page} pages={data?.totalPages ?? 0} onPage={setPage} />
      {editing && <SiteDialog site={editing === "new" ? null : editing} clients={lookups.clients.filter(c => c.status === "ACTIVE").map(c => [c.id, c.tradingName] as const)} onClose={() => setEditing(null)} onSaved={refresh} />}
    </div>
  )
}

function SiteDialog({ site, clients, onClose, onSaved }: { site: FSite | null; clients: (readonly [string, string])[]; onClose: () => void; onSaved: () => void }) {
  const creating = !site
  const [f, setF] = useState<SiteForm>(() => site ? { clientId: site.clientId, name: site.name, siteType: site.siteType, street: site.address?.street ?? "", city: site.address?.city ?? "", province: site.address?.province ?? "", postalCode: site.address?.postalCode ?? "", notes: site.notes ?? "" } : emptySite())
  const [err, setErr] = useState("")
  const set = (k: keyof SiteForm) => (v: string) => setF(s => ({ ...s, [k]: v }))
  const problem = siteProblem(f, creating)
  const save = useMutation({
    mutationFn: () => site ? apiClient.put(`${BASE}/sites/${site.id}`, siteBody(f, false)) : apiClient.post(`${BASE}/sites`, siteBody(f, true)),
    onSuccess: () => { toast.success(site ? "Site saved" : "Site added"); onSaved(); onClose() },
    onError: async e => setErr(await errorText(e, "Could not save the site")),
  })
  return (
    <Dialog title={site ? `Edit ${site.name}` : "Add a site"} onClose={onClose} width={600}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Save"}</Btn></>}>
      {creating && <Pick label="Client" value={f.clientId} onChange={set("clientId")} options={clients} blank="Choose a client" />}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 12px" }}>
        <Input label="Site name" value={f.name} onChange={set("name")} autoFocus maxLength={255} />
        <Pick label="Type" value={f.siteType} onChange={set("siteType")} options={SITE_TYPES} />
        <Input label="Street" value={f.street} onChange={set("street")} />
        <Input label="City" value={f.city} onChange={set("city")} />
        <Input label="Province" value={f.province} onChange={set("province")} />
        <Input label="Postal code" value={f.postalCode} onChange={set("postalCode")} />
      </div>
      <Input label="Notes" value={f.notes} onChange={set("notes")} />
      {problem && f.name.trim() && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
