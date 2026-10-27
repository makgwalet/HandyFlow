// src/pages/facilities/StoresTab.tsx
//
// Stores (where stock is kept) and the stock valuation. Each item keeps one average cost; the stores split the quantity.
// Valuation is the position now, at average cost (it is not a historical report).
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Download, Pencil, Plus, Star } from 'lucide-react'
import { facilitiesApi, type Store } from '../../api/facilities.api'
import Chip from '../../components/ui/Chip'
import { usePermissions } from '../../hooks/usePermission'
import { errorMessage, toast } from '../../store/toast.store'
import { Btn, Card, DataTable, Dialog, Field, KpiTile, Notice, StatusChip, type Column } from './ui'
import { fmtDateTime, fmtMoney, fmtQty, inputStyle, kpiGrid, saveBlob } from './format'

const CAN_MANAGE = ['FACILITIES_MANAGE', 'FACILITIES_ADMIN']
const CAN_REPORTS = ['FACILITIES_READ', 'FACILITIES_MANAGE', 'FACILITIES_ADMIN']

export default function StoresTab() {
  const [form, setForm] = useState<Store | 'new' | null>(null)
  const [storeId, setStoreId] = useState('')
  const qc = useQueryClient()
  const canManage = usePermissions(CAN_MANAGE), canReports = usePermissions(CAN_REPORTS)
  const stores = useQuery({ queryKey: ['facilities', 'stores'], queryFn: facilitiesApi.stores })
  const valuation = useQuery({ queryKey: ['facilities', 'valuation', storeId], queryFn: () => facilitiesApi.stockValuation(storeId || undefined), enabled: canReports })
  const act = useMutation({
    mutationFn: (a: { s: Store; action: 'make-default' | 'deactivate' | 'reactivate' }) => facilitiesApi.storeAction(a.s.id, a.action),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['facilities'] }); toast.success('Store updated') },
    onError: e => toast.error(errorMessage(e)),
  })
  const download = useMutation({
    mutationFn: async () => { const { blob, fileName } = await facilitiesApi.exportCsv('stock-valuation', { storeId: storeId || undefined }); saveBlob(blob, fileName) },
    onError: e => toast.error(errorMessage(e)),
  })
  const v = valuation.data
  const cols: Column<Store>[] = [
    { key: 'c', header: 'Store', render: s => <div><strong>{s.name}</strong><div style={{ fontSize: 12, color: 'var(--hf-text-muted)' }}>{s.code}</div></div> },
    { key: 'd', header: '', render: s => (s.defaultStore ? <Chip tone="accent" icon={<Star size={11} />}>Default</Chip> : null) },
    { key: 'i', header: 'Item lines', align: 'right', render: s => s.itemLines },
    { key: 'v', header: 'Value', align: 'right', render: s => fmtMoney(s.stockValue) },
    { key: 'a', header: 'Status', render: s => <StatusChip status={s.active ? 'ACTIVE' : 'INACTIVE'} /> },
    ...(canManage ? [{
      key: 'do', header: '', align: 'right' as const, render: (s: Store) => (
        <span style={{ display: 'inline-flex', gap: 6 }}>
          <Btn onClick={() => setForm(s)}><Pencil size={13} />Edit</Btn>
          {!s.defaultStore && s.active && <Btn busy={act.isPending} onClick={() => act.mutate({ s, action: 'make-default' })}>Make default</Btn>}
          {!s.defaultStore && <Btn busy={act.isPending} onClick={() => act.mutate({ s, action: s.active ? 'deactivate' : 'reactivate' })}>{s.active ? 'Deactivate' : 'Reactivate'}</Btn>}
        </span>
      ),
    }] : []),
  ]
  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12, gap: 10, flexWrap: 'wrap' }}>
        <h2 style={{ margin: 0, fontSize: 15 }}>Stores</h2>
        {canManage && <Btn kind="primary" onClick={() => setForm('new')}><Plus size={15} />New store</Btn>}
      </div>
      {stores.data?.length === 0 && <div style={{ marginBottom: 12 }}><Notice tone="info">No stores yet. The first stock you receive creates a default store called Main.</Notice></div>}
      <DataTable rows={stores.data ?? []} columns={cols} rowKey={s => s.id} loading={stores.isLoading} error={stores.error} empty="No stores yet." />

      {canReports && (
        <div style={{ marginTop: 26 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12, gap: 10, flexWrap: 'wrap' }}>
            <h2 style={{ margin: 0, fontSize: 15 }}>Stock valuation</h2>
            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <select aria-label="Store" value={storeId} onChange={e => setStoreId(e.target.value)} style={{ ...inputStyle, width: 'auto' }}>
                <option value="">All stores</option>{(stores.data ?? []).map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
              <Btn busy={download.isPending} onClick={() => download.mutate()}><Download size={14} />Export CSV</Btn>
            </div>
          </div>
          {valuation.isError && <Notice tone="bad">The valuation could not be loaded.</Notice>}
          {v && (
            <>
              <div style={kpiGrid}>
                <KpiTile label="Total stock value" value={fmtMoney(v.totalValue)} icon={<Star size={19} />} />
                <KpiTile label="Item lines" value={v.lines.length} icon={<Star size={19} />} tone="info" />
              </div>
              <p style={{ margin: '0 0 10px', fontSize: 12.5, color: 'var(--hf-text-muted)' }}>As at {fmtDateTime(v.asOf)}, at each item's average cost. Deactivated items that still hold stock are included.</p>
              <Card pad={0}>
                <DataTable rows={v.lines} rowKey={l => `${l.storeId}-${l.itemId}`} empty="No stock is held." columns={[
                  { key: 'st', header: 'Store', render: l => l.storeCode },
                  { key: 'sku', header: 'SKU', render: l => <strong>{l.sku}</strong> },
                  { key: 'n', header: 'Item', render: l => <span>{l.name}{!l.itemActive && <Chip tone="neutral">Inactive</Chip>}</span> },
                  { key: 'q', header: 'On hand', align: 'right', render: l => `${fmtQty(l.onHand)} ${l.unit}` },
                  { key: 'c', header: 'Unit cost', align: 'right', render: l => fmtMoney(l.unitCost) },
                  { key: 'v', header: 'Value', align: 'right', render: l => fmtMoney(l.value) },
                ] satisfies Column<(typeof v.lines)[number]>[]} />
              </Card>
            </>
          )}
        </div>
      )}
      {form && <StoreForm store={form === 'new' ? undefined : form} onClose={() => setForm(null)} />}
    </div>
  )
}

function StoreForm({ store, onClose }: { store?: Store; onClose: () => void }) {
  const qc = useQueryClient()
  const [code, setCode] = useState(store?.code ?? '')
  const [name, setName] = useState(store?.name ?? '')
  const sites = useQuery({ queryKey: ['facilities', 'sites'], queryFn: facilitiesApi.sites, staleTime: 5 * 60_000 })
  const [siteId, setSiteId] = useState(store?.siteId ?? '')
  const save = useMutation({
    mutationFn: () => facilitiesApi.saveStore(store?.id ?? null, { code: code.trim().toUpperCase(), name: name.trim(), siteId: siteId || null }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['facilities'] }); toast.success(store ? 'Store updated' : 'Store created'); onClose() },
    onError: e => toast.error(errorMessage(e)),
  })
  return (
    <Dialog title={store ? `Edit ${store.name}` : 'New store'} onClose={onClose} width={440}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" busy={save.isPending} disabled={!code.trim() || !name.trim()} onClick={() => save.mutate()}>Save</Btn></>}>
      <Field label="Code *" hint={store ? 'The code cannot be changed.' : 'Short and unique, e.g. MAIN or VAN-3.'}>
        <input value={code} onChange={e => setCode(e.target.value)} maxLength={30} disabled={!!store} style={inputStyle} autoFocus={!store} />
      </Field>
      <Field label="Name *"><input value={name} onChange={e => setName(e.target.value)} maxLength={100} style={inputStyle} autoFocus={!!store} /></Field>
      <Field label="Site" hint="Optional: where the store is.">
        <select value={siteId} onChange={e => setSiteId(e.target.value)} style={inputStyle}>
          <option value="">Not tied to a site</option>{(sites.data ?? []).map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
        </select>
      </Field>
    </Dialog>
  )
}
