// src/pages/invoicing/CreateRetainerPage.tsx
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation } from '@tanstack/react-query'
import {
  ArrowLeft, AlertCircle, Users, UserPlus, ChevronDown, Gauge,
} from 'lucide-react'
import { apiClient } from '../../api/client'

interface Customer { id: string; name: string; email: string }

const inp: React.CSSProperties = {
  width: '100%', padding: '10px 14px', border: '1.5px solid #E2E8F0',
  borderRadius: 10, fontSize: 14, color: '#0F172A', outline: 'none',
  boxSizing: 'border-box', background: 'white',
}
const inpErr: React.CSSProperties = { ...inp, borderColor: '#DC2626', background: '#FFF5F5' }

function Field({ label, required, error, hint, children }: {
  label: string; required?: boolean; error?: string; hint?: string; children: React.ReactNode
}) {
  return (
    <div style={{ marginBottom: 18 }}>
      <label style={{ display: 'block', fontSize: 12, fontWeight: 700, color: '#374151', marginBottom: 6, textTransform: 'uppercase', letterSpacing: '0.05em' }}>
        {label}{required && <span style={{ color: '#DC2626', marginLeft: 2 }}>*</span>}
      </label>
      {children}
      {hint  && <p style={{ fontSize: 11, color: '#94A3B8', margin: '4px 0 0' }}>{hint}</p>}
      {error && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, color: '#DC2626', marginTop: 4 }}>
          <AlertCircle size={12} />{error}
        </div>
      )}
    </div>
  )
}

export function CreateRetainerPage() {
  const navigate = useNavigate()

  const [clientType, setClientType]   = useState<'existing' | 'walkin'>('existing')
  const [customerId, setCustomerId]   = useState('')
  const [walkinName, setWalkinName]   = useState('')
  const [walkinEmail, setWalkinEmail] = useState('')
  const [walkinPhone, setWalkinPhone] = useState('')
  const [title, setTitle]             = useState('')
  const [committedHours, setCommittedHours] = useState('')
  const [ratePerHour, setRatePerHour]       = useState('')
  const [vatRate, setVatRate]               = useState('15')
  const [notes, setNotes]                   = useState('')
  const [errors, setErrors]                 = useState<Record<string, string>>({})
  const [submitError, setSubmitError]       = useState('')

  const { data: customers = [] } = useQuery<Customer[]>({
    queryKey: ['customers-list'],
    queryFn: async () => {
      const res = await apiClient.get('/api/v1/crm/customers?size=500&sort=name,asc')
      const page = res.data?.data ?? res.data
      return page.content ?? page ?? []
    },
  })

  const hours  = parseFloat(committedHours) || 0
  const rate   = parseFloat(ratePerHour)   || 0
  const vat    = parseFloat(vatRate)        || 0
  const subtotal = hours * rate
  const vatAmount = subtotal * (vat / 100)
  const total  = subtotal + vatAmount

  const fmtR = (n: number) => `R ${n.toLocaleString('en-ZA', { minimumFractionDigits: 2 })}`

  const validate = () => {
    const e: Record<string, string> = {}
    if (clientType === 'existing' && !customerId) e.customerId = 'Please select a customer'
    if (clientType === 'walkin' && !walkinName.trim()) e.walkinName = 'Client name is required'
    if (!title.trim())         e.title = 'Title is required'
    if (!committedHours || hours <= 0) e.committedHours = 'Enter a valid number of hours'
    if (!ratePerHour   || rate  <= 0)  e.ratePerHour   = 'Enter a valid rate per hour'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  const create = useMutation({
    mutationFn: async () => {
      const body: any = {
        title,
        committedHours: hours,
        ratePerHour:    rate,
        vatRate:        vat,
        notes:          notes || undefined,
      }
      if (clientType === 'existing') {
        body.customerId = customerId
      } else {
        body.walkinClientName  = walkinName
        body.walkinClientEmail = walkinEmail || undefined
        body.walkinClientPhone = walkinPhone || undefined
      }
      const res = await apiClient.post('/api/v1/invoicing/invoices/retainer', body)
      return res.data?.data?.id ?? res.data?.id
    },
    onSuccess: () => navigate('/invoices'),
    onError: (e: any) => setSubmitError(e.response?.data?.message ?? 'Failed to create retainer invoice.'),
  })

  const handleSubmit = () => {
    setSubmitError('')
    if (validate()) create.mutate()
  }

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif", maxWidth: 860, margin: '0 auto' }}>

      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 28 }}>
        <button onClick={() => navigate('/invoices')}
          style={{ display: 'flex', alignItems: 'center', gap: 6, background: 'white', border: '1px solid #E2E8F0', borderRadius: 9, padding: '7px 12px', fontSize: 13, fontWeight: 600, color: '#374151', cursor: 'pointer' }}>
          <ArrowLeft size={15} /> Back
        </button>
        <div>
          <h1 style={{ fontSize: 22, fontWeight: 800, color: '#0F172A', margin: 0 }}>New Retainer Invoice</h1>
          <p style={{ fontSize: 12, color: '#94A3B8', margin: 0 }}>Upfront committed hours — client pays before machine is released</p>
        </div>
      </div>

      {submitError && (
        <div style={{ marginBottom: 20, padding: '12px 16px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 10, fontSize: 13, color: '#DC2626', display: 'flex', alignItems: 'center', gap: 10 }}>
          <AlertCircle size={16} style={{ flexShrink: 0 }} />{submitError}
        </div>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 300px', gap: 20, alignItems: 'start' }}>

        {/* Left */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>

          {/* Client card */}
          <div style={{ background: 'white', border: '1px solid #E8EDF5', borderRadius: 16, padding: 24, boxShadow: '0 1px 4px rgba(0,0,0,0.04)' }}>
            <p style={{ fontSize: 12, fontWeight: 700, color: '#0F172A', margin: '0 0 18px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Client</p>

            <div style={{ marginBottom: 18 }}>
              <label style={{ display: 'block', fontSize: 12, fontWeight: 700, color: '#374151', marginBottom: 8, textTransform: 'uppercase', letterSpacing: '0.05em' }}>Client type</label>
              <div style={{ display: 'flex', gap: 8 }}>
                {([['existing', Users, 'Saved customer'], ['walkin', UserPlus, 'Walk-in client']] as const).map(([type, Icon, label]) => (
                  <button key={type} onClick={() => setClientType(type)}
                    style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 7, padding: '10px', borderRadius: 10,
                      border: clientType === type ? '2px solid #7C3AED' : '1.5px solid #E2E8F0',
                      background: clientType === type ? '#F5F3FF' : 'white',
                      color: clientType === type ? '#7C3AED' : '#64748B',
                      fontWeight: 600, fontSize: 13, cursor: 'pointer' }}>
                    <Icon size={15} />{label}
                  </button>
                ))}
              </div>
            </div>

            {clientType === 'existing' ? (
              <Field label="Customer" required error={errors.customerId}>
                <div style={{ position: 'relative' }}>
                  <select style={errors.customerId ? { ...inpErr, paddingRight: 36, appearance: 'none' } : { ...inp, paddingRight: 36, appearance: 'none' }}
                    value={customerId} onChange={e => { setCustomerId(e.target.value); setErrors(f => { const n = { ...f }; delete n.customerId; return n }) }}>
                    <option value="">Select a customer...</option>
                    {customers.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
                  </select>
                  <ChevronDown size={15} color="#94A3B8" style={{ position: 'absolute', right: 12, top: '50%', transform: 'translateY(-50%)', pointerEvents: 'none' }} />
                </div>
              </Field>
            ) : (
              <div style={{ background: '#F8FAFC', borderRadius: 10, padding: 16, border: '1px solid #E2E8F0' }}>
                <div style={{ fontSize: 12, fontWeight: 700, color: '#94A3B8', letterSpacing: '0.05em', marginBottom: 12 }}>WALK-IN CLIENT DETAILS</div>
                <Field label="Name" required error={errors.walkinName}>
                  <input style={errors.walkinName ? inpErr : inp} value={walkinName}
                    onChange={e => { setWalkinName(e.target.value); setErrors(f => { const n = { ...f }; delete n.walkinName; return n }) }}
                    placeholder="e.g. John Smith" autoFocus />
                </Field>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                  <Field label="Email (optional)">
                    <input style={inp} value={walkinEmail} onChange={e => setWalkinEmail(e.target.value)} placeholder="john@example.com" type="email" />
                  </Field>
                  <Field label="Phone (optional)">
                    <input style={inp} value={walkinPhone} onChange={e => setWalkinPhone(e.target.value)} placeholder="+27 82 000 0000" />
                  </Field>
                </div>
              </div>
            )}

            <Field label="Invoice title" required error={errors.title}>
              <input style={errors.title ? inpErr : inp} value={title}
                onChange={e => { setTitle(e.target.value); setErrors(f => { const n = { ...f }; delete n.title; return n }) }}
                placeholder="e.g. Cat 320 Excavator — 200hr upfront commitment" />
            </Field>

            <Field label="Notes (optional)" hint="Machine details, site location, terms, etc.">
              <textarea style={{ ...inp, minHeight: 80, resize: 'vertical' as const }}
                value={notes} onChange={e => setNotes(e.target.value)}
                placeholder="e.g. Machine to be released upon payment. Site: Kathu Northern Cape." />
            </Field>
          </div>

          {/* Hours & Rate card */}
          <div style={{ background: 'white', border: '1px solid #E8EDF5', borderRadius: 16, padding: 24, boxShadow: '0 1px 4px rgba(0,0,0,0.04)' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 18 }}>
              <Gauge size={16} color="#7C3AED" />
              <p style={{ fontSize: 12, fontWeight: 700, color: '#0F172A', margin: 0, textTransform: 'uppercase', letterSpacing: '0.05em' }}>Commitment Terms</p>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 16 }}>
              <Field label="Committed hours *" error={errors.committedHours}
                hint="Minimum hours client must pay upfront">
                <input type="number" min="1" step="0.5" style={errors.committedHours ? inpErr : inp}
                  value={committedHours}
                  onChange={e => { setCommittedHours(e.target.value); setErrors(f => { const n = { ...f }; delete n.committedHours; return n }) }}
                  placeholder="e.g. 200" />
              </Field>
              <Field label="Rate per hour (R) *" error={errors.ratePerHour}>
                <input type="number" min="0" step="0.01" style={errors.ratePerHour ? inpErr : inp}
                  value={ratePerHour}
                  onChange={e => { setRatePerHour(e.target.value); setErrors(f => { const n = { ...f }; delete n.ratePerHour; return n }) }}
                  placeholder="e.g. 1250.00" />
              </Field>
              <Field label="VAT rate (%)" hint="Applied to the total commitment">
                <input type="number" min="0" max="100" step="0.5" style={inp}
                  value={vatRate} onChange={e => setVatRate(e.target.value)} />
              </Field>
            </div>

            {/* Live preview */}
            {hours > 0 && rate > 0 && (
              <div style={{ marginTop: 8, background: '#F5F3FF', border: '1px solid #DDD6FE', borderRadius: 10, padding: '14px 18px' }}>
                <div style={{ fontSize: 11, fontWeight: 700, color: '#7C3AED', letterSpacing: '0.05em', marginBottom: 10 }}>COMMITMENT PREVIEW</div>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 12, marginBottom: 8 }}>
                  {[
                    { label: 'Hours', value: `${hours}h` },
                    { label: 'Rate',  value: fmtR(rate) + '/hr' },
                    { label: 'VAT',   value: `${vat}%` },
                  ].map(m => (
                    <div key={m.label}>
                      <div style={{ fontSize: 11, color: '#7C3AED', marginBottom: 2 }}>{m.label}</div>
                      <div style={{ fontSize: 14, fontWeight: 600, color: '#4C1D95' }}>{m.value}</div>
                    </div>
                  ))}
                </div>
                <div style={{ borderTop: '1px solid #DDD6FE', paddingTop: 10, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div>
                    <div style={{ fontSize: 11, color: '#7C3AED' }}>Subtotal {fmtR(subtotal)} + VAT {fmtR(vatAmount)}</div>
                  </div>
                  <div style={{ fontSize: 20, fontWeight: 800, color: '#4C1D95' }}>{fmtR(total)}</div>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Right — summary & submit */}
        <div style={{ position: 'sticky', top: 80 }}>
          <div style={{ background: 'white', border: '1px solid #E8EDF5', borderRadius: 16, padding: 24, boxShadow: '0 1px 4px rgba(0,0,0,0.04)' }}>
            <p style={{ fontSize: 12, fontWeight: 700, color: '#0F172A', margin: '0 0 16px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Summary</p>

            {[['Committed hours', hours > 0 ? `${hours}h` : '—'],
              ['Rate/hr', rate > 0 ? fmtR(rate) : '—'],
              ['Subtotal', hours > 0 && rate > 0 ? fmtR(subtotal) : '—'],
              ['VAT', hours > 0 && rate > 0 ? fmtR(vatAmount) : '—'],
            ].map(([l, v]) => (
              <div key={l} style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
                <span style={{ fontSize: 13, color: '#64748B' }}>{l}</span>
                <span style={{ fontSize: 13, fontWeight: 600, color: '#0F172A' }}>{v}</span>
              </div>
            ))}

            <div style={{ height: 1, background: '#F1F5F9', margin: '12px 0' }} />
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 20 }}>
              <span style={{ fontSize: 14, fontWeight: 700, color: '#0F172A' }}>Total due</span>
              <span style={{ fontSize: 20, fontWeight: 800, color: '#7C3AED' }}>
                {hours > 0 && rate > 0 ? fmtR(total) : '—'}
              </span>
            </div>

            <div style={{ background: '#FEF3C7', border: '1px solid #FCD34D', borderRadius: 8, padding: '10px 12px', fontSize: 12, color: '#92400E', marginBottom: 16 }}>
              Machine will not be released until this invoice is paid in full.
            </div>

            <button onClick={handleSubmit} disabled={create.isPending}
              style={{ width: '100%', padding: 12, border: 'none', borderRadius: 10,
                background: create.isPending ? '#C4B5FD' : '#7C3AED',
                color: 'white', fontSize: 14, fontWeight: 700,
                cursor: create.isPending ? 'not-allowed' : 'pointer' }}>
              {create.isPending ? 'Creating...' : 'Create retainer invoice'}
            </button>

            <div style={{ marginTop: 10 }}>
              {Object.values(errors).map((e, i) => (
                <p key={i} style={{ fontSize: 11, color: '#DC2626', margin: '2px 0', display: 'flex', alignItems: 'center', gap: 4 }}>
                  <AlertCircle size={11} />{e}
                </p>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
