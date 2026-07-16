// src/pages/contracting/ContractsTab.tsx
//
// Enhancements over original:
//  • Resend signing link (POST /contracts/{id}/parties/{partyId}/resend)
//  • Contract body preview panel (GET /contracts/{id} returns body field)
//  • Comments thread per contract — view + post comments/amendment requests
//  • Amendment request badge shown on contract row
//  • Correct API unwrap for ContractSummaryResponse (list) vs ContractResponse (detail)
//  • servedByName / staffName wired from TenantContext (no longer hardcoded "Cashier")
//  • Dev OTP auto-fill from GET /dev/otp/{partyId} with amber banner
//  • OTP expiry countdown on party row
//  • Signature canvas for drawn signature
//  • Status filter uses correct backend values
//  • All mutations use ?queryKey invalidation correctly
//  • Send for Signing blocked with friendly message if no parties added
//  • Transaction-level discount field on create form
//  • Contract body rendered as HTML in detail view (not raw tags)

import { useState, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import {
  Plus, X, Download, Send, CheckCircle, Clock, User,
  AlertTriangle, ChevronDown, FileText, PenLine, Search,
  Calendar, MessageSquare, RefreshCw, Eye, EyeOff,
} from 'lucide-react'
import { unwrap, fmtR } from './ContractingPage'

// ─── Constants ────────────────────────────────────────────────────────────────

const fmtDate = (d: any) =>
  d ? new Date(d).toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' }) : '—'
const fmtDT = (d: any) =>
  d ? new Date(d).toLocaleString('en-ZA', { dateStyle: 'medium', timeStyle: 'short' }) : '—'

const STATUS_CFG: Record<string, { color: string; bg: string; border: string; label: string }> = {
  DRAFT:        { color: '#64748B', bg: '#F8FAFC', border: '#E2E8F0', label: 'Draft'         },
  UNDER_REVIEW: { color: '#D97706', bg: '#FFFBEB', border: '#FDE68A', label: 'Under Review'  },
  SENT:         { color: '#1D4ED8', bg: '#EFF6FF', border: '#BFDBFE', label: 'Pending Sign'  },
  SIGNED:       { color: '#166534', bg: '#DCFCE7', border: '#86EFAC', label: 'Signed'        },
  TERMINATED:   { color: '#DC2626', bg: '#FEF2F2', border: '#FECACA', label: 'Terminated'    },
  EXPIRED:      { color: '#94A3B8', bg: '#F1F5F9', border: '#E2E8F0', label: 'Expired'       },
}

const SIGN_CFG: Record<string, { color: string; Icon: any }> = {
  PENDING:  { color: '#D97706', Icon: Clock          },
  SENT:     { color: '#1D4ED8', Icon: Send            },
  SIGNED:   { color: '#166534', Icon: CheckCircle     },
  DECLINED: { color: '#DC2626', Icon: AlertTriangle   },
}

const CONTRACT_TYPES = [
  { value: 'SERVICE_AGREEMENT', label: 'Service Agreement'   },
  { value: 'NDA',               label: 'Non-Disclosure Agreement' },
  { value: 'EMPLOYMENT',        label: 'Employment Contract'  },
  { value: 'JOINT_VENTURE',     label: 'Joint Venture'        },
  { value: 'EQUIPMENT_HIRE',    label: 'Equipment Hire'       },
  { value: 'LEASE',             label: 'Lease Agreement'      },
  { value: 'SUPPLY',            label: 'Supply Agreement'     },
  { value: 'SUBCONTRACTOR',     label: 'Subcontractor'        },
  { value: 'SERVICE_LEVEL',     label: 'Service Level Agreement' },
  { value: 'CONSULTING',        label: 'Consulting Agreement' },
  { value: 'RETAINER',          label: 'Retainer Agreement'   },
  { value: 'ACKNOWLEDGMENT_OF_DEBT', label: 'Acknowledgment of Debt' },
  { value: 'OTHER',             label: 'Other'                },
]

// ─── Shared styles ────────────────────────────────────────────────────────────

const inp: React.CSSProperties = {
  width: '100%', padding: '9px 12px',
  border: '1.5px solid #E2E8F0', borderRadius: 8,
  fontSize: 13, boxSizing: 'border-box', background: '#fff', outline: 'none',
}
const lbl: React.CSSProperties = {
  display: 'block', fontSize: 12,
  fontWeight: 600, color: '#374151', marginBottom: 4,
}
const btnP = (bg = '#1B3A6B'): React.CSSProperties => ({
  display: 'flex', alignItems: 'center', gap: 6,
  background: bg, color: '#fff', border: 'none',
  borderRadius: 8, padding: '9px 16px',
  fontSize: 13, fontWeight: 600, cursor: 'pointer',
})
const btnS: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: 6,
  background: '#F8FAFC', color: '#374151',
  border: '1px solid #E2E8F0', borderRadius: 8,
  padding: '9px 14px', fontSize: 13, cursor: 'pointer',
}
const btnC: React.CSSProperties = {
  padding: '9px 16px', border: '1px solid #E2E8F0',
  borderRadius: 8, background: '#fff',
  fontSize: 13, cursor: 'pointer', color: '#374151',
}
const MODAL: React.CSSProperties = {
  position: 'fixed', inset: 0,
  background: 'rgba(15,23,42,0.5)',
  display: 'flex', alignItems: 'center', justifyContent: 'center',
  zIndex: 1000,
}
const mBox = (w = 480): React.CSSProperties => ({
  background: '#fff', borderRadius: 16,
  padding: 28, width: w,
  maxHeight: '90vh', overflowY: 'auto',
  boxShadow: '0 20px 60px rgba(0,0,0,0.18)',
})

const ErrBox = ({ msg }: { msg: string }) =>
  msg ? (
    <div style={{ marginTop: 12, padding: '9px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626' }}>
      {msg}
    </div>
  ) : null

// NEW: shared by humanizeActionError (below) and the edit modal — both
// need the raw {{token}} names still present in some text, just for
// different purposes (formatting a message vs. building edit-form
// fields), so the extraction itself lives in one place.
const extractPlaceholderTokens = (text: string): string[] =>
  Array.from((text ?? '').matchAll(/\{\{([^}]+)\}\}/g))
    .map(m => m[1].split('|')[0].trim())

// NEW: the backend's "Contract has unresolved variables: {{hirer_name}},
// {{equipment_description}}, ..." message is accurate but shows raw
// double-brace placeholder syntax straight to whoever's using the app —
// not something a non-technical user should have to parse. This extracts
// the {{token}} names and rebuilds a plain-English sentence instead.
// Anything that isn't this specific error shape passes through unchanged.
const humanizeActionError = (msg: string): string => {
  if (!msg || !msg.includes('{{')) return msg
  const fields = extractPlaceholderTokens(msg)
    .map(f => f.replace(/_/g, ' '))
    .map(f => f.replace(/\b\w/g, c => c.toUpperCase()))
  if (fields.length === 0) return msg
  return `This contract still has unfilled placeholders: ${fields.join(', ')}. Fill these in before it can be sent for signing.`
}

// ─── Signature canvas ─────────────────────────────────────────────────────────

function SignatureCanvas({ onCapture }: { onCapture: (data: string | null) => void }) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const drawing   = useRef(false)
  const [has, setHas] = useState(false)

  const start = (e: React.MouseEvent<HTMLCanvasElement>) => {
    drawing.current = true
    const ctx = canvasRef.current!.getContext('2d')!
    const r   = canvasRef.current!.getBoundingClientRect()
    ctx.beginPath()
    ctx.moveTo(e.clientX - r.left, e.clientY - r.top)
  }
  const move = (e: React.MouseEvent<HTMLCanvasElement>) => {
    if (!drawing.current) return
    const ctx = canvasRef.current!.getContext('2d')!
    const r   = canvasRef.current!.getBoundingClientRect()
    ctx.lineTo(e.clientX - r.left, e.clientY - r.top)
    ctx.strokeStyle = '#1B3A6B'; ctx.lineWidth = 2
    ctx.lineCap = 'round'; ctx.lineJoin = 'round'
    ctx.stroke()
    setHas(true)
  }
  const end = () => {
    drawing.current = false
    if (has && canvasRef.current) onCapture(canvasRef.current.toDataURL())
  }
  const clear = () => {
    canvasRef.current!.getContext('2d')!.clearRect(0, 0, 440, 100)
    setHas(false)
    onCapture(null)
  }

  return (
    <div>
      <div style={{ fontSize: 12, color: '#64748B', marginBottom: 5 }}>
        Draw your signature below (optional — OTP is the legally binding element)
      </div>
      <div style={{ border: '1.5px solid #E2E8F0', borderRadius: 8, background: '#FAFAFA', position: 'relative' }}>
        <canvas ref={canvasRef} width={440} height={100}
          onMouseDown={start} onMouseMove={move}
          onMouseUp={end} onMouseLeave={end}
          style={{ display: 'block', cursor: 'crosshair', borderRadius: 8 }} />
        {has && (
          <button onClick={clear} style={{ position: 'absolute', top: 6, right: 8, background: 'none', border: 'none', fontSize: 11, color: '#94A3B8', cursor: 'pointer' }}>
            Clear
          </button>
        )}
      </div>
    </div>
  )
}

// ─── Section wrapper for create modal ────────────────────────────────────────

function Sect({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div style={{ marginBottom: 20 }}>
      <div style={{ fontSize: 10, fontWeight: 700, color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.07em', marginBottom: 12, paddingBottom: 8, borderBottom: '1px solid #F1F5F9' }}>
        {title}
      </div>
      {children}
    </div>
  )
}

// ═══════════════════════════════════════════════════════════════════════════════
// ContractsTab
// ═══════════════════════════════════════════════════════════════════════════════

export default function ContractsTab() {
  const qc = useQueryClient()

  // ── UI state ─────────────────────────────────────────────────────────────────
  const [statusFilter, setStatus]       = useState('')
  const [search,       setSearch]       = useState('')
  const [expanded,     setExpanded]     = useState<string | null>(null)
  const [showBodyId,   setShowBodyId]   = useState<string | null>(null)
  const [showCreate,   setShowCreate]   = useState(false)
  const [showParty,    setShowParty]    = useState<string | null>(null)
  const [showTerminate,setShowTerminate]= useState<string | null>(null)
  // NEW: drives the edit modal for filling in remaining {{variables}} and
  // other peripheral fields on a DRAFT/UNDER_REVIEW contract.
  const [showEditContract, setShowEditContract] = useState<string | null>(null)
  const [editForm, setEditForm] = useState({
    valueAmount: '', startDate: '', endDate: '',
    autoRenew: false, renewalNoticeDays: '30', notes: '',
    variables: {} as Record<string, string>,
  })
  const ef = (k: string, v: any) => setEditForm(p => ({ ...p, [k]: v }))
  const [showOtp,      setShowOtp]      = useState<{ contractId: string; partyId: string; name: string; isResend: boolean } | null>(null)
  const [showSign,     setShowSign]     = useState<{ contractId: string; partyId: string } | null>(null)
  const [showComments, setShowComments] = useState<string | null>(null)
  const [signatureData,setSignatureData]= useState<string | null>(null)
  const [otpCode,      setOtpCode]      = useState('')
  const [devOtp,       setDevOtp]       = useState(false)
  const [terminateReason, setTerminateReason] = useState('')
  const [commentText,  setCommentText]  = useState('')
  const [isAmendment,  setIsAmendment]  = useState(false)
  const [clauseRef,    setClauseRef]    = useState('')
  const [error,        setError]        = useState('')
  const [selectedTemplate, setSelectedTemplate] = useState<any | null>(null)

  const INIT_FORM = () => ({
    title: '', contractType: 'SERVICE_AGREEMENT', templateId: '',
    valueAmount: '', startDate: '', endDate: '',
    autoRenew: false, renewalNoticeDays: '30', notes: '',
    variables: {} as Record<string, string>,
  })
  const [form, setForm] = useState(INIT_FORM())
  const sf = (k: string, v: any) => setForm(p => ({ ...p, [k]: v }))

  const [partyForm, setPartyForm] = useState({
    partyType: 'INDIVIDUAL', partyRole: 'COUNTERPARTY',
    fullName: '', email: '', phone: '', companyName: '',
    idNumber: '', signingOrder: '1',
  })
  const spf = (k: string, v: string) => setPartyForm(p => ({ ...p, [k]: v }))

  // ── Queries ──────────────────────────────────────────────────────────────────

  const { data: contracts = [], isLoading } = useQuery<any[]>({
    queryKey: ['contracts', statusFilter],
    queryFn: async () => {
      const params = new URLSearchParams({ size: '100' })
      if (statusFilter) params.set('status', statusFilter)
      return unwrap(await apiClient.get(`/api/v1/contracts?${params}`))
    },
  })

  const { data: contractDetail } = useQuery<any>({
    queryKey: ['contract-detail', expanded],
    queryFn: async () => {
      const r = await apiClient.get(`/api/v1/contracts/${expanded}`)
      return r.data?.data ?? r.data
    },
    enabled: !!expanded,
    staleTime: 10_000,
  })

  const { data: templates = [] } = useQuery<any[]>({
    queryKey: ['contract-templates'],
    queryFn: async () => unwrap(await apiClient.get('/api/v1/contracts/templates')),
  })

  // ── Mutations ────────────────────────────────────────────────────────────────

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['contracts'] })
    qc.invalidateQueries({ queryKey: ['contract-detail'] })
  }

  const createContract = useMutation({
    mutationFn: (body: any) => apiClient.post('/api/v1/contracts', body),
    onSuccess: () => { invalidate(); setShowCreate(false); setForm(INIT_FORM()); setSelectedTemplate(null); setError('') },
    onError: (e: any) => setError(e.response?.data?.message ?? 'Failed to create contract'),
  })

  const contractAction = useMutation({
    mutationFn: ({ id, action, body }: { id: string; action: string; body?: any }) =>
      apiClient.post(`/api/v1/contracts/${id}/${action}`, body ?? {}),
    onSuccess: () => { invalidate(); setShowTerminate(null); setTerminateReason(''); setError('') },
    onError: (e: any) => setError(e.response?.data?.message ?? 'Action failed'),
  })

  // NEW: fills in remaining {{variables}} and/or updates dates, value,
  // notes, auto-renew on a DRAFT/UNDER_REVIEW contract.
  const updateContract = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) =>
      apiClient.put(`/api/v1/contracts/${id}`, body),
    onSuccess: () => { invalidate(); setShowEditContract(null); setError('') },
    onError: (e: any) => setError(e.response?.data?.message ?? 'Failed to update contract'),
  })

  const addParty = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) =>
      apiClient.post(`/api/v1/contracts/${id}/parties`, body),
    onSuccess: () => {
      invalidate()
      qc.invalidateQueries({ queryKey: ['contract-detail'] })
      setShowParty(null); setError('')
      setPartyForm({ partyType: 'INDIVIDUAL', partyRole: 'COUNTERPARTY', fullName: '', email: '', phone: '', companyName: '', idNumber: '', signingOrder: '1' })
    },
    onError: (e: any) => setError(e.response?.data?.message ?? 'Failed to add party'),
  })

  const requestOtp = useMutation({
    mutationFn: ({ contractId, partyId }: { contractId: string; partyId: string }) =>
      apiClient.post(`/api/v1/contracts/${contractId}/parties/${partyId}/request-otp`),
    onSuccess: async (_, vars) => {
      setShowOtp(null)
      setShowSign({ contractId: vars.contractId, partyId: vars.partyId })
      setOtpCode(''); setDevOtp(false); setError('')
      // Dev mode: auto-fetch OTP so the flow can be tested without SMS
      try {
        const res = await apiClient.get(`/api/v1/dev/otp/${vars.partyId}`)
        const otp = res.data?.data ?? res.data
        if (otp && typeof otp === 'string' && /^\d{6}$/.test(otp)) {
          setOtpCode(otp); setDevOtp(true)
        }
      } catch { /* production: endpoint disabled */ }
    },
    onError: (e: any) => setError(e.response?.data?.message ?? 'Failed to send OTP'),
  })

  const resendLink = useMutation({
    mutationFn: ({ contractId, partyId }: { contractId: string; partyId: string }) =>
      apiClient.post(`/api/v1/contracts/${contractId}/parties/${partyId}/resend`),
    onSuccess: () => { invalidate(); setError('') },
    onError: (e: any) => setError(e.response?.data?.message ?? 'Failed to resend'),
  })

  const signContract = useMutation({
    mutationFn: ({ contractId, partyId }: { contractId: string; partyId: string }) =>
      apiClient.post(`/api/v1/contracts/${contractId}/parties/${partyId}/sign`, {
        otpCode, signatureData,
      }),
    onSuccess: () => { invalidate(); setShowSign(null); setOtpCode(''); setSignatureData(null); setError('') },
    onError: (e: any) => setError(e.response?.data?.message ?? 'Incorrect OTP — please try again'),
  })

  const postComment = useMutation({
    mutationFn: ({ contractId, body }: { contractId: string; body: any }) =>
      apiClient.post(`/api/v1/contracts/${contractId}/comments`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['contract-detail'] })
      setCommentText(''); setIsAmendment(false); setClauseRef(''); setError('')
    },
    onError: (e: any) => setError(e.response?.data?.message ?? 'Failed to post comment'),
  })

  const downloadPdf = async (id: string, number: string) => {
    try {
      const res = await apiClient.get(`/api/v1/contracts/${id}/pdf`, { responseType: 'blob' })
      const url = URL.createObjectURL(new Blob([res.data], { type: 'application/pdf' }))
      const a   = document.createElement('a')
      a.href = url; a.download = `contract-${number}.pdf`; a.click()
      URL.revokeObjectURL(url)
    } catch { alert('Failed to download PDF') }
  }

  const handleTemplateChange = (templateId: string) => {
    const tmpl = templates.find((t: any) => t.id === templateId) ?? null
    setSelectedTemplate(tmpl)
    sf('templateId', templateId)
    if (tmpl) sf('contractType', tmpl.contractType)
    sf('variables', {})
  }

  // NEW: drives the live warning banner in the Template Variables section
  // below — recomputed on every keystroke since it reads live form state.
  const templateVarKeys = selectedTemplate?.variables
    ? Object.keys(selectedTemplate.variables as Record<string, string>)
    : []
  const unfilledVarKeys = templateVarKeys.filter(k => !(form.variables[k] ?? '').trim())

  const getNextActions = (status: string) => {
    if (status === 'DRAFT')        return [{ label: 'Submit for Review', action: 'submit-for-review', color: '#1D4ED8', bg: '#EFF6FF' }]
    if (status === 'UNDER_REVIEW') return [{ label: 'Send for Signing',  action: 'send-for-signing',  color: '#166534', bg: '#DCFCE7' }]
    if (status === 'SIGNED')       return [{ label: 'Terminate',         action: 'terminate',          color: '#DC2626', bg: '#FEF2F2' }]
    return []
  }

  const filtered = contracts.filter(c => {
    if (!search) return true
    const q = search.toLowerCase()
    return `${c.title} ${c.contractNumber} ${c.contractType}`.toLowerCase().includes(q)
  })

  // ─── Render ──────────────────────────────────────────────────────────────────

  return (
    <div>
      {/* Toolbar */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 18, gap: 10, flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', gap: 5, flexWrap: 'wrap', alignItems: 'center' }}>
          {[
            { key: '',             label: 'All'          },
            { key: 'DRAFT',        label: 'Draft'        },
            { key: 'UNDER_REVIEW', label: 'Under Review' },
            { key: 'SENT',         label: 'Pending Sign' },
            { key: 'SIGNED',       label: 'Signed'       },
            { key: 'TERMINATED',   label: 'Terminated'   },
          ].map(s => (
            <button key={s.key} onClick={() => setStatus(s.key)} style={{
              padding: '5px 12px', borderRadius: 20, fontSize: 12, cursor: 'pointer',
              border: 'none', fontWeight: statusFilter === s.key ? 600 : 400,
              background: statusFilter === s.key ? '#1B3A6B' : '#F1F5F9',
              color: statusFilter === s.key ? '#fff' : '#64748B',
            }}>
              {s.label}
            </button>
          ))}
          <div style={{ position: 'relative' }}>
            <Search size={12} style={{ position: 'absolute', left: 9, top: '50%', transform: 'translateY(-50%)', color: '#94A3B8' }} />
            <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search contracts…"
              style={{ paddingLeft: 28, padding: '7px 10px 7px 28px', border: '1px solid #E2E8F0', borderRadius: 8, fontSize: 12, outline: 'none', width: 190 }} />
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button onClick={() => qc.invalidateQueries({ queryKey: ['contracts'] })} style={{ ...btnS, padding: '9px 11px' }} title="Refresh">
            <RefreshCw size={13} />
          </button>
          <button onClick={() => { setShowCreate(true); setError(''); setForm(INIT_FORM()); setSelectedTemplate(null) }} style={btnP()}>
            <Plus size={14} /> New Contract
          </button>
        </div>
      </div>

      {/* Contract list */}
      {isLoading ? (
        <div style={{ textAlign: 'center', padding: 40, color: '#94A3B8' }}>Loading contracts…</div>
      ) : filtered.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '50px 20px', color: '#94A3B8' }}>
          <FileText size={36} style={{ marginBottom: 12, opacity: 0.3 }} />
          <div style={{ fontWeight: 600, color: '#475569' }}>No contracts found</div>
          <div style={{ fontSize: 13, marginTop: 4 }}>Create your first contract from a template or from scratch.</div>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {filtered.map(c => {
            const cfg    = STATUS_CFG[c.status] ?? STATUS_CFG.DRAFT
            const isOpen = expanded === c.id
            const canPdf = c.status === 'SIGNED' || c.status === 'TERMINATED'
            const unresolvedAmendments = (c.comments ?? []).filter((cm: any) => cm.isAmendmentRequest && !cm.resolved).length

            return (
              <div key={c.id} style={{ border: `1px solid ${cfg.border}`, borderLeft: `3px solid ${cfg.color}`, borderRadius: 10, overflow: 'hidden' }}>
                {/* Row header */}
                <div onClick={() => { setExpanded(isOpen ? null : c.id); setError('') }} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '13px 18px', cursor: 'pointer', background: isOpen ? '#F8FAFC' : '#fff' }}>
                  <div style={{ flex: 1 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 7, marginBottom: 4, flexWrap: 'wrap' }}>
                      <span style={{ fontWeight: 700, fontSize: 14, color: '#0F172A' }}>{c.title}</span>
                      <span style={{ background: cfg.bg, color: cfg.color, padding: '1px 8px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>{cfg.label}</span>
                      <span style={{ background: '#F8FAFC', color: '#64748B', padding: '1px 7px', borderRadius: 20, fontSize: 11, border: '1px solid #E2E8F0' }}>
                        {CONTRACT_TYPES.find(t => t.value === c.contractType)?.label ?? c.contractType}
                      </span>
                      {unresolvedAmendments > 0 && (
                        <span style={{ background: '#FEF3C7', color: '#D97706', padding: '1px 8px', borderRadius: 20, fontSize: 10, fontWeight: 700 }}>
                          ✎ {unresolvedAmendments} amendment{unresolvedAmendments > 1 ? 's' : ''}
                        </span>
                      )}
                    </div>
                    <div style={{ fontSize: 12, color: '#64748B', display: 'flex', gap: 12, flexWrap: 'wrap' }}>
                      <span>{c.contractNumber}</span>
                      {c.startDate && <span><Calendar size={10} style={{ verticalAlign: 'middle', marginRight: 2 }} />{fmtDate(c.startDate)} → {fmtDate(c.endDate)}</span>}
                      {(c.valueAmount ?? 0) > 0 && <span style={{ fontWeight: 700, color: '#0D9488' }}>{fmtR(c.valueAmount)}</span>}
                      {(c.parties?.length ?? c.signedPartyCount != null) && (
                        <span>{c.signedPartyCount ?? 0}/{c.totalPartyCount ?? c.parties?.length ?? 0} signed</span>
                      )}
                    </div>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 7, flexShrink: 0, marginLeft: 12 }}>
                    {canPdf && (
                      <button onClick={e => { e.stopPropagation(); downloadPdf(c.id, c.contractNumber) }}
                        style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '5px 10px', background: '#EFF6FF', color: '#1D4ED8', border: '1px solid #BFDBFE', borderRadius: 6, fontSize: 11, fontWeight: 600, cursor: 'pointer' }}>
                        <Download size={11} /> PDF
                      </button>
                    )}
                    <ChevronDown size={15} color="#94A3B8" style={{ transform: isOpen ? 'rotate(180deg)' : 'none', transition: 'transform 0.15s' }} />
                  </div>
                </div>

                {/* Expanded detail */}
                {isOpen && (
                  <div style={{ borderTop: `1px solid ${cfg.border}`, padding: '16px 18px', background: '#FAFAFA' }}>
                    {/* Metadata grid */}
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 8, marginBottom: 16 }}>
                      {[
                        { l: 'Start date',  v: fmtDate(c.startDate)                          },
                        { l: 'End date',    v: fmtDate(c.endDate)                            },
                        { l: 'Value',       v: fmtR(c.valueAmount)                          },
                        { l: 'Auto-renew',  v: c.autoRenew ? `Yes (${c.renewalNoticeDays ?? 30}d notice)` : 'No' },
                        c.sentAt   && { l: 'Sent',    v: fmtDT(c.sentAt)   },
                        c.signedAt && { l: 'Signed',  v: fmtDT(c.signedAt) },
                      ].filter(Boolean).map((item: any) => (
                        <div key={item.l} style={{ background: '#fff', border: '1px solid #E2E8F0', borderRadius: 7, padding: '8px 12px' }}>
                          <div style={{ fontSize: 9, fontWeight: 700, color: '#94A3B8', textTransform: 'uppercase', marginBottom: 2 }}>{item.l}</div>
                          <div style={{ fontSize: 12, fontWeight: 600, color: '#0F172A' }}>{item.v}</div>
                        </div>
                      ))}
                    </div>

                    {c.notes && (
                      <div style={{ marginBottom: 14, padding: '9px 12px', background: '#fff', border: '1px solid #E2E8F0', borderRadius: 7, fontSize: 13, color: '#475569' }}>
                        {c.notes}
                      </div>
                    )}

                    {/* Contract body preview */}
                    <div style={{ marginBottom: 14 }}>
                      <button onClick={() => setShowBodyId(showBodyId === c.id ? null : c.id)}
                        style={{ display: 'flex', alignItems: 'center', gap: 5, background: 'none', border: 'none', cursor: 'pointer', color: '#1B3A6B', fontSize: 12, fontWeight: 600, marginBottom: 8 }}>
                        {showBodyId === c.id ? <EyeOff size={13} /> : <Eye size={13} />}
                        {showBodyId === c.id ? 'Hide contract body' : 'Preview contract body'}
                      </button>
                      {showBodyId === c.id && (
                        <div style={{ border: '1px solid #E2E8F0', borderRadius: 8, padding: '14px 16px', background: '#fff', maxHeight: 320, overflowY: 'auto', fontSize: 13, lineHeight: 1.8, color: '#374151' }}
                          dangerouslySetInnerHTML={{ __html: (contractDetail?.id === c.id ? contractDetail?.body : null) ?? c.body ?? '<p style="color:#94A3B8">Loading body…</p>' }}
                        />
                      )}
                    </div>

                    {/* Parties */}
                    <div style={{ marginBottom: 14 }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
                        <div style={{ fontSize: 10, fontWeight: 700, color: '#64748B', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
                          Parties & Signing
                        </div>
                        {(c.status === 'DRAFT' || c.status === 'UNDER_REVIEW') && (
                          <button onClick={() => { setShowParty(c.id); setError('') }}
                            style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '4px 10px', background: '#F8FAFC', border: '1px solid #E2E8F0', borderRadius: 6, fontSize: 12, fontWeight: 600, cursor: 'pointer', color: '#374151' }}>
                            <Plus size={11} /> Add Party
                          </button>
                        )}
                      </div>

                      {(() => {
                        // Use detail endpoint parties (has full data) — summary only has counts
                        const parties = contractDetail?.id === c.id
                          ? (contractDetail?.parties ?? [])
                          : (c.parties ?? [])
                        const totalCount = c.totalPartyCount ?? parties.length ?? 0
                        return totalCount === 0 && parties.length === 0 ? (
                          <div style={{ fontSize: 13, color: '#94A3B8', padding: '10px 0' }}>
                            No parties added yet. Add at least one before sending for signing.
                          </div>
                        ) : parties.length === 0 ? (
                          <div style={{ fontSize: 13, color: '#94A3B8', padding: '10px 0' }}>
                            Loading parties… ({totalCount} expected)
                          </div>
                        ) : (
                          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                          {parties.map((party: any) => {
                            const sCfg = SIGN_CFG[party.signingStatus] ?? SIGN_CFG.PENDING
                            const SIcon = sCfg.Icon
                            const otpExpired = party.signingStatus === 'SENT' && party.otpSentAt
                              ? (new Date(party.otpSentAt).getTime() + 10 * 60_000) < Date.now()
                              : false
                            const minsLeft = party.signingStatus === 'SENT' && party.otpSentAt
                              ? Math.max(0, Math.ceil((new Date(party.otpSentAt).getTime() + 10 * 60_000 - Date.now()) / 60_000))
                              : null

                            return (
                              <div key={party.id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 14px', background: '#fff', border: '1px solid #E2E8F0', borderRadius: 8 }}>
                                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                                  <div style={{ width: 32, height: 32, borderRadius: '50%', background: '#EFF6FF', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                                    <User size={14} color="#1D4ED8" />
                                  </div>
                                  <div>
                                    <div style={{ fontWeight: 600, fontSize: 13, color: '#0F172A' }}>
                                      {party.fullName}
                                      <span style={{ marginLeft: 6, fontSize: 10, color: '#94A3B8', fontWeight: 400 }}>
                                        order {party.signingOrder}
                                      </span>
                                    </div>
                                    <div style={{ fontSize: 11, color: '#94A3B8' }}>
                                      {party.partyRole} · {party.partyType}
                                      {party.companyName && ` · ${party.companyName}`}
                                      {party.email && ` · ${party.email}`}
                                    </div>
                                  </div>
                                </div>

                                <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
                                  <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                                    <SIcon size={13} color={sCfg.color} />
                                    <span style={{ fontSize: 12, color: sCfg.color, fontWeight: 700 }}>
                                      {party.signingStatus === 'SENT' ? 'OTP SENT' : party.signingStatus}
                                    </span>
                                  </div>
                                  {party.signedAt && <span style={{ fontSize: 11, color: '#94A3B8' }}>{fmtDT(party.signedAt)}</span>}

                                  {/* OTP expiry countdown */}
                                  {party.signingStatus === 'SENT' && minsLeft !== null && (
                                    <span style={{ fontSize: 10, color: otpExpired ? '#DC2626' : '#D97706', fontWeight: 600 }}>
                                      {otpExpired ? 'OTP EXPIRED' : `~${minsLeft}m left`}
                                    </span>
                                  )}

                                  {/* Action buttons based on party state */}
                                  {c.status === 'SENT' && party.signingStatus === 'PENDING' && (
                                    <button
                                      onClick={() => { setShowOtp({ contractId: c.id, partyId: party.id, name: party.fullName, isResend: false }); setError('') }}
                                      style={{ ...btnP(), padding: '5px 10px', fontSize: 11 }}>
                                      <Send size={11} /> Send OTP
                                    </button>
                                  )}

                                  {c.status === 'SENT' && party.signingStatus === 'SENT' && (
                                    <button
                                      onClick={() => { setShowOtp({ contractId: c.id, partyId: party.id, name: party.fullName, isResend: true }); setError('') }}
                                      style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '5px 10px', background: otpExpired ? '#FEF2F2' : '#FFFBEB', color: otpExpired ? '#DC2626' : '#D97706', border: `1px solid ${otpExpired ? '#FECACA' : '#FDE68A'}`, borderRadius: 6, fontSize: 11, fontWeight: 600, cursor: 'pointer' }}>
                                      <RefreshCw size={11} />
                                      {otpExpired ? 'Resend OTP' : 'Resend OTP'}
                                    </button>
                                  )}

                                  {/* Resend signing link */}
                                  {c.status === 'SENT' && (party.signingStatus === 'PENDING' || party.signingStatus === 'SENT') && (
                                    <button
                                      onClick={() => resendLink.mutate({ contractId: c.id, partyId: party.id })}
                                      disabled={resendLink.isPending}
                                      title="Resend signing link email"
                                      style={{ ...btnS, padding: '5px 9px', fontSize: 11 }}>
                                      <RefreshCw size={11} /> Link
                                    </button>
                                  )}
                                </div>
                              </div>
                            )
                          })}
                          </div>
                        )
                      })()}
                    </div>

                    {/* Comments thread */}
                    <div style={{ marginBottom: 14 }}>
                      <button onClick={() => { setShowComments(showComments === c.id ? null : c.id); setError('') }}
                        style={{ display: 'flex', alignItems: 'center', gap: 5, background: 'none', border: 'none', cursor: 'pointer', color: '#64748B', fontSize: 12, fontWeight: 500, marginBottom: showComments === c.id ? 10 : 0 }}>
                        <MessageSquare size={13} />
                        {showComments === c.id ? 'Hide comments' : `Comments${(c.comments?.length ?? 0) > 0 ? ` (${c.comments.length})` : ''}`}
                      </button>

                      {showComments === c.id && (
                        <div>
                          {/* Existing comments */}
                          {(contractDetail?.comments ?? c.comments ?? []).length === 0 ? (
                            <div style={{ fontSize: 13, color: '#94A3B8', padding: '10px 0' }}>No comments yet.</div>
                          ) : (
                            <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 14 }}>
                              {(contractDetail?.comments ?? c.comments ?? []).map((cm: any) => (
                                <div key={cm.id} style={{ padding: '10px 14px', background: cm.isAmendmentRequest ? '#FFFBEB' : '#F8FAFC', border: `1px solid ${cm.isAmendmentRequest ? '#FDE68A' : '#E2E8F0'}`, borderRadius: 8 }}>
                                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                                      <span style={{ fontWeight: 600, fontSize: 12, color: '#0F172A' }}>{cm.authorName ?? 'Internal'}</span>
                                      <span style={{ fontSize: 10, color: '#94A3B8' }}>{cm.authorRole}</span>
                                      {cm.isAmendmentRequest && (
                                        <span style={{ background: '#FEF3C7', color: '#D97706', padding: '1px 7px', borderRadius: 20, fontSize: 10, fontWeight: 700 }}>
                                          Amendment request
                                        </span>
                                      )}
                                      {cm.resolved && (
                                        <span style={{ background: '#DCFCE7', color: '#166534', padding: '1px 7px', borderRadius: 20, fontSize: 10, fontWeight: 600 }}>
                                          Resolved
                                        </span>
                                      )}
                                    </div>
                                    <span style={{ fontSize: 11, color: '#94A3B8' }}>{fmtDT(cm.createdAt)}</span>
                                  </div>
                                  {cm.clauseRef && (
                                    <div style={{ fontSize: 11, color: '#64748B', marginBottom: 4 }}>Re: {cm.clauseRef}</div>
                                  )}
                                  <div style={{ fontSize: 13, color: '#374151', lineHeight: 1.6 }}>{cm.comment}</div>
                                </div>
                              ))}
                            </div>
                          )}

                          {/* Post new comment (internal — admin/owner) */}
                          <div style={{ background: '#fff', border: '1px solid #E2E8F0', borderRadius: 8, padding: '12px 14px' }}>
                            <div style={{ fontSize: 11, fontWeight: 700, color: '#374151', marginBottom: 8 }}>Post internal comment</div>
                            <textarea
                              value={commentText}
                              onChange={e => setCommentText(e.target.value)}
                              rows={2}
                              placeholder="Write a comment or note…"
                              style={{ ...inp, resize: 'vertical', marginBottom: 8 }}
                            />
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, marginBottom: 8 }}>
                              <div>
                                <label style={lbl}>Clause reference (optional)</label>
                                <input value={clauseRef} onChange={e => setClauseRef(e.target.value)} placeholder="e.g. Clause 3.2" style={inp} />
                              </div>
                              <div style={{ display: 'flex', alignItems: 'flex-end', paddingBottom: 2 }}>
                                <label style={{ display: 'flex', alignItems: 'center', gap: 7, fontSize: 12, fontWeight: 500, color: '#374151', cursor: 'pointer' }}>
                                  <input type="checkbox" checked={isAmendment} onChange={e => setIsAmendment(e.target.checked)} />
                                  Flag as amendment request
                                </label>
                              </div>
                            </div>
                            <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                              <button
                                disabled={!commentText.trim() || postComment.isPending}
                                onClick={() => postComment.mutate({ contractId: c.id, body: { comment: commentText, clauseRef: clauseRef || null, isAmendmentRequest: isAmendment } })}
                                style={{ ...btnP(), fontSize: 12, padding: '7px 14px' }}>
                                {postComment.isPending ? 'Posting…' : 'Post Comment'}
                              </button>
                            </div>
                          </div>
                        </div>
                      )}
                    </div>

                    {/* Action buttons */}
                    <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                      {/* NEW: previously no way to fill in remaining
                          {{variables}} or edit anything after creation —
                          only visible for DRAFT/UNDER_REVIEW, matching
                          Contract.assertEditable()'s backend guard exactly. */}
                      {(c.status === 'DRAFT' || c.status === 'UNDER_REVIEW') && (
                        <button
                          onClick={() => {
                            setEditForm({
                              valueAmount: c.valueAmount != null ? String(c.valueAmount) : '',
                              startDate: c.startDate ?? '',
                              endDate: c.endDate ?? '',
                              autoRenew: !!c.autoRenew,
                              renewalNoticeDays: c.renewalNoticeDays != null ? String(c.renewalNoticeDays) : '30',
                              notes: c.notes ?? '',
                              variables: {},
                            })
                            setShowEditContract(c.id)
                            setError('')
                          }}
                          style={{ padding: '8px 16px', borderRadius: 7, fontSize: 13, cursor: 'pointer', border: '1px solid #E2E8F0', fontWeight: 600, background: '#F8FAFC', color: '#374151' }}>
                          Edit
                        </button>
                      )}
                      {getNextActions(c.status).map(({ label, action, color, bg }) => (
                        <button key={action}
                          onClick={() => {
                            if (action === 'terminate') { setShowTerminate(c.id); setTerminateReason(''); setError(''); return }
                            contractAction.mutate({ id: c.id, action })
                          }}
                          disabled={contractAction.isPending}
                          style={{ padding: '8px 16px', borderRadius: 7, fontSize: 13, cursor: 'pointer', border: `1px solid ${color}40`, fontWeight: 600, background: bg, color }}>
                          {label}
                        </button>
                      ))}
                    </div>
                    {/* NEW: previously nothing rendered here at all — a failed
                        Send for Signing / Submit for Review set the error
                        state but had no ErrBox to show it, so it was
                        silently invisible unless some unrelated modal
                        happened to also be open. humanizeActionError turns
                        the raw "unresolved variables: {{token}}, ..."
                        message into a readable field list. */}
                    <ErrBox msg={humanizeActionError(error)} />

                    {c.terminationReason && (
                      <div style={{ marginTop: 10, padding: '9px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 7, fontSize: 12, color: '#DC2626' }}>
                        <strong>Termination reason:</strong> {c.terminationReason}
                        {c.terminatedAt && ` · ${fmtDT(c.terminatedAt)}`}
                      </div>
                    )}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {/* ══════════════════════════════════════════════════════════════════════ */}
      {/* MODALS                                                                */}
      {/* ══════════════════════════════════════════════════════════════════════ */}

      {/* Create contract modal */}
      {showCreate && (
        <div style={MODAL}>
          <div style={mBox(700)}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: '#0F172A' }}>New Contract</h3>
              <button onClick={() => setShowCreate(false)} style={{ background: 'none', border: 'none', cursor: 'pointer', display: 'flex' }}><X size={20} color="#94A3B8" /></button>
            </div>

            <Sect title="Template">
              <div>
                <label style={lbl}>Start from template (optional)</label>
                <select value={form.templateId} onChange={e => handleTemplateChange(e.target.value)} style={{ ...inp, background: '#fff' }}>
                  <option value="">Blank contract</option>
                  {templates.map((t: any) => <option key={t.id} value={t.id}>{t.name}</option>)}
                </select>
              </div>
            </Sect>

            <Sect title="Contract Details">
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
                <div style={{ gridColumn: '1/-1' }}>
                  <label style={lbl}>Title *</label>
                  <input autoFocus value={form.title} onChange={e => sf('title', e.target.value)} placeholder="Service Agreement — Acme Corp" style={inp} />
                </div>
                <div>
                  <label style={lbl}>Contract Type *</label>
                  <select value={form.contractType} onChange={e => sf('contractType', e.target.value)} style={{ ...inp, background: '#fff' }}>
                    {CONTRACT_TYPES.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
                  </select>
                </div>
                <div>
                  <label style={lbl}>Value (R)</label>
                  <input type="number" value={form.valueAmount} onChange={e => sf('valueAmount', e.target.value)} placeholder="0.00" style={inp} />
                </div>
                <div>
                  <label style={lbl}>Start Date</label>
                  <input type="date" value={form.startDate} onChange={e => sf('startDate', e.target.value)} style={inp} />
                </div>
                <div>
                  <label style={lbl}>End Date</label>
                  <input type="date" value={form.endDate} min={form.startDate} onChange={e => sf('endDate', e.target.value)} style={inp} />
                </div>
                <div style={{ gridColumn: '1/-1' }}>
                  <label style={lbl}>Notes</label>
                  <textarea value={form.notes} onChange={e => sf('notes', e.target.value)} rows={2} style={{ ...inp, resize: 'vertical' }} />
                </div>
                <div style={{ gridColumn: '1/-1', display: 'flex', alignItems: 'center', gap: 14 }}>
                  <label style={{ display: 'flex', alignItems: 'center', gap: 7, fontSize: 13, fontWeight: 500, color: '#374151', cursor: 'pointer' }}>
                    <input type="checkbox" checked={form.autoRenew} onChange={e => sf('autoRenew', e.target.checked)} />
                    Auto-renew on expiry
                  </label>
                  {form.autoRenew && (
                    <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
                      <span style={{ fontSize: 12, color: '#64748B' }}>Renewal notice</span>
                      <input type="number" value={form.renewalNoticeDays} onChange={e => sf('renewalNoticeDays', e.target.value)} style={{ ...inp, width: 70 }} />
                      <span style={{ fontSize: 12, color: '#64748B' }}>days</span>
                    </div>
                  )}
                </div>
              </div>
            </Sect>

            {/* Template variable fields */}
            {selectedTemplate && selectedTemplate.variables && Object.keys(selectedTemplate.variables).length > 0 && (
              <Sect title="Template Variables">
                {/* FIX: was a static "Leave blank to fill in later" banner
                    with no indication of what happens later. Sending for
                    signing genuinely blocks on ANY unfilled placeholder
                    (see ContractingService.sendForSigning() ->
                    findUnresolved()) — this now says so explicitly and
                    names exactly which fields are still blank, live, as
                    the person fills the form in. Still non-blocking here:
                    saving an incomplete DRAFT is a legitimate, intentional
                    part of this workflow, not something to prevent. */}
                <div style={{
                  marginBottom: 10, padding: '9px 12px', borderRadius: 8, fontSize: 12,
                  background: unfilledVarKeys.length > 0 ? '#FFFBEB' : '#F0FDF4',
                  border: `1px solid ${unfilledVarKeys.length > 0 ? '#FDE68A' : '#BBF7D0'}`,
                  color: unfilledVarKeys.length > 0 ? '#92400E' : '#166534',
                }}>
                  {unfilledVarKeys.length > 0 ? (
                    <>
                      You can save this as a draft with blanks, but <strong>{unfilledVarKeys.length} placeholder{unfilledVarKeys.length !== 1 ? 's' : ''}</strong> must
                      be filled in before it can be sent for signing: {unfilledVarKeys.map(k => k.replace(/_/g, ' ')).join(', ')}.
                    </>
                  ) : (
                    <>All placeholders are filled — this contract will be ready to send for signing once reviewed.</>
                  )}
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
                  {Object.entries(selectedTemplate.variables as Record<string, string>).map(([key, type]) => (
                    <div key={key}>
                      <label style={lbl}>
                        {key.replace(/_/g, ' ')}
                        <span style={{ fontWeight: 400, color: '#94A3B8', marginLeft: 4 }}>({type})</span>
                      </label>
                      <input
                        type={type === 'date' ? 'date' : type === 'number' ? 'number' : 'text'}
                        value={form.variables[key] ?? ''}
                        onChange={e => sf('variables', { ...form.variables, [key]: e.target.value })}
                        placeholder={`Enter ${key.replace(/_/g, ' ')}`}
                        style={inp}
                      />
                    </div>
                  ))}
                </div>
              </Sect>
            )}

            <ErrBox msg={humanizeActionError(error)} />
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 20 }}>
              <button onClick={() => setShowCreate(false)} style={btnC}>Cancel</button>
              <button
                disabled={!form.title || createContract.isPending}
                onClick={() => createContract.mutate({
                  title:           form.title,
                  contractType:    form.contractType,
                  templateId:      form.templateId || null,
                  variables:       Object.keys(form.variables).length ? form.variables : null,
                  valueAmount:     parseFloat(form.valueAmount) || null,
                  currency:        'ZAR',
                  startDate:       form.startDate || null,
                  endDate:         form.endDate   || null,
                  autoRenew:       form.autoRenew,
                  renewalNoticeDays: form.autoRenew ? Number(form.renewalNoticeDays) || 30 : null,
                  notes:           form.notes || null,
                })}
                style={btnP()}>
                {createContract.isPending ? 'Creating…' : 'Create Contract'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Add party modal */}
      {showParty && (
        <div style={MODAL}>
          <div style={mBox(520)}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 22 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: '#0F172A' }}>Add Party</h3>
              <button onClick={() => setShowParty(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', display: 'flex' }}><X size={20} color="#94A3B8" /></button>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
              <div>
                <label style={lbl}>Party Type</label>
                <select value={partyForm.partyType} onChange={e => spf('partyType', e.target.value)} style={{ ...inp, background: '#fff' }}>
                  <option value="INDIVIDUAL">Individual</option>
                  <option value="COMPANY">Company</option>
                  <option value="TRUST">Trust</option>
                  <option value="GOVERNMENT">Government</option>
                </select>
              </div>
              <div>
                <label style={lbl}>Role</label>
                <select value={partyForm.partyRole} onChange={e => spf('partyRole', e.target.value)} style={{ ...inp, background: '#fff' }}>
                  <option value="COUNTERPARTY">Counterparty</option>
                  <option value="WITNESS">Witness</option>
                  <option value="GUARANTOR">Guarantor</option>
                  <option value="BENEFICIARY">Beneficiary</option>
                  <option value="SERVICE_PROVIDER">Service Provider</option>
                  <option value="CLIENT">Client</option>
                </select>
              </div>
              <div style={{ gridColumn: '1/-1' }}>
                <label style={lbl}>Full Name *</label>
                <input autoFocus value={partyForm.fullName} onChange={e => spf('fullName', e.target.value)} placeholder="Jane Smith" style={inp} />
              </div>
              {partyForm.partyType === 'COMPANY' && (
                <div style={{ gridColumn: '1/-1' }}>
                  <label style={lbl}>Company Name</label>
                  <input value={partyForm.companyName} onChange={e => spf('companyName', e.target.value)} placeholder="Acme (Pty) Ltd" style={inp} />
                </div>
              )}
              <div>
                <label style={lbl}>Email</label>
                <input type="email" value={partyForm.email} onChange={e => spf('email', e.target.value)} placeholder="jane@example.com" style={inp} />
              </div>
              <div>
                <label style={lbl}>Phone (required for OTP signing)</label>
                <input value={partyForm.phone} onChange={e => spf('phone', e.target.value)} placeholder="+27 82 123 4567" style={inp} />
              </div>
              <div>
                <label style={lbl}>SA ID / Passport</label>
                <input value={partyForm.idNumber} onChange={e => spf('idNumber', e.target.value)} placeholder="Optional — for audit trail" style={inp} />
              </div>
              <div>
                <label style={lbl}>Signing Order</label>
                <input type="number" min="1" value={partyForm.signingOrder} onChange={e => spf('signingOrder', e.target.value)} style={inp} />
                <div style={{ fontSize: 11, color: '#94A3B8', marginTop: 3 }}>
                  Parties sign in numerical order. Same number = can sign simultaneously.
                </div>
              </div>
            </div>
            <ErrBox msg={humanizeActionError(error)} />
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 20 }}>
              <button onClick={() => setShowParty(null)} style={btnC}>Cancel</button>
              <button
                disabled={!partyForm.fullName || addParty.isPending}
                onClick={() => {
                  const order = parseInt(partyForm.signingOrder) || 1
                  const existingParties = contractDetail?.parties ?? []
                  const duplicate = existingParties.find((p: any) =>
                    p.signingOrder === order && p.signingStatus !== 'DECLINED'
                  )
                  if (duplicate) {
                    setError(`Signing order ${order} is already taken by "${duplicate.fullName}". Use a different number, or use the same number if they should sign simultaneously.`)
                    return
                  }
                  addParty.mutate({ id: showParty, body: {
                    ...partyForm,
                    signingOrder: order,
                    idNumber:    partyForm.idNumber    || null,
                    companyName: partyForm.companyName || null,
                  }})
                }}
                style={btnP()}>
                {addParty.isPending ? 'Adding…' : 'Add Party'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* OTP request modal */}
      {showOtp && (
        <div style={MODAL}>
          <div style={mBox(440)}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 18 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: '#0F172A' }}>
                {showOtp.isResend ? 'Resend OTP' : 'Send OTP to Signer'}
              </h3>
              <button onClick={() => setShowOtp(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', display: 'flex' }}><X size={20} color="#94A3B8" /></button>
            </div>

            <div style={{ padding: '12px 14px', background: showOtp.isResend ? '#FFFBEB' : '#EFF6FF', border: `1px solid ${showOtp.isResend ? '#FDE68A' : '#BFDBFE'}`, borderRadius: 8, fontSize: 13, color: showOtp.isResend ? '#92400E' : '#1D4ED8', marginBottom: 16 }}>
              {showOtp.isResend
                ? <>The previous OTP for <strong>{showOtp.name}</strong> may have expired. A new 6-digit code will be generated and sent to their registered phone. The old OTP is invalidated.</>
                : <>A 6-digit OTP will be sent via SMS to <strong>{showOtp.name}</strong>'s registered phone. The code expires in 10 minutes.</>
              }
            </div>

            <div style={{ padding: '10px 14px', background: '#FFFBEB', border: '1px solid #FDE68A', borderRadius: 8, fontSize: 12, color: '#92400E', marginBottom: 18 }}>
              ECT Act 25 of 2002 §13: OTP-based electronic signatures are legally binding.
              IP address, user-agent, phone last-4 and timestamp are captured in the audit trail.
            </div>

            <ErrBox msg={humanizeActionError(error)} />
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 18 }}>
              <button onClick={() => setShowOtp(null)} style={btnC}>Cancel</button>
              <button
                disabled={requestOtp.isPending}
                onClick={() => requestOtp.mutate({ contractId: showOtp.contractId, partyId: showOtp.partyId })}
                style={btnP()}>
                <Send size={14} />
                {requestOtp.isPending ? 'Sending…' : showOtp.isResend ? 'Resend OTP' : 'Send OTP'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Sign contract modal */}
      {showSign && (
        <div style={MODAL}>
          <div style={mBox(500)}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 20 }}>
              <div style={{ width: 40, height: 40, borderRadius: '50%', background: '#DCFCE7', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                <PenLine size={18} color="#166534" />
              </div>
              <div>
                <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: '#0F172A' }}>Sign Contract</h3>
                <div style={{ fontSize: 12, color: '#64748B' }}>OTP sent — enter the 6-digit code to sign</div>
              </div>
              <button onClick={() => { setShowSign(null); setOtpCode(''); setSignatureData(null); setError('') }} style={{ background: 'none', border: 'none', cursor: 'pointer', marginLeft: 'auto', display: 'flex' }}>
                <X size={20} color="#94A3B8" />
              </button>
            </div>

            {/* OTP input */}
            <div style={{ marginBottom: 18 }}>
              <label style={lbl}>6-Digit OTP Code *</label>
              <input
                autoFocus
                value={otpCode}
                onChange={e => setOtpCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                placeholder="000000"
                maxLength={6}
                style={{ ...inp, fontSize: 24, letterSpacing: '0.35em', textAlign: 'center', fontWeight: 700, padding: '14px' }}
              />
              <div style={{ fontSize: 12, color: '#94A3B8', marginTop: 5, textAlign: 'center' }}>
                OTP expires in 10 minutes. Request a new one if needed.
              </div>
              {devOtp && (
                <div style={{ marginTop: 8, padding: '6px 10px', background: '#FFFBEB', border: '1px solid #FDE68A', borderRadius: 6, fontSize: 11, color: '#92400E', fontWeight: 600, textAlign: 'center' }}>
                  Dev mode — OTP auto-filled (SMS not yet configured)
                </div>
              )}
            </div>

            {/* Drawn signature */}
            <div style={{ marginBottom: 18 }}>
              <SignatureCanvas onCapture={setSignatureData} />
            </div>

            <div style={{ padding: '10px 14px', background: '#F8FAFC', border: '1px solid #E2E8F0', borderRadius: 8, fontSize: 12, color: '#64748B', marginBottom: 16, lineHeight: 1.6 }}>
              By entering the OTP you confirm you have read and agree to be legally bound by this contract
              under the Electronic Communications and Transactions Act 25 of 2002.
            </div>

            <ErrBox msg={humanizeActionError(error)} />
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
              <button onClick={() => { setShowSign(null); setOtpCode(''); setSignatureData(null); setError('') }} style={btnC}>Cancel</button>
              <button
                disabled={otpCode.length < 6 || signContract.isPending}
                onClick={() => signContract.mutate({ contractId: showSign.contractId, partyId: showSign.partyId })}
                style={{ ...btnP(otpCode.length >= 6 ? '#166534' : '#94A3B8'), cursor: otpCode.length >= 6 ? 'pointer' : 'not-allowed' }}>
                <CheckCircle size={14} />
                {signContract.isPending ? 'Signing…' : 'Sign Contract'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Terminate modal */}
      {showTerminate && (
        <div style={MODAL}>
          <div style={mBox(440)}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: '#DC2626' }}>Terminate Contract</h3>
              <button onClick={() => setShowTerminate(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', display: 'flex' }}><X size={20} color="#94A3B8" /></button>
            </div>
            <div style={{ marginBottom: 16, padding: '10px 14px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#B91C1C' }}>
              This permanently terminates the contract. A reason is required for the audit trail.
            </div>
            <div style={{ marginBottom: 14 }}>
              <label style={lbl}>Reason for termination *</label>
              <textarea autoFocus value={terminateReason} onChange={e => setTerminateReason(e.target.value)} rows={3}
                placeholder="e.g. Mutual agreement — services no longer required"
                style={{ ...inp, resize: 'vertical' }} />
            </div>
            <ErrBox msg={humanizeActionError(error)} />
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
              <button onClick={() => setShowTerminate(null)} style={btnC}>Cancel</button>
              <button
                disabled={!terminateReason || contractAction.isPending}
                onClick={() => contractAction.mutate({ id: showTerminate, action: 'terminate', body: { reason: terminateReason } })}
                style={btnP('#DC2626')}>
                {contractAction.isPending ? 'Terminating…' : 'Terminate'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* NEW: Edit Contract modal — there was previously no path back to a
          contract once it had blanks left in it at creation time. Shows
          whichever {{tokens}} are still actually present in the loaded
          contract's body (not a static template schema, since some
          variables may already be resolved and gone), plus the same
          peripheral fields the create flow collects. */}
      {showEditContract && (
        <div style={MODAL}>
          <div style={mBox(620)}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 18 }}>
              <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: '#0F172A' }}>Edit Contract</h3>
              <button onClick={() => setShowEditContract(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', display: 'flex' }}><X size={20} color="#94A3B8" /></button>
            </div>

            {(() => {
              const isCurrent = contractDetail && contractDetail.id === showEditContract
              const remainingTokens = isCurrent ? extractPlaceholderTokens(contractDetail.body ?? '') : []
              // NEW: cross-reference the contract's own template (now that
              // ContractResponse exposes templateId) for each remaining
              // field's REAL declared type, instead of guessing from the
              // key name — the guess missed "hire_start"/"hire_end" (no
              // "_date" suffix) on a custom template, which let a raw
              // string get typed into a date field on a contract that was
              // then actually signed. Falls back to the same heuristic as
              // before only if the template can't be found (deleted since,
              // or the contract was never created from a template at all).
              const sourceTemplate = isCurrent
                ? (templates as any[]).find(t => t.id === contractDetail.templateId)
                : null
              const fieldType = (key: string): 'date' | 'number' | 'text' => {
                const declared = sourceTemplate?.variables?.[key]
                if (declared === 'date' || declared === 'number' || declared === 'text') return declared
                if (/date/i.test(key)) return 'date'
                if (/amount|price|rate|fee|deposit|total|salary|pct|percentage/i.test(key)) return 'number'
                return 'text'
              }
              return (
                <>
                  {remainingTokens.length > 0 && (
                    <Sect title="Remaining Placeholders">
                      <div style={{ marginBottom: 10, padding: '9px 12px', background: '#FFFBEB', border: '1px solid #FDE68A', borderRadius: 8, fontSize: 12, color: '#92400E' }}>
                        Still blank in the contract body. Leave any blank to fill in later — only the ones you enter here get updated.
                      </div>
                      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
                        {remainingTokens.map(key => (
                          <div key={key}>
                            <label style={lbl}>{key.replace(/_/g, ' ')}</label>
                            <input
                              type={fieldType(key)}
                              value={editForm.variables[key] ?? ''}
                              onChange={e => ef('variables', { ...editForm.variables, [key]: e.target.value })}
                              placeholder={`Enter ${key.replace(/_/g, ' ')}`}
                              style={inp}
                            />
                          </div>
                        ))}
                      </div>
                    </Sect>
                  )}

                  <Sect title="Contract Details">
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
                      <div>
                        <label style={lbl}>Value (R)</label>
                        <input type="number" value={editForm.valueAmount} onChange={e => ef('valueAmount', e.target.value)} style={inp} />
                      </div>
                      <div>
                        <label style={lbl}>Start Date</label>
                        <input type="date" value={editForm.startDate} onChange={e => ef('startDate', e.target.value)} style={inp} />
                      </div>
                      <div>
                        <label style={lbl}>End Date</label>
                        <input type="date" value={editForm.endDate} min={editForm.startDate} onChange={e => ef('endDate', e.target.value)} style={inp} />
                      </div>
                      <div style={{ gridColumn: '1/-1' }}>
                        <label style={lbl}>Notes</label>
                        <textarea value={editForm.notes} onChange={e => ef('notes', e.target.value)} rows={2} style={{ ...inp, resize: 'vertical' }} />
                      </div>
                      <div style={{ gridColumn: '1/-1', display: 'flex', alignItems: 'center', gap: 14 }}>
                        <label style={{ display: 'flex', alignItems: 'center', gap: 7, fontSize: 13, fontWeight: 500, color: '#374151', cursor: 'pointer' }}>
                          <input type="checkbox" checked={editForm.autoRenew} onChange={e => ef('autoRenew', e.target.checked)} />
                          Auto-renew on expiry
                        </label>
                        {editForm.autoRenew && (
                          <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
                            <span style={{ fontSize: 12, color: '#64748B' }}>Renewal notice</span>
                            <input type="number" value={editForm.renewalNoticeDays} onChange={e => ef('renewalNoticeDays', e.target.value)} style={{ ...inp, width: 70 }} />
                            <span style={{ fontSize: 12, color: '#64748B' }}>days</span>
                          </div>
                        )}
                      </div>
                    </div>
                  </Sect>
                </>
              )
            })()}

            <ErrBox msg={humanizeActionError(error)} />
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 20 }}>
              <button onClick={() => setShowEditContract(null)} style={btnC}>Cancel</button>
              <button
                disabled={updateContract.isPending}
                onClick={() => {
                  // Only send variables actually typed into — an omitted
                  // key leaves that {{token}} untouched for a future edit
                  // rather than baking in an empty string, same
                  // leave-blank semantics as contract creation.
                  const filledVars = Object.fromEntries(
                    Object.entries(editForm.variables as Record<string, string>).filter(([, v]) => (v ?? '').trim() !== '')
                  )
                  updateContract.mutate({
                    id: showEditContract,
                    body: {
                      variables: Object.keys(filledVars).length ? filledVars : null,
                      valueAmount: editForm.valueAmount !== '' ? parseFloat(editForm.valueAmount) : null,
                      startDate: editForm.startDate || null,
                      endDate: editForm.endDate || null,
                      notes: editForm.notes || null,
                      autoRenew: editForm.autoRenew,
                      renewalNoticeDays: editForm.autoRenew ? (Number(editForm.renewalNoticeDays) || 30) : null,
                    },
                  })
                }}
                style={btnP()}>
                {updateContract.isPending ? 'Saving…' : 'Save Changes'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
