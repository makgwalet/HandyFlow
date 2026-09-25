// src/pages/marketing/ContactsTab.tsx
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import {
  Users, RefreshCw, Plus, X, Search, Download,
  CheckCircle, XCircle, Shield, AlertTriangle,
} from 'lucide-react'

const fmtDate = (d: any) => d ? new Date(d).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' }) : '—'
const inp: React.CSSProperties = { width: '100%', padding: '9px 12px', border: '1.5px solid var(--hf-border)', borderRadius: 8, fontSize: 14, boxSizing: 'border-box' as const, background: 'var(--hf-surface)', outline: 'none' }
const lbl: React.CSSProperties = { display: 'block', fontSize: 11, fontWeight: 700, color: 'var(--hf-text-muted)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 6 }

export default function ContactsTab() {
  const qc = useQueryClient()
  const [search, setSearch] = useState('')
  const [filterOptedIn, setFilterOptedIn] = useState<'ALL'|'OPTED_IN'|'OPTED_OUT'>('ALL')
  const [showOptIn, setShowOptIn] = useState(false)
  const [showImport, setShowImport] = useState(false)
  const [optInEmail, setOptInEmail] = useState('')
  const [optInName,  setOptInName]  = useState('')
  const [importCSV,  setImportCSV]  = useState('')
  const [toast, setToast] = useState<{ msg: string; ok: boolean } | null>(null)
  const showToast = (msg: string, ok = true) => {
    setToast({ msg, ok })
    setTimeout(() => setToast(null), 4000)
  }
  const [error,      setError]      = useState('')

  const { data: contacts, isLoading } = useQuery<any>({
    queryKey: ['marketing-contacts'],
    queryFn: async () => {
      const r = await apiClient.get('/api/v1/marketing/contacts?size=500&sort=createdAt,desc')
      return r.data?.data ?? r.data
    },
  })

  const syncCRM = useMutation({
    mutationFn: () => apiClient.post('/api/v1/marketing/contacts/sync-crm'),
    onSuccess: (r: any) => {
      qc.invalidateQueries({ queryKey: ['marketing-contacts'] })
      qc.invalidateQueries({ queryKey: ['marketing-summary'] })
      const n = r.data?.data ?? r.data
      showToast(`${typeof n === 'number' ? n : 'CRM'} contacts synced successfully.`)
    },
    onError: (e: any) => showToast(e.response?.data?.message || 'Sync failed', false),
  })

  const optIn = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/marketing/contacts/opt-in?email=${encodeURIComponent(optInEmail)}&name=${encodeURIComponent(optInName)}&source=MANUAL`),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['marketing-contacts'] })
      qc.invalidateQueries({ queryKey: ['marketing-summary'] })
      setShowOptIn(false); setOptInEmail(''); setOptInName(''); setError('')
    },
    onError: (e: any) => setError(e.response?.data?.message || 'Failed to opt in contact'),
  })

  // Parse CSV for bulk import
  const parseCSVImport = () => {
    const lines = importCSV.trim().split('\n').filter(l => l.trim())
    return lines.slice(1).map(line => {
      const [email, name, optedIn] = line.split(',').map(s => s.trim().replace(/"/g, ''))
      return { email, name: name || null, emailOptedIn: optedIn?.toLowerCase() === 'true' || optedIn === '1' }
    }).filter(c => c.email?.includes('@'))
  }

  const importContacts = useMutation({
    mutationFn: () => apiClient.post('/api/v1/marketing/contacts/import', {
      contacts: parseCSVImport(), optInSource: 'IMPORT',
    }),
    onSuccess: (r: any) => {
      qc.invalidateQueries({ queryKey: ['marketing-contacts'] })
      qc.invalidateQueries({ queryKey: ['marketing-summary'] })
      const n = r.data?.data ?? r.data
      setShowImport(false); setImportCSV(''); setError('')
      showToast(`${n} contacts imported successfully.`)
    },
    onError: (e: any) => setError(e.response?.data?.message || 'Import failed'),
  })

  const exportCSV = () => {
    const rows = allContacts
    const headers = ['Name','Email','Opted In','Source','Opted In At','Created']
    const csv = [headers, ...rows.map((c: any) => [
      c.name ?? '', c.email, c.emailOptedIn ? 'true' : 'false',
      c.optInSource ?? '', c.emailOptedInAt ? new Date(c.emailOptedInAt).toLocaleDateString('en-ZA') : '',
      new Date(c.createdAt).toLocaleDateString('en-ZA'),
    ])].map(r => r.join(',')).join('\n')
    const a = document.createElement('a')
    a.href = 'data:text/csv;charset=utf-8,' + encodeURIComponent(csv)
    a.download = 'marketing-contacts.csv'; a.click()
  }

  const allContacts: any[] = contacts?.content ?? contacts ?? []
  const filtered = allContacts.filter((c: any) => {
    if (search && !c.name?.toLowerCase().includes(search.toLowerCase()) && !c.email?.toLowerCase().includes(search.toLowerCase())) return false
    if (filterOptedIn === 'OPTED_IN'  && !c.emailOptedIn) return false
    if (filterOptedIn === 'OPTED_OUT' && c.emailOptedIn)  return false
    return true
  })
  const optedInCount  = allContacts.filter((c: any) => c.emailOptedIn).length
  const optedOutCount = allContacts.filter((c: any) => !c.emailOptedIn).length

  return (
    <div>
      {/* POPIA notice */}
      <div style={{ padding: '12px 16px', background: 'var(--hf-orange-soft)', border: '1px solid var(--hf-orange-border)', borderRadius: 10, marginBottom: 18, display: 'flex', alignItems: 'center', gap: 10 }}>
        <Shield size={16} color="#D97706" style={{ flexShrink: 0 }} />
        <div style={{ fontSize: 12, color: 'var(--hf-warning-text-deep)', lineHeight: 1.5 }}>
          <strong>POPIA compliance:</strong> Only contacts who have explicitly opted in are included in campaigns. CRM sync imports contacts but does not grant opt-in — opt-in must be collected separately per POPIA Section 69.
        </div>
      </div>

      {/* Stats row */}
      <div style={{ display: 'flex', gap: 10, marginBottom: 18 }}>
        {[
          { label: 'Total contacts', value: allContacts.length, color: 'var(--hf-primary-text)', bg: 'var(--hf-indigo-soft)' },
          { label: 'Opted in',       value: optedInCount,       color: 'var(--hf-success-text-strong)', bg: 'var(--hf-success-soft-strong)' },
          { label: 'Not opted in',   value: optedOutCount,      color: 'var(--hf-text-faint)', bg: 'var(--hf-surface-muted)' },
        ].map(s => (
          <div key={s.label} style={{ background: s.bg, borderRadius: 9, padding: '10px 16px' }}>
            <div style={{ fontSize: 20, fontWeight: 800, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 11, color: s.color, opacity: 0.8 }}>{s.label}</div>
          </div>
        ))}
      </div>

      {/* Toolbar */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16, flexWrap: 'wrap', gap: 10 }}>
        <div style={{ display: 'flex', gap: 8 }}>
          <div style={{ position: 'relative' as const }}>
            <Search size={13} style={{ position: 'absolute' as const, left: 9, top: '50%', transform: 'translateY(-50%)', color: 'var(--hf-text-faint)' }} />
            <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search contacts..."
              style={{ paddingLeft: 28, padding: '7px 10px 7px 28px', border: '1px solid var(--hf-border)', borderRadius: 8, fontSize: 13, outline: 'none', width: 200 }} />
          </div>
          <select value={filterOptedIn} onChange={e => setFilterOptedIn(e.target.value as any)}
            style={{ padding: '7px 10px', border: '1px solid var(--hf-border)', borderRadius: 8, fontSize: 13, outline: 'none', background: 'var(--hf-surface)' }}>
            <option value="ALL">All contacts</option>
            <option value="OPTED_IN">Opted in only</option>
            <option value="OPTED_OUT">Not opted in</option>
          </select>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button onClick={exportCSV} style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '7px 14px', background: 'var(--hf-surface-sunken)', color: 'var(--hf-text-secondary)', border: '1px solid var(--hf-border)', borderRadius: 8, fontSize: 13, cursor: 'pointer' }}>
            <Download size={13} /> Export CSV
          </button>
          <button onClick={() => setShowImport(true)} style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '7px 14px', background: 'var(--hf-surface-sunken)', color: 'var(--hf-text-secondary)', border: '1px solid var(--hf-border)', borderRadius: 8, fontSize: 13, cursor: 'pointer' }}>
            <Plus size={13} /> Import CSV
          </button>
          <button onClick={() => syncCRM.mutate()} disabled={syncCRM.isPending}
            style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '7px 14px', background: 'var(--hf-surface-sunken)', color: 'var(--hf-text-secondary)', border: '1px solid var(--hf-border)', borderRadius: 8, fontSize: 13, cursor: 'pointer' }}>
            <RefreshCw size={13} style={{ animation: syncCRM.isPending ? 'spin 1s linear infinite' : 'none' }} />
            {syncCRM.isPending ? 'Syncing...' : 'Sync from CRM'}
          </button>
          <button onClick={() => { setShowOptIn(true); setError('') }}
            style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '7px 16px', background: 'var(--hf-primary)', color: 'var(--hf-text-on-solid)', border: 'none', borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: 'pointer' }}>
            <Plus size={13} /> Add contact
          </button>
        </div>
      </div>

      {isLoading ? (
        <div style={{ textAlign: 'center', padding: 40, color: 'var(--hf-text-faint)' }}>Loading contacts...</div>
      ) : filtered.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '50px 20px', color: 'var(--hf-text-faint)' }}>
          <Users size={40} style={{ marginBottom: 12, opacity: 0.3 }} />
          <div style={{ fontWeight: 600, color: 'var(--hf-text-tertiary)', marginBottom: 6 }}>{allContacts.length === 0 ? 'No contacts yet' : 'No contacts match filters'}</div>
          <div style={{ fontSize: 13 }}>{allContacts.length === 0 ? 'Sync from CRM or import a CSV to build your list.' : 'Try adjusting your search or filter.'}</div>
        </div>
      ) : (
        <div style={{ border: '1px solid var(--hf-border)', borderRadius: 12, overflow: 'hidden' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse' as const, fontSize: 13 }}>
            <thead>
              <tr style={{ background: 'var(--hf-surface-muted)', borderBottom: '1px solid var(--hf-border)' }}>
                {['Name', 'Email', 'Opted in', 'Source', 'Opt-in date', 'Type', 'Added'].map(h => (
                  <th key={h} style={{ padding: '10px 16px', textAlign: 'left' as const, fontSize: 11, fontWeight: 700, color: 'var(--hf-text-muted)', letterSpacing: '0.05em' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {filtered.map((c: any, i: number) => (
                <tr key={c.id} style={{ background: i % 2 === 0 ? 'var(--hf-surface)' : 'var(--hf-surface-muted)', borderBottom: '1px solid var(--hf-border-subtle)' }}>
                  <td style={{ padding: '11px 16px', fontWeight: 600, color: 'var(--hf-text)' }}>{c.name ?? '—'}</td>
                  <td style={{ padding: '11px 16px', color: 'var(--hf-text-tertiary)' }}>{c.email}</td>
                  <td style={{ padding: '11px 16px' }}>
                    {c.emailOptedIn ? (
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: 'var(--hf-success-soft-strong)', color: 'var(--hf-success-text-strong)', padding: '2px 8px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                        <CheckCircle size={10} /> Yes
                      </span>
                    ) : (
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: 'var(--hf-surface-muted)', color: 'var(--hf-text-faint)', padding: '2px 8px', borderRadius: 20, fontSize: 11 }}>
                        <XCircle size={10} /> No
                      </span>
                    )}
                  </td>
                  <td style={{ padding: '11px 16px', color: 'var(--hf-text-faint)', fontSize: 12 }}>{c.optInSource ?? '—'}</td>
                  <td style={{ padding: '11px 16px', color: 'var(--hf-text-muted)', fontSize: 12 }}>{fmtDate(c.emailOptedInAt)}</td>
                  <td style={{ padding: '11px 16px' }}>
                    <span style={{ background: 'var(--hf-surface-sunken)', color: 'var(--hf-text-muted)', padding: '1px 7px', borderRadius: 10, fontSize: 10, fontWeight: 600 }}>{c.entityType}</span>
                  </td>
                  <td style={{ padding: '11px 16px', color: 'var(--hf-text-faint)', fontSize: 12 }}>{fmtDate(c.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Add single contact modal */}
      {showOptIn && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, backdropFilter: 'blur(2px)' }}>
          <div style={{ background: 'var(--hf-surface)', borderRadius: 16, padding: 28, width: 400, boxShadow: '0 20px 60px rgba(0,0,0,0.22)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 20 }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800 }}>Add contact</h3>
              <button onClick={() => setShowOptIn(false)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--hf-text-faint)', display: 'flex' }}><X size={18} /></button>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div><label style={lbl}>Email address *</label><input autoFocus type="email" value={optInEmail} onChange={e => setOptInEmail(e.target.value)} placeholder="name@company.co.za" style={inp} /></div>
              <div><label style={lbl}>Full name</label><input value={optInName} onChange={e => setOptInName(e.target.value)} placeholder="Thabo Modise" style={inp} /></div>
              <div style={{ padding: '9px 12px', background: 'var(--hf-orange-soft)', border: '1px solid var(--hf-orange-border)', borderRadius: 8, fontSize: 12, color: 'var(--hf-warning-text-deep)' }}>
                This will mark the contact as opted-in to marketing emails. Only add contacts who have explicitly given consent.
              </div>
            </div>
            {error && <div style={{ marginTop: 10, padding: '8px 12px', background: 'var(--hf-danger-soft)', border: '1px solid var(--hf-danger-border)', borderRadius: 8, fontSize: 13, color: 'var(--hf-danger-text)' }}>{error}</div>}
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 20 }}>
              <button onClick={() => setShowOptIn(false)} style={{ padding: '9px 18px', border: '1px solid var(--hf-border)', borderRadius: 8, background: 'var(--hf-surface)', fontSize: 14, cursor: 'pointer' }}>Cancel</button>
              <button disabled={!optInEmail || optIn.isPending} onClick={() => optIn.mutate()}
                style={{ padding: '9px 22px', background: 'var(--hf-success-solid-strong)', color: 'var(--hf-text-on-solid)', border: 'none', borderRadius: 8, fontSize: 14, fontWeight: 700, cursor: 'pointer', opacity: !optInEmail ? 0.5 : 1 }}>
                {optIn.isPending ? 'Adding...' : 'Add & Opt In'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* CSV Import modal */}
      {showImport && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, backdropFilter: 'blur(2px)' }}>
          <div style={{ background: 'var(--hf-surface)', borderRadius: 16, padding: 28, width: 540, maxHeight: '88vh', overflowY: 'auto', boxShadow: '0 20px 60px rgba(0,0,0,0.22)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 20 }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800 }}>Import contacts (CSV)</h3>
              <button onClick={() => setShowImport(false)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--hf-text-faint)', display: 'flex' }}><X size={18} /></button>
            </div>
            <div style={{ padding: '10px 14px', background: 'var(--hf-orange-soft)', border: '1px solid var(--hf-orange-border)', borderRadius: 8, fontSize: 12, color: 'var(--hf-warning-text-deep)', marginBottom: 14, display: 'flex', alignItems: 'flex-start', gap: 8 }}>
              <AlertTriangle size={14} color="#D97706" style={{ flexShrink: 0, marginTop: 1 }} />
              <div><strong>POPIA declaration:</strong> By importing these contacts you confirm that each contact has given explicit consent to receive marketing communications from your business, and that you hold records of that consent.</div>
            </div>
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>CSV format</label>
              <div style={{ background: 'var(--hf-surface-muted)', border: '1px solid var(--hf-border)', borderRadius: 8, padding: '10px 12px', fontFamily: 'monospace', fontSize: 12, color: 'var(--hf-text-secondary)', marginBottom: 8 }}>
                email,name,emailOptedIn<br />
                thabo@example.co.za,Thabo Modise,true<br />
                nomvula@example.co.za,Nomvula Radebe,false
              </div>
              <label style={lbl}>Paste CSV data *</label>
              <textarea value={importCSV} onChange={e => setImportCSV(e.target.value)} rows={8} placeholder="Paste your CSV content here..." style={{ ...inp, fontFamily: 'monospace', fontSize: 12, resize: 'vertical' as const }} />
              {importCSV && (
                <div style={{ fontSize: 12, color: 'var(--hf-text-muted)', marginTop: 6 }}>
                  {parseCSVImport().length} contacts detected · {parseCSVImport().filter(c => c.emailOptedIn).length} opted-in
                </div>
              )}
            </div>
            {error && <div style={{ marginBottom: 12, padding: '8px 12px', background: 'var(--hf-danger-soft)', border: '1px solid var(--hf-danger-border)', borderRadius: 8, fontSize: 13, color: 'var(--hf-danger-text)' }}>{error}</div>}
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
              <button onClick={() => setShowImport(false)} style={{ padding: '9px 18px', border: '1px solid var(--hf-border)', borderRadius: 8, background: 'var(--hf-surface)', fontSize: 14, cursor: 'pointer' }}>Cancel</button>
              <button disabled={!importCSV.trim() || importContacts.isPending} onClick={() => importContacts.mutate()}
                style={{ padding: '9px 22px', background: 'var(--hf-primary)', color: 'var(--hf-text-on-solid)', border: 'none', borderRadius: 8, fontSize: 14, fontWeight: 700, cursor: 'pointer', opacity: !importCSV.trim() ? 0.5 : 1 }}>
                {importContacts.isPending ? 'Importing...' : `Import ${parseCSVImport().length} contacts`}
              </button>
            </div>
          </div>
        </div>
      )}

      <style>{`@keyframes spin { to { transform: rotate(360deg) } }`}</style>

      {/* Toast notification */}
      {toast && (
        <div style={{ position: 'fixed', bottom: 24, right: 24, zIndex: 3000, display: 'flex', alignItems: 'center', gap: 10, background: toast.ok ? 'var(--hf-success-soft-strong)' : 'var(--hf-danger-soft)', border: `1px solid ${toast.ok ? '#86EFAC' : '#FECACA'}`, borderRadius: 10, padding: '12px 18px', boxShadow: '0 8px 24px rgba(0,0,0,0.12)', maxWidth: 380, animation: 'fadeIn 0.2s ease' }}>
          {toast.ok
            ? <CheckCircle size={16} color="#166534" style={{ flexShrink: 0 }} />
            : <AlertTriangle size={16} color="#DC2626" style={{ flexShrink: 0 }} />}
          <span style={{ fontSize: 13, fontWeight: 600, color: toast.ok ? 'var(--hf-success-text-strong)' : 'var(--hf-danger-text)' }}>{toast.msg}</span>
          <button onClick={() => setToast(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: toast.ok ? 'var(--hf-success-text-strong)' : 'var(--hf-danger-text)', marginLeft: 4, display: 'flex', padding: 0 }}><X size={14} /></button>
        </div>
      )}
    </div>
  )
}
