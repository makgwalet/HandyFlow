// SARS filing: ETI claim flag, EMP201 months, EMP501 reconciliation and tax-certificate figures. Figures for guided filing; nothing is submitted.
import { useEffect, useState } from "react"
import { payrollBureauApi } from "../../api/payrollBureau.api"
import type { PayClient, PayEmployee, Emp201, Emp501, SarsFinding } from "../../types/payrollBureau.types"
import { Table, Empty, Modal, Field, ErrorBox, ModalActions, StatusBadge } from "./ui"
import { currentTaxYear, monthsOfTaxYear, monthLabel } from "./sarsHelpers"
import { fmtR, fmtD, MUTED, FAINT, INK, inputStyle, btnPrimary, btnSecondary, rowStyle, cellStyle } from "./uiStyles"

const message = (e: unknown, fallback: string) =>
  (e as { response?: { data?: { message?: string } } }).response?.data?.message ?? fallback

const REASON_LABEL: Record<string, string> = {
  RULES_NOT_IN_FORCE: "Before the current rules", INCENTIVE_ENDED: "Scheme ended", ID_NOT_QUALIFYING: "ID type does not qualify",
  NO_DATE_OF_BIRTH: "No date of birth", AGE_OUT_OF_RANGE: "Not aged 18 to 29", NO_START_DATE: "No start date",
  EMPLOYED_BEFORE_SCHEME: "Started before 1 Oct 2013", DOMESTIC_WORKER: "Domestic worker", CONNECTED_PERSON: "Connected person",
  BELOW_MINIMUM_WAGE: "Below minimum wage", MONTHS_EXHAUSTED: "24 months used", NO_HOURS_WORKED: "No hours worked",
  NO_REMUNERATION: "No pay", REMUNERATION_ABOVE_LIMIT: "Pay R7,500 or more",
}

function Findings({ items }: { items: SarsFinding[] }) {
  if (items.length === 0) return null
  return (
    <ul style={{ margin: "8px 0", paddingLeft: 18, fontSize: 12.5 }}>
      {items.map((f, i) => (
        <li key={i} style={{ color: f.severity === "BLOCK" ? "var(--hf-danger-text)" : INK, marginBottom: 2 }}>
          <strong>{f.severity === "BLOCK" ? "Must fix: " : "Check: "}</strong>{f.message}
        </li>
      ))}
    </ul>
  )
}

const saveBlob = (blob: Blob, name: string) => {
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement("a"); a.href = url; a.download = name; a.click()
  window.URL.revokeObjectURL(url)
}

export function SarsTab({ client }: { client: PayClient }) {
  const [taxYear, setTaxYear] = useState(currentTaxYear())
  return (
    <div>
      <p style={{ fontSize: 12.5, color: MUTED, margin: "0 0 12px" }}>
        These are the figures to key into SARS eFiling, with checks. Nothing is sent to SARS from here.
      </p>
      <EtiClaimBox client={client} />
      <div style={{ display: "flex", alignItems: "center", gap: 8, margin: "16px 0 8px" }}>
        <label style={{ fontSize: 12, fontWeight: 600, color: MUTED }}>Tax year ending February</label>
        <input type="number" value={taxYear} onChange={e => setTaxYear(Number(e.target.value))} style={{ ...inputStyle, width: 90 }} />
      </div>
      <Emp201Section client={client} taxYear={taxYear} />
      <Emp501Section client={client} taxYear={taxYear} />
    </div>
  )
}

function EtiClaimBox({ client }: { client: PayClient }) {
  const [claimable, setClaimable] = useState(!!client.etiClaimable)
  const [note, setNote] = useState(client.etiClaimableNote ?? "")
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState("")
  const [saved, setSaved] = useState(false)

  const save = async () => {
    setSaving(true); setError(""); setSaved(false)
    try { await payrollBureauApi.setEtiClaim(client.id, claimable, note); setSaved(true) }
    catch (e: unknown) { setError(message(e, "Failed to save")) }
    finally { setSaving(false) }
  }

  return (
    <div style={{ border: "1px solid var(--hf-border)", borderRadius: 8, padding: 12 }}>
      <label style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 13, fontWeight: 600, color: INK }}>
        <input type="checkbox" checked={claimable} onChange={e => setClaimable(e.target.checked)} />
        This client may claim the Employment Tax Incentive
      </label>
      <p style={{ fontSize: 12, color: MUTED, margin: "4px 0 8px" }}>
        Tick this only when the client's SARS returns are up to date and they owe SARS nothing overdue. While it is off, ETI is worked out
        and shown on the EMP201 but not taken off the PAYE.
      </p>
      <input aria-label="Note" placeholder="Note (who checked, and when)" value={note} onChange={e => setNote(e.target.value)} style={{ ...inputStyle, marginBottom: 8 }} />
      {error && <ErrorBox text={error} />}
      <button onClick={save} disabled={saving} style={btnSecondary}>{saving ? "Saving…" : "Save"}</button>
      {saved && <span style={{ marginLeft: 8, fontSize: 12, color: MUTED }}>Saved</span>}
    </div>
  )
}

function Emp201Section({ client, taxYear }: { client: PayClient; taxYear: number }) {
  const [rows, setRows] = useState<Emp201[]>([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState<string | null>(null)
  const [error, setError] = useState("")
  const [open, setOpen] = useState<Emp201 | null>(null)
  const [filing, setFiling] = useState<Emp201 | null>(null)

  const refetch = () => {
    payrollBureauApi.getEmp201s(client.id, taxYear).then(setRows).catch(() => setRows([])).finally(() => setLoading(false))
  }
  useEffect(refetch, [client.id, taxYear])

  const prepare = async (month: string) => {
    setBusy(month); setError("")
    try { await payrollBureauApi.prepareEmp201(client.id, month); refetch() }
    catch (e: unknown) { setError(message(e, "Failed to prepare the EMP201")) }
    finally { setBusy(null) }
  }
  const show = async (month: string) => {
    try { setOpen(await payrollBureauApi.getEmp201(client.id, month)) }
    catch (e: unknown) { setError(message(e, "Failed to load the EMP201")) }
  }
  const byMonth = new Map(rows.map(r => [r.taxMonth.slice(0, 7), r]))

  return (
    <div style={{ marginTop: 8 }}>
      <h4 style={{ fontSize: 13, fontWeight: 800, color: INK, margin: "0 0 8px" }}>EMP201 monthly returns</h4>
      {error && <ErrorBox text={error} />}
      {loading ? <Empty text="Loading…" /> : (
        <Table headers={["Month", "PAYE", "ETI used", "UIF", "SDL", "Payable", "Due", "Status", ""]}>
          {monthsOfTaxYear(taxYear).map(m => {
            const r = byMonth.get(m)
            return (
              <tr key={m} style={rowStyle}>
                <td style={cellStyle}>{monthLabel(m)}</td>
                {r ? <>
                  <td style={cellStyle}>{fmtR(r.paye)}</td>
                  <td style={cellStyle}>{fmtR(r.etiUsed)}</td>
                  <td style={cellStyle}>{fmtR(Number(r.uifEmployee) + Number(r.uifEmployer))}</td>
                  <td style={cellStyle}>{fmtR(r.sdl)}</td>
                  <td style={cellStyle}><strong>{fmtR(r.totalPayable)}</strong></td>
                  <td style={cellStyle}>{fmtD(r.dueDate)}</td>
                  <td style={cellStyle}><StatusBadge status={r.status} /></td>
                </> : <td style={{ ...cellStyle, color: FAINT }} colSpan={7}>Not prepared</td>}
                <td style={cellStyle}>
                  <div style={{ display: "flex", gap: 6 }}>
                    {r?.status !== "FILED" && <button onClick={() => prepare(m)} disabled={busy === m} style={btnSecondary}>{busy === m ? "Working…" : r ? "Prepare again" : "Prepare"}</button>}
                    {r && <button onClick={() => show(m)} style={btnSecondary}>Details</button>}
                    {r && r.status === "DRAFT" && <button onClick={() => setFiling(r)} style={btnPrimaryLite}>Mark filed</button>}
                  </div>
                </td>
              </tr>
            )
          })}
        </Table>
      )}
      {open && <Emp201Details ret={open} onClose={() => setOpen(null)} />}
      {filing && <FileEmp201Modal clientId={client.id} ret={filing} onClose={() => setFiling(null)} onFiled={() => { setFiling(null); refetch() }} />}
    </div>
  )
}

const btnPrimaryLite = btnPrimary

function Emp201Details({ ret, onClose }: { ret: Emp201; onClose: () => void }) {
  const rows: [string, number][] = [
    ["PAYE withheld", ret.paye], ["ETI calculated", ret.etiCalculated], ["ETI brought forward", ret.etiBroughtForward],
    ["ETI taken off the PAYE", ret.etiUsed], ["ETI carried forward", ret.etiCarriedForward],
    ["UIF (employee and employer)", Number(ret.uifEmployee) + Number(ret.uifEmployer)], ["SDL", ret.sdl], ["Total payable", ret.totalPayable],
  ]
  return (
    <Modal title={`EMP201 · ${monthLabel(ret.taxMonth.slice(0, 7))}`} onClose={onClose}>
      <p style={{ fontSize: 12, color: MUTED, margin: "0 0 8px" }}>
        Pay runs: {ret.runNumbers ?? "—"} · {ret.employeeCount} employees · due {fmtD(ret.dueDate)}
        {ret.filingReference && <> · eFiling reference {ret.filingReference}</>}
      </p>
      {!ret.etiClaimed && Number(ret.etiCalculated) > 0 && (
        <ErrorBox text="This client is not marked as allowed to claim ETI, so the ETI below was not taken off the PAYE." />
      )}
      <table style={{ width: "100%", fontSize: 12.5, marginBottom: 12 }}><tbody>
        {rows.map(([k, v]) => <tr key={k}><td style={{ padding: "3px 0", color: MUTED }}>{k}</td><td style={{ textAlign: "right" as const }}>{fmtR(v)}</td></tr>)}
      </tbody></table>
      {ret.etiLines.length > 0 && (
        <Table headers={["Employee", "Pay", "ETI", "Why not"]}>
          {ret.etiLines.map(l => (
            <tr key={l.employeeId} style={rowStyle}>
              <td style={cellStyle}>{l.employeeName}</td>
              <td style={cellStyle}>{fmtR(l.remuneration)}</td>
              <td style={cellStyle}>{l.eligible ? fmtR(l.incentive) : "—"}</td>
              <td style={{ ...cellStyle, color: MUTED }}>{l.reasons.map(r => REASON_LABEL[r] ?? r).join(", ")}</td>
            </tr>
          ))}
        </Table>
      )}
      <ModalActions onClose={onClose} onSubmit={onClose} saving={false} submitLabel="Close" />
    </Modal>
  )
}

function FileEmp201Modal({ clientId, ret, onClose, onFiled }: { clientId: string; ret: Emp201; onClose: () => void; onFiled: () => void }) {
  const [reference, setReference] = useState("")
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState("")
  const submit = async () => {
    setSaving(true); setError("")
    try { await payrollBureauApi.fileEmp201(clientId, ret.taxMonth.slice(0, 7), reference); onFiled() }
    catch (e: unknown) { setError(message(e, "Failed to record the filing")); setSaving(false) }
  }
  return (
    <Modal title={`Mark ${monthLabel(ret.taxMonth.slice(0, 7))} as filed`} onClose={onClose}>
      <p style={{ fontSize: 12.5, color: MUTED, margin: "0 0 12px" }}>
        Do this after the return is submitted and paid on SARS eFiling. The figures are then fixed as the record of what was sent.
      </p>
      {error && <ErrorBox text={error} />}
      <Field label="eFiling reference or payment reference (optional)">
        <input value={reference} onChange={e => setReference(e.target.value)} style={inputStyle} />
      </Field>
      <ModalActions onClose={onClose} onSubmit={submit} saving={saving} submitLabel="Mark as filed" />
    </Modal>
  )
}

function Emp501Section({ client, taxYear }: { client: PayClient; taxYear: number }) {
  const [period, setPeriod] = useState<"INTERIM" | "ANNUAL">("ANNUAL")
  const [data, setData] = useState<Emp501 | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")

  const load = async () => {
    setLoading(true); setError(""); setData(null)
    try { setData(await payrollBureauApi.getEmp501(client.id, period, taxYear)) }
    catch (e: unknown) { setError(message(e, "Failed to build the reconciliation")) }
    finally { setLoading(false) }
  }
  const download = async () => {
    try { saveBlob(await payrollBureauApi.downloadEmp501WorkingPaper(client.id, period, taxYear), `tax-certificates-${period.toLowerCase()}-${taxYear}.csv`) }
    catch (e: unknown) { setError(message(e, "Failed to download")) }
  }

  return (
    <div style={{ marginTop: 24 }}>
      <h4 style={{ fontSize: 13, fontWeight: 800, color: INK, margin: "0 0 4px" }}>EMP501 reconciliation and tax certificates</h4>
      <p style={{ fontSize: 12, color: MUTED, margin: "0 0 8px" }}>
        Interim covers March to August; annual covers March to February. SARS's certificate file is not made here: use these figures and checks in e@syFile or eFiling.
      </p>
      <div style={{ display: "flex", gap: 8, marginBottom: 12 }}>
        <select aria-label="Period" value={period} onChange={e => setPeriod(e.target.value as "INTERIM" | "ANNUAL")} style={{ ...inputStyle, width: 160 }}>
          <option value="ANNUAL">Annual (Mar–Feb)</option>
          <option value="INTERIM">Interim (Mar–Aug)</option>
        </select>
        <button onClick={load} disabled={loading} style={btnPrimary}>{loading ? "Working…" : "Build reconciliation"}</button>
        {data && <button onClick={download} style={btnSecondary}>Download working paper</button>}
      </div>
      {error && <ErrorBox text={error} />}
      {data && (
        <div>
          <p style={{ fontSize: 13, fontWeight: 700, color: data.blocked ? "var(--hf-danger-text)" : INK }}>
            {data.blocked ? "Not ready: some items must be fixed first." : data.balanced ? "The EMP201 figures and the certificates agree." : "Ready, with differences to explain."}
            {" "}{data.certificateCount} certificates, {fmtD(data.from)} to {fmtD(data.to)}.
          </p>
          <Table headers={["", "EMP201 returns", "Certificates", "Difference"]}>
            {data.differences.map(d => (
              <tr key={d.what} style={rowStyle}>
                <td style={cellStyle}>{d.what}</td><td style={cellStyle}>{fmtR(d.emp201)}</td>
                <td style={cellStyle}>{fmtR(d.certificates)}</td>
                <td style={{ ...cellStyle, fontWeight: Number(d.difference) !== 0 ? 700 : 400 }}>{fmtR(d.difference)}</td>
              </tr>
            ))}
          </Table>
          <p style={{ fontSize: 12.5, color: MUTED }}>
            ETI taken off the PAYE: {fmtR(data.etiUsed)}. Unused ETI at the end of the period (refundable on the EMP501): {fmtR(data.etiUnusedAtEnd)}.
          </p>
          <Findings items={data.findings} />
          <Table headers={["Employee", "Type", "Taxable (3699)", "Non-taxable (3696)", "PAYE (4102)", "UIF (4141)", "SDL (4142)", ""]}>
            {data.certificates.map(c => (
              <tr key={c.employeeId} style={rowStyle}>
                <td style={cellStyle}>{c.employeeName}{c.findings.length > 0 && <Findings items={c.findings} />}</td>
                <td style={cellStyle}>{c.type}</td>
                <td style={cellStyle}>{fmtR(c.grossTaxable3699)}</td><td style={cellStyle}>{fmtR(c.nonTaxable3696)}</td>
                <td style={cellStyle}>{fmtR(c.paye4102)}</td><td style={cellStyle}>{fmtR(c.uif4141)}</td><td style={cellStyle}>{fmtR(c.sdl4142)}</td>
                <td style={cellStyle}>{c.blocked && <StatusBadge status="REVERSED" />}</td>
              </tr>
            ))}
          </Table>
        </div>
      )}
    </div>
  )
}

// ── ETI facts of one employee ────────────────────────────────────────────────

export function EmployeeEtiModal({ clientId, employee, onClose, onSaved }: { clientId: string; employee: PayEmployee; onClose: () => void; onSaved: () => void }) {
  const [idKind, setIdKind] = useState(employee.etiIdKind ?? "")
  const [domestic, setDomestic] = useState(!!employee.domesticWorker)
  const [connected, setConnected] = useState(!!employee.connectedPerson)
  const [belowMin, setBelowMin] = useState(!!employee.belowMinimumWage)
  const [months, setMonths] = useState(String(employee.etiMonthsPrior ?? 0))
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState("")

  const submit = async () => {
    const n = Number(months)
    if (!Number.isInteger(n) || n < 0 || n > 24) { setError("Months already claimed must be a whole number from 0 to 24."); return }
    setSaving(true); setError("")
    try {
      await payrollBureauApi.setEmployeeEti(clientId, employee.id, {
        idKind: idKind || null, domesticWorker: domestic, connectedPerson: connected, belowMinimumWage: belowMin, monthsPrior: n })
      onSaved()
    } catch (e: unknown) { setError(message(e, "Failed to save")); setSaving(false) }
  }

  return (
    <Modal title={`ETI facts · ${employee.fullName}`} onClose={onClose}>
      <p style={{ fontSize: 12, color: MUTED, margin: "0 0 12px" }}>
        Age and start date come from the employee record. ETI is for employees aged 18 to 29 who started on or after 1 October 2013.
      </p>
      {error && <ErrorBox text={error} />}
      <Field label="ID type">
        <select value={idKind} onChange={e => setIdKind(e.target.value)} style={inputStyle}>
          <option value="">Work out from the ID number</option>
          <option value="SA_ID">South African ID</option>
          <option value="ASYLUM">Asylum seeker permit</option>
          <option value="REFUGEE">Refugee ID</option>
          <option value="OTHER">Other (does not qualify)</option>
        </select>
      </Field>
      <label style={{ display: "block", fontSize: 13, marginBottom: 6 }}><input type="checkbox" checked={domestic} onChange={e => setDomestic(e.target.checked)} /> Domestic worker</label>
      <label style={{ display: "block", fontSize: 13, marginBottom: 6 }}><input type="checkbox" checked={connected} onChange={e => setConnected(e.target.checked)} /> Connected person to the employer</label>
      <label style={{ display: "block", fontSize: 13, marginBottom: 12 }}><input type="checkbox" checked={belowMin} onChange={e => setBelowMin(e.target.checked)} /> Paid below the minimum wage (no ETI)</label>
      <Field label="Months of ETI already claimed before joining the bureau (0 to 24)">
        <input value={months} onChange={e => setMonths(e.target.value)} style={inputStyle} inputMode="numeric" />
      </Field>
      <ModalActions onClose={onClose} onSubmit={submit} saving={saving} submitLabel="Save" />
    </Modal>
  )
}
