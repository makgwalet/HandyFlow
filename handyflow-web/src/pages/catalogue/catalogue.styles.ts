// Shared field styles for the catalogue screens.
import type { CSSProperties } from 'react'

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


export const primaryBtn: CSSProperties = { padding: '9px 18px', background: 'var(--hf-primary)', border: 'none', borderRadius: 9, fontSize: 13, fontWeight: 600, color: 'var(--hf-text-on-solid)', cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: 6 }
export const cancelBtn: CSSProperties = { padding: '9px 16px', background: 'var(--hf-surface)', border: '1px solid var(--hf-border)', borderRadius: 9, fontSize: 13, fontWeight: 600, cursor: 'pointer', color: 'var(--hf-text-secondary)' }
export const linkBtn: CSSProperties = { padding: '4px 10px', background: 'none', border: '1px solid var(--hf-border)', borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: 'pointer', color: 'var(--hf-text-secondary)' }
export const th: CSSProperties = { textAlign: 'left', padding: '10px 14px', fontSize: 11, fontWeight: 700, color: 'var(--hf-text-faint)', textTransform: 'uppercase', letterSpacing: '0.05em', borderBottom: '1px solid var(--hf-border-subtle)' }
export const td: CSSProperties = { padding: '11px 14px', fontSize: 13.5, color: 'var(--hf-text)', borderBottom: '1px solid var(--hf-border-subtle)', verticalAlign: 'middle' }
