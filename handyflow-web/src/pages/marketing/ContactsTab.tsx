// src/pages/marketing/ContactsTab.tsx
import { useEffect, useMemo, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import {
  Users, RefreshCw, Plus, X, Search, Download, CheckCircle, XCircle,
  Shield, AlertTriangle, ShieldAlert, History, ChevronLeft, ChevronRight,
} from 'lucide-react'

const PAGE_SIZE = 25
const MAX_IMPORT_ROWS = 5000
const EMAIL_RE = /^[^@\s]+@[^@\s]+\.[^@\s]+$/

const fmtDate = (d: any) => d ? new Date(d).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' }) : '—'
const fmtDT   = (d: any) => d ? new Date(d).toLocaleString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—'

const inp: React.CSSProperties = { width: '100%', padding: '9px 12px', border: '1.5px solid #E2E8F0', borderRadius: 8, fontSize: 14, boxSizing: 'border-box', background: '#fff', outline: 'none' }
const lbl: React.CSSProperties = { display: 'block', fontSize: 11, fontWeight: 700, color: '#6B7280', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 6 }

const SOURCES = [
  { v: 'MANUAL',    l: 'Manual entry' },
  { v: 'WEB_FORM',  l: 'Web form' },
  { v: 'EMAIL',     l: 'Email confirmation' },
  { v: 'PHONE',     l: 'Phone (verbal consent)' },
  { v: 'IN_PERSON', l: 'In person / signed form' },
]
const SOURCE_FILTERS = ['MANUAL', 'WEB_FORM', 'EMAIL', 'PHONE', 'IN_PERSON', 'IMPORT', 'CRM_SYNC']

const EVENT_LABEL: Record<string, { label: string; color: string; bg: string }> = {
  OPT_IN:             { label: 'Opted in',                 color: '#166534', bg: '#DCFCE7' },
  OPT_OUT:            { label: 'Opted out',                color: '#9333EA', bg: '#FAF5FF' },
  IMPORT_DECLARATION: { label: 'Import consent declared',  color: '#0369A1', bg: '#E0F2FE' },
  SUPPRESSION_ADDED:  { label: 'Suppression added',        color: '#B91C1C', bg: '#FEF2F2' },
  SUPPRESSION_LIFTED: { label: 'Suppression lifted',       color: '#475569', bg: '#F1F5F9' },
}

type StatusFilter = 'ALL' | 'OPTED_IN' | 'NOT_OPTED_IN' | 'SUPPRESSED'

function parseCsvLine(line: string): string[] {
  const out: string[] = []
  let cur = ''
  let inQuotes = false
  for (let i = 0; i < line.length; i++) {
    const ch = line[i]
    if (inQuotes) {
      if (ch === '"') { if (line[i + 1] === '"') { cur += '"'; i++ } else inQuotes = false }
      else cur += ch
    } else if (ch === '"') inQuotes = true
    else if (ch === ',') { out.push(cur.trim()); cur = '' }
    else cur += ch
  }
  out.push(cur.trim())
  return out
}

export default function ContactsTab() {
  const qc = useQueryClient()
  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch]           = useState('')
  const [status, setStatus]           = useState<StatusFilter>('ALL')
  const [source, setSource]           = useState('')
  const [page, setPage]               = useState(0)

  const [showOptIn, setShowOptIn]   = useState(false)
  const [optIn, setOptIn]           = useState({ email: '', name: '', source: 'MANUAL', evidence: '' })
  const [optInError, setOptInError] = useState('')

  const [showImport, setShowImport]         = useState(false)
  const [importCSV, setImportCSV]           = useState('')
  const [importDeclared, setImportDeclared] = useState(false)
  const [importError, setImportError]       = useState('')

  const [historyContact, setHistoryContact] = useState<any>(null)

  const [toast, setToast] = useState<{ msg: string; ok: boolean } | null>(null)
  const showToast = (msg: string, ok = true) => { setToast({ msg, ok }); setTimeout(() => setToast(null), 6000) }

  // Debounce search; any filter change returns to page 1.
  useEffect(() => {
    const t = setTimeout(() => { setSearch(searchInput.trim()); setPage(0) }, 400)
    return () => clearTimeout(t)
  }, [searchInput])

  // ── Data (all server-side) ─────────────────────────────────────────────────
  const { data: pageData, isLoading, isFetching } = useQuery<any>({
    queryKey: ['marketing-contacts', page, search, status, source],
    queryFn: async () => {
      const r = await apiClient.get('/api/v1/marketing/contacts', {
        params: { page, size: PAGE_SIZE, search: search || undefined, status, source: source || undefined },
      })
      return r.data?.data ?? r.data
    },
    placeholderData: (prev: any) => prev,
  })

  const { data: summary } = useQuery<any>({
    queryKey: ['marketing-summary'],
    queryFn: async () => { const r = await apiClient.get('/api/v1/marketing/summary'); return r.data?.data ?? r.data },
  })

  const { data: history = [], isLoading: historyLoading } = useQuery<any[]>({
    queryKey: ['marketing-consent-history', historyContact?.id],
    enabled: !!historyContact,
    queryFn: async () => {
      const r = await apiClient.get(`/api/v1/marketing/contacts/${historyContact.id}/consent-history`)
      return r.data?.data ?? r.data ?? []
    },
  })

  const rows: any[]    = pageData?.content ?? []
  const totalElements  = pageData?.totalElements ?? 0
  const totalPages     = pageData?.totalPages ?? 1
  const exceptions     = summary?.consentExceptions ?? 0

  const refreshAll = () => {
    qc.invalidateQueries({ queryKey: ['marketing-contacts'] })
    qc.invalidateQueries({ queryKey: ['marketing-summary'] })
    qc.invalidateQueries({ queryKey: ['marketing-segments'] })
  }

  // ── Mutations ──────────────────────────────────────────────────────────────
  const syncCRM = useMutation({
    mutationFn: () => apiClient.post('/api/v1/marketing/contacts/sync-crm'),
    onSuccess: (r: any) => {
      refreshAll()
      const n = r.data?.data ?? r.data
      showToast(`${typeof n === 'number' ? n : 'CRM'} contacts synced. They are NOT opted in — collect consent before emailing them.`)
    },
    onError: (e: any) => showToast(e.response?.data?.message || 'Sync failed', false),
  })

  const optInMutation = useMutation({
    mutationFn: () => apiClient.post('/api/v1/marketing/contacts/opt-in', null, {
      params: { email: optIn.email.trim(), name: optIn.name.trim() || undefined, source: optIn.source, evidence: optIn.evidence.trim() },
    }),
    onSuccess: () => {
      refreshAll()
      setShowOptIn(false); setOptIn({ email: '', name: '', source: 'MANUAL', evidence: '' }); setOptInError('')
      showToast('Contact opted in and consent evidence recorded.')
    },
    onError: (e: any) => setOptInError(e.response?.data?.message || 'Failed to opt in contact'),
  })

  const parsed = useMemo(() => {
    const lines = importCSV.split(/\r?\n/).filter(l => l.trim())
    const body = lines.length > 0 && !lines[0].includes('@') ? lines.slice(1) : lines
    const valid: { email: string; name: string | null; emailOptedIn: boolean }[] = []
    let invalid = 0
    body.forEach(l => {
      const [email, name, opt] = parseCsvLine(l)
      if (!email || !EMAIL_RE.test(email)) { invalid++; return }
      valid.push({ email, name: name || null, emailOptedIn: ['true', '1', 'yes'].includes((opt || '').toLowerCase()) })
    })
    return { valid, invalid }
  }, [importCSV])

  const importOptedInCount = parsed.valid.filter(c => c.emailOptedIn).length
  const tooMany            = parsed.valid.length > MAX_IMPORT_ROWS
  const needsDeclaration   = importOptedInCount > 0
  const canImport          = parsed.valid.length > 0 && !tooMany && (!needsDeclaration || importDeclared)

  const importContacts = useMutation({
    mutationFn: () => apiClient.post('/api/v1/marketing/contacts/import', { contacts: parsed.valid, optInSource: 'IMPORT' }),
    onSuccess: (r: any) => {
      refreshAll()
      setShowImport(false); setImportCSV(''); setImportDeclared(false); setImportError('')
      showToast(r.data?.message ?? 'Contacts imported.')
    },
    onError: (e: any) => setImportError(e.response?.data?.message || 'Import failed'),
  })

  const exportCSV = useMutation({
    mutationFn: async () => {
      const r = await apiClient.get('/api/v1/marketing/contacts/export', {
        params: { search: search || undefined, status, source: source || undefined }, responseType: 'blob',
      })
      const url = URL.createObjectURL(r.data as Blob)
      const a = document.createElement('a')
      a.href = url; a.download = 'marketing-contacts.csv'; a.click()
      URL.revokeObjectURL(url)
    },
    onError: (e: any) => showToast(e.response?.status === 403 ? 'Exporting contacts needs the Marketing admin permission.' : 'Export failed', false),
  })

  const optInValid = EMAIL_RE.test(optIn.email.trim()) && optIn.evidence.trim().length >= 5
  const setFilter = <T,>(fn: (v: T) => void) => (v: T) => { fn(v); setPage(0) }

  return (
    <div>
      {/* POPIA notice */}
      <div style={{ padding: '12px 16px', background: '#FFF7ED', border: '1px solid #FED7AA', borderRadius: 10, marginBottom: 18, display: 'flex', alignItems: 'center', gap: 10 }}>
        <Shield size={16} color="#D97706" style={{ flexShrink: 0 }} />
        <div style={{ fontSize: 12, color: '#92400E', lineHeight: 1.5 }}>
          <strong>POPIA compliance:</strong> Only contacts who have explicitly opted in <em>and</em> are not on the suppression list are included in campaigns.
          CRM sync imports contacts but does not grant opt-in — consent must be collected separately (POPIA Section 69).
        </div>
      </div>

      {exceptions > 0 && (
        <div style={{ padding: '12px 16px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 10, marginBottom: 18, display: 'flex', alignItems: 'flex-start', gap: 10 }}>
          <AlertTriangle size={16} color="#DC2626" style={{ flexShrink: 0, marginTop: 1 }} />
          <div style={{ fontSize: 12, color: '#991B1B', lineHeight: 1.5, flex: 1 }}>
            <strong>{exceptions} consent exception{exceptions === 1 ? '' : 's'}:</strong> {exceptions === 1 ? 'a contact is' : 'contacts are'} marked opted in but {exceptions === 1 ? 'is' : 'are'} on the suppression list.
            They are excluded from every send.
          </div>
          <button onClick={() => { setStatus('SUPPRESSED'); setPage(0) }}
            style={{ padding: '4px 10px', background: '#fff', border: '1px solid #FECACA', borderRadius: 7, fontSize: 11, fontWeight: 700, color: '#B91C1C', cursor: 'pointer' }}>Review</button>
        </div>
      )}

      {/* Stats */}
      <div style={{ display: 'flex', gap: 10, marginBottom: 18, flexWrap: 'wrap' }}>
        {[
          { label: 'Total contacts',      value: summary?.totalContacts ?? 0,       color: '#1B3A6B', bg: '#EEF2FF' },
          { label: 'Opted in',            value: summary?.optedInCount ?? 0,        color: '#166534', bg: '#DCFCE7' },
          { label: 'Not opted in',        value: summary?.optedOutCount ?? 0,       color: '#64748B', bg: '#F8FAFC' },
          { label: 'Active suppressions', value: summary?.activeSuppressions ?? 0,  color: (summary?.activeSuppressions ?? 0) > 0 ? '#B91C1C' : '#64748B', bg: (summary?.activeSuppressions ?? 0) > 0 ? '#FEF2F2' : '#F8FAFC' },
        ].map(s => (
          <div key={s.label} style={{ background: s.bg, borderRadius: 9, padding: '10px 16px', minWidth: 120 }}>
            <div style={{ fontSize: 20, fontWeight: 800, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 11, color: s.color, opacity: 0.8 }}>{s.label}</div>
          </div>
        ))}
      </div>

      {/* Toolbar */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16, flexWrap: 'wrap', gap: 10 }}>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <div style={{ position: 'relative' }}>
            <Search size={13} style={{ position: 'absolute', left: 9, top: '50%', transform: 'translateY(-50%)', color: '#94A3B8' }} />
            <input value={searchInput} onChange={e => setSearchInput(e.target.value)} placeholder="Search name or email…"
              style={{ padding: '7px 10px 7px 28px', border: '1px solid #E2E8F0', borderRadius: 8, fontSize: 13, outline: 'none', width: 220 }} />
          </div>
          <select value={status} onChange={e => setFilter<StatusFilter>(setStatus)(e.target.value as StatusFilter)}
            style={{ padding: '7px 10px', border: '1px solid #E2E8F0', borderRadius: 8, fontSize: 13, outline: 'none', background: '#fff' }}>
            <option value="ALL">All contacts</option>
            <option value="OPTED_IN">Eligible (opted in)</option>
            <option value="NOT_OPTED_IN">Not opted in</option>
            <option value="SUPPRESSED">Suppressed</option>
          </select>
          <select value={source} onChange={e => setFilter<string>(setSource)(e.target.value)}
            style={{ padding: '7px 10px', border: '1px solid #E2E8F0', borderRadius: 8, fontSize: 13, outline: 'none', background: '#fff' }}>
            <option value="">Any source</option>
            {SOURCE_FILTERS.map(s => <option key={s} value={s}>{s.replace(/_/g, ' ')}</option>)}
          </select>
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <button onClick={() => exportCSV.mutate()} disabled={exportCSV.isPending}
            title="Exports every contact matching the current filters (max 50,000)"
            style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '7px 14px', background: '#F1F5F9', color: '#374151', border: '1px solid #E2E8F0', borderRadius: 8, fontSize: 13, cursor: 'pointer' }}>
            <Download size={13} /> {exportCSV.isPending ? 'Exporting…' : 'Export CSV'}
          </button>
          <button onClick={() => { setShowImport(true); setImportError('') }} style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '7px 14px', background: '#F1F5F9', color: '#374151', border: '1px solid #E2E8F0', borderRadius: 8, fontSize: 13, cursor: 'pointer' }}>
            <Plus size={13} /> Import CSV
          </button>
          <button onClick={() => syncCRM.mutate()} disabled={syncCRM.isPending}
            style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '7px 14px', background: '#F1F5F9', color: '#374151', border: '1px solid #E2E8F0', borderRadius: 8, fontSize: 13, cursor: 'pointer' }}>
            <RefreshCw size={13} style={{ animation: syncCRM.isPending ? 'spin 1s linear infinite' : 'none' }} />
            {syncCRM.isPending ? 'Syncing...' : 'Sync from CRM'}
          </button>
          <button onClick={() => { setShowOptIn(true); setOptInError('') }}
            style={{ display: 'flex', alignItems: 'center', gap: 5, padding: '7px 16px', background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: 'pointer' }}>
            <Plus size={13} /> Add contact
          </button>
        </div>
      </div>

      {/* Table */}
      {isLoading ? (
        <div style={{ textAlign: 'center', padding: 40, color: '#94A3B8' }}>Loading contacts...</div>
      ) : rows.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '50px 20px', color: '#94A3B8' }}>
          <Users size={40} style={{ marginBottom: 12, opacity: 0.3 }} />
          <div style={{ fontWeight: 600, color: '#475569', marginBottom: 6 }}>{(summary?.totalContacts ?? 0) === 0 ? 'No contacts yet' : 'No contacts match your filters'}</div>
          <div style={{ fontSize: 13 }}>{(summary?.totalContacts ?? 0) === 0 ? 'Sync from CRM or import a CSV to build your list.' : 'Try adjusting your search or filters.'}</div>
        </div>
      ) : (
        <div style={{ border: '1px solid #E2E8F0', borderRadius: 12, overflow: 'auto', opacity: isFetching ? 0.75 : 1, transition: 'opacity 0.15s' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
            <thead>
              <tr style={{ background: '#F8FAFC', borderBottom: '1px solid #E2E8F0' }}>
                {['Name', 'Email', 'Consent status', 'Source', 'Opt-in date', 'Type', 'Added', ''].map(h => (
                  <th key={h} style={{ padding: '10px 16px', textAlign: 'left', fontSize: 11, fontWeight: 700, color: '#64748B', letterSpacing: '0.05em', whiteSpace: 'nowrap' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((c: any, i: number) => (
                <tr key={c.id} style={{ background: i % 2 === 0 ? '#fff' : '#FAFAFA', borderBottom: '1px solid #F1F5F9' }}>
                  <td style={{ padding: '11px 16px', fontWeight: 600, color: '#0F172A' }}>{c.name ?? '—'}</td>
                  <td style={{ padding: '11px 16px', color: '#475569' }}>{c.email}</td>
                  <td style={{ padding: '11px 16px' }}>
                    {c.suppressed ? (
                      <span title="On the suppression list — see the Suppressions tab"
                        style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: '#FEF2F2', color: '#B91C1C', padding: '2px 8px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                        <ShieldAlert size={10} /> Suppressed
                      </span>
                    ) : c.emailOptedIn ? (
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: '#DCFCE7', color: '#166534', padding: '2px 8px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                        <CheckCircle size={10} /> Opted in
                      </span>
                    ) : (
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, background: '#F8FAFC', color: '#94A3B8', padding: '2px 8px', borderRadius: 20, fontSize: 11 }}>
                        <XCircle size={10} /> Not opted in
                      </span>
                    )}
                  </td>
                  <td style={{ padding: '11px 16px', color: '#94A3B8', fontSize: 12 }}>{c.optInSource ?? '—'}</td>
                  <td style={{ padding: '11px 16px', color: '#64748B', fontSize: 12 }}>{fmtDate(c.emailOptedInAt)}</td>
                  <td style={{ padding: '11px 16px' }}>
                    <span style={{ background: '#F1F5F9', color: '#64748B', padding: '1px 7px', borderRadius: 10, fontSize: 10, fontWeight: 600 }}>{c.entityType}</span>
                  </td>
                  <td style={{ padding: '11px 16px', color: '#94A3B8', fontSize: 12 }}>{fmtDate(c.createdAt)}</td>
                  <td style={{ padding: '11px 16px' }}>
                    <button onClick={() => setHistoryContact(c)}
                      style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '4px 10px', background: '#fff', color: '#475569', border: '1px solid #E2E8F0', borderRadius: 7, fontSize: 11, fontWeight: 600, cursor: 'pointer' }}>
                      <History size={11} /> History
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Pagination */}
      {totalElements > 0 && (
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 14 }}>
          <span style={{ fontSize: 12, color: '#94A3B8' }}>
            {page * PAGE_SIZE + 1}–{Math.min((page + 1) * PAGE_SIZE, totalElements)} of {totalElements.toLocaleString()}
          </span>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <span style={{ fontSize: 12, color: '#94A3B8' }}>Page {page + 1} of {totalPages}</span>
            <button disabled={page === 0} onClick={() => setPage(p => Math.max(0, p - 1))}
              style={{ display: 'flex', padding: 6, border: '1px solid #E2E8F0', borderRadius: 7, background: '#fff', cursor: page === 0 ? 'default' : 'pointer', opacity: page === 0 ? 0.4 : 1 }}><ChevronLeft size={14} /></button>
            <button disabled={page + 1 >= totalPages} onClick={() => setPage(p => p + 1)}
              style={{ display: 'flex', padding: 6, border: '1px solid #E2E8F0', borderRadius: 7, background: '#fff', cursor: page + 1 >= totalPages ? 'default' : 'pointer', opacity: page + 1 >= totalPages ? 0.4 : 1 }}><ChevronRight size={14} /></button>
          </div>
        </div>
      )}

      {/* Consent history drawer */}
      {historyContact && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', display: 'flex', justifyContent: 'flex-end', zIndex: 1000 }} onClick={() => setHistoryContact(null)}>
          <div style={{ background: '#fff', width: 460, height: '100%', overflowY: 'auto', boxShadow: '-10px 0 40px rgba(0,0,0,0.15)', padding: 28 }} onClick={e => e.stopPropagation()}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 18 }}>
              <div>
                <div style={{ fontWeight: 800, fontSize: 16, color: '#0F172A' }}>Consent history</div>
                <div style={{ fontSize: 13, color: '#475569', marginTop: 3 }}>{historyContact.name ? `${historyContact.name} · ` : ''}{historyContact.email}</div>
              </div>
              <button onClick={() => setHistoryContact(null)} style={{ background: '#F1F5F9', border: 'none', borderRadius: '50%', width: 30, height: 30, display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', color: '#64748B' }}><X size={14} /></button>
            </div>
            <div style={{ fontSize: 12, color: '#64748B', background: '#F8FAFC', border: '1px solid #E2E8F0', borderRadius: 8, padding: '8px 12px', marginBottom: 18, lineHeight: 1.5 }}>
              An append-only record of every opt-in, opt-out and suppression change — your proof of permission.
            </div>
            {historyLoading ? (
              <div style={{ color: '#94A3B8', fontSize: 13 }}>Loading…</div>
            ) : history.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '30px 10px', color: '#94A3B8', fontSize: 13 }}>
                No consent events recorded for this contact.{!historyContact.emailOptedIn && ' They have not opted in.'}
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                {history.map((ev: any) => {
                  const meta = EVENT_LABEL[ev.eventType] ?? { label: ev.eventType, color: '#475569', bg: '#F1F5F9' }
                  return (
                    <div key={ev.id} style={{ border: '1px solid #E2E8F0', borderRadius: 10, padding: '12px 14px' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                        <span style={{ background: meta.bg, color: meta.color, padding: '2px 9px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{meta.label}</span>
                        <span style={{ fontSize: 11, color: '#94A3B8' }}>{fmtDT(ev.createdAt)}</span>
                      </div>
                      <div style={{ fontSize: 12, color: '#64748B' }}>Source: <strong style={{ color: '#374151' }}>{ev.source ?? '—'}</strong> · {ev.channel} · {ev.purpose}</div>
                      {ev.evidenceText && <div style={{ fontSize: 12, color: '#374151', marginTop: 6, lineHeight: 1.5, background: '#F8FAFC', padding: '6px 10px', borderRadius: 6 }}>{ev.evidenceText}</div>}
                      {ev.recordedBy && <div style={{ fontSize: 10, color: '#CBD5E1', marginTop: 6 }}>Recorded by user {String(ev.recordedBy).slice(0, 8)}…</div>}
                    </div>
                  )
                })}
              </div>
            )}
          </div>
        </div>
      )}

      {/* Add contact modal */}
      {showOptIn && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, backdropFilter: 'blur(2px)' }}>
          <div style={{ background: '#fff', borderRadius: 16, padding: 28, width: 460, maxHeight: '90vh', overflowY: 'auto', boxShadow: '0 20px 60px rgba(0,0,0,0.22)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 20 }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800 }}>Add contact</h3>
              <button onClick={() => setShowOptIn(false)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', display: 'flex' }}><X size={18} /></button>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div><label style={lbl}>Email address *</label>
                <input autoFocus type="email" value={optIn.email} onChange={e => setOptIn(p => ({ ...p, email: e.target.value }))} placeholder="name@company.co.za" style={inp} /></div>
              <div><label style={lbl}>Full name</label>
                <input value={optIn.name} onChange={e => setOptIn(p => ({ ...p, name: e.target.value }))} placeholder="Thabo Modise" style={inp} /></div>
              <div><label style={lbl}>How was consent given? *</label>
                <select value={optIn.source} onChange={e => setOptIn(p => ({ ...p, source: e.target.value }))} style={inp}>
                  {SOURCES.map(s => <option key={s.v} value={s.v}>{s.l}</option>)}
                </select></div>
              <div><label style={lbl}>Consent evidence * (min. 5 characters)</label>
                <textarea value={optIn.evidence} onChange={e => setOptIn(p => ({ ...p, evidence: e.target.value }))} rows={3}
                  placeholder="e.g. Ticked the newsletter box on the website contact form, 12 June 2026, 14:32" style={{ ...inp, resize: 'vertical' }} /></div>
              <div style={{ padding: '9px 12px', background: '#FFF7ED', border: '1px solid #FED7AA', borderRadius: 8, fontSize: 12, color: '#92400E' }}>
                This marks the contact as opted in to marketing email and stores your evidence. Only add people who have explicitly consented.
                Addresses on the suppression list cannot be opted in.
              </div>
            </div>
            {optInError && <div style={{ marginTop: 10, padding: '8px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626' }}>{optInError}</div>}
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 20 }}>
              <button onClick={() => setShowOptIn(false)} style={{ padding: '9px 18px', border: '1px solid #E2E8F0', borderRadius: 8, background: '#fff', fontSize: 14, cursor: 'pointer' }}>Cancel</button>
              <button disabled={!optInValid || optInMutation.isPending} onClick={() => optInMutation.mutate()}
                style={{ padding: '9px 22px', background: '#166534', color: '#fff', border: 'none', borderRadius: 8, fontSize: 14, fontWeight: 700, cursor: 'pointer', opacity: !optInValid ? 0.5 : 1 }}>
                {optInMutation.isPending ? 'Adding...' : 'Add & Opt In'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* CSV import modal */}
      {showImport && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.55)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, backdropFilter: 'blur(2px)' }}>
          <div style={{ background: '#fff', borderRadius: 16, padding: 28, width: 560, maxHeight: '90vh', overflowY: 'auto', boxShadow: '0 20px 60px rgba(0,0,0,0.22)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 20 }}>
              <h3 style={{ margin: 0, fontSize: 16, fontWeight: 800 }}>Import contacts (CSV)</h3>
              <button onClick={() => setShowImport(false)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', display: 'flex' }}><X size={18} /></button>
            </div>
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>CSV format</label>
              <div style={{ background: '#F8FAFC', border: '1px solid #E2E8F0', borderRadius: 8, padding: '10px 12px', fontFamily: 'monospace', fontSize: 12, color: '#374151', marginBottom: 8 }}>
                email,name,emailOptedIn<br />
                thabo@example.co.za,Thabo Modise,true<br />
                nomvula@example.co.za,Nomvula Radebe,false
              </div>
              <div style={{ fontSize: 11, color: '#94A3B8', marginBottom: 8 }}>
                Max {MAX_IMPORT_ROWS.toLocaleString()} rows. Suppressed and invalid addresses are skipped automatically. Only rows marked <code>true</code> are opted in.
              </div>
              <label style={lbl}>Paste CSV data *</label>
              <textarea value={importCSV} onChange={e => setImportCSV(e.target.value)} rows={8} placeholder="Paste your CSV content here..."
                style={{ ...inp, fontFamily: 'monospace', fontSize: 12, resize: 'vertical' }} />
              {importCSV.trim() && (
                <div style={{ fontSize: 12, color: '#64748B', marginTop: 6 }}>
                  {parsed.valid.length} valid · {importOptedInCount} to opt in
                  {parsed.invalid > 0 && <span style={{ color: '#D97706' }}> · {parsed.invalid} invalid row{parsed.invalid === 1 ? '' : 's'} will be skipped</span>}
                  {tooMany && <span style={{ color: '#DC2626' }}> · over the {MAX_IMPORT_ROWS.toLocaleString()} row limit</span>}
                </div>
              )}
            </div>

            {needsDeclaration && (
              <label style={{ display: 'flex', alignItems: 'flex-start', gap: 10, padding: '10px 14px', background: '#FFF7ED', border: '1px solid #FED7AA', borderRadius: 8, fontSize: 12, color: '#92400E', marginBottom: 14, cursor: 'pointer', lineHeight: 1.5 }}>
                <input type="checkbox" checked={importDeclared} onChange={e => setImportDeclared(e.target.checked)} style={{ marginTop: 2 }} />
                <span><strong>POPIA declaration:</strong> I confirm that each of the {importOptedInCount} contact{importOptedInCount === 1 ? '' : 's'} marked as opted in has given
                  explicit consent to receive marketing from this business, and that I hold records of that consent. This declaration is stored against each contact.
                  I am not importing a purchased list.</span>
              </label>
            )}

            {importError && <div style={{ marginBottom: 12, padding: '8px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626' }}>{importError}</div>}
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
              <button onClick={() => setShowImport(false)} style={{ padding: '9px 18px', border: '1px solid #E2E8F0', borderRadius: 8, background: '#fff', fontSize: 14, cursor: 'pointer' }}>Cancel</button>
              <button disabled={!canImport || importContacts.isPending} onClick={() => importContacts.mutate()}
                style={{ padding: '9px 22px', background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 8, fontSize: 14, fontWeight: 700, cursor: 'pointer', opacity: !canImport ? 0.5 : 1 }}>
                {importContacts.isPending ? 'Importing...' : `Import ${parsed.valid.length} contacts`}
              </button>
            </div>
          </div>
        </div>
      )}

      <style>{`@keyframes spin { to { transform: rotate(360deg) } }`}</style>

      {toast && (
        <div style={{ position: 'fixed', bottom: 24, right: 24, zIndex: 3000, display: 'flex', alignItems: 'center', gap: 10, background: toast.ok ? '#DCFCE7' : '#FEF2F2', border: `1px solid ${toast.ok ? '#86EFAC' : '#FECACA'}`, borderRadius: 10, padding: '12px 18px', boxShadow: '0 8px 24px rgba(0,0,0,0.12)', maxWidth: 420 }}>
          {toast.ok ? <CheckCircle size={16} color="#166534" style={{ flexShrink: 0 }} /> : <AlertTriangle size={16} color="#DC2626" style={{ flexShrink: 0 }} />}
          <span style={{ fontSize: 13, fontWeight: 600, color: toast.ok ? '#166534' : '#DC2626' }}>{toast.msg}</span>
          <button onClick={() => setToast(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', color: toast.ok ? '#166534' : '#DC2626', marginLeft: 4, display: 'flex', padding: 0 }}><X size={14} /></button>
        </div>
      )}
    </div>
  )
}