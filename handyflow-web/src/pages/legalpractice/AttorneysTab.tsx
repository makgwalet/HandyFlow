// The firm's attorneys and what they charge per hour. A matter has one responsible attorney and time is logged against them.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, Empty, ErrorLine, Input, Pager, Pick, Pill, SectionTitle, card, errorText, fail, money, muted, pageOf, td, th } from "./kit"
import { BASE, type LAttorney } from "./lookups"
import { ROLES, attorneyProblem, attorneyRequest, emptyAttorney, labelOf, type AttorneyForm } from "./legal.logic"

export default function AttorneysTab() {
  const qc = useQueryClient()
  const canWrite = usePermission("LEGALPRACTICE_MANAGE") || usePermission("LEGALPRACTICE_ADMIN")
  const [page, setPage] = useState(0), [editing, setEditing] = useState<LAttorney | "new" | null>(null)
  const { data, isLoading, isError } = useQuery({ queryKey: ["lp-attorneys", page], queryFn: async () => pageOf<LAttorney>(await apiClient.get(`${BASE}/attorneys`, { params: { page, size: 25 } })) })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["lp-attorneys"] }); qc.invalidateQueries({ queryKey: ["lp-attorneys-all"] }) }
  const toggle = useMutation({
    mutationFn: (a: LAttorney) => apiClient.post(`${BASE}/attorneys/${a.id}/${a.active ? "deactivate" : "reactivate"}`),
    onSuccess: refresh, onError: e => { void fail(e, "That did not work") },
  })
  const rows = data?.content ?? []
  return (
    <div>
      <SectionTitle right={canWrite && <Btn kind="primary" onClick={() => setEditing("new")}>Add attorney</Btn>}>Attorneys</SectionTitle>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        {isError ? <Empty>Could not load the attorneys. Please try again.</Empty> : isLoading ? <Empty>Loading…</Empty> : rows.length === 0 ? <Empty>No attorneys yet. Add the people who will be responsible for matters.</Empty> : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr><th style={th}>Name</th><th style={th}>Role</th><th style={th}>Contact</th><th style={{ ...th, textAlign: "right" }}>Hourly rate</th><th style={th}>Status</th><th /></tr></thead>
            <tbody>{rows.map(a => (
              <tr key={a.id} style={{ opacity: a.active ? 1 : 0.6 }}>
                <td style={td}><b>{a.name}</b>{a.admissionNumber && <div style={muted}>Admission {a.admissionNumber}</div>}</td>
                <td style={td}>{labelOf(ROLES, a.role)}</td>
                <td style={td}>{a.email ?? "—"}<div style={muted}>{a.phone}</div></td>
                <td style={{ ...td, textAlign: "right" }}>{a.hourlyRate != null ? money(a.hourlyRate) : "—"}</td>
                <td style={td}><Pill tone={a.active ? "success" : "neutral"}>{a.active ? "Active" : "Inactive"}</Pill></td>
                <td style={{ ...td, textAlign: "right", whiteSpace: "nowrap" }}>{canWrite && <><Btn small onClick={() => setEditing(a)}>Edit</Btn>{" "}<Btn small disabled={toggle.isPending} onClick={() => toggle.mutate(a)}>{a.active ? "Deactivate" : "Reactivate"}</Btn></>}</td>
              </tr>))}</tbody>
          </table>)}
      </div>
      <Pager page={page} pages={data?.totalPages ?? 0} onPage={setPage} />
      {editing && <AttorneyDialog attorney={editing === "new" ? null : editing} onClose={() => setEditing(null)} onSaved={refresh} />}
    </div>
  )
}

function AttorneyDialog({ attorney, onClose, onSaved }: { attorney: LAttorney | null; onClose: () => void; onSaved: () => void }) {
  const [f, setF] = useState<AttorneyForm>(() => attorney ? { name: attorney.name, email: attorney.email ?? "", phone: attorney.phone ?? "", role: attorney.role, admissionNumber: attorney.admissionNumber ?? "", hourlyRate: attorney.hourlyRate != null ? String(attorney.hourlyRate) : "" } : emptyAttorney())
  const [err, setErr] = useState("")
  const set = (k: keyof AttorneyForm) => (v: string) => setF(s => ({ ...s, [k]: v }))
  const problem = attorneyProblem(f)
  const save = useMutation({
    // The link to an HR employee record is kept as it was; it is not edited here.
    mutationFn: () => attorney ? apiClient.put(`${BASE}/attorneys/${attorney.id}`, attorneyRequest(f, attorney.employeeId)) : apiClient.post(`${BASE}/attorneys`, attorneyRequest(f, null)),
    onSuccess: () => { toast.success(attorney ? "Attorney saved" : "Attorney added"); onSaved(); onClose() },
    onError: async e => setErr(await errorText(e, "Could not save the attorney")),
  })
  return (
    <Dialog title={attorney ? `Edit ${attorney.name}` : "Add an attorney"} onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Save"}</Btn></>}>
      <Input label="Name" value={f.name} onChange={set("name")} autoFocus maxLength={255} />
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 12px" }}>
        <Pick label="Role" value={f.role} onChange={set("role")} options={ROLES} />
        <Input label="Admission number" value={f.admissionNumber} onChange={set("admissionNumber")} />
        <Input label="Email" type="email" value={f.email} onChange={set("email")} />
        <Input label="Phone" value={f.phone} onChange={set("phone")} />
        <Input label="Hourly rate (R)" type="number" min="0" step="0.01" value={f.hourlyRate} onChange={set("hourlyRate")} hint="Used when time is logged without a rate" />
      </div>
      {problem && f.name.trim() && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
