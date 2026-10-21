// Pay and bank changes: asking for one, deciding on waiting ones, and the history of a record.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { useAuth } from "../../hooks/useAuth"
import { AlertCircle, X } from "lucide-react"
import {
  CHANGE_STATUS, TYPE_TEXT, auditText, bankProblem, cannotDecide, compensationProblem, effectiveProblem, reasonProblem, today,
  type EmployeeChange,
} from "./employees.logic"

const BANKS = ["ABSA", "FNB", "STANDARD BANK", "NEDBANK", "CAPITEC", "INVESTEC", "AFRICAN BANK", "OTHER"]
const FREQS = ["MONTHLY", "BI_WEEKLY", "WEEKLY"]
type Wrapped = { data?: { data?: unknown } & Record<string, unknown> }
const list = <T,>(r: unknown): T[] => {
  const res = r as Wrapped
  const d = (res.data?.data ?? res.data) as unknown
  if (Array.isArray(d)) return d as T[]
  const content = (d as { content?: unknown } | undefined)?.content
  return Array.isArray(content) ? (content as T[]) : []
}
const fmtDate = (d: string | null) => (d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—")
const msg = (e: unknown, fallback: string): string => (e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback

const lbl: React.CSSProperties = { display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4 }
const inp: React.CSSProperties = { width: "100%", padding: "9px 12px", boxSizing: "border-box", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 14, outline: "none", background: "var(--hf-surface)" }
const btn = (primary: boolean, disabled = false): React.CSSProperties => ({
  padding: "9px 18px", borderRadius: 9, fontSize: 14, fontWeight: 600, cursor: disabled ? "default" : "pointer",
  border: primary ? "none" : "1px solid var(--hf-border)",
  background: primary ? (disabled ? "var(--hf-text-faint)" : "var(--hf-primary)") : "var(--hf-surface)",
  color: primary ? "var(--hf-text-on-solid)" : "var(--hf-text-secondary)",
})

function Modal({ title, sub, onClose, children, width = 520 }: { title: string; sub?: string; onClose: () => void; children: React.ReactNode; width?: number }) {
  return (
    <div role="dialog" aria-label={title} style={{ position: "fixed", inset: 0, background: "rgba(15,23,42,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1002 }}>
      <div style={{ background: "var(--hf-surface)", borderRadius: 16, padding: 26, width, maxWidth: "94vw", maxHeight: "88vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,0.2)" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 16 }}>
          <div>
            <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "var(--hf-text)" }}>{title}</h3>
            {sub && <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 2 }}>{sub}</div>}
          </div>
          <button aria-label="Close" onClick={onClose} style={{ background: "none", border: "none", cursor: "pointer", color: "var(--hf-text-faint)", display: "flex" }}><X size={18} /></button>
        </div>
        {children}
      </div>
    </div>
  )
}

function Problem({ text }: { text: string }) {
  return <div role="alert" style={{ display: "flex", gap: 6, alignItems: "center", padding: "9px 12px", background: "var(--hf-danger-soft)", border: "1px solid var(--hf-danger-border)", borderRadius: 8, fontSize: 13, color: "var(--hf-danger-text-strong)", margin: "12px 0" }}><AlertCircle size={14} />{text}</div>
}

interface Person { id: string; fullName: string; employeeNumber: string; startDate: string; grossSalary?: number | null; payFrequency?: string | null; travelAllowance?: number | null; medicalAidContribution?: number | null; pensionContribution?: number | null; bankName?: string | null; bankAccountMasked?: string | null; bankBranchCode?: string | null }

/** Ask for a change to pay (kind "pay") or to bank details (kind "bank"). Nothing changes until someone else approves. */
export function RequestChangeModal({ kind, employee, onClose }: { kind: "pay" | "bank"; employee: Person; onClose: () => void }) {
  const qc = useQueryClient()
  const [pay, setPay] = useState({
    grossSalary: String(employee.grossSalary ?? ""), travelAllowance: String(employee.travelAllowance ?? 0),
    medicalAidContribution: String(employee.medicalAidContribution ?? 0), pensionContribution: String(employee.pensionContribution ?? 0),
    payFrequency: employee.payFrequency ?? "MONTHLY",
  })
  const [bank, setBank] = useState({ bankName: "", accountNumber: "", branchCode: "" })
  const [effectiveFrom, setEffective] = useState(today())
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")

  const send = useMutation({
    mutationFn: () => kind === "pay"
      ? apiClient.post(`/api/v1/hr/employees/${employee.id}/changes/compensation`, {
          grossSalary: Number(pay.grossSalary), travelAllowance: Number(pay.travelAllowance || 0),
          medicalAidContribution: Number(pay.medicalAidContribution || 0), pensionContribution: Number(pay.pensionContribution || 0),
          payFrequency: pay.payFrequency, effectiveFrom, reason,
        })
      : apiClient.post(`/api/v1/hr/employees/${employee.id}/changes/bank`, { bankName: bank.bankName, accountNumber: bank.accountNumber, branchCode: bank.branchCode, effectiveFrom, reason }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["hr-employee-changes"] }); qc.invalidateQueries({ queryKey: ["hr-employees"] }); onClose() },
    onError: (e: unknown) => setError(msg(e, "The request could not be sent.")),
  })

  const submit = () => {
    const problem = (kind === "pay" ? compensationProblem(pay) : bankProblem(bank.bankName, bank.accountNumber, bank.branchCode))
      ?? effectiveProblem(effectiveFrom, employee.startDate) ?? reasonProblem(reason)
    if (problem) { setError(problem); return }
    setError(""); send.mutate()
  }
  const num = (k: keyof typeof pay, label: string) => (
    <div><label style={lbl} htmlFor={`pay-${k}`}>{label}</label><input id={`pay-${k}`} type="number" value={pay[k]} onChange={e => setPay(p => ({ ...p, [k]: e.target.value }))} style={inp} /></div>
  )

  return (
    <Modal title={kind === "pay" ? "Request a pay change" : "Request a bank details change"} sub={`${employee.fullName} · ${employee.employeeNumber}`} onClose={onClose}>
      <div style={{ fontSize: 13, color: "var(--hf-text-muted)", marginBottom: 14 }}>
        Nothing changes until someone else approves this and the date arrives. Payroll uses the new figures from that date.
      </div>
      {kind === "pay" ? (
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
          {num("grossSalary", "Gross salary (R)")}
          <div><label style={lbl} htmlFor="pay-freq">Pay frequency</label>
            <select id="pay-freq" value={pay.payFrequency} onChange={e => setPay(p => ({ ...p, payFrequency: e.target.value }))} style={inp}>{FREQS.map(f => <option key={f}>{f}</option>)}</select></div>
          {num("travelAllowance", "Travel allowance (R)")}
          {num("medicalAidContribution", "Medical aid (R)")}
          {num("pensionContribution", "Pension / provident (R)")}
        </div>
      ) : (
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
          <div style={{ gridColumn: "1/-1", fontSize: 12, color: "var(--hf-text-muted)" }}>
            Now: {employee.bankName ?? "—"} {employee.bankAccountMasked ?? ""} {employee.bankBranchCode ? `· branch ${employee.bankBranchCode}` : ""}
          </div>
          <div><label style={lbl} htmlFor="bank-name">Bank</label>
            <select id="bank-name" value={bank.bankName} onChange={e => setBank(b => ({ ...b, bankName: e.target.value }))} style={inp}><option value="">Select...</option>{BANKS.map(b => <option key={b}>{b}</option>)}</select></div>
          <div><label style={lbl} htmlFor="bank-account">Account number</label><input id="bank-account" value={bank.accountNumber} onChange={e => setBank(b => ({ ...b, accountNumber: e.target.value }))} style={inp} /></div>
          <div><label style={lbl} htmlFor="bank-branch">Branch code</label><input id="bank-branch" value={bank.branchCode} onChange={e => setBank(b => ({ ...b, branchCode: e.target.value }))} style={inp} /></div>
        </div>
      )}
      <div style={{ display: "grid", gridTemplateColumns: "1fr", gap: 12, marginTop: 12 }}>
        <div><label style={lbl} htmlFor="chg-effective">Takes effect on</label><input id="chg-effective" type="date" value={effectiveFrom} onChange={e => setEffective(e.target.value)} style={inp} /></div>
        <div><label style={lbl} htmlFor="chg-reason">Reason</label><textarea id="chg-reason" rows={2} value={reason} onChange={e => setReason(e.target.value)} placeholder="Annual increase, promotion, new bank..." style={{ ...inp, resize: "vertical" }} /></div>
      </div>
      {error && <Problem text={error} />}
      <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 14 }}>
        <button onClick={onClose} style={btn(false)}>Cancel</button>
        <button onClick={submit} disabled={send.isPending} style={btn(true, send.isPending)}>{send.isPending ? "Sending..." : "Send for approval"}</button>
      </div>
    </Modal>
  )
}

function ChangeCard({ change, children }: { change: EmployeeChange; children?: React.ReactNode }) {
  return (
    <div style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: 14, background: "var(--hf-surface)" }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 10, marginBottom: 6 }}>
        <div style={{ fontWeight: 700, fontSize: 14, color: "var(--hf-text)" }}>{change.employeeName} · {TYPE_TEXT[change.changeType] ?? change.changeType}</div>
        <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{CHANGE_STATUS[change.status] ?? change.status}</div>
      </div>
      <ul style={{ margin: "0 0 6px", paddingLeft: 18, fontSize: 13, color: "var(--hf-text)" }}>
        {change.fields.map(f => <li key={f.key}>{f.label}: {f.oldValue ?? "—"} → <strong>{f.newValue ?? "—"}</strong></li>)}
      </ul>
      <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>From {fmtDate(change.effectiveFrom)} · {change.reason}</div>
      {change.decisionNote && <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 2 }}>Decision note: {change.decisionNote}</div>}
      {change.selfApproved && <div style={{ fontSize: 12, color: "var(--hf-warning-text)", marginTop: 2 }}>Approved by the person who asked for it.</div>}
      {children}
    </div>
  )
}

/** Changes waiting for a decision. Approvers approve or reject; the person who asked may cancel. */
export function ApprovalsModal({ canDecide, onClose }: { canDecide: boolean; onClose: () => void }) {
  const qc = useQueryClient()
  const { user } = useAuth() as { user?: { userId?: string } }
  const me = user?.userId
  const [rejecting, setRejecting] = useState<string | null>(null)
  const [note, setNote] = useState("")
  const [error, setError] = useState("")
  const { data = [], isLoading } = useQuery<EmployeeChange[]>({
    queryKey: ["hr-employee-changes", "pending"],
    queryFn: async () => list<EmployeeChange>(await apiClient.get("/api/v1/hr/employee-changes/pending")),
  })
  const done = () => { qc.invalidateQueries({ queryKey: ["hr-employee-changes"] }); qc.invalidateQueries({ queryKey: ["hr-employees"] }); setRejecting(null); setNote(""); setError("") }
  const act = useMutation({
    mutationFn: ({ id, action, body }: { id: string; action: "approve" | "reject" | "cancel"; body?: { note: string } }) =>
      apiClient.post(`/api/v1/hr/employee-changes/${id}/${action}`, body ?? {}),
    onSuccess: done,
    onError: (e: unknown) => setError(msg(e, "That did not go through.")),
  })
  return (
    <Modal title="Pay and bank changes waiting" sub="Someone other than the person who asked has to decide each one." onClose={onClose} width={620}>
      {isLoading ? <div style={{ color: "var(--hf-text-faint)" }}>Loading...</div> : data.length === 0 ? (
        <div style={{ color: "var(--hf-text-muted)", padding: "20px 0" }}>Nothing is waiting for a decision.</div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {data.map(c => {
            const why = cannotDecide(c, me, true)
            const mine = !!me && c.requestedBy === me
            return (
              <ChangeCard key={c.id} change={c}>
                {rejecting === c.id ? (
                  <div style={{ marginTop: 8 }}>
                    <label style={lbl} htmlFor={`rej-${c.id}`}>Why is it rejected?</label>
                    <input id={`rej-${c.id}`} value={note} onChange={e => setNote(e.target.value)} style={inp} />
                    <div style={{ display: "flex", gap: 8, justifyContent: "flex-end", marginTop: 8 }}>
                      <button onClick={() => setRejecting(null)} style={btn(false)}>Back</button>
                      <button disabled={!note.trim() || act.isPending} onClick={() => act.mutate({ id: c.id, action: "reject", body: { note } })} style={btn(true, !note.trim() || act.isPending)}>Reject change</button>
                    </div>
                  </div>
                ) : (
                  <div style={{ display: "flex", gap: 8, justifyContent: "flex-end", alignItems: "center", marginTop: 8 }}>
                    {canDecide && mine && <span style={{ fontSize: 12, color: "var(--hf-text-muted)", marginRight: "auto" }}>You asked for this one. Someone else has to approve it, unless the business has allowed approving your own requests.</span>}
                    {mine && <button onClick={() => act.mutate({ id: c.id, action: "cancel" })} style={btn(false)}>Cancel request</button>}
                    {canDecide && <button onClick={() => { setRejecting(c.id); setNote("") }} style={btn(false)}>Reject</button>}
                    {canDecide && <button disabled={!!why || act.isPending} onClick={() => act.mutate({ id: c.id, action: "approve", body: { note: "" } })} style={btn(true, !!why || act.isPending)}>Approve</button>}
                  </div>
                )}
              </ChangeCard>
            )
          })}
        </div>
      )}
      {error && <Problem text={error} />}
    </Modal>
  )
}

interface AuditRow { id: string; action: string; detail: string | null; actorId: string | null; createdAt: string }

/** What was done to one record, and its pay and bank changes. */
export function HistoryModal({ employee, showChanges, showAudit, onClose }: { employee: { id: string; fullName: string; employeeNumber: string }; showChanges: boolean; showAudit: boolean; onClose: () => void }) {
  const audit = useQuery<AuditRow[]>({
    queryKey: ["hr-employee-audit", employee.id], enabled: showAudit,
    queryFn: async () => list<AuditRow>(await apiClient.get(`/api/v1/hr/employees/${employee.id}/audit`)),
  })
  const changes = useQuery<EmployeeChange[]>({
    queryKey: ["hr-employee-changes", employee.id], enabled: showChanges,
    queryFn: async () => list<EmployeeChange>(await apiClient.get(`/api/v1/hr/employees/${employee.id}/changes`)),
  })
  return (
    <Modal title="History" sub={`${employee.fullName} · ${employee.employeeNumber}`} onClose={onClose} width={600}>
      {showChanges && (
        <div style={{ marginBottom: 16 }}>
          <div style={{ fontSize: 12, fontWeight: 700, color: "var(--hf-text-muted)", marginBottom: 6 }}>PAY AND BANK CHANGES</div>
          {(changes.data ?? []).length === 0 ? <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>None.</div> :
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>{(changes.data ?? []).map(c => <ChangeCard key={c.id} change={c} />)}</div>}
        </div>
      )}
      {showAudit && (
        <div>
          <div style={{ fontSize: 12, fontWeight: 700, color: "var(--hf-text-muted)", marginBottom: 6 }}>RECORD</div>
          {(audit.data ?? []).length === 0 ? <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Nothing recorded yet.</div> : (
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
              <tbody>
                {(audit.data ?? []).map(a => (
                  <tr key={a.id} style={{ borderBottom: "1px solid var(--hf-border-subtle)" }}>
                    <td style={{ padding: "6px 8px 6px 0", color: "var(--hf-text-muted)", whiteSpace: "nowrap" }}>{new Date(a.createdAt).toLocaleString("en-ZA")}</td>
                    <td style={{ padding: "6px 8px", color: "var(--hf-text)" }}>{auditText(a.action)}{a.detail ? ` — ${a.detail}` : ""}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
    </Modal>
  )
}

interface Candidate { id: string; fullName: string; employeeNumber: string }
interface Login { id: string; email: string; firstName: string; lastName: string }

/** Who the employee reports to, and which staff login is theirs (so a manager sees their own team). */
export function ReportingModal({ employee, managerId, userId, candidates, onClose }: { employee: { id: string; fullName: string; employeeNumber: string }; managerId: string | null; userId: string | null; candidates: Candidate[]; onClose: () => void }) {
  const qc = useQueryClient()
  const [manager, setManager] = useState(managerId ?? "")
  const [login, setLogin] = useState(userId ?? "")
  const [error, setError] = useState("")
  const users = useQuery<Login[]>({
    queryKey: ["identity-users-for-link"], retry: false,
    queryFn: async () => list<Login>(await apiClient.get("/api/v1/identity/users")),
  })
  const save = useMutation({
    mutationFn: async () => {
      if ((managerId ?? "") !== manager) await apiClient.put(`/api/v1/hr/employees/${employee.id}/manager`, { managerId: manager || null })
      if ((userId ?? "") !== login) await apiClient.put(`/api/v1/hr/employees/${employee.id}/user-link`, { userId: login || null })
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["hr-employees"] }); onClose() },
    onError: (e: unknown) => setError(msg(e, "Could not save.")),
  })
  return (
    <Modal title="Reporting line and login" sub={`${employee.fullName} · ${employee.employeeNumber}`} onClose={onClose}>
      <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
        <div><label style={lbl} htmlFor="rep-manager">Reports to</label>
          <select id="rep-manager" value={manager} onChange={e => setManager(e.target.value)} style={inp}>
            <option value="">No manager</option>
            {candidates.filter(c => c.id !== employee.id).map(c => <option key={c.id} value={c.id}>{c.fullName} ({c.employeeNumber})</option>)}
          </select></div>
        <div><label style={lbl} htmlFor="rep-login">Staff login</label>
          <select id="rep-login" value={login} onChange={e => setLogin(e.target.value)} style={inp} disabled={users.isError}>
            <option value="">Not linked</option>
            {(users.data ?? []).map(u => <option key={u.id} value={u.id}>{u.firstName} {u.lastName} · {u.email}</option>)}
          </select>
          <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 3 }}>
            {users.isError ? "The list of logins needs permission to read users." : "A manager given team access sees the people who report to them, through this link."}
          </div></div>
      </div>
      {error && <Problem text={error} />}
      <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 16 }}>
        <button onClick={onClose} style={btn(false)}>Cancel</button>
        <button onClick={() => { setError(""); save.mutate() }} disabled={save.isPending} style={btn(true, save.isPending)}>{save.isPending ? "Saving..." : "Save"}</button>
      </div>
    </Modal>
  )
}
