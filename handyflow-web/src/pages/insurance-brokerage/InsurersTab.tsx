// The insurers this brokerage places business with.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { errorText } from "../../api/projectDocs"
import { usePermission } from "../../hooks/usePermission"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, Empty, ErrorLine, Pill, SectionTitle, card, fail, muted, th, td } from "../projects/tabs/pmKit"
import { emptyInsurer, insurerProblem, insurerRequest, type InsurerForm } from "../insurance/insurance.logic"
import { Input, Pager } from "../insurance/parts"
import { BASE, pageOf, type BInsurer } from "./lookups"

export default function InsurersTab() {
  const qc = useQueryClient()
  const canWrite = usePermission("INSURANCEBROKERAGE_MANAGE") || usePermission("INSURANCEBROKERAGE_ADMIN")
  const [page, setPage] = useState(0), [editing, setEditing] = useState<BInsurer | "new" | null>(null)
  const { data, isLoading, isError } = useQuery({ queryKey: ["ib-insurers", page], queryFn: async () => pageOf<BInsurer>(await apiClient.get(`${BASE}/insurers`, { params: { page, size: 24 } })) })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["ib-insurers"] }); qc.invalidateQueries({ queryKey: ["ib-insurers-all"] }) }
  const toggle = useMutation({
    mutationFn: (i: BInsurer) => apiClient.post(`${BASE}/insurers/${i.id}/${i.active ? "deactivate" : "reactivate"}`),
    onSuccess: refresh, onError: e => { void fail(e, "That did not work") },
  })
  const rows = data?.content ?? []
  return (
    <div>
      <SectionTitle right={canWrite && <Btn kind="primary" onClick={() => setEditing("new")}>Add insurer</Btn>}>Insurers</SectionTitle>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {isError ? <Empty>Could not load the insurers. Please try again.</Empty> : isLoading ? <Empty>Loading…</Empty> : rows.length === 0 ? <Empty>No insurers yet. Add the ones you place business with so policies can point to them.</Empty> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Insurer</th><th style={th}>Contact</th><th style={th}>Status</th><th /></tr></thead>
            <tbody>{rows.map(i => (
              <tr key={i.id} style={{ opacity: i.active ? 1 : 0.6 }}>
                <td style={td}><b>{i.name}</b>{i.notes && <div style={muted}>{i.notes}</div>}</td>
                <td style={td}>{i.contactName ?? "—"}<div style={muted}>{[i.contactEmail, i.contactPhone].filter(Boolean).join(" · ")}</div></td>
                <td style={td}><Pill tone={i.active ? "success" : "neutral"}>{i.active ? "Active" : "Inactive"}</Pill></td>
                <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}>{canWrite && <>
                  <Btn small onClick={() => setEditing(i)}>Edit</Btn>{" "}
                  <Btn small disabled={toggle.isPending} onClick={() => toggle.mutate(i)}>{i.active ? "Deactivate" : "Reactivate"}</Btn></>}</td>
              </tr>))}</tbody>
          </table>)}
      </div>
      <Pager page={page} pages={data?.totalPages ?? 0} onPage={setPage} />
      {editing && <InsurerDialog insurer={editing === "new" ? null : editing} onClose={() => setEditing(null)} onSaved={refresh} />}
    </div>
  )
}

function InsurerDialog({ insurer, onClose, onSaved }: { insurer: BInsurer | null; onClose: () => void; onSaved: () => void }) {
  const [f, setF] = useState<InsurerForm>(() => insurer ? { name: insurer.name, contactName: insurer.contactName ?? "", contactEmail: insurer.contactEmail ?? "", contactPhone: insurer.contactPhone ?? "", notes: insurer.notes ?? "" } : emptyInsurer())
  const [err, setErr] = useState("")
  const set = (k: keyof InsurerForm) => (v: string) => setF(s => ({ ...s, [k]: v }))
  const problem = insurerProblem(f)
  const save = useMutation({
    mutationFn: () => insurer ? apiClient.put(`${BASE}/insurers/${insurer.id}`, insurerRequest(f)) : apiClient.post(`${BASE}/insurers`, insurerRequest(f)),
    onSuccess: () => { toast.success(insurer ? "Insurer saved" : "Insurer added"); onSaved(); onClose() },
    onError: async e => setErr(await errorText(e, "Could not save the insurer")),
  })
  return (
    <Dialog title={insurer ? `Edit ${insurer.name}` : "Add an insurer"} onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Save"}</Btn></>}>
      <Input label="Name" value={f.name} onChange={set("name")} autoFocus maxLength={200} />
      <Input label="Contact person" value={f.contactName} onChange={set("contactName")} />
      <Input label="Contact email" type="email" value={f.contactEmail} onChange={set("contactEmail")} />
      <Input label="Contact phone" value={f.contactPhone} onChange={set("contactPhone")} />
      <Input label="Notes" value={f.notes} onChange={set("notes")} />
      {problem && f.name.trim() && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
