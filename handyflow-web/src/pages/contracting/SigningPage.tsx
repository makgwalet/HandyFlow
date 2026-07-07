// src/pages/signing/SigningPage.tsx
//
// Public-facing page opened when an external party clicks their signing link.
// URL format: /sign/{token}
// No HandyFlow account required — all calls go to /api/v1/sign/{token}/...
//
// Steps:
//   1. Load contract from token  (GET  /api/v1/sign/{token}/contract)
//   2. Request OTP               (POST /api/v1/sign/{token}/otp)
//   3. Submit OTP + signature    (POST /api/v1/sign/{token}/submit)
//   4. Show signed confirmation screen
//
// Also supports:
//   • Decline to sign            (POST /api/v1/sign/{token}/decline)
//   • Comment / amendment request (POST /api/v1/sign/{token}/comment)
//   • View comments thread       (GET  /api/v1/sign/{token}/comments)

import { useState, useRef, useEffect } from 'react'
import { useParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import {
  CheckCircle, AlertTriangle, Clock, User,
  MessageSquare, ChevronDown, ChevronUp,
  FileText, Shield, Send, X,
} from 'lucide-react'

// ─── Types ────────────────────────────────────────────────────────────────────

interface PublicContractView {
  contractId: string
  contractNumber: string
  title: string
  contractType: string
  status: string
  bodyHtml: string
  startDate: string | null
  endDate: string | null
  valueAmount: number | null
  currency: string
  notes: string | null
  myDetails: {
    partyId: string
    fullName: string
    partyRole: string
    partyType: string
    companyName: string | null
    email: string | null
    phoneMasked: string | null
    signingOrder: number
    signingStatus: string
  }
  otherParties: {
    fullName: string
    partyRole: string
    companyName: string | null
    signingOrder: number
    signingStatus: string
    signedAt: string | null
  }[]
  alreadySigned: boolean
  signedAt: string | null
  tokenExpiresAt: string
  bodyHash: string | null
  comments: Comment[]
}

interface Comment {
  id: string
  authorName: string
  authorRole: string
  isAmendmentRequest: boolean
  comment: string
  clauseRef: string | null
  resolved: boolean
  createdAt: string
}

interface SigningResult {
  contractId: string
  contractNumber: string
  title: string
  fullyExecuted: boolean
  partyName: string
  signedAt: string
  message: string
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

const fmtDate = (d: string | null) =>
  d ? new Date(d).toLocaleDateString('en-ZA', { day: 'numeric', month: 'long', year: 'numeric' }) : '—'

const fmtDT = (d: string) =>
  new Date(d).toLocaleString('en-ZA', { dateStyle: 'medium', timeStyle: 'short' })

const fmtR = (n: number | null, currency = 'ZAR') =>
  n ? `${currency} ${n.toLocaleString('en-ZA', { minimumFractionDigits: 2 })}` : null

const TYPE_LABEL: Record<string, string> = {
  SERVICE_AGREEMENT: 'Service Agreement', NDA: 'Non-Disclosure Agreement',
  EMPLOYMENT: 'Employment Contract', JOINT_VENTURE: 'Joint Venture',
  EQUIPMENT_HIRE: 'Equipment Hire', LEASE: 'Lease Agreement',
  SUPPLY: 'Supply Agreement', SUBCONTRACTOR: 'Subcontractor Agreement',
  SERVICE_LEVEL: 'Service Level Agreement', CONSULTING: 'Consulting Agreement',
  RETAINER: 'Retainer Agreement', OTHER: 'Contract',
}

// ─── Signature canvas ─────────────────────────────────────────────────────────

function SignatureCanvas({ onCapture }: { onCapture: (data: string | null) => void }) {
  const ref     = useRef<HTMLCanvasElement>(null)
  const drawing = useRef(false)
  const [has, setHas] = useState(false)

  const start = (e: React.MouseEvent | React.TouchEvent) => {
    drawing.current = true
    const ctx  = ref.current!.getContext('2d')!
    const rect = ref.current!.getBoundingClientRect()
    const x    = 'touches' in e ? e.touches[0].clientX - rect.left : (e as React.MouseEvent).clientX - rect.left
    const y    = 'touches' in e ? e.touches[0].clientY - rect.top  : (e as React.MouseEvent).clientY - rect.top
    ctx.beginPath(); ctx.moveTo(x, y)
  }
  const move = (e: React.MouseEvent | React.TouchEvent) => {
    if (!drawing.current) return
    e.preventDefault()
    const ctx  = ref.current!.getContext('2d')!
    const rect = ref.current!.getBoundingClientRect()
    const x    = 'touches' in e ? e.touches[0].clientX - rect.left : (e as React.MouseEvent).clientX - rect.left
    const y    = 'touches' in e ? e.touches[0].clientY - rect.top  : (e as React.MouseEvent).clientY - rect.top
    ctx.lineTo(x, y)
    ctx.strokeStyle = '#1B3A6B'; ctx.lineWidth = 2.5
    ctx.lineCap = 'round'; ctx.lineJoin = 'round'
    ctx.stroke(); setHas(true)
  }
  const end = () => {
    drawing.current = false
    if (has && ref.current) onCapture(ref.current.toDataURL())
  }
  const clear = () => {
    ref.current!.getContext('2d')!.clearRect(0, 0, 500, 120)
    setHas(false); onCapture(null)
  }

  return (
    <div>
      <div style={{ fontSize: 12, color: '#64748B', marginBottom: 5 }}>
        Draw your signature below (optional — the OTP is the legally binding element)
      </div>
      <div style={{ border: '1.5px solid #CBD5E1', borderRadius: 10, background: '#F8FAFC', position: 'relative', touchAction: 'none' }}>
        <canvas
          ref={ref} width={500} height={120}
          onMouseDown={start} onMouseMove={move} onMouseUp={end} onMouseLeave={end}
          onTouchStart={start} onTouchMove={move} onTouchEnd={end}
          style={{ display: 'block', cursor: 'crosshair', borderRadius: 10, width: '100%' }}
        />
        {has && (
          <button onClick={clear} style={{ position: 'absolute', top: 8, right: 10, background: 'none', border: 'none', fontSize: 11, color: '#94A3B8', cursor: 'pointer' }}>
            Clear
          </button>
        )}
        {!has && (
          <div style={{ position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%,-50%)', color: '#CBD5E1', fontSize: 13, pointerEvents: 'none' }}>
            Sign here
          </div>
        )}
      </div>
    </div>
  )
}

// ═══════════════════════════════════════════════════════════════════════════════
// SigningPage
// ═══════════════════════════════════════════════════════════════════════════════

export default function SigningPage() {
  const { token } = useParams<{ token: string }>()

  const [step,          setStep]         = useState<'loading' | 'review' | 'otp' | 'sign' | 'done' | 'declined' | 'error'>('loading')
  const [otpCode,       setOtpCode]      = useState('')
  const [signatureData, setSignatureData] = useState<string | null>(null)
  const [devOtp,        setDevOtp]       = useState(false)
  const [showBody,      setShowBody]     = useState(false)
  const [showComments,  setShowComments] = useState(false)
  const [declineReason, setDeclineReason]= useState('')
  const [showDecline,   setShowDecline]  = useState(false)
  const [commentText,   setCommentText]  = useState('')
  const [clauseRef,     setClauseRef]    = useState('')
  const [isAmendment,   setIsAmendment]  = useState(false)
  const [errMsg,        setErrMsg]       = useState('')
  const [result,        setResult]       = useState<SigningResult | null>(null)

  // Token expiry countdown
  const [timeLeft, setTimeLeft] = useState('')

  // ── Load contract ─────────────────────────────────────────────────────────────

  const { data: contract, isError, error: loadError } = useQuery<PublicContractView>({
    queryKey: ['public-contract', token],
    queryFn: async () => {
      const r = await apiClient.get(`/api/v1/sign/${token}/contract`)
      return r.data?.data ?? r.data
    },
    enabled: !!token,
    retry: false,
  })

  // FIX: needed by postCommentMut below — previously this file never
  // imported/used useQueryClient at all, so a successfully posted comment
  // had no way to make its way back into the visible contract.comments
  // list short of a manual page reload.
  const queryClient = useQueryClient()

  useEffect(() => {
    if (!contract) return

    if (contract.alreadySigned) { setStep('done'); return }
    if (contract.status !== 'SENT') { setStep('error'); setErrMsg('This contract is no longer open for signing.'); return }
    setStep('review')

    // Token expiry countdown
    const update = () => {
      const diff = Math.max(0, new Date(contract.tokenExpiresAt).getTime() - Date.now())
      const h    = Math.floor(diff / 3600000)
      const m    = Math.floor((diff % 3600000) / 60000)
      setTimeLeft(diff === 0 ? 'Expired' : `${h}h ${m}m remaining`)
    }
    update()
    const timer = setInterval(update, 60_000)
    return () => clearInterval(timer)
  }, [contract])

  useEffect(() => {
    if (isError) { setStep('error'); setErrMsg((loadError as any)?.response?.data?.message ?? 'This signing link is invalid, expired, or has already been used.') }
  }, [isError, loadError])

  // ── Mutations ─────────────────────────────────────────────────────────────────

  const requestOtpMut = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/sign/${token}/otp`),
    onSuccess: async () => {
      setStep('sign'); setOtpCode(''); setDevOtp(false); setErrMsg('')
      // Dev mode auto-fill
      try {
        const res = await apiClient.get(`/api/v1/dev/otp/${contract?.myDetails.partyId}`)
        const otp = res.data?.data ?? res.data
        if (otp && typeof otp === 'string' && /^\d{6}$/.test(otp)) { setOtpCode(otp); setDevOtp(true) }
      } catch { /* production: endpoint not available */ }
    },
    onError: (e: any) => setErrMsg(e.response?.data?.message ?? 'Failed to send OTP. Check your phone number is registered.'),
  })

  const submitMut = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/sign/${token}/submit`, { otpCode, signatureData }),
    onSuccess: (r) => { setResult(r.data?.data ?? r.data); setStep('done') },
    onError: (e: any) => setErrMsg(e.response?.data?.message ?? 'Incorrect OTP — please try again or request a new one.'),
  })

  const declineMut = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/sign/${token}/decline`, { reason: declineReason }),
    onSuccess: () => setStep('declined'),
    onError: (e: any) => setErrMsg(e.response?.data?.message ?? 'Failed to record declination'),
  })

  const postCommentMut = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/sign/${token}/comment`, {
      comment: commentText, clauseRef: clauseRef || null, isAmendmentRequest: isAmendment,
    }),
    onSuccess: () => {
      setCommentText(''); setClauseRef(''); setIsAmendment(false); setErrMsg('')
      // FIX: was missing entirely — the comment saved correctly on the
      // backend (confirmed by the 200 response reaching here at all) but
      // the on-screen comments list is just a snapshot from the initial
      // GET /contract fetch, which nothing was ever telling to refresh.
      // Without this, a genuinely successful post looked indistinguishable
      // from a silently swallowed failure until the page was manually reloaded.
      queryClient.invalidateQueries({ queryKey: ['public-contract', token] })
    },
    onError: (e: any) => setErrMsg(e.response?.data?.message ?? 'Failed to post comment'),
  })

  // ─── Shared styles ─────────────────────────────────────────────────────────────

  const inp: React.CSSProperties = { width: '100%', padding: '10px 14px', border: '1.5px solid #E2E8F0', borderRadius: 10, fontSize: 14, boxSizing: 'border-box', background: '#fff', outline: 'none' }

  // ─── Loading ──────────────────────────────────────────────────────────────────

  if (step === 'loading' && !isError) {
    return (
      <div style={{ minHeight: '100vh', background: '#F8FAFC', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <div style={{ textAlign: 'center', color: '#94A3B8' }}>
          <div style={{ width: 40, height: 40, border: '3px solid #E2E8F0', borderTopColor: '#1B3A6B', borderRadius: '50%', animation: 'spin 0.8s linear infinite', margin: '0 auto 16px' }} />
          <div>Loading your contract…</div>
          <style>{`@keyframes spin { to { transform: rotate(360deg) } }`}</style>
        </div>
      </div>
    )
  }

  // ─── Error state ──────────────────────────────────────────────────────────────

  if (step === 'error') {
    return (
      <div style={{ minHeight: '100vh', background: '#F8FAFC', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 20 }}>
        <div style={{ maxWidth: 480, textAlign: 'center' }}>
          <div style={{ width: 60, height: 60, borderRadius: '50%', background: '#FEF2F2', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 20px' }}>
            <AlertTriangle size={28} color="#DC2626" />
          </div>
          <h2 style={{ fontSize: 20, fontWeight: 700, color: '#0F172A', marginBottom: 8 }}>Link not valid</h2>
          <p style={{ fontSize: 14, color: '#64748B', lineHeight: 1.7 }}>{errMsg}</p>
          <p style={{ fontSize: 13, color: '#94A3B8', marginTop: 16 }}>
            Please contact the person who sent this contract to request a new signing link.
          </p>
        </div>
      </div>
    )
  }

  // ─── Declined state ───────────────────────────────────────────────────────────

  if (step === 'declined') {
    return (
      <div style={{ minHeight: '100vh', background: '#F8FAFC', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 20 }}>
        <div style={{ maxWidth: 480, textAlign: 'center' }}>
          <div style={{ width: 60, height: 60, borderRadius: '50%', background: '#FFFBEB', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 20px' }}>
            <X size={28} color="#D97706" />
          </div>
          <h2 style={{ fontSize: 20, fontWeight: 700, color: '#0F172A', marginBottom: 8 }}>Declination recorded</h2>
          <p style={{ fontSize: 14, color: '#64748B', lineHeight: 1.7 }}>
            Your decision has been recorded and the contract sender has been notified.
            {declineReason && <><br /><br /><strong>Your reason:</strong> {declineReason}</>}
          </p>
        </div>
      </div>
    )
  }

  // ─── Done / fully signed ─────────────────────────────────────────────────────

  if (step === 'done') {
    const isFullyExecuted = result?.fullyExecuted ?? contract?.alreadySigned
    return (
      <div style={{ minHeight: '100vh', background: '#F8FAFC', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 20 }}>
        <div style={{ maxWidth: 540, width: '100%' }}>
          <div style={{ background: '#fff', borderRadius: 16, padding: 36, textAlign: 'center', border: '1px solid #E2E8F0', boxShadow: '0 4px 24px rgba(0,0,0,0.06)' }}>
            <div style={{ width: 64, height: 64, borderRadius: '50%', background: '#DCFCE7', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 20px' }}>
              <CheckCircle size={32} color="#166534" />
            </div>
            <h2 style={{ fontSize: 22, fontWeight: 800, color: '#0F172A', marginBottom: 8 }}>
              {contract?.alreadySigned && !result ? 'Already signed' : 'Contract signed'}
            </h2>
            <p style={{ fontSize: 14, color: '#64748B', lineHeight: 1.7, marginBottom: 20 }}>
              {result?.message ?? 'Your signature has been recorded.'}
            </p>

            {isFullyExecuted ? (
              <div style={{ padding: '14px 18px', background: '#F0FDF4', border: '1px solid #86EFAC', borderRadius: 10, fontSize: 14, color: '#166534', fontWeight: 600, marginBottom: 20 }}>
                ✓ All parties have signed — the contract is fully executed.
              </div>
            ) : (
              <div style={{ padding: '14px 18px', background: '#EFF6FF', border: '1px solid #BFDBFE', borderRadius: 10, fontSize: 14, color: '#1D4ED8', marginBottom: 20 }}>
                Awaiting the remaining parties to sign.
              </div>
            )}

            <div style={{ background: '#F8FAFC', borderRadius: 10, padding: '14px 18px', textAlign: 'left', fontSize: 13 }}>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
                {[
                  { l: 'Contract',   v: contract?.title        },
                  { l: 'Reference',  v: contract?.contractNumber },
                  { l: 'Signed by',  v: result?.partyName ?? contract?.myDetails?.fullName },
                  { l: 'Signed at',  v: result?.signedAt ? fmtDT(result.signedAt) : contract?.signedAt ? fmtDT(contract.signedAt) : '—' },
                ].map(({ l, v }) => (
                  <div key={l}>
                    <div style={{ fontSize: 10, color: '#94A3B8', fontWeight: 700, textTransform: 'uppercase', marginBottom: 2 }}>{l}</div>
                    <div style={{ fontWeight: 600, color: '#0F172A' }}>{v}</div>
                  </div>
                ))}
              </div>
            </div>

            <p style={{ fontSize: 12, color: '#94A3B8', marginTop: 20 }}>
              This signature is legally binding under the Electronic Communications and Transactions Act 25 of 2002, s 13.
            </p>
          </div>
        </div>
      </div>
    )
  }

  if (!contract) return null

  // ─── Main signing flow ────────────────────────────────────────────────────────

  return (
    <div style={{ minHeight: '100vh', background: '#F8FAFC' }}>
      {/* Top bar */}
      <div style={{ background: '#1B3A6B', padding: '14px 24px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <FileText size={20} color="#fff" />
          <span style={{ fontSize: 16, fontWeight: 700, color: '#fff' }}>HandyFlow</span>
          <span style={{ fontSize: 12, color: '#93C5FD', marginLeft: 8 }}>Secure signing</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12, color: '#93C5FD' }}>
          <Clock size={13} />
          <span>{timeLeft}</span>
        </div>
      </div>

      {/* Content */}
      <div style={{ maxWidth: 760, margin: '0 auto', padding: '28px 20px' }}>

        {/* Contract header card */}
        <div style={{ background: '#fff', borderRadius: 14, border: '1px solid #E2E8F0', padding: '22px 24px', marginBottom: 16 }}>
          <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginBottom: 12 }}>
            <div>
              <div style={{ fontSize: 20, fontWeight: 800, color: '#0F172A', marginBottom: 4 }}>{contract.title}</div>
              <div style={{ fontSize: 13, color: '#64748B' }}>
                {contract.contractNumber} · {TYPE_LABEL[contract.contractType] ?? contract.contractType}
              </div>
            </div>
            <div style={{ background: '#EFF6FF', color: '#1D4ED8', padding: '5px 12px', borderRadius: 20, fontSize: 12, fontWeight: 700, flexShrink: 0 }}>
              Pending your signature
            </div>
          </div>

          {/* Contract metadata */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 10, marginBottom: 14 }}>
            {[
              { l: 'Start date', v: fmtDate(contract.startDate) },
              { l: 'End date',   v: fmtDate(contract.endDate)   },
              { l: 'Value',      v: fmtR(contract.valueAmount, contract.currency) ?? '—' },
            ].map(({ l, v }) => (
              <div key={l} style={{ background: '#F8FAFC', borderRadius: 8, padding: '8px 12px' }}>
                <div style={{ fontSize: 10, color: '#94A3B8', fontWeight: 700, textTransform: 'uppercase', marginBottom: 2 }}>{l}</div>
                <div style={{ fontSize: 13, fontWeight: 600, color: '#0F172A' }}>{v}</div>
              </div>
            ))}
          </div>

          {/* Contract body toggle */}
          <button onClick={() => setShowBody(!showBody)} style={{ display: 'flex', alignItems: 'center', gap: 6, background: 'none', border: 'none', cursor: 'pointer', color: '#1B3A6B', fontSize: 13, fontWeight: 600, padding: 0 }}>
            {showBody ? <ChevronUp size={15} /> : <ChevronDown size={15} />}
            {showBody ? 'Hide contract body' : 'Read the full contract'}
          </button>

          {showBody && (
            <div style={{ marginTop: 14, border: '1px solid #E2E8F0', borderRadius: 10, padding: '18px 20px', maxHeight: 480, overflowY: 'auto', fontSize: 14, lineHeight: 1.9, color: '#374151' }}
              dangerouslySetInnerHTML={{ __html: contract.bodyHtml }}
            />
          )}

          {contract.bodyHash && (
            <div style={{ marginTop: 10, fontSize: 11, color: '#94A3B8', fontFamily: 'monospace' }}>
              Body hash (SHA-256): {contract.bodyHash.slice(0, 20)}…
            </div>
          )}
        </div>

        {/* Parties card */}
        <div style={{ background: '#fff', borderRadius: 14, border: '1px solid #E2E8F0', padding: '22px 24px', marginBottom: 16 }}>
          <div style={{ fontWeight: 700, fontSize: 15, color: '#0F172A', marginBottom: 14 }}>Signing Parties</div>

          {/* My details */}
          <div style={{ marginBottom: 12, padding: '12px 16px', background: '#EFF6FF', border: '1px solid #BFDBFE', borderRadius: 10 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <div style={{ width: 34, height: 34, borderRadius: '50%', background: '#1D4ED8', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                <User size={16} color="#fff" />
              </div>
              <div style={{ flex: 1 }}>
                <div style={{ fontWeight: 700, fontSize: 14, color: '#0F172A' }}>
                  {contract.myDetails.fullName}
                  <span style={{ marginLeft: 6, background: '#BFDBFE', color: '#1D4ED8', padding: '1px 8px', borderRadius: 20, fontSize: 10, fontWeight: 700 }}>You</span>
                </div>
                <div style={{ fontSize: 12, color: '#64748B' }}>
                  {contract.myDetails.partyRole}
                  {contract.myDetails.companyName && ` · ${contract.myDetails.companyName}`}
                  {contract.myDetails.phoneMasked && ` · OTP to ${contract.myDetails.phoneMasked}`}
                </div>
              </div>
              <div style={{ fontSize: 12, fontWeight: 700, color: contract.myDetails.signingStatus === 'SIGNED' ? '#166534' : '#D97706' }}>
                {contract.myDetails.signingStatus === 'SIGNED' ? '✓ Signed' : 'Awaiting your signature'}
              </div>
            </div>
          </div>

          {/* Other parties */}
          {contract.otherParties.map((p, i) => (
            <div key={i} style={{ padding: '10px 16px', border: '1px solid #E2E8F0', borderRadius: 10, marginBottom: 8, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <div style={{ width: 32, height: 32, borderRadius: '50%', background: '#F1F5F9', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                  <User size={14} color="#64748B" />
                </div>
                <div>
                  <div style={{ fontWeight: 600, fontSize: 13, color: '#0F172A' }}>
                    {p.fullName}
                    {p.companyName && <span style={{ fontSize: 11, color: '#94A3B8', marginLeft: 5 }}>({p.companyName})</span>}
                  </div>
                  <div style={{ fontSize: 11, color: '#94A3B8' }}>
                    {p.partyRole} · sign order {p.signingOrder}
                  </div>
                </div>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                {p.signingStatus === 'SIGNED' ? (
                  <>
                    <CheckCircle size={13} color="#166534" />
                    <span style={{ fontSize: 12, color: '#166534', fontWeight: 600 }}>Signed</span>
                    {p.signedAt && <span style={{ fontSize: 11, color: '#94A3B8' }}>{fmtDT(p.signedAt)}</span>}
                  </>
                ) : (
                  <>
                    <Clock size={13} color="#D97706" />
                    <span style={{ fontSize: 12, color: '#D97706', fontWeight: 600 }}>Pending</span>
                  </>
                )}
              </div>
            </div>
          ))}
        </div>

        {/* Comments card */}
        <div style={{ background: '#fff', borderRadius: 14, border: '1px solid #E2E8F0', padding: '22px 24px', marginBottom: 16 }}>
          <button onClick={() => setShowComments(!showComments)} style={{ display: 'flex', alignItems: 'center', gap: 7, background: 'none', border: 'none', cursor: 'pointer', color: '#374151', fontSize: 14, fontWeight: 600, padding: 0, width: '100%', justifyContent: 'space-between' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
              <MessageSquare size={15} color="#1B3A6B" />
              Comments & Amendment Requests
              {contract.comments.length > 0 && (
                <span style={{ background: '#EEF2FF', color: '#1B3A6B', padding: '1px 8px', borderRadius: 20, fontSize: 11, fontWeight: 700 }}>
                  {contract.comments.length}
                </span>
              )}
            </div>
            {showComments ? <ChevronUp size={14} color="#94A3B8" /> : <ChevronDown size={14} color="#94A3B8" />}
          </button>

          {showComments && (
            <div style={{ marginTop: 16 }}>
              {/* Existing comments */}
              {contract.comments.length > 0 && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 16 }}>
                  {contract.comments.map(cm => (
                    <div key={cm.id} style={{ padding: '10px 14px', background: cm.isAmendmentRequest ? '#FFFBEB' : '#F8FAFC', border: `1px solid ${cm.isAmendmentRequest ? '#FDE68A' : '#E2E8F0'}`, borderRadius: 8 }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                          <span style={{ fontWeight: 600, fontSize: 12, color: '#0F172A' }}>{cm.authorName}</span>
                          <span style={{ fontSize: 10, color: '#94A3B8' }}>{cm.authorRole}</span>
                          {cm.isAmendmentRequest && <span style={{ background: '#FEF3C7', color: '#D97706', padding: '1px 7px', borderRadius: 20, fontSize: 10, fontWeight: 700 }}>Amendment request</span>}
                        </div>
                        <span style={{ fontSize: 11, color: '#94A3B8' }}>{fmtDT(cm.createdAt)}</span>
                      </div>
                      {cm.clauseRef && <div style={{ fontSize: 11, color: '#64748B', marginBottom: 3 }}>Re: {cm.clauseRef}</div>}
                      <div style={{ fontSize: 13, color: '#374151', lineHeight: 1.6 }}>{cm.comment}</div>
                    </div>
                  ))}
                </div>
              )}

              {/* Post comment form */}
              <div style={{ border: '1px solid #E2E8F0', borderRadius: 10, padding: '14px 16px' }}>
                <div style={{ fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 10 }}>Post a comment or request an amendment</div>
                <textarea value={commentText} onChange={e => setCommentText(e.target.value)} rows={3}
                  placeholder="Write your comment or describe the amendment you are requesting…"
                  style={{ ...inp, resize: 'vertical', marginBottom: 10 }} />
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginBottom: 10 }}>
                  <div>
                    <label style={{ display: 'block', fontSize: 12, fontWeight: 600, color: '#374151', marginBottom: 3 }}>Clause reference (optional)</label>
                    <input value={clauseRef} onChange={e => setClauseRef(e.target.value)} placeholder="e.g. Clause 4.1" style={inp} />
                  </div>
                  <div style={{ display: 'flex', alignItems: 'flex-end', paddingBottom: 2 }}>
                    <label style={{ display: 'flex', alignItems: 'center', gap: 7, fontSize: 13, fontWeight: 500, color: '#374151', cursor: 'pointer' }}>
                      <input type="checkbox" checked={isAmendment} onChange={e => setIsAmendment(e.target.checked)} />
                      Flag as amendment request
                    </label>
                  </div>
                </div>
                <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                  <button disabled={!commentText.trim() || postCommentMut.isPending} onClick={() => postCommentMut.mutate()}
                    style={{ display: 'flex', alignItems: 'center', gap: 6, background: !commentText.trim() ? '#94A3B8' : '#1B3A6B', color: '#fff', border: 'none', borderRadius: 8, padding: '9px 18px', fontSize: 13, fontWeight: 600, cursor: 'pointer' }}>
                    <Send size={13} />{postCommentMut.isPending ? 'Posting…' : 'Post Comment'}
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Signing action card */}
        {!contract.alreadySigned && (
          <div style={{ background: '#fff', borderRadius: 14, border: '1px solid #E2E8F0', padding: '24px', marginBottom: 16 }}>
            {step === 'review' && (
              <>
                <div style={{ fontWeight: 700, fontSize: 16, color: '#0F172A', marginBottom: 6 }}>Ready to sign?</div>
                <p style={{ fontSize: 13, color: '#64748B', marginBottom: 16, lineHeight: 1.7 }}>
                  Review the contract above, then click <strong>Sign with OTP</strong>. A 6-digit code will be sent to your registered phone number ending in <strong>{contract.myDetails.phoneMasked?.slice(-4)}</strong>.
                </p>
                {contract.myDetails.signingOrder > 1 && contract.otherParties.some(p => p.signingOrder < contract.myDetails.signingOrder && p.signingStatus !== 'SIGNED') && (
                  <div style={{ marginBottom: 16, padding: '10px 14px', background: '#FFFBEB', border: '1px solid #FDE68A', borderRadius: 8, fontSize: 13, color: '#92400E' }}>
                    Waiting for party {contract.myDetails.signingOrder - 1} to sign before you. You will receive an SMS when it is your turn.
                  </div>
                )}
                {errMsg && <div style={{ marginBottom: 14, padding: '10px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626' }}>{errMsg}</div>}
                <div style={{ display: 'flex', gap: 10 }}>
                  <button onClick={() => requestOtpMut.mutate()} disabled={requestOtpMut.isPending}
                    style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, background: '#1B3A6B', color: '#fff', border: 'none', borderRadius: 10, padding: '14px', fontSize: 15, fontWeight: 700, cursor: 'pointer' }}>
                    <Shield size={18} />{requestOtpMut.isPending ? 'Sending OTP…' : 'Sign with OTP'}
                  </button>
                  <button onClick={() => setShowDecline(true)}
                    style={{ padding: '14px 20px', background: '#fff', color: '#DC2626', border: '1px solid #FECACA', borderRadius: 10, fontSize: 14, fontWeight: 600, cursor: 'pointer' }}>
                    Decline
                  </button>
                </div>
              </>
            )}

            {step === 'sign' && (
              <>
                <div style={{ fontWeight: 700, fontSize: 16, color: '#0F172A', marginBottom: 6 }}>Enter your OTP</div>
                <p style={{ fontSize: 13, color: '#64748B', marginBottom: 18, lineHeight: 1.7 }}>
                  A 6-digit code was sent to <strong>{contract.myDetails.phoneMasked}</strong>. Enter it below to sign. The code expires in 10 minutes.
                </p>

                <div style={{ marginBottom: 20 }}>
                  <input
                    autoFocus value={otpCode}
                    onChange={e => setOtpCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                    placeholder="000000" maxLength={6}
                    style={{ ...inp, fontSize: 28, letterSpacing: '0.4em', textAlign: 'center', fontWeight: 700, padding: '16px' }}
                  />
                  {devOtp && (
                    <div style={{ marginTop: 8, padding: '6px 12px', background: '#FFFBEB', border: '1px solid #FDE68A', borderRadius: 7, fontSize: 12, color: '#92400E', fontWeight: 600, textAlign: 'center' }}>
                      Dev mode — OTP auto-filled (SMS not yet configured)
                    </div>
                  )}
                </div>

                <div style={{ marginBottom: 20 }}>
                  <SignatureCanvas onCapture={setSignatureData} />
                </div>

                <div style={{ padding: '12px 16px', background: '#F0FDF4', border: '1px solid #BBF7D0', borderRadius: 8, fontSize: 12, color: '#166534', marginBottom: 18, lineHeight: 1.7 }}>
                  <strong>Legal notice:</strong> By entering the OTP you confirm you have read and agree to be legally bound by this contract under the Electronic Communications and Transactions Act 25 of 2002, s 13.
                </div>

                {errMsg && <div style={{ marginBottom: 14, padding: '10px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626' }}>{errMsg}</div>}

                <div style={{ display: 'flex', gap: 10 }}>
                  <button onClick={() => { setStep('review'); setErrMsg('') }} style={{ padding: '13px 20px', background: '#fff', color: '#374151', border: '1px solid #E2E8F0', borderRadius: 10, fontSize: 14, cursor: 'pointer' }}>
                    Back
                  </button>
                  <button disabled={otpCode.length < 6 || submitMut.isPending} onClick={() => submitMut.mutate()}
                    style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, background: otpCode.length >= 6 ? '#166534' : '#94A3B8', color: '#fff', border: 'none', borderRadius: 10, padding: '13px', fontSize: 15, fontWeight: 700, cursor: otpCode.length >= 6 ? 'pointer' : 'not-allowed' }}>
                    <CheckCircle size={18} />{submitMut.isPending ? 'Signing…' : 'Confirm & Sign'}
                  </button>
                </div>
                <div style={{ textAlign: 'center', marginTop: 12 }}>
                  <button onClick={() => requestOtpMut.mutate()} disabled={requestOtpMut.isPending}
                    style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 12, color: '#64748B', textDecoration: 'underline' }}>
                    Resend OTP
                  </button>
                </div>
              </>
            )}
          </div>
        )}

        {/* ECT Act footer */}
        <div style={{ textAlign: 'center', fontSize: 11, color: '#94A3B8', lineHeight: 1.6 }}>
          <Shield size={12} style={{ verticalAlign: 'middle', marginRight: 4 }} />
          Secured by HandyFlow · Electronic signatures are legally binding under ECT Act 25 of 2002
        </div>
      </div>

      {/* Decline modal */}
      {showDecline && (
        <div style={{ position: 'fixed', inset: 0, background: 'rgba(15,23,42,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: 20 }}>
          <div style={{ background: '#fff', borderRadius: 16, padding: 28, width: '100%', maxWidth: 440, boxShadow: '0 20px 60px rgba(0,0,0,0.18)' }}>
            <h3 style={{ margin: '0 0 12px', fontSize: 17, fontWeight: 700, color: '#DC2626' }}>Decline to Sign</h3>
            <div style={{ marginBottom: 16, padding: '10px 14px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#B91C1C' }}>
              This will formally record your refusal. The contract sender will be notified.
            </div>
            <div style={{ marginBottom: 18 }}>
              <label style={{ display: 'block', fontSize: 12, fontWeight: 600, color: '#374151', marginBottom: 4 }}>Reason (optional but recommended)</label>
              <textarea autoFocus value={declineReason} onChange={e => setDeclineReason(e.target.value)} rows={3}
                placeholder="e.g. Terms in Clause 4 are unacceptable. Please revise and resend."
                style={{ ...inp, resize: 'vertical' }} />
            </div>
            {errMsg && <div style={{ marginBottom: 12, padding: '9px 12px', background: '#FEF2F2', borderRadius: 8, fontSize: 13, color: '#DC2626' }}>{errMsg}</div>}
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
              <button onClick={() => setShowDecline(false)} style={{ padding: '9px 18px', border: '1px solid #E2E8F0', borderRadius: 9, background: '#fff', fontSize: 13, cursor: 'pointer', color: '#374151' }}>Cancel</button>
              <button disabled={declineMut.isPending} onClick={() => declineMut.mutate()}
                style={{ padding: '9px 22px', background: '#DC2626', color: '#fff', border: 'none', borderRadius: 9, fontSize: 14, fontWeight: 700, cursor: 'pointer' }}>
                {declineMut.isPending ? 'Declining…' : 'Confirm Decline'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
