// src/pages/facilities/ui.tsx
//
// Small shared pieces for the Facilities screens. Everything is coloured with theme tokens (never hex), so the
// light and dark themes and the tenant brand colour all work. The generic components/ui/Table and Modal files
// are empty in this repo, so the table, dialog and form fields the module needs live here.
import { useEffect, useRef, type CSSProperties, type ReactNode } from 'react'
import { Search, X, Inbox, AlertTriangle, Loader2 } from 'lucide-react'
import Chip, { type ChipTone } from '../../components/ui/Chip'
import { inputStyle, titleCase } from './format'

// ── status and priority chips ───────────────────────────────────────────────────────────────
const STATUS_TONE: Record<string, ChipTone> = {
  REQUESTED: 'info', OPEN: 'info', ASSIGNED: 'info', IN_PROGRESS: 'accent', ON_HOLD: 'warn', PENDING_VERIFICATION: 'warn',
  COMPLETED: 'ok', REJECTED: 'bad', CANCELLED: 'neutral',
  ACTIVE: 'ok', INACTIVE: 'neutral', UNDER_MAINTENANCE: 'warn', DECOMMISSIONED: 'neutral', CLOSED: 'neutral',
  VALID: 'ok', EXPIRED: 'bad', REVOKED: 'neutral', EXPIRING_SOON: 'warn',
  PENDING: 'warn', APPROVED: 'ok', SUSPENDED: 'bad',
  OPERATIONAL: 'ok', DOWN: 'bad', MAINTENANCE: 'warn',
  DRAFT: 'neutral', PARTLY_RECEIVED: 'warn', RECEIVED: 'ok',
}
export function StatusChip({ status }: { status?: string | null }) {
  return <Chip tone={STATUS_TONE[status ?? ''] ?? 'neutral'}>{titleCase(status)}</Chip>
}

const PRIORITY_TONE: Record<string, ChipTone> = { LOW: 'neutral', NORMAL: 'info', HIGH: 'warn', URGENT: 'bad', EMERGENCY: 'bad' }
export function PriorityChip({ priority }: { priority?: string | null }) {
  return <Chip tone={PRIORITY_TONE[priority ?? ''] ?? 'neutral'}>{titleCase(priority)}</Chip>
}

// ── layout ──────────────────────────────────────────────────────────────────────────────────
export function KpiTile({ label, value, hint, icon, tone = 'neutral', onClick }:
  { label: string; value: ReactNode; hint?: string; icon: ReactNode; tone?: ChipTone | 'neutral'; onClick?: () => void }) {
  const color: Record<string, string> = {
    neutral: 'var(--hf-text-secondary)', ok: 'var(--hf-success-text)', warn: 'var(--hf-warning-text)', bad: 'var(--hf-danger-text)',
    info: 'var(--hf-info-text)', accent: 'var(--hf-accent-text)',
  }
  const c = color[tone]
  const body = (
    <>
      <div style={{ width: 40, height: 40, borderRadius: 10, background: 'var(--hf-surface-sunken)', color: c, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>{icon}</div>
      <div style={{ minWidth: 0, textAlign: 'left' }}>
        <div style={{ fontSize: 24, fontWeight: 800, color: c, lineHeight: 1.1 }}>{value}</div>
        <div style={{ fontSize: 12, color: 'var(--hf-text-muted)', marginTop: 3 }}>{label}</div>
        {hint && <div style={{ fontSize: 11, color: 'var(--hf-text-faint)', marginTop: 1 }}>{hint}</div>}
      </div>
    </>
  )
  const style: CSSProperties = { display: 'flex', gap: 12, alignItems: 'center', background: 'var(--hf-surface)', border: '1px solid var(--hf-border)', borderRadius: 12, padding: '14px 16px', font: 'inherit', color: 'inherit' }
  return onClick
    ? <button type="button" onClick={onClick} style={{ ...style, cursor: 'pointer' }}>{body}</button>
    : <div style={style}>{body}</div>
}

export function Card({ title, action, children, pad = 18 }: { title?: ReactNode; action?: ReactNode; children: ReactNode; pad?: number }) {
  return (
    <section style={{ background: 'var(--hf-surface)', border: '1px solid var(--hf-border)', borderRadius: 12, padding: pad }}>
      {(title || action) && (
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, marginBottom: 12 }}>
          <h3 style={{ margin: 0, fontSize: 14, fontWeight: 700, color: 'var(--hf-text)' }}>{title}</h3>
          {action}
        </div>
      )}
      {children}
    </section>
  )
}

export function Toolbar({ search, onSearch, placeholder = 'Search…', children }:
  { search?: string; onSearch?: (v: string) => void; placeholder?: string; children?: ReactNode }) {
  return (
    <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap', marginBottom: 14 }}>
      {onSearch && (
        <label style={{ position: 'relative', flex: '1 1 220px', maxWidth: 360 }}>
          <Search size={15} aria-hidden="true" style={{ position: 'absolute', left: 11, top: '50%', transform: 'translateY(-50%)', color: 'var(--hf-text-faint)' }} />
          <input value={search ?? ''} onChange={e => onSearch(e.target.value)} placeholder={placeholder} aria-label={placeholder}
            style={{ ...inputStyle, paddingLeft: 33 }} />
        </label>
      )}
      <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap', marginLeft: 'auto' }}>{children}</div>
    </div>
  )
}

// ── table ───────────────────────────────────────────────────────────────────────────────────
export interface Column<T> {
  key: string
  header: string
  render: (row: T) => ReactNode
  align?: 'left' | 'right'
  width?: number | string
}

export function DataTable<T>({ rows, columns, rowKey, onRowClick, loading, error, empty = 'Nothing to show yet.', rowTone }:
  { rows: T[]; columns: Column<T>[]; rowKey: (r: T) => string; onRowClick?: (r: T) => void; loading?: boolean; error?: unknown;
    empty?: string; rowTone?: (r: T) => 'bad' | 'warn' | undefined }) {
  if (error) return <Notice tone="bad" icon={<AlertTriangle size={16} />}>We couldn't load this list. Check your connection and try again.</Notice>
  if (loading) return <div style={{ padding: 28, display: 'flex', justifyContent: 'center', color: 'var(--hf-text-muted)' }}><Loader2 className="animate-spin" size={20} aria-label="Loading" /></div>
  if (rows.length === 0) return <EmptyState>{empty}</EmptyState>
  return (
    <div style={{ overflowX: 'auto', border: '1px solid var(--hf-border)', borderRadius: 10 }}>
      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
        <thead>
          <tr style={{ background: 'var(--hf-surface-muted)' }}>
            {columns.map(c => (
              <th key={c.key} scope="col" style={{ textAlign: c.align ?? 'left', padding: '10px 14px', fontSize: 11, fontWeight: 700, letterSpacing: '.04em', textTransform: 'uppercase', color: 'var(--hf-text-muted)', whiteSpace: 'nowrap', width: c.width }}>{c.header}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map(r => {
            const tone = rowTone?.(r)
            return (
              <tr key={rowKey(r)}
                onClick={onRowClick ? () => onRowClick(r) : undefined}
                onKeyDown={onRowClick ? e => { if (e.key === 'Enter') onRowClick(r) } : undefined}
                tabIndex={onRowClick ? 0 : undefined}
                style={{ borderTop: '1px solid var(--hf-border-subtle)', cursor: onRowClick ? 'pointer' : 'default',
                  background: tone === 'bad' ? 'var(--hf-danger-soft)' : tone === 'warn' ? 'var(--hf-warning-soft)' : undefined }}>
                {columns.map(c => (
                  <td key={c.key} style={{ textAlign: c.align ?? 'left', padding: '11px 14px', color: 'var(--hf-text)', verticalAlign: 'middle' }}>{c.render(r)}</td>
                ))}
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

export function EmptyState({ children }: { children: ReactNode }) {
  return (
    <div style={{ padding: '36px 16px', textAlign: 'center', color: 'var(--hf-text-muted)', fontSize: 13, border: '1px dashed var(--hf-border-strong)', borderRadius: 10 }}>
      <Inbox size={22} aria-hidden="true" style={{ color: 'var(--hf-text-faint)', marginBottom: 8 }} />
      <div>{children}</div>
    </div>
  )
}

export function Notice({ tone, icon, children }: { tone: 'bad' | 'warn' | 'info'; icon?: ReactNode; children: ReactNode }) {
  const t = { bad: ['var(--hf-danger-soft)', 'var(--hf-danger-border)', 'var(--hf-danger-text)'], warn: ['var(--hf-warning-soft)', 'var(--hf-warning-border)', 'var(--hf-warning-text-strong)'], info: ['var(--hf-info-soft)', 'var(--hf-info-border)', 'var(--hf-info-text)'] }[tone]
  return (
    <div role={tone === 'bad' ? 'alert' : 'status'} style={{ display: 'flex', gap: 10, alignItems: 'center', background: t[0], border: `1px solid ${t[1]}`, color: t[2], borderRadius: 10, padding: '11px 14px', fontSize: 13 }}>
      {icon}<div>{children}</div>
    </div>
  )
}

// ── form controls ───────────────────────────────────────────────────────────────────────────
export function Field({ label, children, hint }: { label: string; children: ReactNode; hint?: string }) {
  return (
    <label style={{ display: 'block', marginBottom: 14 }}>
      <span style={{ display: 'block', fontSize: 12.5, fontWeight: 600, color: 'var(--hf-text-secondary)', marginBottom: 5 }}>{label}</span>
      {children}
      {hint && <span style={{ display: 'block', fontSize: 11.5, color: 'var(--hf-text-faint)', marginTop: 4 }}>{hint}</span>}
    </label>
  )
}

type BtnKind = 'primary' | 'secondary' | 'danger'
export function Btn({ kind = 'secondary', busy, children, style, ...rest }:
  { kind?: BtnKind; busy?: boolean } & React.ButtonHTMLAttributes<HTMLButtonElement>) {
  const k: Record<BtnKind, CSSProperties> = {
    primary: { background: 'var(--hf-accent)', color: 'var(--hf-text-on-solid)', border: '1px solid transparent' },
    secondary: { background: 'var(--hf-surface)', color: 'var(--hf-text-secondary)', border: '1px solid var(--hf-border-strong)' },
    danger: { background: 'var(--hf-danger)', color: 'var(--hf-text-on-solid)', border: '1px solid transparent' },
  }
  return (
    <button type="button" {...rest} disabled={rest.disabled || busy}
      style={{ display: 'inline-flex', alignItems: 'center', gap: 6, padding: '8px 14px', borderRadius: 8, fontSize: 13, fontWeight: 600,
        cursor: rest.disabled || busy ? 'not-allowed' : 'pointer', opacity: rest.disabled || busy ? 0.6 : 1, ...k[kind], ...style }}>
      {busy && <Loader2 size={14} className="animate-spin" aria-hidden="true" />}{children}
    </button>
  )
}

// ── dialog and side drawer ──────────────────────────────────────────────────────────────────
// Open dialogs, topmost last: Escape closes only the topmost one (a confirmation inside a side panel).
const openDialogs: Array<() => void> = []

/** Modal dialog; the shell's overlays sit at z-index 40-45, page overlays at 50 and above. */
export function Dialog({ title, onClose, children, footer, width = 520, side = false }:
  { title: string; onClose: () => void; children: ReactNode; footer?: ReactNode; width?: number; side?: boolean }) {
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const prev = document.activeElement as HTMLElement | null
    ref.current?.focus()
    openDialogs.push(onClose)
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape' && openDialogs[openDialogs.length - 1] === onClose) onClose() }
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('keydown', onKey)
      const i = openDialogs.lastIndexOf(onClose); if (i >= 0) openDialogs.splice(i, 1)
      prev?.focus?.()
    }
  }, [onClose])
  return (
    <div onMouseDown={e => { if (e.target === e.currentTarget) onClose() }}
      style={{ position: 'fixed', inset: 0, zIndex: 60, background: 'rgba(15, 23, 42, 0.45)', display: 'flex', justifyContent: side ? 'flex-end' : 'center', alignItems: side ? 'stretch' : 'center', padding: side ? 0 : 16 }}>
      <div ref={ref} role="dialog" aria-modal="true" aria-label={title} tabIndex={-1}
        style={{ background: 'var(--hf-surface)', color: 'var(--hf-text)', width: `min(${width}px, 100%)`, maxHeight: side ? '100%' : '90vh', display: 'flex', flexDirection: 'column',
          borderRadius: side ? 0 : 14, borderLeft: side ? '1px solid var(--hf-border)' : undefined, boxShadow: 'var(--hf-shadow-md)', outline: 'none' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '16px 20px', borderBottom: '1px solid var(--hf-border)' }}>
          <h2 style={{ margin: 0, fontSize: 16, fontWeight: 700 }}>{title}</h2>
          <button type="button" onClick={onClose} aria-label="Close" style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--hf-text-muted)', display: 'flex' }}><X size={18} /></button>
        </div>
        <div style={{ padding: 20, overflowY: 'auto', flex: 1 }}>{children}</div>
        {footer && <div style={{ padding: '14px 20px', borderTop: '1px solid var(--hf-border)', display: 'flex', justifyContent: 'flex-end', gap: 10, flexWrap: 'wrap' }}>{footer}</div>}
      </div>
    </div>
  )
}

export function DefList({ items }: { items: [string, ReactNode][] }) {
  return (
    <dl style={{ display: 'grid', gridTemplateColumns: 'minmax(110px, 38%) 1fr', gap: '9px 14px', margin: 0, fontSize: 13 }}>
      {items.map(([k, v]) => (
        <div key={k} style={{ display: 'contents' }}>
          <dt style={{ color: 'var(--hf-text-muted)' }}>{k}</dt>
          <dd style={{ margin: 0, color: 'var(--hf-text)', wordBreak: 'break-word' }}>{v ?? '—'}</dd>
        </div>
      ))}
    </dl>
  )
}
