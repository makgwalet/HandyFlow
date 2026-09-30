// src/pages/tasks/tasks.ui.tsx
import { btnPrimary, btnSecondary } from './tasks.styles'
import {
  AlertTriangle,
} from 'lucide-react'
import { PRIORITY, initials } from './tasks.constants'


export const Badge = ({ priority }: { priority: string }) => {
  const p = PRIORITY[priority] || PRIORITY.NORMAL
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: p.bg, color: p.color, border: `1px solid ${p.border}`, padding: '2px 8px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
      <span style={{ width: 5, height: 5, borderRadius: '50%', background: p.dot, flexShrink: 0 }} />{p.label}
    </span>
  )
}

export const Avatar = ({ name, size = 26 }: { name: string | null; size?: number }) => (
  <div style={{ width: size, height: size, borderRadius: '50%', background: 'var(--hf-primary)', color: 'var(--hf-text-on-solid)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: size * 0.38, fontWeight: 700, flexShrink: 0 }}>
    {initials(name)}
  </div>
)

export const ProgressBar = ({ value, max, color = 'var(--hf-primary-text)' }: { value: number; max: number; color?: string }) => {
  const pct = max > 0 ? Math.min(100, (value / max) * 100) : 0
  return (
    <div style={{ height: 4, background: 'var(--hf-surface-sunken)', borderRadius: 99, overflow: 'hidden' }}>
      <div style={{ width: `${pct}%`, height: '100%', background: color, borderRadius: 99, transition: 'width 0.3s' }} />
    </div>
  )
}


// ── Confirm Modal ──────────────────────────────────────────────────────────
export function ConfirmModal({ title, message, confirmLabel = 'Confirm', danger = false, onConfirm, onCancel }: {
  title: string; message: string; confirmLabel?: string; danger?: boolean
  onConfirm: () => void; onCancel: () => void
}) {
  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 2000, backdropFilter: 'blur(2px)' }}>
      <div style={{ background: 'var(--hf-surface)', borderRadius: 14, padding: 28, width: 400, boxShadow: '0 20px 60px rgba(0,0,0,0.22)' }}>
        <div style={{ display: 'flex', alignItems: 'flex-start', gap: 14, marginBottom: 22 }}>
          <div style={{ width: 40, height: 40, borderRadius: '50%', background: danger ? 'var(--hf-danger-soft)' : 'var(--hf-info-soft)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
            <AlertTriangle size={18} style={{ color: danger ? 'var(--hf-danger-text)' : 'var(--hf-info-text)' }} />
          </div>
          <div>
            <div style={{ fontWeight: 700, fontSize: 15, color: 'var(--hf-text)', marginBottom: 6 }}>{title}</div>
            <div style={{ fontSize: 13, color: 'var(--hf-text-muted)', lineHeight: 1.6 }}>{message}</div>
          </div>
        </div>
        <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
          <button onClick={onCancel} style={btnSecondary}>Cancel</button>
          <button onClick={onConfirm} style={{ ...btnPrimary, background: danger ? 'var(--hf-danger)' : 'var(--hf-primary)' }}>{confirmLabel}</button>
        </div>
      </div>
    </div>
  )
}
