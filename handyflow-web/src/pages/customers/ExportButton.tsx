// src/pages/customers/ExportButton.tsx
import { useState, useRef, useEffect, useCallback } from 'react'
import { Download, ChevronDown, AlertCircle, X } from 'lucide-react'
import { apiClient } from '../../api/client'
import styles from './CustomersPage.module.css'

export function ExportButton() {
  const [open, setOpen]       = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError]     = useState<string | null>(null)
  const ref                   = useRef<HTMLDivElement>(null)
  const dismissTimer          = useRef<ReturnType<typeof setTimeout>>()

  // Close dropdown on outside click
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  // Auto-dismiss error after 5s
  const showError = useCallback((msg: string) => {
    setError(msg)
    clearTimeout(dismissTimer.current)
    dismissTimer.current = setTimeout(() => setError(null), 5000)
  }, [])

  useEffect(() => () => clearTimeout(dismissTimer.current), [])

  const download = async (endpoint: string, filename: string) => {
    setLoading(true); setOpen(false); setError(null)
    try {
      const res  = await apiClient.get(endpoint, { responseType: 'blob' })

      // Check if the server returned an error as JSON inside the blob
      // (some servers send 401/500 as JSON even when responseType is blob)
      if (res.headers['content-type']?.includes('application/json')) {
        const text = await (res.data as Blob).text()
        const json = JSON.parse(text)
        throw new Error(json?.message ?? 'Export failed')
      }

      const url  = URL.createObjectURL(new Blob([res.data], { type: 'text/csv;charset=utf-8;' }))
      const link = document.createElement('a')
      link.href = url; link.download = filename
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      URL.revokeObjectURL(url)
    } catch (e: any) {
      const msg = e?.response?.data?.message
             ?? e?.message
             ?? 'Export failed. Check your connection and try again.'
      showError(msg)
    } finally {
      setLoading(false)
    }
  }

  const today = new Date().toISOString().slice(0, 10)

  return (
    <div ref={ref} style={{ position: 'relative' }}>
      <button
        className={styles.btnOutline}
        onClick={() => { setOpen(o => !o); setError(null) }}
        disabled={loading}
        aria-haspopup="menu"
        aria-expanded={open}>
        <Download size={14} />
        {loading ? 'Exporting…' : 'Export'}
        <ChevronDown size={13} />
      </button>

      {/* Dropdown */}
      {open && (
        <div style={{
          position: 'absolute', top: 'calc(100% + 6px)', right: 0,
          background: 'white', border: '1px solid #E2E8F0',
          borderRadius: 10, boxShadow: '0 4px 16px rgba(0,0,0,.1)',
          minWidth: 220, zIndex: 200, overflow: 'hidden',
        }} role="menu">
          <DropItem
            label="Active customers (CSV)"
            sub="All non-deleted customers"
            onClick={() => download('/api/v1/crm/customers/export/csv', `customers-${today}.csv`)}
          />
          <DropItem
            label="All customers incl. deleted"
            sub="For POPIA / full data export"
            onClick={() => download('/api/v1/crm/customers/export/csv/all', `customers-full-${today}.csv`)}
          />
        </div>
      )}

      {/* Inline error banner — shown below the button, auto-dismisses */}
      {error && (
        <div role="alert" style={{
          position: 'absolute', top: 'calc(100% + 6px)', right: 0,
          display: 'flex', alignItems: 'flex-start', gap: 8,
          background: '#FEF2F2', border: '1px solid #FECACA',
          borderRadius: 8, padding: '10px 12px',
          fontSize: 12, color: '#DC2626',
          width: 260, zIndex: 200,
          boxShadow: '0 4px 12px rgba(0,0,0,.08)',
        }}>
          <AlertCircle size={13} style={{ flexShrink: 0, marginTop: 1 }} />
          <span style={{ flex: 1, lineHeight: 1.4 }}>{error}</span>
          <button
            onClick={() => setError(null)}
            style={{ background: 'none', border: 'none', cursor: 'pointer',
                     color: '#DC2626', padding: 0, display: 'flex', flexShrink: 0 }}
            aria-label="Dismiss error">
            <X size={13} />
          </button>
        </div>
      )}
    </div>
  )
}

function DropItem({ label, sub, onClick }: { label: string; sub: string; onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      role="menuitem"
      style={{
        display: 'flex', alignItems: 'flex-start', gap: 10, width: '100%',
        padding: '10px 14px', background: 'none', border: 'none', cursor: 'pointer',
        textAlign: 'left', borderBottom: '1px solid #F1F5F9',
      }}
      onMouseEnter={e => (e.currentTarget.style.background = '#F8FAFC')}
      onMouseLeave={e => (e.currentTarget.style.background = 'none')}>
      <Download size={13} style={{ color: '#64748B', marginTop: 2, flexShrink: 0 }} />
      <div>
        <div style={{ fontSize: 13, fontWeight: 600, color: '#0F172A' }}>{label}</div>
        <div style={{ fontSize: 11, color: '#94A3B8', marginTop: 2 }}>{sub}</div>
      </div>
    </button>
  )
}
