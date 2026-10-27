// src/pages/facilities/format.ts
//
// Formatting helpers and shared style constants for the Facilities screens (kept out of ui.tsx so that file only
// exports components, which React fast refresh needs).
import type { CSSProperties } from 'react'
import type { ChipTone } from '../../components/ui/Chip'

export const fmtDate = (iso?: string | null) =>
  iso ? new Date(iso.length === 10 ? `${iso}T00:00:00` : iso).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' }) : '—'
export const fmtDateTime = (iso?: string | null) =>
  iso ? new Date(iso).toLocaleString('en-ZA', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—'
export const fmtMoney = (n?: number | null) =>
  n == null ? '—' : `R ${Number(n).toLocaleString('en-ZA', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
export const fmtQty = (n?: number | null) => (n == null ? '—' : Number(n).toLocaleString('en-ZA', { maximumFractionDigits: 3 }))
export const titleCase = (s?: string | null) => (s ? s.toLowerCase().replace(/_/g, ' ').replace(/^\w/, c => c.toUpperCase()) : '—')

/** Days from today to a date (negative when past). */
export function daysUntil(iso?: string | null): number | null {
  if (!iso) return null
  const d = new Date(`${iso.slice(0, 10)}T00:00:00`)
  const t = new Date(); t.setHours(0, 0, 0, 0)
  return Math.round((d.getTime() - t.getTime()) / 86_400_000)
}


export const SEVERITY_TONE: Record<string, ChipTone> = { CRITICAL: 'bad', HIGH: 'warn', MEDIUM: 'info' }


export const kpiGrid: CSSProperties = { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(165px, 1fr))', gap: 12, marginBottom: 20 }


export const inputStyle: CSSProperties = {
  width: '100%', padding: '9px 12px', border: '1px solid var(--hf-border-strong)', borderRadius: 8, fontSize: 14,
  boxSizing: 'border-box', background: 'var(--hf-surface)', color: 'var(--hf-text)', outline: 'none',
}


/** Saves a downloaded blob under a file name (the browser's normal download). */
export function saveBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = fileName
  document.body.appendChild(a); a.click(); a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 10_000)
}

export const fmtBytes = (n: number) => (n < 1024 ? `${n} B` : n < 1_048_576 ? `${(n / 1024).toFixed(0)} KB` : `${(n / 1_048_576).toFixed(1)} MB`)

export const CERTIFICATE_TYPES = ['ELECTRICAL_COC', 'FIRE_EQUIPMENT', 'ELEVATOR', 'GAS', 'OTHER']
export const VENDOR_DOC_TYPES = ['PUBLIC_LIABILITY_INSURANCE', 'COIDA_LETTER', 'TAX_CLEARANCE', 'TRADE_LICENCE', 'BEE_CERTIFICATE', 'OTHER']
export const LICENCE_TYPES = ['ELECTRICAL_WIREMAN', 'GAS_INSTALLER', 'PLUMBING', 'HVAC_REFRIGERATION', 'FIRE_EQUIPMENT', 'WORKING_AT_HEIGHTS', 'FIRST_AID', 'DRIVERS_LICENCE', 'OTHER']
