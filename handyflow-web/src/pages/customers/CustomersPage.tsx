// src/pages/customers/CustomersPage.tsx
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Plus, Search, Trash2, Eye, X, Pencil, Mail, Phone, MapPin, Hash,
  AlertTriangle, AlertCircle, Users, ChevronLeft, ChevronRight,
} from 'lucide-react'
import { apiClient } from '../../api/client'
import type { Customer, CreateCustomerRequest } from '../../types/crm.types'

const SA_PROVINCES = [
  'Eastern Cape', 'Free State', 'Gauteng', 'KwaZulu-Natal',
  'Limpopo', 'Mpumalanga', 'North West', 'Northern Cape', 'Western Cape',
]

const EMPTY_FORM = {
  name: '', email: '', phone: '', taxNumber: '',
  street: '', suburb: '', city: '', province: '', postalCode: '', notes: '',
}
const EMPTY_ERRORS: Record<string, string> = {}
const PAGE_SIZE = 10

// Shape of what Spring's Page<T> returns
interface SpringPage {
  content: Customer[]
  totalElements: number
  totalPages: number
  number: number       // current page (0-based)
}

export function CustomersPage() {
  const qc = useQueryClient()

  // ── UI state ───────────────────────────────────────────────────────────────
  const [search, setSearch]             = useState('')
  const [page, setPage]                 = useState(0)
  const [showAdd, setShowAdd]           = useState(false)
  const [viewCustomer, setView]         = useState<Customer | null>(null)
  const [editCustomer, setEdit]         = useState<Customer | null>(null)
  const [form, setForm]                 = useState(EMPTY_FORM)
  const [editForm, setEditForm]         = useState(EMPTY_FORM)
  const [error, setError]               = useState('')
  const [deleteTarget, setDeleteTarget] = useState<Customer | null>(null)
  const [deleteError, setDeleteError]   = useState('')
  const [fieldErrors, setFieldErrors]         = useState<Record<string, string>>(EMPTY_ERRORS)
  const [editFieldErrors, setEditFieldErrors] = useState<Record<string, string>>(EMPTY_ERRORS)

  // Reset page to 0 whenever the search term changes
  const handleSearch = (value: string) => {
    setSearch(value)
    setPage(0)
  }

  // ── Helpers ────────────────────────────────────────────────────────────────
  const parseErrors = (e: any): { fieldMap: Record<string, string>; general: string } => {
    const data = e.response?.data
    if (data?.errors && Array.isArray(data.errors)) {
      const fieldMap: Record<string, string> = {}
      data.errors.forEach((err: any) => { fieldMap[err.field] = err.message })
      return { fieldMap, general: '' }
    }
    return { fieldMap: {}, general: data?.message ?? 'Something went wrong' }
  }

  const validateForm = (f: typeof EMPTY_FORM): Record<string, string> => {
    const errs: Record<string, string> = {}
    if (!f.name.trim()) errs.name = 'Company name is required'
    if (!f.email.trim()) {
      errs.email = 'Email address is required'
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(f.email)) {
      errs.email = 'Please enter a valid email address'
    }
    if (f.phone && !/^(\+|0)[\d\s\-]{7,}$/.test(f.phone)) {
      errs.phone = 'Phone must start with + or 0 and contain only numbers'
    }
    if (f.taxNumber && !/^\d+$/.test(f.taxNumber)) {
      errs.taxNumber = 'VAT number must contain digits only'
    }
    if (f.postalCode) {
      if (!/^\d+$/.test(f.postalCode))    errs.postalCode = 'Postal code must contain digits only'
      else if (f.postalCode.length > 5)   errs.postalCode = 'Postal code cannot exceed 5 digits'
    }
    return errs
  }

  // ── Data fetching ──────────────────────────────────────────────────────────
  // queryKey includes page and search so React Query re-fetches on either change
  const { data, isLoading, isError } = useQuery({
    queryKey: ['customers', search, page],
    queryFn: async () => {
      const params = new URLSearchParams({
        page: String(page),
        size: String(PAGE_SIZE),
        sort: 'name,asc',
      })
      if (search.trim()) params.set('search', search.trim())
      const res = await apiClient.get(`/api/v1/crm/customers?${params}`)
      
      const payload = res.data?.data ?? res.data

      if (payload?.content) return payload as SpringPage
      // res.data is ApiResponse<Page<CustomerResponse>>
      // Spring wraps content in res.data.data for your ApiResponse wrapper
      return { content: [], totalElements: 0, totalPages: 0, number: 0 } as SpringPage
    },
  })

  const customers     = data?.content      ?? []
  const totalPages    = data?.totalPages    ?? 0
  const totalElements = data?.totalElements ?? 0

  // ── Mutations ──────────────────────────────────────────────────────────────
  const createMutation = useMutation({
    mutationFn: (body: CreateCustomerRequest) => apiClient.post('/api/v1/crm/customers', body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['customers'] })
      setShowAdd(false); setForm(EMPTY_FORM); setError(''); setFieldErrors(EMPTY_ERRORS)
    },
    onError: (e: any) => {
      const { fieldMap, general } = parseErrors(e)
      setFieldErrors(fieldMap); setError(general)
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) =>
      apiClient.put(`/api/v1/crm/customers/${id}`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['customers'] })
      setEdit(null); setError(''); setEditFieldErrors(EMPTY_ERRORS)
    },
    onError: (e: any) => {
      const { fieldMap, general } = parseErrors(e)
      setEditFieldErrors(fieldMap); setError(general)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/crm/customers/${id}`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['customers'] })
      setDeleteTarget(null); setDeleteError('')
    },
    onError: (e: any) => {
      setDeleteError(e.response?.data?.message ?? 'Failed to delete customer')
    },
  })

  // ── Form handlers ──────────────────────────────────────────────────────────
  const openEdit = (c: Customer) => {
    setEdit(c)
    setEditForm({
      name: c.name ?? '', email: c.email ?? '', phone: c.phone ?? '',
      taxNumber: c.taxNumber ?? '', notes: c.notes ?? '',
      street: c.address?.street ?? '', suburb: c.address?.suburb ?? '',
      city: c.address?.city ?? '', province: c.address?.province ?? '',
      postalCode: c.address?.postalCode ?? '',
    })
    setError(''); setEditFieldErrors(EMPTY_ERRORS)
  }

  const submitCreate = () => {
    const clientErrors = validateForm(form)
    if (Object.keys(clientErrors).length > 0) { setFieldErrors(clientErrors); return }
    createMutation.mutate({
      name: form.name, email: form.email || undefined, phone: form.phone || undefined,
      taxNumber: form.taxNumber || undefined, notes: form.notes || undefined,
      address: { street: form.street, suburb: form.suburb, city: form.city, province: form.province, postalCode: form.postalCode },
    })
  }

  const submitEdit = () => {
    if (!editCustomer) return
    const clientErrors = validateForm(editForm)
    if (Object.keys(clientErrors).length > 0) { setEditFieldErrors(clientErrors); return }
    updateMutation.mutate({
      id: editCustomer.id,
      body: {
        name: editForm.name, email: editForm.email || undefined, phone: editForm.phone || undefined,
        taxNumber: editForm.taxNumber || undefined, notes: editForm.notes || undefined,
        address: { street: editForm.street, suburb: editForm.suburb, city: editForm.city, province: editForm.province, postalCode: editForm.postalCode },
      },
    })
  }

  // ── Derived display helpers ────────────────────────────────────────────────
  const initials = (name: string) => name.split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase()
  const addrLine = (c: Customer) => [c.address?.suburb, c.address?.city].filter(Boolean).join(', ')

  // Pagination: always show first, last, current ± 1; collapse the rest to ellipsis
  const pageNumbers = (): (number | 'ellipsis')[] => {
    if (totalPages <= 7) return Array.from({ length: totalPages }, (_, i) => i)
    const items: (number | 'ellipsis')[] = [0]
    if (page > 2)              items.push('ellipsis')
    for (let i = Math.max(1, page - 1); i <= Math.min(totalPages - 2, page + 1); i++) items.push(i)
    if (page < totalPages - 3) items.push('ellipsis')
    items.push(totalPages - 1)
    return items
  }

  // ── Render ─────────────────────────────────────────────────────────────────
  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>

      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 24 }}>
        <div>
          <h1 style={{ fontSize: 24, fontWeight: 800, color: '#0F172A', margin: '0 0 4px' }}>Customers</h1>
          <p style={{ fontSize: 13, color: '#94A3B8', margin: 0 }}>
            Manage your client relationships · {totalElements} total
          </p>
        </div>
        <button
          onClick={() => { setShowAdd(true); setError(''); setFieldErrors(EMPTY_ERRORS) }}
          style={{ display: 'flex', alignItems: 'center', gap: 7, background: '#1B3A6B', color: 'white', border: 'none', borderRadius: 10, padding: '10px 18px', fontSize: 14, fontWeight: 600, cursor: 'pointer' }}>
          <Plus size={15} /> Add Customer
        </button>
      </div>

      {/* Search */}
      <div style={{ position: 'relative', marginBottom: 16, maxWidth: 360 }}>
        <Search size={15} style={{ position: 'absolute', left: 12, top: '50%', transform: 'translateY(-50%)', color: '#94A3B8' }} />
        <input
          value={search}
          onChange={e => handleSearch(e.target.value)}
          placeholder="Search customers..."
          style={{ width: '100%', paddingLeft: 36, paddingRight: 12, paddingTop: 10, paddingBottom: 10, border: '1.5px solid #E2E8F0', borderRadius: 10, fontSize: 14, boxSizing: 'border-box' as const, background: 'white' }}
        />
      </div>

      {/* Table */}
      <div style={{ background: 'white', border: '1px solid #E2E8F0', borderRadius: 14, overflow: 'hidden' }}>
        {isLoading ? (
          <div style={{ padding: 60, textAlign: 'center', color: '#94A3B8' }}>Loading customers...</div>
        ) : isError ? (
          <div style={{ padding: 60, textAlign: 'center' }}>
            <AlertCircle size={36} color="#DC2626" style={{ marginBottom: 12 }} />
            <div style={{ fontWeight: 600, color: '#DC2626', marginBottom: 4 }}>Failed to load customers</div>
            <div style={{ fontSize: 13, color: '#94A3B8' }}>Please refresh the page and try again.</div>
          </div>
        ) : customers.length === 0 ? (
          <div style={{ padding: 60, textAlign: 'center', color: '#94A3B8' }}>
            <Users size={36} color="#CBD5E1" style={{ marginBottom: 12 }} />
            <div style={{ fontWeight: 600, color: '#475569', marginBottom: 4 }}>
              {search ? 'No customers match your search' : 'No customers yet'}
            </div>
            <div style={{ fontSize: 13 }}>
              {search ? 'Try a different name or email.' : 'Add your first customer to start creating quotes.'}
            </div>
          </div>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid #F1F5F9', background: '#F8FAFC' }}>
                {['Customer', 'Email', 'Phone', 'Location', 'VAT Number', ''].map(h => (
                  <th key={h} style={{ textAlign: 'left', padding: '11px 16px', fontSize: 11, fontWeight: 700, color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.05em' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {customers.map(c => (
                <tr
                  key={c.id}
                  style={{ borderBottom: '1px solid #F8FAFC' }}
                  onMouseEnter={e => (e.currentTarget.style.background = '#F8FAFC')}
                  onMouseLeave={e => (e.currentTarget.style.background = 'white')}>
                  <td style={{ padding: '14px 16px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                      <div style={{ width: 36, height: 36, borderRadius: '50%', background: '#EFF6FF', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 700, color: '#1D4ED8', flexShrink: 0 }}>
                        {initials(c.name)}
                      </div>
                      <div>
                        <div style={{ fontWeight: 700, fontSize: 14, color: '#0F172A' }}>{c.name}</div>
                        {c.notes && <div style={{ fontSize: 11, color: '#94A3B8', marginTop: 1 }}>{c.notes.slice(0, 40)}{c.notes.length > 40 ? '…' : ''}</div>}
                      </div>
                    </div>
                  </td>
                  <td style={{ padding: '14px 16px', fontSize: 13, color: '#64748B' }}>{c.email || '—'}</td>
                  <td style={{ padding: '14px 16px', fontSize: 13, color: '#64748B' }}>{c.phone || '—'}</td>
                  <td style={{ padding: '14px 16px', fontSize: 13, color: '#64748B' }}>{addrLine(c) || '—'}</td>
                  <td style={{ padding: '14px 16px', fontSize: 13, color: '#64748B' }}>{c.taxNumber || '—'}</td>
                  <td style={{ padding: '14px 16px' }}>
                    <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
                      <button onClick={() => setView(c)} title="View"
                        style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '6px 12px', background: '#F8FAFC', border: '1px solid #E2E8F0', borderRadius: 7, fontSize: 12, color: '#475569', cursor: 'pointer', fontWeight: 500 }}>
                        <Eye size={13} /> View
                      </button>
                      <button onClick={() => openEdit(c)} title="Edit"
                        style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '6px 12px', background: '#EFF6FF', border: '1px solid #BFDBFE', borderRadius: 7, fontSize: 12, color: '#1D4ED8', cursor: 'pointer', fontWeight: 500 }}>
                        <Pencil size={13} /> Edit
                      </button>
                      <button
                        onClick={() => { setDeleteTarget(c); setDeleteError('') }}
                        title="Delete"
                        style={{ padding: '6px 8px', background: 'none', border: '1px solid #E2E8F0', borderRadius: 7, cursor: 'pointer', color: '#94A3B8', display: 'flex' }}>
                        <Trash2 size={13} />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* ── Pagination ─────────────────────────────────────────────────── */}
      {totalPages > 1 && (
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: 16, padding: '0 4px' }}>

          {/* Result count */}
          <span style={{ fontSize: 13, color: '#94A3B8' }}>
            Showing {page * PAGE_SIZE + 1}–{Math.min((page + 1) * PAGE_SIZE, totalElements)} of {totalElements}
          </span>

          {/* Page controls */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>

            {/* Previous button */}
            <button
              onClick={() => setPage(p => p - 1)}
              disabled={page === 0}
              style={{
                display: 'flex', alignItems: 'center', gap: 4,
                padding: '7px 12px', borderRadius: 8, fontSize: 13, fontWeight: 500,
                border: '1.5px solid #E2E8F0', background: 'white',
                color: page === 0 ? '#CBD5E1' : '#374151',
                cursor: page === 0 ? 'not-allowed' : 'pointer',
              }}>
              <ChevronLeft size={14} /> Prev
            </button>

            {/* Page number buttons */}
            {pageNumbers().map((item, idx) =>
              item === 'ellipsis' ? (
                <span key={`ellipsis-${idx}`} style={{ padding: '0 6px', color: '#94A3B8', fontSize: 13, userSelect: 'none' }}>…</span>
              ) : (
                <button
                  key={item}
                  onClick={() => setPage(item)}
                  style={{
                    width: 34, height: 34, borderRadius: 8, fontSize: 13, fontWeight: 600,
                    border: item === page ? 'none' : '1.5px solid #E2E8F0',
                    background: item === page ? '#1B3A6B' : 'white',
                    color: item === page ? 'white' : '#374151',
                    cursor: 'pointer',
                  }}>
                  {item + 1}
                </button>
              )
            )}

            {/* Next button */}
            <button
              onClick={() => setPage(p => p + 1)}
              disabled={page >= totalPages - 1}
              style={{
                display: 'flex', alignItems: 'center', gap: 4,
                padding: '7px 12px', borderRadius: 8, fontSize: 13, fontWeight: 500,
                border: '1.5px solid #E2E8F0', background: 'white',
                color: page >= totalPages - 1 ? '#CBD5E1' : '#374151',
                cursor: page >= totalPages - 1 ? 'not-allowed' : 'pointer',
              }}>
              Next <ChevronRight size={14} />
            </button>
          </div>
        </div>
      )}

      {/* ── View Modal ─────────────────────────────────────────────────── */}
      {viewCustomer && (
        <Modal title={viewCustomer.name} onClose={() => setView(null)}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginBottom: 20 }}>
            <div style={{ width: 52, height: 52, borderRadius: '50%', background: '#EFF6FF', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 18, fontWeight: 700, color: '#1D4ED8' }}>
              {initials(viewCustomer.name)}
            </div>
            <div>
              <div style={{ fontSize: 18, fontWeight: 700, color: '#0F172A' }}>{viewCustomer.name}</div>
              <div style={{ fontSize: 13, color: '#94A3B8' }}>
                Added {new Date(viewCustomer.createdAt).toLocaleDateString('en-ZA')}
              </div>
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
            {[
              { icon: Mail,  label: 'Email',      value: viewCustomer.email     },
              { icon: Phone, label: 'Phone',      value: viewCustomer.phone     },
              { icon: Hash,  label: 'VAT Number', value: viewCustomer.taxNumber },
            ].map(({ icon: Icon, label, value }) => value ? (
              <div key={label} style={{ padding: '12px 14px', background: '#F8FAFC', borderRadius: 10, border: '1px solid #F1F5F9' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 7, marginBottom: 4 }}>
                  <Icon size={13} color="#94A3B8" />
                  <span style={{ fontSize: 11, fontWeight: 600, color: '#94A3B8', textTransform: 'uppercase' as const, letterSpacing: '0.05em' }}>{label}</span>
                </div>
                <div style={{ fontSize: 14, color: '#0F172A', fontWeight: 500 }}>{value}</div>
              </div>
            ) : null)}
          </div>

          {viewCustomer.address && Object.values(viewCustomer.address).some(Boolean) && (
            <div style={{ marginTop: 14, padding: '12px 14px', background: '#F8FAFC', borderRadius: 10, border: '1px solid #F1F5F9' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 7, marginBottom: 6 }}>
                <MapPin size={13} color="#94A3B8" />
                <span style={{ fontSize: 11, fontWeight: 600, color: '#94A3B8', textTransform: 'uppercase' as const, letterSpacing: '0.05em' }}>Address</span>
              </div>
              <div style={{ fontSize: 14, color: '#0F172A', lineHeight: 1.6 }}>
                {[viewCustomer.address.street, viewCustomer.address.suburb, viewCustomer.address.city, viewCustomer.address.province, viewCustomer.address.postalCode].filter(Boolean).join(', ')}
              </div>
            </div>
          )}

          {viewCustomer.notes && (
            <div style={{ marginTop: 14, padding: '12px 14px', background: '#FFFBEB', borderRadius: 10, border: '1px solid #FEF3C7' }}>
              <div style={{ fontSize: 11, fontWeight: 600, color: '#92400E', marginBottom: 4 }}>NOTES</div>
              <div style={{ fontSize: 13, color: '#78350F', lineHeight: 1.6 }}>{viewCustomer.notes}</div>
            </div>
          )}

          <div style={{ display: 'flex', gap: 10, marginTop: 20 }}>
            <button
              onClick={() => { setView(null); openEdit(viewCustomer) }}
              style={{ flex: 1, padding: '10px', background: '#1B3A6B', color: 'white', border: 'none', borderRadius: 9, fontSize: 14, fontWeight: 600, cursor: 'pointer' }}>
              Edit Customer
            </button>
            <button
              onClick={() => setView(null)}
              style={{ padding: '10px 18px', border: '1px solid #E2E8F0', borderRadius: 9, background: 'white', fontSize: 14, cursor: 'pointer', color: '#374151' }}>
              Close
            </button>
          </div>
        </Modal>
      )}

      {/* ── Edit Modal ─────────────────────────────────────────────────── */}
      {editCustomer && (
        <Modal title={`Edit — ${editCustomer.name}`} onClose={() => { setEdit(null); setError(''); setEditFieldErrors(EMPTY_ERRORS) }}>
          <CustomerForm form={editForm} setForm={setEditForm} fieldErrors={editFieldErrors} />
          {error && (
            <div style={{ marginTop: 10, padding: '10px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626', display: 'flex', alignItems: 'center', gap: 8 }}>
              <AlertCircle size={15} color="#DC2626" style={{ flexShrink: 0 }} />
              {error}
            </div>
          )}
          <div style={{ display: 'flex', gap: 10, marginTop: 20 }}>
            <button
              onClick={() => { setEdit(null); setError(''); setEditFieldErrors(EMPTY_ERRORS) }}
              style={{ padding: '10px 18px', border: '1px solid #E2E8F0', borderRadius: 9, background: 'white', fontSize: 14, cursor: 'pointer', color: '#374151' }}>
              Cancel
            </button>
            <button
              onClick={submitEdit}
              disabled={updateMutation.isPending}
              style={{ flex: 1, padding: '10px', background: '#1B3A6B', color: 'white', border: 'none', borderRadius: 9, fontSize: 14, fontWeight: 600, cursor: 'pointer' }}>
              {updateMutation.isPending ? 'Saving...' : 'Save Changes'}
            </button>
          </div>
        </Modal>
      )}

      {/* ── Add Modal ──────────────────────────────────────────────────── */}
      {showAdd && (
        <Modal title="Add Customer" onClose={() => { setShowAdd(false); setForm(EMPTY_FORM); setError(''); setFieldErrors(EMPTY_ERRORS) }}>
          <CustomerForm form={form} setForm={setForm} fieldErrors={fieldErrors} />
          {error && (
            <div style={{ marginTop: 10, padding: '10px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626', display: 'flex', alignItems: 'center', gap: 8 }}>
              <AlertCircle size={15} color="#DC2626" style={{ flexShrink: 0 }} />
              {error}
            </div>
          )}
          <div style={{ display: 'flex', gap: 10, marginTop: 20 }}>
            <button
              onClick={() => { setShowAdd(false); setForm(EMPTY_FORM); setError(''); setFieldErrors(EMPTY_ERRORS) }}
              style={{ padding: '10px 18px', border: '1px solid #E2E8F0', borderRadius: 9, background: 'white', fontSize: 14, cursor: 'pointer', color: '#374151' }}>
              Cancel
            </button>
            <button
              onClick={submitCreate}
              disabled={createMutation.isPending}
              style={{ flex: 1, padding: '10px', background: '#1B3A6B', color: 'white', border: 'none', borderRadius: 9, fontSize: 14, fontWeight: 600, cursor: 'pointer' }}>
              {createMutation.isPending ? 'Creating...' : 'Create Customer'}
            </button>
          </div>
        </Modal>
      )}

      {/* ── Delete Modal ───────────────────────────────────────────────── */}
      {deleteTarget && (
        <DeleteConfirmModal
          customer={deleteTarget}
          isPending={deleteMutation.isPending}
          error={deleteError}
          onConfirm={() => deleteMutation.mutate(deleteTarget.id)}
          onCancel={() => { setDeleteTarget(null); setDeleteError('') }}
        />
      )}
    </div>
  )
}

// ── CustomerForm ──────────────────────────────────────────────────────────────
function CustomerForm({
  form, setForm, fieldErrors = {},
}: {
  form: any; setForm: (f: any) => void; fieldErrors?: Record<string, string>
}) {
  const f = (k: string, v: string) => setForm((p: any) => ({ ...p, [k]: v }))

  const handlePhone  = (v: string) => f('phone',     v.replace(/[^\d\s\-+]/g, ''))
  const handleVat    = (v: string) => f('taxNumber',  v.replace(/\D/g, ''))
  const handlePostal = (v: string) => f('postalCode', v.replace(/\D/g, '').slice(0, 5))

  const inputStyle = (key: string): React.CSSProperties => ({
    ...inp,
    ...(fieldErrors[key] ? { borderColor: '#DC2626', background: '#FFF5F5' } : {}),
  })

  const selectStyle = (key: string): React.CSSProperties => ({
    ...inp,
    appearance: 'none' as const,
    backgroundImage: `url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%2394A3B8' stroke-width='2'%3E%3Cpath d='M6 9l6 6 6-6'/%3E%3C/svg%3E")`,
    backgroundRepeat: 'no-repeat',
    backgroundPosition: 'right 12px center',
    paddingRight: 36, cursor: 'pointer',
    ...(fieldErrors[key] ? { borderColor: '#DC2626', background: '#FFF5F5' } : {}),
  })

  const FieldError = ({ name }: { name: string }) =>
    fieldErrors[name] ? (
      <div style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, color: '#DC2626', marginTop: 4 }}>
        <AlertCircle size={12} color="#DC2626" />{fieldErrors[name]}
      </div>
    ) : null

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>

        <div style={{ gridColumn: '1 / -1' }}>
          <FLabel>Company Name *</FLabel>
          <input value={form.name} onChange={e => f('name', e.target.value)} placeholder="Tau Mining (Pty) Ltd" style={inputStyle('name')} autoFocus />
          <FieldError name="name" />
        </div>

        <div>
          <FLabel>Email *</FLabel>
          <input type="email" value={form.email} onChange={e => f('email', e.target.value)} placeholder="contact@company.co.za" style={inputStyle('email')} />
          <FieldError name="email" />
        </div>

        <div>
          <FLabel>Phone</FLabel>
          <input value={form.phone} onChange={e => handlePhone(e.target.value)} placeholder="+27 82 341 5567 or 082 341 5567" style={inputStyle('phone')} />
          <FieldError name="phone" />
        </div>

        <div>
          <FLabel>VAT Number</FLabel>
          <input value={form.taxNumber} onChange={e => handleVat(e.target.value)} placeholder="4198765432" inputMode="numeric" style={inputStyle('taxNumber')} />
          <FieldError name="taxNumber" />
        </div>

        <div>
          <FLabel>Notes</FLabel>
          <input value={form.notes} onChange={e => f('notes', e.target.value)} placeholder="Key account..." style={inputStyle('notes')} />
          <FieldError name="notes" />
        </div>
      </div>

      <div style={{ borderTop: '1px solid #F1F5F9', paddingTop: 14 }}>
        <div style={{ fontSize: 11, fontWeight: 700, color: '#94A3B8', letterSpacing: '0.05em', marginBottom: 10 }}>ADDRESS</div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>

          <div style={{ gridColumn: '1 / -1' }}>
            <FLabel>Street</FLabel>
            <input value={form.street} onChange={e => f('street', e.target.value)} placeholder="45 Mine Road" style={inputStyle('street')} />
            <FieldError name="street" />
          </div>

          <div>
            <FLabel>Suburb</FLabel>
            <input value={form.suburb} onChange={e => f('suburb', e.target.value)} placeholder="Carletonville" style={inputStyle('suburb')} />
            <FieldError name="suburb" />
          </div>

          <div>
            <FLabel>City</FLabel>
            <input value={form.city} onChange={e => f('city', e.target.value)} placeholder="Merafong" style={inputStyle('city')} />
            <FieldError name="city" />
          </div>

          <div>
            <FLabel>Province</FLabel>
            <select value={form.province} onChange={e => f('province', e.target.value)} style={selectStyle('province')}>
              <option value="">Select province...</option>
              {SA_PROVINCES.map(p => <option key={p} value={p}>{p}</option>)}
            </select>
            <FieldError name="province" />
          </div>

          <div>
            <FLabel>Postal Code</FLabel>
            <input value={form.postalCode} onChange={e => handlePostal(e.target.value)} placeholder="2499" inputMode="numeric" maxLength={5} style={inputStyle('postalCode')} />
            <FieldError name="postalCode" />
          </div>
        </div>
      </div>
    </div>
  )
}

// ── Modal wrapper ─────────────────────────────────────────────────────────────
function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
      <div style={{ background: 'white', borderRadius: 16, padding: 28, width: 560, maxHeight: '88vh', overflowY: 'auto', boxShadow: '0 20px 60px rgba(0,0,0,0.2)' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 22 }}>
          <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: '#0F172A' }}>{title}</h3>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', display: 'flex' }}><X size={20} /></button>
        </div>
        {children}
      </div>
    </div>
  )
}

// ── Delete confirmation modal ─────────────────────────────────────────────────
function DeleteConfirmModal({
  customer, isPending, error, onConfirm, onCancel,
}: {
  customer: Customer; isPending: boolean; error: string; onConfirm: () => void; onCancel: () => void
}) {
  const initials = (name: string) => name.split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase()

  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, backdropFilter: 'blur(2px)' }}>
      <div style={{ background: 'white', borderRadius: 18, padding: 32, width: 420, boxShadow: '0 24px 64px rgba(0,0,0,0.18)', display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center' }}>

        <div style={{ width: 56, height: 56, borderRadius: '50%', background: '#FFF7ED', border: '2px solid #FED7AA', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 16 }}>
          <AlertTriangle size={24} color="#EA580C" strokeWidth={2} />
        </div>

        <h3 style={{ margin: '0 0 6px', fontSize: 18, fontWeight: 700, color: '#0F172A' }}>Delete Customer?</h3>

        <div style={{ display: 'flex', alignItems: 'center', gap: 10, background: '#EFF6FF', border: '2px solid #BFDBFE', borderRadius: 40, padding: '8px 16px', margin: '12px 0' }}>
          <div style={{ width: 30, height: 30, borderRadius: '50%', background: '#DBEAFE', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 11, fontWeight: 700, color: '#1D4ED8', flexShrink: 0 }}>
            {initials(customer.name)}
          </div>
          <span style={{ fontSize: 14, fontWeight: 600, color: '#0F172A' }}>{customer.name}</span>
        </div>

        <p style={{ margin: '0 0 16px', fontSize: 13, color: '#64748B', lineHeight: 1.6 }}>
          This will permanently remove this customer and all associated data.
          <br />This action <strong>cannot be undone</strong>.
        </p>

        {error && (
          <div style={{ width: '100%', marginBottom: 16, padding: '10px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626', display: 'flex', alignItems: 'center', gap: 8 }}>
            <AlertCircle size={15} color="#DC2626" style={{ flexShrink: 0 }} />{error}
          </div>
        )}

        <div style={{ display: 'flex', gap: 10, width: '100%' }}>
          <button onClick={onCancel} disabled={isPending}
            style={{ flex: 1, padding: '11px', border: '1.5px solid #E2E8F0', borderRadius: 10, background: 'white', fontSize: 14, fontWeight: 600, cursor: isPending ? 'not-allowed' : 'pointer', color: '#374151' }}>
            Cancel
          </button>
          <button onClick={onConfirm} disabled={isPending}
            style={{ flex: 1, padding: '11px', border: 'none', borderRadius: 10, background: isPending ? '#93A8C9' : '#1B3A6B', color: 'white', fontSize: 14, fontWeight: 700, cursor: isPending ? 'not-allowed' : 'pointer', transition: 'background 0.15s' }}>
            {isPending ? 'Deleting...' : 'Yes, Delete'}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Shared styles ─────────────────────────────────────────────────────────────
function FLabel({ children }: { children: React.ReactNode }) {
  return <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 5 }}>{children}</label>
}

const inp: React.CSSProperties = {
  width: '100%', padding: '10px 12px', border: '1.5px solid #E2E8F0',
  borderRadius: 9, fontSize: 14, boxSizing: 'border-box' as const, background: 'white',
}