// A full page for registering an employee, in six steps. Entered data is kept when moving between steps and
// saved in this browser as a draft. Every field helper lives at module level so typing never remounts an input
// (the old modal rebuilt its form on each keystroke, which made the page jump).
import { useEffect, useMemo, useRef, useState, type CSSProperties, type ReactNode } from "react"
import { useNavigate } from "react-router-dom"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { useAuth } from "../../hooks/useAuth"
import MedicalSchemeFields from "./MedicalSchemeFields"
import ForeignIdentityFields from "./ForeignIdentityFields"
import { employeeRights } from "./employees.logic"
import {
  EMPTY_REG, STEPS, WHY, allErrors, draftAge, draftKey, draftSaveBody, fromServerDraft, enrolBody, firstBadStep, fromDraft, hasContent, regBody, serverErrors, stepErrors, toDraft, validateSaId,
  type DraftDetail, type DraftSummary, type Errors, type RegForm,
} from "./registration.logic"
import { schemeDependants } from "./payrollChecks.logic"

const EMP_TYPES = ["PERMANENT", "FIXED_TERM", "PART_TIME", "CASUAL", "CONTRACTOR"]
const GENDERS = ["MALE", "FEMALE", "NON_BINARY", "PREFER_NOT_TO_SAY"]
const RACES = ["AFRICAN", "COLOURED", "INDIAN", "WHITE", "OTHER"]
const DEPARTMENTS = ["MANAGEMENT", "FINANCE", "HR", "OPERATIONS", "TECHNICAL", "SALES", "ADMIN", "LOGISTICS", "OTHER"]
const PAY_FREQS = ["MONTHLY", "BI_WEEKLY", "WEEKLY"]
const SA_BANKS = ["ABSA", "FNB", "STANDARD BANK", "NEDBANK", "CAPITEC", "INVESTEC", "AFRICAN BANK", "OTHER"]
const nice = (s: string) => s.replace(/_/g, " ").toLowerCase().replace(/^\w/, c => c.toUpperCase())

const lbl: CSSProperties = { display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4 }
const inp: CSSProperties = { width: "100%", boxSizing: "border-box", padding: "9px 11px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 14, background: "var(--hf-surface)", color: "var(--hf-text)" }
const grid: CSSProperties = { display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(240px, 1fr))", gap: "16px 16px", alignItems: "start" }
const btn: CSSProperties = { padding: "9px 18px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text-secondary)", fontSize: 14, fontWeight: 600, cursor: "pointer" }
const primary: CSSProperties = { ...btn, background: "var(--hf-primary, #2563eb)", color: "#fff", borderColor: "transparent" }

interface FieldProps { id: string; label: string; required?: boolean; error?: string; hint?: string; children: ReactNode }
function Field({ id, label, required, error, hint, children }: FieldProps) {
  const why = required ? WHY[id] : undefined
  return (
    <div>
      <label htmlFor={`reg-${id}`} style={lbl}>{label}{required && <span aria-hidden style={{ color: "var(--hf-danger-text)" }}> *</span>}</label>
      {children}
      {/* The line under a field always reserves its height, so showing or clearing a message never moves anything. */}
      <div style={{ minHeight: 18, fontSize: 12, marginTop: 3, color: error ? "var(--hf-danger-text)" : "var(--hf-text-faint)" }} role={error ? "alert" : undefined}>
        {error ?? hint ?? why ?? ""}
      </div>
    </div>
  )
}

interface StepProps { f: RegForm; set: (patch: Partial<RegForm>) => void; e: Errors }

function TextField({ id, label, f, set, e, required, hint, type = "text", ...rest }: StepProps & { id: keyof RegForm & string; label: string; required?: boolean; hint?: string; type?: string; inputMode?: "numeric" | "tel" | "email"; maxLength?: number; autoFocus?: boolean }) {
  return (
    <Field id={id} label={label} required={required} error={e[id]} hint={hint}>
      <input id={`reg-${id}`} type={type} value={f[id] as string} onChange={ev => set({ [id]: ev.target.value } as Partial<RegForm>)} style={{ ...inp, borderColor: e[id] ? "var(--hf-danger-text)" : "var(--hf-border)" }}
        aria-invalid={!!e[id]} aria-required={required} autoComplete="off" {...rest} />
    </Field>
  )
}
function SelectField({ id, label, f, set, e, options, blank, required }: StepProps & { id: keyof RegForm & string; label: string; options: string[]; blank?: string; required?: boolean }) {
  return (
    <Field id={id} label={label} required={required} error={e[id]}>
      <select id={`reg-${id}`} value={f[id] as string} onChange={ev => set({ [id]: ev.target.value } as Partial<RegForm>)} style={inp}>
        {blank !== undefined && <option value="">{blank}</option>}
        {options.map(o => <option key={o} value={o}>{nice(o)}</option>)}
      </select>
    </Field>
  )
}

function Personal({ f, set, e }: StepProps) {
  const idState = f.idNumber.length === 13 ? (validateSaId(f.idNumber).valid ? "Valid ID. Date of birth and gender were filled in." : undefined) : undefined
  const onId = (v: string) => {
    const digits = v.replace(/\D/g, "").slice(0, 13)
    const r = digits.length === 13 ? validateSaId(digits) : null
    set(r?.valid ? { idNumber: digits, dateOfBirth: r.dob!, gender: r.gender! } : { idNumber: digits })
  }
  return (
    <div style={grid}>
      <TextField id="firstName" label="First name" required f={f} set={set} e={e} autoFocus />
      <TextField id="lastName" label="Last name" required f={f} set={set} e={e} />
      <Field id="idNumber" label="South African ID number" error={e.idNumber} hint={idState ?? "13 digits. Leave empty for someone with a passport."}>
        <input id="reg-idNumber" value={f.idNumber} onChange={ev => onId(ev.target.value)} inputMode="numeric" maxLength={13} style={{ ...inp, borderColor: e.idNumber ? "var(--hf-danger-text)" : "var(--hf-border)" }} autoComplete="off" />
      </Field>
      <TextField id="dateOfBirth" label="Date of birth" type="date" f={f} set={set} e={e} />
      <SelectField id="gender" label="Gender" blank="Select" options={GENDERS} f={f} set={set} e={e} />
      <SelectField id="race" label="Race (for employment equity reporting)" blank="Select" options={RACES} f={f} set={set} e={e} />
      <div style={{ gridColumn: "1 / -1" }}>
        <div style={{ ...grid }}>
          <ForeignIdentityFields value={f.foreign} onChange={foreign => set({ foreign })} lbl={lbl} inp={inp} problem={e.foreign} />
        </div>
      </div>
    </div>
  )
}

function Contact({ f, set, e }: StepProps) {
  return (
    <div style={grid}>
      <TextField id="email" label="Email" type="email" inputMode="email" f={f} set={set} e={e} />
      <TextField id="phone" label="Contact number" inputMode="tel" f={f} set={set} e={e} />
      <TextField id="emergencyContactName" label="Emergency contact name" f={f} set={set} e={e} />
      <TextField id="emergencyContactPhone" label="Emergency contact number" inputMode="tel" f={f} set={set} e={e} />
      <TextField id="emergencyContactRelation" label="Relationship" hint="For example spouse, parent or friend" f={f} set={set} e={e} />
    </div>
  )
}

function Employment({ f, set, e }: StepProps) {
  return (
    <div style={grid}>
      <TextField id="startDate" label="Start date" type="date" required f={f} set={set} e={e} />
      <SelectField id="employmentType" label="Employment type" options={EMP_TYPES} f={f} set={set} e={e} />
      <TextField id="jobTitle" label="Job title" f={f} set={set} e={e} />
      <SelectField id="department" label="Department" blank="Select" options={DEPARTMENTS} f={f} set={set} e={e} />
      <div style={{ gridColumn: "1 / -1" }}>
        <Field id="notes" label="Notes">
          <textarea id="reg-notes" value={f.notes} onChange={ev => set({ notes: ev.target.value })} rows={3} style={{ ...inp, resize: "vertical" }} />
        </Field>
      </div>
    </div>
  )
}

function Pay({ f, set, e }: StepProps) {
  const setScheme = (patch: Partial<RegForm["scheme"]>) => set({ scheme: { ...f.scheme, ...patch } })
  return (
    <div>
      <div style={grid}>
        <TextField id="grossSalary" label="Gross salary (R)" required type="number" inputMode="numeric" f={f} set={set} e={e} />
        <SelectField id="payFrequency" label="Paid" options={PAY_FREQS} f={f} set={set} e={e} />
        <TextField id="travelAllowance" label="Travel allowance (R)" type="number" f={f} set={set} e={e} />
        <TextField id="pensionContribution" label="Retirement fund contribution (R)" type="number" f={f} set={set} e={e} />
      </div>
      <fieldset style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: "14px 16px", margin: "8px 0 0" }}>
        <legend style={{ padding: "0 6px", fontSize: 13, fontWeight: 700 }}>Medical scheme membership</legend>
        <label style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 14, marginBottom: 10 }}>
          <input type="checkbox" checked={f.enrolled} onChange={ev => set({ enrolled: ev.target.checked })} /> This employee belongs to a medical scheme
        </label>
        {!f.enrolled
          ? <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>Not enrolled? Skip this. You can add a scheme later from the employee's Medical scheme button.</div>
          : (
            <>
              <MedicalSchemeFields value={f.scheme} onChange={setScheme} prefix="reg-ms" />
              {e.scheme && <div role="alert" style={{ fontSize: 12, color: "var(--hf-danger-text)" }}>{e.scheme}</div>}
              <div style={{ fontSize: 12, color: "var(--hf-text-faint)", marginTop: 6 }}>A later change is added as a new dated entry, so the history stays.</div>
            </>)}
      </fieldset>
    </div>
  )
}

function Banking({ f, set, e }: StepProps) {
  return (
    <div style={grid}>
      <SelectField id="bankName" label="Bank" blank="Select" options={SA_BANKS} f={f} set={set} e={e} />
      <TextField id="bankAccountNumber" label="Account number" inputMode="numeric" f={f} set={set} e={e} />
      <TextField id="bankBranchCode" label="Branch code" inputMode="numeric" maxLength={6} hint="6 digits" f={f} set={set} e={e} />
      <TextField id="taxNumber" label="SARS income tax number" inputMode="numeric" maxLength={10} hint="10 digits. Needed for the tax certificate." f={f} set={set} e={e} />
    </div>
  )
}

function Row({ k, v }: { k: string; v: string }) {
  return <div style={{ padding: "8px 12px", background: "var(--hf-surface-muted)", borderRadius: 8 }}><div style={{ fontSize: 11, color: "var(--hf-text-faint)", textTransform: "uppercase", fontWeight: 700 }}>{k}</div><div style={{ fontSize: 14 }}>{v || "—"}</div></div>
}

function Review({ f, goTo, problems }: { f: RegForm; goTo: (s: number) => void; problems: Errors }) {
  const bad = Object.keys(problems).length
  const R = (n: number) => `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2 })}`
  return (
    <div>
      {bad > 0 && <div role="alert" style={{ marginBottom: 12, padding: "10px 12px", borderRadius: 8, background: "var(--hf-danger-soft)", color: "var(--hf-danger-text)", fontSize: 13 }}>
        {bad} thing{bad === 1 ? "" : "s"} to fix before you can register. <button type="button" style={{ ...btn, padding: "3px 10px", fontSize: 12 }} onClick={() => goTo(firstBadStep(f))}>Take me there</button></div>}
      <div style={grid}>
        <Row k="Name" v={`${f.firstName} ${f.lastName}`.trim()} /><Row k="ID number" v={f.idNumber} />
        <Row k="Email" v={f.email} /><Row k="Contact number" v={f.phone} />
        <Row k="Start date" v={f.startDate} /><Row k="Role" v={[f.jobTitle, nice(f.employmentType)].filter(Boolean).join(", ")} />
        <Row k="Gross salary" v={f.grossSalary ? `${R(Number(f.grossSalary))} (${nice(f.payFrequency)})` : ""} />
        <Row k="Medical scheme" v={f.enrolled ? `${f.scheme.schemeName}, ${schemeDependants(f.scheme)} dependants, from ${f.scheme.effectiveFrom}` : "Not enrolled"} />
        <Row k="Bank" v={f.bankName ? `${f.bankName} ${f.bankAccountNumber ? "••••" + f.bankAccountNumber.slice(-4) : ""}` : ""} />
        <Row k="Tax number" v={f.taxNumber} />
      </div>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginTop: 12 }}>
        {STEPS.slice(0, 5).map((s, i) => <button key={s.id} type="button" style={{ ...btn, padding: "4px 10px", fontSize: 12 }} onClick={() => goTo(i)}>Edit {s.title.toLowerCase()}</button>)}
      </div>
    </div>
  )
}

export default function EmployeeRegistrationPage() {
  const nav = useNavigate(), qc = useQueryClient(), { user } = useAuth()
  const rights = employeeRights(user?.permissions ?? [])
  const key = draftKey(user?.userId)
  const [f, setF] = useState<RegForm>(EMPTY_REG), [step, setStep] = useState(0), [shown, setShown] = useState<Errors>({})
  const [saved, setSaved] = useState<string>(""), [offer, setOffer] = useState<{ step: number; savedAt: string } | null>(null), [message, setMessage] = useState("")
  const topRef = useRef<HTMLHeadingElement>(null), restored = useRef(false)
  const [drafts, setDrafts] = useState<DraftSummary[]>([]), draftId = useRef<string | null>(null), busy = useRef(false), again = useRef(false)
  const [where, setWhere] = useState<"server" | "browser" | "">("")

  // Offer a saved draft once. Reading can fail (private window), so every storage call is guarded.
  useEffect(() => {
    if (restored.current) return
    restored.current = true
    let alive = true
    ;(async () => {
      let mine: DraftSummary[] = []
      try { const r = await apiClient.get("/api/v1/hr/employee-drafts"); mine = ((r.data?.data ?? r.data) as DraftSummary[]) ?? [] } catch { /* the server list is unavailable: fall back to this browser */ }
      if (!alive) return
      if (mine.length) { setDrafts(mine); return }
      try { const d = fromDraft(localStorage.getItem(key)); if (d && hasContent(d.form)) setOffer({ step: d.step, savedAt: d.savedAt }) } catch { /* no draft */ }
    })()
    return () => { alive = false }
  }, [key])

  /** Saves to the server, one request at a time; a change made meanwhile is saved right after. Returns false when the server could not take it. */
  const saveToServer = async (form: RegForm, atStep: number): Promise<boolean> => {
    if (busy.current) { again.current = true; return true }
    busy.current = true
    try {
      const body = draftSaveBody(form, atStep)
      const r = draftId.current ? await apiClient.put(`/api/v1/hr/employee-drafts/${draftId.current}`, body) : await apiClient.post("/api/v1/hr/employee-drafts", body)
      draftId.current = ((r.data?.data ?? r.data) as DraftDetail).id
      return true
    } catch { return false }
    finally { busy.current = false; if (again.current) { again.current = false; void saveToServer(latest.current.f, latest.current.step) } }
  }
  const latest = useRef({ f, step })
  latest.current = { f, step }
  const mark = (ok: boolean) => { setWhere(ok ? "server" : "browser"); setSaved(new Date().toLocaleTimeString("en-ZA", { hour: "2-digit", minute: "2-digit" })) }

  // Save the draft shortly after the last change.
  useEffect(() => {
    if (offer || drafts.length || !hasContent(f)) return
    const t = setTimeout(async () => {
      try { localStorage.setItem(key, toDraft(f, step)) } catch { /* cannot save in this browser */ }
      mark(await saveToServer(f, step))
    }, 1200)
    return () => clearTimeout(t)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [f, step, key, offer, drafts.length])

  const set = (patch: Partial<RegForm>) => {
    setF(s => ({ ...s, ...patch }))
    setShown(s => { const n = { ...s }; for (const k of Object.keys(patch)) { delete n[k]; if (k === "scheme") delete n.scheme; if (k === "foreign") delete n.foreign } return n })
  }
  const all = useMemo(() => allErrors(f), [f])
  const goTo = (s: number) => { setStep(s); setMessage(""); requestAnimationFrame(() => topRef.current?.scrollIntoView?.({ block: "start" })) }

  const next = () => {
    const errs = stepErrors(step, f)
    if (Object.keys(errs).length) { setShown(errs); requestAnimationFrame(() => document.querySelector<HTMLElement>("[aria-invalid=true]")?.focus()); return }
    setShown({}); goTo(step + 1)
  }

  const create = useMutation({
    mutationFn: async () => {
      const res = await apiClient.post("/api/v1/hr/employees", regBody(f))
      const emp = (res.data?.data ?? res.data) as { id: string }
      const scheme = enrolBody(f)
      let schemeFailed = ""
      if (scheme) { try { await apiClient.post(`/api/v1/hr/employees/${emp.id}/medical-scheme`, scheme) } catch (er: any) { schemeFailed = er?.response?.data?.message ?? "the scheme could not be saved" } }
      return schemeFailed
    },
    onSuccess: schemeFailed => {
      try { localStorage.removeItem(key) } catch { /* ignore */ }
      if (draftId.current) void apiClient.delete(`/api/v1/hr/employee-drafts/${draftId.current}`).catch(() => undefined)
      qc.invalidateQueries({ queryKey: ["hr-employees"] })
      nav("/hr/employees", { state: { registered: `${f.firstName} ${f.lastName}`.trim(), schemeFailed } })
    },
    onError: (er: any) => {
      const s = serverErrors(er?.response?.data)
      setShown(s.errors); setMessage(s.message || "Could not register the employee")
      if (s.step >= 0) goTo(s.step)
    },
  })

  if (!rights.manage) return <div style={{ padding: 24 }}>You do not have permission to register employees.</div>
  const last = step === STEPS.length - 1, S = STEPS[step]
  const props: StepProps = { f, set, e: shown }
  return (
    <div style={{ maxWidth: 880, margin: "0 auto", padding: "8px 0 40px" }}>
      <h1 ref={topRef} style={{ fontSize: 22, margin: "0 0 4px" }}>New employee registration</h1>
      <div style={{ fontSize: 13, color: "var(--hf-text-muted)", marginBottom: 14 }}>Step {step + 1} of {STEPS.length}</div>
      <ol style={{ display: "flex", gap: 6, listStyle: "none", padding: 0, margin: "0 0 18px", flexWrap: "wrap" }}>
        {STEPS.map((s, i) => (
          <li key={s.id}><button type="button" onClick={() => (i <= step || i === firstBadStep(f) + 0 ? goTo(i) : next())} aria-current={i === step ? "step" : undefined}
            style={{ ...btn, padding: "5px 10px", fontSize: 12, background: i === step ? "var(--hf-primary-soft, #dbeafe)" : "var(--hf-surface)", fontWeight: i === step ? 700 : 500 }}>{i + 1}. {s.title}</button></li>))}
      </ol>
      {drafts.length > 0 && (
        <div role="status" style={{ padding: "10px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, marginBottom: 14, fontSize: 13 }}>
          <div style={{ fontWeight: 700, marginBottom: 6 }}>You have {drafts.length === 1 ? "an unfinished registration" : `${drafts.length} unfinished registrations`}</div>
          {drafts.map(d => (
            <div key={d.id} style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", padding: "4px 0" }}>
              <span style={{ flex: 1, minWidth: 160 }}>{d.label} <span style={{ color: "var(--hf-text-faint)" }}>· step {d.step + 1} · {draftAge(d.updatedAt)}</span></span>
              <button type="button" style={primary} onClick={async () => {
                try { const r = await apiClient.get(`/api/v1/hr/employee-drafts/${d.id}`); const x = fromServerDraft((r.data?.data ?? r.data) as DraftDetail); if (x) { setF(x.form); setStep(x.step); draftId.current = d.id; setDrafts([]) } } catch { setMessage("That draft could not be opened.") }
              }}>Continue</button>
              <button type="button" style={btn} onClick={async () => { try { await apiClient.delete(`/api/v1/hr/employee-drafts/${d.id}`); setDrafts(x => x.filter(y => y.id !== d.id)) } catch { setMessage("That draft could not be deleted.") } }}>Delete</button>
            </div>))}
          <button type="button" style={{ ...btn, marginTop: 6 }} onClick={() => setDrafts([])}>Start a new one</button>
        </div>)}
      {offer && (
        <div role="status" style={{ padding: "10px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, marginBottom: 14, fontSize: 13, display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
          You have an unfinished draft{offer.savedAt ? ` from ${new Date(offer.savedAt).toLocaleString("en-ZA")}` : ""}.
          <button type="button" style={primary} onClick={() => { try { const d = fromDraft(localStorage.getItem(key)); if (d) { setF(d.form); setStep(d.step) } } catch { /* ignore */ } setOffer(null) }}>Continue it</button>
          <button type="button" style={btn} onClick={() => { try { localStorage.removeItem(key) } catch { /* ignore */ } setOffer(null) }}>Start fresh</button>
        </div>)}
      <section style={{ border: "1px solid var(--hf-border)", borderRadius: 12, background: "var(--hf-surface)", padding: "20px 22px", minHeight: 420 }}>
        <h2 style={{ fontSize: 17, margin: "0 0 2px" }}>{S.title}</h2>
        <div style={{ fontSize: 13, color: "var(--hf-text-muted)", marginBottom: 6 }}>{S.blurb}</div>
        <div style={{ fontSize: 12, color: "var(--hf-text-faint)", marginBottom: 14 }}><span style={{ color: "var(--hf-danger-text)" }}>*</span> marks what is needed to register. Everything else can be added later.</div>
        {step === 0 && <Personal {...props} />}
        {step === 1 && <Contact {...props} />}
        {step === 2 && <Employment {...props} />}
        {step === 3 && <Pay {...props} />}
        {step === 4 && <Banking {...props} />}
        {last && <Review f={f} goTo={goTo} problems={all} />}
        {message && <div role="alert" style={{ marginTop: 12, padding: "10px 12px", borderRadius: 8, background: "var(--hf-danger-soft)", color: "var(--hf-danger-text)", fontSize: 13 }}>{message}</div>}
      </section>
      <div style={{ display: "flex", gap: 10, alignItems: "center", marginTop: 14, flexWrap: "wrap" }}>
        <button type="button" style={btn} onClick={() => nav("/hr/employees")}>Cancel</button>
        <span style={{ fontSize: 12, color: where === "browser" ? "var(--hf-warning-text)" : "var(--hf-text-faint)", flex: 1 }}>
          {saved ? (where === "browser" ? `Saved in this browser only at ${saved}. The server could not be reached.` : `Draft saved automatically at ${saved}`) : hasContent(f) ? "Saving draft…" : ""}</span>
        {hasContent(f) && <button type="button" style={btn} onClick={async () => { try { localStorage.setItem(key, toDraft(f, step)) } catch { /* ignore */ } mark(await saveToServer(f, step)) }}>Save draft</button>}
        {step > 0 && <button type="button" style={btn} onClick={() => goTo(step - 1)}>← Back</button>}
        {!last && <button type="button" style={primary} onClick={next}>Next step →</button>}
        {last && <button type="button" style={primary} disabled={create.isPending || Object.keys(all).length > 0} onClick={() => { setMessage(""); create.mutate() }}>{create.isPending ? "Registering…" : "Register employee"}</button>}
      </div>
    </div>
  )
}
