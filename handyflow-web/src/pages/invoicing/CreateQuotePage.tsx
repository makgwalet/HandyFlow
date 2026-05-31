// src/pages/invoicing/CreateQuotePage.tsx
import { useState, useRef, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation } from '@tanstack/react-query'
import {
  ArrowLeft, Search, Plus, Trash2, ChevronDown, FileText,
  AlertCircle, Users, UserPlus,
} from 'lucide-react'
import { apiClient } from '../../api/client'

interface Customer { id: string; name: string; email: string; taxNumber: string }
interface CatalogueItem { id: string; name: string; unit: string; defaultPrice: number; vatRate: number; categoryName: string | null }
interface LineItem {
  tempId: string; catalogueItemId?: string; description: string
  unit: string; quantity: number; unitPrice: number; vatRate: number
}

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
          <AlertCircle size={12} color="#DC2626" />{error}
        </div>
      )}
    </div>
  )
}

export function CreateQuotePage() {
  const navigate = useNavigate()

  // Client type: 'existing' = saved customer, 'walkin' = no CRM record
  const [clientType, setClientType]   = useState<'existing' | 'walkin'>('existing')
  const [customerId, setCustomerId]   = useState('')
  const [walkinName, setWalkinName]   = useState('')
  const [walkinEmail, setWalkinEmail] = useState('')
  const [walkinPhone, setWalkinPhone] = useState('')

  const [title, setTitle]       = useState('')
  const [notes, setNotes]       = useState('')
  const [lineItems, setLineItems] = useState<LineItem[]>([])
  const [itemSearch, setItemSearch]   = useState('')
  const [showCatalog, setShowCatalog] = useState(false)
  const [createError, setCreateError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const catalogRef = useRef<HTMLDivElement>(null)

  // Close catalogue on outside click
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (catalogRef.current && !catalogRef.current.contains(e.target as Node)) setShowCatalog(false)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
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

  const createQuote = useMutation({
    mutationFn: async () => {
      const body: any = { title, notes: notes || undefined }

      if (clientType === 'existing') {
        body.customerId = customerId
      } else {
        // Walk-in: pass name/contact but no customerId
        body.walkinClientName  = walkinName
        body.walkinClientEmail = walkinEmail || undefined
        body.walkinClientPhone = walkinPhone || undefined
      }

      const quoteRes = await apiClient.post('/api/v1/invoicing/quotes', body)
      const quoteId = quoteRes.data?.data?.id ?? quoteRes.data?.id

      for (const li of lineItems) {
        await apiClient.post(`/api/v1/invoicing/quotes/${quoteId}/line-items`, {
          catalogueItemId: li.catalogueItemId,
          description: li.description,
          unit: li.unit,
          quantity: li.quantity,
          unitPrice: li.unitPrice,
          vatRate: li.vatRate,
        })
      }
      return quoteId
    },
    onSuccess: (quoteId) => navigate(`/quotes/${quoteId}`),
    onError: (e: any) => {
      const msg = e.response?.data?.message ?? 'Failed to create quote. Please try again.'
      setCreateError(msg)
      // Scroll to top so user sees the error
      window.scrollTo({ top: 0, behavior: 'smooth' })
    },
  })

  const validate = (): boolean => {
    const errs: Record<string, string> = {}

    if (clientType === 'existing' && !customerId) {
        errs.customerId = 'Please select a customer'
    }

    if (clientType === 'walkin') {
        if (!walkinName.trim()) {
        errs.walkinName = 'Walk-in client name is required'
        }
        if (walkinEmail && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(walkinEmail)) {
        errs.walkinEmail = 'Please enter a valid email address'
        }
        if (walkinPhone && !/^(\+|0)[\d\s\-]{7,}$/.test(walkinPhone)) {
        errs.walkinPhone = 'Phone must start with + or 0 and contain only numbers'
        }
    }

    if (!title.trim()) errs.title = 'Quote title is required'
    if (lineItems.length === 0) errs.lineItems = 'Add at least one line item'
    if (lineItems.some(l => !l.description.trim())) errs.lineItems = 'All line items must have a description'

    setFieldErrors(errs)
    return Object.keys(errs).length === 0
    }

  const handleSubmit = () => {
    setCreateError('')
    if (!validate()) return
    createQuote.mutate()
  }

  const addFromCatalogue = (item: CatalogueItem) => {
    setLineItems(prev => [...prev, {
      tempId: crypto.randomUUID(), catalogueItemId: item.id,
      description: item.name, unit: item.unit,
      quantity: 1, unitPrice: item.defaultPrice, vatRate: item.vatRate,
    }])
    setShowCatalog(false); setItemSearch('')
    // Clear line item error if they just added one
    setFieldErrors(e => { const n = { ...e }; delete n.lineItems; return n })
  }

  const addBlankLine = () => {
    setLineItems(prev => [...prev, { tempId: crypto.randomUUID(), description: '', unit: 'Each', quantity: 1, unitPrice: 0, vatRate: 15 }])
    setFieldErrors(e => { const n = { ...e }; delete n.lineItems; return n })
  }

  const updateLine = (tempId: string, field: keyof LineItem, value: string | number) =>
    setLineItems(prev => prev.map(l => l.tempId === tempId ? { ...l, [field]: value } : l))

  const removeLine = (tempId: string) =>
    setLineItems(prev => prev.filter(l => l.tempId !== tempId))

  const subtotal = lineItems.reduce((s, l) => s + l.quantity * l.unitPrice, 0)
  const vatTotal = lineItems.reduce((s, l) => s + l.quantity * l.unitPrice * (l.vatRate / 100), 0)
  const total = subtotal + vatTotal

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif", maxWidth: 960, margin: '0 auto' }}>

      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 28 }}>
        <button onClick={() => navigate('/quotes')}
          style={{ display: 'flex', alignItems: 'center', gap: 6, background: 'white', border: '1px solid #E2E8F0', borderRadius: 9, padding: '7px 12px', fontSize: 13, fontWeight: 600, color: '#374151', cursor: 'pointer' }}>
          <ArrowLeft size={15} /> Back
        </button>
        <div>
          <h1 style={{ fontSize: 22, fontWeight: 800, color: '#0F172A', margin: 0 }}>New Quote</h1>
          <p style={{ fontSize: 12, color: '#94A3B8', margin: 0 }}>Quotes expire 30 days after sending</p>
        </div>
      </div>

      {/* Global error banner */}
      {createError && (
        <div style={{ marginBottom: 20, padding: '12px 16px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 10, fontSize: 13, color: '#DC2626', display: 'flex', alignItems: 'center', gap: 10 }}>
          <AlertCircle size={16} color="#DC2626" style={{ flexShrink: 0 }} />
          <div><strong>Could not create quote:</strong> {createError}</div>
        </div>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 320px', gap: 20, alignItems: 'start' }}>

        {/* Left */}
        <div>

          {/* Quote details card */}
          <div style={{ background: 'white', border: '1px solid #E8EDF5', borderRadius: 16, padding: 24, marginBottom: 16, boxShadow: '0 1px 4px rgba(0,0,0,0.04)' }}>
            <p style={{ fontSize: 12, fontWeight: 700, color: '#0F172A', margin: '0 0 18px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Quote details</p>

            {/* Client type toggle */}
            <div style={{ marginBottom: 20 }}>
              <label style={{ display: 'block', fontSize: 12, fontWeight: 700, color: '#374151', marginBottom: 8, textTransform: 'uppercase', letterSpacing: '0.05em' }}>Client type</label>
              <div style={{ display: 'flex', gap: 8 }}>
                <button
                  onClick={() => { setClientType('existing'); setWalkinName(''); setWalkinEmail(''); setWalkinPhone('') }}
                  style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 7, padding: '10px', borderRadius: 10, border: clientType === 'existing' ? '2px solid #1B3A6B' : '1.5px solid #E2E8F0', background: clientType === 'existing' ? '#EFF6FF' : 'white', color: clientType === 'existing' ? '#1B3A6B' : '#64748B', fontWeight: 600, fontSize: 13, cursor: 'pointer' }}>
                  <Users size={15} /> Saved customer
                </button>
                <button
                  onClick={() => { setClientType('walkin'); setCustomerId('') }}
                  style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 7, padding: '10px', borderRadius: 10, border: clientType === 'walkin' ? '2px solid #1B3A6B' : '1.5px solid #E2E8F0', background: clientType === 'walkin' ? '#EFF6FF' : 'white', color: clientType === 'walkin' ? '#1B3A6B' : '#64748B', fontWeight: 600, fontSize: 13, cursor: 'pointer' }}>
                  <UserPlus size={15} /> Walk-in client
                </button>
              </div>
            </div>

            {/* Saved customer */}
            {clientType === 'existing' && (
              <Field label="Customer" required error={fieldErrors.customerId}>
                <div style={{ position: 'relative' }}>
                  <select style={fieldErrors.customerId ? { ...inpErr, paddingRight: 36, appearance: 'none' } : { ...inp, paddingRight: 36, appearance: 'none' }}
                    value={customerId} onChange={e => { setCustomerId(e.target.value); setFieldErrors(f => { const n = { ...f }; delete n.customerId; return n }) }}>
                    <option value="">Select a customer...</option>
                    {customers.map((c: Customer) => <option key={c.id} value={c.id}>{c.name}</option>)}
                  </select>
                  <ChevronDown size={15} color="#94A3B8" style={{ position: 'absolute', right: 12, top: '50%', transform: 'translateY(-50%)', pointerEvents: 'none' }} />
                </div>
              </Field>
            )}

            {/* Walk-in client */}
            {clientType === 'walkin' && (
              <div style={{ background: '#F8FAFC', borderRadius: 10, padding: 16, marginBottom: 18, border: '1px solid #E2E8F0' }}>
                <div style={{ fontSize: 12, fontWeight: 700, color: '#94A3B8', letterSpacing: '0.05em', marginBottom: 12 }}>WALK-IN CLIENT DETAILS</div>
                <Field label="Name" required error={fieldErrors.walkinName}>
                  <input style={fieldErrors.walkinName ? inpErr : inp} value={walkinName}
                    onChange={e => { setWalkinName(e.target.value); setFieldErrors(f => { const n = { ...f }; delete n.walkinName; return n }) }}
                    placeholder="e.g. John Smith" autoFocus />
                </Field>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                  <Field label="Email (optional)" error={fieldErrors.walkinEmail}>
                    <input style={fieldErrors.walkinEmail ? inpErr : inp}
                        value={walkinEmail} onChange={e => {
                        setWalkinEmail(e.target.value)
                        setFieldErrors(f => { const n = { ...f }; delete n.walkinEmail; return n })
                        }}
                        placeholder="john@example.com" type="email" />
                    </Field>
                    <Field label="Phone (optional)" error={fieldErrors.walkinPhone}>
                    <input style={fieldErrors.walkinPhone ? inpErr : inp}
                        value={walkinPhone} onChange={e => {
                        setWalkinPhone(e.target.value)
                        setFieldErrors(f => { const n = { ...f }; delete n.walkinPhone; return n })
                        }}
                        placeholder="+27 82 000 0000" />
                    </Field>
                </div>
                <p style={{ fontSize: 12, color: '#94A3B8', margin: 0 }}>
                  Walk-in quotes are not linked to a CRM record. You can save the client later if needed.
                </p>
              </div>
            )}

            <Field label="Quote title" required error={fieldErrors.title}>
              <input style={fieldErrors.title ? inpErr : inp} value={title}
                onChange={e => { setTitle(e.target.value); setFieldErrors(f => { const n = { ...f }; delete n.title; return n }) }}
                placeholder="e.g. Dozer hire for Johannesburg site" />
            </Field>

            <Field label="Notes (optional)" hint="Payment terms, delivery conditions, etc.">
              <textarea style={{ ...inp, minHeight: 80, resize: 'vertical' as const }}
                value={notes} onChange={e => setNotes(e.target.value)}
                placeholder="Additional notes or terms..." />
            </Field>
          </div>

          {/* Line Items card */}
          <div style={{ background: 'white', border: '1px solid #E8EDF5', borderRadius: 16, overflow: 'hidden', boxShadow: '0 1px 4px rgba(0,0,0,0.04)' }}>
            <div style={{ padding: '16px 24px', borderBottom: '1px solid #F1F5F9', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <div>
                <p style={{ fontSize: 12, fontWeight: 700, color: '#0F172A', margin: 0, textTransform: 'uppercase', letterSpacing: '0.05em' }}>Line items</p>
                {fieldErrors.lineItems && (
                  <div style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, color: '#DC2626', marginTop: 3 }}>
                    <AlertCircle size={12} color="#DC2626" />{fieldErrors.lineItems}
                  </div>
                )}
              </div>
              <div style={{ display: 'flex', gap: 8 }} ref={catalogRef}>
                {/* Catalogue search — fixed width so it doesn't get cropped */}
                <div style={{ position: 'relative' }}>
                  <button onClick={() => setShowCatalog(s => !s)}
                    style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '7px 12px', background: '#EFF6FF', border: '1px solid #BFDBFE', borderRadius: 8, fontSize: 12, fontWeight: 600, color: '#1D4ED8', cursor: 'pointer', whiteSpace: 'nowrap' }}>
                    <Search size={13} /> Search catalogue
                  </button>

                  {showCatalog && (
                    <div style={{
                      position: 'fixed',    // fixed so it never gets clipped by overflow:hidden parents
                      zIndex: 9999,
                      width: 380,
                      background: 'white',
                      border: '1.5px solid #E2E8F0',
                      borderRadius: 14,
                      boxShadow: '0 12px 40px rgba(0,0,0,0.15)',
                      overflow: 'hidden',
                      // Position below the button — we use JS for exact placement
                    }} ref={el => {
                      if (el && catalogRef.current) {
                        const btn = catalogRef.current.querySelector('button') as HTMLElement
                        if (btn) {
                          const r = btn.getBoundingClientRect()
                          el.style.top  = `${r.bottom + 6}px`
                          el.style.left = `${Math.max(8, r.right - 380)}px`
                        }
                      }
                    }}>
                      <div style={{ padding: '10px 12px', borderBottom: '1px solid #F1F5F9' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8, background: '#F8FAFC', borderRadius: 8, padding: '8px 10px' }}>
                          <Search size={13} color="#94A3B8" />
                          <input value={itemSearch} onChange={e => setItemSearch(e.target.value)}
                            placeholder="Type to search..." autoFocus
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
                              <p style={{ fontSize: 11, color: '#94A3B8', margin: '2px 0 0' }}>
                                {item.categoryName ?? 'Uncategorised'} · {item.unit}
                              </p>
                            </div>
                            <p style={{ fontSize: 13, fontWeight: 700, color: '#1B3A6B', margin: 0, flexShrink: 0, marginLeft: 12 }}>
                              R {Number(item.defaultPrice).toLocaleString('en-ZA', { minimumFractionDigits: 2 })}
                            </p>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </div>

                <button onClick={addBlankLine}
                  style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '7px 12px', background: 'white', border: '1px solid #E2E8F0', borderRadius: 8, fontSize: 12, fontWeight: 600, color: '#374151', cursor: 'pointer', whiteSpace: 'nowrap' }}>
                  <Plus size={13} /> Add blank
                </button>
              </div>
            </div>

            {lineItems.length === 0 ? (
              <div style={{ padding: '48px 24px', textAlign: 'center' }}>
                <FileText size={32} color="#CBD5E1" style={{ marginBottom: 10 }} />
                <p style={{ fontSize: 14, color: '#94A3B8', margin: 0 }}>Search your catalogue or add a blank line to get started</p>
              </div>
            ) : (
              <div>
                <div style={{ display: 'grid', gridTemplateColumns: '3fr 80px 110px 70px 70px 32px', gap: 8, padding: '10px 20px', background: '#F8FAFC', borderBottom: '1px solid #F1F5F9' }}>
                  {['Description', 'Unit', 'Unit price', 'Qty', 'VAT %', ''].map(h => (
                    <p key={h} style={{ fontSize: 11, fontWeight: 700, color: '#94A3B8', margin: 0, textTransform: 'uppercase', letterSpacing: '0.04em' }}>{h}</p>
                  ))}
                </div>
                {lineItems.map((li, i) => (
                  <div key={li.tempId}
                    style={{ display: 'grid', gridTemplateColumns: '3fr 80px 110px 70px 70px 32px', gap: 8, padding: '10px 20px', borderTop: i === 0 ? 'none' : '1px solid #F8FAFC', alignItems: 'center' }}>
                    <input value={li.description} onChange={e => updateLine(li.tempId, 'description', e.target.value)}
                      placeholder="Item description"
                      style={{ ...inp, fontSize: 13, padding: '7px 10px', borderColor: !li.description.trim() && fieldErrors.lineItems ? '#DC2626' : '#E2E8F0' }} />
                    <input value={li.unit} onChange={e => updateLine(li.tempId, 'unit', e.target.value)}
                      style={{ ...inp, fontSize: 13, padding: '7px 10px' }} />
                    <input type="number" value={li.unitPrice}
                      onChange={e => updateLine(li.tempId, 'unitPrice', parseFloat(e.target.value) || 0)}
                      style={{ ...inp, fontSize: 13, padding: '7px 10px' }} />
                    <input type="number" value={li.quantity}
                      onChange={e => updateLine(li.tempId, 'quantity', parseFloat(e.target.value) || 0)}
                      style={{ ...inp, fontSize: 13, padding: '7px 10px' }} />
                    <input type="number" value={li.vatRate}
                      onChange={e => updateLine(li.tempId, 'vatRate', parseFloat(e.target.value) || 0)}
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
            <p style={{ fontSize: 12, fontWeight: 700, color: '#0F172A', margin: '0 0 18px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Summary</p>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 16 }}>
              {[['Subtotal', subtotal], ['VAT', vatTotal]].map(([l, v]) => (
                <div key={l as string} style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <span style={{ fontSize: 13, color: '#64748B' }}>{l as string}</span>
                  <span style={{ fontSize: 13, fontWeight: 600, color: '#0F172A' }}>
                    R {(v as number).toLocaleString('en-ZA', { minimumFractionDigits: 2 })}
                  </span>
                </div>
              ))}
              <div style={{ height: 1, background: '#F1F5F9', margin: '4px 0' }} />
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ fontSize: 14, fontWeight: 700, color: '#0F172A' }}>Total</span>
                <span style={{ fontSize: 20, fontWeight: 800, color: '#1B3A6B' }}>
                  R {total.toLocaleString('en-ZA', { minimumFractionDigits: 2 })}
                </span>
              </div>
            </div>

            {/* Line item count */}
            {lineItems.length > 0 && (
              <div style={{ background: '#F8FAFC', borderRadius: 8, padding: '8px 12px', marginBottom: 14, fontSize: 12, color: '#64748B' }}>
                {lineItems.length} line item{lineItems.length !== 1 ? 's' : ''} · will be saved as <strong>DRAFT</strong>
              </div>
            )}

            <button onClick={handleSubmit} disabled={createQuote.isPending}
              style={{
                width: '100%', padding: '12px', border: 'none', borderRadius: 10,
                background: createQuote.isPending ? '#93A8C9' : '#1B3A6B',
                color: 'white', fontSize: 14, fontWeight: 700,
                cursor: createQuote.isPending ? 'not-allowed' : 'pointer',
              }}>
              {createQuote.isPending ? 'Creating...' : 'Create quote'}
            </button>

            {/* Inline hints for missing fields */}
            <div style={{ marginTop: 10 }}>
              {fieldErrors.customerId && <p style={{ fontSize: 11, color: '#DC2626', margin: '2px 0', display: 'flex', alignItems: 'center', gap: 4 }}><AlertCircle size={11} />{fieldErrors.customerId}</p>}
              {fieldErrors.walkinName  && <p style={{ fontSize: 11, color: '#DC2626', margin: '2px 0', display: 'flex', alignItems: 'center', gap: 4 }}><AlertCircle size={11} />{fieldErrors.walkinName}</p>}
              {fieldErrors.title       && <p style={{ fontSize: 11, color: '#DC2626', margin: '2px 0', display: 'flex', alignItems: 'center', gap: 4 }}><AlertCircle size={11} />{fieldErrors.title}</p>}
              {fieldErrors.lineItems   && <p style={{ fontSize: 11, color: '#DC2626', margin: '2px 0', display: 'flex', alignItems: 'center', gap: 4 }}><AlertCircle size={11} />{fieldErrors.lineItems}</p>}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}