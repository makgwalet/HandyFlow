import type { ReactNode } from 'react'
import { AlertCircle, X } from 'lucide-react'

/** Small shared pieces for the Legal Compliance screens. */

export function Modal({ title, onClose, wide, children }: { title: string; onClose: () => void; wide?: boolean; children: ReactNode }) {
  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, backdropFilter: 'blur(2px)' }}>
      <div role="dialog" aria-label={title} style={{ background: 'var(--hf-surface)', borderRadius: 16, padding: 28, width: wide ? 820 : 560, maxWidth: '94vw', maxHeight: '92vh', overflowY: 'auto', boxShadow: '0 20px 60px rgba(0,0,0,0.2)' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
          <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: 'var(--hf-text)' }}>{title}</h3>
          <button onClick={onClose} aria-label="Close" style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--hf-text-faint)', display: 'flex' }}><X size={20} /></button>
        </div>
        {children}
      </div>
    </div>
  )
}

export function Field({ l, children }: { l: string; children: ReactNode }) {
  return (
    <label style={{ display: 'block', marginBottom: 14 }}>
      <span style={{ display: 'block', fontSize: 13, fontWeight: 600, color: 'var(--hf-text-secondary)', marginBottom: 5 }}>{l}</span>
      {children}
    </label>
  )
}

export function Err({ m }: { m: string }) {
  if (!m) return null
  return (
    <div role="alert" style={{ margin: '0 0 14px', padding: '10px 12px', background: 'var(--hf-danger-soft)', border: '1px solid var(--hf-danger-border)', borderRadius: 8, fontSize: 13, color: 'var(--hf-danger-text)', display: 'flex', alignItems: 'center', gap: 8 }}>
      <AlertCircle size={14} />{m}
    </div>
  )
}

export function Badge({ text, color, bg }: { text: string; color: string; bg: string }) {
  return <span style={{ display: 'inline-block', padding: '3px 10px', borderRadius: 999, fontSize: 11, fontWeight: 700, color, background: bg, whiteSpace: 'nowrap' }}>{text}</span>
}

/** Largest list the server sends for one screen (RowLimit.MAX_ROWS on the backend). */
export const LC_ROW_LIMIT = 500

/** Shown when a list came back at the server's limit: there may be more rows than are on screen. */
export function RowLimitNotice({ count }: { count: number }) {
  if (count < LC_ROW_LIMIT) return null
  return (
    <p role="status" style={{ margin: '0 0 12px', padding: '8px 12px', background: 'var(--hf-warning-soft, #FFFBEB)', border: '1px solid var(--hf-warning-border, #FDE68A)', borderRadius: 8, fontSize: 13, color: 'var(--hf-warning-text, #92400E)' }}>
      Showing the first {LC_ROW_LIMIT} rows. Use the filters above, or switch off "include closed", to narrow the list.
    </p>
  )
}
