// src/pages/quotes/QuoteDetailPage.tsx
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import {
  ArrowLeft, Send, CheckCircle, XCircle, FileCheck, Clock,
  AlertCircle, Download, ExternalLink, AlertTriangle,
} from 'lucide-react'
import { apiClient } from '../../api/client'
import type { Quote } from '../../types/invoicing.types'
import type { Customer } from '../../types/crm.types'

type QuoteStatus = 'DRAFT' | 'SENT' | 'ACCEPTED' | 'REJECTED' | 'EXPIRED' | 'INVOICED'

const STATUS_CONFIG: Record<QuoteStatus, { label: string; bg: string; color: string; icon: React.ElementType }> = {
  DRAFT:    { label: 'Draft',    bg: '#F1F5F9', color: '#475569', icon: AlertCircle },
  SENT:     { label: 'Sent',     bg: '#EFF6FF', color: '#1D4ED8', icon: Send },
  ACCEPTED: { label: 'Accepted', bg: '#F0FDF4', color: '#166534', icon: CheckCircle },
  REJECTED: { label: 'Rejected', bg: '#FEF2F2', color: '#DC2626', icon: XCircle },
  EXPIRED:  { label: 'Expired',  bg: '#FEF3C7', color: '#92400E', icon: Clock },
  INVOICED: { label: 'Invoiced', bg: '#F3E8FF', color: '#7C3AED', icon: FileCheck },
}

function Btn({ label, icon: Icon, onClick, disabled, variant = 'secondary' }: {
  label: string; icon: React.ElementType; onClick: () => void
  disabled?: boolean; variant?: 'primary' | 'secondary' | 'success' | 'danger' | 'outline'
}) {
  const styles = {
    primary:   { bg: '#1B3A6B', color: 'white',  border: 'none' },
    secondary: { bg: 'white',   color: '#374151', border: '1px solid #E2E8F0' },
    success:   { bg: '#16A34A', color: 'white',   border: 'none' },
    danger:    { bg: '#DC2626', color: 'white',   border: 'none' },
    outline:   { bg: 'white',   color: '#1B3A6B', border: '1px solid #1B3A6B' },
  }
  const s = styles[variant]
  return (
    <button onClick={onClick} disabled={disabled} style={{
      display: 'flex', alignItems: 'center', gap: 7, padding: '9px 16px',
      background: disabled ? '#F1F5F9' : s.bg,
      border: disabled ? '1px solid #E2E8F0' : s.border,
      borderRadius: 9, fontSize: 13, fontWeight: 600,
      color: disabled ? '#94A3B8' : s.color,
      cursor: disabled ? 'not-allowed' : 'pointer',
    }}>
      <Icon size={15} />{label}
    </button>
  )
}

// ── Reject confirmation modal ─────────────────────────────────────────────────
function RejectModal({
  quoteNumber, isPending, error, onConfirm, onCancel,
}: {
  quoteNumber: string; isPending: boolean; error: string
  onConfirm: () => void; onCancel: () => void
}) {
  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, backdropFilter: 'blur(2px)' }}>
      <div style={{ background: 'white', borderRadius: 18, padding: 32, width: 420, boxShadow: '0 24px 64px rgba(0,0,0,0.18)', display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center' }}>

        <div style={{ width: 56, height: 56, borderRadius: '50%', background: '#FFF7ED', border: '2px solid #FED7AA', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 16 }}>
          <AlertTriangle size={24} color="#EA580C" strokeWidth={2} />
        </div>

        <h3 style={{ margin: '0 0 6px', fontSize: 18, fontWeight: 700, color: '#0F172A' }}>Reject Quote?</h3>

        <div style={{ display: 'flex', alignItems: 'center', gap: 8, background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 40, padding: '7px 16px', margin: '10px 0' }}>
          <XCircle size={14} color="#DC2626" />
          <span style={{ fontSize: 14, fontWeight: 600, color: '#0F172A' }}>{quoteNumber}</span>
        </div>

        <p style={{ margin: '0 0 16px', fontSize: 13, color: '#64748B', lineHeight: 1.6 }}>
          This will mark the quote as <strong>Rejected</strong>.<br />
          The client will need a new quote to proceed.
        </p>

        {error && (
          <div style={{ width: '100%', marginBottom: 16, padding: '10px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626', display: 'flex', alignItems: 'center', gap: 8 }}>
            <AlertCircle size={15} color="#DC2626" style={{ flexShrink: 0 }} />{error}
          </div>
        )}

        <div style={{ display: 'flex', gap: 10, width: '100%' }}>
          <button onClick={onCancel} disabled={isPending}
            style={{ flex: 1, padding: '11px', border: '1.5px solid #E2E8F0', borderRadius: 10, background: 'white', fontSize: 14, fontWeight: 600, cursor: 'pointer', color: '#374151' }}>
            Cancel
          </button>
          <button onClick={onConfirm} disabled={isPending}
            style={{ flex: 1, padding: '11px', border: 'none', borderRadius: 10, background: isPending ? '#93A8C9' : '#DC2626', color: 'white', fontSize: 14, fontWeight: 700, cursor: isPending ? 'not-allowed' : 'pointer' }}>
            {isPending ? 'Rejecting...' : 'Yes, Reject'}
          </button>
        </div>
      </div>
    </div>
  )
}

export function QuoteDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const qc = useQueryClient()

  const [showRejectModal, setShowRejectModal] = useState(false)
  const [rejectError, setRejectError]         = useState('')

  const { data: quote, isLoading } = useQuery<Quote>({
    queryKey: ['quote', id],
    queryFn: async () => {
      const res = await apiClient.get(`/api/v1/invoicing/quotes/${id}`)
      return (res.data?.data ?? res.data) as Quote
    },
    enabled: !!id,
  })

  const { data: customer } = useQuery<Customer>({
    queryKey: ['customer', quote?.customerId],
    queryFn: async () => {
      const res = await apiClient.get(`/api/v1/crm/customers/${quote!.customerId}`)
      return (res.data?.data ?? res.data) as Customer
    },
    enabled: !!quote?.customerId,   // ← only fetch if not a walk-in
  })

  const invalidate = () => qc.invalidateQueries({ queryKey: ['quote', id] })

  const sendQuote = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/invoicing/quotes/${id}/send`),
    onSuccess: invalidate,
  })

  const acceptQuote = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/invoicing/quotes/${id}/accept`),
    onSuccess: invalidate,
  })

  const rejectQuote = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/invoicing/quotes/${id}/reject`),
    onSuccess: () => { invalidate(); setShowRejectModal(false); setRejectError('') },
    onError: (e: any) => {
      setRejectError(e.response?.data?.message ?? 'Failed to reject quote. Please try again.')
    },
  })

  const convertQuote = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/invoicing/quotes/${id}/convert-to-invoice`),
    onSuccess: () => { invalidate(); qc.invalidateQueries({ queryKey: ['quotes'] }) },
  })

  const downloadPdf = () => {
    apiClient.get(`/api/v1/invoicing/quotes/${id}/pdf`, { responseType: 'blob' } as any)
      .then((res: any) => {
        const url = URL.createObjectURL(new Blob([res.data], { type: 'application/pdf' }))
        const a = document.createElement('a')
        a.href = url; a.download = `${quote?.quoteNumber ?? 'quote'}.pdf`; a.click()
        URL.revokeObjectURL(url)
      })
      .catch(() => alert('Failed to download PDF'))
  }

  if (isLoading) return <div style={{ padding: 80, textAlign: 'center', color: '#94A3B8' }}>Loading quote...</div>
  if (!quote)   return <div style={{ padding: 80, textAlign: 'center', color: '#94A3B8' }}>Quote not found</div>

  const status = quote.status as QuoteStatus
  const sc = STATUS_CONFIG[status] ?? STATUS_CONFIG.DRAFT
  const StatusIcon = sc.icon

  // Support both saved customers and walk-in clients
  const isWalkin       = !quote.customerId
  const walkinName     = (quote as any).walkinClientName
  const walkinEmail    = (quote as any).walkinClientEmail
  const walkinPhone    = (quote as any).walkinClientPhone
  const displayName    = isWalkin ? walkinName : customer?.name
  const displayEmail   = isWalkin ? walkinEmail : customer?.email
  const displayPhone   = isWalkin ? walkinPhone : customer?.phone
  const displayVat     = isWalkin ? null : customer?.taxNumber

  const addr = customer?.address
  const addressLine = addr
    ? [addr.street, addr.suburb, addr.city, addr.province, addr.postalCode].filter(Boolean).join(', ')
    : null

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif", maxWidth: 860, margin: '0 auto' }}>

      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: 28 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <button onClick={() => navigate('/quotes')}
            style={{ display: 'flex', alignItems: 'center', gap: 6, background: 'white', border: '1px solid #E2E8F0', borderRadius: 9, padding: '7px 12px', fontSize: 13, fontWeight: 600, color: '#374151', cursor: 'pointer' }}>
            <ArrowLeft size={15} /> Back
          </button>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <h1 style={{ fontSize: 22, fontWeight: 800, color: '#0F172A', margin: 0 }}>{quote.quoteNumber}</h1>
              <span style={{ display: 'flex', alignItems: 'center', gap: 5, background: sc.bg, color: sc.color, fontSize: 12, fontWeight: 700, padding: '4px 10px', borderRadius: 20 }}>
                <StatusIcon size={12} />{sc.label}
              </span>
              {isWalkin && (
                <span style={{ fontSize: 11, fontWeight: 600, background: '#FEF3C7', color: '#92400E', padding: '3px 8px', borderRadius: 20 }}>
                  Walk-in
                </span>
              )}
            </div>
            <p style={{ fontSize: 13, color: '#94A3B8', margin: '4px 0 0' }}>{quote.title}</p>
          </div>
        </div>

        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
          <Btn label="Download PDF" icon={Download} variant="outline" onClick={downloadPdf} />
          {status === 'DRAFT' && (
            <Btn label={sendQuote.isPending ? 'Sending...' : 'Send quote'} icon={Send} variant="primary"
              onClick={() => sendQuote.mutate()}
              disabled={sendQuote.isPending || quote.lineItems.length === 0} />
          )}
          {status === 'SENT' && (<>
            <Btn label="Accept" icon={CheckCircle} variant="success"
              onClick={() => acceptQuote.mutate()} disabled={acceptQuote.isPending} />
            <Btn label="Reject" icon={XCircle} variant="danger"
              onClick={() => { setShowRejectModal(true); setRejectError('') }}
              disabled={rejectQuote.isPending} />
          </>)}
          {status === 'ACCEPTED' && (
            <Btn label={convertQuote.isPending ? 'Converting...' : 'Convert to invoice'} icon={FileCheck} variant="primary"
              onClick={() => convertQuote.mutate()} disabled={convertQuote.isPending} />
          )}
          {status === 'INVOICED' && (
            <Btn label="View invoice" icon={ExternalLink} variant="outline"
              onClick={() => navigate('/invoices')} />
          )}
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 280px', gap: 16 }}>

        {/* Main card */}
        <div style={{ background: 'white', border: '1px solid #E8EDF5', borderRadius: 16, overflow: 'hidden', boxShadow: '0 1px 4px rgba(0,0,0,0.04)' }}>

          {/* Bill To + Quote meta */}
          <div style={{ padding: '20px 24px', borderBottom: '1px solid #F1F5F9', display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 24 }}>
            <div>
              <p style={{ fontSize: 11, fontWeight: 700, color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.05em', margin: '0 0 8px' }}>Bill To</p>
              {displayName ? (
                <>
                  <p style={{ fontSize: 15, fontWeight: 700, color: '#0F172A', margin: '0 0 4px' }}>{displayName}</p>
                  {isWalkin && (
                    <span style={{ fontSize: 11, background: '#FEF3C7', color: '#92400E', padding: '2px 7px', borderRadius: 10, fontWeight: 600 }}>Walk-in client</span>
                  )}
                  {displayEmail && <p style={{ fontSize: 13, color: '#64748B', margin: '4px 0 2px' }}>{displayEmail}</p>}
                  {displayPhone && <p style={{ fontSize: 13, color: '#64748B', margin: '0 0 2px' }}>{displayPhone}</p>}
                  {addressLine  && <p style={{ fontSize: 12, color: '#94A3B8', margin: '4px 0 0', lineHeight: 1.5 }}>{addressLine}</p>}
                  {displayVat   && <p style={{ fontSize: 12, color: '#94A3B8', margin: '4px 0 0' }}>VAT: {displayVat}</p>}
                </>
              ) : (
                <p style={{ fontSize: 13, color: '#94A3B8', margin: 0 }}>
                  {isWalkin ? 'No client details provided' : 'Loading customer details...'}
                </p>
              )}
            </div>

            <div>
              <p style={{ fontSize: 11, fontWeight: 700, color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.05em', margin: '0 0 8px' }}>Quote Details</p>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
                {[
                  ['Quote #',  quote.quoteNumber],
                  ['Created',  new Date(quote.createdAt).toLocaleDateString('en-ZA')],
                  ['Sent',     quote.sentAt    ? new Date(quote.sentAt).toLocaleDateString('en-ZA')    : '—'],
                  ['Expires',  quote.expiresAt ? new Date(quote.expiresAt).toLocaleDateString('en-ZA') : '—'],
                ].map(([label, value]) => (
                  <div key={label} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13 }}>
                    <span style={{ color: '#64748B' }}>{label}</span>
                    <span style={{ fontWeight: 600, color: '#0F172A' }}>{value}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* Line items */}
          <div>
            <div style={{ display: 'grid', gridTemplateColumns: '3fr 80px 120px 80px 120px', padding: '12px 24px', background: '#F8FAFC', borderBottom: '1px solid #F1F5F9' }}>
              {['Description', 'Unit', 'Unit price', 'Qty', 'Line total'].map(h => (
                <p key={h} style={{ fontSize: 11, fontWeight: 700, color: '#94A3B8', margin: 0, textTransform: 'uppercase', letterSpacing: '0.04em' }}>{h}</p>
              ))}
            </div>

            {quote.lineItems.length === 0 ? (
              <div style={{ padding: '40px 24px', textAlign: 'center' }}>
                <p style={{ color: '#94A3B8', fontSize: 13, margin: 0 }}>No line items on this quote yet</p>
              </div>
            ) : quote.lineItems.map((li, i) => (
              <div key={li.id} style={{ display: 'grid', gridTemplateColumns: '3fr 80px 120px 80px 120px', padding: '14px 24px', borderTop: i === 0 ? 'none' : '1px solid #F8FAFC', alignItems: 'center' }}>
                <div>
                  <p style={{ fontSize: 14, fontWeight: 600, color: '#0F172A', margin: 0 }}>{li.description}</p>
                  <p style={{ fontSize: 11, color: '#94A3B8', margin: '2px 0 0' }}>VAT {li.vatRate}%</p>
                </div>
                <p style={{ fontSize: 13, color: '#64748B', margin: 0 }}>{li.unit}</p>
                <p style={{ fontSize: 13, color: '#0F172A', margin: 0 }}>R {Number(li.unitPrice).toLocaleString('en-ZA', { minimumFractionDigits: 2 })}</p>
                <p style={{ fontSize: 13, color: '#0F172A', margin: 0 }}>{li.quantity}</p>
                <p style={{ fontSize: 14, fontWeight: 700, color: '#1B3A6B', margin: 0 }}>
                  R {Number(li.lineTotal).toLocaleString('en-ZA', { minimumFractionDigits: 2 })}
                </p>
              </div>
            ))}
          </div>

          {/* Totals */}
          <div style={{ padding: '20px 24px', borderTop: '1px solid #F1F5F9', display: 'flex', justifyContent: 'flex-end' }}>
            <div style={{ width: 260 }}>
              {[['Subtotal', quote.subtotal], ['VAT', quote.vatTotal]].map(([label, value]) => (
                <div key={label as string} style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
                  <span style={{ fontSize: 13, color: '#64748B' }}>{label as string}</span>
                  <span style={{ fontSize: 13, fontWeight: 600, color: '#0F172A' }}>R {Number(value).toLocaleString('en-ZA', { minimumFractionDigits: 2 })}</span>
                </div>
              ))}
              <div style={{ height: 1, background: '#E2E8F0', margin: '10px 0' }} />
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ fontSize: 15, fontWeight: 700, color: '#0F172A' }}>Total</span>
                <span style={{ fontSize: 20, fontWeight: 800, color: '#1B3A6B' }}>
                  R {Number(quote.total).toLocaleString('en-ZA', { minimumFractionDigits: 2 })}
                </span>
              </div>
            </div>
          </div>

          {quote.notes && (
            <div style={{ padding: '16px 24px', borderTop: '1px solid #F1F5F9', background: '#FAFBFF' }}>
              <p style={{ fontSize: 11, fontWeight: 700, color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.05em', margin: '0 0 6px' }}>Notes</p>
              <p style={{ fontSize: 13, color: '#475569', margin: 0, lineHeight: 1.6 }}>{quote.notes}</p>
            </div>
          )}
        </div>

        {/* Sidebar */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>

          {/* Timeline */}
          <div style={{ background: 'white', border: '1px solid #E8EDF5', borderRadius: 14, padding: 20, boxShadow: '0 1px 4px rgba(0,0,0,0.04)' }}>
            <p style={{ fontSize: 12, fontWeight: 700, color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.05em', margin: '0 0 14px' }}>Timeline</p>
            {[
              { label: 'Created',        done: true,                  date: quote.createdAt },
              { label: 'Sent to client', done: !!quote.sentAt,        date: quote.sentAt },
              { label: 'Accepted',       done: !!quote.acceptedAt,    date: quote.acceptedAt },
              { label: 'Invoiced',       done: status === 'INVOICED', date: null },
            ].map((step, i) => (
              <div key={step.label} style={{ display: 'flex', gap: 10, marginBottom: i < 3 ? 12 : 0 }}>
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                  <div style={{ width: 20, height: 20, borderRadius: '50%', background: step.done ? '#16A34A' : '#F1F5F9', border: `2px solid ${step.done ? '#16A34A' : '#E2E8F0'}`, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                    {step.done && <CheckCircle size={12} color="white" strokeWidth={3} />}
                  </div>
                  {i < 3 && <div style={{ width: 2, flex: 1, background: step.done ? '#BBF7D0' : '#F1F5F9', minHeight: 16, marginTop: 3 }} />}
                </div>
                <div>
                  <p style={{ fontSize: 13, fontWeight: 600, color: step.done ? '#0F172A' : '#94A3B8', margin: 0 }}>{step.label}</p>
                  {step.date && <p style={{ fontSize: 11, color: '#94A3B8', margin: '1px 0 0' }}>{new Date(step.date).toLocaleDateString('en-ZA')}</p>}
                </div>
              </div>
            ))}
          </div>

          {/* Customer or walk-in card */}
          <div style={{ background: 'white', border: '1px solid #E8EDF5', borderRadius: 14, padding: 16, boxShadow: '0 1px 4px rgba(0,0,0,0.04)' }}>
            <p style={{ fontSize: 12, fontWeight: 700, color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.05em', margin: '0 0 10px' }}>
              {isWalkin ? 'Walk-in Client' : 'Customer'}
            </p>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 10 }}>
              <div style={{ width: 36, height: 36, borderRadius: '50%', background: isWalkin ? '#FEF3C7' : '#EFF6FF', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 13, fontWeight: 700, color: isWalkin ? '#92400E' : '#1D4ED8', flexShrink: 0 }}>
                {(displayName ?? '?').charAt(0).toUpperCase()}
              </div>
              <div>
                <p style={{ fontSize: 13, fontWeight: 700, color: '#0F172A', margin: 0 }}>{displayName ?? 'Unknown'}</p>
                {displayEmail && <p style={{ fontSize: 12, color: '#64748B', margin: '1px 0 0' }}>{displayEmail}</p>}
              </div>
            </div>
            {!isWalkin && (
              <button onClick={() => navigate('/customers')}
                style={{ width: '100%', padding: '7px 12px', background: '#F8FAFC', border: '1px solid #E2E8F0', borderRadius: 8, fontSize: 12, color: '#475569', cursor: 'pointer', fontWeight: 500 }}>
                View in CRM →
              </button>
            )}
          </div>

          {/* Status-specific cards */}
          {status === 'SENT' && quote.expiresAt && (
            <div style={{ background: '#FFFBEB', border: '1px solid #FCD34D', borderRadius: 12, padding: '14px 16px' }}>
              <div style={{ display: 'flex', gap: 8 }}>
                <Clock size={15} color="#F59E0B" style={{ marginTop: 1 }} />
                <div>
                  <p style={{ fontSize: 13, fontWeight: 600, color: '#92400E', margin: 0 }}>Expires soon</p>
                  <p style={{ fontSize: 12, color: '#B45309', margin: '2px 0 0' }}>
                    {new Date(quote.expiresAt).toLocaleDateString('en-ZA', { weekday: 'long', day: 'numeric', month: 'long' })}
                  </p>
                </div>
              </div>
            </div>
          )}

          {status === 'ACCEPTED' && (
            <div style={{ background: '#F0FDF4', border: '1px solid #BBF7D0', borderRadius: 12, padding: '14px 16px' }}>
              <div style={{ display: 'flex', gap: 8 }}>
                <CheckCircle size={15} color="#16A34A" />
                <div>
                  <p style={{ fontSize: 13, fontWeight: 600, color: '#166534', margin: 0 }}>Quote accepted</p>
                  <p style={{ fontSize: 12, color: '#15803D', margin: '2px 0 0' }}>Ready to convert to an invoice</p>
                </div>
              </div>
            </div>
          )}

          {status === 'REJECTED' && (
            <div style={{ background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 12, padding: '14px 16px' }}>
              <div style={{ display: 'flex', gap: 8 }}>
                <XCircle size={15} color="#DC2626" />
                <div>
                  <p style={{ fontSize: 13, fontWeight: 600, color: '#DC2626', margin: 0 }}>Quote rejected</p>
                  <p style={{ fontSize: 12, color: '#B91C1C', margin: '2px 0 0' }}>Create a new quote to proceed</p>
                </div>
              </div>
            </div>
          )}

          {status === 'INVOICED' && (
            <div style={{ background: '#F5F3FF', border: '1px solid #DDD6FE', borderRadius: 12, padding: '14px 16px' }}>
              <div style={{ display: 'flex', gap: 8, marginBottom: 10 }}>
                <FileCheck size={15} color="#7C3AED" />
                <div>
                  <p style={{ fontSize: 13, fontWeight: 600, color: '#6D28D9', margin: 0 }}>Invoice created</p>
                  <p style={{ fontSize: 12, color: '#7C3AED', margin: '2px 0 0' }}>This quote has been invoiced</p>
                </div>
              </div>
              <button onClick={() => navigate('/invoices')}
                style={{ width: '100%', padding: '7px 12px', background: 'white', border: '1px solid #DDD6FE', borderRadius: 8, fontSize: 12, color: '#7C3AED', cursor: 'pointer', fontWeight: 600 }}>
                View invoices →
              </button>
            </div>
          )}
        </div>
      </div>

      {/* Reject modal */}
      {showRejectModal && (
        <RejectModal
          quoteNumber={quote.quoteNumber}
          isPending={rejectQuote.isPending}
          error={rejectError}
          onConfirm={() => rejectQuote.mutate()}
          onCancel={() => { setShowRejectModal(false); setRejectError('') }}
        />
      )}
    </div>
  )
}