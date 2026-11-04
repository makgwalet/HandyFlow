// src/pages/projects/NotificationSettingsPage.tsx
// Projects > My notifications: which Projects events also email you. The bell always shows them.
import { useEffect, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Bell, Mail } from 'lucide-react'
import { apiClient } from '../../api/client'
import { toast } from '../../store/toast.store'
import { errorText } from '../../api/projectDocs'
import { Btn, Empty, ErrorLine, card, list, muted, td, th } from './tabs/pmKit'

interface Setting { event: string; label: string; email: boolean; emailByDefault: boolean }

export function NotificationSettingsPage() {
  const qc = useQueryClient()
  const { data = [], isLoading, isError } = useQuery<Setting[]>({ queryKey: ['pm-notification-settings'], queryFn: async () => list<Setting>(await apiClient.get('/api/v1/projects/notification-settings')) })
  const [draft, setDraft] = useState<Record<string, boolean>>({})
  const [busy, setBusy] = useState(false)
  useEffect(() => { setDraft(Object.fromEntries(data.map(s => [s.event, s.email]))) }, [data])
  const changed = data.filter(s => draft[s.event] !== undefined && draft[s.event] !== s.email)

  const save = async () => {
    setBusy(true)
    try {
      const r = await apiClient.put('/api/v1/projects/notification-settings', Object.fromEntries(changed.map(s => [s.event, draft[s.event]])))
      qc.setQueryData(['pm-notification-settings'], list<Setting>(r)); toast.success('Notification settings saved')
    } catch (e) { toast.error(await errorText(e, 'Could not save your settings')) } finally { setBusy(false) }
  }

  if (isLoading) return <Empty>Loading…</Empty>
  if (isError) return <ErrorLine text="Could not load your notification settings." />
  return (
    <div style={{ maxWidth: 760 }}>
      <div style={{ ...muted, marginBottom: 12, lineHeight: 1.6 }}>
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}><Bell size={12} /> Every Projects event below appears in your notification bell.</span>{' '}
        Choose which ones also send you an email. You are never notified about something you did yourself.
      </div>
      <div style={{ ...card, padding: 0 }}>
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead><tr><th style={th}>When</th><th style={{ ...th, textAlign: 'center', width: 90 }}><span style={{ display: 'inline-flex', gap: 4, alignItems: 'center' }}><Mail size={11} /> Email</span></th></tr></thead>
          <tbody>{data.map(s => (
            <tr key={s.event}>
              <td style={td}>{s.label}{!s.emailByDefault && <span style={muted}> · off unless you switch it on</span>}</td>
              <td style={{ ...td, textAlign: 'center' }}>
                <input type="checkbox" aria-label={`Email: ${s.label}`} checked={!!draft[s.event]} onChange={e => setDraft(d => ({ ...d, [s.event]: e.target.checked }))}
                  style={{ width: 16, height: 16, cursor: 'pointer' }} />
              </td>
            </tr>
          ))}</tbody>
        </table>
      </div>
      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 12 }}>
        <Btn disabled={!changed.length || busy} onClick={() => setDraft(Object.fromEntries(data.map(s => [s.event, s.email])))}>Undo changes</Btn>
        <Btn kind="primary" disabled={!changed.length || busy} onClick={save}>{busy ? 'Saving…' : changed.length ? `Save ${changed.length} change${changed.length === 1 ? '' : 's'}` : 'Saved'}</Btn>
      </div>
      <div style={{ ...muted, marginTop: 10 }}>Switching email off for all notifications in your account settings also stops these emails.</div>
    </div>
  )
}
