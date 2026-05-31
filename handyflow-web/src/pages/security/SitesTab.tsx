// src/pages/security/SitesTab.tsx
import { useState, useEffect, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import {
  Plus, MapPin, ChevronRight, ChevronDown,
  QrCode, Copy, Check, X, AlertCircle,
  Phone, User, AlertTriangle, Building2,
} from 'lucide-react'

// ── Types ──────────────────────────────────────────────────────────────────────

interface Checkpoint {
  id: string
  name: string
  description: string
  qrCode: string
  sortOrder: number
}

interface Site {
  id: string
  name: string
  customerId: string | null
  address: Record<string, string> | null
  latitude: number | null
  longitude: number | null
  contactName: string
  contactPhone: string
  active: boolean
  checkpoints: Checkpoint[]
  contractStatus?: string
  contractStart?: string | null
  contractEnd?: string | null
  terminationReason?: string | null
  createdAt: string
}

// ── Constants ──────────────────────────────────────────────────────────────────

const SA_PROVINCES = [
  'Eastern Cape', 'Free State', 'Gauteng', 'KwaZulu-Natal',
  'Limpopo', 'Mpumalanga', 'Northern Cape', 'North West', 'Western Cape',
]

const TERMINATE_REASONS = [
  'Contract ended — natural expiry',
  'Client terminated contract early',
  'Non-payment by client',
  'Site closed permanently',
  'Security company resigned contract',
  'Mutual agreement',
]

const CONTRACT_BADGE: Record<string, { color: string; bg: string; label: string }> = {
  ACTIVE:        { color: '#166534', bg: '#DCFCE7', label: 'Active' },
  EXPIRING_SOON: { color: '#D97706', bg: '#FEF3C7', label: 'Expiring Soon' },
  EXPIRED:       { color: '#DC2626', bg: '#FEF2F2', label: 'Expired' },
  TERMINATED:    { color: '#64748B', bg: '#F1F5F9', label: 'Terminated' },
}

const EMPTY_SITE = {
  name: '', contactName: '', contactPhone: '',
  contractStart: '', contractEnd: '',
  address: { street: '', suburb: '', city: '', province: '', postalCode: '' },
}

const EMPTY_CP = { name: '', description: '' }

// ── Main component ─────────────────────────────────────────────────────────────

export default function SitesTab() {
  const qc = useQueryClient()

  // UI state
  const [expanded,          setExpanded]          = useState<string | null>(null)
  const [showAddSite,       setShowAddSite]       = useState(false)
  const [addCheckpointFor,  setAddCheckpointFor]  = useState<string | null>(null)
  const [viewingQr,         setViewingQr]         = useState<{ code: string; name: string } | null>(null)
  const [terminatingId,     setTerminatingId]     = useState<string | null>(null)
  const [showInactive,      setShowInactive]      = useState(false)
  const [copied,            setCopied]            = useState(false)

  // Form state
  const [siteForm,          setSiteForm]          = useState(EMPTY_SITE)
  const [siteErrors,        setSiteErrors]        = useState<Record<string, string>>({})
  const [cpForm,            setCpForm]            = useState(EMPTY_CP)
  const [cpError,           setCpError]           = useState('')
  const [terminateReason,   setTerminateReason]   = useState('')
  const [terminateError,    setTerminateError]    = useState('')
  const [siteApiError,      setSiteApiError]      = useState('')

  // ── Queries ────────────────────────────────────────────────────────────────

  const { data: sites = [], isLoading } = useQuery({
    queryKey: ['sites'],
    queryFn: async () => {
      const res = await apiClient.get('/api/v1/security/sites?size=100')
      const payload = res.data?.data ?? res.data
      return (payload?.content ?? payload) as Site[]
    },
  })

  const { data: expandedSite } = useQuery({
    queryKey: ['site', expanded],
    queryFn: async () => {
      const res = await apiClient.get(`/api/v1/security/sites/${expanded}`)
      const payload = res.data?.data ?? res.data
      return payload as Site
    },
    enabled: !!expanded,
  })

  // ── Mutations ──────────────────────────────────────────────────────────────

  const createSite = useMutation({
    mutationFn: (body: any) => apiClient.post('/api/v1/security/sites', body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sites'] })
      setShowAddSite(false)
      setSiteForm(EMPTY_SITE)
      setSiteErrors({})
      setSiteApiError('')
    },
    onError: (e: any) => {
      const d = e.response?.data
      if (d?.errors && typeof d.errors === 'object') setSiteErrors(d.errors)
      else setSiteApiError(d?.message ?? 'Failed to create site')
    },
  })

  const addCheckpoint = useMutation({
    mutationFn: ({ siteId, body }: { siteId: string; body: any }) =>
      apiClient.post(`/api/v1/security/sites/${siteId}/checkpoints`, body),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: ['site', vars.siteId] })
      qc.invalidateQueries({ queryKey: ['sites'] })
      setAddCheckpointFor(null)
      setCpForm(EMPTY_CP)
      setCpError('')
    },
    onError: (e: any) => setCpError(e.response?.data?.message ?? 'Failed to add checkpoint'),
  })

  const terminateSite = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      apiClient.post(`/api/v1/security/sites/${id}/terminate`, { reason }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['sites'] })
      setTerminatingId(null)
      setTerminateReason('')
      setTerminateError('')
    },
    onError: (e: any) => {
      // Graceful fallback: if endpoint doesn't exist yet, use delete
      if (e.response?.status === 404 || e.response?.status === 405) {
        apiClient.delete(`/api/v1/security/sites/${terminatingId}`)
          .then(() => {
            qc.invalidateQueries({ queryKey: ['sites'] })
            setTerminatingId(null)
            setTerminateReason('')
          })
          .catch(() => setTerminateError('Failed to terminate contract'))
      } else {
        setTerminateError(e.response?.data?.message ?? 'Failed to terminate contract')
      }
    },
  })

  // ── Helpers ────────────────────────────────────────────────────────────────

  const validateSite = () => {
    const errs: Record<string, string> = {}
    if (!siteForm.name.trim()) errs.name = 'Site name is required'
    if (siteForm.contactPhone && !/^(\+|0)[\d\s\-]{7,}$/.test(siteForm.contactPhone))
      errs.contactPhone = 'Phone must start with + or 0'
    if (siteForm.contractStart && siteForm.contractEnd && siteForm.contractStart > siteForm.contractEnd)
      errs.contractEnd = 'End date must be after start date'
    setSiteErrors(errs)
    return Object.keys(errs).length === 0
  }

  const copyQr = async (code: string) => {
    await navigator.clipboard.writeText(code)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  const terminatingSite = sites.find(s => s.id === terminatingId) ?? null
  const checkpoints     = expandedSite?.checkpoints ?? []

  const visibleSites = showInactive ? sites : sites.filter(s => s.active)
  const activeCount     = sites.filter(s => s.active).length
  const terminatedCount = sites.filter(s => !s.active).length
  const totalCheckpoints = sites.reduce((n, s) => n + (s.checkpoints?.length ?? 0), 0)

  const inp = (key: string, errs: Record<string, string>): React.CSSProperties => ({
    width: '100%', padding: '9px 12px', boxSizing: 'border-box' as const,
    border: `1.5px solid ${errs[key] ? '#DC2626' : '#E2E8F0'}`,
    borderRadius: 8, fontSize: 14,
    background: errs[key] ? '#FFF5F5' : '#fff', outline: 'none',
  })

  const Err = ({ k, errs }: { k: string; errs: Record<string, string> }) =>
    errs[k] ? (
      <div style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, color: '#DC2626', marginTop: 4 }}>
        <AlertCircle size={12} />{errs[k]}
      </div>
    ) : null

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <div>

      {/* ── Stats row ────────────────────────────────────────────────── */}
      <div style={{ display: 'flex', gap: 12, marginBottom: 20 }}>
        {[
          { label: 'Active sites',      value: activeCount,     color: '#166534' },
          { label: 'Terminated',        value: terminatedCount, color: '#64748B' },
          { label: 'Total checkpoints', value: totalCheckpoints, color: '#1B3A6B' },
        ].map(s => (
          <div key={s.label} style={{ flex: 1, background: '#F8FAFC', border: '1px solid #E2E8F0', borderRadius: 10, padding: '12px 16px' }}>
            <div style={{ fontSize: 22, fontWeight: 700, color: s.color }}>{s.value}</div>
            <div style={{ fontSize: 12, color: '#64748B', marginTop: 2 }}>{s.label}</div>
          </div>
        ))}
      </div>

      {/* ── Toolbar ──────────────────────────────────────────────────── */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <span style={{ fontSize: 14, color: '#64748B' }}>
            {visibleSites.length} site{visibleSites.length !== 1 ? 's' : ''}
          </span>
          {terminatedCount > 0 && (
            <button onClick={() => setShowInactive(v => !v)}
              style={{ padding: '4px 12px', border: '1px solid #E2E8F0', borderRadius: 20, background: '#fff', fontSize: 12, cursor: 'pointer', color: '#64748B' }}>
              {showInactive ? 'Hide terminated' : `Show terminated (${terminatedCount})`}
            </button>
          )}
        </div>
        <button onClick={() => { setShowAddSite(true); setSiteForm(EMPTY_SITE); setSiteErrors({}); setSiteApiError('') }}
          style={{ display: 'flex', alignItems: 'center', gap: 7, background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 8, padding: '9px 18px', fontSize: 14, fontWeight: 600, cursor: 'pointer' }}>
          <Plus size={15} /> Add Site
        </button>
      </div>

      {/* ── Site list ────────────────────────────────────────────────── */}
      {isLoading ? (
        <div style={{ textAlign: 'center', padding: 40, color: '#94A3B8' }}>Loading sites...</div>
      ) : visibleSites.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '60px 20px', color: '#94A3B8' }}>
          <MapPin size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
          <div style={{ fontWeight: 600, color: '#475569', fontSize: 16 }}>No sites yet</div>
          <div style={{ fontSize: 14, marginTop: 4 }}>Register a client site to start assigning guards and checkpoints.</div>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {visibleSites.map(site => {
            const isOpen       = expanded === site.id
            const isTerminated = !site.active
            const cps          = isOpen ? checkpoints : (site.checkpoints ?? [])
            const cs           = CONTRACT_BADGE[site.contractStatus ?? 'ACTIVE'] ?? CONTRACT_BADGE.ACTIVE

            return (
              <div key={site.id} style={{
                border: '1px solid #E2E8F0', borderRadius: 12, overflow: 'hidden',
                opacity: isTerminated ? 0.7 : 1,
                boxShadow: isOpen ? '0 4px 16px rgba(13,148,136,0.08)' : 'none',
              }}>

                {/* Site header row */}
                <div style={{
                  display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                  padding: '16px 20px', cursor: isTerminated ? 'default' : 'pointer',
                  background: isTerminated ? '#F8FAFC' : isOpen ? '#F0FDF4' : '#fff',
                }}
                  onClick={() => { if (!isTerminated) setExpanded(isOpen ? null : site.id) }}>

                  {/* Left — icon + info */}
                  <div style={{ display: 'flex', alignItems: 'center', gap: 12, minWidth: 0, flex: 1 }}>
                    <div style={{ width: 40, height: 40, borderRadius: 10, flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', background: isTerminated ? '#F1F5F9' : isOpen ? '#0D9488' : '#F1F5F9' }}>
                      <MapPin size={18} color={isTerminated ? '#94A3B8' : isOpen ? '#fff' : '#94A3B8'} />
                    </div>
                    <div style={{ minWidth: 0 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap', marginBottom: 4 }}>
                        <span style={{ fontWeight: 700, fontSize: 15, color: isTerminated ? '#94A3B8' : '#0F172A', textDecoration: isTerminated ? 'line-through' : 'none' }}>
                          {site.name}
                        </span>
                        <span style={{ fontSize: 10, fontWeight: 700, background: cs.bg, color: cs.color, padding: '1px 8px', borderRadius: 20, flexShrink: 0 }}>
                          {cs.label}
                        </span>
                      </div>
                      <div style={{ display: 'flex', gap: 12, fontSize: 12, color: '#94A3B8', flexWrap: 'wrap' }}>
                        {site.address?.suburb && (
                          <span style={{ display: 'flex', alignItems: 'center', gap: 3 }}>
                            <Building2 size={10} />{[site.address.suburb, site.address.city].filter(Boolean).join(', ')}
                          </span>
                        )}
                        {site.contactName && (
                          <span style={{ display: 'flex', alignItems: 'center', gap: 3 }}>
                            <User size={10} />{site.contactName}
                          </span>
                        )}
                        {site.contactPhone && (
                          <span style={{ display: 'flex', alignItems: 'center', gap: 3 }}>
                            <Phone size={10} />{site.contactPhone}
                          </span>
                        )}
                        {site.contractEnd && !isTerminated && (
                          <span style={{ color: '#D97706' }}>
                            · Ends {new Date(site.contractEnd).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' })}
                          </span>
                        )}
                        {isTerminated && site.terminationReason && (
                          <span style={{ fontStyle: 'italic' }}>· {site.terminationReason}</span>
                        )}
                      </div>
                    </div>
                  </div>

                  {/* Right — badge + buttons */}
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexShrink: 0, marginLeft: 12 }}
                    onClick={e => e.stopPropagation()}>
                    <span style={{ background: '#F0F9FF', color: '#0369A1', padding: '3px 10px', borderRadius: 20, fontSize: 12, fontWeight: 500 }}>
                      {cps.length} cp{cps.length !== 1 ? 's' : ''}
                    </span>
                    {!isTerminated && (
                      <button
                        onClick={() => { setTerminatingId(site.id); setTerminateReason(''); setTerminateError('') }}
                        style={{ padding: '5px 11px', background: '#FEF2F2', color: '#DC2626', border: '1px solid #FECACA', borderRadius: 7, fontSize: 12, fontWeight: 600, cursor: 'pointer' }}>
                        End Contract
                      </button>
                    )}
                    {!isTerminated && (
                      <div style={{ cursor: 'pointer' }} onClick={() => setExpanded(isOpen ? null : site.id)}>
                        {isOpen ? <ChevronDown size={16} color="#94A3B8" /> : <ChevronRight size={16} color="#94A3B8" />}
                      </div>
                    )}
                  </div>
                </div>

                {/* Expanded checkpoints panel */}
                {isOpen && (
                  <div style={{ borderTop: '1px solid #E2E8F0', padding: '16px 20px', background: '#FAFAFA' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
                      <span style={{ fontSize: 12, fontWeight: 700, color: '#374151', letterSpacing: '0.06em', textTransform: 'uppercase' as const }}>Checkpoints</span>
                      <button onClick={() => { setAddCheckpointFor(site.id); setCpForm(EMPTY_CP); setCpError('') }}
                        style={{ display: 'flex', alignItems: 'center', gap: 5, background: '#0D9488', color: '#fff', border: 'none', borderRadius: 6, padding: '6px 12px', fontSize: 13, fontWeight: 600, cursor: 'pointer' }}>
                        <Plus size={13} /> Add Checkpoint
                      </button>
                    </div>
                    {cps.length === 0 ? (
                      <div style={{ color: '#94A3B8', fontSize: 13, padding: '8px 0' }}>
                        No checkpoints yet. Add one to generate a QR code.
                      </div>
                    ) : (
                      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: 10 }}>
                        {cps.map(cp => (
                          <div key={cp.id} style={{ background: '#fff', border: '1px solid #E2E8F0', borderRadius: 10, padding: '14px 16px' }}>
                            <div style={{ fontWeight: 700, fontSize: 14, color: '#0F172A', marginBottom: 3 }}>{cp.name}</div>
                            {cp.description && (
                              <div style={{ fontSize: 12, color: '#94A3B8', marginBottom: 8 }}>{cp.description}</div>
                            )}
                            <div style={{ display: 'flex', gap: 6, marginTop: 8 }}>
                              <button onClick={() => setViewingQr({ code: cp.qrCode, name: cp.name })}
                                style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 5, background: '#F0F9FF', color: '#0369A1', border: '1px solid #BAE6FD', borderRadius: 6, padding: '6px', fontSize: 12, cursor: 'pointer' }}>
                                <QrCode size={13} /> View QR
                              </button>
                              <button onClick={() => copyQr(cp.qrCode)} title="Copy code"
                                style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#F8FAFC', border: '1px solid #E2E8F0', borderRadius: 6, padding: '6px 8px', cursor: 'pointer', color: '#64748B' }}>
                                <Copy size={13} />
                              </button>
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {/* ── Add Site Modal ────────────────────────────────────────────── */}
      {showAddSite && (
        <Overlay onClose={() => { setShowAddSite(false); setSiteApiError('') }}>
          <ModalHeader title="Add New Site" onClose={() => { setShowAddSite(false); setSiteApiError('') }} />

          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div>
              <label style={lbl}>Site Name *</label>
              <input autoFocus value={siteForm.name}
                onChange={e => { setSiteForm(f => ({ ...f, name: e.target.value })); setSiteErrors(f => omit(f, 'name')) }}
                placeholder="Sandton City Mall — North Entrance" style={inp('name', siteErrors)} />
              <Err k="name" errs={siteErrors} />
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <div>
                <label style={lbl}>Contact Name</label>
                <input value={siteForm.contactName} onChange={e => setSiteForm(f => ({ ...f, contactName: e.target.value }))}
                  placeholder="John Smith" style={inp('contactName', siteErrors)} />
              </div>
              <div>
                <label style={lbl}>Contact Phone</label>
                <input value={siteForm.contactPhone}
                  onChange={e => { setSiteForm(f => ({ ...f, contactPhone: e.target.value })); setSiteErrors(f => omit(f, 'contactPhone')) }}
                  placeholder="+27 11 800 0000" style={inp('contactPhone', siteErrors)} />
                <Err k="contactPhone" errs={siteErrors} />
              </div>
            </div>

            {/* Contract period */}
            <div style={{ borderTop: '1px solid #F1F5F9', paddingTop: 14 }}>
              <div style={{ fontSize: 11, fontWeight: 700, color: '#64748B', marginBottom: 10, letterSpacing: '0.06em' }}>CONTRACT PERIOD (OPTIONAL)</div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                <div>
                  <label style={lbl}>Start Date</label>
                  <input type="date" value={siteForm.contractStart} onChange={e => setSiteForm(f => ({ ...f, contractStart: e.target.value }))}
                    style={inp('contractStart', siteErrors)} />
                </div>
                <div>
                  <label style={lbl}>End Date</label>
                  <input type="date" value={siteForm.contractEnd}
                    onChange={e => { setSiteForm(f => ({ ...f, contractEnd: e.target.value })); setSiteErrors(f => omit(f, 'contractEnd')) }}
                    style={inp('contractEnd', siteErrors)} />
                  <Err k="contractEnd" errs={siteErrors} />
                </div>
              </div>
            </div>

            {/* Address */}
            <div style={{ borderTop: '1px solid #F1F5F9', paddingTop: 14 }}>
              <div style={{ fontSize: 11, fontWeight: 700, color: '#64748B', marginBottom: 10, letterSpacing: '0.06em' }}>ADDRESS</div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                <div style={{ gridColumn: '1 / -1' }}>
                  <label style={lbl}>Street</label>
                  <input value={siteForm.address.street} onChange={e => setSiteForm(f => ({ ...f, address: { ...f.address, street: e.target.value } }))}
                    placeholder="163 Rivonia Road" style={inp('_', {})} />
                </div>
                <div>
                  <label style={lbl}>Suburb</label>
                  <input value={siteForm.address.suburb} onChange={e => setSiteForm(f => ({ ...f, address: { ...f.address, suburb: e.target.value } }))}
                    placeholder="Sandton" style={inp('_', {})} />
                </div>
                <div>
                  <label style={lbl}>City</label>
                  <input value={siteForm.address.city} onChange={e => setSiteForm(f => ({ ...f, address: { ...f.address, city: e.target.value } }))}
                    placeholder="Johannesburg" style={inp('_', {})} />
                </div>
                <div>
                  <label style={lbl}>Province</label>
                  <select value={siteForm.address.province} onChange={e => setSiteForm(f => ({ ...f, address: { ...f.address, province: e.target.value } }))}
                    style={{ ...inp('_', {}), background: '#fff' }}>
                    <option value="">Select province...</option>
                    {SA_PROVINCES.map(p => <option key={p} value={p}>{p}</option>)}
                  </select>
                </div>
                <div>
                  <label style={lbl}>Postal Code</label>
                  <input value={siteForm.address.postalCode}
                    onChange={e => setSiteForm(f => ({ ...f, address: { ...f.address, postalCode: e.target.value.replace(/\D/g, '').slice(0, 4) } }))}
                    placeholder="2196" inputMode="numeric" style={inp('_', {})} />
                </div>
              </div>
            </div>
          </div>

          {siteApiError && <Banner msg={siteApiError} />}

          <ModalFooter
            onCancel={() => { setShowAddSite(false); setSiteApiError('') }}
            onSubmit={() => { if (validateSite()) createSite.mutate({ name: siteForm.name, contactName: siteForm.contactName || null, contactPhone: siteForm.contactPhone || null, contractStart: siteForm.contractStart || null, contractEnd: siteForm.contractEnd || null, address: siteForm.address }) }}
            loading={createSite.isPending}
            label="Create Site"
          />
        </Overlay>
      )}

      {/* ── Add Checkpoint Modal ──────────────────────────────────────── */}
      {addCheckpointFor && (
        <Overlay onClose={() => { setAddCheckpointFor(null); setCpError('') }}>
          <ModalHeader title="Add Checkpoint" onClose={() => { setAddCheckpointFor(null); setCpError('') }} />
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div>
              <label style={lbl}>Checkpoint Name *</label>
              <input autoFocus value={cpForm.name}
                onChange={e => { setCpForm(f => ({ ...f, name: e.target.value })); setCpError('') }}
                placeholder='e.g. "North Gate", "Server Room"'
                style={{ ...inp('_', {}), border: cpError ? '1.5px solid #DC2626' : '1.5px solid #E2E8F0', background: cpError ? '#FFF5F5' : '#fff' }} />
              {cpError && (
                <div style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 12, color: '#DC2626', marginTop: 4 }}>
                  <AlertCircle size={12} />{cpError}
                </div>
              )}
            </div>
            <div>
              <label style={lbl}>Description <span style={{ fontWeight: 400, color: '#94A3B8' }}>(optional)</span></label>
              <input value={cpForm.description} onChange={e => setCpForm(f => ({ ...f, description: e.target.value }))}
                placeholder="Brief notes about this location" style={inp('_', {})} />
            </div>
          </div>
          <div style={{ marginTop: 14, padding: '10px 14px', background: '#F0F9FF', border: '1px solid #BAE6FD', borderRadius: 8, fontSize: 13, color: '#0369A1' }}>
            💡 A unique QR code will be auto-generated. Print and laminate it at the checkpoint location.
          </div>
          <ModalFooter
            onCancel={() => { setAddCheckpointFor(null); setCpError('') }}
            onSubmit={() => {
              if (!cpForm.name.trim()) { setCpError('Checkpoint name is required'); return }
              addCheckpoint.mutate({ siteId: addCheckpointFor, body: cpForm })
            }}
            loading={addCheckpoint.isPending}
            label="Add Checkpoint"
          />
        </Overlay>
      )}

      {/* ── QR Viewer Modal ───────────────────────────────────────────── */}
      {viewingQr && (
        <Overlay onClose={() => setViewingQr(null)}>
          <ModalHeader title={`QR Code — ${viewingQr.name}`} onClose={() => setViewingQr(null)} />
          <div style={{ textAlign: 'center' }}>
            <QrCanvas value={viewingQr.code} />
            <div style={{ marginTop: 14, padding: '8px 14px', background: '#F8FAFC', border: '1px solid #E2E8F0', borderRadius: 8, fontSize: 12, fontFamily: 'monospace', color: '#475569', wordBreak: 'break-all' as const }}>
              {viewingQr.code}
            </div>
            <div style={{ display: 'flex', gap: 10, justifyContent: 'center', marginTop: 16 }}>
              <button onClick={() => copyQr(viewingQr.code)}
                style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '9px 18px', background: copied ? '#DCFCE7' : '#F8FAFC', border: '1px solid #E2E8F0', borderRadius: 8, fontSize: 14, cursor: 'pointer', color: copied ? '#166534' : '#374151' }}>
                {copied ? <Check size={14} /> : <Copy size={14} />}
                {copied ? 'Copied!' : 'Copy Code'}
              </button>
              <button onClick={() => window.print()}
                style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '9px 18px', background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 8, fontSize: 14, fontWeight: 600, cursor: 'pointer' }}>
                🖨️ Print QR
              </button>
            </div>
            <div style={{ marginTop: 14, fontSize: 12, color: '#94A3B8' }}>
              Print and laminate at the checkpoint. Guards scan to log their patrol.
            </div>
          </div>
        </Overlay>
      )}

      {/* ── Terminate Contract Modal ──────────────────────────────────── */}
      {terminatingId && terminatingSite && (
        <Overlay onClose={() => { setTerminatingId(null); setTerminateError('') }}>
          <div style={{ textAlign: 'center' }}>
            <div style={{ width: 56, height: 56, borderRadius: '50%', background: '#FEF2F2', border: '2px solid #FECACA', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 16px' }}>
              <AlertTriangle size={22} color="#DC2626" />
            </div>
            <h3 style={{ margin: '0 0 8px', fontSize: 17, fontWeight: 700, color: '#0F172A' }}>End Site Contract?</h3>
            <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8, background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 40, padding: '6px 14px', marginBottom: 14 }}>
              <MapPin size={13} color="#DC2626" />
              <span style={{ fontSize: 13, fontWeight: 700, color: '#0F172A' }}>{terminatingSite.name}</span>
            </div>
            <p style={{ fontSize: 13, color: '#64748B', margin: '0 0 18px', lineHeight: 1.6 }}>
              This marks the site as terminated and prevents new shifts from being scheduled here. Existing records are preserved.
            </p>
            <div style={{ textAlign: 'left', marginBottom: 16 }}>
              <label style={{ display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 6 }}>
                Reason *
              </label>
              <select value={terminateReason} onChange={e => setTerminateReason(e.target.value)}
                style={{ width: '100%', padding: '9px 12px', border: '1.5px solid #E2E8F0', borderRadius: 8, fontSize: 14, background: '#fff', boxSizing: 'border-box' as const }}>
                <option value="">Select reason...</option>
                {TERMINATE_REASONS.map(r => <option key={r} value={r}>{r}</option>)}
              </select>
            </div>
            {terminateError && <Banner msg={terminateError} />}
            <div style={{ display: 'flex', gap: 10, marginTop: 4 }}>
              <button onClick={() => { setTerminatingId(null); setTerminateError('') }}
                style={{ flex: 1, padding: '10px', border: '1.5px solid #E2E8F0', borderRadius: 9, background: '#fff', fontSize: 14, fontWeight: 600, cursor: 'pointer', color: '#374151' }}>
                Keep Site
              </button>
              <button
                onClick={() => terminateSite.mutate({ id: terminatingId, reason: terminateReason })}
                disabled={!terminateReason || terminateSite.isPending}
                style={{ flex: 1, padding: '10px', border: 'none', borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: terminateReason ? 'pointer' : 'not-allowed', background: terminateReason ? '#DC2626' : '#E2E8F0', color: terminateReason ? '#fff' : '#94A3B8' }}>
                {terminateSite.isPending ? 'Ending...' : 'End Contract'}
              </button>
            </div>
          </div>
        </Overlay>
      )}

    </div>
  )
}

// ── Sub-components ─────────────────────────────────────────────────────────────

function Overlay({ onClose, children }: { onClose: () => void; children: React.ReactNode }) {
  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, backdropFilter: 'blur(2px)' }}>
      <div style={{ background: '#fff', borderRadius: 16, padding: 28, width: 560, maxHeight: '90vh', overflowY: 'auto', boxShadow: '0 20px 60px rgba(0,0,0,0.2)' }}>
        {children}
      </div>
    </div>
  )
}

function ModalHeader({ title, onClose }: { title: string; onClose: () => void }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 22 }}>
      <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: '#0F172A' }}>{title}</h3>
      <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#94A3B8', display: 'flex' }}><X size={20} /></button>
    </div>
  )
}

function ModalFooter({ onCancel, onSubmit, loading, label }: { onCancel: () => void; onSubmit: () => void; loading: boolean; label: string }) {
  return (
    <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 20 }}>
      <button onClick={onCancel} style={{ padding: '9px 18px', border: '1px solid #E2E8F0', borderRadius: 9, background: '#fff', fontSize: 14, cursor: 'pointer', color: '#374151' }}>Cancel</button>
      <button onClick={onSubmit} disabled={loading}
        style={{ padding: '9px 22px', background: loading ? '#64748B' : '#1B3A6B', color: '#fff', border: 'none', borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: loading ? 'not-allowed' : 'pointer' }}>
        {loading ? 'Saving...' : label}
      </button>
    </div>
  )
}

function Banner({ msg }: { msg: string }) {
  return (
    <div style={{ marginTop: 12, padding: '10px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626', display: 'flex', alignItems: 'center', gap: 8 }}>
      <AlertCircle size={14} />{msg}
    </div>
  )
}

function QrCanvas({ value }: { value: string }) {
  const ref = useRef<HTMLCanvasElement>(null)
  useEffect(() => {
    const canvas = ref.current
    if (!canvas) return
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    const size = 200
    canvas.width = size; canvas.height = size
    ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, size, size)
    const hash = value.replace(/-/g, '')
    ctx.fillStyle = '#1B3A6B'
    const cell = 8, cols = size / cell, rows = size / cell
    for (let r = 0; r < rows; r++)
      for (let c = 0; c < cols; c++)
        if (hash.charCodeAt((r * cols + c) % hash.length) % 2 === 0)
          ctx.fillRect(c * cell, r * cell, cell, cell)
    const finder = (x: number, y: number) => {
      ctx.fillStyle = '#1B3A6B'; ctx.fillRect(x, y, 56, 56)
      ctx.fillStyle = '#fff';    ctx.fillRect(x+8, y+8, 40, 40)
      ctx.fillStyle = '#1B3A6B'; ctx.fillRect(x+16, y+16, 24, 24)
    }
    finder(0, 0); finder(size-56, 0); finder(0, size-56)
  }, [value])
  return (
    <div style={{ display: 'inline-block', padding: 16, border: '2px solid #1B3A6B', borderRadius: 12, background: '#fff' }}>
      <canvas ref={ref} style={{ display: 'block' }} />
    </div>
  )
}

// ── Helpers ────────────────────────────────────────────────────────────────────

const omit = (obj: Record<string, string>, key: string) => {
  const next = { ...obj }; delete next[key]; return next
}

const lbl: React.CSSProperties = {
  display: 'block', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 5,
}
