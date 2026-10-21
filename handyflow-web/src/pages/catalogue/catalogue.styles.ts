// Shared field styles and the unit list for the catalogue screens.
import type { CSSProperties } from 'react'

export const UNIT_GROUPS = [
  { group: 'Time',           options: ['Hour', 'Half Day', 'Day', 'Week', 'Month', 'Year'] },
  { group: 'Quantity',       options: ['Each', 'Unit', 'Pair', 'Set', 'Box', 'Pallet', 'Dozen'] },
  { group: 'Weight',         options: ['kg', 'Ton', 'g'] },
  { group: 'Volume',         options: ['Litre', 'ML', 'm³', 'Gallon'] },
  { group: 'Distance/Area',  options: ['km', 'm', 'm²', 'Linear metre', 'Square metre'] },
  { group: 'Transport',      options: ['Per Trip', 'Trip', 'Load', 'Delivery'] },
  { group: 'Service',        options: ['Job', 'Visit', 'Session', 'Project', 'Contract', 'Per Person', 'Per Event', 'Call-out'] },
  { group: 'Trade',          options: ['Point', 'Connection', 'Slab', 'Panel', 'Bay'] },
  { group: 'Hospitality',    options: ['Per Night', 'Per Plate', 'Per Table', 'Per Guest'] },
  { group: 'Medical',        options: ['Consultation', 'Procedure', 'Script', 'Test'] },
]

export const ALL_UNITS = UNIT_GROUPS.flatMap(g => g.options)

// ── Shared styles ─────────────────────────────────────────────────────────────
export const inp: CSSProperties = {
  width: '100%', padding: '10px 12px', border: '1.5px solid var(--hf-border)', borderRadius: 9,
  fontSize: 14, color: 'var(--hf-text)', outline: 'none', boxSizing: 'border-box', background: 'var(--hf-surface)',
}
export const inpErr: CSSProperties = { ...inp, borderColor: 'var(--hf-danger)', background: 'var(--hf-danger-soft)' }
export const sel: CSSProperties = {
  ...inp, appearance: 'none',
  backgroundImage: `url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%2394A3B8' stroke-width='2'%3E%3Cpath d='M6 9l6 6 6-6'/%3E%3C/svg%3E")`,
  backgroundRepeat: 'no-repeat', backgroundPosition: 'right 12px center', paddingRight: 36, cursor: 'pointer',
}

