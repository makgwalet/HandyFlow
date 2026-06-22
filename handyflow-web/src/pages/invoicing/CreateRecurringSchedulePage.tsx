// src/pages/invoicing/CreateRecurringSchedulePage.tsx
import { useState, useRef, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation } from '@tanstack/react-query'
import {
  ArrowLeft, AlertCircle, Users, UserPlus, ChevronDown,
  Search, Plus, Trash2, FileText, RefreshCw,
} from 'lucide-react'
import { apiClient } from '../../api/client'

interface Customer { id: string; name: string; email: string }
interface CatalogueItem { id: string; name: string; unit: string; defaultPrice: number; vatRate: number; categoryName: string | null }
interface LineItem { tempId: string; catalogueItemId?: string; description: string; unit: string; quantity: number; unitPrice: number; vatRate: number }

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

const FREQUENCIES = [
  { value: 'DAILY',   label: 'Daily',         hint: 'Every day' },
  { value: 'WEEKLY',  label: 'Weekly',        hint: 'Every 7 days' },
  { value: 'MONTHLY', label: 'Monthly',       hint: 'Every 30 days' },
  { value: 'CUSTOM',  label: 'Custom interval', hint: 'You choose the days' },
]

export function CreateRecurringSchedulePage() {
  const navigate = useNavigate()

  const [clientType, setClientType]   = useState<'existing' | 'walkin'>('existing')
  const [customerId, setCustomerId]   = useState('')
  const [walkinName, setWalkinName]   = useState('')
  const [walkinEmail, setWalkinEmail] = useState('')
  const [walkinPhone, setWalkinPhone] = useState('')
  const [title, setTitle]             = useState('')
  const [notes, setNotes]             = useState('')
  const [frequency, setFrequency]     = useState('MONTHLY')
  const [customDays, setCustomDays]   = useState('')
  const [startDate, setStartDate]     = useState(() => {
    const d = new Date(); d.setDate(d.getDate() + 1)
    return d.toISOString().split('T')[0]
  })
  const [endDate, setEndDate]         = useState('')
  const [lineItems, setLineItems]     = useState<LineItem[]>([])
  const [itemSearch, setItemSearch]   = useState('')
  const [showCatalog, setShowCatalog] = useState(false)
  const [errors, setErrors]           = useState<Record<string, string>>({})
  const [submitError, setSubmitError] = useState('')

  const catalogRef = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const h = (e: MouseEvent) => {
      if (catalogRef.current && !catalogRef.current.contains(e.target as Node)) setShowCatalog(false)
    }
    document.addEventListener('mousedown', h)
    return () => document.removeEventListener('mousedown', h)
  }, [])

  const { data: customers = [] } = useQuery<Customer[]>({
    queryKey: ['customers-list'],
    queryFn: async () => {
      const res = await apiClient.get('/api/v1/crm/customers?size=500&sort=name,asc')
      const page = res.data?.data ?? res.data
      return page.content ?? page ?? []
    },
  })

  const { data: catalogueItems = [] } = useQuery<CatalogueItem[]>({
    queryKey: ['catalogue-search', itemSearch],
    queryFn: async () => {
      const res = await apiClient.get('/api/v1/catalogue/items', { params: { query: itemSearch } })
      return (res.data?.data ?? res.data) as CatalogueItem[]
    },
  })

  const subtotal = lineItems.reduce((s, l) => s + l.quantity * l.unitPrice, 0)
  const vatTotal = lineItems.reduce((s, l) => s + l.quantity * l.unitPrice * (l.vatRate / 100), 0)
  const total    = subtotal + vatTotal
  const fmtR = (n: number) => `R ${n.toLocaleString('en-ZA', { minimumFractionDigits: 2 })}`

  const addFromCatalogue = (item: CatalogueItem) => {
    setLineItems(prev => [...prev, {
      tempId: crypto.randomUUID(), catalogueItemId: item.id,
      description: item.name, unit: item.unit,
      quantity: 1, unitPrice: item.defaultPrice, vatRate: item.vatRate,
    }])
    setShowCatalog(false); setItemSearch('')
    setErrors(e => { const n = { ...e }; delete n.lineItems; return n })
  }

  const addBlank = () => {
    setLineItems(prev => [...prev, { tempId: crypto.randomUUID(), description: '', unit: 'Each', quantity: 1, unitPrice: 0, vatRate: 15 }])
    setErrors(e => { const n = { ...e }; delete n.lineItems; return n })
  }

  const updateLine = (tempId: string, field: keyof LineItem, value: string | number) =>
    setLineItems(prev => prev.map(l => l.tempId === tempId ? { ...l, [field]: value } : l))

  const removeLine = (tempId: string) =>
    setLineItems(prev => prev.filter(l => l.tempId !== tempId))

  const validate = () => {
    const e: Record<string, string> = {}
    if (clientType === 'existing' && !customerId) e.customerId = 'Please select a customer'
    if (clientType === 'walkin' && !walkinName.trim()) e.walkinName = 'Client name is required'
    if (!title.trim()) e.title = 'Title is required'
    if (!startDate)    e.startDate = 'Start date is required'
    if (frequency === 'CUSTOM' && (!customDays || parseInt(customDays) < 1)) e.customDays = 'Enter a valid interval in days'
    if (lineItems.length === 0) e.lineItems = 'Add at least one line item'
    if (lineItems.some(l => !l.description.trim())) e.lineItems = 'All line items need a description'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  const create = useMutation({
    mutationFn: async () => {
      const body: any = {
        title, notes: notes || undefined,
        frequency,
        customIntervalDays: frequency === 'CUSTOM' ? parseInt(customDays) : undefined,
        startDate: new Date(startDate).toISOString(),
        endDate:   endDate ? new Date(endDate).toISOString() : undefined,
      }
      if (clientType === 'existing') {
        body.customerId = customerId
      } else {
        body.walkinClientName  = walkinName
        body.walkinClientEmail = walkinEmail || undefined
        body.walkinClientPhone = walkinPhone || undefined
      }
      const schedRes = await apiClient.post('/api/v1/invoicing/recurring-schedules', body)
      const schedId  = schedRes.data?.data?.id ?? schedRes.data?.id

      for (const li of lineItems) {
        await apiClient.post(`/api/v1/invoicing/recurring-schedules/${schedId}/line-items`, {
          catalogueItemId: li.catalogueItemId,
          description: li.description,
          unit: li.unit,
          quantity: li.quantity,
          unitPrice: li.unitPrice,
          vatRate: li.vatRate,
        })
      }
      return schedId
    },
    onSuccess: () => navigate('/recurring'),
    onError: (e: any) => setSubmitError(e.response?.data?.message ?? 'Failed to create schedule.'),
  })

  const handleSubmit = () => {
    setSubmitError('')
    if (validate()) create.mutate()
  }

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif", maxWidth: 960, margin: '0 auto' }}>

      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 28 }}>
        <button onClick={() => navigate('/recurring')}
          style={{ display: 'flex', alignItems: 'center', gap: 6, background: 'white', border: '1px solid #E2E8F0', borderRadius: 9, padding: '7px 12px', fontSize: 13, fontWeight: 600, color: '#374151', cursor: 'pointer' }}>
          <ArrowLeft size={15} /> Back
        </button>
        <div>
          <h1 style={{ fontSize: 22, fontWeight: 800, color: '#0F172A', margin: 0 }}>New Recurring Schedule</h1>
          <p style={{ fontSize: 12, color: '#94A3B8', margin: 0 }}>Invoices will be generated automatically on the configured cadence</p>
        </div>
      </div>

      {submitError && (
        <div style={{ marginBottom: 20, padding: '12px 16px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 10, fontSize: 13, color: '#DC2626', display: 'flex', gap: 10 }}>
          <AlertCircle size={16} style={{ flexShrink: 0, marginTop: 1 }} />{submitError}
        </div>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 320px', gap: 20, alignItems: 'start' }}>

        {/* Left */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>

          {/* Schedule details */}
          <div style={{ background: 'white', border: '1px solid #E8EDF5', borderRadius: 16, padding: 24, boxShadow: '0 1px 4px rgba(0,0,0,0.04)' }}>
            <p style={{ fontSize: 12, fontWeight: 700, color: '#0F172A', margin: '0 0 18px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Schedule details</p>

            {/* Client type */}
            <div style={{ marginBottom: 18 }}>
              <label style={{ display: 'block', fontSize: 12, fontWeight: 700, color: '#374151', marginBottom: 8, textTransform: 'uppercase', letterSpacing: '0.05em' }}>Client type</label>
              <div style={{ display: 'flex', gap: 8 }}>
                {([['existing', Users, 'Saved customer'], ['walkin', UserPlus, 'Walk-in client']] as const).map(([type, Icon, label]) => (
                  <button key={type} onClick={() => setClientType(type)}
                    style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 7, padding: '10px', borderRadius: 10,
                      border: clientType === type ? '2px solid #0D9488' : '1.5px solid #E2E8F0',
                      background: clientType === type ? '#F0FDFA' : 'white',
                      color: clientType === type ? '#0D9488' : '#64748B',
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
              <div style={{ background: '#F8FAFC', borderRadius: 10, padding: 16, marginBottom: 18, border: '1px solid #E2E8F0' }}>
                <div style={{ fontSize: 12, fontWeight: 700, color: '#94A3B8', letterSpacing: '0.05em', marginBottom: 12 }}>WALK-IN CLIENT DETAILS</div>
                <Field label="Name" required error={errors.walkinName}>
                  <input style={errors.walkinName ? inpErr : inp} value={walkinName}
                    onChange={e => { setWalkinName(e.target.value); setErrors(f => { const n = { ...f }; delete n.walkinName; return n }) }}
                    placeholder="e.g. Kathu Mining Pty Ltd" autoFocus />
                </Field>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                  <Field label="Email (optional)">
                    <input style={inp} value={walkinEmail} onChange={e => setWalkinEmail(e.target.value)} placeholder="accounts@company.co.za" type="email" />
                  </Field>
                  <Field label="Phone (optional)">
                    <input style={inp} value={walkinPhone} onChange={e => setWalkinPhone(e.target.value)} placeholder="+27 53 000 0000" />
                  </Field>
                </div>
              </div>
            )}

            <Field label="Schedule title" required error={errors.title}>
              <input style={errors.title ? inpErr : inp} value={title}
                onChange={e => { setTitle(e.target.value); setErrors(f => { const n = { ...f }; delete n.title; return n }) }}
                placeholder="e.g. Monthly site security fee — Kathu Mine" />
            </Field>

            <Field label="Notes (optional)" hint="Included on each generated invoice">
              <textarea style={{ ...inp, minHeight: 70, resize: 'vertical' as const }}
                value={notes} onChange={e => setNotes(e.target.value)}
                placeholder="Payment terms, service details, etc." />
            </Field>
          </div>

          {/* Cadence card */}
          <div style={{ background: 'white', border: '1px solid #E8EDF5', borderRadius: 16, padding: 24, boxShadow: '0 1px 4px rgba(0,0,0,0.04)' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 18 }}>
              <RefreshCw size={15} color="#0D9488" />
              <p style={{ fontSize: 12, fontWeight: 700, color: '#0F172A', margin: 0, textTransform: 'uppercase', letterSpacing: '0.05em' }}>Billing cadence</p>
            </div>

            {/* Frequency selector */}
            <div style={{ marginBottom: 18 }}>
              <label style={{ display: 'block', fontSize: 12, fontWeight: 700, color: '#374151', marginBottom: 8, textTransform: 'uppercase', letterSpacing: '0.05em' }}>Frequency *</label>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 8 }}>
                {FREQUENCIES.map(f => (
                  <button key={f.value} onClick={() => setFrequency(f.value)}
                    style={{ padding: '10px 8px', borderRadius: 10, textAlign: 'center',
                      border: frequency === f.value ? '2px solid #0D9488' : '1.5px solid #E2E8F0',
                      background: frequency === f.value ? '#F0FDFA' : 'white',
                      color: frequency === f.value ? '#0D9488' : '#64748B',
                      cursor: 'pointer' }}>
                    <div style={{ fontSize: 13, fontWeight: 700 }}>{f.label}</div>
                    <div style={{ fontSize: 10, color: frequency === f.value ? '#0F766E' : '#94A3B8', marginTop: 2 }}>{f.hint}</div>
                  </button>
                ))}
              </div>
            </div>

            {frequency === 'CUSTOM' && (
              <Field label="Interval (days)" required error={errors.customDays} hint="e.g. 14 = every two weeks">
                <input type="number" min="1" style={errors.customDays ? inpErr : inp}
                  value={customDays} onChange={e => { setCustomDays(e.target.value); setErrors(f => { const n = { ...f }; delete n.customDays; return n }) }}
                  placeholder="e.g. 14" />
              </Field>
            )}

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
              <Field label="Start date" required error={errors.startDate} hint="First invoice fires on this date">
                <input type="date" style={errors.startDate ? inpErr : inp}
                  value={startDate} onChange={e => { setStartDate(e.target.value); setErrors(f => { const n = { ...f }; delete n.startDate; return n }) }} />
              </Field>
              <Field label="End date (optional)" hint="Leave blank to run indefinitely">
                <input type="date" style={inp} value={endDate} onChange={e => setEndDate(e.target.value)}
                  min={startDate} />
              </Field>
            </div>
          </div>

          {/* Line items card */}
          <div style={{ background: 'white', border: '1px solid #E8EDF5', borderRadius: 16, overflow: 'hidden', boxShadow: '0 1px 4px rgba(0,0,0,0.04)' }}>
            <div style={{ padding: '16px 24px', borderBottom: '1px solid #F1F5F9', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <div>
                <p style={{ fontSize: 12, fontWeight: 700, color: '#0F172A', margin: 0, textTransform: 'uppercase', letterSpacing: '0.05em' }}>Line items (template)</p>
                <p style={{ fontSize: 11, color: '#94A3B8', margin: '2px 0 0' }}>Copied to every generated invoice</p>
                {errors.lineItems && (
                  <div style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, color: '#DC2626', marginTop: 3 }}>
                    <AlertCircle size={12} />{errors.lineItems}
                  </div>
                )}
              </div>
              <div style={{ display: 'flex', gap: 8 }} ref={catalogRef}>
                <div style={{ position: 'relative' }}>
                  <button onClick={() => setShowCatalog(s => !s)}
                    style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '7px 12px', background: '#F0FDFA', border: '1px solid #CCFBF1', borderRadius: 8, fontSize: 12, fontWeight: 600, color: '#0D9488', cursor: 'pointer', whiteSpace: 'nowrap' }}>
                    <Search size={13} /> Search catalogue
                  </button>
                  {showCatalog && (
                    <div style={{ position: 'fixed', zIndex: 9999, width: 380, background: 'white', border: '1.5px solid #E2E8F0', borderRadius: 14, boxShadow: '0 12px 40px rgba(0,0,0,0.15)', overflow: 'hidden' }}
                      ref={el => {
                        if (el && catalogRef.current) {
                          const btn = catalogRef.current.querySelector('button') as HTMLElement
                          if (btn) { const r = btn.getBoundingClientRect(); el.style.top = `${r.bottom + 6}px`; el.style.left = `${Math.max(8, r.right - 380)}px` }
                        }
                      }}>
                      <div style={{ padding: '10px 12px', borderBottom: '1px solid #F1F5F9' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8, background: '#F8FAFC', borderRadius: 8, padding: '8px 10px' }}>
                          <Search size={13} color="#94A3B8" />
                          <input value={itemSearch} onChange={e => setItemSearch(e.target.value)} placeholder="Type to search..." autoFocus
                            style={{ border: 'none', background: 'none', outline: 'none', fontSize: 13, width: '100%', color: '#0F172A' }} />
                        </div>
                      </div>
                      <div style={{ maxHeight: 280, overflowY: 'auto' }}>
                        {catalogueItems.length === 0 ? (
                          <p style={{ padding: '20px 14px', fontSize: 13, color: '#94A3B8', textAlign: 'center', margin: 0 }}>
                            {itemSearch ? 'No items match' : 'Start typing to search...'}
                          </p>
                        ) : catalogueItems.map(item => (
                          <div key={item.id} onClick={() => addFromCatalogue(item)}
                            style={{ padding: '11px 14px', cursor: 'pointer', borderBottom: '1px solid #F8FAFC', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}
                            onMouseEnter={e => (e.currentTarget.style.background = '#F8FAFC')}
                            onMouseLeave={e => (e.currentTarget.style.background = 'white')}>
                            <div>
                              <p style={{ fontSize: 13, fontWeight: 600, color: '#0F172A', margin: 0 }}>{item.name}</p>
                              <p style={{ fontSize: 11, color: '#94A3B8', margin: '2px 0 0' }}>{item.categoryName ?? 'Uncategorised'} · {item.unit}</p>
                            </div>
                            <p style={{ fontSize: 13, fontWeight: 700, color: '#0D9488', margin: 0, flexShrink: 0, marginLeft: 12 }}>
                              R {Number(item.defaultPrice).toLocaleString('en-ZA', { minimumFractionDigits: 2 })}
                            </p>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
                <button onClick={addBlank}
                  style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '7px 12px', background: 'white', border: '1px solid #E2E8F0', borderRadius: 8, fontSize: 12, fontWeight: 600, color: '#374151', cursor: 'pointer', whiteSpace: 'nowrap' }}>
                  <Plus size={13} /> Add blank
                </button>
              </div>
            </div>

            {lineItems.length === 0 ? (
              <div style={{ padding: '40px 24px', textAlign: 'center' }}>
                <FileText size={32} color="#CBD5E1" style={{ marginBottom: 10 }} />
                <p style={{ fontSize: 14, color: '#94A3B8', margin: 0 }}>Add line items — these become the template for every invoice</p>
              </div>
            ) : (
              <div>
                <div style={{ display: 'grid', gridTemplateColumns: '3fr 80px 110px 70px 70px 32px', gap: 8, padding: '10px 20px', background: '#F8FAFC', borderBottom: '1px solid #F1F5F9' }}>
                  {['Description', 'Unit', 'Unit price', 'Qty', 'VAT %', ''].map(h => (
                    <p key={h} style={{ fontSize: 11, fontWeight: 700, color: '#94A3B8', margin: 0, textTransform: 'uppercase', letterSpacing: '0.04em' }}>{h}</p>
                  ))}
                </div>
                {lineItems.map((li, i) => (
                  <div key={li.tempId} style={{ display: 'grid', gridTemplateColumns: '3fr 80px 110px 70px 70px 32px', gap: 8, padding: '10px 20px', borderTop: i === 0 ? 'none' : '1px solid #F8FAFC', alignItems: 'center' }}>
                    <input value={li.description} onChange={e => updateLine(li.tempId, 'description', e.target.value)}
                      placeholder="Item description"
                      style={{ ...inp, fontSize: 13, padding: '7px 10px', borderColor: !li.description.trim() && errors.lineItems ? '#DC2626' : '#E2E8F0' }} />
                    <input value={li.unit} onChange={e => updateLine(li.tempId, 'unit', e.target.value)}
                      style={{ ...inp, fontSize: 13, padding: '7px 10px' }} />
                    <input type="number" value={li.unitPrice} onChange={e => updateLine(li.tempId, 'unitPrice', parseFloat(e.target.value) || 0)}
                      style={{ ...inp, fontSize: 13, padding: '7px 10px' }} />
                    <input type="number" value={li.quantity} onChange={e => updateLine(li.tempId, 'quantity', parseFloat(e.target.value) || 0)}
                      style={{ ...inp, fontSize: 13, padding: '7px 10px' }} />
                    <input type="number" value={li.vatRate} onChange={e => updateLine(li.tempId, 'vatRate', parseFloat(e.target.value) || 0)}
                      style={{ ...inp, fontSize: 13, padding: '7px 10px' }} />
                    <button onClick={() => removeLine(li.tempId)}
                      style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#FDA4AF', padding: 4, borderRadius: 6, display: 'flex' }}>
                      <Trash2 size={15} />
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Right — summary */}
        <div style={{ position: 'sticky', top: 80 }}>
          <div style={{ background: 'white', border: '1px solid #E8EDF5', borderRadius: 16, padding: 24, boxShadow: '0 1px 4px rgba(0,0,0,0.04)' }}>
            <p style={{ fontSize: 12, fontWeight: 700, color: '#0F172A', margin: '0 0 16px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Per invoice</p>

            {[['Subtotal', fmtR(subtotal)], ['VAT', fmtR(vatTotal)]].map(([l, v]) => (
              <div key={l} style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
                <span style={{ fontSize: 13, color: '#64748B' }}>{l}</span>
                <span style={{ fontSize: 13, fontWeight: 600, color: '#0F172A' }}>{v}</span>
              </div>
            ))}
            <div style={{ height: 1, background: '#F1F5F9', margin: '10px 0' }} />
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
              <span style={{ fontSize: 14, fontWeight: 700, color: '#0F172A' }}>Total</span>
              <span style={{ fontSize: 20, fontWeight: 800, color: '#0D9488' }}>{fmtR(total)}</span>
            </div>

            {/* Cadence summary */}
            <div style={{ background: '#F0FDFA', border: '1px solid #CCFBF1', borderRadius: 8, padding: '10px 12px', fontSize: 12, color: '#0F766E', marginBottom: 16 }}>
              <div style={{ fontWeight: 700, marginBottom: 4 }}>
                {FREQUENCIES.find(f => f.value === frequency)?.label ?? frequency} billing
              </div>
              <div>Starts {startDate || '—'}{endDate ? ` · Ends ${endDate}` : ' · No end date'}</div>
              {frequency === 'CUSTOM' && customDays && <div>Every {customDays} day{parseInt(customDays) !== 1 ? 's' : ''}</div>}
            </div>

            {lineItems.length > 0 && (
              <div style={{ background: '#F8FAFC', borderRadius: 8, padding: '8px 12px', marginBottom: 14, fontSize: 12, color: '#64748B' }}>
                {lineItems.length} line item{lineItems.length !== 1 ? 's' : ''} · auto-issued on each run
              </div>
            )}

            <button onClick={handleSubmit} disabled={create.isPending}
              style={{ width: '100%', padding: 12, border: 'none', borderRadius: 10,
                background: create.isPending ? '#5EEAD4' : '#0D9488',
                color: 'white', fontSize: 14, fontWeight: 700,
                cursor: create.isPending ? 'not-allowed' : 'pointer' }}>
              {create.isPending ? 'Creating...' : 'Create recurring schedule'}
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
