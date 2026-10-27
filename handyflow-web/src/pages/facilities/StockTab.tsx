// src/pages/facilities/StockTab.tsx
//
// Stock items with receive / issue-to-work-order / return / stocktake, the movements ledger, and reorder suggestions.
// Issuing needs an open job: the server adds a PARTS cost line to it and refuses more than is on hand.
import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, ArrowRightLeft, PackagePlus, PackageMinus, Pencil, Plus } from 'lucide-react'
import { facilitiesApi, type StockItem, type StockLevel } from '../../api/facilities.api'
import { usePermissions } from '../../hooks/usePermission'
import { errorMessage, toast } from '../../store/toast.store'
import Chip from '../../components/ui/Chip'
import { Btn, DataTable, DefList, Dialog, Field, KpiTile, Notice, Toolbar, type Column } from './ui'
import { StockItemFormDialog } from './forms'
import { fmtDateTime, fmtMoney, fmtQty, inputStyle, kpiGrid, titleCase } from './format'

type Mode = 'receive' | 'issue' | 'return' | 'adjust' | 'transfer'
const CAN_MANAGE = ['FACILITIES_MANAGE', 'FACILITIES_ADMIN']
const CAN_ISSUE = ['FACILITIES_MANAGE', 'FACILITIES_ADMIN', 'FACILITIES_TECHNICIAN']

export default function StockTab() {
  const [search, setSearch] = useState('')
  const [lowOnly, setLowOnly] = useState(false)
  const [open, setOpen] = useState<StockItem | null>(null)
  const [creating, setCreating] = useState(false)
  const canManage = usePermissions(CAN_MANAGE)
  const q = useQuery({ queryKey: ['facilities', 'stock', search], queryFn: () => facilitiesApi.stock(search.trim() || undefined) })
  const sugg = useQuery({ queryKey: ['facilities', 'reorder'], queryFn: facilitiesApi.reorderSuggestions, enabled: canManage })

  const items = q.data ?? []
  const rows = lowOnly ? items.filter(i => i.belowReorderLevel) : items
  const lowCount = items.filter(i => i.belowReorderLevel).length
  const value = items.reduce((s, i) => s + i.stockValue, 0)

  const cols: Column<StockItem>[] = [
    { key: 'sku', header: 'SKU', render: i => <strong style={{ whiteSpace: 'nowrap' }}>{i.sku}</strong> },
    { key: 'name', header: 'Item', render: i => <div>{i.name}<div style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>{i.location ?? ''}</div></div> },
    { key: 'oh', header: 'On hand', align: 'right', render: i => <span style={{ fontWeight: 700, color: i.belowReorderLevel ? 'var(--hf-danger-text)' : undefined }}>{fmtQty(i.onHand)} {i.unit}</span> },
    { key: 'lvl', header: 'Reorder at', align: 'right', render: i => i.reorderLevel ? fmtQty(i.reorderLevel) : '—' },
    { key: 'cost', header: 'Unit cost', align: 'right', render: i => fmtMoney(i.unitCost) },
    { key: 'val', header: 'Value', align: 'right', render: i => fmtMoney(i.stockValue) },
    { key: 'flag', header: '', render: i => (!i.active ? <Chip tone="neutral">Inactive</Chip> : i.belowReorderLevel ? <Chip tone="bad">Low</Chip> : null) },
  ]

  return (
    <div>
      <div style={kpiGrid}>
        <KpiTile label="Items stocked" value={items.length} icon={<PackagePlus size={19} />} tone="info" />
        <KpiTile label="At or below reorder level" value={lowCount} icon={<AlertTriangle size={19} />} tone={lowCount ? 'bad' : 'ok'} onClick={() => setLowOnly(v => !v)} />
        <KpiTile label="Stock value" value={fmtMoney(value)} icon={<PackageMinus size={19} />} />
      </div>
      {sugg.data && sugg.data.length > 0 && (
        <div style={{ marginBottom: 14 }}>
          <Notice tone="warn" icon={<AlertTriangle size={16} />}>
            Reorder suggested for {sugg.data.length} item{sugg.data.length > 1 ? 's' : ''}:{' '}
            {sugg.data.slice(0, 4).map(s => `${s.name} (${fmtQty(s.suggestedQuantity)} ${s.unit}${s.onOrder ? `, ${fmtQty(s.onOrder)} already on order` : ''})`).join(' · ')}
            {sugg.data.length > 4 ? ' …' : ''}
          </Notice>
        </div>
      )}
      <Toolbar search={search} onSearch={setSearch} placeholder="Search SKU or name">
        <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, color: 'var(--hf-text-secondary)' }}>
          <input type="checkbox" checked={lowOnly} onChange={e => setLowOnly(e.target.checked)} /> Low stock only
        </label>
        {canManage && <Btn kind="primary" onClick={() => setCreating(true)}><Plus size={15} />New item</Btn>}
      </Toolbar>
      <DataTable rows={rows} columns={cols} rowKey={i => i.id} loading={q.isLoading} error={q.error} onRowClick={setOpen}
        empty={search || lowOnly ? 'No stock items match.' : 'No stock items yet.'} rowTone={i => (i.belowReorderLevel ? 'warn' : undefined)} />
      {open && <ItemPanel id={open.id} initial={open} onClose={() => setOpen(null)} />}
      {creating && <StockItemFormDialog onClose={() => setCreating(false)} />}
    </div>
  )
}

function ItemPanel({ id, initial, onClose }: { id: string; initial: StockItem; onClose: () => void }) {
  const [mode, setMode] = useState<Mode | null>(null)
  const [editing, setEditing] = useState(false)
  const qc = useQueryClient()
  const canManage = usePermissions(CAN_MANAGE), canIssue = usePermissions(CAN_ISSUE)
  const levels = useQuery({ queryKey: ['facilities', 'stock', id, 'levels'], queryFn: () => facilitiesApi.stockLevels(id) })
  const moves = useQuery({ queryKey: ['facilities', 'stock', id, 'movements'], queryFn: () => facilitiesApi.stockMovements(id) })
  // The list query refreshes after each action; read the live row from it so the panel updates in place.
  const live = useQuery({ queryKey: ['facilities', 'stock', ''], queryFn: () => facilitiesApi.stock(), select: l => l.find(i => i.id === id) })
  const item = live.data ?? initial
  const toggle = useMutation({
    mutationFn: () => facilitiesApi.stockActive(item.id, item.active ? 'deactivate' : 'reactivate'),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['facilities'] }); toast.success(`${item.sku} ${item.active ? 'deactivated' : 'reactivated'}`) },
    onError: e => toast.error(errorMessage(e)),
  })
  return (
    <Dialog title={`${item.sku} · ${item.name}`} onClose={onClose} side width={500}
      footer={canManage || item.active ? (
        <>
          {canManage && <Btn onClick={() => setEditing(true)}><Pencil size={13} />Edit</Btn>}
          {canManage && <Btn busy={toggle.isPending} onClick={() => toggle.mutate()}>{item.active ? 'Deactivate' : 'Reactivate'}</Btn>}
          {item.active && canManage && (levels.data?.length ?? 0) > 1 && <Btn onClick={() => setMode('transfer')}><ArrowRightLeft size={13} />Transfer</Btn>}
          {item.active && canManage && <Btn onClick={() => setMode('adjust')}>Stocktake</Btn>}
          {item.active && canIssue && <Btn onClick={() => setMode('return')}>Return</Btn>}
          {item.active && canIssue && <Btn kind="primary" onClick={() => setMode('issue')}>Issue to job</Btn>}
          {item.active && canManage && <Btn kind="primary" onClick={() => setMode('receive')}>Receive</Btn>}
        </>
      ) : undefined}>
      <DefList items={[
        ['On hand', <strong key="oh">{fmtQty(item.onHand)} {item.unit}</strong>], ['Reorder level', item.reorderLevel ? `${fmtQty(item.reorderLevel)} ${item.unit}` : 'Not set'],
        ['Standard reorder qty', item.reorderQty ? fmtQty(item.reorderQty) : '—'], ['Average unit cost', fmtMoney(item.unitCost)], ['Stock value', fmtMoney(item.stockValue)], ['Location', item.location ?? '—'],
      ]} />
      {(levels.data?.length ?? 0) > 1 && (
        <>
          <h3 style={{ margin: '22px 0 8px', fontSize: 13, fontWeight: 700 }}>By store</h3>
          <table style={{ width: '100%', fontSize: 12.5, borderCollapse: 'collapse' }}>
            <tbody>{levels.data!.map(l => (
              <tr key={l.storeId} style={{ borderTop: '1px solid var(--hf-border-subtle)' }}><td style={{ padding: '7px 0' }}>{l.storeName} <span style={{ color: 'var(--hf-text-muted)' }}>({l.storeCode})</span></td><td style={{ textAlign: 'right', fontWeight: 700 }}>{fmtQty(l.onHand)} {item.unit}</td></tr>
            ))}</tbody>
          </table>
        </>
      )}
      <h3 style={{ margin: '22px 0 8px', fontSize: 13, fontWeight: 700 }}>Recent movements</h3>
      {moves.data && moves.data.length > 0 ? (
        <table style={{ width: '100%', fontSize: 12.5, borderCollapse: 'collapse' }}>
          <tbody>
            {moves.data.map(m => (
              <tr key={m.id} style={{ borderTop: '1px solid var(--hf-border-subtle)' }}>
                <td style={{ padding: '7px 0' }}>{titleCase(m.movementType)}<div style={{ fontSize: 11.5, color: 'var(--hf-text-muted)' }}>{fmtDateTime(m.createdAt)}{m.reference ? ` · ${m.reference}` : ''}</div></td>
                <td style={{ textAlign: 'right', fontWeight: 700, color: m.quantity < 0 ? 'var(--hf-danger-text)' : 'var(--hf-success-text)' }}>{m.quantity > 0 ? '+' : ''}{fmtQty(m.quantity)}</td>
                <td style={{ textAlign: 'right', color: 'var(--hf-text-muted)', width: 70 }}>{fmtQty(m.balanceAfter)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : <div style={{ fontSize: 12.5, color: 'var(--hf-text-muted)' }}>{moves.isLoading ? 'Loading…' : 'No movements yet.'}</div>}
      {editing && <StockItemFormDialog item={item} onClose={() => setEditing(false)} />}
      {mode === 'transfer' && <TransferDialog item={item} levels={levels.data ?? []} onClose={() => setMode(null)} />}
      {mode && mode !== 'transfer' && <MovementDialog mode={mode} item={item} stores={levels.data ?? []} onClose={() => setMode(null)} />}
    </Dialog>
  )
}

const MODE_COPY: Record<Exclude<Mode, 'transfer'>, { title: string; confirm: string }> = {
  receive: { title: 'Receive stock', confirm: 'Receive' },
  issue: { title: 'Issue to work order', confirm: 'Issue' },
  return: { title: 'Return to stock', confirm: 'Return' },
  adjust: { title: 'Stocktake', confirm: 'Save count' },
}

function MovementDialog({ mode, item, stores, onClose }: { mode: Exclude<Mode, 'transfer'>; item: StockItem; stores: StockLevel[]; onClose: () => void }) {
  const qc = useQueryClient()
  const [qty, setQty] = useState('')
  const [cost, setCost] = useState(String(item.unitCost || ''))
  const [text, setText] = useState('')
  const [woId, setWoId] = useState('')
  const [storeId, setStoreId] = useState('')
  const orders = useQuery({
    queryKey: ['facilities', 'work-orders', 'IN_PROGRESS'], queryFn: () => facilitiesApi.workOrders('IN_PROGRESS'),
    enabled: mode === 'issue' || mode === 'return',
  })
  const assigned = useQuery({ queryKey: ['facilities', 'work-orders', 'ASSIGNED'], queryFn: () => facilitiesApi.workOrders('ASSIGNED'), enabled: mode === 'issue' })
  const jobs = useMemo(() => [...(orders.data ?? []), ...(assigned.data ?? [])], [orders.data, assigned.data])
  const n = Number(qty)

  const run = useMutation({
    mutationFn: () => {
      switch (mode) {
        case 'receive': return facilitiesApi.stockAction(item.id, 'receive', { quantity: n, unitCost: Number(cost), reference: text || undefined }, storeId)
        case 'issue': return facilitiesApi.stockAction(item.id, 'issue', { workOrderId: woId, quantity: n, note: text || undefined }, storeId)
        case 'return': return facilitiesApi.stockAction(item.id, 'return', { workOrderId: woId || undefined, quantity: n, reason: text }, storeId)
        case 'adjust': return facilitiesApi.stockAction(item.id, 'adjust', { countedQuantity: n, reason: text }, storeId)
      }
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['facilities'] }); toast.success(`${MODE_COPY[mode].title}: ${item.sku} updated`); onClose() },
    onError: e => toast.error(errorMessage(e)),
  })
  const qtyOk = qty !== '' && (mode === 'adjust' ? n >= 0 : n > 0)
  const ok = qtyOk && (mode !== 'issue' || woId) && (mode !== 'return' && mode !== 'adjust' || text.trim()) && (mode !== 'receive' || cost !== '')
  return (
    <Dialog title={`${MODE_COPY[mode].title}: ${item.sku}`} onClose={onClose} width={440}
      footer={<><Btn onClick={onClose}>Back</Btn><Btn kind="primary" busy={run.isPending} disabled={!ok} onClick={() => run.mutate()}>{MODE_COPY[mode].confirm}</Btn></>}>
      {stores.length > 1 && (
        <Field label="Store" hint={mode === 'issue' ? 'Issuing is limited to what that store holds.' : 'Leave blank for the default store.'}>
          <select value={storeId} onChange={e => setStoreId(e.target.value)} style={inputStyle}>
            <option value="">Default store</option>{stores.map(s => <option key={s.storeId} value={s.storeId}>{s.storeName} ({fmtQty(s.onHand)} {item.unit})</option>)}
          </select>
        </Field>
      )}
      {mode === 'issue' && (
        <Field label="Work order *" hint="Jobs that are assigned or in progress. Costs are frozen once a job is complete.">
          <select value={woId} onChange={e => setWoId(e.target.value)} style={inputStyle}>
            <option value="">Select a job…</option>
            {jobs.map(w => <option key={w.id} value={w.id}>{w.workOrderNumber} · {w.description.slice(0, 50)}</option>)}
          </select>
        </Field>
      )}
      {mode === 'return' && (
        <Field label="From work order" hint="Optional.">
          <select value={woId} onChange={e => setWoId(e.target.value)} style={inputStyle}>
            <option value="">None</option>
            {(orders.data ?? []).map(w => <option key={w.id} value={w.id}>{w.workOrderNumber} · {w.description.slice(0, 50)}</option>)}
          </select>
        </Field>
      )}
      <Field label={mode === 'adjust' ? `Counted quantity (${item.unit}) *` : `Quantity (${item.unit}) *`} hint={mode === 'adjust' ? `System shows ${fmtQty(item.onHand)}. The difference is recorded as an adjustment.` : mode === 'issue' ? `${fmtQty(item.onHand)} ${item.unit} on hand.` : undefined}>
        <input type="number" min="0" step="0.001" value={qty} onChange={e => setQty(e.target.value)} style={inputStyle} autoFocus />
      </Field>
      {mode === 'receive' && <Field label="Unit cost (R) *"><input type="number" min="0" step="0.01" value={cost} onChange={e => setCost(e.target.value)} style={inputStyle} /></Field>}
      <Field label={mode === 'receive' ? 'Reference' : mode === 'issue' ? 'Note' : 'Reason *'} hint={mode === 'receive' ? 'Delivery note or invoice number.' : undefined}>
        <input value={text} onChange={e => setText(e.target.value)} maxLength={300} style={inputStyle} />
      </Field>
    </Dialog>
  )
}

function TransferDialog({ item, levels, onClose }: { item: StockItem; levels: StockLevel[]; onClose: () => void }) {
  const qc = useQueryClient()
  const [from, setFrom] = useState(levels.find(l => l.onHand > 0)?.storeId ?? '')
  const [to, setTo] = useState('')
  const [qty, setQty] = useState('')
  const [reason, setReason] = useState('')
  const n = Number(qty)
  const held = levels.find(l => l.storeId === from)?.onHand ?? 0
  const ok = !!from && !!to && from !== to && n > 0 && n <= held
  const run = useMutation({
    mutationFn: () => facilitiesApi.transferStock(item.id, { fromStoreId: from, toStoreId: to, quantity: n, reason: reason.trim() || undefined }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['facilities'] }); toast.success(`${item.sku} transferred`); onClose() },
    onError: e => toast.error(errorMessage(e)),
  })
  return (
    <Dialog title={`Transfer ${item.sku}`} onClose={onClose} width={440}
      footer={<><Btn onClick={onClose}>Back</Btn><Btn kind="primary" busy={run.isPending} disabled={!ok} onClick={() => run.mutate()}>Transfer</Btn></>}>
      <Field label="From *">
        <select value={from} onChange={e => setFrom(e.target.value)} style={inputStyle}>
          <option value="">Select a store…</option>{levels.map(l => <option key={l.storeId} value={l.storeId}>{l.storeName} ({fmtQty(l.onHand)} {item.unit})</option>)}
        </select>
      </Field>
      <Field label="To *">
        <select value={to} onChange={e => setTo(e.target.value)} style={inputStyle}>
          <option value="">Select a store…</option>{levels.filter(l => l.storeId !== from).map(l => <option key={l.storeId} value={l.storeId}>{l.storeName}</option>)}
        </select>
      </Field>
      <Field label={`Quantity (${item.unit}) *`} hint={from ? `${fmtQty(held)} ${item.unit} available there.` : undefined}>
        <input type="number" min="0" step="0.001" value={qty} onChange={e => setQty(e.target.value)} style={inputStyle} />
      </Field>
      <Field label="Reason"><input value={reason} onChange={e => setReason(e.target.value)} maxLength={200} style={inputStyle} /></Field>
    </Dialog>
  )
}
