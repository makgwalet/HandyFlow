// Which clients each member of staff may see. No restriction means every client.
import { useEffect, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { toast } from "../../store/toast.store"
import { Btn, Empty, ErrorLine, Pick, SectionTitle, card, errorText, muted } from "./kit"
import { BASE, useLookups } from "./lookups"
import { accessSummary, accessWarning } from "./fm.logic"

interface UserRow { id: string; firstName?: string; lastName?: string; email?: string }
interface Access { userId: string; restricted: boolean; clientIds: string[] }
const nameOf = (u: UserRow) => [u.firstName, u.lastName].filter(Boolean).join(" ") || u.email || u.id

export default function ClientAccessTab() {
  const qc = useQueryClient(), lookups = useLookups()
  const [userId, setUserId] = useState(""), [picked, setPicked] = useState<string[]>([]), [err, setErr] = useState("")
  const users = useQuery({ queryKey: ["fm-users"], retry: false, queryFn: async () => { const d = (await apiClient.get<UserRow[]>("/api/v1/identity/users")).data; return Array.isArray(d) ? d : [] } })
  const acc = useQuery({ enabled: !!userId, queryKey: ["fm-access", userId], queryFn: async () => (await apiClient.get<Access>(`${BASE}/access/users/${userId}/clients`)).data })
  useEffect(() => { if (acc.data) setPicked(acc.data.clientIds) }, [acc.data])
  const save = useMutation({
    mutationFn: async () => (await apiClient.put<Access>(`${BASE}/access/users/${userId}/clients`, { clientIds: picked })).data,
    onSuccess: () => { toast.success("Client access updated"); qc.invalidateQueries({ queryKey: ["fm-access", userId] }) }, onError: async e => setErr(await errorText(e, "Could not save the access")),
  })
  const a = acc.data, warn = a ? accessWarning(picked.length, a.restricted) : null
  const dirty = !!a && [...picked].sort().join() !== [...a.clientIds].sort().join()
  return (
    <div>
      <SectionTitle>Client access</SectionTitle>
      <div style={{ ...muted, marginBottom: 10, maxWidth: 560 }}>Limit a team member to certain clients. Someone with no limit sees every client. Only administrators can change this.</div>
      {users.isError ? <Empty>Could not load the users (this needs permission to read users).</Empty> : (
        <div style={{ maxWidth: 360 }}><Pick label="Team member" value={userId} onChange={v => { setUserId(v); setPicked([]); setErr("") }} blank="Choose a person…" options={(users.data ?? []).map(u => [u.id, nameOf(u)] as const)} /></div>)}
      {userId && (acc.isError ? <Empty>Could not load this person's access.</Empty> : !a ? <Empty>Loading…</Empty> : (
        <div style={card}>
          <div style={{ marginBottom: 8 }}><b>{accessSummary(a.restricted, a.clientIds.length)}</b></div>
          <div style={{ display: "flex", flexWrap: "wrap", gap: "6px 18px" }}>
            {lookups.clients.map(c => <label key={c.id} style={{ display: "flex", gap: 6, alignItems: "center", fontSize: 13 }}><input type="checkbox" checked={picked.includes(c.id)} onChange={() => setPicked(p => p.includes(c.id) ? p.filter(x => x !== c.id) : [...p, c.id])} />{c.tradingName}</label>)}
          </div>
          {warn && <div style={{ ...muted, marginTop: 8 }}>{warn}</div>}
          {err && <ErrorLine text={err} />}
          <div style={{ marginTop: 12 }}><Btn kind="primary" disabled={!dirty || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Save access"}</Btn></div>
        </div>))}
    </div>
  )
}
