// src/pages/hr/TaxCertificatesTab.tsx
//
// Tax certificates (IRP5 / IT3(a)) and the reconciliation with the EMP201 returns. The server builds and checks everything;
// this screen shows what it found, and issues only when nothing must be fixed. The spreadsheet is a workpaper; the e@syFile file is the import file
// for SARS's own program, and SARS checks it on upload.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { AlertCircle, CheckCircle2, ChevronDown, ChevronUp, Download } from "lucide-react"
import { apiClient } from "../../api/client"
import { useAuth } from "../../hooks/useAuth"
import {
  countBySeverity, defaultSelection, FUND_LABEL, issueSummary, lineAmount, needsReason, periodLabel, rand, reasonProblem, SEVERITY_STYLE,
  settingsProblem, sic7Problem, taxRights, TYPE_LABEL, yearOptions, type Period, type Problem,
} from "./taxCertificates.logic"

type Wrapped = { data?: { data?: unknown } }
type ApiErr = { response?: { data?: { message?: string } } }
const unwrap = <T,>(r: Wrapped): T => (r.data?.data ?? r.data) as T
const errorText = (e: unknown, fallback: string) => (e as ApiErr)?.response?.data?.message ?? fallback

interface Line { code: string; description: string; amount: number }
interface Recon { tax: string; payslips: number; certificates: number; emp201: number; difference: number; balanced: boolean }
interface Cert {
  id: string | null; employeeId: string; employeeNumber: string; employeeName: string; idNumber: string | null; taxNumber: string | null
  certificateNumber: string | null; certificateType: string; version: number; status: string; state: string
  payslipCount: number; releasedAt: string | null; lines: Line[]; problems: Problem[]
}
interface Reconciliation {
  taxYear: number; period: Period; runs: number; canIssue: boolean; interim: boolean
  problems: Problem[]; reconciliation: Recon[]; certificates: Cert[]; notProduced: string[]
}
interface Settings {
  payeReference: string | null; sdlReference: string | null; uifReference: string | null; retirementFundType: string
  contactFirstName?: string | null; contactSurname?: string | null; sic7Code?: string | null
}

const card: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 12, background: "var(--hf-surface)", marginBottom: 20, overflow: "hidden" }
const head: React.CSSProperties = { padding: "14px 18px", borderBottom: "1px solid var(--hf-border-subtle)" }
const th: React.CSSProperties = { textAlign: "left", padding: "9px 12px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase", letterSpacing: "0.05em", whiteSpace: "nowrap" }
const td: React.CSSProperties = { padding: "10px 12px", fontSize: 13, color: "var(--hf-text)", borderTop: "1px solid var(--hf-border-subtle)", verticalAlign: "top" }
const input: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 13, boxSizing: "border-box" }
const label: React.CSSProperties = { display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4 }
const primary: React.CSSProperties = { padding: "9px 18px", background: "var(--hf-primary)", color: "var(--hf-text-on-solid)", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 700, cursor: "pointer" }
const plain: React.CSSProperties = { padding: "8px 14px", background: "var(--hf-surface)", color: "var(--hf-text-secondary)", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer" }

function Pill({ text, color, bg }: { text: string; color: string; bg: string }) {
  return <span style={{ background: bg, color, padding: "2px 10px", borderRadius: 20, fontSize: 11, fontWeight: 700, whiteSpace: "nowrap" }}>{text}</span>
}

export default function TaxCertificatesTab() {
  const qc = useQueryClient()
  const { user } = useAuth() as { user?: { permissions?: string[] } }
  const rights = taxRights(user?.permissions ?? [])
  const start = defaultSelection(new Date())
  const [taxYear, setTaxYear] = useState(start.taxYear)
  const [period, setPeriod] = useState<Period>(start.period)
  const [reason, setReason] = useState("")
  const [open, setOpen] = useState<string | null>(null)
  const [cancelReason, setCancelReason] = useState("")
  const [message, setMessage] = useState("")
  const [error, setError] = useState("")
  const [easyOpen, setEasyOpen] = useState(false)

  const recon = useQuery<Reconciliation>({
    queryKey: ["tax-reconciliation", taxYear, period],
    queryFn: async () => unwrap<Reconciliation>(await apiClient.get(`/api/v1/hr/tax-certificates/reconciliation?taxYear=${taxYear}&period=${period}`)),
    enabled: rights.view,
  })
  const settings = useQuery<Settings>({
    queryKey: ["tax-settings"],
    queryFn: async () => unwrap<Settings>(await apiClient.get("/api/v1/hr/tax-certificates/settings")),
    enabled: rights.view,
  })

  // The form shows what is saved, with whatever has been typed over it.
  type Form = {
    payeReference: string; sdlReference: string; uifReference: string; retirementFundType: string
    contactFirstName: string; contactSurname: string; sic7Code: string
  }
  const [edits, setEdits] = useState<Partial<Form>>({})
  const saved: Form = {
    payeReference: settings.data?.payeReference ?? "", sdlReference: settings.data?.sdlReference ?? "",
    uifReference: settings.data?.uifReference ?? "", retirementFundType: settings.data?.retirementFundType ?? "PENSION",
    contactFirstName: settings.data?.contactFirstName ?? "", contactSurname: settings.data?.contactSurname ?? "", sic7Code: settings.data?.sic7Code ?? "",
  }
  const form: Form = { ...saved, ...edits }
  const setForm = (f: (cur: Form) => Form) => setEdits(f(form))

  const refresh = () => { qc.invalidateQueries({ queryKey: ["tax-reconciliation"] }); qc.invalidateQueries({ queryKey: ["tax-settings"] }) }

  const saveSettings = useMutation({
    mutationFn: () => apiClient.put("/api/v1/hr/tax-certificates/settings", form),
    onSuccess: () => { setMessage("Tax settings saved."); setError(""); setEdits({}); refresh() },
    onError: (e) => setError(errorText(e, "Failed to save the tax settings")),
  })
  const issue = useMutation({
    mutationFn: () => apiClient.post("/api/v1/hr/tax-certificates/issue", { taxYear, period, reason: reason.trim() || null }),
    onSuccess: (r) => {
      const res = unwrap<{ issued: number; unchanged: number; replaced: number }>(r as Wrapped)
      setMessage(`${res.issued} certificate(s) issued${res.replaced ? `, ${res.replaced} replaced` : ""}${res.unchanged ? `, ${res.unchanged} already issued and unchanged` : ""}.`)
      setError(""); setReason(""); refresh()
    },
    onError: (e) => setError(errorText(e, "Certificates could not be issued")),
  })
  const cancel = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/hr/tax-certificates/${id}/cancel`, { reason: cancelReason.trim() }),
    onSuccess: () => { setMessage("Certificate cancelled."); setError(""); setCancelReason(""); setOpen(null); refresh() },
    onError: (e) => setError(errorText(e, "The certificate could not be cancelled")),
  })
  const release = useMutation({
    mutationFn: () => apiClient.post("/api/v1/hr/tax-certificates/release", { taxYear }),
    onSuccess: () => { setMessage("The annual certificates can now be seen by the employees."); setError(""); refresh() },
    onError: (e) => setError(errorText(e, "The certificates could not be released")),
  })

  const easyCheck = useQuery<string[]>({
    queryKey: ["tax-easyfile", taxYear, period],
    queryFn: async () => unwrap<string[]>(await apiClient.get(`/api/v1/hr/tax-certificates/easyfile/check?taxYear=${taxYear}&period=${period}`)),
    enabled: rights.view && easyOpen,
  })

  const save = (blob: Blob, name: string) => {
    const url = URL.createObjectURL(blob)
    const a = document.createElement("a"); a.href = url; a.download = name; a.click()
    URL.revokeObjectURL(url)
  }
  const downloadEasy = async (live: boolean) => {
    try {
      const res = await apiClient.get(`/api/v1/hr/tax-certificates/export.easyfile.csv?taxYear=${taxYear}&period=${period}&live=${live}`, { responseType: "blob" })
      save(new Blob([res.data], { type: "text/csv" }), `easyfile-${taxYear}-${period.toLowerCase()}${live ? "" : "-test"}.csv`)
    } catch (e) { setError(errorText(e, "The e@syFile file could not be made")) }
  }
  const downloadPdf = async (id: string) => {
    try {
      const res = await apiClient.get(`/api/v1/hr/tax-certificates/${id}/pdf`, { responseType: "blob" })
      save(new Blob([res.data], { type: "application/pdf" }), `tax-certificate-${id}.pdf`)
    } catch { setError("The certificate PDF could not be downloaded") }
  }

  const download = async () => {
    try {
      const res = await apiClient.get(`/api/v1/hr/tax-certificates/export.csv?taxYear=${taxYear}&period=${period}`, { responseType: "blob" })
      const url = URL.createObjectURL(new Blob([res.data], { type: "text/csv" }))
      const a = document.createElement("a"); a.href = url; a.download = `tax-certificates-${taxYear}-${period.toLowerCase()}.csv`; a.click()
      URL.revokeObjectURL(url)
    } catch { setError("The spreadsheet could not be downloaded") }
  }

  if (!rights.view) {
    return <div role="alert" style={{ padding: 24, color: "var(--hf-text-muted)", fontSize: 14 }}>You do not have access to tax certificates. Ask for the statutory export permission.</div>
  }

  const data = recon.data
  const certs = data?.certificates ?? []
  const counts = countBySeverity([...(data?.problems ?? []), ...certs.flatMap(c => c.problems)])
  const reasonNeeded = needsReason(certs)
  const reasonMissing = reasonNeeded && !!reasonProblem(reason, "replacing certificates")
  const hasIssued = certs.some(c => c.certificateNumber)
  const payeProblem = settingsProblem(form.payeReference)
  const sicProblem = sic7Problem(form.sic7Code)

  return (
    <div>
      <div style={{ ...card, padding: "14px 18px" }}>
        <div style={{ display: "flex", gap: 12, flexWrap: "wrap", alignItems: "flex-end" }}>
          <div><label style={label} htmlFor="tc-year">Tax year</label>
            <select id="tc-year" value={taxYear} onChange={e => setTaxYear(Number(e.target.value))} style={{ ...input, width: 120, background: "var(--hf-surface)" }}>
              {yearOptions(new Date()).map(y => <option key={y} value={y}>{y}</option>)}
            </select></div>
          <div><label style={label} htmlFor="tc-period">Reconciliation</label>
            <select id="tc-period" value={period} onChange={e => setPeriod(e.target.value as Period)} style={{ ...input, width: 160, background: "var(--hf-surface)" }}>
              <option value="INTERIM">Interim (Mar–Aug)</option>
              <option value="ANNUAL">Annual (Mar–Feb)</option>
            </select></div>
          <div style={{ fontSize: 12, color: "var(--hf-text-muted)", flex: 1, minWidth: 200 }}>{periodLabel(taxYear, period)}. SARS publishes each year's submission dates; check sars.gov.za.</div>
        </div>
      </div>

      <div style={{ ...card, padding: "14px 18px", background: "var(--hf-warning-soft)", borderColor: "var(--hf-warning-border)" }}>
        <div style={{ fontWeight: 700, fontSize: 13, color: "var(--hf-warning-text)", marginBottom: 6 }}>These certificates are a workpaper</div>
        <p style={{ margin: "0 0 8px", fontSize: 12, color: "var(--hf-warning-text-deep)" }}>
          Only the codes that match payslip figures are produced. Import the e@syFile test file and check it against the program before you submit. Left out:
        </p>
        <ul style={{ margin: 0, paddingLeft: 18, fontSize: 12, color: "var(--hf-warning-text-deep)" }}>
          {(data?.notProduced ?? []).map(n => <li key={n}>{n}</li>)}
        </ul>
        <p style={{ margin: "8px 0 0", fontSize: 12, color: "var(--hf-warning-text-deep)" }}>
          Interim certificates are for SARS only and are not given to employees. Annual certificates can be released to employees once the annual reconciliation is done.
        </p>
      </div>

      {message && <div role="status" style={{ marginBottom: 14, padding: "10px 14px", background: "var(--hf-success-soft)", color: "var(--hf-success-text-strong)", borderRadius: 8, fontSize: 13, display: "flex", gap: 8, alignItems: "center" }}><CheckCircle2 size={14} />{message}</div>}
      {error && <div role="alert" style={{ marginBottom: 14, padding: "10px 14px", background: "var(--hf-danger-soft)", color: "var(--hf-danger-text)", borderRadius: 8, fontSize: 13, display: "flex", gap: 8, alignItems: "center" }}><AlertCircle size={14} />{error}</div>}

      <section style={card} aria-labelledby="tc-settings-h">
        <div style={head}><h3 id="tc-settings-h" style={{ margin: 0, fontSize: 15, fontWeight: 700, color: "var(--hf-text)" }}>Employer tax details</h3></div>
        <div style={{ padding: "14px 18px", display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(190px, 1fr))", gap: 14 }}>
          {rights.configure ? <>
            <div><label style={label} htmlFor="tc-paye">PAYE reference number</label>
              <input id="tc-paye" value={form.payeReference} onChange={e => setForm(f => ({ ...f, payeReference: e.target.value }))} style={input} />
              {payeProblem && <div style={{ fontSize: 12, color: "var(--hf-danger-text)", marginTop: 4 }}>{payeProblem}</div>}</div>
            <div><label style={label} htmlFor="tc-sdl">SDL reference number</label>
              <input id="tc-sdl" value={form.sdlReference} onChange={e => setForm(f => ({ ...f, sdlReference: e.target.value }))} style={input} /></div>
            <div><label style={label} htmlFor="tc-uif">UIF reference number</label>
              <input id="tc-uif" value={form.uifReference} onChange={e => setForm(f => ({ ...f, uifReference: e.target.value }))} style={input} /></div>
            <div><label style={label} htmlFor="tc-fund">Retirement fund</label>
              <select id="tc-fund" value={form.retirementFundType} onChange={e => setForm(f => ({ ...f, retirementFundType: e.target.value }))} style={{ ...input, background: "var(--hf-surface)" }}>
                {Object.entries(FUND_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
              </select></div>
            <div><label style={label} htmlFor="tc-contact-first">Contact person: first name</label>
              <input id="tc-contact-first" value={form.contactFirstName} onChange={e => setForm(f => ({ ...f, contactFirstName: e.target.value }))} style={input} /></div>
            <div><label style={label} htmlFor="tc-contact-last">Contact person: surname</label>
              <input id="tc-contact-last" value={form.contactSurname} onChange={e => setForm(f => ({ ...f, contactSurname: e.target.value }))} style={input} /></div>
            <div><label style={label} htmlFor="tc-sic7">SIC7 industry code</label>
              <input id="tc-sic7" value={form.sic7Code} onChange={e => setForm(f => ({ ...f, sic7Code: e.target.value }))} style={input} />
              {sicProblem && <div style={{ fontSize: 12, color: "var(--hf-danger-text)", marginTop: 4 }}>{sicProblem}</div>}</div>
            <div style={{ alignSelf: "end" }}>
              <button onClick={() => saveSettings.mutate()} disabled={!!payeProblem || !!sicProblem || saveSettings.isPending} style={{ ...primary, opacity: payeProblem || sicProblem ? 0.6 : 1 }}>Save tax settings</button></div>
          </> : <div style={{ fontSize: 13, color: "var(--hf-text-secondary)" }}>
            PAYE reference: {settings.data?.payeReference ?? "not set"} · Retirement fund: {FUND_LABEL[settings.data?.retirementFundType ?? "PENSION"]}.
            Changing these needs the payroll configuration permission.
          </div>}
        </div>
      </section>

      {recon.isLoading && <div style={{ padding: 24, color: "var(--hf-text-faint)" }}>Checking the pay runs…</div>}
      {recon.isError && <div role="alert" style={{ padding: 16, color: "var(--hf-danger-text)" }}>{errorText(recon.error, "The reconciliation could not be loaded")}</div>}

      {data && <>
        <section style={card} aria-labelledby="tc-recon-h">
          <div style={head}>
            <h3 id="tc-recon-h" style={{ margin: 0, fontSize: 15, fontWeight: 700, color: "var(--hf-text)" }}>Payslips, certificates and EMP201 returns</h3>
            <p style={{ margin: "4px 0 0", fontSize: 12, color: "var(--hf-text-muted)" }}>{data.runs} finalised pay run(s) in the period. The three must agree before certificates are issued.</p>
          </div>
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr><th style={th}>Tax</th><th style={th}>Payslips</th><th style={th}>Certificates</th><th style={th}>EMP201</th><th style={th}>Difference</th><th style={th}>Result</th></tr></thead>
              <tbody>
                {data.reconciliation.map(r => (
                  <tr key={r.tax}>
                    <td style={{ ...td, fontWeight: 700 }}>{r.tax}</td><td style={td}>{rand(r.payslips)}</td><td style={td}>{rand(r.certificates)}</td><td style={td}>{rand(r.emp201)}</td>
                    <td style={td}>{rand(r.difference)}</td>
                    <td style={td}>{r.balanced ? <Pill text="Agrees" color="var(--hf-success-text-strong)" bg="var(--hf-success-soft-strong)" /> : <Pill text="Differs" color="var(--hf-danger-text)" bg="var(--hf-danger-soft)" />}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>

        {(data.problems.length > 0) && (
          <section style={card} aria-labelledby="tc-problems-h">
            <div style={head}><h3 id="tc-problems-h" style={{ margin: 0, fontSize: 15, fontWeight: 700, color: "var(--hf-text)" }}>Checks ({counts.errors} to fix, {counts.warnings} to look at)</h3></div>
            <div>{data.problems.map((p, i) => {
              const s = SEVERITY_STYLE[p.severity] ?? SEVERITY_STYLE.INFO
              return <div key={i} style={{ ...td, display: "flex", gap: 10, alignItems: "baseline" }}><Pill text={s.label} color={s.color} bg={s.bg} /><div><strong>{p.subject}</strong>: {p.message}</div></div>
            })}</div>
          </section>
        )}

        <section style={card} aria-labelledby="tc-certs-h">
          <div style={{ ...head, display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
            <div>
              <h3 id="tc-certs-h" style={{ margin: 0, fontSize: 15, fontWeight: 700, color: "var(--hf-text)" }}>Certificates ({certs.length})</h3>
              <p style={{ margin: "4px 0 0", fontSize: 12, color: "var(--hf-text-muted)" }}>{issueSummary(certs)}</p>
            </div>
            <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
              {hasIssued && <button onClick={download} style={{ ...plain, display: "flex", alignItems: "center", gap: 6 }}><Download size={13} /> Download spreadsheet</button>}
              {hasIssued && <button onClick={() => setEasyOpen(o => !o)} aria-expanded={easyOpen} style={{ ...plain, display: "flex", alignItems: "center", gap: 6 }}><Download size={13} /> e@syFile file</button>}
              {rights.issue && period === "ANNUAL" && hasIssued && <button onClick={() => release.mutate()} disabled={release.isPending} style={plain}>Release to employees</button>}
              {rights.issue && <button onClick={() => issue.mutate()} disabled={!data.canIssue || reasonMissing || issue.isPending} style={{ ...primary, opacity: !data.canIssue || reasonMissing ? 0.6 : 1 }}>Issue certificates</button>}
            </div>
          </div>
          {easyOpen && hasIssued && (
            <div role="region" aria-label="e@syFile import file" style={{ padding: "12px 18px", borderBottom: "1px solid var(--hf-border-subtle)", fontSize: 13 }}>
              <div style={{ fontWeight: 700, marginBottom: 4 }}>File for SARS e@syFile Employer</div>
              <p style={{ margin: "0 0 8px", fontSize: 12, color: "var(--hf-text-muted)" }}>
                Made from the issued certificates. Import the test file first: e@syFile shows an error report on upload, and rejects the whole file if there is one.
                An IT3(a) carries its reason code (4150), and employees who claimed the employment tax incentive carry a set of incentive codes (7002 to 7008) for each month, worked out from their payslips; check both on the e@syFile error report. An employee needs a 13-digit ID number, or a passport with its country.
              </p>
              {easyCheck.isLoading && <div style={{ color: "var(--hf-text-faint)" }}>Checking…</div>}
              {easyCheck.isError && <div role="alert" style={{ color: "var(--hf-danger-text)" }}>{errorText(easyCheck.error, "The file could not be checked")}</div>}
              {easyCheck.data && easyCheck.data.length > 0 && (
                <div>
                  <div style={{ color: "var(--hf-danger-text)", fontWeight: 600, marginBottom: 4 }}>{easyCheck.data.length} thing(s) to fix before the file can be made:</div>
                  <ul style={{ margin: 0, paddingLeft: 18, color: "var(--hf-danger-text)", fontSize: 12 }}>{easyCheck.data.map((m, i) => <li key={i}>{m}</li>)}</ul>
                </div>
              )}
              {easyCheck.data && easyCheck.data.length === 0 && (
                <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
                  <button onClick={() => downloadEasy(false)} style={primary}>Download test file</button>
                  <button onClick={() => downloadEasy(true)} style={plain}>Download live file</button>
                  <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>The live file is the one you submit to SARS.</span>
                </div>
              )}
            </div>
          )}
          {rights.issue && reasonNeeded && (
            <div style={{ padding: "12px 18px", borderBottom: "1px solid var(--hf-border-subtle)" }}>
              <label style={label} htmlFor="tc-reason">Reason for replacing issued certificates</label>
              <input id="tc-reason" value={reason} onChange={e => setReason(e.target.value)} style={input} placeholder="For example: back pay added to the May run" />
            </div>
          )}
          {certs.length === 0 ? <div style={{ padding: 24, color: "var(--hf-text-faint)", fontSize: 13 }}>No payslips from finalised pay runs fall in this period.</div> : (
            <div style={{ overflowX: "auto" }}>
              <table style={{ width: "100%", borderCollapse: "collapse" }}>
                <thead><tr><th style={th}>Employee</th><th style={th}>Type</th><th style={th}>Payslips</th><th style={th}>Certificate number</th><th style={th}>Status</th><th style={th} /></tr></thead>
                <tbody>
                  {certs.map(c => {
                    const key = c.employeeId
                    const isOpen = open === key
                    const errs = c.problems.filter(p => p.severity === "ERROR").length
                    return <CertRow key={key} c={c} isOpen={isOpen} errs={errs} onToggle={() => setOpen(isOpen ? null : key)}
                      canCancel={rights.issue && !!c.id && c.state !== "NEW"} cancelReason={cancelReason} setCancelReason={setCancelReason}
                      onCancel={() => c.id && cancel.mutate(c.id)} cancelling={cancel.isPending} onPdf={() => c.id && downloadPdf(c.id)} />
                  })}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </>}
    </div>
  )
}

function CertRow({ c, isOpen, errs, onToggle, canCancel, cancelReason, setCancelReason, onCancel, cancelling, onPdf }: {
  c: Cert; isOpen: boolean; errs: number; onToggle: () => void; canCancel: boolean
  cancelReason: string; setCancelReason: (v: string) => void; onCancel: () => void; cancelling: boolean; onPdf: () => void
}) {
  const state: Record<string, { text: string; color: string; bg: string }> = {
    NEW: { text: "To issue", color: "var(--hf-info-text)", bg: "var(--hf-info-soft)" },
    CHANGED: { text: "Figures changed", color: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)" },
    UNCHANGED: { text: "Issued", color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)" },
  }
  const s = state[c.state] ?? state.NEW
  return <>
    <tr onClick={onToggle} style={{ cursor: "pointer" }}>
      <td style={td}><div style={{ fontWeight: 600 }}>{c.employeeName}</div><div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{c.employeeNumber}</div></td>
      <td style={td}>{TYPE_LABEL[c.certificateType] ?? c.certificateType}</td>
      <td style={td}>{c.payslipCount}</td>
      <td style={{ ...td, fontFamily: "monospace", fontSize: 12 }}>{c.certificateNumber ?? "Given when issued"}{c.certificateNumber && c.version > 1 ? ` (v${c.version})` : ""}</td>
      <td style={td}><Pill text={s.text} color={s.color} bg={s.bg} />{errs > 0 && <> <Pill text={`${errs} to fix`} color="var(--hf-danger-text)" bg="var(--hf-danger-soft)" /></>}{c.releasedAt && <> <Pill text="Released" color="var(--hf-info-text)" bg="var(--hf-info-soft)" /></>}</td>
      <td style={td}>{isOpen ? <ChevronUp size={14} /> : <ChevronDown size={14} />}</td>
    </tr>
    {isOpen && (
      <tr><td colSpan={6} style={{ ...td, background: "var(--hf-surface-muted)" }}>
        <div style={{ fontSize: 12, color: "var(--hf-text-muted)", marginBottom: 8 }}>ID {c.idNumber ?? "not recorded"} · Tax number {c.taxNumber ?? "not recorded"}</div>
        {c.problems.map((p, i) => { const sv = SEVERITY_STYLE[p.severity] ?? SEVERITY_STYLE.INFO; return <div key={i} style={{ fontSize: 12, color: sv.color, marginBottom: 4 }}>{sv.label}: {p.message}</div> })}
        <table style={{ borderCollapse: "collapse", marginTop: 6 }}>
          <thead><tr><th style={th}>Code</th><th style={th}>Description</th><th style={{ ...th, textAlign: "right" }}>Amount</th></tr></thead>
          <tbody>{c.lines.map(l => <tr key={l.code}><td style={td}>{l.code}</td><td style={td}>{l.description}</td><td style={{ ...td, textAlign: "right" }}>{lineAmount(l.code, l.amount)}</td></tr>)}</tbody>
        </table>
        {c.id && c.certificateNumber && (
          <div style={{ marginTop: 10 }}>
            <button onClick={e => { e.stopPropagation(); onPdf() }} style={{ ...plain, display: "flex", alignItems: "center", gap: 6 }}><Download size={13} /> Certificate PDF</button>
          </div>
        )}
        {canCancel && (
          <div style={{ marginTop: 12, display: "flex", gap: 8, alignItems: "flex-end", flexWrap: "wrap" }}>
            <div style={{ flex: 1, minWidth: 220 }}><label style={label} htmlFor={`tc-cancel-${c.employeeId}`}>Reason for cancelling</label>
              <input id={`tc-cancel-${c.employeeId}`} value={cancelReason} onChange={e => setCancelReason(e.target.value)} onClick={e => e.stopPropagation()} style={input} /></div>
            <button onClick={e => { e.stopPropagation(); onCancel() }} disabled={!cancelReason.trim() || cancelling} style={plain}>Cancel certificate</button>
          </div>
        )}
      </td></tr>
    )}
  </>
}
