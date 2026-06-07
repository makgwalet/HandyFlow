// src/pages/invoices/AdminInvoicesPage.tsx
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '../../api/client'
import {
  FileText, Plus, Send, CheckCircle, X, Download,
  AlertTriangle, RefreshCw, Search, ChevronDown,
} from 'lucide-react'

const fmtR    = (n: any) => n != null ? `R ${Number(n).toLocaleString('en-ZA', { minimumFractionDigits: 2 })}` : '—'
const fmtDate = (d: any) => d ? new Date(d).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' }) : '—'

const STATUS: Record<string, { label: string; color: string; bg: string; border: string }> = {
  DRAFT: { label: 'Draft',  color: '#718096', bg: '#1A202C', border: '#2D3748' },
  SENT:  { label: 'Sent',   color: '#60A5FA', bg: '#1D4ED820', border: '#1D4ED840' },
  PAID:  { label: 'Paid',   color: '#68D391', bg: '#16653420', border: '#16653440' },
  VOID:  { label: 'Void',   color: '#FC8181', bg: '#DC262620', border: '#DC262640' },
}

const inp: React.CSSProperties = {
  width: '100%', padding: '9px 12px', border: '1.5px solid #2D3748',
  borderRadius: 8, fontSize: 13, background: '#1A202C', color: '#F7FAFC',
  outline: 'none', boxSizing: 'border-box' as const, fontFamily: 'inherit',
}
const lbl: React.CSSProperties = {
  display: 'block', fontSize: 11, fontWeight: 700, color: '#718096',
  marginBottom: 5, textTransform: 'uppercase', letterSpacing: '0.06em',
}
const btnP: React.CSSProperties = {
  display: 'inline-flex', alignItems: 'center', gap: 6,
  background: 'linear-gradient(135deg,#1B3A6B,#2563EB)', color: '#fff',
  border: 'none', borderRadius: 8, padding: '9px 16px',
  fontSize: 13, fontWeight: 600, cursor: 'pointer',
}
const btnS: React.CSSProperties = {
  display: 'inline-flex', alignItems: 'center', gap: 6,
  padding: '9px 14px', border: '1.5px solid #2D3748',
  borderRadius: 8, background: '#1A202C', fontSize: 13,
  cursor: 'pointer', color: '#A0AEC0', fontWeight: 500,
}

function Toast({ msg, ok, onDismiss }: { msg: string; ok: boolean; onDismiss: () => void }) {
  return (
    <div style={{
      position: 'fixed' as const, bottom: 24, right: 24, zIndex: 3000,
      display: 'flex', alignItems: 'center', gap: 10,
      background: ok ? '#1C3A2A' : '#3B1515',
      border: `1px solid ${ok ? '#68D39150' : '#FC818150'}`,
      borderRadius: 10, padding: '12px 18px',
      boxShadow: '0 8px 24px rgba(0,0,0,0.4)',
      fontSize: 13, fontWeight: 600, color: ok ? '#68D391' : '#FC8181',
    }}>
      {ok ? <CheckCircle size={15} /> : <AlertTriangle size={15} />}
      {msg}
      <button onClick={onDismiss} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'inherit', padding: 0, display: 'flex', marginLeft: 4 }}>
        <X size={13} />
      </button>
    </div>
  )
}

function GenerateModal({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const [slug,  setSlug]  = useState('')
  const [year,  setYear]  = useState(new Date().getFullYear())
  const [month, setMonth] = useState(new Date().getMonth() + 1)
  const [error, setError] = useState('')

  const generate = useMutation({
    mutationFn: () => adminApi.post(`/tenants/${slug}/invoices?year=${year}&month=${month}`),
    onSuccess: () => { onDone(); onClose() },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to generate invoice'),
  })

  const months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec']

  return (
    <div style={{ position: 'fixed' as const, inset: 0, background: 'rgba(0,0,0,0.7)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, backdropFilter: 'blur(4px)' }}>
      <div style={{ background: '#13161E', border: '1px solid #2D3748', borderRadius: 16, padding: 28, width: 440, boxShadow: '0 25px 80px rgba(0,0,0,0.5)' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 22 }}>
          <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: '#F7FAFC' }}>Generate invoice</h3>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#718096', display: 'flex' }}><X size={18} /></button>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div>
            <label style={lbl}>Tenant slug *</label>
            <input autoFocus value={slug} onChange={e => setSlug(e.target.value)} placeholder="zeta-earthmoving" style={inp} />
            <div style={{ fontSize: 11, color: '#4A5568', marginTop: 4 }}>The tenant's URL slug (from the tenants list)</div>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <div>
              <label style={lbl}>Month</label>
              <select value={month} onChange={e => setMonth(Number(e.target.value))} style={{ ...inp, background: '#1A202C' }}>
                {months.map((m, i) => <option key={i+1} value={i+1}>{m}</option>)}
              </select>
            </div>
            <div>
              <label style={lbl}>Year</label>
              <select value={year} onChange={e => setYear(Number(e.target.value))} style={{ ...inp, background: '#1A202C' }}>
                {[2024,2025,2026,2027].map(y => <option key={y}>{y}</option>)}
              </select>
            </div>
          </div>
          <div style={{ padding: '10px 14px', background: '#1A202C', border: '1px solid #2D3748', borderRadius: 8, fontSize: 12, color: '#718096', lineHeight: 1.6 }}>
            The invoice will be generated as <strong style={{ color: '#F7FAFC' }}>DRAFT</strong>. Review it, then click <strong style={{ color: '#60A5FA' }}>Send</strong> to email it to the tenant.
          </div>
        </div>
        {error && <div style={{ marginTop: 12, padding: '10px 14px', background: '#3B1515', border: '1px solid #FC818150', borderRadius: 8, fontSize: 13, color: '#FC8181' }}>{error}</div>}
        <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 22 }}>
          <button onClick={onClose} style={btnS}>Cancel</button>
          <button onClick={() => generate.mutate()} disabled={!slug.trim() || generate.isPending} style={{ ...btnP, opacity: !slug.trim() ? 0.5 : 1 }}>
            {generate.isPending ? 'Generating...' : <><FileText size={13} /> Generate draft</>}
          </button>
        </div>
      </div>
    </div>
  )
}

export function AdminInvoicesPage() {
  const qc = useQueryClient()
  const [tenantFilter, setTenantFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [search,       setSearch]       = useState('')
  const [showGenerate, setShowGenerate] = useState(false)
  const [toast,        setToast]        = useState<{ msg: string; ok: boolean } | null>(null)

  const showToast = (msg: string, ok = true) => {
    setToast({ msg, ok }); setTimeout(() => setToast(null), 4000)
  }

  const { data: invoices = [], isLoading, refetch } = useQuery<any[]>({
    queryKey: ['admin-invoices', statusFilter],
    queryFn: async () => {
      const params = new URLSearchParams({ size: '100' })
      if (statusFilter) params.set('status', statusFilter)
      const r = await adminApi.get(`/invoices?${params}`)
      return r.data?.data ?? r.data ?? []
    },
  })

  const doAction = useMutation({
    mutationFn: ({ id, action }: { id: string; action: string }) =>
      adminApi.post(`/invoices/${id}/${action}`),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: ['admin-invoices'] })
      const labels: Record<string, string> = { send: 'Invoice sent', 'mark-paid': 'Invoice marked as paid', void: 'Invoice voided' }
      showToast(labels[vars.action] ?? 'Done')
    },
    onError: (e: any) => showToast(e.response?.data?.message || 'Action failed', false),
  })

  const downloadPdf = async (id: string, num: string) => {
    try {
      const r = await adminApi.get(`/invoices/${id}/pdf`, { responseType: 'blob' })
      const url = URL.createObjectURL(r.data)
      const a = document.createElement('a'); a.href = url; a.download = `${num}.pdf`; a.click()
      URL.revokeObjectURL(url)
    } catch { showToast('PDF download failed', false) }
  }

  const filtered = (invoices as any[]).filter(inv => {
    if (search && !inv.invoice_number?.toLowerCase().includes(search.toLowerCase()) &&
        !inv.tenant_name?.toLowerCase().includes(search.toLowerCase())) return false
    if (tenantFilter && inv.tenant_slug !== tenantFilter) return false
    return true
  })

  const totals = {
    draft: (invoices as any[]).filter(i => i.status === 'DRAFT').length,
    sent:  (invoices as any[]).filter(i => i.status === 'SENT').length,
    paid:  (invoices as any[]).filter(i => i.status === 'PAID').length,
    revenue: (invoices as any[]).filter(i => i.status === 'PAID').reduce((s, i) => s + (Number(i.total) || 0), 0),
  }

  return (
    <div style={{ color: '#F7FAFC' }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 24 }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
            <div style={{ width: 36, height: 36, borderRadius: 10, background: '#1A202C', border: '1px solid #2D3748', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <FileText size={16} color="#0D9488" />
            </div>
            <h1 style={{ fontSize: 22, fontWeight: 800, margin: 0 }}>Tenant Invoices</h1>
          </div>
          <p style={{ fontSize: 13, color: '#4A5568', margin: 0, paddingLeft: 46 }}>
            Generate, send and track HandyFlow billing to tenant companies
          </p>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button onClick={() => refetch()} style={btnS}><RefreshCw size={13} /></button>
          <button onClick={() => setShowGenerate(true)} style={btnP}><Plus size={14} /> Generate invoice</button>
        </div>
      </div>

      {/* KPI strip */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12, marginBottom: 22 }}>
        {[
          { label: 'Draft',    value: totals.draft,   color: '#718096', bg: '#1A202C',      border: '#2D3748' },
          { label: 'Sent',     value: totals.sent,    color: '#60A5FA', bg: '#1D4ED820',   border: '#1D4ED840' },
          { label: 'Paid',     value: totals.paid,    color: '#68D391', bg: '#16653420',   border: '#16653440' },
          { label: 'Revenue collected', value: fmtR(totals.revenue), color: '#0D9488', bg: '#0D948820', border: '#0D948840' },
        ].map(k => (
          <div key={k.label} style={{ background: k.bg, border: `1px solid ${k.border}`, borderRadius: 12, padding: '14px 18px' }}>
            <div style={{ fontSize: 22, fontWeight: 800, color: k.color }}>{k.value}</div>
            <div style={{ fontSize: 11, color: '#4A5568', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.06em', marginTop: 3 }}>{k.label}</div>
          </div>
        ))}
      </div>

      {/* Filters */}
      <div style={{ display: 'flex', gap: 8, marginBottom: 16, flexWrap: 'wrap', alignItems: 'center' }}>
        <div style={{ position: 'relative' as const }}>
          <Search size={13} style={{ position: 'absolute' as const, left: 9, top: '50%', transform: 'translateY(-50%)', color: '#4A5568' }} />
          <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search invoices..."
            style={{ ...inp, width: 220, paddingLeft: 28 }} />
        </div>
        <select value={statusFilter} onChange={e => setStatusFilter(e.target.value)}
          style={{ ...inp, width: 'auto', background: '#1A202C' }}>
          <option value="">All statuses</option>
          {Object.entries(STATUS).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
        </select>
        {(search || statusFilter) && (
          <button onClick={() => { setSearch(''); setStatusFilter('') }} style={{ ...btnS, padding: '7px 12px', fontSize: 12 }}>
            <X size={11} /> Clear
          </button>
        )}
        <div style={{ marginLeft: 'auto', fontSize: 12, color: '#4A5568' }}>{filtered.length} invoices</div>
      </div>

      {/* Table */}
      <div style={{ background: '#13161E', border: '1px solid #1E2532', borderRadius: 12, overflow: 'hidden' }}>
        {isLoading ? (
          <div style={{ textAlign: 'center', padding: 60, color: '#4A5568' }}>
            <RefreshCw size={28} style={{ marginBottom: 10, animation: 'spin 1s linear infinite' }} />
            <div>Loading invoices...</div>
            <style>{`@keyframes spin { to { transform: rotate(360deg) } }`}</style>
          </div>
        ) : filtered.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '60px 20px', color: '#4A5568' }}>
            <FileText size={40} style={{ marginBottom: 12, opacity: 0.2 }} />
            <div style={{ fontSize: 15, fontWeight: 600, color: '#718096', marginBottom: 6 }}>No invoices yet</div>
            <div style={{ fontSize: 13, marginBottom: 20 }}>Generate the first invoice for a tenant to get started.</div>
            <button onClick={() => setShowGenerate(true)} style={btnP}><Plus size={14} /> Generate first invoice</button>
          </div>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse' as const, fontSize: 13 }}>
            <thead>
              <tr style={{ borderBottom: '1px solid #1E2532' }}>
                {['Invoice #','Tenant','Period','Subtotal','VAT','Total','Status','Due','Actions'].map(h => (
                  <th key={h} style={{ padding: '10px 16px', textAlign: 'left' as const, fontSize: 11, fontWeight: 700, color: '#4A5568', letterSpacing: '0.05em', whiteSpace: 'nowrap' as const }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {filtered.map((inv: any, i: number) => {
                const sc = STATUS[inv.status] ?? STATUS.DRAFT
                return (
                  <tr key={inv.id} style={{ borderBottom: '1px solid #1E2532' }}
                    onMouseEnter={e => (e.currentTarget as HTMLElement).style.background = '#1A202C'}
                    onMouseLeave={e => (e.currentTarget as HTMLElement).style.background = 'transparent'}>
                    <td style={{ padding: '12px 16px', fontWeight: 700, color: '#F7FAFC', fontFamily: 'monospace' }}>{inv.invoice_number}</td>
                    <td style={{ padding: '12px 16px' }}>
                      <div style={{ fontWeight: 600, color: '#F7FAFC' }}>{inv.tenant_name}</div>
                      <div style={{ fontSize: 11, color: '#4A5568' }}>{inv.tenant_slug}</div>
                    </td>
                    <td style={{ padding: '12px 16px', color: '#A0AEC0' }}>{inv.period_label}</td>
                    <td style={{ padding: '12px 16px', color: '#A0AEC0' }}>{fmtR(inv.subtotal)}</td>
                    <td style={{ padding: '12px 16px', color: '#A0AEC0' }}>{fmtR(inv.vat_amount)}</td>
                    <td style={{ padding: '12px 16px', fontWeight: 700, color: '#F7FAFC' }}>{fmtR(inv.total)}</td>
                    <td style={{ padding: '12px 16px' }}>
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: sc.bg, color: sc.color, border: `1px solid ${sc.border}`, padding: '2px 9px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                        {sc.label}
                      </span>
                    </td>
                    <td style={{ padding: '12px 16px', color: inv.status === 'PAID' ? '#68D391' : '#A0AEC0', fontSize: 12 }}>{fmtDate(inv.due_date)}</td>
                    <td style={{ padding: '12px 16px' }}>
                      <div style={{ display: 'flex', gap: 5 }}>
                        {/* Download PDF */}
                        <button onClick={() => downloadPdf(inv.id, inv.invoice_number)}
                          title="Download PDF"
                          style={{ padding: '5px 8px', background: '#1A202C', border: '1px solid #2D3748', borderRadius: 6, cursor: 'pointer', color: '#718096', display: 'flex' }}>
                          <Download size={12} />
                        </button>
                        {/* Send */}
                        {inv.status === 'DRAFT' && (
                          <button onClick={() => doAction.mutate({ id: inv.id, action: 'send' })}
                            title="Send to tenant"
                            style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '5px 10px', background: '#1D4ED820', border: '1px solid #1D4ED840', borderRadius: 6, cursor: 'pointer', color: '#60A5FA', fontSize: 11, fontWeight: 700 }}>
                            <Send size={11} /> Send
                          </button>
                        )}
                        {/* Mark paid */}
                        {inv.status === 'SENT' && (
                          <button onClick={() => doAction.mutate({ id: inv.id, action: 'mark-paid' })}
                            title="Mark as paid"
                            style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '5px 10px', background: '#16653420', border: '1px solid #16653440', borderRadius: 6, cursor: 'pointer', color: '#68D391', fontSize: 11, fontWeight: 700 }}>
                            <CheckCircle size={11} /> Paid
                          </button>
                        )}
                        {/* Void */}
                        {['DRAFT','SENT'].includes(inv.status) && (
                          <button onClick={() => { if (confirm(`Void ${inv.invoice_number}? This cannot be undone.`)) doAction.mutate({ id: inv.id, action: 'void' }) }}
                            title="Void invoice"
                            style={{ padding: '5px 8px', background: '#DC262620', border: '1px solid #DC262640', borderRadius: 6, cursor: 'pointer', color: '#FC8181', display: 'flex' }}>
                            <X size={12} />
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </div>

      {showGenerate && (
        <GenerateModal onClose={() => setShowGenerate(false)} onDone={() => { qc.invalidateQueries({ queryKey: ['admin-invoices'] }); showToast('Invoice generated — review and send') }} />
      )}

      {toast && <Toast msg={toast.msg} ok={toast.ok} onDismiss={() => setToast(null)} />}
    </div>
  )
}
