// Small components shared by the employee relations screens.
import type React from "react"
import { input, label, type PersonOption } from "./er.shared"
import { STATUS_LABEL, statusColors } from "./er.logic"
export { Chip, Notice } from "./AttendanceParts"
import { Chip } from "./AttendanceParts"

export function StatusChip({ status }: { status: string }) {
  const [color, bg] = statusColors(status)
  return <Chip text={STATUS_LABEL[status] ?? status} color={color} bg={bg} />
}

export function Field({ id, text, children, hint }: { id: string; text: string; children: React.ReactNode; hint?: string }) {
  return (
    <div style={{ marginBottom: 12 }}>
      <label htmlFor={id} style={label}>{text}</label>
      {children}
      {hint && <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 3 }}>{hint}</div>}
    </div>
  )
}

export function PersonSelect({ id, value, onChange, options, empty = "Choose…", disabled }:
  { id: string; value: string; onChange: (v: string) => void; options: PersonOption[]; empty?: string; disabled?: boolean }) {
  return (
    <select id={id} value={value} onChange={e => onChange(e.target.value)} style={input} disabled={disabled}>
      <option value="">{empty}</option>
      {options.map(o => <option key={o.id} value={o.id}>{o.name}</option>)}
    </select>
  )
}

export function Facts({ rows }: { rows: [string, React.ReactNode][] }) {
  return (
    <dl style={{ display: "grid", gridTemplateColumns: "minmax(140px, 200px) 1fr", gap: "6px 16px", margin: 0, fontSize: 13 }}>
      {rows.filter(([, v]) => v !== null && v !== undefined && v !== "").map(([k, v]) => (
        <div key={k} style={{ display: "contents" }}>
          <dt style={{ color: "var(--hf-text-faint)", fontWeight: 600 }}>{k}</dt>
          <dd style={{ margin: 0, color: "var(--hf-text)", whiteSpace: "pre-wrap" }}>{v}</dd>
        </div>
      ))}
    </dl>
  )
}

export function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section style={{ border: "1px solid var(--hf-border)", borderRadius: 12, background: "var(--hf-surface)", marginBottom: 16, padding: "14px 16px" }}>
      <h3 style={{ margin: "0 0 10px", fontSize: 13, fontWeight: 700, color: "var(--hf-text-secondary)", textTransform: "uppercase", letterSpacing: "0.04em" }}>{title}</h3>
      {children}
    </section>
  )
}
