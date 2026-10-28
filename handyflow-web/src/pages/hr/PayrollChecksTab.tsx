// src/pages/hr/PayrollChecksTab.tsx
//
// Read-only checks for payroll: the statutory rules each tax year uses, and what a recalculation under those rules
// would change in payslips already issued. Nothing on this screen changes payroll data.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { AlertCircle, CheckCircle2 } from "lucide-react"
import { apiClient } from "../../api/client"
import { useAuth } from "../../hooks/useAuth"
import { nominatedBody, nominatedProblem } from "./payRunPayments.logic"
import {
  impactSummary, rand, signedRand, taxYearName, VERDICT_STYLE, VERDICT_TEXT,
  type Impact, type ImpactRun, type TaxYear,
} from "./payrollChecks.logic"

type Wrapped = { data?: { data?: unknown } }
type ApiErr = { response?: { data?: { message?: string } } }
const unwrap = <T,>(r: Wrapped): T => (r.data?.data ?? r.data) as T
const errorText = (e: unknown, fallback: string) => (e as ApiErr)?.response?.data?.message ?? fallback
const fmtDate = (d: string | null | undefined) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"

const card: React.CSSProperties = { border: "1px solid var(--hf-border)", borderRadius: 12, background: "var(--hf-surface)", marginBottom: 20, overflow: "hidden" }
const head: React.CSSProperties = { padding: "14px 18px", borderBottom: "1px solid var(--hf-border-subtle)" }
const th: React.CSSProperties = { textAlign: "left", padding: "9px 12px", fontSize: 11, fontWeight: 700, color: "var(--hf-text-faint)", textTransform: "uppercase", letterSpacing: "0.05em", whiteSpace: "nowrap" }
const td: React.CSSProperties = { padding: "10px 12px", fontSize: 13, color: "var(--hf-text)", borderTop: "1px solid var(--hf-border-subtle)", verticalAlign: "top" }

function Pill({ text, color, bg }: { text: string; color: string; bg: string }) {
  return <span style={{ background: bg, color, padding: "2px 10px", borderRadius: 20, fontSize: 11, fontWeight: 700, whiteSpace: "nowrap" }}>{text}</span>
}

function Problem({ message }: { message: string }) {
  return (
    <div role="alert" style={{ display: "flex", gap: 8, alignItems: "center", padding: "12px 18px", color: "var(--hf-danger-text)", fontSize: 13 }}>
      <AlertCircle size={15} /> {message}
    </div>
  )
}

export function StatutoryRulesPanel() {
  const q = useQuery<TaxYear[]>({
    queryKey: ["hr-tax-years"],
    queryFn: async () => unwrap<TaxYear[]>(await apiClient.get("/api/v1/hr/statutory/tax-years")),
  })
  return (
    <section style={card} aria-labelledby="hr-rules-h">
      <div style={head}>
        <h3 id="hr-rules-h" style={{ margin: 0, fontSize: 15, fontWeight: 700, color: "var(--hf-text)" }}>Statutory rules by tax year</h3>
        <p style={{ margin: "4px 0 0", fontSize: 12, color: "var(--hf-text-muted)" }}>
          Payroll uses the rules of the tax year the pay date falls in, and will not run a period whose year is missing or unapproved.
          A tax year is named by the year it ends, as SARS names it: 2027 is 1 March 2026 to 28 February 2027. This list is read-only.
        </p>
      </div>
      {q.isLoading ? <div style={{ padding: 24, color: "var(--hf-text-faint)" }}>Loading tax years…</div>
        : q.isError ? <Problem message={errorText(q.error, "The tax years could not be loaded.")} />
        : (q.data ?? []).length === 0 ? <div style={{ padding: 24, color: "var(--hf-text-faint)" }}>No tax years are set up, so payroll cannot run.</div>
        : (
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr>
                <th style={th}>Tax year</th><th style={th}>Period</th><th style={th}>State</th><th style={th}>Primary rebate</th>
                <th style={th}>Medical credit a month</th><th style={th}>UIF ceiling</th><th style={th}>Retirement cap</th><th style={th}>Source</th>
              </tr></thead>
              <tbody>
                {(q.data ?? []).map(y => (
                  <tr key={y.taxYear}>
                    <td style={td}><strong>{taxYearName(y.taxYear)}</strong><div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>SARS: {y.taxYear}</div></td>
                    <td style={td}>{fmtDate(y.effectiveFrom)} – {fmtDate(y.effectiveTo)}</td>
                    <td style={td}>
                      {y.usable
                        ? <Pill text="Approved, in use" color="var(--hf-success-text-strong)" bg="var(--hf-success-soft-strong)" />
                        : <Pill text="Cannot be used" color="var(--hf-danger-text)" bg="var(--hf-danger-soft)" />}
                      {y.problems.map(p => <div key={p} style={{ fontSize: 11, color: "var(--hf-danger-text)", marginTop: 4 }}>{p}</div>)}
                      {y.usable && y.approvalNote && <div style={{ fontSize: 11, color: "var(--hf-text-muted)", marginTop: 4, maxWidth: 260 }}>{y.approvalNote}</div>}
                    </td>
                    <td style={td}>{rand(y.primaryRebate)}</td>
                    <td style={td}>{rand(y.mtcTaxpayer)} / {rand(y.mtcFirstDependant)} / {rand(y.mtcAdditional)}
                      <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>member / first dependant / each more</div></td>
                    <td style={td}>{rand(y.uifMonthlyCeiling)}</td>
                    <td style={td}>{rand(y.retirementCapAmount)}<div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>or {(y.retirementCapPct * 100).toFixed(1)}% of income</div></td>
                    <td style={td}><details><summary style={{ cursor: "pointer", fontSize: 12 }}>Where it came from</summary>
                      <div style={{ fontSize: 11, color: "var(--hf-text-muted)", maxWidth: 320, marginTop: 4 }}>{y.sourceNote}</div></details></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
    </section>
  )
}

function RunDetail({ runId }: { runId: string }) {
  const q = useQuery<Impact>({
    queryKey: ["hr-impact", runId],
    queryFn: async () => unwrap<Impact>(await apiClient.get("/api/v1/hr/payroll/impact-report", { params: { runId } })),
  })
  if (q.isLoading) return <div style={{ padding: 16, color: "var(--hf-text-faint)" }}>Loading payslips…</div>
  if (q.isError) return <Problem message={errorText(q.error, "The payslips could not be compared.")} />
  const run = q.data?.runs[0]
  if (!run) return <div style={{ padding: 16, color: "var(--hf-text-faint)" }}>This run has no payslips to compare.</div>
  return (
    <div style={{ padding: "12px 16px", background: "var(--hf-surface-muted)" }}>
      <p style={{ margin: "0 0 10px", fontSize: 13, color: "var(--hf-text-secondary)" }}>{run.action}</p>
      <div style={{ overflowX: "auto" }}>
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr><th style={th}>Employee</th><th style={th}>Result</th><th style={th}>PAYE paid</th><th style={th}>PAYE recalculated</th><th style={th}>Difference</th><th style={th}>Why</th></tr></thead>
          <tbody>
            {(run.lines ?? []).map(l => (
              <tr key={l.payslipId}>
                <td style={td}>{l.employeeName}<div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>{l.employeeNumber}</div></td>
                <td style={td}><Pill text={VERDICT_TEXT[l.verdict]} {...VERDICT_STYLE[l.verdict]} /></td>
                <td style={td}>{rand(l.recordedPaye)}</td>
                <td style={td}>{rand(l.recalculatedPaye)}</td>
                <td style={td}>{signedRand(l.payeDifference)}</td>
                <td style={{ ...td, fontSize: 12, color: "var(--hf-text-secondary)" }}>{l.reasons.length ? l.reasons.map(r => <div key={r}>{r}</div>) : "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

export function ImpactReportPanel() {
  const [open, setOpen] = useState<string | null>(null)
  const q = useQuery<Impact>({
    queryKey: ["hr-impact"],
    queryFn: async () => unwrap<Impact>(await apiClient.get("/api/v1/hr/payroll/impact-report")),
  })
  return (
    <section style={card} aria-labelledby="hr-impact-h">
      <div style={head}>
        <h3 id="hr-impact-h" style={{ margin: 0, fontSize: 15, fontWeight: 700, color: "var(--hf-text)" }}>Payslips already issued: what would change</h3>
        <p style={{ margin: "4px 0 0", fontSize: 12, color: "var(--hf-text-muted)" }}>
          Each processed run is recalculated under the approved rules and compared with what was paid. This report changes nothing.
          Any correction has to go through an approved adjustment, not through this screen.
        </p>
      </div>
      {q.isLoading ? <div style={{ padding: 24, color: "var(--hf-text-faint)" }}>Comparing pay runs…</div>
        : q.isError ? <Problem message={errorText(q.error, "The report could not be produced.")} />
        : q.data && (
          <>
            <div style={{ display: "flex", alignItems: "center", gap: 8, padding: "12px 18px", fontSize: 13, fontWeight: 600, color: "var(--hf-text)" }}>
              {q.data.runsWithDifferences === 0 ? <CheckCircle2 size={15} color="var(--hf-success-text-strong)" /> : <AlertCircle size={15} color="var(--hf-warning-text)" />}
              {impactSummary(q.data)}
            </div>
            {q.data.runs.length > 0 && (
              <div style={{ overflowX: "auto" }}>
                <table style={{ width: "100%", borderCollapse: "collapse" }}>
                  <thead><tr>
                    <th style={th}>Run</th><th style={th}>Pay date</th><th style={th}>Tax year</th><th style={th}>Status</th><th style={th}>Payslips</th>
                    <th style={th}>PAYE paid</th><th style={th}>PAYE recalculated</th><th style={th}>Difference</th><th style={th}>Result</th><th style={th}><span className="sr-only" style={{ position: "absolute", width: 1, height: 1, overflow: "hidden" }}>Details</span></th>
                  </tr></thead>
                  <tbody>
                    {q.data.runs.map((r: ImpactRun) => (
                      <RunRows key={r.runId} run={r} open={open === r.runId} onToggle={() => setOpen(open === r.runId ? null : r.runId)} />
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            <ul style={{ margin: 0, padding: "12px 18px 16px 34px", fontSize: 12, color: "var(--hf-text-muted)" }}>
              {q.data.notes.map(n => <li key={n}>{n}</li>)}
            </ul>
          </>
        )}
    </section>
  )
}

function RunRows({ run, open, onToggle }: { run: ImpactRun; open: boolean; onToggle: () => void }) {
  const mislabelled = run.recordedTaxYear != null && run.recordedTaxYear !== run.correctTaxYear
  return (
    <>
      <tr>
        <td style={td}><strong>{run.runNumber}</strong></td>
        <td style={td}>{fmtDate(run.payDate)}</td>
        <td style={td}>{taxYearName(run.correctTaxYear)}
          {mislabelled && <div style={{ fontSize: 11, color: "var(--hf-warning-text)" }}>was labelled {taxYearName(run.recordedTaxYear as number)}</div>}</td>
        <td style={td}>{run.status}{run.emp201Status && <div style={{ fontSize: 11, color: "var(--hf-text-faint)" }}>EMP201: {run.emp201Status}</div>}</td>
        <td style={td}>{run.payslips}</td>
        <td style={td}>{rand(run.recordedPaye)}</td>
        <td style={td}>{rand(run.recalculatedPaye)}</td>
        <td style={td}>{signedRand(run.payeDifference)}</td>
        <td style={td}><Pill text={VERDICT_TEXT[run.verdict]} {...VERDICT_STYLE[run.verdict]} />
          <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 4 }}>
            {run.confirmedDifference} confirmed · {run.potentialDifference} possible · {run.reviewRequired} to review
          </div></td>
        <td style={td}>
          <button type="button" onClick={onToggle} aria-expanded={open} aria-label={`${open ? "Hide" : "Show"} payslips of ${run.runNumber}`}
            style={{ padding: "5px 10px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", cursor: "pointer", fontSize: 12, color: "var(--hf-text-secondary)" }}>
            {open ? "Hide" : "Payslips"}
          </button>
        </td>
      </tr>
      {open && <tr><td colSpan={10} style={{ padding: 0, borderTop: "1px solid var(--hf-border-subtle)" }}><RunDetail runId={run.runId} /></td></tr>}
    </>
  )
}

interface AccessReview { legacyAccessEnds: string; rolesNeedingReview: number; roles: { roleId: string; roleName: string; usersInRole: number; held: string; granted: string; needsReview: boolean; note: string }[] }
interface PayrollSettings {
  allowSelfApproval: boolean; legacyAccessEnds: string; legacyAccessOpen: boolean
  bankFileSecondCheck?: boolean; nominatedBranch?: string | null; nominatedAccountSet?: boolean; nominatedAccountLast4?: string | null
}

/** Who can do what in payroll: the self-approval choice and what the permissions migration did to each role. */
export function PayrollControlsPanel() {
  const qc = useQueryClient()
  const { user } = useAuth()
  const canConfigure = (user?.permissions ?? []).includes("PAYROLL_CONFIGURATION_ADMIN")
  const [error, setError] = useState("")
  const settings = useQuery<PayrollSettings>({
    queryKey: ["hr-payroll-settings"],
    queryFn: async () => unwrap<PayrollSettings>(await apiClient.get("/api/v1/hr/payroll/settings")),
  })
  const review = useQuery<AccessReview>({
    queryKey: ["hr-payroll-access-review"],
    queryFn: async () => unwrap<AccessReview>(await apiClient.get("/api/v1/hr/payroll/access-review")),
    enabled: canConfigure,
  })
  const save = useMutation({
    mutationFn: (allow: boolean) => apiClient.put("/api/v1/hr/payroll/settings", { allowSelfApproval: allow }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["hr-payroll-settings"] }); setError("") },
    onError: e => setError(errorText(e, "The setting could not be saved.")),
  })
  const [branch, setBranch] = useState("")
  const [account, setAccount] = useState("")
  const bankProblem = nominatedProblem(branch, account)
  const saveBank = useMutation({
    mutationFn: (v: { secondCheck: boolean; branch: string; account: string }) =>
      apiClient.put("/api/v1/hr/payroll/settings", nominatedBody(settings.data?.allowSelfApproval ?? false, v.secondCheck, v.branch, v.account)),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ["hr-payroll-settings"] }); setError(""); setBranch(""); setAccount("") },
    onError: e => setError(errorText(e, "The bank file setting could not be saved.")),
  })
  return (
    <section style={card} aria-labelledby="hr-controls-h">
      <div style={head}>
        <h3 id="hr-controls-h" style={{ margin: 0, fontSize: 15, fontWeight: 700, color: "var(--hf-text)" }}>Payroll approval and access</h3>
        <p style={{ margin: "4px 0 0", fontSize: 12, color: "var(--hf-text-muted)" }}>
          A pay run is calculated by one person and approved and finalised by another. Employees see their payslips once it is finalised.
        </p>
      </div>
      {settings.isLoading ? <div style={{ padding: 24, color: "var(--hf-text-faint)" }}>Loading…</div>
        : settings.isError ? <Problem message={errorText(settings.error, "The payroll settings could not be loaded.")} />
        : settings.data && (
          <div style={{ padding: "12px 18px", fontSize: 13, color: "var(--hf-text)" }}>
            <label style={{ display: "flex", gap: 10, alignItems: "flex-start", cursor: canConfigure ? "pointer" : "default" }}>
              <input type="checkbox" checked={settings.data.allowSelfApproval} disabled={!canConfigure || save.isPending}
                onChange={e => save.mutate(e.target.checked)} style={{ marginTop: 3 }} />
              <span>
                <strong>One person may calculate and approve</strong>
                <span style={{ display: "block", fontSize: 12, color: "var(--hf-text-muted)" }}>
                  For a business with a single payroll person. Off by default. When on, every time someone approves or finalises their own run, the run's history says so.
                  {!canConfigure && " Only someone with payroll configuration access can change this."}
                </span>
              </span>
            </label>
            <label style={{ display: "flex", gap: 10, alignItems: "flex-start", marginTop: 12, cursor: canConfigure ? "pointer" : "default" }}>
              <input type="checkbox" checked={!!settings.data.bankFileSecondCheck} disabled={!canConfigure || saveBank.isPending}
                onChange={e => saveBank.mutate({ secondCheck: e.target.checked, branch: "", account: "" })} style={{ marginTop: 3 }} />
              <span>
                <strong>A second person confirms the bank file was paid</strong>
                <span style={{ display: "block", fontSize: 12, color: "var(--hf-text-muted)" }}>
                  Off by default. When on, whoever downloaded the bank file cannot also confirm it. Leave it off for a business with one payroll person.
                </span>
              </span>
            </label>
            <fieldset style={{ marginTop: 12, border: "1px solid var(--hf-border-subtle)", borderRadius: 8, padding: "10px 12px" }} disabled={!canConfigure}>
              <legend style={{ fontSize: 12, fontWeight: 700, color: "var(--hf-text-muted)" }}>The company's own bank account (for the BankServ file)</legend>
              <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "flex-end" }}>
                <div>
                  <label htmlFor="bf-branch" style={{ display: "block", fontSize: 12, marginBottom: 3 }}>Branch code</label>
                  <input id="bf-branch" inputMode="numeric" maxLength={7} value={branch} onChange={e => setBranch(e.target.value)}
                    placeholder={settings.data.nominatedBranch ?? "6 digits"} style={{ padding: "7px 9px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 13, width: 120, background: "var(--hf-surface)", color: "var(--hf-text)" }} />
                </div>
                <div>
                  <label htmlFor="bf-account" style={{ display: "block", fontSize: 12, marginBottom: 3 }}>Account number</label>
                  <input id="bf-account" inputMode="numeric" maxLength={13} value={account} onChange={e => setAccount(e.target.value)}
                    placeholder={settings.data.nominatedAccountSet ? `Saved, ends ${settings.data.nominatedAccountLast4}` : "up to 11 digits"}
                    style={{ padding: "7px 9px", border: "1.5px solid var(--hf-border)", borderRadius: 8, fontSize: 13, width: 180, background: "var(--hf-surface)", color: "var(--hf-text)" }} />
                </div>
                <button type="button" disabled={!(branch || account) || !!bankProblem || saveBank.isPending}
                  onClick={() => saveBank.mutate({ secondCheck: !!settings.data?.bankFileSecondCheck, branch, account })}
                  style={{ padding: "8px 14px", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, color: "var(--hf-text-on-solid)", cursor: "pointer",
                    background: !(branch || account) || bankProblem ? "var(--hf-text-faint)" : "var(--hf-primary)" }}>Save account</button>
              </div>
              {bankProblem && <div role="alert" style={{ marginTop: 6, fontSize: 12, color: "var(--hf-danger-text)" }}>{bankProblem}</div>}
            </fieldset>
            {error && <div role="alert" style={{ marginTop: 8, color: "var(--hf-danger-text)" }}>{error}</div>}
            {settings.data.legacyAccessOpen && (
              <div style={{ marginTop: 12, padding: "8px 12px", background: "var(--hf-warning-soft)", border: "1px solid var(--hf-warning-border)", borderRadius: 8, fontSize: 12, color: "var(--hf-warning-text)" }}>
                The old permissions (Process pay runs and Update users) still calculate pay runs until {fmtDate(settings.data.legacyAccessEnds)}. After that date only the payroll permissions work.
              </div>
            )}
          </div>
        )}
      {canConfigure && (
        review.isLoading ? <div style={{ padding: "0 18px 18px", color: "var(--hf-text-faint)" }}>Loading the access review…</div>
        : review.isError ? <Problem message={errorText(review.error, "The access review could not be loaded.")} />
        : review.data && (
          <div style={{ overflowX: "auto", borderTop: "1px solid var(--hf-border-subtle)" }}>
            <div style={{ padding: "10px 18px", fontSize: 12, fontWeight: 600, color: review.data.rolesNeedingReview ? "var(--hf-warning-text)" : "var(--hf-text-muted)" }}>
              {review.data.rolesNeedingReview === 0 ? "Every role that had payroll access was mapped." : `${review.data.rolesNeedingReview} ${review.data.rolesNeedingReview === 1 ? "role needs" : "roles need"} a decision.`}
            </div>
            <table style={{ width: "100%", borderCollapse: "collapse" }}>
              <thead><tr><th style={th}>Role</th><th style={th}>People</th><th style={th}>Held</th><th style={th}>Given</th><th style={th}>What to do</th></tr></thead>
              <tbody>
                {review.data.roles.map(r => (
                  <tr key={r.roleId}>
                    <td style={td}><strong>{r.roleName}</strong>{r.needsReview && <div><Pill text="Needs a decision" color="var(--hf-warning-text)" bg="var(--hf-warning-soft)" /></div>}</td>
                    <td style={td}>{r.usersInRole}</td>
                    <td style={{ ...td, fontSize: 12 }}>{r.held}</td>
                    <td style={{ ...td, fontSize: 12 }}>{r.granted || "Nothing new"}</td>
                    <td style={{ ...td, fontSize: 12, color: "var(--hf-text-secondary)", maxWidth: 360 }}>{r.note}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )
      )}
    </section>
  )
}

export default function PayrollChecksTab() {
  return (
    <div>
      <PayrollControlsPanel />
      <StatutoryRulesPanel />
      <ImpactReportPanel />
    </div>
  )
}
