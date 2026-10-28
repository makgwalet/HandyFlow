// Passport and work permit fields for the employee form. Shown to people who may see personal data; the server checks everything again.
import type { CSSProperties } from "react"
import { PERMIT_TYPES, type ForeignForm } from "./foreignIdentity.logic"

interface Props {
  value: ForeignForm
  onChange: (next: ForeignForm) => void
  lbl: CSSProperties
  inp: CSSProperties
  problem?: string | null
}

const hint: CSSProperties = { fontSize: 11, color: "var(--hf-text-faint)", marginTop: 2 }

export default function ForeignIdentityFields({ value, onChange, lbl, inp, problem }: Props) {
  const set = (patch: Partial<ForeignForm>) => onChange({ ...value, ...patch })
  const permanent = value.workPermitType === "PERMANENT_RESIDENCE"
  return (
    <>
      <div style={{ gridColumn: "1 / -1", fontSize: 12, color: "var(--hf-text-muted)" }}>
        For people without a South African ID number. SARS accepts a passport and its country on the tax certificate instead.
      </div>
      <div><label htmlFor="emp-passport" style={lbl}>Passport number</label>
        <input id="emp-passport" value={value.passportNumber} onChange={e => set({ passportNumber: e.target.value })} style={inp} autoComplete="off" /></div>
      <div><label htmlFor="emp-passport-country" style={lbl}>Passport country</label>
        <input id="emp-passport-country" maxLength={2} value={value.passportCountry} onChange={e => set({ passportCountry: e.target.value.toUpperCase() })}
          placeholder="ZW" style={inp} autoComplete="off" />
        <div style={hint}>Two-letter code of the country that issued it</div></div>
      <div><label htmlFor="emp-passport-expiry" style={lbl}>Passport expires</label>
        <input id="emp-passport-expiry" type="date" value={value.passportExpiry} onChange={e => set({ passportExpiry: e.target.value })} style={inp} /></div>
      <div><label htmlFor="emp-permit-type" style={lbl}>Work permit or visa</label>
        <select id="emp-permit-type" value={value.workPermitType} onChange={e => set({ workPermitType: e.target.value })} style={{ ...inp, background: "var(--hf-surface)" }}>
          <option value="">None</option>
          {PERMIT_TYPES.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
        </select></div>
      {value.workPermitType && (
        <>
          <div><label htmlFor="emp-permit-number" style={lbl}>Permit or visa number</label>
            <input id="emp-permit-number" value={value.workPermitNumber} onChange={e => set({ workPermitNumber: e.target.value })} style={inp} autoComplete="off" /></div>
          <div><label htmlFor="emp-permit-expiry" style={lbl}>Permit expires{permanent ? " (not needed)" : ""}</label>
            <input id="emp-permit-expiry" type="date" value={value.workPermitExpiry} disabled={permanent} onChange={e => set({ workPermitExpiry: e.target.value })} style={inp} />
            <div style={hint}>HR is reminded as it nears, and so is the employee</div></div>
        </>
      )}
      {problem && <div role="alert" style={{ gridColumn: "1 / -1", color: "var(--hf-danger-text)", fontSize: 12 }}>{problem}</div>}
    </>
  )
}
