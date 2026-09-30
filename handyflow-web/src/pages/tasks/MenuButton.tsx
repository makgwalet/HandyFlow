// src/pages/tasks/MenuButton.tsx
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Check } from 'lucide-react'

export interface MenuItem {
  label: string
  onSelect?: () => void
  /** A non-interactive section label (e.g. "Move to"). */
  heading?: boolean
  disabled?: boolean
  checked?: boolean
  danger?: boolean
}

interface Props {
  /** Accessible name for the trigger, e.g. "Actions for Fix the gate". */
  label: string
  icon: ReactNode
  items: MenuItem[]
  /** Visible text next to the icon (the export button has one; the card's "..." does not). */
  text?: string
  triggerStyle?: React.CSSProperties
}

const MENU_WIDTH = 210
const ROW_HEIGHT = 34

/**
 * A button that opens a small menu. The menu is position:fixed, placed from the trigger's rectangle,
 * so it is not clipped by a scrolling column. It closes on outside click, Escape, or any scroll. The
 * wrapper stops pointer/click/key events so using it never starts a drag or opens the card behind it.
 */
export function MenuButton({ label, icon, items, text, triggerStyle }: Props) {
  const [pos, setPos] = useState<{ top: number; left: number } | null>(null)
  const wrap = useRef<HTMLDivElement>(null)
  const open = pos !== null

  useEffect(() => {
    if (!open) return
    const close = () => setPos(null)
    const onDown = (e: MouseEvent) => { if (!wrap.current?.contains(e.target as Node)) close() }
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') close() }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    window.addEventListener('scroll', close, true)
    window.addEventListener('resize', close)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
      window.removeEventListener('scroll', close, true)
      window.removeEventListener('resize', close)
    }
  }, [open])

  const toggle = (e: React.MouseEvent<HTMLButtonElement>) => {
    if (open) { setPos(null); return }
    const r = e.currentTarget.getBoundingClientRect()
    const height = items.length * ROW_HEIGHT + 12
    const left = Math.max(8, Math.min(r.right - MENU_WIDTH, window.innerWidth - MENU_WIDTH - 8))
    const top = r.bottom + 4 + height > window.innerHeight ? Math.max(8, r.top - 4 - height) : r.bottom + 4
    setPos({ top, left })
  }

  return (
    <div ref={wrap} style={{ position: 'relative', display: 'inline-flex' }}
      onPointerDown={e => e.stopPropagation()} onClick={e => e.stopPropagation()} onKeyDown={e => e.stopPropagation()}>
      <button type="button" onClick={toggle} aria-label={label} aria-haspopup="menu" aria-expanded={open}
        style={{ display: 'inline-flex', alignItems: 'center', gap: 6, background: 'none', border: 'none', cursor: 'pointer',
          color: 'var(--hf-text-muted)', padding: 4, borderRadius: 6, fontSize: 13, fontWeight: 600, ...triggerStyle }}>
        {icon}{text}
      </button>
      {open && pos && (
        <div role="menu" aria-label={label}
          style={{ position: 'fixed', top: pos.top, left: pos.left, width: MENU_WIDTH, zIndex: 2500, background: 'var(--hf-surface)',
            border: '1px solid var(--hf-border)', borderRadius: 10, padding: 6, boxShadow: 'var(--hf-shadow-md)',
            maxHeight: 'min(360px, calc(100vh - 16px))', overflowY: 'auto' }}>
          {items.map((it, i) => it.heading ? (
            <div key={i} style={{ padding: '8px 10px 4px', fontSize: 10, fontWeight: 700, letterSpacing: '0.06em',
              textTransform: 'uppercase', color: 'var(--hf-text-faint)' }}>{it.label}</div>
          ) : (
            <button key={i} type="button" role="menuitem" disabled={it.disabled}
              onClick={() => { setPos(null); it.onSelect?.() }}
              style={{ display: 'flex', alignItems: 'center', gap: 8, width: '100%', height: ROW_HEIGHT, padding: '0 10px', border: 'none',
                borderRadius: 7, background: 'none', textAlign: 'left', fontSize: 13, fontWeight: 500,
                cursor: it.disabled ? 'default' : 'pointer',
                color: it.disabled ? 'var(--hf-text-disabled)' : it.danger ? 'var(--hf-danger-text)' : 'var(--hf-text-secondary)' }}
              onMouseEnter={e => { if (!it.disabled) e.currentTarget.style.background = 'var(--hf-surface-sunken)' }}
              onMouseLeave={e => { e.currentTarget.style.background = 'none' }}>
              <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{it.label}</span>
              {it.checked && <Check size={14} />}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
