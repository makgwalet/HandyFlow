// Monthly retainer agreements: choose a client to see theirs.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, Empty, ErrorLine, Input, Pick, Pill, SectionTitle, card, day, errorText, inp, money, muted, td, th } from "./kit"
import { BASE, useLookups } from "./lookups"
import { emptyRetainer, retainerProblem, retainerRequest, type RetainerForm } from "./legal.logic"

interface Retainer { id: string; clientId: string; monthlyFee: number; startDate: string | null; endDate: string | null; status: string; notes: string | null }

export default function RetainersTab() {
  const qc = useQueryClient()
  const canWrite = usePermission("LEGALPRACTICE_MANAGE") || usePermission("LEGALPRACTICE_ADMIN")
  const lookups = useLookups()
  const [clientId, setClientId] = useState(""), [editing, setEditing] = useState<Retainer | "new" | null>(null), [cancelling, setCancelling] = useState<Retainer | null>(null)
  const { data = [], isLoading, isError } = useQuery({
    queryKey: ["lp-retainers", clientId], enabled: !!clientId,
    queryFn: async () => { const r: any = await apiClient.get(`${BASE}/retainer-agreements`, { params: { clientId } }); const d = r?.data?.data ?? r?.data; return (Array.isArray(d) ? d : []) as Retainer[] },
  })
  const refresh = () => qc.invalidateQueries({ queryKey: ["lp-retainers"] })
  return (
    <div>
      <SectionTitle right={canWrite && <Btn kind="primary" onClick={() => setEditing("new")}>New retainer</Btn>}>Retainer agreements</SectionTitle>
      <div style={{ marginBottom: 12, maxWidth: 360 }}>
        <select aria-label="Client" value={clientId} onChange={e => setClientId(e.target.value)} style={inp}>
          <option value="">Choose a client…</option>{lookups.clients.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
      </div>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {!clientId ? <Empty>Choose a client to see their retainer agreements.</Empty> : isError ? <Empty>Could not load the agreements. Please try again.</Empty> : isLoading ? <Empty>Loading…</Empty> : data.length === 0 ? <Empty>No retainer agreements for this client.</Empty> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Monthly fee</th><th style={th}>Runs</th><th style={th}>Status</th><th style={th}>Notes</th><th /></tr></thead>
            <tbody>{data.map(r => (
              <tr key={r.id} style={{ opacity: r.status === "ACTIVE" ? 1 : 0.6 }}>
                <td style={td}><b>{money(r.monthlyFee)}</b></td>
                <td style={td}>{r.startDate ? day(r.startDate) : "—"} – {r.endDate ? day(r.endDate) : "ongoing"}</td>
                <td style={td}><Pill tone={r.status === "ACTIVE" ? "success" : "neutral"}>{r.status === "ACTIVE" ? "Active" : "Cancelled"}</Pill></td>
                <td style={td}><span style={muted}>{r.notes ?? ""}</span></td>
                <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}>{canWrite && r.status === "ACTIVE" && <><Btn small onClick={() => setEditing(r)}>Edit</Btn>{" "}<Btn small kind="danger" onClick={() => setCancelling(r)}>Cancel</Btn></>}</td>
              </tr>))}</tbody>
          </table>)}
      </div>
      {editing && <RetainerDialog retainer={editing === "new" ? null : editing} defaultClient={clientId} lookups={lookups} onClose={() => setEditing(null)} onSaved={cid => { setClientId(cid); refresh() }} />}
      {cancelling && (
        <Dialog title="Cancel this retainer?" onClose={() => setCancelling(null)}
          footer={<><Btn onClick={() => setCancelling(null)}>Keep it</Btn><CancelButton id={cancelling.id} onDone={() => { refresh(); setCancelling(null) }} /></>}>
          <div style={{ lineHeight: 1.5, fontSize: 13 }}>The agreement for <b>{money(cancelling.monthlyFee)}</b> a month will be marked cancelled. This cannot be undone.</div>
        </Dialog>)}
    </div>
  )
}

function CancelButton({ id, onDone }: { id: string; onDone: () => void }) {
  const [err, setErr] = useState("")
  const m = useMutation({ mutationFn: () => apiClient.post(`${BASE}/retainer-agreements/${id}/cancel`), onSuccess: () => { toast.success("Retainer cancelled"); onDone() }, onError: async e => setErr(await errorText(e, "Could not cancel")) })
  return <><Btn kind="danger" disabled={m.isPending} onClick={() => m.mutate()}>{m.isPending ? "Cancelling…" : "Cancel retainer"}</Btn>{err && <ErrorLine text={err} />}</>
}

function RetainerDialog({ retainer, defaultClient, lookups, onClose, onSaved }: { retainer: Retainer | null; defaultClient: string; lookups: ReturnType<typeof useLookups>; onClose: () => void; onSaved: (clientId: string) => void }) {
  const [f, setF] = useState<RetainerForm>(() => retainer ? { clientId: retainer.clientId, monthlyFee: String(retainer.monthlyFee), startDate: retainer.startDate ?? "", endDate: retainer.endDate ?? "", notes: retainer.notes ?? "" } : { ...emptyRetainer(), clientId: defaultClient })
  const [err, setErr] = useState("")
  const set = (k: keyof RetainerForm) => (v: string) => setF(s => ({ ...s, [k]: v }))
  const problem = retainerProblem(f, !retainer)
  const save = useMutation({
    mutationFn: () => retainer ? apiClient.put(`${BASE}/retainer-agreements/${retainer.id}`, retainerRequest(f, false)) : apiClient.post(`${BASE}/retainer-agreements`, retainerRequest(f, true)),
    onSuccess: () => { toast.success(retainer ? "Retainer saved" : "Retainer created"); onSaved(f.clientId); onClose() },
    onError: async e => setErr(await errorText(e, "Could not save the retainer")),
  })
  return (
    <Dialog title={retainer ? "Edit retainer" : "New retainer"} onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Save"}</Btn></>}>
      {!retainer && <Pick label="Client" value={f.clientId} onChange={set("clientId")} options={lookups.clients.filter(c => c.status === "ACTIVE").map(c => [c.id, c.name] as const)} blank="Choose a client" />}
      <Input label="Monthly fee (R)" type="number" min="0" step="0.01" value={f.monthlyFee} onChange={set("monthlyFee")} />
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 12px" }}>
        {!retainer && <Input label="Starts" type="date" value={f.startDate} onChange={set("startDate")} />}
        <Input label="Ends (optional)" type="date" value={f.endDate} onChange={set("endDate")} />
      </div>
      <Input label="Notes" value={f.notes} onChange={set("notes")} />
      {problem && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
