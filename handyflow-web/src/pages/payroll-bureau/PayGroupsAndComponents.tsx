// Pay groups, the component catalogue, an employee's recurring components and a draft run's one-off items.
import { useEffect, useState } from "react"
import { payrollBureauApi } from "../../api/payrollBureau.api"
import type { PayClient, PayEmployee, PayGroup, PayComponent, EmployeeComponent, RunItem, PayRun } from "../../types/payrollBureau.types"
import { Table, Empty, Modal, Field, ErrorBox, ModalActions, StatusBadge } from "./ui"
import { fmtR, fmtD, MUTED, FAINT, INK, inputStyle, btnPrimary, btnSecondary, rowStyle, cellStyle } from "./uiStyles"

const message = (e: unknown, fallback: string) =>
  (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? fallback

const FREQUENCY_LABEL: Record<string, string> = { MONTHLY: "Monthly", FORTNIGHTLY: "Fortnightly", WEEKLY: "Weekly" }
const ROLE_LABEL: Record<string, string> = {
  TAXABLE_EARNING: "Taxable earning", BONUS: "Bonus", NON_TAXABLE_EARNING: "Non-taxable earning", DEDUCTION: "Deduction",
}
const ROLE_HELP: Record<string, string> = {
  TAXABLE_EARNING: "Taxed with the period's pay; counts for UIF and SDL. Overtime, commission, allowances.",
  BONUS: "Taxed by the bonus method in the month paid; counts for UIF and SDL.",
  NON_TAXABLE_EARNING: "Paid without tax, UIF or SDL. Expense reimbursements.",
  DEDUCTION: "Taken from net pay after tax. Loan repayments, garnishee orders, union fees.",
}

// ── Pay groups ───────────────────────────────────────────────────────────────

export function PayGroupsTab({ client }: { client: PayClient }) {
  const [groups, setGroups] = useState<PayGroup[]>([])
  const [loading, setLoading] = useState(true)
  const [showNew, setShowNew] = useState(false)

  const refetch = () => {
    payrollBureauApi.getPayGroups(client.id).then(setGroups).finally(() => setLoading(false))
  }
  useEffect(refetch, [client.id])

  const archive = async (g: PayGroup) => {
    if (!window.confirm(`Archive "${g.name}"? Its employees must be moved first.`)) return
    try { await payrollBureauApi.archivePayGroup(g.id); refetch() }
    catch (e: unknown) { alert(message(e, "Failed to archive the pay group")) }
  }

  return (
    <div>
      <p style={{ fontSize: 12.5, color: MUTED, margin: "0 0 12px" }}>
        Employees not in a group are paid monthly. For an employee in a weekly or fortnightly group, the amounts on their record
        (salary, travel, pension, medical aid) are what they get <strong>per pay period</strong> of that group.
      </p>
      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 12 }}>
        <button onClick={() => setShowNew(true)} style={btnPrimary}>+ New Pay Group</button>
      </div>
      {loading ? <Empty text="Loading…" /> : groups.length === 0 ? <Empty text="No pay groups yet. Everyone is paid monthly." /> : (
        <Table headers={["Name", "Frequency", "First period starts", "Days to pay day", "Employees", "Status", ""]}>
          {groups.map(g => (
            <tr key={g.id} style={rowStyle}>
              <td style={cellStyle}>{g.name}</td>
              <td style={cellStyle}>{FREQUENCY_LABEL[g.frequency] ?? g.frequency}</td>
              <td style={cellStyle}>{g.anchorDate ? fmtD(g.anchorDate) : <span style={{ color: FAINT }}>—</span>}</td>
              <td style={cellStyle}>{g.payLagDays}</td>
              <td style={cellStyle}>{g.employeeCount}</td>
              <td style={cellStyle}><StatusBadge status={g.status === "ACTIVE" ? "ACTIVE" : "DRAFT"} /></td>
              <td style={cellStyle}>
                {g.status === "ACTIVE" && <button onClick={() => archive(g)} style={btnSecondary}>Archive</button>}
              </td>
            </tr>
          ))}
        </Table>
      )}
      {showNew && <NewPayGroupModal clientId={client.id} onClose={() => setShowNew(false)}
        onCreated={() => { setShowNew(false); refetch() }} />}
    </div>
  )
}

function NewPayGroupModal({ clientId, onClose, onCreated }: { clientId: string; onClose: () => void; onCreated: () => void }) {
  const [name, setName] = useState("")
  const [frequency, setFrequency] = useState("WEEKLY")
  const [anchorDate, setAnchorDate] = useState("")
  const [lag, setLag] = useState("0")
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState("")

  const submit = async () => {
    setSaving(true); setError("")
    try {
      await payrollBureauApi.createPayGroup(clientId, {
        name, frequency, payLagDays: Number(lag) || 0,
        anchorDate: frequency === "MONTHLY" ? undefined : anchorDate || undefined,
      })
      onCreated()
    } catch (e: unknown) { setError(message(e, "Failed to create the pay group")) }
    finally { setSaving(false) }
  }

  return (
    <Modal title="New Pay Group" onClose={onClose}>
      <Field label="Name"><input value={name} onChange={e => setName(e.target.value)} style={inputStyle} placeholder="e.g. Factory floor" /></Field>
      <Field label="Frequency">
        <select value={frequency} onChange={e => setFrequency(e.target.value)} style={inputStyle}>
          <option value="WEEKLY">Weekly</option><option value="FORTNIGHTLY">Fortnightly</option><option value="MONTHLY">Monthly</option>
        </select>
      </Field>
      {frequency !== "MONTHLY" && (
        <Field label="First day of any pay period">
          <input type="date" value={anchorDate} onChange={e => setAnchorDate(e.target.value)} style={inputStyle} />
        </Field>
      )}
      <Field label="Days between the end of a period and pay day">
        <input type="number" min={0} max={31} value={lag} onChange={e => setLag(e.target.value)} style={inputStyle} />
      </Field>
      {error && <ErrorBox text={error} />}
      <ModalActions onClose={onClose} onSubmit={submit} saving={saving} submitLabel="Create Pay Group" />
    </Modal>
  )
}

/** The group an employee is in, changed from the employees list. */
export function PayGroupSelect({ clientId, employee, groups, onChanged }:
  { clientId: string; employee: PayEmployee; groups: PayGroup[]; onChanged: () => void }) {
  const change = async (value: string) => {
    try { await payrollBureauApi.assignPayGroup(clientId, employee.id, value || null); onChanged() }
    catch (e: unknown) { alert(message(e, "Failed to move the employee")) }
  }
  return (
    <select value={employee.payGroupId ?? ""} onChange={e => change(e.target.value)} style={{ ...inputStyle, padding: "4px 6px", fontSize: 12 }}>
      <option value="">Default (monthly)</option>
      {groups.filter(g => g.status === "ACTIVE" || g.id === employee.payGroupId).map(g => (
        <option key={g.id} value={g.id}>{g.name} ({FREQUENCY_LABEL[g.frequency]})</option>
      ))}
    </select>
  )
}

/** Fills a new pay run's dates from the group's next period. */
export function GroupPeriodPicker({ groups, value, onPick }:
  { groups: PayGroup[]; value: string; onPick: (groupId: string, period?: { start: string; end: string; payDate: string }) => void }) {
  const pick = async (id: string) => {
    if (!id) { onPick(""); return }
    try {
      const p = await payrollBureauApi.nextPayPeriod(id)
      onPick(id, { start: p.periodStart, end: p.periodEnd, payDate: p.payDate })
    } catch (e: unknown) { alert(message(e, "Could not work out the next period")); onPick(id) }
  }
  const active = groups.filter(g => g.status === "ACTIVE")
  if (active.length === 0) return null
  return (
    <Field label="Pay group">
      <select value={value} onChange={e => pick(e.target.value)} style={inputStyle}>
        <option value="">Default (monthly)</option>
        {active.map(g => <option key={g.id} value={g.id}>{g.name} ({FREQUENCY_LABEL[g.frequency]})</option>)}
      </select>
    </Field>
  )
}

// ── Component catalogue ──────────────────────────────────────────────────────

export function ComponentsTab() {
  const [components, setComponents] = useState<PayComponent[]>([])
  const [loading, setLoading] = useState(true)
  const [showNew, setShowNew] = useState(false)

  const refetch = () => {
    payrollBureauApi.getPayComponents().then(setComponents).finally(() => setLoading(false))
  }
  useEffect(refetch, [])

  const archive = async (c: PayComponent) => {
    if (!window.confirm(`Archive "${c.name}"? It stops being paid in new pay runs. Payslips already made keep it.`)) return
    try { await payrollBureauApi.archivePayComponent(c.id); refetch() }
    catch (e: unknown) { alert(message(e, "Failed to archive the component")) }
  }

  return (
    <div>
      <p style={{ fontSize: 12.5, color: MUTED, margin: "0 0 12px" }}>
        Components are what can be added to or taken from pay beyond salary, travel, pension and medical aid. They are shared by all your clients.
        SARS codes are a reference; check them against SARS's current code list before relying on them for tax certificates.
      </p>
      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 12 }}>
        <button onClick={() => setShowNew(true)} style={btnPrimary}>+ New Component</button>
      </div>
      {loading ? <Empty text="Loading…" /> : (
        <Table headers={["Code", "Name", "How it is treated", "SARS code", "Status", ""]}>
          {components.map(c => (
            <tr key={c.id} style={rowStyle}>
              <td style={cellStyle}>{c.code}</td>
              <td style={cellStyle}>{c.name}</td>
              <td style={cellStyle} title={ROLE_HELP[c.role]}>{ROLE_LABEL[c.role] ?? c.role}</td>
              <td style={cellStyle}>{c.sarsCode ?? <span style={{ color: FAINT }}>—</span>}</td>
              <td style={cellStyle}><StatusBadge status={c.status === "ACTIVE" ? "ACTIVE" : "DRAFT"} /></td>
              <td style={cellStyle}>{c.status === "ACTIVE" && <button onClick={() => archive(c)} style={btnSecondary}>Archive</button>}</td>
            </tr>
          ))}
        </Table>
      )}
      {showNew && <NewComponentModal onClose={() => setShowNew(false)} onCreated={() => { setShowNew(false); refetch() }} />}
    </div>
  )
}

function NewComponentModal({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const [code, setCode] = useState("")
  const [name, setName] = useState("")
  const [role, setRole] = useState("TAXABLE_EARNING")
  const [sarsCode, setSarsCode] = useState("")
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState("")

  const submit = async () => {
    setSaving(true); setError("")
    try {
      await payrollBureauApi.createPayComponent({ code, name, role, sarsCode: sarsCode || undefined })
      onCreated()
    } catch (e: unknown) { setError(message(e, "Failed to create the component")) }
    finally { setSaving(false) }
  }

  return (
    <Modal title="New Component" onClose={onClose}>
      <Field label="Code (letters, digits, underscores)"><input value={code} onChange={e => setCode(e.target.value)} style={inputStyle} placeholder="SHIFT_ALLOWANCE" /></Field>
      <Field label="Name"><input value={name} onChange={e => setName(e.target.value)} style={inputStyle} placeholder="Shift allowance" /></Field>
      <Field label="How it is treated">
        <select value={role} onChange={e => setRole(e.target.value)} style={inputStyle}>
          {Object.entries(ROLE_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
        <div style={{ fontSize: 11.5, color: MUTED, marginTop: 4 }}>{ROLE_HELP[role]}</div>
      </Field>
      <Field label="SARS source code (optional, four digits)"><input value={sarsCode} onChange={e => setSarsCode(e.target.value)} style={inputStyle} maxLength={4} /></Field>
      {error && <ErrorBox text={error} />}
      <ModalActions onClose={onClose} onSubmit={submit} saving={saving} submitLabel="Create Component" />
    </Modal>
  )
}

// ── Per employee ─────────────────────────────────────────────────────────────

export function EmployeeComponentsModal({ clientId, employee, onClose }: { clientId: string; employee: PayEmployee; onClose: () => void }) {
  const [rows, setRows] = useState<EmployeeComponent[]>([])
  const [components, setComponents] = useState<PayComponent[]>([])
  const [componentId, setComponentId] = useState("")
  const [amount, setAmount] = useState("")
  const [error, setError] = useState("")
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    payrollBureauApi.getEmployeeComponents(clientId, employee.id).then(setRows)
    payrollBureauApi.getPayComponents().then(c => setComponents(c.filter(x => x.status === "ACTIVE")))
  }, [clientId, employee.id])

  const save = async (id: string, value: number) => {
    setSaving(true); setError("")
    try { setRows(await payrollBureauApi.setEmployeeComponent(clientId, employee.id, id, value)); setComponentId(""); setAmount("") }
    catch (e: unknown) { setError(message(e, "Failed to save")) }
    finally { setSaving(false) }
  }

  return (
    <Modal title={`Recurring pay items: ${employee.fullName}`} onClose={onClose}>
      <p style={{ fontSize: 12, color: MUTED, marginTop: 0 }}>
        Amounts for every pay period of this employee's group. They are saved straight away and are not held for approval.
      </p>
      {rows.length === 0 ? <Empty text="Nothing set." /> : (
        <Table headers={["Item", "Treatment", "Amount", ""]}>
          {rows.map(r => (
            <tr key={r.id} style={rowStyle}>
              <td style={cellStyle}>{r.name}</td>
              <td style={cellStyle}>{ROLE_LABEL[r.role] ?? r.role}</td>
              <td style={cellStyle}>{fmtR(r.amount)}</td>
              <td style={cellStyle}><button onClick={() => save(r.componentId, 0)} disabled={saving} style={btnSecondary}>Remove</button></td>
            </tr>
          ))}
        </Table>
      )}
      <div style={{ display: "flex", gap: 8, alignItems: "flex-end", marginTop: 12 }}>
        <Field label="Item">
          <select value={componentId} onChange={e => setComponentId(e.target.value)} style={inputStyle}>
            <option value="">Choose…</option>
            {components.filter(c => c.role !== "BONUS").map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </Field>
        <Field label="Amount"><input type="number" min={0} step="0.01" value={amount} onChange={e => setAmount(e.target.value)} style={inputStyle} /></Field>
        <button onClick={() => save(componentId, Number(amount))} disabled={saving || !componentId || !(Number(amount) > 0)}
          style={{ ...btnPrimary, marginBottom: 12 }}>Set</button>
      </div>
      {error && <ErrorBox text={error} />}
      <div style={{ display: "flex", justifyContent: "flex-end" }}><button onClick={onClose} style={btnSecondary}>Close</button></div>
    </Modal>
  )
}

// ── Per pay run ──────────────────────────────────────────────────────────────

export function RunItemsModal({ run, employees, onClose }: { run: PayRun; employees: PayEmployee[]; onClose: () => void }) {
  const [items, setItems] = useState<RunItem[]>([])
  const [components, setComponents] = useState<PayComponent[]>([])
  const [employeeId, setEmployeeId] = useState("")
  const [componentId, setComponentId] = useState("")
  const [amount, setAmount] = useState("")
  const [note, setNote] = useState("")
  const [error, setError] = useState("")
  const [saving, setSaving] = useState(false)
  const editable = run.status === "DRAFT"
  const nameOf = (id: string) => employees.find(e => e.id === id)?.fullName ?? "Unknown"

  useEffect(() => {
    payrollBureauApi.getRunItems(run.id).then(setItems)
    payrollBureauApi.getPayComponents().then(c => setComponents(c.filter(x => x.status === "ACTIVE")))
  }, [run.id])

  const add = async () => {
    setSaving(true); setError("")
    try {
      setItems(await payrollBureauApi.setRunItem(run.id, { employeeId, componentId, amount: Number(amount), note: note || undefined }))
      setAmount(""); setNote("")
    } catch (e: unknown) { setError(message(e, "Failed to save")) }
    finally { setSaving(false) }
  }
  const remove = async (id: string) => {
    try { setItems(await payrollBureauApi.removeRunItem(run.id, id)) }
    catch (e: unknown) { setError(message(e, "Failed to remove")) }
  }

  return (
    <Modal title={`One-off items: ${run.payRunNumber}`} onClose={onClose}>
      <p style={{ fontSize: 12, color: MUTED, marginTop: 0 }}>
        Overtime, a bonus, a reimbursement or a deduction for this pay run only. {editable ? "" : "The run is no longer a draft, so these can no longer change."}
      </p>
      {items.length === 0 ? <Empty text="No one-off items." /> : (
        <Table headers={["Employee", "Item", "Amount", "Note", ""]}>
          {items.map(i => (
            <tr key={i.id} style={rowStyle}>
              <td style={cellStyle}>{nameOf(i.payEmployeeId)}</td>
              <td style={cellStyle}>{i.name}</td>
              <td style={cellStyle}>{fmtR(i.amount)}</td>
              <td style={{ ...cellStyle, color: INK }}>{i.note ?? ""}</td>
              <td style={cellStyle}>{editable && <button onClick={() => remove(i.id)} style={btnSecondary}>Remove</button>}</td>
            </tr>
          ))}
        </Table>
      )}
      {editable && (
        <div style={{ marginTop: 12 }}>
          <div style={{ display: "flex", gap: 8 }}>
            <Field label="Employee">
              <select value={employeeId} onChange={e => setEmployeeId(e.target.value)} style={inputStyle}>
                <option value="">Choose…</option>
                {employees.filter(e => e.status === "ACTIVE").map(e => <option key={e.id} value={e.id}>{e.fullName}</option>)}
              </select>
            </Field>
            <Field label="Item">
              <select value={componentId} onChange={e => setComponentId(e.target.value)} style={inputStyle}>
                <option value="">Choose…</option>
                {components.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </Field>
          </div>
          <div style={{ display: "flex", gap: 8, alignItems: "flex-end" }}>
            <Field label="Amount"><input type="number" min={0} step="0.01" value={amount} onChange={e => setAmount(e.target.value)} style={inputStyle} /></Field>
            <Field label="Note (optional)"><input value={note} maxLength={200} onChange={e => setNote(e.target.value)} style={inputStyle} /></Field>
            <button onClick={add} disabled={saving || !employeeId || !componentId || !(Number(amount) > 0)}
              style={{ ...btnPrimary, marginBottom: 12 }}>Add</button>
          </div>
        </div>
      )}
      {error && <ErrorBox text={error} />}
      <div style={{ display: "flex", justifyContent: "flex-end" }}><button onClick={onClose} style={btnSecondary}>Close</button></div>
    </Modal>
  )
}
