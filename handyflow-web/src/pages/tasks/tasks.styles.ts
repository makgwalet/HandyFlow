// src/pages/tasks/tasks.styles.ts
// Shared inline styles. Kept apart from tasks.ui.tsx so that file only exports components (fast refresh).

export const inp: React.CSSProperties = {
  width: '100%', padding: '9px 12px', border: '1.5px solid var(--hf-border)', borderRadius: 8,
  fontSize: 14, boxSizing: 'border-box', background: 'var(--hf-surface)', color: 'var(--hf-text)', outline: 'none',
}

export const lbl: React.CSSProperties = {
  display: 'block', fontSize: 11, fontWeight: 700, color: 'var(--hf-text-muted)',
  textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 6,
}

export const btnPrimary: React.CSSProperties = {
  display: 'inline-flex', alignItems: 'center', gap: 6, background: 'var(--hf-primary)', color: 'var(--hf-text-on-solid)',
  border: 'none', borderRadius: 8, padding: '9px 16px', fontSize: 13, fontWeight: 600, cursor: 'pointer',
}

export const btnSecondary: React.CSSProperties = {
  padding: '9px 16px', border: '1.5px solid var(--hf-border)', borderRadius: 8, background: 'var(--hf-surface)',
  fontSize: 13, cursor: 'pointer', color: 'var(--hf-text-secondary)', fontWeight: 500,
}
