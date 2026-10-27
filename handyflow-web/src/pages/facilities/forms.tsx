// src/pages/facilities/forms.tsx
//
// Create / edit dialogs for the Facilities registers: sites, assets, vendors, stock items and purchase orders.
// Each dialog validates the obvious things (required fields, non-negative numbers) so the person gets an instant
// answer; everything else is the server's call, and its message is shown as a toast when it refuses.
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2 } from 'lucide-react'
import {
  facilitiesApi, type Asset, type Site, type StockItem, type Vendor,
  type AssetBody, type PurchaseOrderBody, type SiteBody, type StockItemBody, type VendorBody,
} from '../../api/facilities.api'
import { errorMessage, toast } from '../../store/toast.store'
import { Btn, Dialog, Field } from './ui'
import { fmtMoney, inputStyle, titleCase } from './format'

const SITE_TYPES = ['OFFICE', 'WAREHOUSE', 'RETAIL', 'FACTORY', 'OTHER']
const CRITICALITY = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']
const ASSET_STATUS = ['OPERATIONAL', 'DOWN', 'MAINTENANCE', 'DECOMMISSIONED']
const ADDRESS_FIELDS: [string, string][] = [['street', 'Street'], ['suburb', 'Suburb'], ['city', 'City'], ['province', 'Province'], ['postalCode', 'Postal code']]
const two: React.CSSProperties = { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }
const blank = (v: string) => (v.trim() === '' ? undefined : v.trim())

/** Shared save plumbing: run the call, refresh every Facilities query, toast, close. */
function useSave<B, R>(call: (b: B) => Promise<R>, done: string, onClose: () => void, onSaved?: (r: R) => void) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: call,
    onSuccess: r => { qc.invalidateQueries({ queryKey: ['facilities'] }); toast.success(done); onSaved?.(r); onClose() },
    onError: e => toast.error(errorMessage(e)),
  })
}

function FormDialog({ title, onClose, onSave, busy, valid, children, width = 520, saveLabel = 'Save' }:
  { title: string; onClose: () => void; onSave: () => void; busy: boolean; valid: boolean; children: React.ReactNode; width?: number; saveLabel?: string }) {
  return (
    <Dialog title={title} onClose={onClose} width={width}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" busy={busy} disabled={!valid} onClick={onSave}>{saveLabel}</Btn></>}>
      <form onSubmit={e => { e.preventDefault(); if (valid && !busy) onSave() }}>{children}</form>
    </Dialog>
  )
}

// ── site ────────────────────────────────────────────────────────────────────────────────────
export function SiteFormDialog({ site, onClose }: { site?: Site; onClose: () => void }) {
  const [name, setName] = useState(site?.name ?? '')
  const [siteType, setSiteType] = useState(site?.siteType ?? 'OFFICE')
  const [notes, setNotes] = useState(site?.notes ?? '')
  const [address, setAddress] = useState<Record<string, string>>(site?.address ?? {})
  const save = useSave((b: SiteBody) => (site ? facilitiesApi.updateSite(site.id, b) : facilitiesApi.createSite(b)), site ? 'Site updated' : 'Site created', onClose)
  const body = (): SiteBody => ({
    name: name.trim(), siteType, notes: blank(notes),
    address: Object.fromEntries(Object.entries(address).filter(([, v]) => v.trim()).map(([k, v]) => [k, v.trim()])),
  })
  return (
    <FormDialog title={site ? `Edit ${site.name}` : 'New site'} onClose={onClose} busy={save.isPending} valid={name.trim().length > 0} onSave={() => save.mutate(body())}>
      <Field label="Name *"><input value={name} onChange={e => setName(e.target.value)} maxLength={200} style={inputStyle} autoFocus /></Field>
      <Field label="Type"><select value={siteType} onChange={e => setSiteType(e.target.value)} style={inputStyle}>{SITE_TYPES.map(t => <option key={t} value={t}>{titleCase(t)}</option>)}</select></Field>
      <div style={two}>
        {ADDRESS_FIELDS.map(([k, label]) => (
          <Field key={k} label={label}><input value={address[k] ?? ''} onChange={e => setAddress(a => ({ ...a, [k]: e.target.value }))} maxLength={120} style={inputStyle} /></Field>
        ))}
      </div>
      <Field label="Notes"><textarea value={notes} onChange={e => setNotes(e.target.value)} rows={3} maxLength={1000} style={{ ...inputStyle, resize: 'vertical' }} /></Field>
    </FormDialog>
  )
}

// ── asset ───────────────────────────────────────────────────────────────────────────────────
export function AssetFormDialog({ asset, defaultSiteId, onClose }: { asset?: Asset; defaultSiteId?: string; onClose: () => void }) {
  const sites = useQuery({ queryKey: ['facilities', 'sites'], queryFn: facilitiesApi.sites, staleTime: 5 * 60_000 })
  const [f, setF] = useState({
    siteId: asset?.siteId ?? defaultSiteId ?? '', assetTag: asset?.assetTag ?? '', name: asset?.name ?? '', assetType: asset?.assetType ?? '',
    location: asset?.location ?? '', manufacturer: asset?.manufacturer ?? '', model: asset?.model ?? '', serialNumber: asset?.serialNumber ?? '',
    installDate: asset?.installDate ?? '', warrantyExpiryDate: asset?.warrantyExpiryDate ?? '', criticality: asset?.criticality ?? 'MEDIUM',
    status: asset?.status ?? 'OPERATIONAL', notes: asset?.notes ?? '',
  })
  const set = (k: keyof typeof f, v: string) => setF(s => ({ ...s, [k]: v }))
  const qc = useQueryClient()
  const save = useMutation({
    mutationFn: async () => {
      const body: AssetBody = {
        name: f.name.trim(), location: blank(f.location), manufacturer: blank(f.manufacturer), model: blank(f.model), serialNumber: blank(f.serialNumber),
        warrantyExpiryDate: f.warrantyExpiryDate || null, criticality: f.criticality, notes: blank(f.notes),
      }
      if (!asset) return facilitiesApi.createAsset({ ...body, siteId: f.siteId, assetTag: blank(f.assetTag), assetType: f.assetType.trim(), installDate: f.installDate || null })
      const updated = await facilitiesApi.updateAsset(asset.id, body)
      // Status has its own endpoint (it is audited separately), so call it only when it actually changed.
      return f.status !== asset.status ? facilitiesApi.setAssetStatus(asset.id, f.status) : updated
    },
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['facilities'] }); toast.success(asset ? 'Asset updated' : 'Asset created'); onClose() },
    onError: e => toast.error(errorMessage(e)),
  })
  const valid = f.name.trim() && (asset || (f.siteId && f.assetType.trim()))
  return (
    <FormDialog title={asset ? `Edit ${asset.assetTag}` : 'New asset'} onClose={onClose} busy={save.isPending} valid={!!valid} onSave={() => save.mutate()} width={600}>
      {!asset && (
        <Field label="Site *">
          <select value={f.siteId} onChange={e => set('siteId', e.target.value)} style={inputStyle}>
            <option value="">Select a site…</option>
            {(sites.data ?? []).filter(s => s.status !== 'CLOSED').map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
        </Field>
      )}
      <div style={two}>
        <Field label="Name *"><input value={f.name} onChange={e => set('name', e.target.value)} maxLength={200} style={inputStyle} autoFocus /></Field>
        {!asset
          ? <Field label="Tag" hint="Leave blank to generate one."><input value={f.assetTag} onChange={e => set('assetTag', e.target.value)} maxLength={60} style={inputStyle} /></Field>
          : <Field label="Tag"><input value={f.assetTag} disabled style={inputStyle} /></Field>}
        <Field label={asset ? 'Type' : 'Type *'}><input value={f.assetType} onChange={e => set('assetType', e.target.value)} disabled={!!asset} placeholder="e.g. HVAC, LIFT, GENERATOR" maxLength={60} style={inputStyle} /></Field>
        <Field label="Location"><input value={f.location} onChange={e => set('location', e.target.value)} maxLength={200} placeholder="Floor, room or area" style={inputStyle} /></Field>
        <Field label="Manufacturer"><input value={f.manufacturer} onChange={e => set('manufacturer', e.target.value)} maxLength={120} style={inputStyle} /></Field>
        <Field label="Model"><input value={f.model} onChange={e => set('model', e.target.value)} maxLength={120} style={inputStyle} /></Field>
        <Field label="Serial number"><input value={f.serialNumber} onChange={e => set('serialNumber', e.target.value)} maxLength={120} style={inputStyle} /></Field>
        <Field label="Criticality"><select value={f.criticality} onChange={e => set('criticality', e.target.value)} style={inputStyle}>{CRITICALITY.map(c => <option key={c} value={c}>{titleCase(c)}</option>)}</select></Field>
        <Field label="Installed"><input type="date" value={f.installDate} onChange={e => set('installDate', e.target.value)} disabled={!!asset} style={inputStyle} /></Field>
        <Field label="Warranty until"><input type="date" value={f.warrantyExpiryDate} onChange={e => set('warrantyExpiryDate', e.target.value)} style={inputStyle} /></Field>
      </div>
      {asset && <Field label="Status"><select value={f.status} onChange={e => set('status', e.target.value)} style={inputStyle}>{ASSET_STATUS.map(c => <option key={c} value={c}>{titleCase(c)}</option>)}</select></Field>}
      <Field label="Notes"><textarea value={f.notes} onChange={e => set('notes', e.target.value)} rows={3} maxLength={1000} style={{ ...inputStyle, resize: 'vertical' }} /></Field>
    </FormDialog>
  )
}

// ── vendor ──────────────────────────────────────────────────────────────────────────────────
export function VendorFormDialog({ vendor, onClose }: { vendor?: Vendor; onClose: () => void }) {
  const [f, setF] = useState({
    companyName: vendor?.companyName ?? '', serviceType: vendor?.serviceType ?? '', contactName: vendor?.contactName ?? '',
    contactPhone: vendor?.contactPhone ?? '', contactEmail: vendor?.contactEmail ?? '', notes: vendor?.notes ?? '',
  })
  const set = (k: keyof typeof f, v: string) => setF(s => ({ ...s, [k]: v }))
  const save = useSave((b: VendorBody) => (vendor ? facilitiesApi.updateVendor(vendor.id, b) : facilitiesApi.createVendor(b)), vendor ? 'Vendor updated' : 'Vendor added', onClose)
  const emailOk = !f.contactEmail.trim() || /^\S+@\S+\.\S+$/.test(f.contactEmail.trim())
  const body = (): VendorBody => ({ companyName: f.companyName.trim(), serviceType: blank(f.serviceType), contactName: blank(f.contactName), contactPhone: blank(f.contactPhone), contactEmail: blank(f.contactEmail), notes: blank(f.notes) })
  return (
    <FormDialog title={vendor ? `Edit ${vendor.companyName}` : 'New vendor'} onClose={onClose} busy={save.isPending} valid={f.companyName.trim().length > 0 && emailOk} onSave={() => save.mutate(body())}>
      {!vendor && <p style={{ margin: '0 0 14px', fontSize: 12.5, color: 'var(--hf-text-muted)' }}>New vendors start as pending. Add their documents, then someone else approves them before they can be given work or ordered from.</p>}
      <Field label="Company name *"><input value={f.companyName} onChange={e => set('companyName', e.target.value)} maxLength={200} style={inputStyle} autoFocus /></Field>
      <Field label="Service"><input value={f.serviceType} onChange={e => set('serviceType', e.target.value)} maxLength={100} placeholder="e.g. Electrical, Plumbing, HVAC" style={inputStyle} /></Field>
      <div style={two}>
        <Field label="Contact person"><input value={f.contactName} onChange={e => set('contactName', e.target.value)} maxLength={120} style={inputStyle} /></Field>
        <Field label="Phone"><input value={f.contactPhone} onChange={e => set('contactPhone', e.target.value)} maxLength={40} inputMode="tel" style={inputStyle} /></Field>
      </div>
      <Field label="Email" hint={emailOk ? undefined : 'That email address does not look right.'}><input type="email" value={f.contactEmail} onChange={e => set('contactEmail', e.target.value)} maxLength={200} style={inputStyle} /></Field>
      <Field label="Notes"><textarea value={f.notes} onChange={e => set('notes', e.target.value)} rows={3} maxLength={1000} style={{ ...inputStyle, resize: 'vertical' }} /></Field>
    </FormDialog>
  )
}

// ── stock item ──────────────────────────────────────────────────────────────────────────────
export function StockItemFormDialog({ item, onClose }: { item?: StockItem; onClose: () => void }) {
  const [f, setF] = useState({
    sku: item?.sku ?? '', name: item?.name ?? '', unit: item?.unit ?? 'ea', location: item?.location ?? '',
    reorderLevel: item ? String(item.reorderLevel ?? '') : '', reorderQty: item ? String(item.reorderQty ?? '') : '', openingQuantity: '', openingUnitCost: '',
  })
  const set = (k: keyof typeof f, v: string) => setF(s => ({ ...s, [k]: v }))
  const save = useSave((b: StockItemBody) => (item ? facilitiesApi.updateStockItem(item.id, b) : facilitiesApi.createStockItem(b)), item ? 'Item updated' : 'Item added', onClose)
  const num = (v: string) => (v.trim() === '' ? undefined : Number(v))
  const bad = (v: string) => v.trim() !== '' && !(Number(v) >= 0)
  const opening = num(f.openingQuantity) ?? 0
  const valid = f.sku.trim() && f.name.trim() && f.unit.trim() && !bad(f.reorderLevel) && !bad(f.reorderQty) && !bad(f.openingQuantity) && !bad(f.openingUnitCost)
    && (opening === 0 || f.openingUnitCost.trim() !== '')
  const body = (): StockItemBody => ({
    sku: f.sku.trim(), name: f.name.trim(), unit: f.unit.trim(), location: blank(f.location), reorderLevel: num(f.reorderLevel), reorderQty: num(f.reorderQty),
    ...(item ? {} : { openingQuantity: opening || undefined, openingUnitCost: opening ? num(f.openingUnitCost) : undefined }),
  })
  return (
    <FormDialog title={item ? `Edit ${item.sku}` : 'New stock item'} onClose={onClose} busy={save.isPending} valid={!!valid} onSave={() => save.mutate(body())}>
      <div style={two}>
        <Field label="SKU *" hint={item ? 'The SKU cannot change.' : undefined}><input value={f.sku} onChange={e => set('sku', e.target.value)} disabled={!!item} maxLength={60} style={inputStyle} autoFocus={!item} /></Field>
        <Field label="Unit *"><input value={f.unit} onChange={e => set('unit', e.target.value)} maxLength={20} placeholder="ea, m, kg, l" style={inputStyle} /></Field>
      </div>
      <Field label="Name *"><input value={f.name} onChange={e => set('name', e.target.value)} maxLength={200} style={inputStyle} autoFocus={!!item} /></Field>
      <Field label="Storage location"><input value={f.location} onChange={e => set('location', e.target.value)} maxLength={100} placeholder="Shelf or store" style={inputStyle} /></Field>
      <div style={two}>
        <Field label="Reorder level" hint="Alert when on hand falls to this."><input type="number" min="0" step="0.001" value={f.reorderLevel} onChange={e => set('reorderLevel', e.target.value)} style={inputStyle} /></Field>
        <Field label="Standard reorder quantity"><input type="number" min="0" step="0.001" value={f.reorderQty} onChange={e => set('reorderQty', e.target.value)} style={inputStyle} /></Field>
      </div>
      {!item && (
        <div style={two}>
          <Field label="Opening quantity" hint="Booked as a receipt."><input type="number" min="0" step="0.001" value={f.openingQuantity} onChange={e => set('openingQuantity', e.target.value)} style={inputStyle} /></Field>
          <Field label={opening ? 'Opening unit cost (R) *' : 'Opening unit cost (R)'}><input type="number" min="0" step="0.01" value={f.openingUnitCost} onChange={e => set('openingUnitCost', e.target.value)} style={inputStyle} /></Field>
        </div>
      )}
    </FormDialog>
  )
}

// ── purchase order ──────────────────────────────────────────────────────────────────────────
interface Line { itemId: string; quantity: string; unitCost: string }

export function PurchaseOrderFormDialog({ onClose }: { onClose: () => void }) {
  const vendors = useQuery({ queryKey: ['facilities', 'vendors'], queryFn: facilitiesApi.vendors })
  const items = useQuery({ queryKey: ['facilities', 'stock', ''], queryFn: () => facilitiesApi.stock() })
  const sugg = useQuery({ queryKey: ['facilities', 'reorder'], queryFn: facilitiesApi.reorderSuggestions })
  const [vendorId, setVendorId] = useState('')
  const [expected, setExpected] = useState('')
  const [notes, setNotes] = useState('')
  const [lines, setLines] = useState<Line[]>([{ itemId: '', quantity: '', unitCost: '' }])
  const save = useSave((b: PurchaseOrderBody) => facilitiesApi.createPurchaseOrder(b), 'Purchase order saved as a draft', onClose)

  const setLine = (i: number, patch: Partial<Line>) => setLines(ls => ls.map((l, j) => (j === i ? { ...l, ...patch } : l)))
  const pickItem = (i: number, itemId: string) => {
    const it = items.data?.find(x => x.id === itemId)
    setLine(i, { itemId, unitCost: it && it.unitCost ? String(it.unitCost) : lines[i].unitCost })
  }
  const fromSuggestions = () => {
    const s = (sugg.data ?? []).map(x => ({ itemId: x.itemId, quantity: String(x.suggestedQuantity), unitCost: x.unitCost ? String(x.unitCost) : '' }))
    if (s.length) setLines(s)
  }
  const total = lines.reduce((t, l) => t + (Number(l.quantity) || 0) * (Number(l.unitCost) || 0), 0)
  const lineOk = (l: Line) => l.itemId && Number(l.quantity) > 0 && l.unitCost.trim() !== '' && Number(l.unitCost) >= 0
  const dup = new Set(lines.map(l => l.itemId).filter(Boolean)).size !== lines.filter(l => l.itemId).length
  const valid = vendorId && lines.length > 0 && lines.every(lineOk) && !dup
  const eligible = (vendors.data ?? []).filter(v => v.active && v.onboardingStatus === 'APPROVED')
  return (
    <FormDialog title="New purchase order" onClose={onClose} busy={save.isPending} valid={!!valid} width={680} saveLabel="Save draft"
      onSave={() => save.mutate({
        vendorId, expectedDate: expected || null, notes: blank(notes),
        lines: lines.map(l => ({ itemId: l.itemId, quantity: Number(l.quantity), unitCost: Number(l.unitCost) })),
      })}>
      <div style={two}>
        <Field label="Vendor *" hint={eligible.length === 0 && !vendors.isLoading ? 'No approved vendors yet. Approve one first.' : 'Only approved vendors are listed.'}>
          <select value={vendorId} onChange={e => setVendorId(e.target.value)} style={inputStyle}>
            <option value="">Select a vendor…</option>
            {eligible.map(v => <option key={v.id} value={v.id}>{v.companyName}</option>)}
          </select>
        </Field>
        <Field label="Expected delivery"><input type="date" value={expected} onChange={e => setExpected(e.target.value)} style={inputStyle} /></Field>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', margin: '4px 0 8px' }}>
        <h3 style={{ margin: 0, fontSize: 13, fontWeight: 700 }}>Lines</h3>
        {(sugg.data?.length ?? 0) > 0 && <Btn onClick={fromSuggestions}>Fill from reorder suggestions</Btn>}
      </div>
      {lines.map((l, i) => (
        <div key={i} style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: 10, alignItems: 'center' }}>
          <select aria-label={`Item, line ${i + 1}`} value={l.itemId} onChange={e => pickItem(i, e.target.value)} style={{ ...inputStyle, flex: '1 1 240px', width: 'auto', minWidth: 0 }}>
            <option value="">Select an item…</option>
            {(items.data ?? []).filter(x => x.active).map(x => <option key={x.id} value={x.id}>{x.sku} · {x.name}</option>)}
          </select>
          <input aria-label={`Quantity, line ${i + 1}`} type="number" min="0" step="0.001" placeholder="Qty" value={l.quantity} onChange={e => setLine(i, { quantity: e.target.value })} style={{ ...inputStyle, flex: '1 1 90px', width: 'auto', minWidth: 0 }} />
          <input aria-label={`Unit cost, line ${i + 1}`} type="number" min="0" step="0.01" placeholder="Unit cost" value={l.unitCost} onChange={e => setLine(i, { unitCost: e.target.value })} style={{ ...inputStyle, flex: '1 1 110px', width: 'auto', minWidth: 0 }} />
          <button type="button" aria-label={`Remove line ${i + 1}`} disabled={lines.length === 1} onClick={() => setLines(ls => ls.filter((_, j) => j !== i))}
            style={{ background: 'none', border: 'none', cursor: lines.length === 1 ? 'not-allowed' : 'pointer', color: 'var(--hf-text-muted)', opacity: lines.length === 1 ? 0.4 : 1, display: 'flex' }}><Trash2 size={16} /></button>
        </div>
      ))}
      {dup && <div role="alert" style={{ fontSize: 12.5, color: 'var(--hf-danger-text)', marginBottom: 8 }}>An item appears on more than one line. Combine them.</div>}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', margin: '4px 0 16px' }}>
        <Btn onClick={() => setLines(ls => [...ls, { itemId: '', quantity: '', unitCost: '' }])} disabled={lines.length >= 50}><Plus size={14} />Add line</Btn>
        <div style={{ fontSize: 14 }}>Total <strong>{fmtMoney(total)}</strong></div>
      </div>
      <Field label="Notes"><textarea value={notes} onChange={e => setNotes(e.target.value)} rows={2} maxLength={1000} style={{ ...inputStyle, resize: 'vertical' }} /></Field>
    </FormDialog>
  )
}
