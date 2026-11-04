// The fields of one medical scheme membership, shared by the employee's Medical scheme dialog and the registration page.
// Defined at module level so typing never remounts the inputs.
import type { CSSProperties } from "react"
import { schemeDependants, type SchemeForm } from "./payrollChecks.logic"

const lbl: CSSProperties = { display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 4 }
const inp: CSSProperties = { width: "100%", boxSizing: "border-box", padding: "9px 11px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 14, background: "var(--hf-surface)", color: "var(--hf-text)" }
const grid: CSSProperties = { display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: "12px 12px" }
const hint: CSSProperties = { fontSize: 12, color: "var(--hf-text-faint)", marginTop: 6 }

export default function MedicalSchemeFields({ value, onChange, prefix }: { value: SchemeForm; onChange: (patch: Partial<SchemeForm>) => void; prefix: string }) {
  const id = (k: string) => `${prefix}-${k}`
  const covered = schemeDependants(value)
  return (
    <div>
      <div style={grid}>
        <div><label htmlFor={id("name")} style={lbl}>Scheme name *</label><input id={id("name")} style={inp} value={value.schemeName} onChange={e => onChange({ schemeName: e.target.value })} maxLength={120} /></div>
        <div><label htmlFor={id("no")} style={lbl}>Membership number</label><input id={id("no")} style={inp} value={value.memberNumber} onChange={e => onChange({ memberNumber: e.target.value })} maxLength={60} /></div>
        <div><label htmlFor={id("from")} style={lbl}>Starts on *</label><input id={id("from")} style={inp} type="date" value={value.effectiveFrom} onChange={e => onChange({ effectiveFrom: e.target.value })} /></div>
        <div><label htmlFor={id("emp")} style={lbl}>Employee pays each month (R)</label><input id={id("emp")} style={inp} type="number" min={0} step="0.01" inputMode="decimal" value={value.employeeContribution} onChange={e => onChange({ employeeContribution: e.target.value })} /></div>
        <div><label htmlFor={id("er")} style={lbl}>Employer pays each month (R)</label><input id={id("er")} style={inp} type="number" min={0} step="0.01" inputMode="decimal" value={value.employerContribution} onChange={e => onChange({ employerContribution: e.target.value })} /></div>
      </div>
      <div style={{ fontSize: 13, fontWeight: 700, margin: "14px 0 6px" }}>Dependants covered by the scheme</div>
      <div style={grid}>
        <div><label htmlFor={id("spouse")} style={lbl}>Spouse or partner</label>
          <select id={id("spouse")} style={inp} value={value.spouses || "0"} onChange={e => onChange({ spouses: e.target.value })}><option value="0">None</option><option value="1">1</option></select></div>
        <div><label htmlFor={id("children")} style={lbl}>Children</label><input id={id("children")} style={inp} type="number" min={0} max={20} step={1} inputMode="numeric" value={value.children} onChange={e => onChange({ children: e.target.value })} /></div>
        <div><label htmlFor={id("other")} style={lbl}>Other eligible dependants</label><input id={id("other")} style={inp} type="number" min={0} max={20} step={1} inputMode="numeric" value={value.other} onChange={e => onChange({ other: e.target.value })} /></div>
      </div>
      <div style={hint} aria-live="polite">{Number.isNaN(covered) ? "" : `People covered: ${1 + covered} (the employee and ${covered} ${covered === 1 ? "dependant" : "dependants"}).`} Payroll works out the medical tax credit from this; the household's children are kept separate.</div>
    </div>
  )
}
