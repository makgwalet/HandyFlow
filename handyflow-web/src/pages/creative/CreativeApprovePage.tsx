// src/pages/creative/CreativeApprovePage.tsx
// PUBLIC route — no authentication required.
// Accessed via: /creative/approve/:token
// The token comes from the approval email sent by the designer.

import { useState, useRef } from 'react'
import { useParams } from 'react-router-dom'
import { useQuery, useMutation } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import {
  CheckCircle, XCircle, MessageSquare, AlertTriangle,
  Clock, FileText, Image, Video, Package, Loader2,
  ChevronDown, ChevronUp,
} from 'lucide-react'

const inp: React.CSSProperties = { width: '100%', padding: '10px 13px', border: '1.5px solid #E2E8F0', borderRadius: 9, fontSize: 14, boxSizing: 'border-box' as const, background: '#fff', outline: 'none', fontFamily: 'inherit' }
const lbl: React.CSSProperties = { display: 'block', fontSize: 12, fontWeight: 700, color: '#6B7280', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 7 }

// NEW: shared by the comment form and the comment list — "0:45" not "45s".
const formatTimecode = (seconds: number) => {
  const total = Math.round(seconds)
  const m = Math.floor(total / 60)
  const s = total % 60
  return `${m}:${s.toString().padStart(2, '0')}`
}

type View = 'proof' | 'approved' | 'rejected' | 'error' | 'already_done'

export function CreativeApprovePage() {
  const { token } = useParams<{ token: string }>()

  const [view,         setView]         = useState<View>('proof')
  const [clientName,   setClientName]   = useState('')
  const [clientEmail,  setClientEmail]  = useState('')
  const [rejectReason, setRejectReason] = useState('')
  const [comment,      setComment]      = useState('')
  const [commentName,  setCommentName]  = useState('')
  const [commentSent,  setCommentSent]  = useState(false)
  const [showComments, setShowComments] = useState(true)
  const [showReject,   setShowReject]   = useState(false)
  const [errorMsg,     setErrorMsg]     = useState('')
  const [tagTimecode,  setTagTimecode]  = useState(true)
  const videoRef = useRef<HTMLVideoElement>(null)
  const [pendingPin, setPendingPin] = useState<{ x: number; y: number } | null>(null)
  const [highlightedCommentId, setHighlightedCommentId] = useState<string | null>(null)
  const imageWrapRef = useRef<HTMLDivElement>(null)

  // Load proof data
  const { data: proof, isLoading, error: loadError } = useQuery<any>({
    queryKey: ['public-proof', token],
    queryFn: async () => {
      const r = await apiClient.get(`/api/v1/creative/approve/${token}`)
      return r.data?.data ?? r.data
    },
    retry: false,
    enabled: !!token,
  })

  const approve = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/creative/approve/${token}/approve`, {
      clientName: clientName.trim(),
      clientEmail: clientEmail.trim() || null,
    }),
    onSuccess: () => setView('approved'),
    onError: (e: any) => {
      const msg = e.response?.data?.message ?? 'Approval failed. Please try again.'
      if (msg.includes('already been approved')) setView('already_done')
      else if (msg.includes('expired'))          setView('error')
      else                                        setErrorMsg(msg)
    },
  })

  const reject = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/creative/approve/${token}/reject`, {
      reason: rejectReason.trim(),
    }),
    onSuccess: () => setView('rejected'),
    onError: (e: any) => setErrorMsg(e.response?.data?.message ?? 'Failed to submit feedback.'),
  })

  const addComment = useMutation({
    mutationFn: () => apiClient.post(`/api/v1/creative/approve/${token}/comments`, {
      comment: comment.trim(),
      authorName: commentName.trim() || 'Client',
      // NEW: only meaningful for video proofs, and only when the client
      // actually wants this comment tied to a moment — read at submit time
      // rather than tracked continuously in state, since we only need the
      // value once, at the instant of posting.
      timecodeSeconds: tagTimecode && videoRef.current ? videoRef.current.currentTime : null,
      // NEW: set only when the client clicked a point on the image before
      // writing this comment — see the image click handler below.
      anchorX: pendingPin?.x ?? null,
      anchorY: pendingPin?.y ?? null,
    }),
    onSuccess: () => { setCommentSent(true); setComment(''); setPendingPin(null); setTimeout(() => setCommentSent(false), 3000) },
    onError: (e: any) => setErrorMsg(e.response?.data?.message ?? 'Failed to add comment.'),
  })

  // ── Loading state ──────────────────────────────────────────────────────────
  if (isLoading) {
    return (
      <Page>
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '80px 20px', color: '#64748B' }}>
          <Loader2 size={36} style={{ animation: 'spin 1s linear infinite', marginBottom: 16 }} />
          <div style={{ fontWeight: 600, fontSize: 16 }}>Loading your proof...</div>
        </div>
      </Page>
    )
  }

  // ── Load error ─────────────────────────────────────────────────────────────
  if (loadError || !proof) {
    const msg = (loadError as any)?.response?.data?.message ?? ''
    const isExpired  = msg.includes('expired')
    const isInvalid  = msg.includes('Invalid') || msg.includes('not found')
    const alreadyDone = msg.includes('already been approved')
    return (
      <Page>
        <StatusCard
          icon={isExpired ? <Clock size={48} color="#D97706" /> : alreadyDone ? <CheckCircle size={48} color="#166534" /> : <XCircle size={48} color="#DC2626" />}
          title={isExpired ? 'Link expired' : alreadyDone ? 'Already approved' : 'Invalid link'}
          color={isExpired ? '#D97706' : alreadyDone ? '#166534' : '#DC2626'}
          bg={isExpired ? '#FFFBEB' : alreadyDone ? '#F0FDF4' : '#FEF2F2'}>
          {isExpired
            ? 'This approval link has expired. Please ask your designer to resend the proof.'
            : alreadyDone
            ? 'This proof has already been approved. No further action is needed.'
            : 'This approval link is not valid. Please check that you have the correct URL or ask your designer to resend it.'}
        </StatusCard>
      </Page>
    )
  }

  // ── Already approved ───────────────────────────────────────────────────────
  if (proof.status === 'APPROVED') {
    return (
      <Page tenantName={proof.tenantName}>
        <StatusCard icon={<CheckCircle size={48} color="#166534" />} title="Already approved" color="#166534" bg="#F0FDF4">
          This proof has already been approved. Thank you!
        </StatusCard>
      </Page>
    )
  }

  // ── Post-approve success ───────────────────────────────────────────────────
  if (view === 'approved') {
    return (
      <Page tenantName={proof?.tenantName}>
        <StatusCard icon={<CheckCircle size={48} color="#166534" />} title="Approved!" color="#166534" bg="#F0FDF4">
          <div style={{ marginBottom: 12 }}>
            Thank you, <strong>{clientName}</strong>. Your approval has been recorded and your designer has been notified.
          </div>
          <div style={{ fontSize: 13, color: '#64748B', background: '#F8FAFC', border: '1px solid #E2E8F0', borderRadius: 8, padding: '10px 14px' }}>
            Your approval has been logged with a timestamp and IP address as legal proof of sign-off.
          </div>
        </StatusCard>
      </Page>
    )
  }

  // ── Post-reject success ────────────────────────────────────────────────────
  if (view === 'rejected') {
    return (
      <Page tenantName={proof?.tenantName}>
        <StatusCard icon={<MessageSquare size={48} color="#D97706" />} title="Feedback submitted" color="#D97706" bg="#FFFBEB">
          Your change request has been sent to your designer. They will review your feedback and send you a revised proof.
        </StatusCard>
      </Page>
    )
  }

  // ── Already done (approved mid-session) ───────────────────────────────────
  if (view === 'already_done') {
    return (
      <Page tenantName={proof?.tenantName}>
        <StatusCard icon={<CheckCircle size={48} color="#166534" />} title="Already approved" color="#166534" bg="#F0FDF4">
          This proof was already approved. No further action is needed.
        </StatusCard>
      </Page>
    )
  }

  // ── Main approval view ─────────────────────────────────────────────────────
  const fileIsImage = proof.fileType?.startsWith('image/') || proof.fileName?.match(/\.(jpg|jpeg|png|gif|webp|svg)$/i)
  const fileIsVideo = proof.fileType?.startsWith('video/') || proof.fileName?.match(/\.(mp4|mov|webm|avi)$/i)
  const fileIsPdf   = proof.fileType === 'application/pdf' || proof.fileName?.endsWith('.pdf')

  return (
    <Page tenantName={proof.tenantName}>
      <style>{`@keyframes spin { to { transform: rotate(360deg) } } @keyframes fadeIn { from { opacity: 0; transform: translateY(8px) } to { opacity: 1; transform: translateY(0) } } @keyframes pulse { 0%, 100% { transform: translate(-50%, -50%) scale(1); opacity: 1 } 50% { transform: translate(-50%, -50%) scale(1.4); opacity: 0.5 } }`}</style>

      {/* Job context header */}
      <div style={{ background: '#fff', border: '1px solid #E2E8F0', borderRadius: 14, padding: '20px 24px', marginBottom: 20, animation: 'fadeIn 0.3s ease' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 10 }}>
          <div>
            <div style={{ fontSize: 11, fontWeight: 700, color: '#94A3B8', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 5 }}>
              {proof.tenantName} · Proof for review
            </div>
            <h2 style={{ margin: 0, fontSize: 20, fontWeight: 800, color: '#0F172A', marginBottom: 5 }}>{proof.jobTitle}</h2>
            <div style={{ fontSize: 13, color: '#64748B' }}>
              Version {proof.versionNumber}
              {proof.title && <span> — {proof.title}</span>}
              {proof.fileName && <span> · {proof.fileName}</span>}
            </div>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, background: '#FFFBEB', border: '1px solid #FDE68A', borderRadius: 8, padding: '6px 12px', fontSize: 12, color: '#D97706', fontWeight: 600 }}>
            <Clock size={13} /> Awaiting your approval
          </div>
        </div>
      </div>

      {/* NEW: multi-stakeholder chain visibility — completely absent for
          SINGLE-mode proofs, which is the vast majority and behaves exactly
          as it always has. Shows who this link belongs to and where
          everyone else in the chain stands, read-only (no tokens exposed). */}
      {proof.approvalMode && proof.approvalMode !== 'SINGLE' && (
        <div style={{ background: '#EFF6FF', border: '1px solid #BFDBFE', borderRadius: 12, padding: '14px 18px', marginBottom: 20 }}>
          <div style={{ fontSize: 12, fontWeight: 700, color: '#1D4ED8', marginBottom: 8 }}>
            {proof.approvalMode === 'SEQUENTIAL' ? 'Sequential' : 'Parallel'} approval
            {proof.myApproverName && <> — you are <strong>{proof.myApproverName}</strong></>}
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            {(proof.otherApprovers ?? []).map((a: any) => {
              const isMe = a.approverName === proof.myApproverName
              const label = a.status === 'APPROVED' ? 'Approved'
                : a.status === 'REJECTED' ? 'Requested changes'
                : isMe ? 'Your review' : 'Pending';
              const color = a.status === 'APPROVED' ? '#166534'
                : a.status === 'REJECTED' ? '#DC2626' : '#94A3B8';
              return (
                <div key={a.approvalOrder} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12 }}>
                  <span style={{ color: isMe ? '#1D4ED8' : '#374151', fontWeight: isMe ? 700 : 400 }}>
                    {a.approvalOrder}. {a.approverName}{isMe && ' (you)'}
                  </span>
                  <span style={{ color, fontWeight: 600 }}>{label}</span>
                </div>
              )
            })}
          </div>
        </div>
      )}

      {/* Proof preview */}
      <div style={{ background: '#fff', border: '1px solid #E2E8F0', borderRadius: 14, overflow: 'hidden', marginBottom: 20, animation: 'fadeIn 0.35s ease' }}>
        <div style={{ padding: '14px 20px', borderBottom: '1px solid #F1F5F9', display: 'flex', alignItems: 'center', gap: 8 }}>
          <div style={{ width: 28, height: 28, borderRadius: 7, background: '#EFF6FF', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            {fileIsImage ? <Image size={14} color="#1D4ED8" /> : fileIsVideo ? <Video size={14} color="#1D4ED8" /> : fileIsPdf ? <FileText size={14} color="#1D4ED8" /> : <Package size={14} color="#1D4ED8" />}
          </div>
          <span style={{ fontSize: 13, fontWeight: 700, color: '#374151' }}>Proof Preview</span>
        </div>

        <div style={{ padding: 20, minHeight: 200, background: '#F9FAFB', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          {proof.fileUrl ? (
            fileIsImage ? (
              <div
                ref={imageWrapRef}
                onClick={e => {
                  const rect = e.currentTarget.getBoundingClientRect()
                  setPendingPin({
                    x: (e.clientX - rect.left) / rect.width,
                    y: (e.clientY - rect.top) / rect.height,
                  })
                }}
                title="Click anywhere on the image to pin your next comment to that spot"
                style={{ position: 'relative', display: 'inline-block', cursor: 'crosshair', lineHeight: 0 }}>
                <img
                  src={`data:${proof.fileType || 'image/png'};base64,${proof.fileUrl}`}
                  alt={proof.fileName ?? 'Proof'}
                  style={{ maxWidth: '100%', maxHeight: 600, borderRadius: 8, boxShadow: '0 4px 20px rgba(0,0,0,0.12)', display: 'block' }}
                />
                {/* Existing pinned comments — numbered among pinned comments only, not the whole thread */}
                {(proof.comments ?? [])
                  .filter((c: any) => c.anchorX != null)
                  .map((c: any, i: number) => (
                    <button
                      key={c.id}
                      onClick={ev => {
                        ev.stopPropagation() // don't also register a new pin click
                        setHighlightedCommentId(c.id)
                        document.getElementById(`comment-${c.id}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
                        setTimeout(() => setHighlightedCommentId(null), 2000)
                      }}
                      title={c.comment}
                      style={{
                        position: 'absolute', left: `${c.anchorX * 100}%`, top: `${c.anchorY * 100}%`,
                        transform: 'translate(-50%, -50%)', width: 24, height: 24, borderRadius: '50%',
                        background: c.authorType === 'CLIENT' ? '#0D9488' : '#1B3A6B', color: '#fff',
                        border: '2px solid #fff', boxShadow: '0 2px 6px rgba(0,0,0,0.3)',
                        fontSize: 11, fontWeight: 700, cursor: 'pointer', display: 'flex',
                        alignItems: 'center', justifyContent: 'center', padding: 0,
                      }}>
                      {i + 1}
                    </button>
                  ))}
                {/* Pending pin — where the next comment will be anchored if submitted */}
                {pendingPin && (
                  <div style={{
                    position: 'absolute', left: `${pendingPin.x * 100}%`, top: `${pendingPin.y * 100}%`,
                    transform: 'translate(-50%, -50%)', width: 20, height: 20, borderRadius: '50%',
                    background: 'rgba(217,119,6,0.25)', border: '2px solid #D97706',
                    animation: 'pulse 1.2s ease-in-out infinite', pointerEvents: 'none',
                  }} />
                )}
              </div>
            ) : fileIsVideo ? (
              <video ref={videoRef} controls style={{ maxWidth: '100%', maxHeight: 500, borderRadius: 8 }}>
                <source src={`data:${proof.fileType};base64,${proof.fileUrl}`} type={proof.fileType} />
              </video>
            ) : fileIsPdf ? (
              <iframe
                src={`data:application/pdf;base64,${proof.fileUrl}`}
                style={{ width: '100%', height: 600, border: 'none', borderRadius: 8 }}
                title="PDF proof"
              />
            ) : (
              <div style={{ textAlign: 'center', padding: '40px 20px' }}>
                <Package size={40} style={{ marginBottom: 12, color: '#CBD5E1' }} />
                <div style={{ fontWeight: 600, color: '#475569', marginBottom: 8 }}>{proof.fileName}</div>
                <a
                  href={`data:${proof.fileType || 'application/octet-stream'};base64,${proof.fileUrl}`}
                  download={proof.fileName}
                  style={{ display: 'inline-flex', alignItems: 'center', gap: 6, background: '#1B3A6B', color: '#fff', padding: '10px 20px', borderRadius: 8, textDecoration: 'none', fontSize: 14, fontWeight: 600 }}>
                  Download to view
                </a>
              </div>
            )
          ) : (
            <div style={{ textAlign: 'center', padding: '40px 20px', color: '#94A3B8' }}>
              <FileText size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
              <div style={{ fontWeight: 600, color: '#475569' }}>No preview available</div>
              <div style={{ fontSize: 13, marginTop: 4 }}>The designer has not attached a file to this proof.</div>
            </div>
          )}
        </div>
      </div>

      {/* Comments thread */}
      {proof.comments?.length > 0 && (
        <div style={{ background: '#fff', border: '1px solid #E2E8F0', borderRadius: 14, marginBottom: 20, overflow: 'hidden', animation: 'fadeIn 0.4s ease' }}>
          <button onClick={() => setShowComments(p => !p)}
            style={{ width: '100%', display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '14px 20px', background: 'none', border: 'none', cursor: 'pointer', borderBottom: showComments ? '1px solid #F1F5F9' : 'none' }}>
            <span style={{ fontSize: 13, fontWeight: 700, color: '#374151', display: 'flex', alignItems: 'center', gap: 7 }}>
              <MessageSquare size={14} color="#64748B" /> Comments ({proof.comments.length})
            </span>
            {showComments ? <ChevronUp size={14} color="#94A3B8" /> : <ChevronDown size={14} color="#94A3B8" />}
          </button>
          {showComments && (
            <div style={{ padding: '14px 20px' }}>
              {proof.comments.map((c: any) => (
                <div key={c.id} id={`comment-${c.id}`}
                  style={{ display: 'flex', gap: 10, marginBottom: 12, borderRadius: 10, transition: 'background 0.3s',
                    background: highlightedCommentId === c.id ? '#FEF3C7' : 'transparent', padding: highlightedCommentId === c.id ? 6 : 0 }}>
                  <div style={{ width: 30, height: 30, borderRadius: '50%', background: c.authorType === 'CLIENT' ? '#0D9488' : '#1B3A6B', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, color: '#fff', fontWeight: 700, flexShrink: 0 }}>
                    {c.authorName.charAt(0).toUpperCase()}
                  </div>
                  <div style={{ flex: 1, background: '#F9FAFB', borderRadius: 10, padding: '10px 14px', border: '1px solid #E2E8F0' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                      <span style={{ fontSize: 13, fontWeight: 700, color: '#0F172A' }}>{c.authorName}</span>
                      <span style={{ fontSize: 11, color: '#94A3B8' }}>{c.authorType === 'CLIENT' ? 'You' : 'Designer'}</span>
                    </div>
                    {c.timecodeSeconds != null && (
                      <button
                        onClick={() => { if (videoRef.current) { videoRef.current.currentTime = c.timecodeSeconds; videoRef.current.scrollIntoView({ behavior: 'smooth', block: 'center' }) } }}
                        style={{ display: 'inline-flex', alignItems: 'center', gap: 4, marginBottom: 6, marginRight: 6, padding: '2px 8px', background: '#EFF6FF', color: '#1D4ED8', border: 'none', borderRadius: 20, fontSize: 11, fontWeight: 700, cursor: 'pointer' }}>
                        ⏱ {formatTimecode(c.timecodeSeconds)}
                      </button>
                    )}
                    {c.anchorX != null && (
                      <button
                        onClick={() => imageWrapRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' })}
                        title="Scroll up to see where this is pinned on the image"
                        style={{ display: 'inline-flex', alignItems: 'center', gap: 4, marginBottom: 6, padding: '2px 8px', background: '#F0FDF9', color: '#0D9488', border: 'none', borderRadius: 20, fontSize: 11, fontWeight: 700, cursor: 'pointer' }}>
                        📍 Pinned comment
                      </button>
                    )}
                    <div style={{ fontSize: 13, color: '#374151', lineHeight: 1.6 }}>{c.comment}</div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Add comment */}
      <div style={{ background: '#fff', border: '1px solid #E2E8F0', borderRadius: 14, padding: '18px 20px', marginBottom: 20, animation: 'fadeIn 0.45s ease' }}>
        <div style={{ fontSize: 13, fontWeight: 700, color: '#374151', marginBottom: 14, display: 'flex', alignItems: 'center', gap: 7 }}>
          <MessageSquare size={14} color="#64748B" /> Add a comment
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
            <div>
              <label style={lbl}>Your name</label>
              <input value={commentName} onChange={e => setCommentName(e.target.value)} placeholder="Your name" style={inp} />
            </div>
          </div>
          <div>
            <label style={lbl}>Comment</label>
            <textarea value={comment} onChange={e => setComment(e.target.value)} rows={3}
              placeholder={fileIsVideo ? "e.g. At this point the logo should be bigger..." : fileIsImage ? "Click a spot on the image above to pin this comment, or just write a general note..." : "Ask a question, share feedback, or leave a note for the designer..."}
              style={{ ...inp, resize: 'vertical' as const }} />
          </div>
          {fileIsImage && (
            pendingPin ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: '#0D9488', fontWeight: 600 }}>
                📍 This comment will be pinned to that spot on the image
                <button onClick={() => setPendingPin(null)} style={{ background: 'none', border: 'none', color: '#94A3B8', fontSize: 12, cursor: 'pointer', textDecoration: 'underline', fontWeight: 400 }}>
                  Clear pin
                </button>
              </div>
            ) : (
              <div style={{ fontSize: 12, color: '#94A3B8' }}>
                Tip: click anywhere on the image above to pin your next comment to a specific spot.
              </div>
            )
          )}
          {fileIsVideo && (
            <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: '#374151', cursor: 'pointer' }}>
              <input type="checkbox" checked={tagTimecode} onChange={e => setTagTimecode(e.target.checked)} />
              Tag this comment to the current moment in the video
              {/* NOTE: this preview only updates when something else causes a
                  re-render (typing, toggling the checkbox) — it's not wired
                  to the video's own timeupdate event, so it can look briefly
                  stale while the video is playing. Not a functional bug: the
                  value actually SENT is always read fresh from
                  videoRef.current.currentTime at the moment "Send comment" is
                  clicked, in the mutation above — this label is a best-effort
                  hint, not the source of truth. */}
              {tagTimecode && videoRef.current && (
                <span style={{ fontSize: 11, color: '#1D4ED8', fontWeight: 700 }}>
                  (⏱ {formatTimecode(videoRef.current.currentTime)})
                </span>
              )}
            </label>
          )}
          {commentSent && (
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, color: '#166534', fontSize: 13, fontWeight: 600 }}>
              <CheckCircle size={14} /> Comment sent — your designer has been notified.
            </div>
          )}
          <button
            disabled={!comment.trim() || addComment.isPending}
            onClick={() => addComment.mutate()}
            style={{ alignSelf: 'flex-start', display: 'flex', alignItems: 'center', gap: 6, padding: '9px 18px', background: !comment.trim() ? '#94A3B8' : '#1B3A6B', color: '#fff', border: 'none', borderRadius: 8, fontSize: 14, fontWeight: 600, cursor: !comment.trim() ? 'default' : 'pointer', transition: 'background 0.15s' }}>
            {addComment.isPending ? <Loader2 size={14} style={{ animation: 'spin 1s linear infinite' }} /> : <MessageSquare size={14} />}
            Send comment
          </button>
        </div>
      </div>

      {/* Approve / Request changes */}
      <div style={{ background: '#fff', border: '1px solid #E2E8F0', borderRadius: 14, padding: '22px 24px', animation: 'fadeIn 0.5s ease' }}>
        <div style={{ fontSize: 15, fontWeight: 700, color: '#0F172A', marginBottom: 6 }}>Ready to decide?</div>
        <div style={{ fontSize: 13, color: '#64748B', marginBottom: 20, lineHeight: 1.6 }}>
          If you are happy with this proof, click <strong>Approve</strong>. If you need changes, click <strong>Request changes</strong> and describe what you'd like adjusted.
        </div>

        {/* Name + email collection */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 18 }}>
          <div>
            <label style={lbl}>Your full name *</label>
            <input value={clientName} onChange={e => setClientName(e.target.value)} placeholder="Your full name" style={inp} autoComplete="name" />
          </div>
          <div>
            <label style={lbl}>Your email address</label>
            <input type="email" value={clientEmail} onChange={e => setClientEmail(e.target.value)} placeholder="you@company.co.za" style={inp} autoComplete="email" />
          </div>
        </div>

        {/* Request changes form */}
        {showReject && (
          <div style={{ background: '#FEF9F0', border: '1px solid #FED7AA', borderRadius: 10, padding: '16px 18px', marginBottom: 18 }}>
            <label style={{ ...lbl, color: '#92400E' }}>Describe the changes you need *</label>
            <textarea
              value={rejectReason}
              onChange={e => setRejectReason(e.target.value)}
              rows={4}
              autoFocus
              placeholder="Please change the font colour to navy blue, adjust the logo size, and move the tagline to below the logo..."
              style={{ ...inp, resize: 'vertical' as const, border: '1.5px solid #FED7AA', background: '#fff' }}
            />
            <div style={{ fontSize: 12, color: '#92400E', marginTop: 6 }}>
              Be as specific as possible — your designer will use this feedback to create the next version.
            </div>
          </div>
        )}

        {errorMsg && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '10px 14px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626', marginBottom: 16 }}>
            <AlertTriangle size={14} /> {errorMsg}
          </div>
        )}

        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
          {/* Approve button */}
          <button
            disabled={!clientName.trim() || approve.isPending}
            onClick={() => approve.mutate()}
            style={{ flex: 1, minWidth: 160, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, padding: '14px 24px', background: !clientName.trim() ? '#94A3B8' : '#166534', color: '#fff', border: 'none', borderRadius: 10, fontSize: 15, fontWeight: 700, cursor: !clientName.trim() ? 'default' : 'pointer', transition: 'all 0.15s' }}>
            {approve.isPending
              ? <Loader2 size={16} style={{ animation: 'spin 1s linear infinite' }} />
              : <CheckCircle size={16} />}
            {approve.isPending ? 'Approving...' : 'Approve proof'}
          </button>

          {/* Request changes */}
          {!showReject ? (
            <button
              disabled={!clientName.trim()}
              onClick={() => setShowReject(true)}
              style={{ flex: 1, minWidth: 160, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, padding: '14px 24px', background: !clientName.trim() ? '#F8FAFC' : '#FEF2F2', color: !clientName.trim() ? '#94A3B8' : '#DC2626', border: `1.5px solid ${!clientName.trim() ? '#E2E8F0' : '#FECACA'}`, borderRadius: 10, fontSize: 15, fontWeight: 700, cursor: !clientName.trim() ? 'default' : 'pointer', transition: 'all 0.15s' }}>
              <XCircle size={16} /> Request changes
            </button>
          ) : (
            <div style={{ display: 'flex', gap: 8, flex: 1, minWidth: 160 }}>
              <button onClick={() => setShowReject(false)} style={{ flex: 1, padding: '14px', border: '1.5px solid #E2E8F0', borderRadius: 10, background: '#fff', fontSize: 14, cursor: 'pointer', fontWeight: 500, color: '#64748B' }}>
                Cancel
              </button>
              <button
                disabled={!rejectReason.trim() || !clientName.trim() || reject.isPending}
                onClick={() => reject.mutate()}
                style={{ flex: 2, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 7, padding: '14px', background: !rejectReason.trim() || !clientName.trim() ? '#94A3B8' : '#DC2626', color: '#fff', border: 'none', borderRadius: 10, fontSize: 14, fontWeight: 700, cursor: (!rejectReason.trim() || !clientName.trim()) ? 'default' : 'pointer' }}>
                {reject.isPending ? <Loader2 size={14} style={{ animation: 'spin 1s linear infinite' }} /> : <XCircle size={14} />}
                {reject.isPending ? 'Sending...' : 'Submit feedback'}
              </button>
            </div>
          )}
        </div>

        {/* Legal notice */}
        <div style={{ marginTop: 20, padding: '12px 14px', background: '#F8FAFC', border: '1px solid #E2E8F0', borderRadius: 8, fontSize: 11, color: '#94A3B8', lineHeight: 1.7 }}>
          By clicking Approve, you confirm that you are authorised to approve this proof on behalf of your organisation.
          Your approval will be recorded with your name, email address, IP address, and a timestamp as a legally binding record of sign-off.
        </div>
      </div>
    </Page>
  )
}

// ── Layout wrapper ─────────────────────────────────────────────────────────
function Page({ children, tenantName }: { children: React.ReactNode; tenantName?: string }) {
  return (
    <div style={{ minHeight: '100vh', background: '#F1F5F9', fontFamily: "'Inter', system-ui, sans-serif" }}>
      {/* Top bar */}
      <div style={{ background: '#1B3A6B', padding: '14px 24px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <div style={{ width: 30, height: 30, borderRadius: 7, background: 'rgba(255,255,255,0.15)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <FileText size={16} color="#fff" />
          </div>
          <span style={{ color: '#fff', fontWeight: 700, fontSize: 15 }}>
            {tenantName ? `${tenantName} — Proof Review` : 'Proof Review'}
          </span>
        </div>
        <span style={{ color: 'rgba(255,255,255,0.5)', fontSize: 12 }}>Powered by HandyFlow</span>
      </div>

      {/* Content */}
      <div style={{ maxWidth: 700, margin: '0 auto', padding: '28px 20px 60px' }}>
        {children}
      </div>
    </div>
  )
}

// ── Status result card ─────────────────────────────────────────────────────
function StatusCard({ icon, title, color, bg, children }: {
  icon: React.ReactNode; title: string; color: string; bg: string; children: React.ReactNode
}) {
  return (
    <div style={{ background: '#fff', border: '1px solid #E2E8F0', borderRadius: 14, padding: '48px 36px', textAlign: 'center' }}>
      <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 20 }}>
        <div style={{ width: 80, height: 80, borderRadius: '50%', background: bg, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          {icon}
        </div>
      </div>
      <h2 style={{ margin: '0 0 14px', fontSize: 22, fontWeight: 800, color }}>{title}</h2>
      <div style={{ fontSize: 14, color: '#64748B', lineHeight: 1.7, maxWidth: 480, margin: '0 auto' }}>{children}</div>
    </div>
  )
}
