// src/pages/marketing/SettingsTab.tsx
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { Mail, MessageSquare, Smartphone, ShieldCheck, Webhook, Link2, ChevronDown, ChevronUp } from 'lucide-react'
import SuppressionsTab from './SuppressionsTab'
import { C, Card, Pill, Switch, Btn } from './ui'
import DeliverabilityPanel from './DeliverabilityPanel'

const Row = ({ icon, title, text, right }: { icon?: React.ReactNode; title: string; text?: string; right?: React.ReactNode }) => (
  <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '12px 0', borderTop: '1px solid #F1F5F9' }}>
    {icon && <div style={{ width: 34, height: 34, borderRadius: 9, background: C.bg, display: 'flex', alignItems: 'center', justifyContent: 'center', color: C.text, flexShrink: 0 }}>{icon}</div>}
    <div style={{ flex: 1, minWidth: 0 }}>
      <div style={{ fontSize: 13, fontWeight: 600, color: C.ink }}>{title}</div>
      {text && <div style={{ fontSize: 12, color: C.text, marginTop: 2, lineHeight: 1.5 }}>{text}</div>}
    </div>
    {right}
  </div>
)

export default function SettingsTab() {
  const qc = useQueryClient()
  const [showSupp, setShowSupp] = useState(false)
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null)
  const [approvers, setApprovers] = useState('')
  const [apMsg, setApMsg] = useState<{ ok: boolean; text: string } | null>(null)
  const { data: apList } = useQuery<string[]>({ queryKey: ['marketing-approvers'], queryFn: async () => (await apiClient.get('/api/v1/marketing/notifications/approvers')).data?.data ?? [] })
  useEffect(() => { if (apList) setApprovers(apList.join('\n')) }, [apList])
  const saveApprovers = useMutation({
    mutationFn: () => apiClient.put('/api/v1/marketing/notifications/approvers', approvers.split(/[\s,;]+/).filter(Boolean)),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['marketing-approvers'] }); setApMsg({ ok: true, text: 'Saved.' }) },
    onError: (e: any) => setApMsg({ ok: false, text: e.response?.status === 403 ? 'Needs the Marketing admin permission.' : (e.response?.data?.message || 'Failed to save') }),
  })

  const { data: settings } = useQuery<any>({ queryKey: ['marketing-settings'], queryFn: async () => (await apiClient.get('/api/v1/marketing/settings')).data?.data })
  const { data: summary } = useQuery<any>({ queryKey: ['marketing-summary'], queryFn: async () => { const r = await apiClient.get('/api/v1/marketing/summary'); return r.data?.data ?? r.data } })

  const save = useMutation({
    mutationFn: (s: { requireApproval: boolean; allowSelfApproval: boolean }) => apiClient.put('/api/v1/marketing/settings', s),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['marketing-settings'] }); setMsg({ ok: true, text: 'Saved.' }) },
    onError: (e: any) => setMsg({ ok: false, text: e.response?.status === 403 ? 'Changing approval settings needs the Marketing admin permission.' : (e.response?.data?.message || 'Failed to save') }),
  })
  const requireApproval = !!settings?.requireApproval, selfApprove = !!settings?.allowSelfApproval
  const base = ((apiClient.defaults as any)?.baseURL as string) || window.location.origin

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 18, maxWidth: 860 }}>
      <Card title="Channels">
        <Row icon={<Mail size={16} />} title="Email" text="Campaigns, journeys and test sends." right={<Pill color="#166534" bg="#DCFCE7">Active</Pill>} />
        <Row icon={<MessageSquare size={16} />} title="SMS" text="No SMS provider is connected." right={<><Pill color="#64748B" bg="#F1F5F9">Not available</Pill><Switch on={false} disabled /></>} />
        <Row icon={<Smartphone size={16} />} title="WhatsApp" text="No WhatsApp provider is connected." right={<><Pill color="#64748B" bg="#F1F5F9">Not available</Pill><Switch on={false} disabled /></>} />
      </Card>

      <Card title="Approval">
        <div style={{ fontSize: 12, color: C.text, marginBottom: 4 }}>Control who can send. Changing these needs the Marketing admin permission.</div>
        <Row title="Require approval before sending" text="Every campaign version must be approved by a Marketing admin. Editing the content voids the approval. Applies to scheduled sends too."
          right={<Switch on={requireApproval} disabled={!settings || save.isPending} onChange={v => save.mutate({ requireApproval: v, allowSelfApproval: v ? selfApprove : false })} />} />
        <Row title="Allow self-approval" text="Lets the person who submitted a campaign also approve it. Off by default (four-eyes). Turn on only if you have a single admin."
          right={<Switch on={selfApprove} disabled={!settings || !requireApproval || save.isPending} onChange={v => save.mutate({ requireApproval, allowSelfApproval: v })} />} />
        {msg && <div style={{ marginTop: 8, fontSize: 12, color: msg.ok ? '#166534' : C.red }}>{msg.text}</div>}
      </Card>

            <Card title="Approver notifications">
        <div style={{ fontSize: 12, color: C.text, marginBottom: 8, lineHeight: 1.5 }}>These addresses are emailed when a campaign is submitted for review. Whoever submitted it is emailed when it is approved or sent back. One address per line.</div>
        <textarea value={approvers} onChange={e => setApprovers(e.target.value)} rows={4} placeholder="admin@yourbusiness.co.za"
          style={{ width: '100%', padding: '9px 12px', border: `1.5px solid ${C.line}`, borderRadius: 8, fontSize: 13, boxSizing: 'border-box', fontFamily: 'inherit' }} />
        <div style={{ display: 'flex', gap: 10, alignItems: 'center', marginTop: 8 }}>
          <Btn variant="primary" disabled={saveApprovers.isPending} onClick={() => { setApMsg(null); saveApprovers.mutate() }}>{saveApprovers.isPending ? 'Saving…' : 'Save'}</Btn>
          {apMsg && <span style={{ fontSize: 12, color: apMsg.ok ? '#166534' : C.red }}>{apMsg.text}</span>}
        </div>
      </Card>

      <Card title="Compliance (POPIA)">
        <Row icon={<ShieldCheck size={16} />} title="Unsubscribe link and one-click header" text="Added to every campaign and journey email, and cannot be turned off." right={<Pill color="#166534" bg="#DCFCE7">Always on</Pill>} />
        <Row icon={<ShieldCheck size={16} />} title="Consent evidence" text="Every opt-in, opt-out and suppression is recorded per contact. See a contact’s history in the Contacts tab." right={<Pill color="#166534" bg="#DCFCE7">Always on</Pill>} />
        <Row icon={<ShieldCheck size={16} />} title="Suppression list" text={`${summary?.activeSuppressions ?? 0} address(es) suppressed. Suppressed addresses are checked at launch and again just before every send.`}
          right={<Btn onClick={() => setShowSupp(s => !s)}>{showSupp ? <>Hide <ChevronUp size={13} /></> : <>Manage <ChevronDown size={13} /></>}</Btn>} />
        {showSupp && <div style={{ marginTop: 12, paddingTop: 16, borderTop: '1px solid #F1F5F9' }}><SuppressionsTab /></div>}
      </Card>

      <Card title="Delivery webhooks">
        <div style={{ fontSize: 12, color: C.text, lineHeight: 1.6, marginBottom: 6 }}>
          Point your email provider’s event webhooks here to get confirmed delivery, bounces after acceptance and spam complaints. Without them, those figures stay at zero and the deliverability auto-pause cannot trigger.
          The shared secret is set on the server as <code>marketing.webhook.secret</code> (Mailgun also uses <code>marketing.webhook.mailgun-signing-key</code>).
        </div>
        {['mailgun', 'postmark', 'generic'].map(p => (
          <Row key={p} icon={<Webhook size={16} />} title={p.charAt(0).toUpperCase() + p.slice(1)} right={<code style={{ fontSize: 11, color: C.text, background: C.bg, padding: '4px 8px', borderRadius: 6 }}>{base}/api/v1/marketing/webhooks/{p}</code>} />))}
        <Row icon={<Link2 size={16} />} title="Provider integrations" text="Only generic SMTP and the webhook parsers above are supported today. Direct Mailchimp and SendGrid integrations aren’t built." />
      </Card>
    </div>
  )
}