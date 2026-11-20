// Ethical walls: bar a member of staff from one matter, or from all of one client's matters. The server enforces it:
// the barred person cannot open the matter or add to it, and each time they try it is recorded below.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { useAuthStore } from "../../store/auth.store"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, Empty, ErrorLine, Input, Pager, Pick, Pill, SectionTitle, card, day, errorText, muted, pageOf, td, th } from "./kit"
import { BASE, useLookups } from "./lookups"
import type { Matter } from "./MattersTab"
import { WALL_SCOPES, canLiftWall, emptyWall, liftProblem, wallHeadline, wallProblem, wallRequest, type Wall, type WallDenial, type WallForm } from "./legal.logic"

interface Staff { id: string; firstName?: string; lastName?: string; email?: string }
const staffName = (u: Staff) => `${u.firstName ?? ""} ${u.lastName ?? ""}`.trim() || u.email || u.id
const URL = `${BASE}/ethical-walls`

export default function WallsTab() {
  const canManage = usePermissions(["LEGALPRACTICE_WALL_MANAGE", "LEGALPRACTICE_ADMIN"])
  const canAudit = usePermissions(["LEGALPRACTICE_WALL_AUDIT", "LEGALPRACTICE_ADMIN"])
  const me = useAuthStore(s => s.user?.userId)
  const [activeOnly, setActiveOnly] = useState(true), [page, setPage] = useState(0), [dpage, setDpage] = useState(0)
  const [adding, setAdding] = useState(false), [lifting, setLifting] = useState<Wall | null>(null)
  const list = useQuery({ queryKey: ["lp-walls", activeOnly, page], queryFn: async () => pageOf<Wall>(await apiClient.get(URL, { params: { activeOnly, page, size: 15 } })) })
  const denials = useQuery({ queryKey: ["lp-wall-denials", dpage], enabled: canAudit, queryFn: async () => pageOf<WallDenial>(await apiClient.get(`${URL}/denials`, { params: { page: dpage, size: 10 } })) })
  const rows = list.data?.content ?? []
  return (
    <div>
      <SectionTitle right={canManage && <Btn kind="primary" onClick={() => setAdding(true)}>Put up a wall</Btn>}>Ethical walls</SectionTitle>
      <div style={{ ...muted, marginBottom: 10 }}>A wall bars one person from a matter, or from all of a client's matters. They cannot open it, see its time, disbursements or dates, or add to it. A wall applies to everyone it names, administrators included. It can only be lifted by someone else, with a reason.</div>
      <label style={{ fontSize: 13, display: "inline-flex", gap: 6, alignItems: "center", marginBottom: 8 }}>
        <input type="checkbox" checked={activeOnly} onChange={e => { setActiveOnly(e.target.checked); setPage(0) }} /> Only walls in force
      </label>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {list.isError ? <Empty>Could not load the walls.</Empty> : list.isLoading ? <Empty>Loading…</Empty> : rows.length === 0 ? <Empty>{activeOnly ? "No walls are in force." : "No walls."}</Empty> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Wall</th><th style={th}>Why</th><th style={th}>Put up</th><th style={th}>Status</th><th style={th} /></tr></thead>
            <tbody>{rows.map(w => (
              <tr key={w.id}>
                <td style={td}><b>{wallHeadline(w)}</b></td>
                <td style={td}>{w.reason}</td>
                <td style={td}>{day(w.createdAt)}<div style={muted}>{w.createdByName ?? ""}</div></td>
                <td style={td}>{w.active ? <Pill tone="warning">In force</Pill> : <><Pill tone="neutral">Lifted</Pill><div style={muted}>{day(w.liftedAt)} by {w.liftedByName ?? "—"}: {w.liftReason}</div></>}</td>
                <td style={{ ...td, textAlign: "right" }}>{canLiftWall(w, me, canManage) && <Btn small onClick={() => setLifting(w)}>Lift</Btn>}</td>
              </tr>))}</tbody>
          </table>)}
      </div>
      <Pager page={page} pages={list.data?.totalPages ?? 0} onPage={setPage} />

      {canAudit && (
        <div style={{ marginTop: 22 }}>
          <div style={{ fontWeight: 700, marginBottom: 6 }}>Times someone was turned away</div>
          <div style={{ ...card, padding: 0, overflowX: "auto" }}>
            {denials.isError ? <Empty>Could not load the record.</Empty> : denials.isLoading ? <Empty>Loading…</Empty> : (denials.data?.content ?? []).length === 0 ? <Empty>Nobody has been turned away.</Empty> : (
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead><tr><th style={th}>When</th><th style={th}>Person</th><th style={th}>Tried to open</th></tr></thead>
                <tbody>{(denials.data?.content ?? []).map(d => (
                  <tr key={d.id}><td style={td}>{day(d.deniedAt)}</td><td style={td}>{d.userName ?? d.userId}</td><td style={td}>{d.matterLabel && !d.matterLabel.startsWith("(matter not found") ? d.matterLabel : "A walled client"}</td></tr>))}</tbody>
              </table>)}
          </div>
          <Pager page={dpage} pages={denials.data?.totalPages ?? 0} onPage={setDpage} />
        </div>
      )}
      {adding && <AddDialog onClose={() => setAdding(false)} />}
      {lifting && <LiftDialog wall={lifting} onClose={() => setLifting(null)} />}
    </div>
  )
}

function AddDialog({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient(), lookups = useLookups()
  const [f, setF] = useState<WallForm>(emptyWall()), [err, setErr] = useState("")
  const set = (k: keyof WallForm) => (v: string) => setF(s => ({ ...s, [k]: v }))
  const staff = useQuery({ queryKey: ["lp-wall-staff"], queryFn: async () => { const r = await apiClient.get("/api/v1/identity/users"); const d = r.data?.data ?? r.data; return (Array.isArray(d) ? d : d?.content ?? []) as Staff[] } })
  const matters = useQuery({ queryKey: ["lp-wall-matters"], enabled: f.scope === "MATTER", queryFn: async () => pageOf<Matter>(await apiClient.get(`${BASE}/matters`, { params: { size: 500 } })) })
  const people = staff.data ?? []
  const problem = wallProblem(f)
  const save = useMutation({
    mutationFn: () => apiClient.post(URL, wallRequest(f, staffName(people.find(p => p.id === f.userId) ?? { id: f.userId }))),
    onSuccess: () => { toast.success("Wall put up"); qc.invalidateQueries({ queryKey: ["lp-walls"] }); onClose() },
    onError: async e => setErr(await errorText(e, "Could not put up the wall")),
  })
  return (
    <Dialog title="Put up a wall" onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Put up the wall"}</Btn></>}>
      <Pick label="The wall covers" value={f.scope} onChange={set("scope")} options={WALL_SCOPES} />
      {f.scope === "MATTER"
        ? <Pick label="Matter" value={f.matterId} onChange={set("matterId")} blank="Choose a matter" options={(matters.data?.content ?? []).map(m => [m.id, `${m.matterNumber} ${m.matterName}`] as const)} />
        : <Pick label="Client" value={f.clientId} onChange={set("clientId")} blank="Choose a client" options={lookups.clients.map(c => [c.id, c.name] as const)} />}
      <Pick label="Person the wall bars" value={f.userId} onChange={set("userId")} blank={staff.isLoading ? "Loading…" : "Choose a person"} options={people.map(p => [p.id, staffName(p)] as const)} />
      <Input label="Why" value={f.reason} onChange={set("reason")} hint="For example: acted for the other side at a previous firm. At least 10 characters." />
      {problem && (f.reason || f.userId) && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

function LiftDialog({ wall, onClose }: { wall: Wall; onClose: () => void }) {
  const qc = useQueryClient()
  const [reason, setReason] = useState(""), [err, setErr] = useState("")
  const problem = liftProblem(reason)
  const save = useMutation({
    mutationFn: () => apiClient.post(`${URL}/${wall.id}/lift`, { reason: reason.trim() }),
    onSuccess: () => { toast.success("Wall lifted"); qc.invalidateQueries({ queryKey: ["lp-walls"] }); onClose() },
    onError: async e => setErr(await errorText(e, "Could not lift the wall")),
  })
  return (
    <Dialog title="Lift this wall" onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Lift the wall"}</Btn></>}>
      <div style={muted}>{wallHeadline(wall)}. Once lifted, they can open it again. The wall and who lifted it stay on record.</div>
      <div style={{ marginTop: 10 }}><Input label="Why is it being lifted" value={reason} onChange={setReason} /></div>
      {problem && reason.trim() && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
