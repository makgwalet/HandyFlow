// The "what to check" part of a rule: a field, how to compare, and a value that is a list when the field has fixed choices.
import { FIELDS, choicesFor, opsFor, withField, type CondField, type Condition } from "./rules.logic"

const field: React.CSSProperties = { padding: "6px 9px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13 }

export function ConditionEditor({ value, onChange, optional = false, label = "Condition" }: { value: Condition; onChange: (c: Condition) => void; optional?: boolean; label?: string }) {
  const choices = value.field ? choicesFor(value.field) : null
  return (
    <div role="group" aria-label={label} style={{ display: "flex", gap: 6, flexWrap: "wrap", alignItems: "center" }}>
      <select aria-label={`${label} field`} style={field} value={value.field} onChange={e => onChange(withField(e.target.value as CondField | ""))}>
        <option value="">{optional ? "Every lead" : "Choose what to check…"}</option>{FIELDS.map(f => <option key={f.id} value={f.id}>{f.label}</option>)}</select>
      {value.field && (
        <>
          <select aria-label={`${label} comparison`} style={field} value={value.op} onChange={e => onChange({ ...value, op: e.target.value as Condition["op"] })}>{opsFor(value.field).map(o => <option key={o.id} value={o.id}>{o.label}</option>)}</select>
          {choices
            ? <select aria-label={`${label} value`} style={field} value={value.value} onChange={e => onChange({ ...value, value: e.target.value })}><option value="">Choose…</option>{choices.map(c => <option key={c.id} value={c.id}>{c.label}</option>)}</select>
            : <input aria-label={`${label} value`} style={{ ...field, width: 150 }} inputMode={value.field === "DEAL_VALUE" ? "decimal" : "text"} placeholder={value.field === "DEAL_VALUE" ? "Amount in R" : "Tag"} value={value.value} onChange={e => onChange({ ...value, value: e.target.value })} />}
        </>)}
    </div>
  )
}
