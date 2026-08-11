// src/pages/customers/CustomersPage.tsx
import { useState, useCallback, useEffect, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Plus, Search, Trash2, Eye, Pencil, Mail, Phone, MapPin, Hash,
  AlertTriangle, AlertCircle, Users, ChevronLeft, ChevronRight, ChevronDown,
  RotateCcw, Clock, Tag, X, FileText, CheckCircle2, Ban, TrendingDown, Check, Upload, FileDown,
} from 'lucide-react'
import { apiClient } from '../../api/client'
import type {
  Customer, CustomerActivity, CreateCustomerRequest, UpdateCustomerRequest,
  SpringPage, CustomerType, CustomerStatus,
} from '../../types/crm.types'
import styles from './CustomersPage.module.css'
import { ExportButton }       from './ExportButton'
import { ImportModal }        from './ImportModal'
import { Customer360Panel }   from './Customer360Panel'
import { ConsentPanel }       from './ConsentPanel'
import { StageSelector }      from './StageSelector'
import { FollowUpPanel }      from './FollowUpPanel'
import { CommunicationPanel } from './CommunicationPanel'

// ─────────────────────────────────────────────────────────────────────────────
// Predefined tag catalogue
// WHY predefined and not free-text?
// Free-text creates "vip", "VIP", "Vip", "viip" — four versions of the
// same concept.  A fixed catalogue enforces consistency.  Typing in the
// picker filters the list (fast UX) but cannot create new values (no typos).
// To add a new tag option, add it here — one place, propagates everywhere.
// ─────────────────────────────────────────────────────────────────────────────

interface TagDefinition {
  value: string
  label: string
  color: string   // dot colour in the picker
}

const TAG_CATALOGUE: TagDefinition[] = [
  { value: 'vip',              label: 'VIP',              color: '#7C3AED' },
  { value: 'key-account',      label: 'Key Account',      color: '#1D4ED8' },
  { value: 'enterprise',       label: 'Enterprise',       color: '#0891B2' },
  { value: 'new-lead',         label: 'New Lead',         color: '#16A34A' },
  { value: 'at-risk',          label: 'At Risk',          color: '#DC2626' },
  { value: 'overdue',          label: 'Overdue',          color: '#EA580C' },
  { value: 'bad-debt',         label: 'Bad Debt',         color: '#991B1B' },
  { value: 'blocked',          label: 'Blocked',          color: '#6B7280' },
  { value: 'follow-up',        label: 'Follow Up',        color: '#B45309' },
  { value: 'seasonal',         label: 'Seasonal',         color: '#0D9488' },
  { value: 'trade-show',       label: 'Trade Show',       color: '#7C3AED' },
  { value: 'agri',             label: 'Agriculture',      color: '#15803D' },
  { value: 'mining',           label: 'Mining',           color: '#92400E' },
  { value: 'construction',     label: 'Construction',     color: '#B45309' },
  { value: 'retail',           label: 'Retail',           color: '#0369A1' },
  { value: 'manufacturing',    label: 'Manufacturing',    color: '#4338CA' },
  { value: 'steel',            label: 'Steel',            color: '#374151' },
  // Common freeform tags that may already exist in your DB:
  { value: 'jse-listed',       label: 'JSE Listed',       color: '#1D4ED8' },
  { value: 'annual-contract',  label: 'Annual Contract',  color: '#0891B2' },
  { value: 'preferred',        label: 'Preferred',        color: '#7C3AED' },
  { value: 'new',              label: 'New',              color: '#16A34A' },
  { value: 'inactive',         label: 'Inactive',         color: '#6B7280' },
  { value: 'prospect',         label: 'Prospect',         color: '#B45309' },
  { value: 'referral',         label: 'Referral',         color: '#0D9488' },
  { value: 'government',       label: 'Government',       color: '#374151' },
  { value: 'ngo',              label: 'NGO',              color: '#15803D' },
]

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

const SA_PROVINCES = [
  'Eastern Cape', 'Free State', 'Gauteng', 'KwaZulu-Natal',
  'Limpopo', 'Mpumalanga', 'North West', 'Northern Cape', 'Western Cape',
]

const PAGE_SIZE = 10

const EMPTY_FORM = {
  name: '', email: '', phone: '', taxNumber: '',
  street: '', suburb: '', city: '', province: '', postalCode: '',
  notes: '', customerType: 'CUSTOMER' as CustomerType,
  status: 'ACTIVE' as CustomerStatus,
}

// ─────────────────────────────────────────────────────────────────────────────
// Display config maps
// ─────────────────────────────────────────────────────────────────────────────

const STATUS_CONFIG: Record<CustomerStatus, { label: string; className: string; Icon: React.FC<any> }> = {
  ACTIVE:   { label: 'Active',   className: styles.badgeActive,   Icon: CheckCircle2 },
  INACTIVE: { label: 'Inactive', className: styles.badgeInactive, Icon: TrendingDown },
  BLOCKED:  { label: 'Blocked',  className: styles.badgeBlocked,  Icon: Ban          },
}

const TYPE_CONFIG: Record<CustomerType, { label: string; className: string }> = {
  CUSTOMER: { label: 'Customer', className: styles.typeCustomer },
  LEAD:     { label: 'Lead',     className: styles.typeLead     },
}

const ACTIVITY_LABELS: Record<string, string> = {
  CREATED:        'Customer created',
  UPDATED:        'Details updated',
  DELETED:        'Customer deleted',
  RESTORED:       'Customer restored',
  STATUS_CHANGED: 'Status changed',
  TAG_ADDED:      'Tag added',
  TAG_REMOVED:    'Tag removed',
  NOTE_ADDED:     'Note added',
  BOOKING_LINKED: 'Booking linked',
  INVOICE_LINKED: 'Invoice linked',
  QUOTE_LINKED:   'Quote linked',
}

// ─────────────────────────────────────────────────────────────────────────────
// Hooks
// ─────────────────────────────────────────────────────────────────────────────

function useDebounce<T>(value: T, delay = 300): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), delay)
    return () => clearTimeout(id)
  }, [value, delay])
  return debounced
}

function useFocusTrap(active: boolean) {
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!active || !ref.current) return
    const el = ref.current
    const focusable = el.querySelectorAll<HTMLElement>(
      'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
    )
    const first = focusable[0]
    const last  = focusable[focusable.length - 1]
    first?.focus()
    const handler = (e: KeyboardEvent) => {
      if (e.key !== 'Tab') return
      if (e.shiftKey) {
        if (document.activeElement === first) { e.preventDefault(); last?.focus() }
      } else {
        if (document.activeElement === last)  { e.preventDefault(); first?.focus() }
      }
    }
    el.addEventListener('keydown', handler)
    return () => el.removeEventListener('keydown', handler)
  }, [active])
  return ref
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

const initials = (name: string) =>
  name.split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase()

const addrLine = (c: Customer) =>
  [c.address?.suburb, c.address?.city].filter(Boolean).join(', ')

const formatDate = (iso: string) =>
  new Date(iso).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' })

const formatDateTime = (iso: string) =>
  new Date(iso).toLocaleString('en-ZA', {
    day: 'numeric', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })

function parseApiError(e: unknown): { fieldMap: Record<string, string>; general: string } {
  const data = (e as any)?.response?.data
  if (data?.errors && Array.isArray(data.errors)) {
    const fieldMap: Record<string, string> = {}
    data.errors.forEach((err: any) => { fieldMap[err.field] = err.message })
    return { fieldMap, general: '' }
  }
  return { fieldMap: {}, general: data?.message ?? 'Something went wrong. Please try again.' }
}

function validateForm(f: typeof EMPTY_FORM): Record<string, string> {
  const errs: Record<string, string> = {}
  if (!f.name.trim()) errs.name = 'Company name is required'
  if (f.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(f.email))
    errs.email = 'Enter a valid email address'
  if (f.phone && !/^(\+|0)[\d\s\-]{7,}$/.test(f.phone))
    errs.phone = 'Phone must start with + or 0'
  if (f.taxNumber && !/^\d{10}$/.test(f.taxNumber))
    errs.taxNumber = 'SA VAT number must be exactly 10 digits'
  if (f.postalCode && !/^\d{4}$/.test(f.postalCode))
    errs.postalCode = 'SA postal code must be exactly 4 digits'
  return errs
}

const tagDef = (value: string): TagDefinition =>
  TAG_CATALOGUE.find(t => t.value === value) ?? { value, label: value, color: '#94A3B8' }

// ─────────────────────────────────────────────────────────────────────────────
// TagPicker — dropdown of predefined options, filterable by typing
// ─────────────────────────────────────────────────────────────────────────────

function TagPicker({
  currentTags,
  onAdd,
  onRemove,
}: {
  currentTags: string[]
  onAdd:       (tag: string) => void
  onRemove:    (tag: string) => void
}) {
  const [open, setOpen]         = useState(false)
  const [filter, setFilter]     = useState('')
  const wrapRef                 = useRef<HTMLDivElement>(null)
  const inputRef                = useRef<HTMLInputElement>(null)

  // Close on outside click
  useEffect(() => {
    if (!open) return
    const handler = (e: MouseEvent) => {
      if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) {
        setOpen(false); setFilter('')
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [open])

  const openPicker = () => {
    setOpen(true)
    // Focus the search input after render
    setTimeout(() => inputRef.current?.focus(), 0)
  }

  const toggle = (value: string) => {
    if (currentTags.includes(value)) {
      onRemove(value)
    } else {
      onAdd(value)
    }
  }

  const filtered = TAG_CATALOGUE.filter(t =>
    t.label.toLowerCase().includes(filter.toLowerCase()) ||
    t.value.toLowerCase().includes(filter.toLowerCase())
  )

  return (
    <div ref={wrapRef} className={styles.tagPickerWrap}>
      <button className={styles.tagAddBtn} onClick={openPicker} type="button">
        <Tag size={10} /> Add tag
      </button>

      {open && (
        <div className={styles.tagDropdown}>
          <input
            ref={inputRef}
            className={styles.tagSearch}
            value={filter}
            onChange={e => setFilter(e.target.value)}
            placeholder="Filter tags…"
            onKeyDown={e => { if (e.key === 'Escape') { setOpen(false); setFilter('') } }}
          />
          <div className={styles.tagOptions}>
            {filtered.length === 0 ? (
              <div className={styles.tagNoResults}>No matching tags</div>
            ) : filtered.map(t => {
              const selected = currentTags.includes(t.value)
              return (
                <button
                  key={t.value}
                  type="button"
                  className={`${styles.tagOption} ${selected ? styles.tagOptionSelected : ''}`}
                  onClick={() => toggle(t.value)}>
                  <span
                    className={styles.tagOptionDot}
                    style={{ background: t.color }}
                  />
                  {t.label}
                  {selected && <Check size={12} className={styles.tagOptionCheck} />}
                </button>
              )
            })}
          </div>
        </div>
      )}
    </div>
  )
}

// ─────────────────────────────────────────────────────────────────────────────
// Main Page
// ─────────────────────────────────────────────────────────────────────────────

type View  = 'active' | 'deleted'
type Modal = 'none' | 'view' | 'add' | 'edit' | 'delete' | 'timeline'

export function CustomersPage() {
  const qc = useQueryClient()

  const [activeView, setActiveView]     = useState<View>('active')
  const [modal, setModal]               = useState<Modal>('none')
  const [selectedCustomer, setSelected] = useState<Customer | null>(null)
  const [searchInput, setSearchInput]   = useState('')
  const [page, setPage]                 = useState(0)
  const debouncedSearch                 = useDebounce(searchInput, 300)
  const [form, setForm]                 = useState(EMPTY_FORM)
  const [fieldErrors, setFieldErrors]   = useState<Record<string, string>>({})
  const [generalError, setGeneralError] = useState('')
  const [noteText, setNoteText]         = useState('')
  const [showImport, setShowImport]     = useState(false)

  useEffect(() => { setPage(0) }, [debouncedSearch])

  const closeModal = useCallback(() => {
    setModal('none'); setForm(EMPTY_FORM)
    setFieldErrors({}); setGeneralError(''); setNoteText('')
  }, [])

  useEffect(() => {
    const handler = (e: KeyboardEvent) => { if (e.key === 'Escape') closeModal() }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [closeModal])

  // ── Queries ────────────────────────────────────────────────────────────────

  const { data: activeData, isLoading: activeLoading, isError: activeError } =
    useQuery<SpringPage<Customer>>({
      queryKey: ['customers', 'active', debouncedSearch, page],
      queryFn: async () => {
        const params = new URLSearchParams({
          page: String(page), size: String(PAGE_SIZE), sort: 'name,asc',
        })
        if (debouncedSearch.trim()) params.set('search', debouncedSearch.trim())
        const res = await apiClient.get(`/api/v1/crm/customers?${params}`)
        return res.data?.data ?? res.data
      },
      placeholderData: prev => prev,
    })

  const { data: deletedData, isLoading: deletedLoading, isError: deletedError } =
    useQuery<SpringPage<Customer>>({
      queryKey: ['customers', 'deleted'],
      queryFn: async () => {
        const res = await apiClient.get('/api/v1/crm/customers/deleted?size=50&sort=name,asc')
        return res.data?.data ?? res.data
      },
      enabled: activeView === 'deleted',
    })

  const { data: timelineData, isLoading: timelineLoading } =
    useQuery<SpringPage<CustomerActivity>>({
      queryKey: ['customers', 'timeline', selectedCustomer?.id],
      queryFn: async () => {
        const res = await apiClient.get(
          `/api/v1/crm/customers/${selectedCustomer!.id}/activities?size=30&sort=createdAt,desc`
        )
        return res.data?.data ?? res.data
      },
      enabled: modal === 'timeline' && !!selectedCustomer,
    })

  // ── Mutations ──────────────────────────────────────────────────────────────

  const invalidate = () => qc.invalidateQueries({ queryKey: ['customers'] })

  const createMutation = useMutation({
    mutationFn: (body: CreateCustomerRequest) => apiClient.post('/api/v1/crm/customers', body),
    onSuccess: () => { invalidate(); closeModal() },
    onError: (e) => {
      const { fieldMap, general } = parseApiError(e)
      setFieldErrors(fieldMap); setGeneralError(general)
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdateCustomerRequest }) =>
      apiClient.put(`/api/v1/crm/customers/${id}`, body),
    onSuccess: () => { invalidate(); closeModal() },
    onError: (e) => {
      const { fieldMap, general } = parseApiError(e)
      setFieldErrors(fieldMap); setGeneralError(general)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/crm/customers/${id}`),
    onSuccess: () => { invalidate(); closeModal() },
    onError: (e) => setGeneralError(parseApiError(e).general),
  })

  const restoreMutation = useMutation({
    mutationFn: (id: string) => apiClient.post(`/api/v1/crm/customers/${id}/restore`),
    onSuccess: () => invalidate(),
    onError: (e) => setGeneralError(parseApiError(e).general),
  })

  const addTagMutation = useMutation({
    mutationFn: ({ id, tag }: { id: string; tag: string }) =>
      apiClient.put(`/api/v1/crm/customers/${id}/tags/${encodeURIComponent(tag)}`),
    onSuccess: () => invalidate(),
  })

  const removeTagMutation = useMutation({
    mutationFn: ({ id, tag }: { id: string; tag: string }) =>
      apiClient.delete(`/api/v1/crm/customers/${id}/tags/${encodeURIComponent(tag)}`),
    onSuccess: () => invalidate(),
  })

  const addNoteMutation = useMutation({
    mutationFn: ({ id, note }: { id: string; note: string }) =>
      apiClient.post(`/api/v1/crm/customers/${id}/notes`, { note }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['customers', 'timeline'] })
      setNoteText('')
    },
    onError: (e) => setGeneralError(parseApiError(e).general),
  })

  // ── Form helpers ───────────────────────────────────────────────────────────

  const openAdd = () => {
    setForm(EMPTY_FORM); setFieldErrors({}); setGeneralError(''); setModal('add')
  }

  const openEdit = (c: Customer) => {
    setSelected(c)
    setForm({
      name: c.name ?? '', email: c.email ?? '', phone: c.phone ?? '',
      taxNumber: c.taxNumber ?? '', notes: c.notes ?? '',
      street: c.address?.street ?? '', suburb: c.address?.suburb ?? '',
      city: c.address?.city ?? '', province: c.address?.province ?? '',
      postalCode: c.address?.postalCode ?? '',
      customerType: c.customerType ?? 'CUSTOMER',
      status: c.status ?? 'ACTIVE',
    })
    setFieldErrors({}); setGeneralError(''); setModal('edit')
  }

  const f = (k: string, v: string) => setForm(p => ({ ...p, [k]: v }))

  const buildPayload = (frm: typeof EMPTY_FORM) => ({
    name:         frm.name,
    email:        frm.email        || undefined,
    phone:        frm.phone        || undefined,
    taxNumber:    frm.taxNumber    || undefined,
    notes:        frm.notes        || undefined,
    customerType: frm.customerType,
    address: {
      street:     frm.street     || undefined,
      suburb:     frm.suburb     || undefined,
      city:       frm.city       || undefined,
      province:   frm.province   || undefined,
      postalCode: frm.postalCode || undefined,
    },
  })

  const submitCreate = () => {
    const errs = validateForm(form)
    if (Object.keys(errs).length) { setFieldErrors(errs); return }
    createMutation.mutate(buildPayload(form))
  }

  const submitEdit = () => {
    if (!selectedCustomer) return
    const errs = validateForm(form)
    if (Object.keys(errs).length) { setFieldErrors(errs); return }
    updateMutation.mutate({
      id: selectedCustomer.id,
      body: { ...buildPayload(form), status: form.status as CustomerStatus },
    })
  }

  // ── Derived ────────────────────────────────────────────────────────────────

  const customers     = activeData?.content      ?? []
  const totalPages    = activeData?.totalPages    ?? 0
  const totalElements = activeData?.totalElements ?? 0
  const deletedList   = deletedData?.content      ?? []
  const timeline      = timelineData?.content     ?? []
  const isLoading     = activeView === 'active' ? activeLoading : deletedLoading
  const isError       = activeView === 'active' ? activeError   : deletedError

  const pageNumbers = (): (number | 'ellipsis')[] => {
    if (totalPages <= 7) return Array.from({ length: totalPages }, (_, i) => i)
    const items: (number | 'ellipsis')[] = [0]
    if (page > 2) items.push('ellipsis')
    for (let i = Math.max(1, page - 1); i <= Math.min(totalPages - 2, page + 1); i++) items.push(i)
    if (page < totalPages - 3) items.push('ellipsis')
    items.push(totalPages - 1)
    return items
  }

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <div className={styles.page}>

      {/* Header */}
      <div className={styles.header}>
        <div>
          <h1 className={styles.title}>Customers</h1>
          <p className={styles.subtitle}>
            Manage your client relationships
            {activeView === 'active' && totalElements > 0 && (
              <span className={styles.count}> · {totalElements} total</span>
            )}
          </p>
        </div>
        <div className={styles.headerRight}>
          <ExportButton />
          <button className={styles.btnOutline} onClick={() => setShowImport(true)}>
            <Upload size={14} /> Import CSV
          </button>
          <button className={styles.btnPrimary} onClick={openAdd}>
            <Plus size={15} /> Add Customer
          </button>
        </div>
      </div>

      {/* Tabs */}
      <div className={styles.tabs}>
        <button
          className={`${styles.tab} ${activeView === 'active' ? styles.tabActive : ''}`}
          onClick={() => { setActiveView('active'); setPage(0) }}>
          <Users size={14} /> Customers
        </button>
        <button
          className={`${styles.tab} ${activeView === 'deleted' ? styles.tabActive : ''}`}
          onClick={() => setActiveView('deleted')}>
          <Trash2 size={14} /> Deleted
          {deletedData && deletedData.totalElements > 0 && (
            <span className={styles.tabBadge}>{deletedData.totalElements}</span>
          )}
        </button>
      </div>

      {/* Search */}
      {activeView === 'active' && (
        <div className={styles.searchWrap}>
          <Search size={15} className={styles.searchIcon} />
          <input
            className={styles.searchInput}
            value={searchInput}
            onChange={e => setSearchInput(e.target.value)}
            placeholder="Search by name, email, phone or VAT number…"
            aria-label="Search customers"
          />
          {searchInput && (
            <button className={styles.searchClear} onClick={() => setSearchInput('')} aria-label="Clear search">
              <X size={14} />
            </button>
          )}
        </div>
      )}

      {/* Table */}
      <div className={styles.card}>
        {isLoading ? (
          <div className={styles.stateCenter}>
            <div className={styles.spinner} aria-label="Loading" />
            <p className={styles.stateText}>Loading customers…</p>
          </div>
        ) : isError ? (
          <div className={styles.stateCenter}>
            <AlertCircle size={36} className={styles.errorIcon} />
            <p className={styles.stateHeading}>Failed to load customers</p>
            <p className={styles.stateText}>Refresh the page to try again.</p>
          </div>
        ) : activeView === 'deleted' ? (
          <DeletedView
            customers={deletedList}
            restoring={restoreMutation.isPending}
            onRestore={id => restoreMutation.mutate(id)}
          />
        ) : customers.length === 0 ? (
          <div className={styles.stateCenter}>
            <Users size={40} className={styles.emptyIcon} />
            <p className={styles.stateHeading}>
              {debouncedSearch ? 'No customers match your search' : 'No customers yet'}
            </p>
            <p className={styles.stateText}>
              {debouncedSearch
                ? 'Try a different name, email, or VAT number.'
                : 'Add your first customer to start creating quotes and bookings.'}
            </p>
            {!debouncedSearch && (
              <button className={styles.btnPrimary} style={{ marginTop: 16 }} onClick={openAdd}>
                <Plus size={14} /> Add Customer
              </button>
            )}
          </div>
        ) : (
          <table className={styles.table}>
            <thead>
              <tr>
                {['Customer', 'Contact', 'Location', 'VAT Number', 'Status', ''].map(h => (
                  <th key={h} className={styles.th}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {customers.map(c => (
                <CustomerRow
                  key={c.id}
                  customer={c}
                  onView={() => { setSelected(c); setModal('view') }}
                  onEdit={() => openEdit(c)}
                  onDelete={() => { setSelected(c); setGeneralError(''); setModal('delete') }}
                  onTimeline={() => { setSelected(c); setModal('timeline') }}
                  onAddTag={tag => addTagMutation.mutate({ id: c.id, tag })}
                  onRemoveTag={tag => removeTagMutation.mutate({ id: c.id, tag })}
                />
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* Pagination */}
      {activeView === 'active' && totalPages > 1 && (
        <div className={styles.pagination}>
          <span className={styles.pageInfo}>
            Showing {page * PAGE_SIZE + 1}–{Math.min((page + 1) * PAGE_SIZE, totalElements)} of {totalElements}
          </span>
          <div className={styles.pageControls}>
            <button className={styles.pageBtn} onClick={() => setPage(p => p - 1)} disabled={page === 0} aria-label="Previous page">
              <ChevronLeft size={14} /> Prev
            </button>
            {pageNumbers().map((item, idx) =>
              item === 'ellipsis'
                ? <span key={`e${idx}`} className={styles.ellipsis}>…</span>
                : <button
                    key={item}
                    className={`${styles.pageNum} ${item === page ? styles.pageNumActive : ''}`}
                    onClick={() => setPage(item)}
                    aria-current={item === page ? 'page' : undefined}>
                    {item + 1}
                  </button>
            )}
            <button className={styles.pageBtn} onClick={() => setPage(p => p + 1)} disabled={page >= totalPages - 1} aria-label="Next page">
              Next <ChevronRight size={14} />
            </button>
          </div>
        </div>
      )}

      {/* ── Modals ── */}

      {modal === 'view' && selectedCustomer && (
        <ViewModal
          customer={selectedCustomer}
          onClose={closeModal}
          onEdit={() => openEdit(selectedCustomer)}
          onTimeline={() => { setModal('timeline') }}
          onAddTag={tag => addTagMutation.mutate({ id: selectedCustomer.id, tag })}
          onRemoveTag={tag => removeTagMutation.mutate({ id: selectedCustomer.id, tag })}
        />
      )}

      {(modal === 'add' || modal === 'edit') && (
        <CustomerFormModal
          mode={modal}
          customer={modal === 'edit' ? selectedCustomer ?? undefined : undefined}
          form={form}
          fieldErrors={fieldErrors}
          generalError={generalError}
          isPending={modal === 'add' ? createMutation.isPending : updateMutation.isPending}
          onSubmit={modal === 'add' ? submitCreate : submitEdit}
          onClose={closeModal}
          f={f}
        />
      )}

      {modal === 'delete' && selectedCustomer && (
        <DeleteModal
          customer={selectedCustomer}
          isPending={deleteMutation.isPending}
          error={generalError}
          onConfirm={() => deleteMutation.mutate(selectedCustomer.id)}
          onCancel={closeModal}
        />
      )}

      {modal === 'timeline' && selectedCustomer && (
        <TimelineModal
          customer={selectedCustomer}
          activities={timeline}
          isLoading={timelineLoading}
          noteText={noteText}
          onNoteChange={setNoteText}
          isAddingNote={addNoteMutation.isPending}
          onAddNote={() => {
            if (!noteText.trim()) return
            addNoteMutation.mutate({ id: selectedCustomer.id, note: noteText.trim() })
          }}
          onClose={closeModal}
        />
      )}

      {showImport && <ImportModal onClose={() => setShowImport(false)} />}
    </div>
  )
}

// ─────────────────────────────────────────────────────────────────────────────
// CustomerRow
// ─────────────────────────────────────────────────────────────────────────────

function CustomerRow({
  customer: c, onView, onEdit, onDelete, onTimeline, onAddTag, onRemoveTag,
}: {
  customer:    Customer
  onView:      () => void
  onEdit:      () => void
  onDelete:    () => void
  onTimeline:  () => void
  onAddTag:    (tag: string) => void
  onRemoveTag: (tag: string) => void
}) {
  const statusCfg = STATUS_CONFIG[c.status]
  const typeCfg   = TYPE_CONFIG[c.customerType]

  return (
    <tr className={styles.tr}>
      <td className={styles.td}>
        <div className={styles.customerCell}>
          <div className={`${styles.avatar} ${c.status === 'BLOCKED' ? styles.avatarBlocked : ''}`}>
            {initials(c.name)}
          </div>
          <div className={styles.customerInfo}>
            <div className={styles.customerName}>{c.name}</div>
            <div className={styles.customerMeta}>
              <span className={`${styles.typeBadge} ${typeCfg.className}`}>{typeCfg.label}</span>
              {c.tags.slice(0, 3).map(tag => {
                const def = tagDef(tag)
                return (
                  <span key={tag} className={styles.tag}>
                    <span style={{ width: 6, height: 6, borderRadius: '50%', background: def.color, flexShrink: 0, display: 'inline-block' }} />
                    {def.label}
                    <button
                      className={styles.tagRemove}
                      onClick={() => onRemoveTag(tag)}
                      aria-label={`Remove tag ${def.label}`}>
                      <X size={9} />
                    </button>
                  </span>
                )
              })}
              {c.tags.length > 3 && (
                <span className={styles.tagMore}>+{c.tags.length - 3}</span>
              )}
              <TagPicker
                currentTags={c.tags}
                onAdd={onAddTag}
                onRemove={onRemoveTag}
              />
            </div>
          </div>
        </div>
      </td>

      <td className={styles.td}>
        <div className={styles.contactCell}>
          {c.email && <span className={styles.contactLine}><Mail size={12} />{c.email}</span>}
          {c.phone && <span className={styles.contactLine}><Phone size={12} />{c.phone}</span>}
          {!c.email && !c.phone && <span className={styles.muted}>—</span>}
        </div>
      </td>

      <td className={styles.td}>
        <span className={styles.muted}>{addrLine(c) || '—'}</span>
      </td>

      <td className={styles.td}>
        <span className={styles.muted}>{c.taxNumber || '—'}</span>
      </td>

      <td className={styles.td}>
        <span className={`${styles.statusBadge} ${statusCfg.className}`}>
          <statusCfg.Icon size={11} />{statusCfg.label}
        </span>
      </td>

      <td className={styles.td}>
        <div className={styles.actions}>
          <button className={styles.btnGhost} onClick={onView} title="View details">
            <Eye size={13} /> View
          </button>
          <button className={styles.btnBlue} onClick={onEdit} title="Edit customer">
            <Pencil size={13} /> Edit
          </button>
          <button className={styles.btnTimeline} onClick={onTimeline} title="Activity timeline">
            <Clock size={13} />
          </button>
          <button className={styles.btnDanger} onClick={onDelete} title="Delete customer">
            <Trash2 size={13} />
          </button>
        </div>
      </td>
    </tr>
  )
}

// ─────────────────────────────────────────────────────────────────────────────
// DeletedView
// ─────────────────────────────────────────────────────────────────────────────

function DeletedView({ customers, restoring, onRestore }: {
  customers: Customer[]; restoring: boolean; onRestore: (id: string) => void
}) {
  if (customers.length === 0) {
    return (
      <div className={styles.stateCenter}>
        <CheckCircle2 size={36} className={styles.successIcon} />
        <p className={styles.stateHeading}>No deleted customers</p>
        <p className={styles.stateText}>All customers are active.</p>
      </div>
    )
  }
  return (
    <table className={styles.table}>
      <thead>
        <tr>
          {['Customer', 'Email', 'Deleted', ''].map(h => (
            <th key={h} className={styles.th}>{h}</th>
          ))}
        </tr>
      </thead>
      <tbody>
        {customers.map(c => (
          <tr key={c.id} className={`${styles.tr} ${styles.trDeleted}`}>
            <td className={styles.td}>
              <div className={styles.customerCell}>
                <div className={`${styles.avatar} ${styles.avatarDeleted}`}>{initials(c.name)}</div>
                <span className={styles.customerName} style={{ opacity: 0.6 }}>{c.name}</span>
              </div>
            </td>
            <td className={styles.td}><span className={styles.muted}>{c.email || '—'}</span></td>
            <td className={styles.td}><span className={styles.muted}>{formatDate(c.updatedAt)}</span></td>
            <td className={styles.td}>
              <button className={styles.btnRestore} disabled={restoring} onClick={() => onRestore(c.id)}>
                <RotateCcw size={13} /> Restore
              </button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

// ─────────────────────────────────────────────────────────────────────────────
// ViewModal
// ─────────────────────────────────────────────────────────────────────────────

function ViewModal({ customer: c, onClose, onEdit, onTimeline, onAddTag, onRemoveTag }: {
  customer:   Customer; onClose: () => void; onEdit: () => void
  onTimeline: () => void; onAddTag: (tag: string) => void; onRemoveTag: (tag: string) => void
}) {
  const trapRef   = useFocusTrap(true)
  const statusCfg = STATUS_CONFIG[c.status]
  const typeCfg   = TYPE_CONFIG[c.customerType]
  const [activeTab, setActiveTab] = useState<'overview' | 'followups' | 'communications' | 'consent'>('overview')

  const downloadProfilePdf = async (id: string, name: string) => {
    try {
      const res      = await apiClient.get(`/api/v1/crm/customers/${id}/profile.pdf`, { responseType: 'blob' })
      const filename = `customer-profile-${name.replace(/[^a-z0-9]/gi, '-').toLowerCase()}.pdf`
      const url      = URL.createObjectURL(new Blob([res.data], { type: 'application/pdf' }))
      const link     = document.createElement('a')
      link.href = url; link.download = filename
      document.body.appendChild(link); link.click()
      document.body.removeChild(link); URL.revokeObjectURL(url)
    } catch {
      // silent — PDF is non-critical enrichment
    }
  }

  // FIX: "the POPIA data-subject export isn't reachable from the UI" —
  // PopiaExportController implements exactly what POPIA Section 23
  // requires, but nothing in the UI called it; fulfilling a "what data do
  // you hold on me" request meant someone calling the API directly.
  // Deliberately NOT silent-fail like the profile PDF above — that one is
  // enrichment a user can live without noticing; this is a compliance
  // request a staff member is actively fulfilling for a customer, so a
  // failure needs to be visible, not swallowed.
  const [popiaError, setPopiaError] = useState<string | null>(null)
  const [popiaMenuOpen, setPopiaMenuOpen] = useState(false)
  const downloadPopiaExport = async (id: string, name: string, format: 'json' | 'pdf') => {
    setPopiaError(null); setPopiaMenuOpen(false)
    try {
      const path = format === 'pdf' ? `/api/v1/crm/customers/${id}/popia-export.pdf` : `/api/v1/crm/customers/${id}/popia-export`
      const res      = await apiClient.get(path, { responseType: 'blob' })
      const slug      = name.replace(/[^a-z0-9]/gi, '-').toLowerCase()
      const filename = `popia-export-${slug}-${new Date().toISOString().slice(0, 10)}.${format}`
      const mime     = format === 'pdf' ? 'application/pdf' : 'application/json'
      const url      = URL.createObjectURL(new Blob([res.data], { type: mime }))
      const link     = document.createElement('a')
      link.href = url; link.download = filename
      document.body.appendChild(link); link.click()
      document.body.removeChild(link); URL.revokeObjectURL(url)
    } catch (e: any) {
      setPopiaError(e?.response?.data?.message ?? 'Failed to generate POPIA export')
    }
  }

  return (
    <Overlay onClose={onClose}>
      <div ref={trapRef} className={`${styles.modal} ${styles.modalWide}`} role="dialog" aria-modal="true" aria-label={c.name}>

        <div className={styles.modalHeader}>
          <div className={styles.viewAvatarWrap}>
            <div className={styles.viewAvatar}>{initials(c.name)}</div>
            <div>
              <div className={styles.viewName}>{c.name}</div>
              <div className={styles.viewMeta}>
                <span className={`${styles.typeBadge} ${typeCfg.className}`}>{typeCfg.label}</span>
                <span className={`${styles.statusBadge} ${statusCfg.className}`}>
                  <statusCfg.Icon size={11} />{statusCfg.label}
                </span>
                <span className={styles.muted}>Added {formatDate(c.createdAt)}</span>
              </div>
            </div>
          </div>
          <button onClick={onClose} className={styles.closeBtn} aria-label="Close"><X size={20} /></button>
        </div>

        <StageSelector customerId={c.id} customerType={c.customerType} />

        {/* FIX: "view modal is getting long" — Consent/Follow-ups/
            Communications were all stacked inline (on top of the detail
            grid, tags, notes, and Customer 360), making the modal an
            ever-growing scroll. Activity Timeline already opened as its
            own separate view (onTimeline, unchanged below) — this applies
            that same decision consistently instead of leaving two
            different patterns (a separate view vs. inline collapsible
            panels) side by side. */}
        <div style={{ display: 'flex', gap: 4, borderBottom: '1px solid #E2E8F0', marginBottom: 16 }}>
          {([
            { key: 'overview', label: 'Overview' },
            { key: 'followups', label: 'Follow-ups' },
            { key: 'communications', label: 'Communications' },
            { key: 'consent', label: 'Consent' },
          ] as const).map(tab => (
            <button key={tab.key} onClick={() => setActiveTab(tab.key)}
              style={{
                padding: '8px 4px', marginRight: 16, background: 'none', border: 'none',
                borderBottom: activeTab === tab.key ? '2px solid #1D4ED8' : '2px solid transparent',
                fontSize: 13, fontWeight: activeTab === tab.key ? 700 : 500,
                color: activeTab === tab.key ? '#1D4ED8' : '#64748B',
                cursor: 'pointer', fontFamily: 'inherit',
              }}>
              {tab.label}
            </button>
          ))}
        </div>

        {activeTab === 'overview' && (
          <>
            <div className={styles.detailGrid}>
              {c.email     && <Detail icon={Mail}     label="Email"      value={c.email} />}
              {c.phone     && <Detail icon={Phone}    label="Phone"      value={c.phone} />}
              {c.taxNumber && <Detail icon={Hash}     label="VAT Number" value={c.taxNumber} />}
              {c.address && Object.values(c.address).some(Boolean) && (
                <Detail icon={MapPin} label="Address" fullWidth
                  value={[c.address.street, c.address.suburb, c.address.city,
                          c.address.province, c.address.postalCode].filter(Boolean).join(', ')}
                />
              )}
            </div>

            {/* Tags — same TagPicker used in the table row */}
            <div className={styles.section}>
              <div className={styles.sectionLabel}><Tag size={12} /> Tags</div>
              <div className={styles.tagList}>
                {c.tags.map(tag => {
                  const def = tagDef(tag)
                  return (
                    <span key={tag} className={styles.tag}>
                      <span style={{ width: 6, height: 6, borderRadius: '50%', background: def.color, flexShrink: 0, display: 'inline-block' }} />
                      {def.label}
                      <button className={styles.tagRemove} onClick={() => onRemoveTag(tag)} aria-label={`Remove ${def.label}`}>
                        <X size={9} />
                      </button>
                    </span>
                  )
                })}
                <TagPicker currentTags={c.tags} onAdd={onAddTag} onRemove={onRemoveTag} />
              </div>
            </div>

            {c.notes && (
              <div className={styles.notesBox}>
                <div className={styles.sectionLabel}><FileText size={12} /> Notes</div>
                <p className={styles.notesText}>{c.notes}</p>
              </div>
            )}

            {/* Customer 360 — linked bookings & invoice summary */}
            <Customer360Panel customerId={c.id} />
          </>
        )}

        {activeTab === 'followups' && <FollowUpPanel customerId={c.id} />}
        {activeTab === 'communications' && <CommunicationPanel customerId={c.id} />}
        {activeTab === 'consent' && <ConsentPanel customerId={c.id} />}

        <p className={styles.updatedLine}>Last updated {formatDateTime(c.updatedAt)}</p>

        {popiaError && (
          <div style={{
            display: 'flex', alignItems: 'flex-start', gap: 8,
            background: '#FEF2F2', border: '1px solid #FECACA',
            borderRadius: 8, padding: '8px 12px', fontSize: 12, color: '#DC2626',
            marginBottom: 12,
          }} role="alert">
            <AlertCircle size={13} style={{ flexShrink: 0, marginTop: 1 }} />
            <span style={{ flex: 1, lineHeight: 1.4 }}>{popiaError}</span>
          </div>
        )}

        <div className={styles.modalFooter}>
          <button className={styles.btnGhostSm} onClick={onTimeline}>
            <Clock size={14} /> Activity timeline
          </button>
          <button
            className={styles.btnGhostSm}
            onClick={() => downloadProfilePdf(c.id, c.name)}
            title="Download customer profile as PDF">
            <FileDown size={14} /> Profile PDF
          </button>
          <div style={{ position: 'relative' }}>
            <button
              className={styles.btnGhostSm}
              onClick={() => setPopiaMenuOpen(o => !o)}
              title="Download the full POPIA Section 23 data subject export"
              aria-haspopup="menu"
              aria-expanded={popiaMenuOpen}>
              <FileDown size={14} /> POPIA Export <ChevronDown size={12} />
            </button>
            {popiaMenuOpen && (
              <div role="menu" style={{
                position: 'absolute', bottom: 'calc(100% + 6px)', left: 0,
                background: 'white', border: '1px solid #E2E8F0', borderRadius: 10,
                boxShadow: '0 4px 16px rgba(0,0,0,.1)', minWidth: 160, zIndex: 200, overflow: 'hidden',
              }}>
                <button role="menuitem" onClick={() => downloadPopiaExport(c.id, c.name, 'pdf')}
                  style={{ display: 'block', width: '100%', padding: '9px 14px', background: 'none', border: 'none', cursor: 'pointer', textAlign: 'left', fontSize: 13, color: '#0F172A', borderBottom: '1px solid #F1F5F9' }}
                  onMouseEnter={e => (e.currentTarget.style.background = '#F8FAFC')}
                  onMouseLeave={e => (e.currentTarget.style.background = 'none')}>
                  PDF (readable document)
                </button>
                <button role="menuitem" onClick={() => downloadPopiaExport(c.id, c.name, 'json')}
                  style={{ display: 'block', width: '100%', padding: '9px 14px', background: 'none', border: 'none', cursor: 'pointer', textAlign: 'left', fontSize: 13, color: '#0F172A' }}
                  onMouseEnter={e => (e.currentTarget.style.background = '#F8FAFC')}
                  onMouseLeave={e => (e.currentTarget.style.background = 'none')}>
                  JSON (machine-readable)
                </button>
              </div>
            )}
          </div>
          <button className={styles.btnPrimary} onClick={onEdit}>
            <Pencil size={14} /> Edit Customer
          </button>
        </div>
      </div>
    </Overlay>
  )
}

// ─────────────────────────────────────────────────────────────────────────────
// CustomerFormModal (Add + Edit)
// ─────────────────────────────────────────────────────────────────────────────

function CustomerFormModal({
  mode, customer, form, fieldErrors, generalError, isPending, onSubmit, onClose, f,
}: {
  mode:         'add' | 'edit'
  customer?:    Customer
  form:         typeof EMPTY_FORM
  fieldErrors:  Record<string, string>
  generalError: string
  isPending:    boolean
  onSubmit:     () => void
  onClose:      () => void
  f:            (k: string, v: string) => void
}) {
  const trapRef = useFocusTrap(true)
  const title   = mode === 'add' ? 'Add Customer' : `Edit — ${customer?.name}`

  return (
    <Overlay onClose={onClose}>
      <div ref={trapRef} className={`${styles.modal} ${styles.modalWide}`} role="dialog" aria-modal="true" aria-label={title}>
        <div className={styles.modalHeader}>
          <h3 className={styles.modalTitle}>{title}</h3>
          <button onClick={onClose} className={styles.closeBtn} aria-label="Close"><X size={20} /></button>
        </div>

        <div className={styles.formGrid}>
          <div className={styles.formFieldFull}>
            <FLabel required>Company / Contact Name</FLabel>
            <input className={inputCls(fieldErrors, 'name')} value={form.name}
              onChange={e => f('name', e.target.value)} placeholder="Tau Mining (Pty) Ltd" autoFocus />
            <FError msg={fieldErrors.name} />
          </div>

          <div>
            <FLabel>Type</FLabel>
            <select className={styles.select} value={form.customerType}
              onChange={e => f('customerType', e.target.value)}>
              <option value="CUSTOMER">Customer</option>
              <option value="LEAD">Lead</option>
            </select>
          </div>

          {mode === 'edit' && (
            <div>
              <FLabel>Status</FLabel>
              <select className={styles.select} value={form.status}
                onChange={e => f('status', e.target.value)}>
                <option value="ACTIVE">Active</option>
                <option value="INACTIVE">Inactive</option>
                <option value="BLOCKED">Blocked</option>
              </select>
            </div>
          )}

          <div>
            <FLabel>Email</FLabel>
            <input type="email" className={inputCls(fieldErrors, 'email')} value={form.email}
              onChange={e => f('email', e.target.value)} placeholder="contact@company.co.za" />
            <FError msg={fieldErrors.email} />
          </div>

          <div>
            <FLabel>Phone</FLabel>
            <input className={inputCls(fieldErrors, 'phone')} value={form.phone}
              onChange={e => f('phone', e.target.value.replace(/[^\d\s\-+]/g, ''))}
              placeholder="+27 82 341 5567" />
            <FError msg={fieldErrors.phone} />
          </div>

          <div>
            <FLabel>VAT Number</FLabel>
            <input className={inputCls(fieldErrors, 'taxNumber')} value={form.taxNumber}
              onChange={e => f('taxNumber', e.target.value.replace(/\D/g, '').slice(0, 10))}
              placeholder="4198765432" inputMode="numeric" />
            <FError msg={fieldErrors.taxNumber} />
          </div>

          <div className={styles.formFieldFull}>
            <FLabel>Notes</FLabel>
            <textarea className={styles.textarea} value={form.notes}
              onChange={e => f('notes', e.target.value)}
              placeholder="Key account, prefers invoice on the 25th…" rows={2} />
          </div>

          <div className={styles.formFieldFull}>
            <div className={styles.sectionDivider}>Address</div>
          </div>

          <div className={styles.formFieldFull}>
            <FLabel>Street</FLabel>
            <input className={styles.input} value={form.street}
              onChange={e => f('street', e.target.value)} placeholder="45 Mine Road" />
          </div>

          <div>
            <FLabel>Suburb</FLabel>
            <input className={styles.input} value={form.suburb}
              onChange={e => f('suburb', e.target.value)} placeholder="Carletonville" />
          </div>

          <div>
            <FLabel>City</FLabel>
            <input className={styles.input} value={form.city}
              onChange={e => f('city', e.target.value)} placeholder="Merafong" />
          </div>

          <div>
            <FLabel>Province</FLabel>
            <select className={styles.select} value={form.province}
              onChange={e => f('province', e.target.value)}>
              <option value="">Select province…</option>
              {SA_PROVINCES.map(p => <option key={p} value={p}>{p}</option>)}
            </select>
          </div>

          <div>
            <FLabel>Postal Code</FLabel>
            <input className={inputCls(fieldErrors, 'postalCode')} value={form.postalCode}
              onChange={e => f('postalCode', e.target.value.replace(/\D/g, '').slice(0, 4))}
              placeholder="2499" inputMode="numeric" maxLength={4} />
            <FError msg={fieldErrors.postalCode} />
          </div>
        </div>

        {generalError && <ErrorBanner msg={generalError} />}

        <div className={styles.modalFooter}>
          <button className={styles.btnOutline} onClick={onClose} disabled={isPending}>Cancel</button>
          <button className={styles.btnPrimary} onClick={onSubmit} disabled={isPending}>
            {isPending
              ? (mode === 'add' ? 'Creating…' : 'Saving…')
              : (mode === 'add' ? 'Create Customer' : 'Save Changes')}
          </button>
        </div>
      </div>
    </Overlay>
  )
}

// ─────────────────────────────────────────────────────────────────────────────
// DeleteModal
// ─────────────────────────────────────────────────────────────────────────────

function DeleteModal({ customer, isPending, error, onConfirm, onCancel }: {
  customer: Customer; isPending: boolean; error: string
  onConfirm: () => void; onCancel: () => void
}) {
  const trapRef = useFocusTrap(true)
  return (
    <Overlay onClose={onCancel}>
      <div ref={trapRef} className={`${styles.modal} ${styles.modalNarrow}`} role="alertdialog" aria-modal="true" aria-label="Delete customer">
        <div className={styles.deleteIcon}><AlertTriangle size={24} color="#EA580C" /></div>
        <h3 className={styles.deleteTitle}>Delete Customer?</h3>
        <div className={styles.deleteChip}>
          <div className={styles.avatar} style={{ width: 28, height: 28, fontSize: 11 }}>
            {initials(customer.name)}
          </div>
          <span>{customer.name}</span>
        </div>
        <p className={styles.deleteBody}>
          This customer will be soft-deleted and can be restored from the <strong>Deleted</strong> tab.
          Active bookings or invoices will remain intact.
        </p>
        {error && <ErrorBanner msg={error} />}
        <div className={styles.modalFooter}>
          <button className={styles.btnOutline} onClick={onCancel} disabled={isPending}>Cancel</button>
          <button className={styles.btnDelete} onClick={onConfirm} disabled={isPending}>
            {isPending ? 'Deleting…' : 'Yes, Delete'}
          </button>
        </div>
      </div>
    </Overlay>
  )
}

// ─────────────────────────────────────────────────────────────────────────────
// TimelineModal
// ─────────────────────────────────────────────────────────────────────────────

function TimelineModal({ customer, activities, isLoading, noteText, onNoteChange, isAddingNote, onAddNote, onClose }: {
  customer:     Customer
  activities:   CustomerActivity[]
  isLoading:    boolean
  noteText:     string
  onNoteChange: (v: string) => void
  isAddingNote: boolean
  onAddNote:    () => void
  onClose:      () => void
}) {
  const trapRef = useFocusTrap(true)
  return (
    <Overlay onClose={onClose}>
      <div ref={trapRef} className={`${styles.modal} ${styles.modalWide}`} role="dialog" aria-modal="true" aria-label="Activity timeline">
        <div className={styles.modalHeader}>
          <div>
            <h3 className={styles.modalTitle}>Activity Timeline</h3>
            <p className={styles.modalSubtitle}>{customer.name}</p>
          </div>
          <button onClick={onClose} className={styles.closeBtn} aria-label="Close"><X size={20} /></button>
        </div>

        <div className={styles.noteBox}>
          <textarea
            className={styles.noteTextarea}
            value={noteText}
            onChange={e => onNoteChange(e.target.value)}
            placeholder="Add a note — e.g. 'Called Sipho to follow up on quote Q-0042…'"
            rows={2}
            maxLength={5000}
          />
          <button
            className={styles.btnPrimary}
            onClick={onAddNote}
            disabled={isAddingNote || !noteText.trim()}
            style={{ alignSelf: 'flex-end' }}>
            {isAddingNote ? 'Adding…' : 'Add Note'}
          </button>
        </div>

        <div className={styles.timeline}>
          {isLoading ? (
            <div className={styles.stateCenter} style={{ padding: 40 }}>
              <div className={styles.spinner} />
            </div>
          ) : activities.length === 0 ? (
            <p className={styles.muted} style={{ padding: '20px 0', textAlign: 'center' }}>
              No activity recorded yet.
            </p>
          ) : activities.map((a, i) => (
            <div key={a.id} className={styles.timelineItem}>
              <div className={`${styles.timelineDot} ${(styles as any)[`dot_${a.activityType}`] ?? ''}`} />
              {i < activities.length - 1 && <div className={styles.timelineLine} />}
              <div className={styles.timelineContent}>
                <div className={styles.timelineLabel}>
                  {ACTIVITY_LABELS[a.activityType] ?? a.activityType}
                </div>
                {a.note && <p className={styles.timelineNote}>{a.note}</p>}
                {a.payload && a.activityType === 'STATUS_CHANGED' && (
                  <p className={styles.timelineDetail}>
                    {String((a.payload as any).from)} → {String((a.payload as any).to)}
                  </p>
                )}
                <time className={styles.timelineTime}>{formatDateTime(a.createdAt)}</time>
              </div>
            </div>
          ))}
        </div>
      </div>
    </Overlay>
  )
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared primitives
// ─────────────────────────────────────────────────────────────────────────────

function Overlay({ children, onClose }: { children: React.ReactNode; onClose: () => void }) {
  return (
    <div className={styles.overlay} onClick={e => { if (e.target === e.currentTarget) onClose() }}>
      {children}
    </div>
  )
}

function Detail({ icon: Icon, label, value, fullWidth = false }: {
  icon: React.FC<any>; label: string; value: string; fullWidth?: boolean
}) {
  return (
    <div className={`${styles.detailCard} ${fullWidth ? styles.detailCardFull : ''}`}>
      <div className={styles.detailLabel}><Icon size={12} />{label}</div>
      <div className={styles.detailValue}>{value}</div>
    </div>
  )
}

function FLabel({ children, required }: { children: React.ReactNode; required?: boolean }) {
  return (
    <label className={styles.flabel}>
      {children}{required && <span className={styles.required}>*</span>}
    </label>
  )
}

function FError({ msg }: { msg?: string }) {
  if (!msg) return null
  return <div className={styles.ferror}><AlertCircle size={12} />{msg}</div>
}

function ErrorBanner({ msg }: { msg: string }) {
  return (
    <div className={styles.errorBanner} role="alert">
      <AlertCircle size={15} style={{ flexShrink: 0 }} />{msg}
    </div>
  )
}

const inputCls = (errors: Record<string, string>, key: string) =>
  `${styles.input} ${errors[key] ? styles.inputError : ''}`
