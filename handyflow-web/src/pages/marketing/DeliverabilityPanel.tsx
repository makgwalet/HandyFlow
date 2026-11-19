// src/pages/marketing/DeliverabilityPanel.tsx
import { useEffect, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import { CheckCircle, AlertTriangle, XCircle, Info } from 'lucide-react'
import { C, Card, Btn, Pill, inp, lbl, fmtDT } from './ui'

const STYLE: Record<string, { color: string; bg: string; icon: any }> = {
  PASS: { color: '#166534', bg: '#DCFCE7', icon: CheckCircle }, WARN: { color: '#92400E', bg: '#FEF3C7', icon: AlertTriangle },
  FAIL: { color: '#991B1B', bg: '#FEE2E2', icon: XCircle }, INFO: { color: '#475569', bg: '#F1F5F9', icon: Info },
}

export default function DeliverabilityPanel() {
  const qc = useQueryClient()
  const [domain, setDomain] = useState('')
  const [sel, setSel] = useState('')
  const [err, setErr] = useState('')

  const { data } = useQuery<any>({
    queryKey: ['marketing-deliverability'],
    queryFn: async () => (await apiClient.get('/api/v1/marketing/deliverability')).data?.data,
  })
  const selKey = (data?.selectors ?? []).join(',')
  useEffect(() => { if (data) { setDomain(data.domain ?? ''); setSel((data.selectors ?? []).join(', ')) } }, [data?.domain, selKey]) // eslint-disable-line react-hooks/exhaustive-deps

  const run = useMutation({
    mutationFn: async () => {
      try {
        await apiClient.put('/api/v1/marketing/deliverability/settings', { domain: domain.trim() || null, selectors: sel.split(/[\s,;]+/).filter(Boolean) })
      } catch (e: any) {
        // Non-admins can still re-run a check on the saved settings.
        if (e.response?.status !== 403) throw e
        if (domain.trim().toLowerCase() !== (data?.domain ?? '') || sel.split(/[\s,;]+/).filter(Boolean).join(',') !== selKey) throw e
      }
      return apiClient.post('/api/v1/marketing/deliverability/check')
    },
    onSuccess: () => { setErr(''); qc.invalidateQueries({ queryKey: ['marketing-deliverability'] }) },
    onError: (e: any) => setErr(e.response?.status === 403 ? 'Changing the sending domain needs the Marketing admin permission.' : (e.response?.data?.message || 'The check failed')),
  })

  const r = data?.result
  const ready = r && r.fail === 0

  return (
    <Card title="Sending domain (SPF, DKIM, DMARC)">
      <div style={{ fontSize: 12, color: C.text, lineHeight: 1.6, marginBottom: 12 }}>
        Receivers use DNS records on the domain you send from to decide whether your email is genuine. Gmail and Yahoo require SPF, DKIM and DMARC from bulk senders.
        Enter the domain in your campaigns’ From address, then run the check.
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
        <div><label style={lbl}>Sending domain</label><input style={inp} value={domain} placeholder="yourbusiness.co.za" onChange={e => setDomain(e.target.value)} /></div>
        <div><label style={lbl}>DKIM selector(s) (optional)</label><input style={inp} value={sel} placeholder="s1, selector2" onChange={e => setSel(e.target.value)} /></div>
      </div>
      <div style={{ fontSize: 11, color: C.mute, margin: '6px 0 10px' }}>Your email provider shows the DKIM selector in its domain-authentication screen. If you leave it blank, 12 common names are tried.</div>
      <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
        <Btn variant="primary" disabled={!domain.trim() || run.isPending} onClick={() => { setErr(''); run.mutate() }}>{run.isPending ? 'Checking…' : 'Save & check'}</Btn>
        {r && <span style={{ fontSize: 12, color: C.mute }}>Last checked {fmtDT(r.checkedAt)}</span>}
      </div>
      {err && <div style={{ marginTop: 10, padding: '9px 12px', background: '#FEF2F2', border: '1px solid #FECACA', borderRadius: 8, fontSize: 13, color: '#DC2626' }}>{err}</div>}

      {r && (
        <div style={{ marginTop: 16 }}>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 10, flexWrap: 'wrap' }}>
            <Pill color={ready ? '#166534' : '#991B1B'} bg={ready ? '#DCFCE7' : '#FEE2E2'}>{ready ? 'No blocking problems' : `${r.fail} problem(s) to fix`}</Pill>
            <span style={{ fontSize: 12, color: C.text }}>{r.pass} passed · {r.warn} warning(s) · {r.domain}</span>
          </div>
          <div style={{ border: `1px solid ${C.line}`, borderRadius: 10, overflow: 'hidden' }}>
            {r.checks.map((c: any, i: number) => { const s = STYLE[c.status] ?? STYLE.INFO; const I = s.icon; return (
              <div key={c.id} style={{ display: 'flex', gap: 10, padding: '11px 14px', borderTop: i ? '1px solid #F1F5F9' : 'none' }}>
                <I size={16} color={s.color} style={{ flexShrink: 0, marginTop: 1 }} />
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontSize: 13, fontWeight: 700, color: C.ink }}>{c.title}</div>
                  <div style={{ fontSize: 12, color: C.text, lineHeight: 1.5, marginTop: 2 }}>{c.detail}</div>
                  {c.fix && <div style={{ fontSize: 12, color: s.color, lineHeight: 1.5, marginTop: 3 }}><strong>Fix:</strong> {c.fix}</div>}
                </div>
              </div>) })}
          </div>
          <div style={{ fontSize: 11, color: C.mute, marginTop: 10, lineHeight: 1.6 }}>
            This reads your public DNS records only. It can’t prove your provider signs with the selector shown, or that the From address aligns with the signing domain. Changes to DNS can take a few hours to appear.
            Before a large send, email yourself and open Gmail’s <em>Show original</em>: it should say SPF, DKIM and DMARC all <strong>PASS</strong>.
          </div>
        </div>)}
    </Card>
  )
}