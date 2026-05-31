import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { Plus, ShoppingCart, Package, AlertTriangle, X, Minus } from 'lucide-react'

interface StockItem {
  id: string; catalogueItemId: string; itemName: string; sku: string | null
  barcode: string | null; qtyOnHand: number; availableQty: number
  reorderLevel: number; costPrice: number; sellingPrice: number
  location: string | null; lowStock: boolean
}
interface Transaction {
  id: string; transactionNumber: string; customerName: string | null
  totalAmount: number; paymentMethod: string; changeGiven: number | null
  status: string; servedByName: string | null; createdAt: string
  items: any[]
}
interface CartItem { catalogueItemId: string; itemName: string; unitPrice: number; qty: number }

type Tab = 'sell' | 'stock' | 'transactions'

export function PosPage() {
  const qc = useQueryClient()
  const [activeTab, setActiveTab] = useState<Tab>('sell')
  const [cart, setCart] = useState<CartItem[]>([])
  const [paymentMethod, setPaymentMethod] = useState('CASH')
  const [amountTendered, setAmountTendered] = useState('')
  const [customerName, setCustomerName] = useState('')
  const [saleError, setSaleError] = useState('')
  const [showStockCreate, setShowStockCreate] = useState(false)
  const [stockError, setStockError] = useState('')
  const [stockForm, setStockForm] = useState({ catalogueItemId: '', qtyOnHand: '', reorderLevel: '', costPrice: '', location: '' })
  const sf = (k: keyof typeof stockForm, v: string) => setStockForm(p => ({ ...p, [k]: v }))

  const { data: summary } = useQuery({
    queryKey: ['pos-summary'],
    queryFn: async () => { const r = await apiClient.get('/api/v1/pos/summary'); return r.data },
  })
  const { data: stock = [], isLoading: loadingStock } = useQuery<StockItem[]>({
    queryKey: ['pos-stock'],
    queryFn: async () => { const r = await apiClient.get('/api/v1/pos/stock?size=100'); return r.data?.content || [] },
  })
  const { data: transactions = [] } = useQuery<Transaction[]>({
    queryKey: ['pos-transactions'],
    queryFn: async () => { const r = await apiClient.get('/api/v1/pos/transactions?size=50'); return r.data?.content || [] },
    enabled: activeTab === 'transactions',
  })
  const { data: catalogueItems = [] } = useQuery({
    queryKey: ['catalogue-items-pos'],
    queryFn: async () => { const r = await apiClient.get('/api/v1/catalogue/items?size=200&active=true'); return r.data?.content || [] },
  })

  const processSale = useMutation({
    mutationFn: (body: any) => apiClient.post('/api/v1/pos/sell', body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['pos-summary'] })
      qc.invalidateQueries({ queryKey: ['pos-stock'] })
      qc.invalidateQueries({ queryKey: ['pos-transactions'] })
      setCart([]); setAmountTendered(''); setCustomerName(''); setSaleError('')
      alert('Sale completed!')
    },
    onError: (e: any) => setSaleError(e.response?.data?.message || 'Sale failed'),
  })
  const createStockItem = useMutation({
    mutationFn: (body: any) => apiClient.post('/api/v1/pos/stock', body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['pos-stock'] }); setShowStockCreate(false); setStockForm({ catalogueItemId: '', qtyOnHand: '', reorderLevel: '', costPrice: '', location: '' }) },
    onError: (e: any) => setStockError(e.response?.data?.message || 'Failed'),
  })

  const addToCart = (item: StockItem) => {
    setCart(c => {
      const existing = c.find(x => x.catalogueItemId === item.catalogueItemId)
      if (existing) return c.map(x => x.catalogueItemId === item.catalogueItemId ? { ...x, qty: x.qty + 1 } : x)
      return [...c, { catalogueItemId: item.catalogueItemId, itemName: item.itemName, unitPrice: item.sellingPrice, qty: 1 }]
    })
  }
  const removeFromCart = (id: string) => setCart(c => c.filter(x => x.catalogueItemId !== id))
  const updateQty = (id: string, qty: number) => {
    if (qty <= 0) return removeFromCart(id)
    setCart(c => c.map(x => x.catalogueItemId === id ? { ...x, qty } : x))
  }

  const subtotal = cart.reduce((s, i) => s + i.unitPrice * i.qty, 0)
  const vat = subtotal * 0.15
  const total = subtotal + vat
  const change = paymentMethod === 'CASH' && amountTendered ? Number(amountTendered) - total : null

  const fmtR = (n: number) => `R ${n.toLocaleString('en-ZA', { minimumFractionDigits: 2 })}`
  const fmtDate = (d: string) => new Date(d).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })

  const stats = [
    { label: 'Sales Today',    value: fmtR(summary?.salesToday    ?? 0), color: '#0D9488' },
    { label: 'Transactions',   value: summary?.transactionsToday  ?? 0, color: '#1B3A6B' },
    { label: 'Stock Items',    value: summary?.totalStockItems    ?? 0, color: '#475569' },
    { label: 'Low Stock',      value: summary?.lowStockItems      ?? 0, color: '#DC2626' },
  ]

  return (
    <div>
      <div style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: 24, fontWeight: 700, color: '#0F172A', margin: '0 0 4px' }}>POS & Stock</h1>
        <p style={{ fontSize: 14, color: '#64748B', margin: 0 }}>Point of sale terminal and inventory management</p>
      </div>

      <div style={{ display: 'flex', gap: 12, marginBottom: 24, flexWrap: 'wrap' }}>
        {stats.map(s => (
          <div key={s.label} style={{ flex: 1, minWidth: 120, background: '#fff', border: '1px solid #E2E8F0', borderRadius: 12, padding: '16px 20px' }}>
            <div style={{ fontSize: typeof s.value === 'string' ? 18 : 26, fontWeight: 700, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 12, color: '#64748B', marginTop: 3 }}>{s.label}</div>
          </div>
        ))}
      </div>

      <div style={{ background: '#fff', border: '1px solid #E2E8F0', borderRadius: 12, padding: 24 }}>
        <div style={{ display: 'flex', gap: 4, borderBottom: '1px solid #E2E8F0', marginBottom: 24 }}>
          {(['sell', 'stock', 'transactions'] as Tab[]).map(tab => (
            <button key={tab} onClick={() => setActiveTab(tab)} style={{
              display: 'flex', alignItems: 'center', gap: 7, padding: '10px 18px',
              background: 'none', border: 'none', borderBottom: activeTab === tab ? '2px solid #0D9488' : '2px solid transparent',
              color: activeTab === tab ? '#0D9488' : '#64748B', fontWeight: activeTab === tab ? 600 : 400,
              fontSize: 14, cursor: 'pointer', marginBottom: -1,
            }}>
              {tab === 'sell' ? <ShoppingCart size={15} /> : tab === 'stock' ? <Package size={15} /> : <ShoppingCart size={15} />}
              {tab === 'sell' ? 'POS Terminal' : tab === 'stock' ? 'Stock' : 'Transactions'}
            </button>
          ))}
        </div>

        {/* POS Terminal */}
        {activeTab === 'sell' && (
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 360px', gap: 24 }}>
            {/* Items grid */}
            <div>
              <div style={{ fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 12 }}>Select Items</div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))', gap: 10 }}>
                {stock.filter(s => s.availableQty > 0).map(item => (
                  <button key={item.id} onClick={() => addToCart(item)}
                    style={{ padding: '14px', border: '1px solid #E2E8F0', borderRadius: 10, background: '#fff', cursor: 'pointer', textAlign: 'left' as const, transition: 'all 0.15s' }}
                    onMouseEnter={e => { (e.currentTarget as HTMLElement).style.borderColor = '#0D9488'; (e.currentTarget as HTMLElement).style.background = '#F0FDF4' }}
                    onMouseLeave={e => { (e.currentTarget as HTMLElement).style.borderColor = '#E2E8F0'; (e.currentTarget as HTMLElement).style.background = '#fff' }}>
                    <div style={{ fontSize: 13, fontWeight: 700, color: '#0F172A', marginBottom: 4 }}>{item.itemName}</div>
                    <div style={{ fontSize: 16, fontWeight: 700, color: '#0D9488' }}>{fmtR(item.sellingPrice)}</div>
                    <div style={{ fontSize: 11, color: '#94A3B8', marginTop: 3 }}>Stock: {item.availableQty}</div>
                  </button>
                ))}
                {stock.filter(s => s.availableQty > 0).length === 0 && (
                  <div style={{ gridColumn: '1 / -1', padding: '40px 20px', textAlign: 'center', color: '#94A3B8' }}>
                    Add stock items first to sell them at the POS terminal.
                  </div>
                )}
              </div>
            </div>
            {/* Cart */}
            <div style={{ border: '1px solid #E2E8F0', borderRadius: 12, padding: 20, display: 'flex', flexDirection: 'column' }}>
              <div style={{ fontSize: 14, fontWeight: 700, color: '#0F172A', marginBottom: 14 }}>Current Sale</div>
              <div style={{ flex: 1, marginBottom: 14, minHeight: 100 }}>
                {cart.length === 0 ? (
                  <div style={{ textAlign: 'center', padding: '30px 0', color: '#CBD5E1', fontSize: 13 }}>
                    <ShoppingCart size={28} style={{ marginBottom: 8, opacity: 0.4 }} /><br />Tap items to add
                  </div>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    {cart.map(item => (
                      <div key={item.catalogueItemId} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <div style={{ flex: 1, fontSize: 13, fontWeight: 600, color: '#374151' }}>{item.itemName}</div>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                          <button onClick={() => updateQty(item.catalogueItemId, item.qty - 1)} style={{ width: 22, height: 22, borderRadius: 4, border: '1px solid #E2E8F0', background: '#F8FAFC', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' }}><Minus size={11} /></button>
                          <span style={{ fontSize: 13, fontWeight: 700, minWidth: 20, textAlign: 'center' }}>{item.qty}</span>
                          <button onClick={() => updateQty(item.catalogueItemId, item.qty + 1)} style={{ width: 22, height: 22, borderRadius: 4, border: '1px solid #E2E8F0', background: '#F8FAFC', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' }}><Plus size={11} /></button>
                          <span style={{ fontSize: 13, fontWeight: 700, color: '#0F172A', minWidth: 70, textAlign: 'right' }}>{fmtR(item.unitPrice * item.qty)}</span>
                          <button onClick={() => removeFromCart(item.catalogueItemId)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', padding: 2 }}><X size={13} /></button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
              {/* Totals */}
              <div style={{ borderTop: '1px solid #F1F5F9', paddingTop: 12, marginBottom: 12 }}>
                {[['Subtotal', fmtR(subtotal)], ['VAT (15%)', fmtR(vat)]].map(([l, v]) => (
                  <div key={l} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, color: '#64748B', marginBottom: 4 }}>
                    <span>{l}</span><span>{v}</span>
                  </div>
                ))}
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 17, fontWeight: 800, color: '#0F172A', borderTop: '1px solid #E2E8F0', paddingTop: 8, marginTop: 4 }}>
                  <span>Total</span><span>{fmtR(total)}</span>
                </div>
              </div>
              {/* Payment */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 12 }}>
                <input value={customerName} onChange={e => setCustomerName(e.target.value)} placeholder="Customer name (optional)" style={{ padding: '8px 12px', border: '1px solid #E2E8F0', borderRadius: 8, fontSize: 13 }} />
                <select value={paymentMethod} onChange={e => setPaymentMethod(e.target.value)} style={{ padding: '8px 12px', border: '1px solid #E2E8F0', borderRadius: 8, fontSize: 13, background: '#fff' }}>
                  {['CASH','CARD','EFT','ACCOUNT'].map(m => <option key={m}>{m}</option>)}
                </select>
                {paymentMethod === 'CASH' && (
                  <input type="number" value={amountTendered} onChange={e => setAmountTendered(e.target.value)} placeholder={`Amount tendered (min ${fmtR(total)})`} style={{ padding: '8px 12px', border: '1px solid #E2E8F0', borderRadius: 8, fontSize: 13 }} />
                )}
                {change !== null && change >= 0 && (
                  <div style={{ padding: '8px 12px', background: '#DCFCE7', borderRadius: 8, fontSize: 14, fontWeight: 700, color: '#166534', textAlign: 'center' }}>
                    Change: {fmtR(change)}
                  </div>
                )}
              </div>
              {saleError && <div style={{ color: '#DC2626', fontSize: 12, marginBottom: 8 }}>{saleError}</div>}
              <button onClick={() => processSale.mutate({ customerName: customerName || null, paymentMethod, amountTendered: amountTendered ? Number(amountTendered) : null, items: cart.map(i => ({ catalogueItemId: i.catalogueItemId, qty: i.qty, unitPrice: i.unitPrice, discountPct: 0 })) })}
                disabled={cart.length === 0 || processSale.isPending || (paymentMethod === 'CASH' && amountTendered !== '' && Number(amountTendered) < total)}
                style={{ background: '#0D9488', color: '#fff', border: 'none', borderRadius: 10, padding: '12px', fontSize: 15, fontWeight: 700, cursor: 'pointer', width: '100%' }}>
                {processSale.isPending ? 'Processing...' : `Charge ${fmtR(total)}`}
              </button>
            </div>
          </div>
        )}

        {/* Stock */}
        {activeTab === 'stock' && (
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
              <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                {(summary?.lowStockItems ?? 0) > 0 && (
                  <span style={{ display: 'flex', alignItems: 'center', gap: 5, background: '#FEF2F2', color: '#DC2626', padding: '5px 12px', borderRadius: 20, fontSize: 12, fontWeight: 600 }}>
                    <AlertTriangle size={12} /> {summary.lowStockItems} low stock
                  </span>
                )}
              </div>
              <button onClick={() => { setShowStockCreate(true); setStockError('') }} style={btnPrimary}><Plus size={15} /> Add Stock Item</button>
            </div>
            {loadingStock ? (
              <div style={{ textAlign: 'center', padding: 40, color: '#94A3B8' }}>Loading...</div>
            ) : stock.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '60px 20px', color: '#94A3B8' }}>
                <Package size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
                <div style={{ fontWeight: 600, color: '#475569' }}>No stock items yet</div>
                <div style={{ fontSize: 13, marginTop: 4 }}>Add catalogue items to track inventory.</div>
              </div>
            ) : (
              <div style={{ border: '1px solid #E2E8F0', borderRadius: 10, overflow: 'hidden' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                  <thead><tr style={{ background: '#F8FAFC' }}>
                    {['Item', 'In Stock', 'Reorder At', 'Cost', 'Sell Price', 'Location', 'Status'].map(h => <th key={h} style={th}>{h}</th>)}
                  </tr></thead>
                  <tbody>
                    {stock.map((s, i) => (
                      <tr key={s.id} style={{ background: i % 2 === 0 ? '#fff' : '#FAFAFA' }}>
                        <td style={td}><div style={{ fontWeight: 600, fontSize: 13, color: '#0F172A' }}>{s.itemName}</div>{s.sku && <div style={{ fontSize: 11, color: '#94A3B8' }}>{s.sku}</div>}</td>
                        <td style={td}><span style={{ fontWeight: 700, fontSize: 14, color: s.lowStock ? '#DC2626' : '#0F172A' }}>{s.availableQty}</span></td>
                        <td style={td}><span style={{ fontSize: 13, color: '#64748B' }}>{s.reorderLevel}</span></td>
                        <td style={td}><span style={{ fontSize: 13, color: '#64748B' }}>{fmtR(s.costPrice)}</span></td>
                        <td style={td}><span style={{ fontWeight: 700, color: '#0D9488' }}>{fmtR(s.sellingPrice)}</span></td>
                        <td style={td}><span style={{ fontSize: 12, color: '#94A3B8' }}>{s.location || '—'}</span></td>
                        <td style={td}>
                          {s.lowStock ? (
                            <span style={{ display: 'flex', alignItems: 'center', gap: 4, background: '#FEF2F2', color: '#DC2626', padding: '2px 10px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                              <AlertTriangle size={10} /> LOW
                            </span>
                          ) : (
                            <span style={{ background: '#DCFCE7', color: '#166534', padding: '2px 10px', borderRadius: 20, fontSize: 11, fontWeight: 600 }}>OK</span>
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

        {/* Transactions */}
        {activeTab === 'transactions' && (
          <div>
            {transactions.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '60px 20px', color: '#94A3B8' }}>
                <ShoppingCart size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
                <div style={{ fontWeight: 600, color: '#475569' }}>No transactions yet</div>
              </div>
            ) : (
              <div style={{ border: '1px solid #E2E8F0', borderRadius: 10, overflow: 'hidden' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                  <thead><tr style={{ background: '#F8FAFC' }}>
                    {['TXN #', 'Customer', 'Total', 'Payment', 'Served By', 'Time', 'Status'].map(h => <th key={h} style={th}>{h}</th>)}
                  </tr></thead>
                  <tbody>
                    {transactions.map((t, i) => (
                      <tr key={t.id} style={{ background: i % 2 === 0 ? '#fff' : '#FAFAFA' }}>
                        <td style={td}><span style={{ fontWeight: 700, fontSize: 13 }}>{t.transactionNumber}</span></td>
                        <td style={td}><span style={{ fontSize: 13, color: '#475569' }}>{t.customerName || 'Walk-in'}</span></td>
                        <td style={td}><span style={{ fontWeight: 700, fontSize: 14 }}>{fmtR(t.totalAmount)}</span></td>
                        <td style={td}><span style={{ fontSize: 12, color: '#64748B' }}>{t.paymentMethod}</span></td>
                        <td style={td}><span style={{ fontSize: 12, color: '#64748B' }}>{t.servedByName || '—'}</span></td>
                        <td style={td}><span style={{ fontSize: 12, color: '#94A3B8' }}>{fmtDate(t.createdAt)}</span></td>
                        <td style={td}>
                          <span style={{ background: t.status === 'COMPLETED' ? '#DCFCE7' : '#FEF2F2', color: t.status === 'COMPLETED' ? '#166534' : '#DC2626', padding: '2px 10px', borderRadius: 20, fontSize: 11, fontWeight: 600 }}>
                            {t.status}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Add Stock Item Modal */}
      {showStockCreate && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000 }}>
          <div style={{ background: '#fff', borderRadius: 14, padding: 28, width: 480, boxShadow: '0 20px 60px rgba(0,0,0,0.2)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700 }}>Add Stock Item</h3>
              <button onClick={() => setShowStockCreate(false)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8' }}><X size={20} /></button>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              <F label="Catalogue Item *">
                <select value={stockForm.catalogueItemId} onChange={e => sf('catalogueItemId', e.target.value)} style={inp}>
                  <option value="">Select from catalogue...</option>
                  {(catalogueItems as any[]).map((item: any) => <option key={item.id} value={item.id}>{item.name} — R{item.defaultPrice}</option>)}
                </select>
              </F>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
                <F label="Opening Qty"><input type="number" value={stockForm.qtyOnHand} onChange={e => sf('qtyOnHand', e.target.value)} placeholder="0" style={inp} /></F>
                <F label="Reorder Level"><input type="number" value={stockForm.reorderLevel} onChange={e => sf('reorderLevel', e.target.value)} placeholder="5" style={inp} /></F>
                <F label="Cost Price (R)"><input type="number" value={stockForm.costPrice} onChange={e => sf('costPrice', e.target.value)} placeholder="0.00" style={inp} /></F>
                <F label="Location"><input value={stockForm.location} onChange={e => sf('location', e.target.value)} placeholder="Shelf A1" style={inp} /></F>
              </div>
            </div>
            {stockError && <div style={{ marginTop: 10, color: '#DC2626', fontSize: 13 }}>{stockError}</div>}
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 20 }}>
              <button onClick={() => setShowStockCreate(false)} style={btnCancel}>Cancel</button>
              <button onClick={() => createStockItem.mutate({ catalogueItemId: stockForm.catalogueItemId, qtyOnHand: stockForm.qtyOnHand ? Number(stockForm.qtyOnHand) : 0, reorderLevel: stockForm.reorderLevel ? Number(stockForm.reorderLevel) : 0, reorderQty: 10, costPrice: stockForm.costPrice ? Number(stockForm.costPrice) : 0, location: stockForm.location || null })}
                disabled={!stockForm.catalogueItemId || createStockItem.isPending} style={btnPrimary}>
                {createStockItem.isPending ? 'Adding...' : 'Add Stock Item'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function F({ label, children }: { label: string; children: React.ReactNode }) {
  return <div><label style={{ display: 'block', fontSize: 13, fontWeight: 500, color: '#374151', marginBottom: 5 }}>{label}</label>{children}</div>
}
const btnPrimary: React.CSSProperties = { display: 'flex', alignItems: 'center', gap: 7, background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 8, padding: '9px 18px', fontSize: 14, fontWeight: 500, cursor: 'pointer' }
const btnCancel: React.CSSProperties  = { padding: '9px 18px', border: '1px solid #E2E8F0', borderRadius: 8, background: '#fff', fontSize: 14, cursor: 'pointer', color: '#374151' }
const inp: React.CSSProperties = { width: '100%', padding: '9px 12px', border: '1px solid #E2E8F0', borderRadius: 8, fontSize: 14, boxSizing: 'border-box' as const, background: '#fff' }
const th: React.CSSProperties = { padding: '10px 16px', textAlign: 'left', fontSize: 11, fontWeight: 600, color: '#64748B', letterSpacing: '0.05em', borderBottom: '1px solid #E2E8F0' }
const td: React.CSSProperties = { padding: '12px 16px', fontSize: 13, borderBottom: '1px solid #F1F5F9' }
