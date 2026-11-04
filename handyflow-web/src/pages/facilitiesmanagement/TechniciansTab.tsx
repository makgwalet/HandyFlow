// The firm's own technicians. Linking one to a user account is what lets that person see and work their jobs.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, Empty, ErrorLine, Input, Pager, Pick, Pill, SectionTitle, card, errorText, fail, muted, pageOf, td, th } from "./kit"
import { BASE } from "./lookups"
import { emptyTech, techBody, techProblem, type TechForm } from "./fm.logic"

interface Tech { id: string; name: string; contactPhone: string | null; contactEmail: string | null; specialization: string | null; linkedUserId: string | null; active: boolean }
interface UserRow { id: string; firstName?: string; lastName?: string; email?: string }
const userName = (u: UserRow) => [u.firstName, u.lastName].filter(Boolean).join(" ") || u.email || u.id

function useUsers() {
  return useQuery({ queryKey: ["fm-users"], retry: false, queryFn: async () => { const d = (await apiClient.get<UserRow[]>("/api/v1/identity/users")).data; return Array.isArray(d) ? d : [] } })
}

export default function TechniciansTab() {
  const qc = useQueryClient(), users = useUsers()
  const canWrite = usePermission("FACILITIESMANAGEMENT_MANAGE") || usePermission("FACILITIESMANAGEMENT_ADMIN")
  const [page, setPage] = useState(0), [editing, setEditing] = useState<Tech | "new" | null>(null)
  const { data, isLoading, isError } = useQuery({ queryKey: ["fm-technicians", page], queryFn: async () => pageOf<Tech>(await apiClient.get(`${BASE}/technicians`, { params: { page, size: 25 } })) })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["fm-technicians"] }); qc.invalidateQueries({ queryKey: ["fm-technicians-all"] }) }
  const toggle = useMutation({
    mutationFn: (t: Tech) => apiClient.post(`${BASE}/technicians/${t.id}/${t.active ? "deactivate" : "reactivate"}`),
    onSuccess: refresh, onError: e => { void fail(e, "That did not work") },
  })
  const rows = data?.content ?? []
  const who = (id: string | null) => id ? (users.data ?? []).find(u => u.id === id) : undefined
  return (
    <div>
      <SectionTitle right={canWrite && <Btn kind="primary" onClick={() => setEditing("new")}>Add technician</Btn>}>Technicians</SectionTitle>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {isError ? <Empty>Could not load the technicians. Please try again.</Empty> : isLoading ? <Empty>Loading…</Empty> : rows.length === 0 ? <Empty>No technicians yet.</Empty> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Technician</th><th style={th}>Contact</th><th style={th}>Signs in as</th><th style={th}>Status</th><th /></tr></thead>
            <tbody>{rows.map(t => (
              <tr key={t.id} style={{ opacity: t.active ? 1 : 0.6 }}>
                <td style={td}><b>{t.name}</b><div style={muted}>{t.specialization}</div></td>
                <td style={td}>{t.contactPhone ?? "—"}<div style={muted}>{t.contactEmail}</div></td>
                <td style={td}>{t.linkedUserId ? (who(t.linkedUserId) ? userName(who(t.linkedUserId)!) : "A user account") : <span style={muted}>Not linked</span>}</td>
                <td style={td}><Pill tone={t.active ? "success" : "neutral"}>{t.active ? "Active" : "Inactive"}</Pill></td>
                <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}>{canWrite && <><Btn small onClick={() => setEditing(t)}>Edit</Btn>{" "}<Btn small disabled={toggle.isPending} onClick={() => toggle.mutate(t)}>{t.active ? "Deactivate" : "Reactivate"}</Btn></>}</td>
              </tr>))}</tbody>
          </table>)}
      </div>
      <Pager page={page} pages={data?.totalPages ?? 0} onPage={setPage} />
      {editing && <TechDialog tech={editing === "new" ? null : editing} users={users.data ?? []} usersFailed={users.isError} onClose={() => setEditing(null)} onSaved={refresh} />}
    </div>
  )
}

function TechDialog({ tech, users, usersFailed, onClose, onSaved }: { tech: Tech | null; users: UserRow[]; usersFailed: boolean; onClose: () => void; onSaved: () => void }) {
  const [f, setF] = useState<TechForm>(() => tech ? { name: tech.name, contactPhone: tech.contactPhone ?? "", contactEmail: tech.contactEmail ?? "", specialization: tech.specialization ?? "", linkedUserId: tech.linkedUserId ?? "" } : emptyTech())
  const [err, setErr] = useState("")
  const set = (k: keyof TechForm) => (v: string) => setF(s => ({ ...s, [k]: v }))
  const problem = techProblem(f)
  const save = useMutation({
    mutationFn: () => tech ? apiClient.put(`${BASE}/technicians/${tech.id}`, techBody(f)) : apiClient.post(`${BASE}/technicians`, techBody(f)),
    onSuccess: () => { toast.success(tech ? "Technician saved" : "Technician added"); onSaved(); onClose() },
    onError: async e => setErr(await errorText(e, "Could not save the technician")),
  })
  return (
    <Dialog title={tech ? `Edit ${tech.name}` : "Add a technician"} onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Save"}</Btn></>}>
      <Input label="Name *" value={f.name} onChange={set("name")} autoFocus maxLength={255} />
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 12px" }}>
        <Input label="Speciality" value={f.specialization} onChange={set("specialization")} />
        <Input label="Phone" value={f.contactPhone} onChange={set("contactPhone")} />
        <Input label="Email" type="email" value={f.contactEmail} onChange={set("contactEmail")} />
      </div>
      {usersFailed
        ? <div style={muted}>{f.linkedUserId ? "Linked to a user account." : "Linking to a user account needs permission to see users."}</div>
        : <Pick label="Signs in as" value={f.linkedUserId} onChange={set("linkedUserId")} options={users.map(u => [u.id, userName(u)] as const)} blank="Not linked to a user" />}
      <div style={muted}>A user can be linked to one active technician. The link decides which jobs that person sees.</div>
      {problem && f.name.trim() && <div style={{ ...muted, marginTop: 6 }}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
