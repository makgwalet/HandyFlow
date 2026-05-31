// src/pages/catalogue/CataloguePage.tsx
import { useState, useRef, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Plus, Search, Trash2, Package, Tag, ChevronDown, ChevronUp,
  X, Pencil, AlertTriangle, AlertCircle, MoreVertical,
} from 'lucide-react'
import { apiClient } from '../../api/client'

interface Category { id: string; name: string; description: string; sortOrder: number }
interface CatalogueItem {
  id: string; name: string; description: string; unit: string;
  defaultPrice: number; vatRate: number; categoryName: string | null
}

// All units grouped by business type
const UNIT_GROUPS = [
  { group: 'Time',           options: ['Hour', 'Half Day', 'Day', 'Week', 'Month', 'Year'] },
  { group: 'Quantity',       options: ['Each', 'Unit', 'Pair', 'Set', 'Box', 'Pallet', 'Dozen'] },
  { group: 'Weight',         options: ['kg', 'Ton', 'g'] },
  { group: 'Volume',         options: ['Litre', 'ML', 'm³', 'Gallon'] },
  { group: 'Distance/Area',  options: ['km', 'm', 'm²', 'Linear metre', 'Square metre'] },
  { group: 'Transport',      options: ['Per Trip', 'Trip', 'Load', 'Delivery'] },
  { group: 'Service',        options: ['Job', 'Visit', 'Session', 'Project', 'Contract', 'Per Person', 'Per Event', 'Call-out'] },
  { group: 'Trade',          options: ['Point', 'Connection', 'Slab', 'Panel', 'Bay'] },
  { group: 'Hospitality',    options: ['Per Night', 'Per Plate', 'Per Table', 'Per Guest'] },
  { group: 'Medical',        options: ['Consultation', 'Procedure', 'Script', 'Test'] },
]

const ALL_UNITS = UNIT_GROUPS.flatMap(g => g.options)

const EMPTY_ITEM_FORM = { name: '', description: '', categoryId: '', unit: 'Day', defaultPrice: '', vatRate: '15' }
const EMPTY_CAT_FORM  = { name: '', description: '' }
const EMPTY_ERRORS: Record<string, string> = {}

// ── Shared styles ─────────────────────────────────────────────────────────────
const inp: React.CSSProperties = {
  width: '100%', padding: '10px 12px', border: '1.5px solid #E2E8F0', borderRadius: 9,
  fontSize: 14, color: '#0F172A', outline: 'none', boxSizing: 'border-box', background: 'white',
}
const inpErr: React.CSSProperties = { ...inp, borderColor: '#DC2626', background: '#FFF5F5' }
const sel: React.CSSProperties = {
  ...inp, appearance: 'none',
  backgroundImage: `url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 24 24' fill='none' stroke='%2394A3B8' stroke-width='2'%3E%3Cpath d='M6 9l6 6 6-6'/%3E%3C/svg%3E")`,
  backgroundRepeat: 'no-repeat', backgroundPosition: 'right 12px center', paddingRight: 36, cursor: 'pointer',
}

// ── Modal wrapper ─────────────────────────────────────────────────────────────
function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', zIndex: 200, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 24, backdropFilter: 'blur(2px)' }}>
      <div style={{ background: 'white', borderRadius: 16, width: '100%', maxWidth: 500, boxShadow: '0 20px 60px rgba(0,0,0,0.2)', overflow: 'hidden', maxHeight: '92vh', display: 'flex', flexDirection: 'column' }}>
        <div style={{ padding: '20px 24px', borderBottom: '1px solid #F1F5F9', display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexShrink: 0 }}>
          <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: '#0F172A' }}>{title}</h3>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', padding: 4, display: 'flex' }}><X size={18} /></button>
        </div>
        <div style={{ padding: 24, overflowY: 'auto' }}>{children}</div>
      </div>
    </div>
  )
}

// ── Field wrapper ─────────────────────────────────────────────────────────────
function Field({ label, required, error, children }: { label: string; required?: boolean; error?: string; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 16 }}>
      <label style={{ display: 'block', fontSize: 12, fontWeight: 600, color: '#374151', marginBottom: 6, textTransform: 'uppercase', letterSpacing: '0.04em' }}>
        {label}{required && <span style={{ color: '#DC2626', marginLeft: 2 }}>*</span>}
      </label>
      {children}
      {error && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, color: '#DC2626', marginTop: 4 }}>
          <AlertCircle size={12} color="#DC2626" />{error}
        </div>
      )}
    </div>
  )
}

// ── Searchable Unit Dropdown ──────────────────────────────────────────────────
function UnitSelect({ value, onChange, hasError }: { value: string; onChange: (v: string) => void; hasError: boolean }) {
  const [open, setOpen]     = useState(false)
  const [query, setQuery]   = useState('')
  const ref                 = useRef<HTMLDivElement>(null)

  // Close on outside click
  useEffect(() => {
    const handler = (e: MouseEvent) => { if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false) }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  const filteredGroups = UNIT_GROUPS.map(g => ({
    ...g,
    options: g.options.filter(o => o.toLowerCase().includes(query.toLowerCase())),
  })).filter(g => g.options.length > 0)

  return (
    <div ref={ref} style={{ position: 'relative' }}>
      {/* Trigger */}
      <button
        type="button"
        onClick={() => { setOpen(o => !o); setQuery('') }}
        style={{
          ...inp,
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          cursor: 'pointer', textAlign: 'left',
          ...(hasError ? { borderColor: '#DC2626', background: '#FFF5F5' } : {}),
        }}>
        <span style={{ color: value ? '#0F172A' : '#94A3B8' }}>{value || 'Select unit...'}</span>
        <ChevronDown size={14} color="#94A3B8" />
      </button>

      {/* Dropdown */}
      {open && (
        <div style={{
          position: 'absolute', top: 'calc(100% + 4px)', left: 0, right: 0, zIndex: 999,
          background: 'white', border: '1.5px solid #E2E8F0', borderRadius: 10,
          boxShadow: '0 8px 30px rgba(0,0,0,0.12)', overflow: 'hidden',
        }}>
          {/* Search inside dropdown */}
          <div style={{ padding: '10px 12px', borderBottom: '1px solid #F1F5F9' }}>
            <div style={{ position: 'relative' }}>
              <Search size={13} color="#94A3B8" style={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)' }} />
              <input
                autoFocus
                value={query}
                onChange={e => setQuery(e.target.value)}
                placeholder="Search units..."
                style={{ ...inp, paddingLeft: 32, padding: '8px 8px 8px 30px', fontSize: 13 }}
              />
            </div>
          </div>

          {/* Options list */}
          <div style={{ maxHeight: 240, overflowY: 'auto' }}>
            {filteredGroups.length === 0 ? (
              <div style={{ padding: '16px', textAlign: 'center', fontSize: 13, color: '#94A3B8' }}>No units match</div>
            ) : (
              filteredGroups.map(group => (
                <div key={group.group}>
                  <div style={{ padding: '6px 12px 4px', fontSize: 10, fontWeight: 700, color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.06em', background: '#F8FAFC' }}>
                    {group.group}
                  </div>
                  {group.options.map(unit => (
                    <div
                      key={unit}
                      onClick={() => { onChange(unit); setOpen(false); setQuery('') }}
                      style={{
                        padding: '9px 14px', fontSize: 14, cursor: 'pointer',
                        background: unit === value ? '#EFF6FF' : 'white',
                        color: unit === value ? '#1D4ED8' : '#0F172A',
                        fontWeight: unit === value ? 600 : 400,
                      }}
                      onMouseEnter={e => { if (unit !== value) (e.currentTarget as HTMLElement).style.background = '#F8FAFC' }}
                      onMouseLeave={e => { if (unit !== value) (e.currentTarget as HTMLElement).style.background = 'white' }}
                    >
                      {unit}
                    </div>
                  ))}
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  )
}

// ── Delete Confirm Modal ──────────────────────────────────────────────────────
function DeleteModal({
  title, description, badge, badgeIcon, isPending, error, onConfirm, onCancel,
}: {
  title: string; description: React.ReactNode; badge: string
  badgeIcon: React.ReactNode; isPending: boolean; error: string
  onConfirm: () => void; onCancel: () => void
}) {
  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 300, backdropFilter: 'blur(2px)' }}>
      <div style={{ background: 'white', borderRadius: 18, padding: 32, width: 420, boxShadow: '0 24px 64px rgba(0,0,0,0.18)', display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center' }}>
        <div style={{ width: 56, height: 56, borderRadius: '50%', background: '#FFF7ED', border: '2px solid #FED7AA', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 16 }}>
          <AlertTriangle size={24} color="#EA580C" strokeWidth={2} />
        </div>
        <h3 style={{ margin: '0 0 6px', fontSize: 18, fontWeight: 700, color: '#0F172A' }}>{title}</h3>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, background: '#F8FAFC', border: '1px solid #E2E8F0', borderRadius: 40, padding: '7px 16px', margin: '10px 0' }}>
          {badgeIcon}
          <span style={{ fontSize: 14, fontWeight: 600, color: '#0F172A' }}>{badge}</span>
        </div>
        <div style={{ margin: '0 0 16px', fontSize: 13, color: '#64748B', lineHeight: 1.7 }}>{description}</div>
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
            style={{ flex: 1, padding: '11px', border: 'none', borderRadius: 10, background: isPending ? '#93A8C9' : '#1B3A6B', color: 'white', fontSize: 14, fontWeight: 700, cursor: isPending ? 'not-allowed' : 'pointer' }}>
            {isPending ? 'Deleting...' : 'Yes, Delete'}
          </button>
        </div>
      </div>
    </div>
  )
}

// ── Item Form ─────────────────────────────────────────────────────────────────
function ItemForm({ form, setForm, categories, fieldErrors }: {
  form: typeof EMPTY_ITEM_FORM; setForm: (f: typeof EMPTY_ITEM_FORM) => void
  categories: Category[]; fieldErrors: Record<string, string>
}) {
  const f = (k: string, v: string) => setForm({ ...form, [k]: v })
  const handlePrice = (v: string) => { if (/^\d*\.?\d{0,2}$/.test(v)) f('defaultPrice', v) }

  return (
    <>
      <Field label="Item name" required error={fieldErrors.name}>
        <input style={fieldErrors.name ? inpErr : inp} value={form.name}
          onChange={e => f('name', e.target.value)} placeholder="e.g. Caterpillar D9 Dozer" autoFocus />
      </Field>

      <Field label="Category (optional)">
        <select style={sel} value={form.categoryId} onChange={e => f('categoryId', e.target.value)}>
          <option value="">— No category —</option>
          {categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
      </Field>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
        <Field label="Unit" required error={fieldErrors.unit}>
          <UnitSelect value={form.unit} onChange={v => f('unit', v)} hasError={!!fieldErrors.unit} />
        </Field>
        <Field label="Default price (R)" required error={fieldErrors.defaultPrice}>
          <input style={fieldErrors.defaultPrice ? inpErr : inp}
            value={form.defaultPrice} onChange={e => handlePrice(e.target.value)}
            placeholder="0.00" inputMode="decimal" />
        </Field>
      </div>

      <Field label="VAT rate (%)">
        <select style={sel} value={form.vatRate} onChange={e => f('vatRate', e.target.value)}>
          <option value="15">15% (Standard)</option>
          <option value="0">0% (Zero-rated / Exempt)</option>
        </select>
      </Field>

      <Field label="Description (optional)">
        <input style={inp} value={form.description}
          onChange={e => f('description', e.target.value)} placeholder="Brief description" />
      </Field>
    </>
  )
}

// ── Category Options Menu ─────────────────────────────────────────────────────
function CategoryMenu({ cat, onEdit, onDelete }: { cat: Category; onEdit: () => void; onDelete: () => void }) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const handler = (e: MouseEvent) => { if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false) }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  return (
    <div ref={ref} style={{ position: 'relative' }} onClick={e => e.stopPropagation()}>
      <button
        onClick={() => setOpen(o => !o)}
        style={{ padding: '4px 6px', background: 'none', border: '1px solid #E2E8F0', borderRadius: 6, cursor: 'pointer', display: 'flex', alignItems: 'center', color: '#94A3B8' }}>
        <MoreVertical size={14} />
      </button>
      {open && (
        <div style={{ position: 'absolute', right: 0, top: 'calc(100% + 4px)', background: 'white', border: '1px solid #E2E8F0', borderRadius: 10, boxShadow: '0 8px 24px rgba(0,0,0,0.1)', zIndex: 100, minWidth: 140, overflow: 'hidden' }}>
          <button onClick={() => { onEdit(); setOpen(false) }}
            style={{ width: '100%', padding: '10px 14px', background: 'none', border: 'none', cursor: 'pointer', fontSize: 13, fontWeight: 500, color: '#374151', display: 'flex', alignItems: 'center', gap: 8, textAlign: 'left' }}>
            <Pencil size={13} color="#1D4ED8" /> Edit category
          </button>
          <button onClick={() => { onDelete(); setOpen(false) }}
            style={{ width: '100%', padding: '10px 14px', background: 'none', border: 'none', cursor: 'pointer', fontSize: 13, fontWeight: 500, color: '#DC2626', display: 'flex', alignItems: 'center', gap: 8, textAlign: 'left', borderTop: '1px solid #FEF2F2' }}>
            <Trash2 size={13} color="#DC2626" /> Delete category
          </button>
        </div>
      )}
    </div>
  )
}

// ── Main Page ─────────────────────────────────────────────────────────────────
export function CataloguePage() {
  const qc = useQueryClient()
  const [search, setSearch]             = useState('')
  const [expandedCats, setExpandedCats] = useState<Set<string>>(new Set())

  // Add item
  const [showItemModal, setShowItemModal]     = useState(false)
  const [itemForm, setItemForm]               = useState(EMPTY_ITEM_FORM)
  const [itemError, setItemError]             = useState('')
  const [itemFieldErrors, setItemFieldErrors] = useState<Record<string, string>>(EMPTY_ERRORS)

  // Edit item
  const [editItem, setEditItem]               = useState<CatalogueItem | null>(null)
  const [editForm, setEditForm]               = useState(EMPTY_ITEM_FORM)
  const [editError, setEditError]             = useState('')
  const [editFieldErrors, setEditFieldErrors] = useState<Record<string, string>>(EMPTY_ERRORS)

  // Delete item
  const [deleteItem, setDeleteItem]     = useState<CatalogueItem | null>(null)
  const [deleteItemError, setDeleteItemError] = useState('')

  // Add category
  const [showCatModal, setShowCatModal]     = useState(false)
  const [catForm, setCatForm]               = useState(EMPTY_CAT_FORM)
  const [catError, setCatError]             = useState('')
  const [catFieldErrors, setCatFieldErrors] = useState<Record<string, string>>(EMPTY_ERRORS)

  // Edit category
  const [editCat, setEditCat]               = useState<Category | null>(null)
  const [editCatForm, setEditCatForm]       = useState(EMPTY_CAT_FORM)
  const [editCatError, setEditCatError]     = useState('')
  const [editCatFieldErrors, setEditCatFieldErrors] = useState<Record<string, string>>(EMPTY_ERRORS)

  // Delete category
  const [deleteCat, setDeleteCat]           = useState<Category | null>(null)
  const [deleteCatError, setDeleteCatError] = useState('')

  // ── Validation ──────────────────────────────────────────────────────────────
  const validateItem = (f: typeof EMPTY_ITEM_FORM): Record<string, string> => {
    const errs: Record<string, string> = {}
    if (!f.name.trim())  errs.name = 'Item name is required'
    if (!f.unit)         errs.unit = 'Unit is required'
    if (!f.defaultPrice) errs.defaultPrice = 'Price is required'
    else if (isNaN(parseFloat(f.defaultPrice)) || parseFloat(f.defaultPrice) < 0)
      errs.defaultPrice = 'Price must be a positive number'
    return errs
  }

  const validateCat = (f: typeof EMPTY_CAT_FORM): Record<string, string> => {
    const errs: Record<string, string> = {}
    if (!f.name.trim()) errs.name = 'Category name is required'
    return errs
  }

  const parseApiErrors = (e: any): { fieldMap: Record<string, string>; general: string } => {
    const data = e.response?.data
    // Spring field validation errors
    if (data?.errors && Array.isArray(data.errors)) {
      const fieldMap: Record<string, string> = {}
      data.errors.forEach((err: any) => { fieldMap[err.field] = err.message })
      return { fieldMap, general: '' }
    }
    // Business logic errors (duplicate name etc.)
    return { fieldMap: {}, general: data?.message ?? 'Something went wrong. Please try again.' }
  }

  // ── Queries ─────────────────────────────────────────────────────────────────
  const { data: categories = [] } = useQuery<Category[]>({
    queryKey: ['categories'],
    queryFn: async () => {
      const res = await apiClient.get('/api/v1/catalogue/categories')
      return (res.data?.data ?? res.data) as Category[]
    },
  })

  const { data: items = [] } = useQuery<CatalogueItem[]>({
    queryKey: ['catalogue-items', search],
    queryFn: async () => {
      const res = await apiClient.get('/api/v1/catalogue/items', { params: { query: search } })
      return (res.data?.data ?? res.data) as CatalogueItem[]
    },
  })

  // ── Mutations ───────────────────────────────────────────────────────────────
  const createCatMutation = useMutation({
    mutationFn: (d: typeof EMPTY_CAT_FORM) => apiClient.post('/api/v1/catalogue/categories', d),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['categories'] }); setShowCatModal(false); setCatForm(EMPTY_CAT_FORM); setCatError(''); setCatFieldErrors(EMPTY_ERRORS) },
    onError: (e: any) => { const { fieldMap, general } = parseApiErrors(e); setCatFieldErrors(fieldMap); setCatError(general) },
  })

  const updateCatMutation = useMutation({
    mutationFn: ({ id, d }: { id: string; d: typeof EMPTY_CAT_FORM }) => apiClient.put(`/api/v1/catalogue/categories/${id}`, d),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['categories'] }); qc.invalidateQueries({ queryKey: ['catalogue-items'] }); setEditCat(null); setEditCatError(''); setEditCatFieldErrors(EMPTY_ERRORS) },
    onError: (e: any) => { const { fieldMap, general } = parseApiErrors(e); setEditCatFieldErrors(fieldMap); setEditCatError(general) },
  })

  const deleteCatMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/catalogue/categories/${id}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['categories'] }); qc.invalidateQueries({ queryKey: ['catalogue-items'] }); setDeleteCat(null); setDeleteCatError('') },
    onError: (e: any) => { setDeleteCatError(e.response?.data?.message ?? 'Failed to delete category') },
  })

  const createItemMutation = useMutation({
    mutationFn: (d: typeof EMPTY_ITEM_FORM) => apiClient.post('/api/v1/catalogue/items', {
      name: d.name, description: d.description || undefined,
      categoryId: d.categoryId || undefined, unit: d.unit,
      defaultPrice: parseFloat(d.defaultPrice), vatRate: parseFloat(d.vatRate),
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['catalogue-items'] }); setShowItemModal(false); setItemForm(EMPTY_ITEM_FORM); setItemError(''); setItemFieldErrors(EMPTY_ERRORS) },
    onError: (e: any) => { const { fieldMap, general } = parseApiErrors(e); setItemFieldErrors(fieldMap); setItemError(general) },
  })

  const updateItemMutation = useMutation({
    mutationFn: ({ id, d }: { id: string; d: typeof EMPTY_ITEM_FORM }) => apiClient.put(`/api/v1/catalogue/items/${id}`, {
      name: d.name, description: d.description || undefined,
      categoryId: d.categoryId || undefined, unit: d.unit,
      defaultPrice: parseFloat(d.defaultPrice), vatRate: parseFloat(d.vatRate),
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['catalogue-items'] }); setEditItem(null); setEditError(''); setEditFieldErrors(EMPTY_ERRORS) },
    onError: (e: any) => { const { fieldMap, general } = parseApiErrors(e); setEditFieldErrors(fieldMap); setEditError(general) },
  })

  const deleteItemMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/api/v1/catalogue/items/${id}`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['catalogue-items'] }); setDeleteItem(null); setDeleteItemError('') },
    onError: (e: any) => { setDeleteItemError(e.response?.data?.message ?? 'Failed to delete item') },
  })

  // ── Submit handlers ─────────────────────────────────────────────────────────
  const submitCreateItem = () => {
    const errs = validateItem(itemForm)
    if (Object.keys(errs).length) { setItemFieldErrors(errs); return }
    createItemMutation.mutate(itemForm)
  }

  const submitEditItem = () => {
    if (!editItem) return
    const errs = validateItem(editForm)
    if (Object.keys(errs).length) { setEditFieldErrors(errs); return }
    updateItemMutation.mutate({ id: editItem.id, d: editForm })
  }

  const submitCreateCat = () => {
    const errs = validateCat(catForm)
    if (Object.keys(errs).length) { setCatFieldErrors(errs); return }
    createCatMutation.mutate(catForm)
  }

  const submitEditCat = () => {
    if (!editCat) return
    const errs = validateCat(editCatForm)
    if (Object.keys(errs).length) { setEditCatFieldErrors(errs); return }
    updateCatMutation.mutate({ id: editCat.id, d: editCatForm })
  }

  const openEditItem = (item: CatalogueItem) => {
    setEditItem(item)
    const cat = categories.find(c => c.name === item.categoryName)
    setEditForm({ name: item.name, description: item.description ?? '', categoryId: cat?.id ?? '', unit: item.unit, defaultPrice: String(item.defaultPrice), vatRate: String(item.vatRate) })
    setEditError(''); setEditFieldErrors(EMPTY_ERRORS)
  }

  const openEditCat = (cat: Category) => {
    setEditCat(cat)
    setEditCatForm({ name: cat.name, description: cat.description ?? '' })
    setEditCatError(''); setEditCatFieldErrors(EMPTY_ERRORS)
  }

  const toggleCat = (id: string) => setExpandedCats(s => { const n = new Set(s); n.has(id) ? n.delete(id) : n.add(id); return n })
  const filtered = items.filter(i => i.name.toLowerCase().includes(search.toLowerCase()))

  // Count items per category for delete warning
  const catItemCount = (catName: string) => items.filter(i => i.categoryName === catName).length

  return (
    <div style={{ fontFamily: "'Inter', system-ui, sans-serif" }}>

      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: 28 }}>
        <div>
          <h1 style={{ fontSize: 24, fontWeight: 800, color: '#0F172A', margin: '0 0 4px', letterSpacing: '-0.4px' }}>Catalogue</h1>
          <p style={{ fontSize: 13, color: '#94A3B8', margin: 0 }}>Manage your products and services</p>
        </div>
        <div style={{ display: 'flex', gap: 10 }}>
          <button onClick={() => { setShowCatModal(true); setCatError(''); setCatFieldErrors(EMPTY_ERRORS) }}
            style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '9px 16px', background: 'white', border: '1px solid #E2E8F0', borderRadius: 9, fontSize: 13, fontWeight: 600, color: '#374151', cursor: 'pointer' }}>
            <Tag size={15} /> Add category
          </button>
          <button onClick={() => { setShowItemModal(true); setItemError(''); setItemFieldErrors(EMPTY_ERRORS) }}
            style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '9px 16px', background: '#1B3A6B', border: 'none', borderRadius: 9, fontSize: 13, fontWeight: 600, color: 'white', cursor: 'pointer' }}>
            <Plus size={15} /> Add item
          </button>
        </div>
      </div>

      {/* Search */}
      <div style={{ position: 'relative', marginBottom: 24 }}>
        <Search size={15} color="#94A3B8" style={{ position: 'absolute', left: 12, top: '50%', transform: 'translateY(-50%)' }} />
        <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search items..."
          style={{ ...inp, paddingLeft: 38 }} />
      </div>

      {/* Stats */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 12, marginBottom: 28 }}>
        {[
          { label: 'Total items', value: items.length, color: '#EFF6FF', iconColor: '#2563EB', icon: Package },
          { label: 'Categories',  value: categories.length, color: '#F0FDF4', iconColor: '#16A34A', icon: Tag },
          { label: 'Avg price', value: items.length ? `R ${Math.round(items.reduce((s, i) => s + i.defaultPrice, 0) / items.length).toLocaleString()}` : 'R 0', color: '#FEFCE8', iconColor: '#CA8A04', icon: Package },
        ].map(s => (
          <div key={s.label} style={{ background: 'white', border: '1px solid #E8EDF5', borderRadius: 12, padding: '16px 18px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <div>
              <p style={{ fontSize: 11, color: '#94A3B8', margin: '0 0 4px', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.04em' }}>{s.label}</p>
              <p style={{ fontSize: 22, fontWeight: 800, color: '#0F172A', margin: 0 }}>{s.value}</p>
            </div>
            <div style={{ width: 40, height: 40, borderRadius: 10, background: s.color, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <s.icon size={18} color={s.iconColor} />
            </div>
          </div>
        ))}
      </div>

      {/* Content */}
      {categories.length === 0 && items.length === 0 ? (
        <div style={{ background: 'white', border: '1px solid #E8EDF5', borderRadius: 16, padding: '60px 24px', textAlign: 'center' }}>
          <Package size={40} color="#CBD5E1" style={{ marginBottom: 12 }} />
          <p style={{ fontSize: 16, fontWeight: 700, color: '#64748B', margin: '0 0 6px' }}>No items yet</p>
          <p style={{ fontSize: 13, color: '#94A3B8', margin: 0 }}>Create a category and start adding your products and services</p>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>

          {/* Uncategorised */}
          {filtered.filter(i => !i.categoryName).length > 0 && (
            <div style={{ background: 'white', border: '1px solid #E8EDF5', borderRadius: 14, overflow: 'hidden' }}>
              <div style={{ padding: '12px 20px', background: '#F8FAFC', borderBottom: '1px solid #F1F5F9' }}>
                <span style={{ fontSize: 12, fontWeight: 700, color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Uncategorised</span>
              </div>
              <ItemTable items={filtered.filter(i => !i.categoryName)} onEdit={openEditItem} onDelete={item => { setDeleteItem(item); setDeleteItemError('') }} />
            </div>
          )}

          {/* Per-category */}
          {categories.map(cat => {
            const catItems = filtered.filter(i => i.categoryName === cat.name)
            const expanded = expandedCats.has(cat.id)
            return (
              <div key={cat.id} style={{ background: 'white', border: '1px solid #E8EDF5', borderRadius: 14, overflow: 'hidden' }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '14px 20px', cursor: 'pointer', background: expanded ? '#FAFBFF' : 'white' }}
                  onClick={() => toggleCat(cat.id)}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                    <div style={{ width: 32, height: 32, borderRadius: 8, background: '#F3E8FF', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                      <Tag size={15} color="#7C3AED" />
                    </div>
                    <div>
                      <p style={{ fontSize: 14, fontWeight: 700, color: '#0F172A', margin: 0 }}>{cat.name}</p>
                      {cat.description && <p style={{ fontSize: 12, color: '#94A3B8', margin: 0 }}>{cat.description}</p>}
                    </div>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                    <span style={{ fontSize: 12, color: '#94A3B8', fontWeight: 500 }}>{catItems.length} items</span>
                    <CategoryMenu cat={cat} onEdit={() => openEditCat(cat)} onDelete={() => { setDeleteCat(cat); setDeleteCatError('') }} />
                    {expanded ? <ChevronUp size={16} color="#94A3B8" /> : <ChevronDown size={16} color="#94A3B8" />}
                  </div>
                </div>
                {expanded && catItems.length > 0 && (
                  <div style={{ borderTop: '1px solid #F1F5F9' }}>
                    <ItemTable items={catItems} onEdit={openEditItem} onDelete={item => { setDeleteItem(item); setDeleteItemError('') }} />
                  </div>
                )}
                {expanded && catItems.length === 0 && (
                  <div style={{ padding: '20px 24px', borderTop: '1px solid #F1F5F9', textAlign: 'center' }}>
                    <p style={{ fontSize: 13, color: '#94A3B8', margin: 0 }}>No items in this category yet</p>
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {/* ── Add Category Modal ─────────────────────────────────────────── */}
      {showCatModal && (
        <Modal title="New Category" onClose={() => { setShowCatModal(false); setCatForm(EMPTY_CAT_FORM); setCatError(''); setCatFieldErrors(EMPTY_ERRORS) }}>
          <Field label="Category name" required error={catFieldErrors.name}>
            <input style={catFieldErrors.name ? inpErr : inp} value={catForm.name}
              onChange={e => setCatForm(f => ({ ...f, name: e.target.value }))} placeholder="e.g. Heavy Equipment" autoFocus />
          </Field>
          <Field label="Description (optional)">
            <input style={inp} value={catForm.description}
              onChange={e => setCatForm(f => ({ ...f, description: e.target.value }))} placeholder="Brief description" />
          </Field>
          {catError && <ErrorBanner message={catError} />}
          <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
            <button onClick={() => { setShowCatModal(false); setCatForm(EMPTY_CAT_FORM); setCatError('') }}
              style={{ padding: '9px 16px', background: 'white', border: '1px solid #E2E8F0', borderRadius: 9, fontSize: 13, fontWeight: 600, cursor: 'pointer', color: '#374151' }}>Cancel</button>
            <button onClick={submitCreateCat} disabled={createCatMutation.isPending}
              style={{ padding: '9px 18px', background: '#1B3A6B', border: 'none', borderRadius: 9, fontSize: 13, fontWeight: 600, color: 'white', cursor: 'pointer' }}>
              {createCatMutation.isPending ? 'Creating...' : 'Create category'}
            </button>
          </div>
        </Modal>
      )}

      {/* ── Edit Category Modal ────────────────────────────────────────── */}
      {editCat && (
        <Modal title={`Edit — ${editCat.name}`} onClose={() => { setEditCat(null); setEditCatError(''); setEditCatFieldErrors(EMPTY_ERRORS) }}>
          <Field label="Category name" required error={editCatFieldErrors.name}>
            <input style={editCatFieldErrors.name ? inpErr : inp} value={editCatForm.name}
              onChange={e => setEditCatForm(f => ({ ...f, name: e.target.value }))} autoFocus />
          </Field>
          <Field label="Description (optional)">
            <input style={inp} value={editCatForm.description}
              onChange={e => setEditCatForm(f => ({ ...f, description: e.target.value }))} />
          </Field>
          {editCatError && <ErrorBanner message={editCatError} />}
          <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
            <button onClick={() => { setEditCat(null); setEditCatError('') }}
              style={{ padding: '9px 16px', background: 'white', border: '1px solid #E2E8F0', borderRadius: 9, fontSize: 13, fontWeight: 600, cursor: 'pointer', color: '#374151' }}>Cancel</button>
            <button onClick={submitEditCat} disabled={updateCatMutation.isPending}
              style={{ padding: '9px 18px', background: '#1B3A6B', border: 'none', borderRadius: 9, fontSize: 13, fontWeight: 600, color: 'white', cursor: 'pointer' }}>
              {updateCatMutation.isPending ? 'Saving...' : 'Save changes'}
            </button>
          </div>
        </Modal>
      )}

      {/* ── Add Item Modal ─────────────────────────────────────────────── */}
      {showItemModal && (
        <Modal title="New Catalogue Item" onClose={() => { setShowItemModal(false); setItemForm(EMPTY_ITEM_FORM); setItemError(''); setItemFieldErrors(EMPTY_ERRORS) }}>
          <ItemForm form={itemForm} setForm={setItemForm} categories={categories} fieldErrors={itemFieldErrors} />
          {itemError && <ErrorBanner message={itemError} />}
          <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
            <button onClick={() => { setShowItemModal(false); setItemForm(EMPTY_ITEM_FORM); setItemError('') }}
              style={{ padding: '9px 16px', background: 'white', border: '1px solid #E2E8F0', borderRadius: 9, fontSize: 13, fontWeight: 600, cursor: 'pointer', color: '#374151' }}>Cancel</button>
            <button onClick={submitCreateItem} disabled={createItemMutation.isPending}
              style={{ padding: '9px 18px', background: '#1B3A6B', border: 'none', borderRadius: 9, fontSize: 13, fontWeight: 600, color: 'white', cursor: 'pointer' }}>
              {createItemMutation.isPending ? 'Creating...' : 'Create item'}
            </button>
          </div>
        </Modal>
      )}

      {/* ── Edit Item Modal ────────────────────────────────────────────── */}
      {editItem && (
        <Modal title={`Edit — ${editItem.name}`} onClose={() => { setEditItem(null); setEditError(''); setEditFieldErrors(EMPTY_ERRORS) }}>
          <ItemForm form={editForm} setForm={setEditForm} categories={categories} fieldErrors={editFieldErrors} />
          {editError && <ErrorBanner message={editError} />}
          <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
            <button onClick={() => { setEditItem(null); setEditError('') }}
              style={{ padding: '9px 16px', background: 'white', border: '1px solid #E2E8F0', borderRadius: 9, fontSize: 13, fontWeight: 600, cursor: 'pointer', color: '#374151' }}>Cancel</button>
            <button onClick={submitEditItem} disabled={updateItemMutation.isPending}
              style={{ padding: '9px 18px', background: '#1B3A6B', border: 'none', borderRadius: 9, fontSize: 13, fontWeight: 600, color: 'white', cursor: 'pointer' }}>
              {updateItemMutation.isPending ? 'Saving...' : 'Save changes'}
            </button>
          </div>
        </Modal>
      )}

      {/* ── Delete Item Modal ──────────────────────────────────────────── */}
      {deleteItem && (
        <DeleteModal
          title="Delete Item?"
          badge={deleteItem.name}
          badgeIcon={<Package size={14} color="#64748B" />}
          description={<>This item will be permanently removed from your catalogue.<br />This action <strong>cannot be undone</strong>.</>}
          isPending={deleteItemMutation.isPending}
          error={deleteItemError}
          onConfirm={() => deleteItemMutation.mutate(deleteItem.id)}
          onCancel={() => { setDeleteItem(null); setDeleteItemError('') }}
        />
      )}

      {/* ── Delete Category Modal ──────────────────────────────────────── */}
      {deleteCat && (
        <DeleteModal
          title="Delete Category?"
          badge={deleteCat.name}
          badgeIcon={<Tag size={14} color="#7C3AED" />}
          description={
            catItemCount(deleteCat.name) > 0 ? (
              <>
                This will permanently delete <strong>{deleteCat.name}</strong> and all{' '}
                <strong style={{ color: '#DC2626' }}>{catItemCount(deleteCat.name)} item{catItemCount(deleteCat.name) !== 1 ? 's' : ''}</strong> inside it.
                <br />This action <strong>cannot be undone</strong>.
              </>
            ) : (
              <>This category is empty and will be permanently removed.<br />This action <strong>cannot be undone</strong>.</>
            )
          }
          isPending={deleteCatMutation.isPending}
          error={deleteCatError}
          onConfirm={() => deleteCatMutation.mutate(deleteCat.id)}
          onCancel={() => { setDeleteCat(null); setDeleteCatError('') }}
        />
      )}
    </div>
  )
}

// ── Item Table ────────────────────────────────────────────────────────────────
function ItemTable({ items, onEdit, onDelete }: {
  items: CatalogueItem[]; onEdit: (item: CatalogueItem) => void; onDelete: (item: CatalogueItem) => void
}) {
  return (
    <table style={{ width: '100%', borderCollapse: 'collapse' }}>
      <thead>
        <tr style={{ background: '#F8FAFC' }}>
          {['Name', 'Unit', 'Default price', 'VAT', ''].map(h => (
            <th key={h} style={{ padding: '10px 20px', textAlign: 'left', fontSize: 11, fontWeight: 700, color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.05em' }}>{h}</th>
          ))}
        </tr>
      </thead>
      <tbody>
        {items.map((item, i) => (
          <tr key={item.id} style={{ borderTop: i === 0 ? 'none' : '1px solid #F1F5F9' }}
            onMouseEnter={e => (e.currentTarget.style.background = '#FAFBFF')}
            onMouseLeave={e => (e.currentTarget.style.background = 'white')}>
            <td style={{ padding: '12px 20px' }}>
              <p style={{ fontSize: 14, fontWeight: 600, color: '#0F172A', margin: 0 }}>{item.name}</p>
              {item.description && <p style={{ fontSize: 12, color: '#94A3B8', margin: '2px 0 0' }}>{item.description}</p>}
            </td>
            <td style={{ padding: '12px 20px', fontSize: 13, color: '#64748B' }}>{item.unit}</td>
            <td style={{ padding: '12px 20px', fontSize: 14, fontWeight: 700, color: '#0F172A' }}>
              R {Number(item.defaultPrice).toLocaleString('en-ZA', { minimumFractionDigits: 2 })}
            </td>
            <td style={{ padding: '12px 20px', fontSize: 13, color: '#64748B' }}>{item.vatRate}%</td>
            <td style={{ padding: '12px 20px', textAlign: 'right' }}>
              <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
                <button onClick={() => onEdit(item)}
                  style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '5px 10px', background: '#EFF6FF', border: '1px solid #BFDBFE', borderRadius: 7, fontSize: 12, color: '#1D4ED8', cursor: 'pointer', fontWeight: 500 }}>
                  <Pencil size={12} /> Edit
                </button>
                <button onClick={() => onDelete(item)}
                  style={{ padding: '5px 8px', background: 'none', border: '1px solid #E2E8F0', borderRadius: 7, cursor: 'pointer', color: '#94A3B8', display: 'flex' }}>
                  <Trash2 size={13} />
                </button>
              </div>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

// ── Error Banner ──────────────────────────────────────────────────────────────
function ErrorBanner({ message }: { message: string }) {
  return (
    <div style={{ marginBottom: 16, padding: '10px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626', display: 'flex', alignItems: 'center', gap: 8 }}>
      <AlertCircle size={15} color="#DC2626" style={{ flexShrink: 0 }} />{message}
    </div>
  )
}