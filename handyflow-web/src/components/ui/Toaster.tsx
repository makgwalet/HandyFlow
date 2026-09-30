// src/components/ui/Toaster.tsx
import { CheckCircle2, AlertCircle, Info, X } from 'lucide-react'
import { useToastStore, type ToastKind } from '../../store/toast.store'

const STYLE: Record<ToastKind, { accent: string; icon: React.ReactNode }> = {
  success: { accent: 'var(--hf-success)', icon: <CheckCircle2 size={16} style={{ color: 'var(--hf-success-text)' }} /> },
  error:   { accent: 'var(--hf-danger)',  icon: <AlertCircle  size={16} style={{ color: 'var(--hf-danger-text)' }} /> },
  info:    { accent: 'var(--hf-info)',    icon: <Info         size={16} style={{ color: 'var(--hf-info-text)' }} /> },
}

/** Fixed bottom-right stack. Errors use role="alert" so screen readers announce them at once. */
export function Toaster() {
  const toasts = useToastStore(s => s.toasts)
  const dismiss = useToastStore(s => s.dismiss)
  if (toasts.length === 0) return null
  return (
    <div aria-live="polite"
      style={{ position: 'fixed', right: 20, bottom: 20, zIndex: 3000, display: 'flex', flexDirection: 'column', gap: 10, maxWidth: 'min(380px, calc(100vw - 40px))' }}>
      {toasts.map(t => (
        <div key={t.id} role={t.kind === 'error' ? 'alert' : 'status'}
          style={{ display: 'flex', alignItems: 'flex-start', gap: 10, background: 'var(--hf-surface)', color: 'var(--hf-text)',
            border: '1px solid var(--hf-border)', borderLeft: `4px solid ${STYLE[t.kind].accent}`, borderRadius: 10,
            padding: '11px 12px 11px 14px', boxShadow: 'var(--hf-shadow-md)', fontSize: 13, lineHeight: 1.45 }}>
          <span style={{ marginTop: 1, flexShrink: 0 }}>{STYLE[t.kind].icon}</span>
          <span style={{ flex: 1 }}>{t.message}</span>
          <button onClick={() => dismiss(t.id)} aria-label="Dismiss"
            style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--hf-text-faint)', padding: 2, display: 'flex' }}>
            <X size={14} />
          </button>
        </div>
      ))}
    </div>
  )
}
