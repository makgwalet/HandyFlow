// src/pages/projects/tabs/pmKit.tsx
// Small shared pieces for the project control screens (change orders, cost control, schedule, lifecycle, time, portal).
import React, { useState } from 'react'
import { X } from 'lucide-react'
import { apiClient } from '../../../api/client'
import { errorText } from '../../../api/projectDocs'
import { filenameFrom, saveBlob } from '../../../api/recipientAccess.client'
import { toast } from '../../../store/toast.store'

/** apiClient already unwraps ApiResponse into { data }; some older endpoints return pages. */
export function list<T>(r: any): T[] { const d = r?.data?.data ?? r?.data ?? []; return Array.isArray(d) ? d as T[] : d?.content ?? [] }
export function one<T>(r: any): T { return (r?.data?.data ?? r?.data) as T }

export const money = (n: number | null | undefined, dp = 0) =>
  n == null ? '—' : `${n < 0 ? '−' : ''}R ${Math.abs(Number(n)).toLocaleString('en-ZA', { minimumFractionDigits: dp, maximumFractionDigits: dp })}`
export const signedMoney = (n: number | null | undefined) => n == null ? '—' : `${n > 0 ? '+' : ''}${money(n)}`
export const day = (d: string | null | undefined) => d ? new Date(d.length === 10 ? d + 'T00:00:00' : d).toLocaleDateString('en-ZA', { day: '2-digit', month: 'short', year: 'numeric' }) : '—'
export const when = (d: string | null | undefined) => d ? new Date(d).toLocaleString('en-ZA', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—'
export const today = () => new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10)

export const inp: React.CSSProperties = { width: '100%', padding: '8px 11px', border: '1.5px solid var(--hf-border)', borderRadius: 8, fontSize: 13, boxSizing: 'border-box', outline: 'none', background: 'var(--hf-surface)', color: 'var(--hf-text)' }
export const th: React.CSSProperties = { textAlign: 'left', padding: '8px 10px', fontSize: 11, fontWeight: 700, color: 'var(--hf-text-muted)', textTransform: 'uppercase', letterSpacing: '.03em', borderBottom: '1px solid var(--hf-border)', whiteSpace: 'nowrap' }
export const td: React.CSSProperties = { padding: '8px 10px', fontSize: 13, color: 'var(--hf-text)', borderBottom: '1px solid var(--hf-border-subtle, var(--hf-border))', verticalAlign: 'top' }
export const card: React.CSSProperties = { background: 'var(--hf-surface)', border: '1px solid var(--hf-border)', borderRadius: 12, padding: 16 }
export const muted: React.CSSProperties = { fontSize: 12, color: 'var(--hf-text-muted)' }

/** Shows the server's message as a toast; returns it for inline display too. */
export async function fail(e: unknown, fallback: string): Promise<string> {
  const m = await errorText(e, fallback); toast.error(m); return m
}

export async function downloadFile(url: string, fallbackName: string) {
  try {
    const r = await apiClient.get(url, { responseType: 'blob' })
    saveBlob(r.data as Blob, filenameFrom(r.headers?.['content-disposition']) || fallbackName)
  } catch (e) { await fail(e, 'Could not download the file') }
}

type Tone = 'neutral' | 'info' | 'success' | 'warning' | 'danger' | 'violet'
const TONE: Record<Tone, { bg: string; color: string }> = {
  neutral: { bg: 'var(--hf-surface-sunken)', color: 'var(--hf-text-tertiary)' },
  info: { bg: 'var(--hf-info-soft-strong)', color: 'var(--hf-info-text)' },
  success: { bg: 'var(--hf-success-soft-strong)', color: 'var(--hf-success-text-strong)' },
  warning: { bg: 'var(--hf-warning-soft-strong)', color: 'var(--hf-warning-text-deep)' },
  danger: { bg: 'var(--hf-danger-soft)', color: 'var(--hf-danger-text)' },
  violet: { bg: 'var(--hf-violet-soft-strong)', color: 'var(--hf-violet-text)' },
}
export function Pill({ tone = 'neutral', children }: { tone?: Tone; children: React.ReactNode }) {
  const t = TONE[tone]
  return <span style={{ background: t.bg, color: t.color, fontSize: 11, fontWeight: 700, padding: '2px 8px', borderRadius: 20, whiteSpace: 'nowrap' }}>{children}</span>
}

export function Btn({ onClick, kind = 'secondary', disabled, children, title, type = 'button', small }:
  { onClick?: () => void; kind?: 'primary' | 'secondary' | 'danger' | 'ghost'; disabled?: boolean; children: React.ReactNode; title?: string; type?: 'button' | 'submit'; small?: boolean }) {
  const s: Record<string, React.CSSProperties> = {
    primary: { background: 'var(--hf-primary)', color: 'var(--hf-text-on-solid)', border: '1px solid var(--hf-primary)' },
    secondary: { background: 'var(--hf-surface)', color: 'var(--hf-text)', border: '1px solid var(--hf-border)' },
    danger: { background: 'var(--hf-danger-soft)', color: 'var(--hf-danger-text)', border: '1px solid var(--hf-danger-border, var(--hf-border))' },
    ghost: { background: 'none', color: 'var(--hf-text-muted)', border: '1px solid transparent' },
  }
  return (
    <button type={type} onClick={onClick} disabled={disabled} title={title}
      style={{ ...s[kind], display: 'inline-flex', alignItems: 'center', gap: 5, padding: small ? '4px 9px' : '7px 13px', borderRadius: 8,
        fontSize: small ? 12 : 13, fontWeight: 600, cursor: disabled ? 'not-allowed' : 'pointer', opacity: disabled ? 0.55 : 1, whiteSpace: 'nowrap' }}>
      {children}
    </button>
  )
}

export function Field({ label, hint, children }: { label: string; hint?: React.ReactNode; children: React.ReactNode }) {
  return (
    <label style={{ display: 'block', marginBottom: 12 }}>
      <span style={{ display: 'block', fontSize: 12, fontWeight: 600, color: 'var(--hf-text-secondary, var(--hf-text))', marginBottom: 4 }}>{label}</span>
      {children}
      {hint && <span style={{ display: 'block', fontSize: 11, color: 'var(--hf-text-faint)', marginTop: 3 }}>{hint}</span>}
    </label>
  )
}

export function Dialog({ title, onClose, children, width = 520, footer }:
  { title: React.ReactNode; onClose: () => void; children: React.ReactNode; width?: number; footer?: React.ReactNode }) {
  return (
    <div role="dialog" aria-modal="true" onMouseDown={e => { if (e.target === e.currentTarget) onClose() }}
      style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,.45)', zIndex: 2000, display: 'flex', alignItems: 'flex-start', justifyContent: 'center', padding: '6vh 16px', overflowY: 'auto' }}>
      <div style={{ background: 'var(--hf-surface)', borderRadius: 14, width: '100%', maxWidth: width, boxShadow: 'var(--hf-shadow-lg, 0 20px 50px rgba(0,0,0,.25))' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '14px 18px', borderBottom: '1px solid var(--hf-border)' }}>
          <div style={{ fontSize: 15, fontWeight: 700, color: 'var(--hf-text)' }}>{title}</div>
          <button onClick={onClose} aria-label="Close" style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--hf-text-faint)', display: 'flex' }}><X size={17} /></button>
        </div>
        <div style={{ padding: 18 }}>{children}</div>
        {footer && <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, padding: '12px 18px', borderTop: '1px solid var(--hf-border)' }}>{footer}</div>}
      </div>
    </div>
  )
}

/** Asks for a reason (or other text) before an action; the action's own error stays in the dialog. */
export function ReasonDialog({ title, label = 'Reason', intro, confirm, danger, required = true, max = 2000, onSubmit, onClose }:
  { title: string; label?: string; intro?: React.ReactNode; confirm: string; danger?: boolean; required?: boolean; max?: number
    onSubmit: (text: string) => Promise<unknown>; onClose: () => void }) {
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState('')
  const go = async () => {
    if (required && !text.trim()) { setErr(`${label} is required`); return }
    setBusy(true); setErr('')
    try { await onSubmit(text.trim()); onClose() } catch (e) { setErr(await errorText(e, 'That did not work')) } finally { setBusy(false) }
  }
  return (
    <Dialog title={title} onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind={danger ? 'danger' : 'primary'} disabled={busy} onClick={go}>{busy ? 'Working…' : confirm}</Btn></>}>
      {intro && <div style={{ ...muted, marginBottom: 12, lineHeight: 1.5 }}>{intro}</div>}
      <Field label={required ? label : `${label} (optional)`}>
        <textarea autoFocus rows={4} maxLength={max} value={text} onChange={e => setText(e.target.value)} style={{ ...inp, resize: 'vertical' }} />
      </Field>
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

export function ErrorLine({ text }: { text: string }) {
  return <div role="alert" style={{ background: 'var(--hf-danger-soft)', color: 'var(--hf-danger-text)', fontSize: 12, padding: '8px 10px', borderRadius: 8, marginTop: 4 }}>{text}</div>
}

export function Stat({ label, value, sub, tone }: { label: string; value: React.ReactNode; sub?: React.ReactNode; tone?: 'good' | 'bad' | 'warn' }) {
  const color = tone === 'good' ? 'var(--hf-success-text)' : tone === 'bad' ? 'var(--hf-danger-text)' : tone === 'warn' ? 'var(--hf-warning-text)' : 'var(--hf-text)'
  return (
    <div style={{ minWidth: 0 }}>
      <div style={{ fontSize: 11, fontWeight: 600, color: 'var(--hf-text-muted)', textTransform: 'uppercase', letterSpacing: '.03em', marginBottom: 3 }}>{label}</div>
      <div style={{ fontSize: 17, fontWeight: 800, color, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{value}</div>
      {sub && <div style={{ fontSize: 11, color: 'var(--hf-text-faint)', marginTop: 2 }}>{sub}</div>}
    </div>
  )
}

export function Empty({ children }: { children: React.ReactNode }) {
  return <div style={{ padding: '28px 12px', textAlign: 'center', color: 'var(--hf-text-faint)', fontSize: 13 }}>{children}</div>
}

export function SectionTitle({ children, right }: { children: React.ReactNode; right?: React.ReactNode }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10, margin: '4px 0 10px' }}>
      <div style={{ fontSize: 14, fontWeight: 700, color: 'var(--hf-text)' }}>{children}</div>
      {right && <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>{right}</div>}
    </div>
  )
}
