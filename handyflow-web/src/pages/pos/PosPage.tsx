// src/pages/pos/PosPage.tsx
import React, { useState, useCallback, useRef, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import {
  ShoppingCart, Package, AlertTriangle, X, Minus, Plus,
  Search, RefreshCw, Printer, ChevronDown, Truck, Receipt,
  CheckCircle, TrendingUp, RotateCcw, Scan,
} from 'lucide-react'

// ─── Types ────────────────────────────────────────────────────────────────────

interface StockItem {
  id: string
  catalogueItemId: string
  itemName: string
  sku: string | null
  barcode: string | null
  qtyOnHand: number
  availableQty: number
  reorderLevel: number
  costPrice: number
  sellingPrice: number
  location: string | null
  lowStock: boolean
}

interface Transaction {
  id: string
  transactionNumber: string
  customerName: string | null
  totalAmount: number
  paymentMethod: string
  changeGiven: number | null
  status: string
  servedByName: string | null
  createdAt: string
  cashSessionNumber: string | null
  items: TxnItem[]
}

interface TxnItem {
  id: string
  itemName: string
  qty: number
  unitPrice: number
  discountPct: number
  vatAmount: number
  lineTotal: number
}

interface CartItem {
  catalogueItemId: string
  itemName: string
  unitPrice: number
  qty: number
  discountPct: number
}

interface CashSession {
  id: string
  sessionNumber: string
  openedByName: string
  openingFloat: number
  expectedCash: number | null
  totalSales: number
  transactionCount: number
  status: string
  openedAt: string
}

interface PurchaseOrder {
  id: string
  orderNumber: string
  supplierName: string
  status: string
  orderDate: string
  expectedDate: string | null
  totalAmount: number
  items: POItem[]
}

interface POItem {
  id: string
  itemName: string
  qtyOrdered: number
  qtyReceived: number
  unitCost: number
  lineTotal: number
  fullyReceived: boolean
}

type Tab = 'sell' | 'stock' | 'transactions' | 'orders'

// ─── Helpers ──────────────────────────────────────────────────────────────────

const VAT_RATE = 0.15

const fmtR = (n: number | null | undefined) =>
  n == null
    ? 'R 0.00'
    : `R ${Number(n).toLocaleString('en-ZA', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      })}`

const fmtDate = (d: string) =>
  new Date(d).toLocaleString('en-ZA', {
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  })

const fmtDateShort = (d: string) =>
  new Date(d).toLocaleDateString('en-ZA', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  })

const extractError = (e: any): string => {
  const d = e.response?.data
  return d?.message ?? d?.data?.message ?? d?.error ?? d?.detail ?? e.message ?? 'An unexpected error occurred'
}

const unwrap = (r: any): any[] =>
  r.data?.data?.content ?? r.data?.content ?? r.data?.data ?? []

const STATUS_COLORS: Record<string, [string, string]> = {
  COMPLETED:           ['#166534', '#DCFCE7'],
  VOIDED:              ['#DC2626', '#FEF2F2'],
  REFUNDED:            ['#D97706', '#FFFBEB'],
  ORDERED:             ['#1D4ED8', '#EFF6FF'],
  PARTIALLY_RECEIVED:  ['#D97706', '#FFFBEB'],
  RECEIVED:            ['#166534', '#DCFCE7'],
  CANCELLED:           ['#DC2626', '#FEF2F2'],
  DRAFT:               ['#64748B', '#F8FAFC'],
}

function StatusBadge({ status }: { status: string }) {
  const [color, bg] = STATUS_COLORS[status] ?? ['#64748B', '#F8FAFC']
  return (
    <span style={{
      background: bg, color,
      padding: '2px 9px', borderRadius: 20,
      fontSize: 10, fontWeight: 700, whiteSpace: 'nowrap',
    }}>
      {status.replace(/_/g, ' ')}
    </span>
  )
}

// ─── Shared style tokens ──────────────────────────────────────────────────────

const inp: React.CSSProperties = {
  width: '100%', padding: '9px 12px',
  border: '1px solid #E2E8F0', borderRadius: 8,
  fontSize: 13, boxSizing: 'border-box', background: '#fff', outline: 'none',
}

const lbl: React.CSSProperties = {
  display: 'block', fontSize: 11,
  fontWeight: 600, color: '#374151', marginBottom: 3,
}

const TH: React.CSSProperties = {
  padding: '9px 14px', textAlign: 'left',
  fontSize: 11, fontWeight: 600, color: '#64748B',
  letterSpacing: '0.05em', borderBottom: '1px solid #E2E8F0',
  whiteSpace: 'nowrap', background: '#F8FAFC',
}

const TD: React.CSSProperties = {
  padding: '10px 14px', fontSize: 13, borderBottom: '1px solid #F1F5F9',
}

const btnPrimary = (bg = '#1B3A6B'): React.CSSProperties => ({
  display: 'flex', alignItems: 'center', gap: 6,
  background: bg, color: '#fff',
  border: 'none', borderRadius: 8,
  padding: '9px 15px', fontSize: 13, fontWeight: 600, cursor: 'pointer',
})

const btnSecondary: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: 6,
  background: '#F8FAFC', color: '#374151',
  border: '1px solid #E2E8F0', borderRadius: 8,
  padding: '9px 15px', fontSize: 13, cursor: 'pointer',
}

const btnCancel: React.CSSProperties = {
  padding: '9px 15px', border: '1px solid #E2E8F0',
  borderRadius: 8, background: '#fff',
  fontSize: 13, cursor: 'pointer', color: '#374151',
}

const MODAL_BG: React.CSSProperties = {
  position: 'fixed', inset: 0,
  background: 'rgba(15,23,42,0.5)',
  display: 'flex', alignItems: 'center', justifyContent: 'center',
  zIndex: 1000,
}

const modalBox = (w = 480): React.CSSProperties => ({
  background: '#fff', borderRadius: 16,
  padding: 28, width: w,
  maxHeight: '90vh', overflowY: 'auto',
  boxShadow: '0 20px 60px rgba(0,0,0,0.18)',
})

const modalHeader = (title: string, onClose: () => void) => (
  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
    <h3 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: '#0F172A' }}>{title}</h3>
    <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer' }}>
      <X size={18} color="#94A3B8" />
    </button>
  </div>
)

const ErrMsg = ({ msg }: { msg: string }) =>
  msg ? (
    <div style={{ marginTop: 10, padding: '8px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626' }}>
      {msg}
    </div>
  ) : null

// ═══════════════════════════════════════════════════════════════════════════════
// PosPage
// ═══════════════════════════════════════════════════════════════════════════════


// ── CatalogueCombo — searchable catalogue dropdown ───────────────────────────
// Replaces the plain <select> in stock and PO modals.
// Groups items alphabetically. Filters by name or category as user types.
function CatalogueCombo({
  items,
  value,
  onChange,
  placeholder = 'Search catalogue…',
}: {
  items: { id: string; name: string; defaultPrice?: number }[]
  value: string
  onChange: (id: string, name: string) => void
  placeholder?: string
}) {
  const [open, setOpen]   = React.useState(false)
  const [query, setQuery] = React.useState('')
  const containerRef      = React.useRef<HTMLDivElement>(null)

  React.useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node))
        setOpen(false)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  const selected = items.find(i => i.id === value)
  // Fuzzy match: strip spaces/punctuation from both sides before comparing
  // so "redbull" matches "Red Bull 250ml" and "labgloves" matches "Labourers Gloves"
  const normalise = (s: string) => s.toLowerCase().replace(/[^a-z0-9]/g, '')
  const filtered = items.filter(i =>
    !query ||
    i.name.toLowerCase().includes(query.toLowerCase()) ||
    normalise(i.name).includes(normalise(query))
  )
  const grouped = query
    ? { '': filtered }
    : filtered.reduce<Record<string, typeof filtered>>((acc, item) => {
        const letter = item.name[0]?.toUpperCase() ?? '#'
        if (!acc[letter]) acc[letter] = []
        acc[letter].push(item)
        return acc
      }, {})

  return (
    <div ref={containerRef} style={{ position: 'relative' }}>
      <div
        onClick={() => { setOpen(!open); setQuery('') }}
        style={{
          width: '100%', padding: '9px 12px',
          border: `1px solid ${open ? '#1B3A6B' : '#E2E8F0'}`,
          borderRadius: 8, fontSize: 13, boxSizing: 'border-box' as const,
          background: '#fff', cursor: 'pointer',
          display: 'flex', justifyContent: 'space-between', alignItems: 'center',
          color: selected ? '#0F172A' : '#94A3B8',
          userSelect: 'none' as const,
        }}
      >
        <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' as const, flex: 1 }}>
          {selected ? selected.name : placeholder}
        </span>
        <span style={{ fontSize: 10, color: '#94A3B8', marginLeft: 6, flexShrink: 0 }}>▼</span>
      </div>

      {open && (
        <div style={{
          position: 'absolute', top: 'calc(100% + 4px)', left: 0, right: 0,
          background: '#fff', border: '1px solid #E2E8F0', borderRadius: 10,
          boxShadow: '0 8px 24px rgba(0,0,0,0.12)', zIndex: 300,
          maxHeight: 280, display: 'flex', flexDirection: 'column',
        }}>
          <div style={{ padding: '8px 10px', borderBottom: '1px solid #F1F5F9', flexShrink: 0 }}>
            <input
              autoFocus
              value={query}
              onChange={e => setQuery(e.target.value)}
              placeholder="Type to filter…"
              onClick={e => e.stopPropagation()}
              style={{
                width: '100%', padding: '6px 10px',
                border: '1px solid #E2E8F0', borderRadius: 6,
                fontSize: 12, outline: 'none', boxSizing: 'border-box' as const,
              }}
            />
          </div>
          <div style={{ overflowY: 'auto' as const, flex: 1 }}>
            {filtered.length === 0 ? (
              <div style={{ padding: '14px 12px', fontSize: 12, color: '#94A3B8', textAlign: 'center' as const }}>
                No items match "{query}"
              </div>
            ) : (
              Object.entries(grouped).map(([letter, groupItems]) => (
                <div key={letter}>
                  {letter && (
                    <div style={{
                      padding: '4px 12px 2px',
                      fontSize: 10, fontWeight: 700,
                      color: '#94A3B8', background: '#F8FAFC',
                      letterSpacing: '0.07em',
                      textTransform: 'uppercase' as const,
                      position: 'sticky' as const, top: 0,
                    }}>
                      {letter}
                    </div>
                  )}
                  {groupItems.map(item => (
                    <div
                      key={item.id}
                      onMouseDown={e => {
                        e.preventDefault()
                        onChange(item.id, item.name)
                        setOpen(false)
                        setQuery('')
                      }}
                      style={{
                        padding: '9px 12px', cursor: 'pointer',
                        background: item.id === value ? '#EFF6FF' : 'transparent',
                        fontSize: 13,
                        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                        transition: 'background 0.08s',
                      }}
                      onMouseEnter={e => {
                        if (item.id !== value)
                          (e.currentTarget as HTMLElement).style.background = '#F0FDF4'
                      }}
                      onMouseLeave={e => {
                        if (item.id !== value)
                          (e.currentTarget as HTMLElement).style.background = 'transparent'
                      }}
                    >
                      <span style={{ color: '#0F172A' }}>{item.name}</span>
                      {item.defaultPrice != null && (
                        <span style={{ fontSize: 11, color: '#0D9488', fontWeight: 600, marginLeft: 8, flexShrink: 0 }}>
                          R {Number(item.defaultPrice).toFixed(2)}
                        </span>
                      )}
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

export function PosPage() {
  const qc = useQueryClient()

  // ── UI state ────────────────────────────────────────────────────────────────
  const [tab,         setTab]       = useState<Tab>('sell')
  const [cart,        setCart]      = useState<CartItem[]>([])
  const [payment,     setPayment]   = useState('CASH')
  const [tendered,    setTendered]  = useState('')
  const [paymentRef,  setPaymentRef] = useState('')
  const [customer,    setCustomer]  = useState('')
  const [saleErr,     setSaleErr]   = useState('')
  const [itemSearch,  setItemSearch]= useState('')
  const [errMsg,      setErrMsg]    = useState('')
  // localSession shadows the RQ session query for instant banner updates
  // (bypasses staleTime: 30_000 in QueryClient which delays re-renders)
  const [localSession, setLocalSession] = useState<CashSession | null | 'loading'>('loading')

  // modal visibility
  const [cashModal,    setCashModal]   = useState<'open' | 'close' | null>(null)
  const [showStock,    setShowStock]   = useState(false)
  const [showPO,       setShowPO]      = useState(false)
  const [showReceive,  setShowReceive] = useState<PurchaseOrder | null>(null)
  const [showRefund,   setShowRefund]  = useState<Transaction | null>(null)
  const [showReceipt,  setShowReceipt] = useState<Transaction | null>(null)
  const [expandedTxn,  setExpanded]    = useState<string | null>(null)

  // form state
  const [openFloat,  setOpenFloat]  = useState('500')
  const [closeFloat, setCloseFloat] = useState('')
  const [sNotes,     setSNotes]     = useState('')
  const [stockForm,  setStockForm]  = useState({
    catalogueItemId: '', qtyOnHand: '', reorderLevel: '', costPrice: '', location: '',
  })
  const [poForm, setPOForm] = useState({ supplierName: '', expectedDate: '', notes: '' })
  const [poLines, setPOLines] = useState([{ catalogueItemId: '', itemName: '', qtyOrdered: '', unitCost: '' }])
  const [refundLines,  setRefundLines]  = useState<Record<string, string>>({})
  const [refundReason, setRefundReason] = useState('')
  const [receiveQtys,  setReceiveQtys]  = useState<Record<string, string>>({})

  // ── Queries ─────────────────────────────────────────────────────────────────

  const { data: summary } = useQuery({
    queryKey: ['pos-summary'],
    queryFn: async () => {
      const r = await apiClient.get('/api/v1/pos/summary')
      return r.data?.data ?? r.data
    },
    refetchInterval: 30_000,
  })

  const { data: stock = [], isLoading: stockLoading } = useQuery<StockItem[]>({
    queryKey: ['pos-stock'],
    queryFn: async () => unwrap(await apiClient.get('/api/v1/pos/stock?size=200')),
  })

  const { data: transactions = [] } = useQuery<Transaction[]>({
    queryKey: ['pos-transactions'],
    queryFn: async () => unwrap(await apiClient.get('/api/v1/pos/transactions?size=50')),
    enabled: tab === 'transactions',
  })

  const { data: purchaseOrders = [] } = useQuery<PurchaseOrder[]>({
    queryKey: ['pos-orders'],
    queryFn: async () => unwrap(await apiClient.get('/api/v1/pos/purchase-orders?size=50')),
    enabled: tab === 'orders',
  })

  const { data: openSession, isLoading: sessionLoading } = useQuery<CashSession | null | undefined>({
    queryKey: ['pos-session'],
    queryFn: async () => {
      try {
        const r = await apiClient.get('/api/v1/pos/cash-sessions/current')
        return r.data?.data ?? null
      } catch {
        return null
      }
    },
    refetchInterval: 60_000,
  })

  // Sync RQ session data into localSession when it arrives from the server
  // This handles page refresh / initial load
  React.useEffect(() => {
    if (openSession !== undefined) {
      setLocalSession(openSession ?? null)
    }
  }, [openSession])

  const { data: catalogueItems = [] } = useQuery({
    queryKey: ['catalogue-items-pos'],
    queryFn: async () => {
      try {
        const r = await apiClient.get('/api/v1/catalogue/items')
        const payload = r.data?.data ?? r.data
        // CatalogueController returns List<T> (plain array), NOT Page<T>
        // unwrap() looks for .content which doesn't exist → returns []
        if (Array.isArray(payload)) return payload
        if (Array.isArray(payload?.content)) return payload.content
        return []
      } catch {
        return []
      }
    },
  })

  // ── Invalidation helper ──────────────────────────────────────────────────────

  const invalidateAll = useCallback(() => {
    qc.invalidateQueries({ queryKey: ['pos-summary'] })
    qc.invalidateQueries({ queryKey: ['pos-stock'] })
    qc.invalidateQueries({ queryKey: ['pos-transactions'] })
    qc.invalidateQueries({ queryKey: ['pos-session'] })
  }, [qc])

  // ── Mutations ────────────────────────────────────────────────────────────────

  const saleMut = useMutation({
    mutationFn: (body: any) => apiClient.post('/api/v1/pos/sell', body),
    onSuccess: (r) => {
      invalidateAll()
      setCart([]); setTendered(''); setCustomer(''); setPaymentRef(''); setSaleErr('')
      setShowReceipt(r.data?.data ?? r.data)
    },
    onError: (e: any) => setSaleErr(extractError(e)),
  })

  const openSeshMut = useMutation({
    mutationFn: (body: any) => apiClient.post('/api/v1/pos/cash-sessions/open', body),
    onSuccess: (res) => {
      const session = res.data?.data ?? res.data
      if (session?.id) {
        setLocalSession(session)
        qc.setQueryData(['pos-session'], session)
      }
      setCashModal(null); setErrMsg('')
      qc.invalidateQueries({ queryKey: ['pos-summary'] })
    },
    onError: (e: any) => {
      const msg = extractError(e)
      if (msg.toLowerCase().includes('already open') || msg.toLowerCase().includes('session')) {
        // Close modal immediately — no await so React renders synchronously
        setCashModal(null)
        // Fetch existing session in background and update banner via .then()
        apiClient.get('/api/v1/pos/cash-sessions/current')
          .then(r => {
            const existing = r.data?.data ?? null
            if (existing?.id) {
              setLocalSession(existing)
              qc.setQueryData(['pos-session'], existing)
            }
          })
          .catch(() => { /* session fetch failed — banner stays orange */ })
        qc.invalidateQueries({ queryKey: ['pos-summary'] })
      } else {
        setErrMsg(msg)
      }
    },
  })

  const closeSeshMut = useMutation({
    mutationFn: ({ id, body }: any) =>
      apiClient.post(`/api/v1/pos/cash-sessions/${id}/close`, body),
    onSuccess: () => {
      setLocalSession(null)             // instant orange banner
      qc.setQueryData(['pos-session'], null)
      setCashModal(null); setErrMsg('')
      invalidateAll()
    },
    onError: (e: any) => setErrMsg(extractError(e)),
  })

  const createStockMut = useMutation({
    mutationFn: (body: any) => apiClient.post('/api/v1/pos/stock', body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['pos-stock'] })
      setShowStock(false); setErrMsg('')
      setStockForm({ catalogueItemId: '', qtyOnHand: '', reorderLevel: '', costPrice: '', location: '' })
    },
    onError: (e: any) => setErrMsg(extractError(e)),
  })

  const createPOMut = useMutation({
    mutationFn: (body: any) => apiClient.post('/api/v1/pos/purchase-orders', body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['pos-orders'] }); setShowPO(false); setErrMsg('') },
    onError: (e: any) => setErrMsg(extractError(e)),
  })

  const receiveMut = useMutation({
    mutationFn: ({ id, body }: any) =>
      apiClient.post(`/api/v1/pos/purchase-orders/${id}/receive`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['pos-orders'] })
      qc.invalidateQueries({ queryKey: ['pos-stock'] })
      setShowReceive(null); setReceiveQtys({})
    },
    onError: (e: any) => setErrMsg(extractError(e)),
  })

  const refundMut = useMutation({
    mutationFn: ({ id, body }: any) =>
      apiClient.post(`/api/v1/pos/transactions/${id}/refund`, body),
    onSuccess: () => {
      invalidateAll()
      setShowRefund(null); setRefundLines({}); setRefundReason(''); setErrMsg('')
    },
    onError: (e: any) => setErrMsg(extractError(e)),
  })

  // ── Cart helpers ─────────────────────────────────────────────────────────────

  const addToCart = useCallback((item: StockItem) => {
    setCart(prev => {
      const existing = prev.find(x => x.catalogueItemId === item.catalogueItemId)
      if (existing)
        return prev.map(x =>
          x.catalogueItemId === item.catalogueItemId
            ? { ...x, qty: x.qty + 1 }
            : x,
        )
      return [...prev, {
        catalogueItemId: item.catalogueItemId,
        itemName: item.itemName,
        unitPrice: item.sellingPrice,
        qty: 1,
        discountPct: 0,
      }]
    })
  }, [])

  const removeFromCart = (id: string) =>
    setCart(c => c.filter(x => x.catalogueItemId !== id))

  const updateQty = (id: string, q: number) => {
    if (q <= 0) removeFromCart(id)
    else setCart(c => c.map(x => x.catalogueItemId === id ? { ...x, qty: q } : x))
  }

  const updateDiscount = (id: string, d: number) =>
    setCart(c => c.map(x =>
      x.catalogueItemId === id
        ? { ...x, discountPct: Math.min(100, Math.max(0, d)) }
        : x,
    ))

  const handleBarcodeScan = async (code: string) => {
    if (!code.trim()) return
    const found = stock.find(
      s => s.barcode === code.trim() || s.sku === code.trim(),
    )
    if (found) { addToCart(found); setItemSearch(''); return }
    try {
      const r = await apiClient.get(`/api/v1/pos/barcode/${encodeURIComponent(code.trim())}`)
      const item = r.data?.data ?? r.data
      const si = stock.find(s => s.catalogueItemId === item?.catalogueItemId)
      if (si) addToCart(si)
      else setSaleErr(`No stock item for barcode: ${code}`)
    } catch {
      setSaleErr(`Item not found: ${code}`)
    }
    setItemSearch('')
  }

  // ── Totals ───────────────────────────────────────────────────────────────────

  const totals = cart.reduce(
    (acc, item) => {
      const base   = item.unitPrice * item.qty
      const disc   = base * (item.discountPct / 100)
      const net    = base - disc
      const vat    = net * VAT_RATE
      return {
        subtotal: acc.subtotal + base,
        discount: acc.discount + disc,
        vat:      acc.vat + vat,
        total:    acc.total + net + vat,
      }
    },
    { subtotal: 0, discount: 0, vat: 0, total: 0 },
  )

  const change = payment === 'CASH' && tendered
    ? Number(tendered) - totals.total
    : null

  const canCharge =
    cart.length > 0 &&
    !saleMut.isPending &&
    !(payment === 'CASH' && tendered !== '' && Number(tendered) < totals.total)

  const visibleStock = stock.filter(s => {
    if (!itemSearch) return true
    const q = itemSearch.toLowerCase()
    return (
      s.itemName.toLowerCase().includes(q) ||
      (s.sku ?? '').toLowerCase().includes(q) ||
      (s.barcode ?? '').includes(q)
    )
  })

  // ── Stats strip ──────────────────────────────────────────────────────────────

  const STATS = [
    { label: 'Sales today',    value: fmtR(summary?.salesToday ?? 0),    color: '#0D9488', Icon: TrendingUp   },
    { label: 'Transactions',   value: summary?.transactionsToday ?? 0,    color: '#1B3A6B', Icon: Receipt      },
    { label: 'Stock items',    value: summary?.totalStockItems ?? 0,      color: '#475569', Icon: Package      },
    { label: 'Low stock',      value: summary?.lowStockItems ?? 0,        color: '#DC2626', Icon: AlertTriangle },
    { label: 'Pending orders', value: summary?.pendingOrders ?? 0,        color: '#D97706', Icon: Truck        },
  ]

  // ─────────────────────────────────────────────────────────────────────────────
  // RENDER
  // ─────────────────────────────────────────────────────────────────────────────

  return (
    <div>

      {/* ── Page header ─────────────────────────────────────────────────────── */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: 20 }}>
        <div>
          <h1 style={{ fontSize: 22, fontWeight: 800, color: '#0F172A', margin: '0 0 2px' }}>
            POS & Stock
          </h1>
          <p style={{ fontSize: 12, color: '#94A3B8', margin: 0 }}>
            Point of sale · Inventory · Purchase orders
          </p>
        </div>
        <button onClick={invalidateAll} style={btnSecondary} title="Refresh all data">
          <RefreshCw size={14} />
        </button>
      </div>

      {/* ── Stats ───────────────────────────────────────────────────────────── */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 10, marginBottom: 20 }}>
        {STATS.map(({ label, value, color, Icon }) => (
          <div key={label} style={{
            background: '#fff', border: '1px solid #E2E8F0',
            borderRadius: 12, padding: '14px 16px',
            display: 'flex', alignItems: 'flex-start', gap: 10,
          }}>
            <div style={{
              width: 32, height: 32, borderRadius: 8,
              background: `${color}18`,
              display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
            }}>
              <Icon size={15} color={color} />
            </div>
            <div>
              <div style={{
                fontSize: typeof value === 'string' ? 15 : 22,
                fontWeight: 800, color, lineHeight: 1.1,
              }}>{value}</div>
              <div style={{ fontSize: 10, color: '#94A3B8', marginTop: 2 }}>{label}</div>
            </div>
          </div>
        ))}
      </div>

      {/* ── Main card ───────────────────────────────────────────────────────── */}
      <div style={{ background: '#fff', border: '1px solid #E2E8F0', borderRadius: 14, padding: 24 }}>

        {/* Cash session banner */}
        {localSession === 'loading' ? null : localSession == null ? (
          <div style={{
            display: 'flex', alignItems: 'center', justifyContent: 'space-between',
            padding: '10px 16px', background: '#FFFBEB',
            border: '1px solid #FDE68A', borderRadius: 10, marginBottom: 16,
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <AlertTriangle size={14} color="#D97706" />
              <span style={{ fontSize: 13, color: '#92400E', fontWeight: 500 }}>
                No cash session open — CASH sales are blocked.
              </span>
            </div>
            <button
              onClick={() => { setCashModal('open'); setErrMsg('') }}
              style={{ ...btnPrimary('#D97706'), padding: '6px 14px', fontSize: 12 }}>
              Open Session
            </button>
          </div>
        ) : (
          <div style={{
            display: 'flex', alignItems: 'center', justifyContent: 'space-between',
            padding: '10px 16px', background: '#F0FDF4',
            border: '1px solid #86EFAC', borderRadius: 10, marginBottom: 16,
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <CheckCircle size={14} color="#166534" />
              <span style={{ fontSize: 13, color: '#166534', fontWeight: 600 }}>
                Session {(localSession as CashSession).sessionNumber}
              </span>
              <span style={{ fontSize: 12, color: '#166534', opacity: 0.7 }}>
                · Float {fmtR((localSession as CashSession).openingFloat)}
                · {(localSession as CashSession).transactionCount} txns
                · {fmtR((localSession as CashSession).totalSales)} total
              </span>
            </div>
            <button
              onClick={() => { setCashModal('close'); setCloseFloat(''); setErrMsg('') }}
              style={{ ...btnSecondary, padding: '6px 14px', fontSize: 12 }}>
              Cash Up
            </button>
          </div>
        )}

        {/* Tab bar */}
        <div style={{ display: 'flex', gap: 2, borderBottom: '1px solid #E2E8F0', marginBottom: 22 }}>
          {([
            { id: 'sell',         label: 'POS Terminal',    Icon: ShoppingCart },
            { id: 'stock',        label: 'Stock',           Icon: Package      },
            { id: 'transactions', label: 'Transactions',    Icon: Receipt      },
            { id: 'orders',       label: 'Purchase Orders', Icon: Truck        },
          ] as { id: Tab; label: string; Icon: any }[]).map(t => (
            <button key={t.id} onClick={() => setTab(t.id)} style={{
              display: 'flex', alignItems: 'center', gap: 6,
              padding: '9px 16px', background: 'none', border: 'none',
              borderBottom: tab === t.id ? '2px solid #0D9488' : '2px solid transparent',
              color: tab === t.id ? '#0D9488' : '#64748B',
              fontWeight: tab === t.id ? 700 : 400,
              fontSize: 13, cursor: 'pointer', marginBottom: -1,
            }}>
              <t.Icon size={13} />{t.label}
            </button>
          ))}
        </div>

        {/* ════════════════════════════════════════════════════════════════════ */}
        {/* SELL TAB                                                            */}
        {/* ════════════════════════════════════════════════════════════════════ */}
        {tab === 'sell' && (
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 375px', gap: 20 }}>

            {/* Left — item grid */}
            <div>
              {/* Search / barcode bar */}
              <div style={{ display: 'flex', gap: 8, marginBottom: 14 }}>
                <div style={{ flex: 1, position: 'relative' }}>
                  <Search size={13} style={{
                    position: 'absolute', left: 10, top: '50%',
                    transform: 'translateY(-50%)', color: '#94A3B8',
                  }} />
                  <input
                    value={itemSearch}
                    onChange={e => setItemSearch(e.target.value)}
                    onKeyDown={e => e.key === 'Enter' && handleBarcodeScan(itemSearch)}
                    placeholder="Search items or scan barcode…"
                    style={{ ...inp, paddingLeft: 32 }}
                  />
                </div>
                <button
                  onClick={() => handleBarcodeScan(itemSearch)}
                  style={{ ...btnSecondary, padding: '9px 12px' }}
                  title="Confirm barcode">
                  <Scan size={14} />
                </button>
              </div>

              {/* Product grid */}
              <div style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fill, minmax(145px, 1fr))',
                gap: 8,
              }}>
                {visibleStock.filter(s => s.availableQty > 0).map(item => (
                  <button
                    key={item.id}
                    onClick={() => addToCart(item)}
                    style={{
                      padding: 12, border: '1px solid #E2E8F0',
                      borderRadius: 10, background: '#fff',
                      cursor: 'pointer', textAlign: 'left', transition: 'all 0.12s',
                    }}
                    onMouseEnter={e => {
                      const el = e.currentTarget as HTMLButtonElement
                      el.style.borderColor = '#0D9488'
                      el.style.background  = '#F0FDF4'
                    }}
                    onMouseLeave={e => {
                      const el = e.currentTarget as HTMLButtonElement
                      el.style.borderColor = '#E2E8F0'
                      el.style.background  = '#fff'
                    }}
                  >
                    <div style={{ fontSize: 12, fontWeight: 700, color: '#0F172A', marginBottom: 3, lineHeight: 1.3 }}>
                      {item.itemName}
                    </div>
                    <div style={{ fontSize: 15, fontWeight: 800, color: '#0D9488' }}>
                      {fmtR(item.sellingPrice)}
                    </div>
                    <div style={{
                      fontSize: 10, marginTop: 2,
                      color: item.lowStock ? '#DC2626' : '#94A3B8',
                      fontWeight: item.lowStock ? 700 : 400,
                    }}>
                      {item.lowStock ? `⚠ Low: ${item.availableQty}` : `Stock: ${item.availableQty}`}
                    </div>
                  </button>
                ))}

                {visibleStock.filter(s => s.availableQty > 0).length === 0 && (
                  <div style={{ gridColumn: '1/-1', padding: '50px 0', textAlign: 'center', color: '#CBD5E1' }}>
                    <Package size={30} style={{ marginBottom: 10, opacity: 0.3 }} />
                    <div style={{ fontSize: 13, color: '#94A3B8', fontWeight: 500 }}>
                      {itemSearch ? 'No match found' : 'No stock items yet — add some in the Stock tab'}
                    </div>
                  </div>
                )}
              </div>
            </div>

            {/* Right — cart panel */}
            <div style={{
              border: '1px solid #E2E8F0', borderRadius: 12,
              padding: 16, display: 'flex', flexDirection: 'column',
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                <span style={{ fontSize: 14, fontWeight: 700, color: '#0F172A' }}>Current Sale</span>
                {cart.length > 0 && (
                  <button onClick={() => setCart([])} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', fontSize: 12 }}>
                    Clear
                  </button>
                )}
              </div>

              {/* Cart items */}
              <div style={{ flex: 1, marginBottom: 12, minHeight: 80 }}>
                {cart.length === 0 ? (
                  <div style={{ textAlign: 'center', padding: '40px 0', color: '#CBD5E1' }}>
                    <ShoppingCart size={26} style={{ marginBottom: 8, opacity: 0.3 }} />
                    <div style={{ fontSize: 12 }}>Tap items or scan barcode</div>
                  </div>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    {cart.map(item => (
                      <div key={item.catalogueItemId} style={{ background: '#F8FAFC', borderRadius: 8, padding: '10px 12px' }}>
                        {/* Item row */}
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 6 }}>
                          <span style={{ fontSize: 12, fontWeight: 600, color: '#0F172A', flex: 1, marginRight: 6 }}>
                            {item.itemName}
                          </span>
                          <button
                            onClick={() => removeFromCart(item.catalogueItemId)}
                            style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#CBD5E1', padding: 0 }}>
                            <X size={11} />
                          </button>
                        </div>
                        {/* Qty + total */}
                        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                          <button
                            onClick={() => updateQty(item.catalogueItemId, item.qty - 1)}
                            style={{ width: 22, height: 22, borderRadius: 4, border: '1px solid #E2E8F0', background: '#fff', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                            <Minus size={10} />
                          </button>
                          <span style={{ fontSize: 13, fontWeight: 700, minWidth: 20, textAlign: 'center' }}>
                            {item.qty}
                          </span>
                          <button
                            onClick={() => updateQty(item.catalogueItemId, item.qty + 1)}
                            style={{ width: 22, height: 22, borderRadius: 4, border: '1px solid #E2E8F0', background: '#fff', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                            <Plus size={10} />
                          </button>
                          <span style={{ fontSize: 11, color: '#94A3B8', flex: 1, textAlign: 'right' }}>
                            @ {fmtR(item.unitPrice)}
                          </span>
                          <span style={{ fontSize: 13, fontWeight: 700, color: '#0F172A', minWidth: 65, textAlign: 'right' }}>
                            {fmtR(item.unitPrice * item.qty * (1 - item.discountPct / 100))}
                          </span>
                        </div>
                        {/* Per-item discount */}
                        <div style={{ display: 'flex', alignItems: 'center', gap: 5, marginTop: 5 }}>
                          <span style={{ fontSize: 10, color: '#94A3B8' }}>Disc %</span>
                          <input
                            type="number" min="0" max="100"
                            value={item.discountPct || ''}
                            onChange={e => updateDiscount(item.catalogueItemId, Number(e.target.value))}
                            placeholder="0"
                            style={{ width: 50, padding: '2px 6px', border: '1px solid #E2E8F0', borderRadius: 4, fontSize: 11, textAlign: 'center', background: '#fff' }}
                          />
                          {item.discountPct > 0 && (
                            <span style={{ fontSize: 10, color: '#0D9488' }}>
                              − {fmtR(item.unitPrice * item.qty * item.discountPct / 100)}
                            </span>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Totals */}
              {cart.length > 0 && (
                <div style={{ borderTop: '1px solid #F1F5F9', paddingTop: 10, marginBottom: 10 }}>
                  {totals.discount > 0 && (
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, color: '#0D9488', marginBottom: 2 }}>
                      <span>Discount</span><span>− {fmtR(totals.discount)}</span>
                    </div>
                  )}
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, color: '#64748B', marginBottom: 2 }}>
                    <span>Subtotal (excl. VAT)</span><span>{fmtR(totals.subtotal - totals.discount)}</span>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, color: '#64748B', marginBottom: 7 }}>
                    <span>VAT (15%)</span><span>{fmtR(totals.vat)}</span>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 18, fontWeight: 800, color: '#0F172A', borderTop: '1px solid #E2E8F0', paddingTop: 8 }}>
                    <span>Total</span><span>{fmtR(totals.total)}</span>
                  </div>
                </div>
              )}

              {/* Payment method + tendered */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 10 }}>
                <input
                  value={customer}
                  onChange={e => setCustomer(e.target.value)}
                  placeholder="Customer name (optional)"
                  style={{ ...inp, fontSize: 12 }}
                />
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 5 }}>
                  {['CASH', 'CARD', 'EFT', 'ACCOUNT'].map(m => (
                    <button key={m} onClick={() => setPayment(m)} style={{
                      padding: '7px 4px',
                      border: `1px solid ${payment === m ? '#1B3A6B' : '#E2E8F0'}`,
                      borderRadius: 7, fontSize: 11,
                      fontWeight: payment === m ? 700 : 400,
                      background: payment === m ? '#1B3A6B' : '#fff',
                      color: payment === m ? '#fff' : '#475569',
                      cursor: 'pointer',
                    }}>
                      {m}
                    </button>
                  ))}
                </div>
                {payment === 'CASH' && (
                  <input
                    type="number"
                    value={tendered}
                    onChange={e => setTendered(e.target.value)}
                    placeholder={`Tendered (min ${fmtR(totals.total)})`}
                    style={{ ...inp, fontSize: 12 }}
                  />
                )}
                {(payment === 'EFT' || payment === 'CARD') && (
                  <input
                    value={paymentRef}
                    onChange={e => setPaymentRef(e.target.value)}
                    placeholder={
                      payment === 'EFT'
                        ? 'EFT reference / proof of payment number'
                        : 'Card authorisation code (optional)'
                    }
                    style={{ ...inp, fontSize: 12 }}
                  />
                )}
                {change !== null && change >= 0 && (
                  <div style={{ padding: '8px 12px', background: '#DCFCE7', borderRadius: 8, fontSize: 14, fontWeight: 700, color: '#166534', textAlign: 'center' }}>
                    Change: {fmtR(change)}
                  </div>
                )}
              </div>

              {saleErr && (
                <div style={{ color: '#DC2626', fontSize: 12, marginBottom: 8, padding: '6px 10px', background: '#FEF2F2', borderRadius: 6 }}>
                  {saleErr}
                </div>
              )}

              <button
                disabled={!canCharge}
                onClick={() => saleMut.mutate({
                  customerName: customer || null,
                  paymentMethod: payment,
                  amountTendered: tendered ? Number(tendered) : null,
                  paymentRef: paymentRef || null,
                  items: cart.map(i => ({
                    catalogueItemId: i.catalogueItemId,
                    qty: i.qty,
                    unitPrice: i.unitPrice,
                    discountPct: i.discountPct,
                  })),
                })}
                style={{
                  background: canCharge ? '#0D9488' : '#94A3B8',
                  color: '#fff', border: 'none', borderRadius: 10,
                  padding: '13px', fontSize: 15, fontWeight: 800,
                  cursor: canCharge ? 'pointer' : 'not-allowed', width: '100%',
                }}>
                {saleMut.isPending ? 'Processing…' : `Charge ${fmtR(totals.total)}`}
              </button>
            </div>
          </div>
        )}

        {/* ════════════════════════════════════════════════════════════════════ */}
        {/* STOCK TAB                                                           */}
        {/* ════════════════════════════════════════════════════════════════════ */}
        {tab === 'stock' && (
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
              <div style={{ display: 'flex', gap: 8 }}>
                {(summary?.lowStockItems ?? 0) > 0 && (
                  <span style={{ display: 'flex', alignItems: 'center', gap: 4, background: '#FEF2F2', color: '#DC2626', padding: '5px 12px', borderRadius: 20, fontSize: 11, fontWeight: 600 }}>
                    <AlertTriangle size={11} /> {summary.lowStockItems} low stock
                  </span>
                )}
              </div>
              <button onClick={() => { setShowStock(true); setErrMsg('') }} style={btnPrimary()}>
                <Plus size={14} /> Add Stock Item
              </button>
            </div>

            {stockLoading ? (
              <div style={{ textAlign: 'center', padding: 40, color: '#94A3B8' }}>Loading…</div>
            ) : stock.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '50px 0', color: '#94A3B8' }}>
                <Package size={34} style={{ marginBottom: 12, opacity: 0.3 }} />
                <div style={{ fontWeight: 600, color: '#475569' }}>No stock items yet</div>
                <div style={{ fontSize: 12, marginTop: 3 }}>Add catalogue items to start tracking inventory.</div>
              </div>
            ) : (
              <div style={{ border: '1px solid #E2E8F0', borderRadius: 10, overflow: 'hidden' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                  <thead>
                    <tr>
                      {['Item', 'Available', 'On Hand', 'Reorder At', 'Cost', 'Sell Price', 'Location', 'Status'].map(h => (
                        <th key={h} style={TH}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {stock.map((s, i) => (
                      <tr key={s.id} style={{ background: i % 2 === 0 ? '#fff' : '#FAFAFA' }}>
                        <td style={TD}>
                          <div style={{ fontWeight: 600, fontSize: 13, color: '#0F172A' }}>{s.itemName}</div>
                          {s.sku && <div style={{ fontSize: 10, color: '#94A3B8' }}>SKU: {s.sku}</div>}
                        </td>
                        <td style={TD}>
                          <span style={{ fontWeight: 800, fontSize: 15, color: s.lowStock ? '#DC2626' : '#0F172A' }}>
                            {s.availableQty}
                          </span>
                        </td>
                        <td style={TD}><span style={{ fontSize: 12, color: '#64748B' }}>{s.qtyOnHand}</span></td>
                        <td style={TD}><span style={{ fontSize: 12, color: '#64748B' }}>{s.reorderLevel}</span></td>
                        <td style={TD}><span style={{ fontSize: 12, color: '#64748B' }}>{fmtR(s.costPrice)}</span></td>
                        <td style={TD}><span style={{ fontWeight: 700, color: '#0D9488' }}>{fmtR(s.sellingPrice)}</span></td>
                        <td style={TD}><span style={{ fontSize: 11, color: '#94A3B8' }}>{s.location || '—'}</span></td>
                        <td style={TD}>
                          {s.lowStock ? (
                            <span style={{ display: 'flex', alignItems: 'center', gap: 3, background: '#FEF2F2', color: '#DC2626', padding: '2px 8px', borderRadius: 20, fontSize: 10, fontWeight: 700, width: 'fit-content' }}>
                              <AlertTriangle size={9} /> LOW
                            </span>
                          ) : (
                            <span style={{ background: '#DCFCE7', color: '#166534', padding: '2px 8px', borderRadius: 20, fontSize: 10, fontWeight: 600 }}>
                              OK
                            </span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}

        {/* ════════════════════════════════════════════════════════════════════ */}
        {/* TRANSACTIONS TAB                                                    */}
        {/* ════════════════════════════════════════════════════════════════════ */}
        {tab === 'transactions' && (
          <div>
            {transactions.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '50px 0', color: '#94A3B8' }}>
                <Receipt size={34} style={{ marginBottom: 12, opacity: 0.3 }} />
                <div style={{ fontWeight: 600, color: '#475569' }}>No transactions yet</div>
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                {transactions.map(t => {
                  const open = expandedTxn === t.id
                  return (
                    <div key={t.id} style={{ border: '1px solid #E2E8F0', borderRadius: 10, overflow: 'hidden' }}>
                      {/* Row header */}
                      <div
                        onClick={() => setExpanded(open ? null : t.id)}
                        style={{ display: 'flex', alignItems: 'center', padding: '12px 16px', cursor: 'pointer', background: open ? '#F8FAFC' : '#fff', gap: 12 }}>
                        <div style={{ flex: 1 }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: 7, marginBottom: 2 }}>
                            <span style={{ fontWeight: 700, fontSize: 13, color: '#0F172A' }}>{t.transactionNumber}</span>
                            <StatusBadge status={t.status} />
                            <span style={{ fontSize: 10, color: '#64748B', background: '#F1F5F9', padding: '1px 6px', borderRadius: 20 }}>
                              {t.paymentMethod}
                            </span>
                          </div>
                          <div style={{ fontSize: 11, color: '#94A3B8' }}>
                            {t.customerName || 'Walk-in'} · {t.servedByName || '—'} · {fmtDate(t.createdAt)}
                            {t.cashSessionNumber && <> · {t.cashSessionNumber}</>}
                          </div>
                        </div>
                        <div style={{ textAlign: 'right', flexShrink: 0 }}>
                          <div style={{ fontWeight: 800, fontSize: 15, color: '#0F172A' }}>{fmtR(t.totalAmount)}</div>
                          {(t.changeGiven ?? 0) > 0 && (
                            <div style={{ fontSize: 10, color: '#0D9488' }}>Chg: {fmtR(t.changeGiven)}</div>
                          )}
                        </div>
                        <ChevronDown size={14} color="#94A3B8" style={{ transform: open ? 'rotate(180deg)' : 'none', transition: 'transform 0.15s', flexShrink: 0 }} />
                      </div>

                      {/* Expanded detail */}
                      {open && (
                        <div style={{ borderTop: '1px solid #F1F5F9', padding: '12px 16px', background: '#FAFAFA' }}>
                          {t.items?.length > 0 && (
                            <table style={{ width: '100%', borderCollapse: 'collapse', marginBottom: 12 }}>
                              <thead>
                                <tr>
                                  {['Item', 'Qty', 'Unit Price', 'Disc %', 'VAT', 'Total'].map(h => (
                                    <th key={h} style={{ ...TH, padding: '5px 10px', background: '#F1F5F9' }}>{h}</th>
                                  ))}
                                </tr>
                              </thead>
                              <tbody>
                                {t.items.map(li => (
                                  <tr key={li.id}>
                                    <td style={{ ...TD, padding: '5px 10px' }}>{li.itemName}</td>
                                    <td style={{ ...TD, padding: '5px 10px' }}>{li.qty}</td>
                                    <td style={{ ...TD, padding: '5px 10px' }}>{fmtR(li.unitPrice)}</td>
                                    <td style={{ ...TD, padding: '5px 10px', color: '#0D9488' }}>{li.discountPct > 0 ? `${li.discountPct}%` : '—'}</td>
                                    <td style={{ ...TD, padding: '5px 10px' }}>{fmtR(li.vatAmount)}</td>
                                    <td style={{ ...TD, padding: '5px 10px', fontWeight: 700 }}>{fmtR(li.lineTotal)}</td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          )}
                          <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                            <button onClick={() => setShowReceipt(t)} style={{ ...btnSecondary, fontSize: 12 }}>
                              <Printer size={13} /> Receipt
                            </button>
                            {t.status === 'COMPLETED' && (
                              <button
                                onClick={() => { setShowRefund(t); setRefundLines({}); setRefundReason(''); setErrMsg('') }}
                                style={{ ...btnSecondary, fontSize: 12, color: '#D97706', borderColor: '#FDE68A' }}>
                                <RotateCcw size={13} /> Refund
                              </button>
                            )}
                          </div>
                        </div>
                      )}
                    </div>
                  )
                })}
              </div>
            )}
          </div>
        )}

        {/* ════════════════════════════════════════════════════════════════════ */}
        {/* PURCHASE ORDERS TAB                                                 */}
        {/* ════════════════════════════════════════════════════════════════════ */}
        {tab === 'orders' && (
          <div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 14 }}>
              <button onClick={() => { setShowPO(true); setErrMsg('') }} style={btnPrimary()}>
                <Plus size={14} /> New Purchase Order
              </button>
            </div>

            {purchaseOrders.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '50px 0', color: '#94A3B8' }}>
                <Truck size={34} style={{ marginBottom: 12, opacity: 0.3 }} />
                <div style={{ fontWeight: 600, color: '#475569' }}>No purchase orders yet</div>
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {purchaseOrders.map(po => (
                  <div key={po.id} style={{ border: '1px solid #E2E8F0', borderRadius: 10, padding: '14px 16px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 10 }}>
                      <div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 3 }}>
                          <span style={{ fontWeight: 700, fontSize: 13 }}>{po.orderNumber}</span>
                          <StatusBadge status={po.status} />
                        </div>
                        <div style={{ fontSize: 12, color: '#64748B' }}>
                          {po.supplierName} · Ordered {fmtDateShort(po.orderDate)}
                          {po.expectedDate && ` · Expected ${fmtDateShort(po.expectedDate)}`}
                        </div>
                      </div>
                      <div style={{ textAlign: 'right' }}>
                        <div style={{ fontWeight: 800, fontSize: 15 }}>{fmtR(po.totalAmount)}</div>
                        <div style={{ fontSize: 10, color: '#94A3B8' }}>incl. VAT</div>
                      </div>
                    </div>

                    {/* PO lines */}
                    <div style={{ background: '#F8FAFC', borderRadius: 7, padding: '8px 12px', marginBottom: 10 }}>
                      {po.items?.map(li => (
                        <div key={li.id} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12, padding: '3px 0', color: '#374151' }}>
                          <span>{li.itemName}</span>
                          <span style={{ color: '#64748B' }}>
                            {li.qtyReceived}/{li.qtyOrdered} received
                            {li.fullyReceived && <span style={{ color: '#166534', marginLeft: 5, fontWeight: 700 }}>✓</span>}
                          </span>
                        </div>
                      ))}
                    </div>

                    {(po.status === 'ORDERED' || po.status === 'PARTIALLY_RECEIVED') && (
                      <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                        <button
                          onClick={() => { setShowReceive(po); setReceiveQtys({}) }}
                          style={{ ...btnPrimary('#0D9488'), fontSize: 12 }}>
                          <Truck size={13} /> Receive Stock
                        </button>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      {/* ══════════════════════════════════════════════════════════════════════ */}
      {/* MODALS                                                                */}
      {/* ══════════════════════════════════════════════════════════════════════ */}

      {/* Cash session modal */}
      {cashModal && (
        <div style={MODAL_BG}>
          <div style={modalBox(440)}>
            {modalHeader(
              cashModal === 'open' ? 'Open Cash Session' : 'Close Session (Cash Up)',
              () => setCashModal(null),
            )}

            {cashModal === 'open' && (
              <>
                <div style={{ marginBottom: 12 }}>
                  <label style={lbl}>Opening Float (cash in drawer) *</label>
                  <input type="number" value={openFloat} onChange={e => setOpenFloat(e.target.value)} style={inp} autoFocus />
                </div>
                <div style={{ marginBottom: 18 }}>
                  <label style={lbl}>Notes</label>
                  <input value={sNotes} onChange={e => setSNotes(e.target.value)} placeholder="e.g. Morning shift — Thabang" style={inp} />
                </div>
                <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                  <button onClick={() => setCashModal(null)} style={btnCancel}>Cancel</button>
                  <button
                    disabled={!openFloat || openSeshMut.isPending}
                    onClick={() => openSeshMut.mutate({ openingFloat: Number(openFloat), notes: sNotes || null })}
                    style={btnPrimary('#0D9488')}>
                    {openSeshMut.isPending ? 'Opening…' : 'Open Session'}
                  </button>
                </div>
              </>
            )}

            {cashModal === 'close' && (
              <>
                {localSession && localSession !== 'loading' && (
                  <div style={{ marginBottom: 14, padding: '12px 14px', background: '#F0FDF4', border: '1px solid #86EFAC', borderRadius: 8, fontSize: 13 }}>
                    {[
                      ['Session',            (localSession as CashSession).sessionNumber],
                      ['Opening float',      fmtR((localSession as CashSession).openingFloat)],
                      ['Cash sales',         fmtR((localSession as CashSession).expectedCash ?? 0)],
                      ['Expected in drawer', fmtR(((localSession as CashSession).openingFloat ?? 0) + ((localSession as CashSession).expectedCash ?? 0))],
                    ].map(([l, v]) => (
                      <div key={l} style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 3 }}>
                        <span style={{ color: '#374151' }}>{l}</span>
                        <span style={{ fontWeight: 600 }}>{v}</span>
                      </div>
                    ))}
                  </div>
                )}
                <div style={{ marginBottom: 12 }}>
                  <label style={lbl}>Physical cash count *</label>
                  <input
                    type="number" value={closeFloat}
                    onChange={e => setCloseFloat(e.target.value)}
                    placeholder="e.g. 685.00" style={inp} autoFocus
                  />
                  {closeFloat && localSession && localSession !== 'loading' && (() => {
                    const ls = localSession as CashSession
                    const expected = (ls.openingFloat ?? 0) + (ls.expectedCash ?? 0)
                    const variance = Number(closeFloat) - expected
                    return (
                      <div style={{ marginTop: 6, padding: '7px 12px', background: variance >= 0 ? '#F0FDF4' : '#FEF2F2', borderRadius: 7, fontSize: 13, fontWeight: 700, color: variance >= 0 ? '#166534' : '#DC2626' }}>
                        Variance: {variance >= 0 ? '+' : ''}{fmtR(variance)} ({variance >= 0 ? 'surplus' : 'short'})
                      </div>
                    )
                  })()}
                </div>
                <div style={{ marginBottom: 18 }}>
                  <label style={lbl}>Notes</label>
                  <input value={sNotes} onChange={e => setSNotes(e.target.value)} placeholder="End-of-shift notes" style={inp} />
                </div>
                <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                  <button onClick={() => setCashModal(null)} style={btnCancel}>Cancel</button>
                  <button
                    disabled={!closeFloat || closeSeshMut.isPending}
                    onClick={() => (localSession as CashSession)?.id && closeSeshMut.mutate({
                      id: (localSession as CashSession).id,
                      body: { closingFloat: Number(closeFloat), notes: sNotes || null },
                    })}
                    style={btnPrimary('#DC2626')}>
                    {closeSeshMut.isPending ? 'Closing…' : 'Close Session'}
                  </button>
                </div>
              </>
            )}

            <ErrMsg msg={errMsg} />
          </div>
        </div>
      )}

      {/* Add stock item modal */}
      {showStock && (
        <div style={MODAL_BG}>
          <div style={modalBox(480)}>
            {modalHeader('Add Stock Item', () => setShowStock(false))}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div>
                <label style={lbl}>Catalogue Item *</label>
                <CatalogueCombo
                  items={catalogueItems as any[]}
                  value={stockForm.catalogueItemId}
                  onChange={(id) => setStockForm(p => ({ ...p, catalogueItemId: id }))}
                  placeholder="Search catalogue items…"
                />
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                <div>
                  <label style={lbl}>Opening Qty</label>
                  <input type="number" value={stockForm.qtyOnHand} onChange={e => setStockForm(p => ({ ...p, qtyOnHand: e.target.value }))} placeholder="0" style={inp} />
                </div>
                <div>
                  <label style={lbl}>Reorder Level</label>
                  <input type="number" value={stockForm.reorderLevel} onChange={e => setStockForm(p => ({ ...p, reorderLevel: e.target.value }))} placeholder="5" style={inp} />
                </div>
                <div>
                  <label style={lbl}>Cost Price (R)</label>
                  <input type="number" value={stockForm.costPrice} onChange={e => setStockForm(p => ({ ...p, costPrice: e.target.value }))} placeholder="0.00" style={inp} />
                </div>
                <div>
                  <label style={lbl}>Location</label>
                  <input value={stockForm.location} onChange={e => setStockForm(p => ({ ...p, location: e.target.value }))} placeholder="Shelf A1" style={inp} />
                </div>
              </div>
            </div>
            <ErrMsg msg={errMsg} />
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 18 }}>
              <button onClick={() => setShowStock(false)} style={btnCancel}>Cancel</button>
              <button
                disabled={!stockForm.catalogueItemId || createStockMut.isPending}
                onClick={() => createStockMut.mutate({
                  catalogueItemId: stockForm.catalogueItemId,
                  qtyOnHand:   Number(stockForm.qtyOnHand)   || 0,
                  reorderLevel: Number(stockForm.reorderLevel) || 0,
                  reorderQty:  10,
                  costPrice:   Number(stockForm.costPrice)   || 0,
                  location:    stockForm.location || null,
                })}
                style={btnPrimary()}>
                {createStockMut.isPending ? 'Adding…' : 'Add Stock Item'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Receive stock modal */}
      {showReceive && (
        <div style={MODAL_BG}>
          <div style={modalBox(520)}>
            {modalHeader(`Receive Stock — ${showReceive.orderNumber}`, () => setShowReceive(null))}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 18 }}>
              {showReceive.items?.filter(li => !li.fullyReceived).map(li => (
                <div key={li.id} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '10px 14px', background: '#F8FAFC', borderRadius: 8 }}>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontWeight: 600, fontSize: 13 }}>{li.itemName}</div>
                    <div style={{ fontSize: 11, color: '#94A3B8' }}>
                      Ordered: {li.qtyOrdered} · Received so far: {li.qtyReceived}
                    </div>
                  </div>
                  <div>
                    <label style={lbl}>Qty receiving</label>
                    <input
                      type="number" min="0" max={li.qtyOrdered - li.qtyReceived}
                      value={receiveQtys[li.id] ?? ''}
                      onChange={e => setReceiveQtys(p => ({ ...p, [li.id]: e.target.value }))}
                      placeholder={String(li.qtyOrdered - li.qtyReceived)}
                      style={{ ...inp, width: 90 }}
                    />
                  </div>
                </div>
              ))}
            </div>
            <ErrMsg msg={errMsg} />
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              <button onClick={() => setShowReceive(null)} style={btnCancel}>Cancel</button>
              <button
                disabled={receiveMut.isPending}
                onClick={() => receiveMut.mutate({
                  id: showReceive.id,
                  body: {
                    lines: showReceive.items?.map(li => ({
                      itemId:      li.id,
                      qtyReceived: Number(receiveQtys[li.id] ?? (li.qtyOrdered - li.qtyReceived)),
                    })),
                  },
                })}
                style={btnPrimary('#0D9488')}>
                {receiveMut.isPending ? 'Receiving…' : 'Confirm Receipt'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Create PO modal */}
      {showPO && (
        <div style={MODAL_BG}>
          <div style={modalBox(580)}>
            {modalHeader('New Purchase Order', () => setShowPO(false))}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 16 }}>
              <div style={{ gridColumn: '1/-1' }}>
                <label style={lbl}>Supplier Name *</label>
                <input autoFocus value={poForm.supplierName} onChange={e => setPOForm(p => ({ ...p, supplierName: e.target.value }))} placeholder="Acme Distributors" style={inp} />
              </div>
              <div>
                <label style={lbl}>Expected Date</label>
                <input type="date" value={poForm.expectedDate} onChange={e => setPOForm(p => ({ ...p, expectedDate: e.target.value }))} style={inp} />
              </div>
              <div>
                <label style={lbl}>Notes</label>
                <input value={poForm.notes} onChange={e => setPOForm(p => ({ ...p, notes: e.target.value }))} style={inp} />
              </div>
            </div>

            <div style={{ fontSize: 11, fontWeight: 700, color: '#374151', marginBottom: 8 }}>Line Items</div>
            {poLines.map((line, idx) => (
              <div key={idx} style={{ display: 'grid', gridTemplateColumns: '2fr 1fr 1fr auto', gap: 8, marginBottom: 8, alignItems: 'end' }}>
                <div>
                  <label style={lbl}>Item</label>
                  <CatalogueCombo
                    items={catalogueItems as any[]}
                    value={line.catalogueItemId}
                    onChange={(id, name) => setPOLines(ls => ls.map((l, i) =>
                      i === idx ? { ...l, catalogueItemId: id, itemName: name } : l,
                    ))}
                    placeholder="Search item…"
                  />
                </div>
                <div>
                  <label style={lbl}>Qty</label>
                  <input type="number" value={line.qtyOrdered} onChange={e => setPOLines(ls => ls.map((l, i) => i === idx ? { ...l, qtyOrdered: e.target.value } : l))} placeholder="10" style={inp} />
                </div>
                <div>
                  <label style={lbl}>Unit Cost (R)</label>
                  <input type="number" value={line.unitCost} onChange={e => setPOLines(ls => ls.map((l, i) => i === idx ? { ...l, unitCost: e.target.value } : l))} placeholder="0.00" style={inp} />
                </div>
                <button onClick={() => setPOLines(ls => ls.filter((_, i) => i !== idx))} style={{ ...btnSecondary, padding: '9px 10px', alignSelf: 'flex-end' }}>
                  <X size={13} />
                </button>
              </div>
            ))}
            <button onClick={() => setPOLines(ls => [...ls, { catalogueItemId: '', itemName: '', qtyOrdered: '', unitCost: '' }])} style={{ ...btnSecondary, fontSize: 12, marginBottom: 16 }}>
              <Plus size={12} /> Add Line
            </button>

            <ErrMsg msg={errMsg} />
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              <button onClick={() => setShowPO(false)} style={btnCancel}>Cancel</button>
              <button
                disabled={!poForm.supplierName || createPOMut.isPending}
                onClick={() => createPOMut.mutate({
                  supplierName: poForm.supplierName,
                  expectedDate: poForm.expectedDate || null,
                  notes:        poForm.notes || null,
                  items: poLines
                    .filter(l => l.catalogueItemId && l.qtyOrdered)
                    .map(l => ({
                      catalogueItemId: l.catalogueItemId,
                      itemName:   l.itemName,
                      qtyOrdered: Number(l.qtyOrdered),
                      unitCost:   Number(l.unitCost),
                      vatRate:    15,
                    })),
                })}
                style={btnPrimary()}>
                {createPOMut.isPending ? 'Creating…' : 'Create Purchase Order'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Refund modal */}
      {showRefund && (
        <div style={MODAL_BG}>
          <div style={modalBox(520)}>
            {modalHeader(`Refund — ${showRefund.transactionNumber}`, () => setShowRefund(null))}
            <div style={{ marginBottom: 12, padding: '9px 12px', background: '#FFFBEB', border: '1px solid #FDE68A', borderRadius: 8, fontSize: 12, color: '#92400E' }}>
              Enter the quantity to refund per item. Leave blank to use the full original quantity. Refunded stock is returned to inventory automatically.
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 14 }}>
              {showRefund.items?.map(li => (
                <div key={li.id} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '10px 14px', background: '#F8FAFC', borderRadius: 8 }}>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontWeight: 600, fontSize: 13 }}>{li.itemName}</div>
                    <div style={{ fontSize: 11, color: '#94A3B8' }}>Sold qty: {li.qty} · {fmtR(li.lineTotal)}</div>
                  </div>
                  <div>
                    <label style={lbl}>Qty to refund</label>
                    <input
                      type="number" min="0" max={li.qty}
                      value={refundLines[li.id] ?? li.qty}
                      onChange={e => setRefundLines(p => ({ ...p, [li.id]: e.target.value }))}
                      style={{ ...inp, width: 80 }}
                    />
                  </div>
                </div>
              ))}
            </div>
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>Reason *</label>
              <input value={refundReason} onChange={e => setRefundReason(e.target.value)} placeholder="e.g. Customer changed mind / item defective" style={inp} />
            </div>
            <ErrMsg msg={errMsg} />
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              <button onClick={() => setShowRefund(null)} style={btnCancel}>Cancel</button>
              <button
                disabled={!refundReason || refundMut.isPending}
                onClick={() => refundMut.mutate({
                  id: showRefund.id,
                  body: {
                    reason: refundReason,
                    items: showRefund.items?.map(li => ({
                      transactionItemId: li.id,
                      qtyReturned: Number(refundLines[li.id] ?? li.qty),
                    })),
                  },
                })}
                style={btnPrimary('#D97706')}>
                {refundMut.isPending ? 'Processing…' : 'Process Refund'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Receipt modal */}
      {showReceipt && (
        <div style={MODAL_BG}>
          <div style={modalBox(420)}>
            {modalHeader(`Receipt — ${showReceipt.transactionNumber}`, () => setShowReceipt(null))}
            <div style={{ background: '#F8FAFC', border: '1px solid #E2E8F0', borderRadius: 10, padding: 16, fontFamily: 'monospace', fontSize: 12, lineHeight: 1.9 }}>
              <div style={{ textAlign: 'center', fontWeight: 700, fontSize: 14, marginBottom: 2 }}>HandyFlow</div>
              <div style={{ textAlign: 'center', color: '#64748B', fontSize: 10, marginBottom: 10 }}>ECTA-compliant electronic receipt</div>
              <div style={{ borderTop: '1px dashed #CBD5E1', paddingTop: 8, marginBottom: 8 }}>
                <div>TXN: {showReceipt.transactionNumber}</div>
                <div>Date: {fmtDate(showReceipt.createdAt)}</div>
                {showReceipt.servedByName && <div>Cashier: {showReceipt.servedByName}</div>}
                {showReceipt.customerName && <div>Customer: {showReceipt.customerName}</div>}
              </div>
              <div style={{ borderTop: '1px dashed #CBD5E1', paddingTop: 8, marginBottom: 8 }}>
                {showReceipt.items?.map(li => (
                  <div key={li.id} style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <span>{li.itemName} ×{li.qty}</span>
                    <span>{fmtR(li.lineTotal)}</span>
                  </div>
                ))}
              </div>
              <div style={{ borderTop: '1px dashed #CBD5E1', paddingTop: 8 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', color: '#64748B' }}>
                  <span>Payment</span><span>{showReceipt.paymentMethod}</span>
                </div>
                {(showReceipt.changeGiven ?? 0) > 0 && (
                  <div style={{ display: 'flex', justifyContent: 'space-between', color: '#64748B' }}>
                    <span>Change</span><span>{fmtR(showReceipt.changeGiven)}</span>
                  </div>
                )}
                <div style={{ display: 'flex', justifyContent: 'space-between', fontWeight: 700, fontSize: 14, marginTop: 4 }}>
                  <span>TOTAL</span><span>{fmtR(showReceipt.totalAmount)}</span>
                </div>
              </div>
            </div>
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 14 }}>
              <button onClick={() => setShowReceipt(null)} style={btnCancel}>Close</button>
              <button onClick={() => window.print()} style={btnPrimary()}>
                <Printer size={13} /> Print
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
  )
}
