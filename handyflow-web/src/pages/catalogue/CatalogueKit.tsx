// src/pages/catalogue/CatalogueKit.tsx — the shared pieces of the catalogue screens: field styles, modal, field wrapper,
// delete confirmation, error banner and category menu.
import { useState, useRef, useEffect } from 'react'
import { X, AlertTriangle, AlertCircle, MoreVertical, Pencil, Trash2 } from 'lucide-react'
import { STATUS_LABEL, STATUS_TONE, type Category, type ItemStatus } from './catalogue.logic'

// ── Modal wrapper ─────────────────────────────────────────────────────────────
export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', zIndex: 200, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 24, backdropFilter: 'blur(2px)' }}>
      <div role="dialog" aria-modal="true" aria-label={title} style={{ background: 'var(--hf-surface)', borderRadius: 16, width: '100%', maxWidth: 500, boxShadow: '0 20px 60px rgba(0,0,0,0.2)', overflow: 'hidden', maxHeight: '92vh', display: 'flex', flexDirection: 'column' }}>
        <div style={{ padding: '20px 24px', borderBottom: '1px solid var(--hf-border-subtle)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexShrink: 0 }}>
          <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: 'var(--hf-text)' }}>{title}</h3>
          <button type="button" aria-label="Close" onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--hf-text-faint)', padding: 4, display: 'flex' }}><X size={18} /></button>
        </div>
        <div style={{ padding: 24, overflowY: 'auto' }}>{children}</div>
      </div>
    </div>
  )
}

// ── Field wrapper ─────────────────────────────────────────────────────────────
export function Field({ label, required, error, id, children }: { label: string; required?: boolean; error?: string; id?: string; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 16 }}>
      <label htmlFor={id} style={{ display: 'block', fontSize: 12, fontWeight: 600, color: 'var(--hf-text-secondary)', marginBottom: 6, textTransform: 'uppercase', letterSpacing: '0.04em' }}>
        {label}{required && <span style={{ color: 'var(--hf-danger-text)', marginLeft: 2 }}>*</span>}
      </label>
      {children}
      {error && (
        <div role="alert" style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, color: 'var(--hf-danger-text)', marginTop: 4 }}>
          <AlertCircle size={12} style={{ color: 'var(--hf-danger-text)' }} />{error}
        </div>
      )}
    </div>
  )
}

// ── Delete Confirm Modal ──────────────────────────────────────────────────────
export function DeleteModal({
  title, description, badge, badgeIcon, isPending, error, onConfirm, onCancel, confirmLabel = 'Yes, Delete', pendingLabel = 'Deleting...',
}: {
  confirmLabel?: string; pendingLabel?: string
  title: string; description: React.ReactNode; badge: string
  badgeIcon: React.ReactNode; isPending: boolean; error: string
  onConfirm: () => void; onCancel: () => void
}) {
  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 300, backdropFilter: 'blur(2px)' }}>
      <div role="dialog" aria-modal="true" aria-label={title} style={{ background: 'var(--hf-surface)', borderRadius: 18, padding: 32, width: 420, maxWidth: '92vw', boxShadow: '0 24px 64px rgba(0,0,0,0.18)', display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center' }}>
        <div style={{ width: 56, height: 56, borderRadius: '50%', background: 'var(--hf-orange-soft)', border: '2px solid var(--hf-orange-border)', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 16 }}>
          <AlertTriangle size={24} style={{ color: 'var(--hf-orange-text)' }} strokeWidth={2} />
        </div>
        <h3 style={{ margin: '0 0 6px', fontSize: 18, fontWeight: 700, color: 'var(--hf-text)' }}>{title}</h3>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, background: 'var(--hf-surface-muted)', border: '1px solid var(--hf-border)', borderRadius: 40, padding: '7px 16px', margin: '10px 0' }}>
          {badgeIcon}
          <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--hf-text)' }}>{badge}</span>
        </div>
        <div style={{ margin: '0 0 16px', fontSize: 13, color: 'var(--hf-text-muted)', lineHeight: 1.7 }}>{description}</div>
        {error && (
          <div style={{ width: '100%', marginBottom: 16, padding: '10px 12px', background: 'var(--hf-danger-soft)', border: '1px solid var(--hf-danger-border)', borderRadius: 8, fontSize: 13, color: 'var(--hf-danger-text)', display: 'flex', alignItems: 'center', gap: 8 }}>
            <AlertCircle size={15} style={{ color: 'var(--hf-danger-text)', flexShrink: 0 }} />{error}
          </div>
        )}
        <div style={{ display: 'flex', gap: 10, width: '100%' }}>
          <button onClick={onCancel} disabled={isPending}
            style={{ flex: 1, padding: '11px', border: '1.5px solid var(--hf-border)', borderRadius: 10, background: 'var(--hf-surface)', fontSize: 14, fontWeight: 600, cursor: 'pointer', color: 'var(--hf-text-secondary)' }}>
            Cancel
          </button>
          <button onClick={onConfirm} disabled={isPending}
            style={{ flex: 1, padding: '11px', border: 'none', borderRadius: 10, background: isPending ? 'color-mix(in srgb, var(--hf-primary) 45%, var(--hf-surface))' : 'var(--hf-primary)', color: 'var(--hf-text-on-solid)', fontSize: 14, fontWeight: 700, cursor: isPending ? 'not-allowed' : 'pointer' }}>
            {isPending ? pendingLabel : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Category Options Menu ─────────────────────────────────────────────────────
export function CategoryMenu({ onEdit, onDelete }: { cat: Category; onEdit: () => void; onDelete: () => void }) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const handler = (e: MouseEvent) => { if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false) }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  return (
    <div ref={ref} style={{ position: 'relative' }} onClick={e => e.stopPropagation()}>
      <button
        onClick={() => setOpen(o => !o)}
        style={{ padding: '4px 6px', background: 'none', border: '1px solid var(--hf-border)', borderRadius: 6, cursor: 'pointer', display: 'flex', alignItems: 'center', color: 'var(--hf-text-faint)' }}>
        <MoreVertical size={14} />
      </button>
      {open && (
        <div style={{ position: 'absolute', right: 0, top: 'calc(100% + 4px)', background: 'var(--hf-surface)', border: '1px solid var(--hf-border)', borderRadius: 10, boxShadow: '0 8px 24px rgba(0,0,0,0.1)', zIndex: 100, minWidth: 140, overflow: 'hidden' }}>
          <button onClick={() => { onEdit(); setOpen(false) }}
            style={{ width: '100%', padding: '10px 14px', background: 'none', border: 'none', cursor: 'pointer', fontSize: 13, fontWeight: 500, color: 'var(--hf-text-secondary)', display: 'flex', alignItems: 'center', gap: 8, textAlign: 'left' }}>
            <Pencil size={13} style={{ color: 'var(--hf-info-text)' }} /> Edit category
          </button>
          <button onClick={() => { onDelete(); setOpen(false) }}
            style={{ width: '100%', padding: '10px 14px', background: 'none', border: 'none', cursor: 'pointer', fontSize: 13, fontWeight: 500, color: 'var(--hf-danger-text)', display: 'flex', alignItems: 'center', gap: 8, textAlign: 'left', borderTop: '1px solid var(--hf-danger-border)' }}>
            <Trash2 size={13} style={{ color: 'var(--hf-danger-text)' }} /> Delete category
          </button>
        </div>
      )}
    </div>
  )
}

// ── Error Banner ──────────────────────────────────────────────────────────────
export function ErrorBanner({ message }: { message: string }) {
  return (
    <div style={{ marginBottom: 16, padding: '10px 12px', background: 'var(--hf-danger-soft)', border: '1px solid var(--hf-danger-border)', borderRadius: 8, fontSize: 13, color: 'var(--hf-danger-text)', display: 'flex', alignItems: 'center', gap: 8 }}>
      <AlertCircle size={15} style={{ color: 'var(--hf-danger-text)', flexShrink: 0 }} />{message}
    </div>
  )
}
export function StatusBadge({ status }: { status: ItemStatus }) {
  const t = STATUS_TONE[status]
  return <span style={{ display: 'inline-block', padding: '2px 10px', borderRadius: 20, fontSize: 11.5, fontWeight: 700, color: t.fg, background: t.bg }}>{STATUS_LABEL[status]}</span>
}
