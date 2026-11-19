// src/pages/marketing/RevenuePanel.tsx
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { Plus, X, Info, DollarSign, Pencil, ChevronLeft, ChevronRight, Undo2 } from 'lucide-react'
import { C, Card, Kpi, Btn, Pill, Empty, LineChart, inp, lbl, fmtN, fmtDT } from './ui'

export const money = (cents: number | null | undefined, cur = 'ZAR') =>
  new Intl.NumberFormat('en-ZA', { style: 'currency', currency: cur, maximumFractionDigits: 2 }).format((cents ?? 0) / 100)
const toCents = (v: string) => { const n = Number(v.replace(',', '.')); return Number.isFinite(n) && n >= 0 ? Math.round(n * 100) : null }
const dayLabel = (iso: string) => new Date(iso + 'T00:00:00').toLocaleDateString('en-ZA', { day: 'numeric', month: 'short' })
const adminMsg = (e: any, d: string) => e.response?.status === 403 ? 'That needs the Marketing admin permission.' : (e.response?.data?.message || d)

export default function RevenuePanel() {
  const qc = useQueryClient()
  const [days, setDays] = useState(30)
  const [cur] = useState('ZAR')
  const [drill, setDrill] = useState<any>(null)
  const [costEdit, setCostEdit] = useState<{ id: string; value: string } | null>(null)
  const [showRecord, setShowRecord] = useState<'sale' | 'refund' | null>(null)
  const [toast, setToast] = useState<{ ok: boolean; text: string } | null>(null)
  const say = (ok: boolean, text: string) => { setToast({ ok, text }); setTimeout(() => setToast(null), 6000) }

  const { data, isLoading } = useQuery<any>({
    queryKey: ['marketing-attribution', days, cur],
    queryFn: async () => (await apiClient.get('/api/v1/marketing/attribution/overview', { params: { days, currency: cur } })).data?.data,
  })
  const refresh = () => { qc.invalidateQueries({ queryKey: ['marketing-attribution'] }); qc.invalidateQueries({ queryKey: ['marketing-attribution-dash'] }) }

  const saveCost = useMutation({
    mutationFn: ({ id, cents }: { id: string; cents: number | null }) => apiClient.put(`/api/v1/marketing/attribution/campaigns/${id}/cost`, { costCents: cents }),
    onSuccess: () => { setCostEdit(null); refresh() }, onError: (e: any) => say(false, adminMsg(e, 'Could not save cost')),
  })
  const saveWindow = useMutation({
    mutationFn: (d: number) => apiClient.put('/api/v1/marketing/attribution/settings', { windowDays: d }),
    onSuccess: () => { refresh(); say(true, 'Window saved. It applies to sales recorded from now on.') }, onError: (e: any) => say(false, adminMsg(e, 'Could not save')),
  })

  const t = data?.totals
  const unattributed = t ? t.revenueCents - t.attributedRevenueCents : 0
  const share = t && t.revenueCents > 0 ? t.attributedRevenueCents / t.revenueCents : null
  const campaigns: any[] = data?.campaigns ?? []
  const costed = campaigns.filter(c => c.costCents && c.costCents > 0)
  const totalCost = costed.reduce((a, c) => a + c.costCents, 0)
  const costedRev = costed.reduce((a, c) => a + c.revenueCents, 0)
  const series: any[] = data?.series ?? []

  return (
    <div>
      <div style={{ display: 'flex', gap: 10, padding: '12px 16px', background: '#F0F9FF', border: '1px solid #BAE6FD', borderRadius: 10, marginBottom: 16, fontSize: 12, color: '#0C4A6E', lineHeight: 1.6 }}>
        <Info size={16} color="#0369A1" style={{ flexShrink: 0, marginTop: 2 }} />
        <div><strong>How revenue is credited:</strong> a sale goes to the campaign whose link the buyer last clicked within {data?.windowDays ?? 7} days before they paid. Opens are never counted.
          Buyers who read an email and later bought without clicking are not credited, so these figures are a floor, not a total. Revenue is net of refunds, and ROI is revenue-based, not profit.</div>
      </div>

      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10, marginBottom: 16, flexWrap: 'wrap', alignItems: 'center' }}>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: 13, color: C.text }}>
          Sales from the last
          <select value={days} onChange={e => setDays(Number(e.target.value))} style={{ ...inp, width: 'auto', padding: '7px 10px', fontSize: 13 }}>{[7, 30, 90, 180, 365].map(d => <option key={d} value={d}>{d} days</option>)}</select>
          · attribution window
          <select value={data?.windowDays ?? 7} onChange={e => saveWindow.mutate(Number(e.target.value))} style={{ ...inp, width: 'auto', padding: '7px 10px', fontSize: 13 }}>{[1, 3, 7, 14, 30].map(d => <option key={d} value={d}>{d} day{d > 1 ? 's' : ''}</option>)}</select>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <Btn onClick={() => setShowRecord('refund')}><Undo2 size={13} /> Record refund</Btn>
          <Btn variant="primary" onClick={() => setShowRecord('sale')}><Plus size={14} /> Record sale</Btn>
        </div>
      </div>

      {isLoading ? <div style={{ textAlign: 'center', padding: 40, color: C.mute }}>Loading…</div>
        : !t || (t.orders === 0 && t.revenueCents === 0) ? (
          <Empty icon={<DollarSign size={36} />} title="No sales recorded in this period"
            text="Sales appear here once your payments are reported to Marketing. Until the invoicing connection is in place, use “Record sale” for offline sales." />
        ) : (
          <>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 14, marginBottom: 18 }}>
              <Kpi label="Attributed revenue" value={money(t.attributedRevenueCents, cur)} sub={`${fmtN(t.attributedOrders)} order(s) credited to campaigns`} />
              <Kpi label="All recorded revenue" value={money(t.revenueCents, cur)} sub={`${fmtN(t.orders)} order(s), net of refunds`} />
              <Kpi label="Share attributed" value={share == null ? '—' : `${Math.round(share * 100)}%`} sub={`${money(unattributed, cur)} not linked to a campaign`} />
              <Kpi label="ROI (costed campaigns)" value={totalCost > 0 ? `${Math.round(((costedRev - totalCost) / totalCost) * 100)}%` : '—'}
                sub={totalCost > 0 ? `${money(costedRev, cur)} on ${money(totalCost, cur)} spend` : 'Enter a campaign cost to see ROI'} />
            </div>
            {data.otherCurrencyCount > 0 && <div style={{ fontSize: 12, color: C.amber, marginBottom: 12 }}>{data.otherCurrencyCount} sale(s) in other currencies are not included in these {cur} figures.</div>}

            <Card title="Revenue by day" style={{ marginBottom: 18 }}>
              <LineChart labels={series.map(p => dayLabel(p.date))} series={[
                { name: 'All revenue', color: C.mute, values: series.map(p => Math.max(0, Math.round(p.revenueCents / 100))) },
                { name: 'Credited to campaigns', color: C.primary, values: series.map(p => Math.max(0, Math.round(p.attributedCents / 100))) }]} />
              <div style={{ fontSize: 11, color: C.mute, marginTop: 6 }}>Rand amounts, by payment date (South African time).</div>
            </Card>

            <Card title="Revenue by campaign" pad={0}>
              {campaigns.length === 0 ? <Empty title="No sales have been credited to a campaign yet" text="A sale is credited when the buyer’s email matches a contact who clicked a campaign link before paying." /> : (
                <div style={{ overflowX: 'auto' }}>
                  <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
                    <thead><tr style={{ borderBottom: `1px solid ${C.line}` }}>
                      {['Campaign', 'Orders', 'Revenue', 'Per email sent', 'Cost', 'ROI', ''].map(h => <th key={h} style={{ padding: '10px 16px', textAlign: 'left', fontSize: 11, color: C.mute, fontWeight: 700, whiteSpace: 'nowrap' }}>{h}</th>)}
                    </tr></thead>
                    <tbody>
                      {campaigns.map(c => (
                        <tr key={c.id} style={{ borderBottom: '1px solid #F1F5F9' }}>
                          <td style={{ padding: '11px 16px' }}>
                            <div style={{ fontWeight: 700, color: C.ink }}>{c.name.replace(/^Journey: /, '')}</div>
                            {c.kind === 'JOURNEY' && <Pill color="#7C3AED" bg="#F5F3FF">Automation</Pill>}
                            <span style={{ fontSize: 11, color: C.mute, marginLeft: c.kind === 'JOURNEY' ? 6 : 0 }}>{fmtN(c.sent)} sent · {fmtN(c.clicked)} clicked</span>
                          </td>
                          <td style={{ padding: '11px 16px' }}>{fmtN(c.orders)}</td>
                          <td style={{ padding: '11px 16px', fontWeight: 700 }}>{money(c.revenueCents, cur)}</td>
                          <td style={{ padding: '11px 16px' }}>{c.revenuePerSentCents == null ? '—' : money(c.revenuePerSentCents, cur)}</td>
                          <td style={{ padding: '11px 16px' }}>
                            {costEdit && costEdit.id === c.id ? (
                              <span style={{ display: 'inline-flex', gap: 4 }}>
                                <input autoFocus style={{ ...inp, width: 90, padding: '4px 8px', fontSize: 12 }} value={costEdit.value} placeholder="R"
                                  onChange={e => setCostEdit({ id: c.id, value: e.target.value })}
                                  onKeyDown={e => { if (e.key === 'Enter') { const v = costEdit.value.trim(); const cents = v === '' ? null : toCents(v); if (v === '' || cents != null) saveCost.mutate({ id: c.id, cents }) } if (e.key === 'Escape') setCostEdit(null) }} />
                                <Btn variant="primary" style={{ padding: '3px 9px', fontSize: 11 }} disabled={saveCost.isPending}
                                  onClick={() => { const v = costEdit.value.trim(); const cents = v === '' ? null : toCents(v); if (v === '' || cents != null) saveCost.mutate({ id: c.id, cents }) }}>Save</Btn>
                              </span>
                            ) : (
                              <button onClick={() => setCostEdit({ id: c.id, value: c.costCents != null ? String(c.costCents / 100) : '' })} title="Set the cost (admin)"
                                style={{ background: 'none', border: 'none', cursor: 'pointer', color: c.costCents != null ? C.ink : C.primary, fontSize: 13, display: 'inline-flex', gap: 4, alignItems: 'center' }}>
                                {c.costCents != null ? money(c.costCents, cur) : 'Add cost'} <Pencil size={11} /></button>)}
                          </td>
                          <td style={{ padding: '11px 16px', fontWeight: 700, color: c.roi == null ? C.mute : c.roi >= 0 ? C.green : C.red }}>{c.roi == null ? '—' : `${Math.round(c.roi * 100)}%`}</td>
                          <td style={{ padding: '11px 16px' }}><Btn onClick={() => setDrill(c)} style={{ padding: '4px 10px', fontSize: 12 }}>Sales</Btn></td>
                        </tr>))}
                    </tbody>
                  </table>
                </div>)}
            </Card>
            <div style={{ fontSize: 11, color: C.mute, marginTop: 8 }}>Only campaigns that were credited with a sale in this period are listed. Costs are entered by hand.</div>
          </>)}

      {drill && <Conversions campaign={drill} cur={cur} onClose={() => setDrill(null)} />}
      {showRecord && <RecordModal kind={showRecord} onClose={() => setShowRecord(null)} onDone={(m: string) => { setShowRecord(null); refresh(); say(true, m) }} />}

      {toast && <div style={{ position: 'fixed', bottom: 24, right: 24, zIndex: 3000, background: toast.ok ? '#DCFCE7' : '#FEF2F2', border: `1px solid ${toast.ok ? '#86EFAC' : '#FECACA'}`, borderRadius: 10, padding: '12px 18px', maxWidth: 440, fontSize: 13, fontWeight: 600, color: toast.ok ? '#166534' : '#DC2626' }}>{toast.text}</div>}
    </div>
  )
}

function Conversions({ campaign, cur, onClose }: { campaign: any; cur: string; onClose: () => void }) {
  const [page, setPage] = useState(0)
  const { data } = useQuery<any>({
    queryKey: ['marketing-attribution-conv', campaign.id, page], placeholderData: (p: any) => p,
    queryFn: async () => (await apiClient.get(`/api/v1/marketing/attribution/campaigns/${campaign.id}/conversions`, { params: { page, size: 20 } })).data?.data,
  })
  const pages = data?.totalPages ?? 1
  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', display: 'flex', justifyContent: 'flex-end', zIndex: 1000 }} onClick={onClose}>
      <div style={{ background: '#fff', width: 560, maxWidth: '96vw', height: '100%', overflowY: 'auto', padding: 28 }} onClick={e => e.stopPropagation()}>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 14 }}>
          <div><div style={{ fontWeight: 800, fontSize: 17 }}>{campaign.name.replace(/^Journey: /, '')}</div><div style={{ fontSize: 12, color: C.text, marginTop: 3 }}>Sales credited to this campaign · {money(campaign.revenueCents, cur)} net</div></div>
          <button onClick={onClose} style={{ background: '#F1F5F9', border: 'none', borderRadius: '50%', width: 30, height: 30, cursor: 'pointer' }}><X size={14} /></button>
        </div>
        <div style={{ border: `1px solid ${C.line}`, borderRadius: 10, overflow: 'hidden' }}>
          {(data?.content ?? []).map((r: any, i: number) => (
            <div key={r.id} style={{ display: 'flex', gap: 10, padding: '10px 14px', borderTop: i ? '1px solid #F1F5F9' : 'none', fontSize: 12, alignItems: 'center' }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontWeight: 600, color: C.ink }}>{r.emailMasked} {r.kind === 'REFUND' && <Pill color="#991B1B" bg="#FEE2E2">Refund</Pill>}</div>
                <div style={{ color: C.mute, fontSize: 11 }}>{fmtDT(r.paidAt)}{r.clickAt ? ` · clicked ${fmtDT(r.clickAt)}` : ''}{r.description ? ` · ${r.description}` : ''}{r.source === 'MANUAL' ? ' · entered by hand' : ''}</div>
              </div>
              <div style={{ fontWeight: 700, color: r.amountCents < 0 ? C.red : C.ink }}>{money(r.amountCents, r.currency)}</div>
            </div>))}
          {(data?.content ?? []).length === 0 && <div style={{ padding: 24, textAlign: 'center', color: C.mute, fontSize: 12 }}>No sales.</div>}
        </div>
        {pages > 1 && <div style={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: 8, marginTop: 10, fontSize: 11, color: C.mute }}>Page {page + 1} of {pages}
          <button disabled={page === 0} onClick={() => setPage(p => p - 1)} style={{ display: 'flex', padding: 5, border: `1px solid ${C.line}`, borderRadius: 7, background: '#fff', opacity: page === 0 ? 0.4 : 1 }}><ChevronLeft size={13} /></button>
          <button disabled={page + 1 >= pages} onClick={() => setPage(p => p + 1)} style={{ display: 'flex', padding: 5, border: `1px solid ${C.line}`, borderRadius: 7, background: '#fff', opacity: page + 1 >= pages ? 0.4 : 1 }}><ChevronRight size={13} /></button></div>}
      </div>
    </div>
  )
}

function RecordModal({ kind, onClose, onDone }: { kind: 'sale' | 'refund'; onClose: () => void; onDone: (msg: string) => void }) {
  const [f, setF] = useState({ email: '', amount: '', ref: '', original: '', date: '', description: '' })
  const [error, setError] = useState('')
  const set = (k: string, v: string) => setF(p => ({ ...p, [k]: v }))
  const cents = toCents(f.amount)
  const ok = cents != null && cents > 0 && (kind === 'sale' ? /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(f.email.trim()) : f.original.trim().length > 0)

  const save = useMutation({
    mutationFn: () => kind === 'sale'
      ? apiClient.post('/api/v1/marketing/attribution/conversions', { email: f.email.trim(), amountCents: cents, currency: 'ZAR', externalRef: f.ref.trim() || null, paidAt: f.date ? new Date(f.date).toISOString() : null, description: f.description.trim() || null })
      : apiClient.post('/api/v1/marketing/attribution/conversions/refund', { originalRef: f.original.trim(), externalRef: f.ref.trim() || null, amountCents: cents, refundedAt: f.date ? new Date(f.date).toISOString() : null }),
    onSuccess: (r: any) => {
      const d = r.data?.data
      onDone(!d.recorded ? 'That reference was already recorded, so nothing changed.' : d.attributed ? `Recorded and credited to “${d.campaignName}”.` : 'Recorded. It was not linked to a campaign (no qualifying click in the window).')
    },
    onError: (e: any) => setError(adminMsg(e, 'Could not record')),
  })

  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 2000 }}>
      <div style={{ background: '#fff', borderRadius: 16, padding: 26, width: 460, maxWidth: '96vw' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 14 }}>
          <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800 }}>{kind === 'sale' ? 'Record a sale' : 'Record a refund'}</h3>
          <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: C.mute }}><X size={18} /></button>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 11 }}>
          {kind === 'sale'
            ? <div><label style={lbl}>Customer email *</label><input style={inp} autoFocus value={f.email} onChange={e => set('email', e.target.value)} /></div>
            : <div><label style={lbl}>Reference of the original sale *</label><input style={inp} autoFocus value={f.original} onChange={e => set('original', e.target.value)} /></div>}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
            <div><label style={lbl}>Amount (R) *</label><input style={inp} inputMode="decimal" value={f.amount} placeholder="1250.00" onChange={e => set('amount', e.target.value)} /></div>
            <div><label style={lbl}>{kind === 'sale' ? 'Paid on' : 'Refunded on'}</label><input style={inp} type="datetime-local" value={f.date} onChange={e => set('date', e.target.value)} /></div>
          </div>
          <div><label style={lbl}>Your reference (optional)</label><input style={inp} value={f.ref} maxLength={100} placeholder="e.g. receipt number. Re-entering the same one changes nothing." onChange={e => set('ref', e.target.value)} /></div>
          {kind === 'sale' && <div><label style={lbl}>Description</label><input style={inp} value={f.description} maxLength={200} onChange={e => set('description', e.target.value)} /></div>}
          <div style={{ fontSize: 11, color: C.mute }}>Amounts are in ZAR. Needs the Marketing admin permission.</div>
        </div>
        {error && <div style={{ marginTop: 12, padding: '9px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626' }}>{error}</div>}
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 16 }}>
          <Btn onClick={onClose}>Cancel</Btn>
          <Btn variant="primary" disabled={!ok || save.isPending} onClick={() => { setError(''); save.mutate() }}>{save.isPending ? 'Saving…' : 'Record'}</Btn>
        </div>
      </div>
    </div>
  )
}