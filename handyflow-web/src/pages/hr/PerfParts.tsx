// Small components shared by the performance screens.
import { Chip } from "./AttendanceParts"
import { CHECKIN_LABEL, CYCLE_STATUS, GOAL_STATUS, PLAN_STATUS, RATING_LABELS, REVIEW_STATUS, checkinColors, goalColors, planColors, reviewColors } from "./perf.logic"
import { input } from "./perf.shared"
export { Chip, Notice } from "./AttendanceParts"
export { Facts, Field, PersonSelect, Section } from "./ErParts"

export function ReviewStatus({ status }: { status: string }) {
  const [c, b] = reviewColors(status)
  return <Chip text={REVIEW_STATUS[status] ?? status} color={c} bg={b} />
}

export function CycleStatus({ status }: { status: string }) {
  return <Chip text={CYCLE_STATUS[status] ?? status} color={status === "OPEN" ? "var(--hf-info-text)" : "var(--hf-text-muted)"} bg={status === "OPEN" ? "var(--hf-info-soft)" : "var(--hf-surface-sunken)"} />
}

export function GoalStatus({ status }: { status: string }) {
  const [c, b] = goalColors(status)
  return <Chip text={GOAL_STATUS[status] ?? status} color={c} bg={b} />
}

export function PlanStatus({ status }: { status: string }) {
  const [c, b] = planColors(status)
  return <Chip text={PLAN_STATUS[status] ?? status} color={c} bg={b} />
}

export function CheckinChip({ progress }: { progress: string | null }) {
  if (!progress) return <span style={{ color: "var(--hf-text-faint)" }}>No check-in yet</span>
  const [c, b] = checkinColors(progress)
  return <Chip text={CHECKIN_LABEL[progress] ?? progress} color={c} bg={b} />
}

/** A rating from 1 to 5 chosen from a list, with the words beside the numbers. */
export function RatingSelect({ id, value, onChange, empty = "Choose…" }: { id: string; value: number | null; onChange: (v: number | null) => void; empty?: string }) {
  return (
    <select id={id} value={value ?? ""} onChange={e => onChange(e.target.value ? Number(e.target.value) : null)} style={input}>
      <option value="">{empty}</option>
      {RATING_LABELS.map((l, i) => <option key={l} value={i + 1}>{i + 1} – {l}</option>)}
    </select>
  )
}

export function Bar({ percent, label }: { percent: number; label: string }) {
  const p = Math.max(0, Math.min(100, percent))
  return (
    <div role="progressbar" aria-label={label} aria-valuenow={p} aria-valuemin={0} aria-valuemax={100}
      style={{ height: 8, background: "var(--hf-surface-sunken)", borderRadius: 4, overflow: "hidden", minWidth: 80 }}>
      <div style={{ width: `${p}%`, height: "100%", background: "var(--hf-primary)" }} />
    </div>
  )
}

/** Wrapped text kept as the person wrote it. */
export function Prose({ text }: { text: string | null | undefined }) {
  return <div style={{ whiteSpace: "pre-wrap", fontSize: 13, lineHeight: 1.5 }}>{text && text.trim() ? text : "–"}</div>
}

export function Tabs<T extends string>({ tabs, value, onChange }: { tabs: { id: T; label: string }[]; value: T; onChange: (t: T) => void }) {
  return (
    <div style={{ display: "flex", gap: 8, marginBottom: 18, flexWrap: "wrap" }} role="tablist">
      {tabs.map(t => (
        <button key={t.id} role="tab" aria-selected={value === t.id} onClick={() => onChange(t.id)}
          style={{ padding: "6px 14px", borderRadius: 20, fontSize: 13, cursor: "pointer", border: "none", fontWeight: value === t.id ? 600 : 400,
            background: value === t.id ? "var(--hf-primary)" : "var(--hf-surface-sunken)", color: value === t.id ? "var(--hf-text-on-solid)" : "var(--hf-text-muted)" }}>
          {t.label}
        </button>
      ))}
    </div>
  )
}
