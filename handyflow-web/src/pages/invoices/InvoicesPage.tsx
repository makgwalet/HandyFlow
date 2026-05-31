import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { FileText, Download, ChevronDown, ChevronUp, CheckCircle, Send, X } from 'lucide-react'

interface InvoiceLine {
  description: string
  quantity: number
  unitPrice: number
  lineTotal: number
  vatRate?: number
}

interface Invoice {
  id: string
  invoiceNumber: string
  customerId: string
  status: 'DRAFT' | 'ISSUED' | 'PAID' | 'OVERDUE' | 'CANCELLED'
  issuedAt: string | null
  dueDate: string | null
  subtotal: number
  vatTotal: number
  total: number
  lineItems: InvoiceLine[]
  createdAt: string
}

const STATUS_STYLES: Record<string, { bg: string; color: string; label: string }> = {
  DRAFT:     { bg: '#F1F5F9', color: '#475569', label: 'Draft'     },
  ISSUED:    { bg: '#EFF6FF', color: '#1D4ED8', label: 'Issued'    },
  SENT:      { bg: '#EFF6FF', color: '#1D4ED8', label: 'Sent'      },
  PAID:      { bg: '#F0FDF4', color: '#166534', label: 'Paid'      },
  OVERDUE:   { bg: '#FEF2F2', color: '#DC2626', label: 'Overdue'   },
  CANCELLED: { bg: '#F8FAFC', color: '#94A3B8', label: 'Cancelled' },
}

function fmtR(n: number | null | undefined) {
  if (n == null) return '—'
  return `R ${n.toLocaleString('en-ZA', { minimumFractionDigits: 2 })}`
}
function fmtDate(d: string | null) {
  if (!d) return '—'
  return new Date(d).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' })
}

export function InvoicesPage() {
  const qc = useQueryClient()
  const [expanded, setExpanded]       = useState<string | null>(null)
  const [downloading, setDownloading] = useState<string | null>(null)
  const [payModal, setPayModal]       = useState<Invoice | null>(null)
  const [statusFilter, setStatusFilter] = useState<string>('ALL')
  const [payForm, setPayForm] = useState({ amount: '', paymentMethod: 'EFT', reference: '', note: '' })

  const { data: invoices = [], isLoading } = useQuery<Invoice[]>({
    queryKey: ['invoices'],
    queryFn: async () => {
      const res = await apiClient.get('/api/v1/invoicing/invoices?size=100&sort=createdAt,desc')
      return res.data.content ?? res.data.data?.content ?? []
    },
  })

  const { data: customers = [] } = useQuery({
    queryKey: ['customers-map'],
    queryFn: async () => {
      const res = await apiClient.get('/api/v1/crm/customers?size=200')
      return res.data.content as { id: string; name: string }[]
    },
  })
  const customerMap = Object.fromEntries(customers.map(c => [c.id, c.name]))

  const issueInvoice = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/invoicing/invoices/${id}/issue`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['invoices'] }),
  })

  const markPaid = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) =>
      apiClient.post(`/api/v1/invoicing/invoices/${id}/payments`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['invoices'] })
      setPayModal(null)
      setPayForm({ amount: '', paymentMethod: 'EFT', reference: '', note: '' })
    },
  })

  const downloadPdf = async (invoice: Invoice) => {
    setDownloading(invoice.id)
    try {
      const res = await apiClient.get(`/api/v1/invoicing/invoices/${invoice.id}/pdf`, { responseType: 'blob' } as any)
      const url = URL.createObjectURL(new Blob([res.data], { type: 'application/pdf' }))
      const a = document.createElement('a')
      a.href = url; a.download = `${invoice.invoiceNumber}.pdf`; a.click()
      URL.revokeObjectURL(url)
    } catch { alert('Failed to download PDF') }
    finally { setDownloading(null) }
  }

  const filtered = statusFilter === 'ALL' ? invoices : invoices.filter(i => i.status === statusFilter)

  const totalRevenue     = invoices.filter(i => i.status === 'PAID').reduce((s, i) => s + i.total, 0)
  const totalOutstanding = invoices.filter(i => ['ISSUED','SENT','OVERDUE'].includes(i.status)).reduce((s, i) => s + i.total, 0)
  const overdueCount     = invoices.filter(i => i.status === 'OVERDUE').length

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>

      {/* Header */}
      <div style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: 24, fontWeight: 800, color: '#0F172A', margin: '0 0 4px' }}>Invoices</h1>
        <p style={{ fontSize: 13, color: '#94A3B8', margin: 0 }}>Tax invoices generated from accepted quotes</p>
      </div>

      {/* Stats */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 14, marginBottom: 24 }}>
        {[
          { label: 'Total invoices',  value: invoices.length,  fmt: false, color: '#1B3A6B' },
          { label: 'Paid',            value: invoices.filter(i => i.status === 'PAID').length, fmt: false, color: '#166534' },
          { label: 'Total revenue',   value: fmtR(totalRevenue),    fmt: true, color: '#0D9488' },
          { label: 'Outstanding',     value: fmtR(totalOutstanding), fmt: true, color: overdueCount > 0 ? '#DC2626' : '#D97706' },
        ].map(s => (
          <div key={s.label} style={{ background: 'white', border: '1px solid #E2E8F0', borderRadius: 12, padding: '16px 20px' }}>
            <div style={{ fontSize: s.fmt ? 18 : 26, fontWeight: 700, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 12, color: '#64748B', marginTop: 3 }}>{s.label}</div>
          </div>
        ))}
      </div>

      {/* Status filter */}
      <div style={{ display: 'flex', gap: 6, marginBottom: 16 }}>
        {['ALL', 'DRAFT', 'ISSUED', 'PAID', 'OVERDUE', 'CANCELLED'].map(s => (
          <button key={s} onClick={() => setStatusFilter(s)}
            style={{ padding: '6px 14px', borderRadius: 20, fontSize: 12, fontWeight: 600, cursor: 'pointer', border: 'none',
              background: statusFilter === s ? '#1B3A6B' : '#F1F5F9',
              color:      statusFilter === s ? 'white'   : '#64748B',
            }}>
            {s === 'ALL' ? 'All' : (STATUS_STYLES[s]?.label ?? s)}
            {s !== 'ALL' && ` (${invoices.filter(i => i.status === s).length})`}
          </button>
        ))}
      </div>

      {/* Table */}
      <div style={{ background: 'white', border: '1px solid #E2E8F0', borderRadius: 14, overflow: 'hidden' }}>
        {isLoading ? (
          <div style={{ padding: 60, textAlign: 'center', color: '#94A3B8' }}>Loading invoices...</div>
        ) : filtered.length === 0 ? (
          <div style={{ padding: 60, textAlign: 'center', color: '#94A3B8' }}>
            <FileText size={36} style={{ marginBottom: 12, opacity: 0.3 }} />
            <div style={{ fontWeight: 600, color: '#475569', marginBottom: 4 }}>No invoices</div>
            <div style={{ fontSize: 13 }}>Convert an accepted quote to generate an invoice.</div>
          </div>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid #F1F5F9', background: '#F8FAFC' }}>
                {['Invoice #', 'Customer', 'Issued', 'Due', 'Total', 'Status', 'Actions'].map(h => (
                  <th key={h} style={{ textAlign: 'left', padding: '11px 16px', fontSize: 11, fontWeight: 700, color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.05em' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {filtered.map(inv => {
                const ss = STATUS_STYLES[inv.status] ?? STATUS_STYLES.DRAFT
                const isExpanded = expanded === inv.id
                return (
                  <>
                    <tr key={inv.id}
                      onClick={() => setExpanded(isExpanded ? null : inv.id)}
                      style={{ borderBottom: '1px solid #F8FAFC', cursor: 'pointer', background: isExpanded ? '#FAFBFF' : 'white' }}
                      onMouseEnter={e => { if (!isExpanded) (e.currentTarget as HTMLElement).style.background = '#F8FAFC' }}
                      onMouseLeave={e => { if (!isExpanded) (e.currentTarget as HTMLElement).style.background = 'white' }}
                    >
                      <td style={{ padding: '14px 16px' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                          <FileText size={14} color="#0D9488" />
                          <span style={{ fontWeight: 700, fontSize: 14, color: '#0F172A' }}>{inv.invoiceNumber}</span>
                          {isExpanded ? <ChevronUp size={13} color="#94A3B8" /> : <ChevronDown size={13} color="#94A3B8" />}
                        </div>
                      </td>
                      <td style={{ padding: '14px 16px', fontSize: 13, fontWeight: 600, color: '#374151' }}>
                        {customerMap[inv.customerId] ?? inv.customerId.slice(0, 8) + '…'}
                      </td>
                      <td style={{ padding: '14px 16px', fontSize: 13, color: '#64748B' }}>{fmtDate(inv.issuedAt)}</td>
                      <td style={{ padding: '14px 16px', fontSize: 13, color: inv.status === 'OVERDUE' ? '#DC2626' : '#64748B' }}>
                        {fmtDate(inv.dueDate)}
                      </td>
                      <td style={{ padding: '14px 16px', fontSize: 14, fontWeight: 700, color: '#0F172A' }}>{fmtR(inv.total)}</td>
                      <td style={{ padding: '14px 16px' }}>
                        <span style={{ background: ss.bg, color: ss.color, padding: '3px 10px', borderRadius: 20, fontSize: 12, fontWeight: 600 }}>
                          {ss.label}
                        </span>
                      </td>
                      <td style={{ padding: '14px 16px' }}>
                        <div style={{ display: 'flex', gap: 6, alignItems: 'center' }} onClick={e => e.stopPropagation()}>
                          {/* Issue — DRAFT only */}
                          {inv.status === 'DRAFT' && (
                            <button
                              disabled={issueInvoice.isPending}
                              onClick={() => issueInvoice.mutate(inv.id)}
                              style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '6px 12px', background: '#EFF6FF', color: '#1D4ED8', border: '1px solid #BFDBFE', borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: 'pointer' }}>
                              <Send size={12} /> Issue
                            </button>
                          )}
                          {/* Mark as Paid — ISSUED/SENT/OVERDUE */}
                          {['ISSUED', 'SENT', 'OVERDUE'].includes(inv.status) && (
                            <button
                              onClick={() => { setPayModal(inv); setPayForm(f => ({ ...f, amount: String(inv.total) })) }}
                              style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '6px 12px', background: '#F0FDF4', color: '#166534', border: '1px solid #BBF7D0', borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: 'pointer' }}>
                              <CheckCircle size={12} /> Mark paid
                            </button>
                          )}
                          {/* PDF */}
                          <button
                            disabled={downloading === inv.id}
                            onClick={() => downloadPdf(inv)}
                            style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '6px 12px', background: '#F8FAFC', color: '#64748B', border: '1px solid #E2E8F0', borderRadius: 7, fontSize: 12, fontWeight: 500, cursor: 'pointer' }}>
                            <Download size={12} />
                            {downloading === inv.id ? '…' : 'PDF'}
                          </button>
                        </div>
                      </td>
                    </tr>

                    {/* Expanded line items */}
                    {isExpanded && (
                      <tr key={`${inv.id}-lines`}>
                        <td colSpan={7} style={{ padding: 0 }}>
                          <div style={{ background: '#F8FAFC', borderBottom: '1px solid #F1F5F9', padding: '16px 24px' }}>
                            <div style={{ fontSize: 11, fontWeight: 700, color: '#94A3B8', letterSpacing: '0.06em', marginBottom: 10 }}>LINE ITEMS</div>
                            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
                              <thead>
                                <tr style={{ borderBottom: '1px solid #E2E8F0' }}>
                                  {['Description', 'Qty', 'Unit Price', 'VAT %', 'Total'].map(h => (
                                    <th key={h} style={{ textAlign: 'left', padding: '6px 12px', fontSize: 11, fontWeight: 600, color: '#64748B', textTransform: 'uppercase' as const }}>{h}</th>
                                  ))}
                                </tr>
                              </thead>
                              <tbody>
                                {inv.lineItems.map((li, i) => (
                                  <tr key={i} style={{ borderBottom: i < inv.lineItems.length - 1 ? '1px solid #F8FAFC' : 'none' }}>
                                    <td style={{ padding: '8px 12px', color: '#374151' }}>{li.description}</td>
                                    <td style={{ padding: '8px 12px', color: '#64748B' }}>{li.quantity}</td>
                                    <td style={{ padding: '8px 12px', color: '#64748B' }}>{fmtR(li.unitPrice)}</td>
                                    <td style={{ padding: '8px 12px', color: '#64748B' }}>{li.vatRate ?? 15}%</td>
                                    <td style={{ padding: '8px 12px', fontWeight: 600, color: '#0F172A' }}>{fmtR(li.lineTotal)}</td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 12 }}>
                              <div style={{ minWidth: 240 }}>
                                {[['Subtotal', fmtR(inv.subtotal)], ['VAT (15%)', fmtR(inv.vatTotal)]].map(([label, value]) => (
                                  <div key={label} style={{ display: 'flex', justifyContent: 'space-between', padding: '4px 0', fontSize: 13, color: '#64748B' }}>
                                    <span>{label}</span><span>{value}</span>
                                  </div>
                                ))}
                                <div style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0 0', fontSize: 15, fontWeight: 700, color: '#0F172A', borderTop: '1px solid #E2E8F0', marginTop: 4 }}>
                                  <span>Total</span><span>{fmtR(inv.total)}</span>
                                </div>
                              </div>
                            </div>
                          </div>
                        </td>
                      </tr>
                    )}
                  </>
                )
              })}
            </tbody>
          </table>
        )}
      </div>

      {/* Mark as Paid modal */}
      {payModal && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div style={{ background: 'white', borderRadius: 16, padding: 28, width: 460, boxShadow: '0 20px 60px rgba(0,0,0,0.2)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
              <div>
                <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: '#0F172A' }}>Mark as Paid</h3>
                <p style={{ margin: '3px 0 0', fontSize: 13, color: '#94A3B8' }}>{payModal.invoiceNumber} · {fmtR(payModal.total)}</p>
              </div>
              <button onClick={() => setPayModal(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8' }}><X size={20} /></button>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              <MField label="Amount Received (R) *">
                <input type="number" value={payForm.amount} onChange={e => setPayForm(f => ({ ...f, amount: e.target.value }))}
                  style={inp} placeholder={String(payModal.total)} />
              </MField>
              <MField label="Payment Method">
                <select value={payForm.paymentMethod} onChange={e => setPayForm(f => ({ ...f, paymentMethod: e.target.value }))} style={inp}>
                  {['EFT', 'CASH', 'CARD', 'CHEQUE', 'OTHER'].map(m => <option key={m}>{m}</option>)}
                </select>
              </MField>
              <MField label="Payment Reference">
                <input value={payForm.reference} onChange={e => setPayForm(f => ({ ...f, reference: e.target.value }))}
                  style={inp} placeholder="e.g. EFT-20260517-001" />
              </MField>
              <MField label="Note (optional)">
                <input value={payForm.note} onChange={e => setPayForm(f => ({ ...f, note: e.target.value }))}
                  style={inp} placeholder="e.g. Paid in full" />
              </MField>
            </div>

            <div style={{ display: 'flex', gap: 10, marginTop: 22, justifyContent: 'flex-end' }}>
              <button onClick={() => setPayModal(null)}
                style={{ padding: '10px 18px', border: '1px solid #E2E8F0', borderRadius: 9, background: 'white', fontSize: 14, cursor: 'pointer', color: '#374151' }}>
                Cancel
              </button>
              <button
                disabled={!payForm.amount || markPaid.isPending}
                onClick={() => markPaid.mutate({
                  id: payModal.id,
                  body: { amount: Number(payForm.amount), paymentMethod: payForm.paymentMethod, reference: payForm.reference, note: payForm.note },
                })}
                style={{ padding: '10px 22px', background: '#16A34A', color: 'white', border: 'none', borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: 'pointer' }}>
                {markPaid.isPending ? 'Saving...' : '✓ Confirm payment'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function MField({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 5 }}>{label}</label>
      {children}
    </div>
  )
}

const inp: React.CSSProperties = {
  width: '100%', padding: '10px 12px', border: '1.5px solid #E2E8F0',
  borderRadius: 9, fontSize: 14, boxSizing: 'border-box' as const, background: 'white',
}
