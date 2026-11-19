// src/pages/marketing/JourneyFlow.tsx
import { Mail, Clock, Flag, Zap, CheckCircle2, GitBranch } from 'lucide-react'
import { C, pct, fmtN } from './ui'

const connector = <div style={{ width: 2, height: 22, background: '#CBD5E1', margin: '0 auto' }} />

function Node({ icon, color, bg, title, sub, badge, dashed }: { icon: React.ReactNode; color: string; bg: string; title: string; sub?: string; badge?: React.ReactNode; dashed?: boolean }) {
  return (
    <div style={{ display: 'flex', gap: 12, alignItems: 'center', padding: '12px 14px', background: '#fff', border: `1.5px ${dashed ? 'dashed' : 'solid'} ${dashed ? color : C.line}`, borderRadius: 12, width: 380, maxWidth: '100%' }}>
      <div style={{ width: 36, height: 36, borderRadius: 9, background: bg, color, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>{icon}</div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 13, fontWeight: 700, color: C.ink, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{title}</div>
        {sub && <div style={{ fontSize: 11, color: C.text, marginTop: 2 }}>{sub}</div>}
      </div>
      {badge}
    </div>
  )
}

export default function JourneyFlow({ trigger, steps }: { trigger: string; steps: any[] }) {
  const waitingBadge = (n: number) => n > 0 ? <span style={{ fontSize: 10, fontWeight: 700, color: '#B45309', background: '#FEF3C7', padding: '2px 8px', borderRadius: 20, whiteSpace: 'nowrap' }}>{n} here</span> : null
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', padding: '16px 0', background: C.bg, border: `1px solid ${C.line}`, borderRadius: 12 }}>
      <Node icon={<Zap size={17} />} color={C.green} bg="#F0FDF4" title="Trigger" sub={trigger} />
      {steps.map(s => (
        <div key={s.index} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', width: '100%' }}>
          {connector}
          {s.type === 'EMAIL' && (
            <Node icon={<Mail size={17} />} color={C.primary} bg="#EFF6FF" title={s.summary}
              sub={s.metrics ? `${fmtN(s.metrics.sent)} sent · click ${pct(s.metrics.clickRate)} · open (approx.) ${pct(s.metrics.openRate)}` : 'Not sent yet'}
              badge={s.campaignStatus === 'PAUSED' ? <span style={{ fontSize: 10, fontWeight: 700, color: '#991B1B', background: '#FEE2E2', padding: '2px 8px', borderRadius: 20 }}>Paused</span> : waitingBadge(s.waiting)} />)}
          {s.type === 'WAIT' && <Node icon={<Clock size={17} />} color="#64748B" bg="#F1F5F9" title={s.summary} badge={waitingBadge(s.waiting)} />}
          {s.type === 'BRANCH' && (
            <Node icon={<GitBranch size={17} />} color="#0E7490" bg="#ECFEFF" title={s.summary ?? 'Branch'} sub="Decision: yes and no paths can jump forward" dashed badge={waitingBadge(s.waiting)} />)}
          {s.type === 'EXIT_IF' && (
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, maxWidth: '100%' }}>
              <Node icon={<Flag size={17} />} color={C.purple} bg="#F5F3FF" title={s.summary} sub="Goal check" dashed badge={waitingBadge(s.waiting)} />
              <span style={{ fontSize: 11, color: C.purple, fontWeight: 600, whiteSpace: 'nowrap' }}>Yes → leaves journey</span>
            </div>)}
        </div>))}
      {connector}
      <Node icon={<CheckCircle2 size={17} />} color={C.mute} bg="#F1F5F9" title="Journey complete" sub="Contacts also leave early if they opt out or are suppressed" />
    </div>
  )
}