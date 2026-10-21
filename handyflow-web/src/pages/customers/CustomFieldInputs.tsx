// src/pages/customers/CustomFieldInputs.tsx — the account's own fields on the customer form.
import type { CustomField } from "./customFields.logic"

const box: React.CSSProperties = { width: "100%", padding: "6px 9px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13 }

export function CustomFieldInputs({ fields, values, onChange }: { fields: CustomField[]; values: Record<string, string>; onChange: (key: string, value: string) => void }) {
  if (fields.length === 0) return null
  return (
    <fieldset style={{ border: "none", padding: 0, margin: "12px 0 0" }}>
      <legend style={{ fontSize: 12, fontWeight: 700, color: "var(--hf-text-muted)", marginBottom: 6 }}>Additional details</legend>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: 10 }}>
        {fields.map(f => {
          const v = values[f.key] ?? "", label = `${f.label}${f.required ? " *" : ""}`
          return (
            <label key={f.key} style={{ fontSize: 13 }}>
              <span style={{ display: "block", marginBottom: 3 }}>{label}</span>
              {f.type === "CHOICE" || f.type === "YES_NO"
                ? <select aria-label={f.label} style={box} value={v} onChange={e => onChange(f.key, e.target.value)}>
                    <option value="">{f.required ? "Choose…" : "Not set"}</option>
                    {f.type === "YES_NO" ? <><option value="yes">Yes</option><option value="no">No</option></> : f.options.map(o => <option key={o} value={o}>{o}</option>)}
                  </select>
                : <input aria-label={f.label} style={box} type={f.type === "DATE" ? "date" : "text"} inputMode={f.type === "NUMBER" ? "decimal" : undefined} value={v} onChange={e => onChange(f.key, e.target.value)} />}
            </label>)
        })}
      </div>
    </fieldset>
  )
}
