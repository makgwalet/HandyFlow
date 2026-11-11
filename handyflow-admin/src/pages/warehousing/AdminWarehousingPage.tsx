// src/pages/warehousing/AdminWarehousingPage.tsx
//
// Platform administrators only. Billing check: what each tenant's Warehousing tier says against what the platform bills. Tenant packages: assign a
// tier with its dates, and give a temporary extra allowance on one limit, with a reason that is kept in the audit trail.
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Warehouse } from 'lucide-react'
import { adminApi } from '../../api/client'
import { findingText, LIMIT_KEYS, limitLabel, limitValue, money, overrideProblem, windowProblem } from './warehousingAdmin'

const inp: React.CSSProperties = { padding: '8px 10px', border: '1.5px solid #2D3748', borderRadius: 8, fontSize: 13, background: '#1A202C', color: '#F7FAFC', fontFamily: 'inherit', boxSizing: 'border-box' }
const lbl: React.CSSProperties = { display: 'block', fontSize: 11, fontWeight: 700, color: '#718096', marginBottom: 4, textTransform: 'uppercase', letterSpacing: '0.06em' }
const btn: React.CSSProperties = { background: 'linear-gradient(135deg,#1B3A6B,#2563EB)', color: '#fff', border: 'none', borderRadius: 8, padding: '9px 16px', fontSize: 13, fontWeight: 600, cursor: 'pointer' }
const btnQuiet: React.CSSProperties = { padding: '6px 10px', border: '1.5px solid #2D3748', borderRadius: 8, background: '#1A202C', fontSize: 12, cursor: 'pointer', color: '#A0AEC0' }
const panel: React.CSSProperties = { background: '#13161E', border: '1px solid #1E2532', borderRadius: 12, padding: 20, marginBottom: 20 }
const TONE: Record<string, string> = { ok: '#68D391', warn: '#F6AD55', info: '#90CDF4' }

const unwrap = (r: any) => r.data?.data ?? r.data
const errMsg = (e: any, fallback: string) => e?.response?.data?.message || fallback
const todayStr = () => new Date().toLocaleDateString('en-CA')

function BillingCheck() {
  const { data: rows = [], isLoading, error } = useQuery<any[]>({
    queryKey: ['admin-whse-billing-check'],
    queryFn: async () => unwrap(await adminApi.get('/warehousing/billing-check')) ?? [],
  })
  return (
    <section style={panel} aria-label="Billing check">
      <h2 style={{ fontSize: 15, fontWeight: 800, margin: '0 0 4px' }}>Billing check</h2>
      <p style={{ fontSize: 12.5, color: '#718096', margin: '0 0 14px' }}>
        The platform bills one flat catalogue price for Warehousing. Where a tenant's tier price differs, adjust the invoice by hand until tiered billing exists.
      </p>
      {isLoading ? <p style={{ color: '#718096', fontSize: 13 }}>Loading…</p>
        : error ? <p role="alert" style={{ color: '#FC8181', fontSize: 13 }}>{errMsg(error, 'Could not load the billing check')}</p>
        : rows.length === 0 ? <p style={{ color: '#718096', fontSize: 13 }}>Nothing to report.</p>
        : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
              <thead><tr style={{ textAlign: 'left', color: '#718096', fontSize: 11, textTransform: 'uppercase' }}>
                {['Tenant', 'Module', 'Tier', 'Enforcement', 'Tier price', 'Billed', 'Finding'].map(h => <th key={h} style={{ padding: '6px 10px' }}>{h}</th>)}
              </tr></thead>
              <tbody>
                {rows.map(r => {
                  const f = findingText(r.finding)
                  return (
                    <tr key={r.tenantId} style={{ borderTop: '1px solid #1E2532' }}>
                      <td style={{ padding: '8px 10px', fontWeight: 600 }}>{r.tenantName}</td>
                      <td style={{ padding: '8px 10px' }}>{r.moduleStatus ?? '—'}</td>
                      <td style={{ padding: '8px 10px' }}>{r.tierCode ?? '—'}</td>
                      <td style={{ padding: '8px 10px' }}>{r.enforcement ?? '—'}</td>
                      <td style={{ padding: '8px 10px' }}>{money(r.tierPrice)}</td>
                      <td style={{ padding: '8px 10px' }}>{money(r.cataloguePrice)}</td>
                      <td style={{ padding: '8px 10px' }} title={r.message}><span style={{ color: TONE[f.tone], fontWeight: 700 }}>{f.label}</span></td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
    </section>
  )
}

function Overrides({ tenantId }: { tenantId: string }) {
  const qc = useQueryClient()
  const [limit, setLimit] = useState(LIMIT_KEYS[0].key)
  const [extra, setExtra] = useState('')
  const [validUntil, setValidUntil] = useState('')
  const [reason, setReason] = useState('')
  const [shown, setShown] = useState<string | null>(null)
  const refresh = () => { qc.invalidateQueries({ queryKey: ['admin-whse-overrides', tenantId] }); qc.invalidateQueries({ queryKey: ['admin-whse-tenant', tenantId] }) }

  const { data: list = [] } = useQuery<any[]>({
    queryKey: ['admin-whse-overrides', tenantId],
    queryFn: async () => unwrap(await adminApi.get(`/warehousing/tenants/${tenantId}/overrides`)) ?? [],
  })
  const add = useMutation({
    mutationFn: () => adminApi.post(`/warehousing/tenants/${tenantId}/overrides`, { limit, extra: Number(extra), validUntil, reason: reason.trim() }),
    onSuccess: () => { setExtra(''); setValidUntil(''); setReason(''); setShown(null); refresh() },
    onError: (e: any) => setShown(errMsg(e, 'Could not save the override')),
  })
  const revoke = useMutation({
    mutationFn: (id: string) => adminApi.delete(`/warehousing/tenants/${tenantId}/overrides/${id}`),
    onSuccess: refresh,
    onError: (e: any) => setShown(errMsg(e, 'Could not revoke the override')),
  })
  const problem = overrideProblem(extra, validUntil, reason, todayStr())

  return (
    <div style={{ marginTop: 18 }}>
      <h3 style={{ fontSize: 13.5, fontWeight: 800, margin: '0 0 8px' }}>Extra allowances</h3>
      {list.length === 0 ? <p style={{ color: '#718096', fontSize: 13, margin: '0 0 10px' }}>No overrides on this tenant.</p> : (
        <ul style={{ listStyle: 'none', padding: 0, margin: '0 0 12px', display: 'grid', gap: 6 }}>
          {list.map(o => (
            <li key={o.id} style={{ display: 'flex', justifyContent: 'space-between', gap: 10, alignItems: 'center', fontSize: 13, opacity: o.inForce ? 1 : 0.55 }}>
              <span>
                <strong>+{Number(o.extra).toLocaleString('en-ZA')} {limitLabel(o.limitKey)}</strong> until {o.validUntil} · {o.reason}
                {!o.inForce && ' (ended or revoked)'}
              </span>
              {o.inForce && <button type="button" style={btnQuiet} disabled={revoke.isPending} onClick={() => revoke.mutate(o.id)}>Revoke</button>}
            </li>
          ))}
        </ul>
      )}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(150px,1fr))', gap: 10, alignItems: 'end' }}>
        <div><label style={lbl} htmlFor="ov-limit">Limit</label>
          <select id="ov-limit" style={{ ...inp, width: '100%' }} value={limit} onChange={e => setLimit(e.target.value)}>{LIMIT_KEYS.map(l => <option key={l.key} value={l.key}>{l.label}</option>)}</select></div>
        <div><label style={lbl} htmlFor="ov-extra">Extra allowed</label>
          <input id="ov-extra" style={{ ...inp, width: '100%' }} inputMode="numeric" value={extra} onChange={e => setExtra(e.target.value)} /></div>
        <div><label style={lbl} htmlFor="ov-until">Until</label>
          <input id="ov-until" type="date" style={{ ...inp, width: '100%' }} value={validUntil} onChange={e => setValidUntil(e.target.value)} /></div>
      </div>
      <div style={{ marginTop: 10 }}>
        <label style={lbl} htmlFor="ov-reason">Reason (kept in the audit trail)</label>
        <input id="ov-reason" style={{ ...inp, width: '100%' }} value={reason} onChange={e => setReason(e.target.value)} maxLength={300} />
      </div>
      {(shown || (problem && (extra || validUntil || reason))) && <p role="alert" style={{ color: '#FC8181', fontSize: 12, margin: '8px 0 0' }}>{shown ?? problem}</p>}
      <div style={{ marginTop: 10 }}><button type="button" style={{ ...btn, opacity: problem || add.isPending ? 0.5 : 1 }} disabled={!!problem || add.isPending} onClick={() => { setShown(null); add.mutate() }}>Add override</button></div>
    </div>
  )
}

function TenantPackage({ tenantId, tenantName }: { tenantId: string; tenantName: string }) {
  const qc = useQueryClient()
  const { data: packages = [] } = useQuery<any[]>({ queryKey: ['admin-whse-packages'], queryFn: async () => unwrap(await adminApi.get('/warehousing/packages')) ?? [] })
  const { data: u, isLoading } = useQuery<any>({ queryKey: ['admin-whse-tenant', tenantId], queryFn: async () => unwrap(await adminApi.get(`/warehousing/tenants/${tenantId}/package`)) })
  const [code, setCode] = useState<string | null>(null)
  const [enforcement, setEnforcement] = useState<string | null>(null)
  const [from, setFrom] = useState<string | null>(null)
  const [to, setTo] = useState<string | null>(null)
  const [note, setNote] = useState<string | null>(null)
  const [msg, setMsg] = useState('')

  const cur = {
    code: code ?? u?.pkg?.code ?? '', enforcement: enforcement ?? u?.enforcement ?? 'MONITOR',
    from: from ?? u?.effectiveFrom ?? '', to: to ?? u?.expiresOn ?? '', note: note ?? u?.note ?? '',
  }
  const problem = windowProblem(cur.from, cur.to)
  const save = useMutation({
    mutationFn: () => adminApi.put(`/warehousing/tenants/${tenantId}/package`, {
      packageCode: cur.code || null, enforcement: cur.enforcement, effectiveFrom: cur.from || null, expiresOn: cur.to || null, note: cur.note.trim() || null,
    }),
    onSuccess: () => { setMsg('Saved'); setCode(null); setEnforcement(null); setFrom(null); setTo(null); setNote(null); qc.invalidateQueries({ queryKey: ['admin-whse-tenant', tenantId] }); qc.invalidateQueries({ queryKey: ['admin-whse-billing-check'] }) },
    onError: (e: any) => setMsg(errMsg(e, 'Could not save the package')),
  })

  return (
    <section style={panel} aria-label={`Package for ${tenantName}`}>
      <h2 style={{ fontSize: 15, fontWeight: 800, margin: '0 0 12px' }}>{tenantName}</h2>
      {isLoading ? <p style={{ color: '#718096', fontSize: 13 }}>Loading…</p> : (
        <>
          {u && (
            <div style={{ marginBottom: 14, fontSize: 13 }}>
              <div>{u.assigned ? <>Package <strong>{u.pkg?.name}</strong> · {u.state}</> : 'No package assigned: no limits apply.'}</div>
              {u.meters?.length > 0 && (
                <ul style={{ margin: '8px 0 0', paddingLeft: 18, color: '#A0AEC0' }}>
                  {u.meters.map((m: any) => (
                    <li key={m.key}>{m.label}: {Number(m.used).toLocaleString('en-ZA')} of {limitValue(m.limit)}{m.extra ? ` (includes ${m.extra} extra)` : ''}{m.over ? ' — over' : ''}</li>
                  ))}
                </ul>
              )}
            </div>
          )}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(170px,1fr))', gap: 10, alignItems: 'end' }}>
            <div><label style={lbl} htmlFor="pk-code">Package</label>
              <select id="pk-code" style={{ ...inp, width: '100%' }} value={cur.code} onChange={e => setCode(e.target.value)}>
                <option value="">No package (unrestricted)</option>
                {packages.map(p => <option key={p.code} value={p.code}>{p.name} · {money(p.monthlyPrice)}</option>)}
              </select></div>
            <div><label style={lbl} htmlFor="pk-enf">Enforcement</label>
              <select id="pk-enf" style={{ ...inp, width: '100%' }} value={cur.enforcement} onChange={e => setEnforcement(e.target.value)}>
                <option value="OFF">Off</option><option value="MONITOR">Monitor (warn only)</option><option value="ENFORCE">Enforce (block)</option>
              </select></div>
            <div><label style={lbl} htmlFor="pk-from">Starts</label><input id="pk-from" type="date" style={{ ...inp, width: '100%' }} value={cur.from} onChange={e => setFrom(e.target.value)} /></div>
            <div><label style={lbl} htmlFor="pk-to">Ends</label><input id="pk-to" type="date" style={{ ...inp, width: '100%' }} value={cur.to} onChange={e => setTo(e.target.value)} /></div>
          </div>
          <div style={{ marginTop: 10 }}><label style={lbl} htmlFor="pk-note">Renewal note</label>
            <input id="pk-note" style={{ ...inp, width: '100%' }} value={cur.note} maxLength={300} onChange={e => setNote(e.target.value)} /></div>
          {(problem || msg) && <p role={problem ? 'alert' : 'status'} style={{ color: problem || msg !== 'Saved' ? '#FC8181' : '#68D391', fontSize: 12, margin: '8px 0 0' }}>{problem ?? msg}</p>}
          <div style={{ marginTop: 10 }}><button type="button" style={{ ...btn, opacity: problem || save.isPending ? 0.5 : 1 }} disabled={!!problem || save.isPending} onClick={() => { setMsg(''); save.mutate() }}>Save package</button></div>
          <Overrides tenantId={tenantId} />
        </>
      )}
    </section>
  )
}

export function AdminWarehousingPage() {
  const [search, setSearch] = useState('')
  const [picked, setPicked] = useState<{ id: string; name: string } | null>(null)
  const { data: tenants = [] } = useQuery<any[]>({
    queryKey: ['admin-whse-tenant-search', search],
    enabled: search.trim().length >= 2,
    queryFn: async () => unwrap(await adminApi.get('/tenants', { params: { search: search.trim(), size: 20 } })) ?? [],
  })
  return (
    <div style={{ color: '#F7FAFC' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 24 }}>
        <div style={{ width: 36, height: 36, borderRadius: 10, background: '#1A202C', border: '1px solid #2D3748', display: 'flex', alignItems: 'center', justifyContent: 'center' }}><Warehouse size={16} color="#0D9488" /></div>
        <h1 style={{ fontSize: 22, fontWeight: 800, margin: 0 }}>Warehousing packages</h1>
      </div>
      <BillingCheck />
      <section style={panel} aria-label="Find a tenant">
        <h2 style={{ fontSize: 15, fontWeight: 800, margin: '0 0 10px' }}>Tenant package</h2>
        <label style={lbl} htmlFor="wh-tenant-search">Find a tenant</label>
        <input id="wh-tenant-search" style={{ ...inp, width: '100%', maxWidth: 360 }} placeholder="Type at least 2 letters of the name" value={search} onChange={e => setSearch(e.target.value)} />
        {tenants.length > 0 && (
          <ul style={{ listStyle: 'none', padding: 0, margin: '10px 0 0', display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            {tenants.map(t => <li key={t.id}><button type="button" style={btnQuiet} onClick={() => { setPicked({ id: t.id, name: t.name }); setSearch('') }}>{t.name}</button></li>)}
          </ul>
        )}
      </section>
      {picked && <TenantPackage key={picked.id} tenantId={picked.id} tenantName={picked.name} />}
    </div>
  )
}
