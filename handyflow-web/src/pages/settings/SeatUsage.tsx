import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../api/client'

export interface SeatMeter { used: number; limit: number | null; percent: number | null; level: string }
export interface SeatSnapshot { tier: string | null; limitsProvisional: boolean; seats: SeatMeter; activeUsers: number; pendingInvitations: number }

/** The sentence under the seat count. Null when there is nothing worth saying. */
export function seatMessage(s: SeatSnapshot): string | null {
  const { limit } = s.seats
  if (limit == null) return null
  switch (s.seats.level) {
    case 'AT_LIMIT': return 'Every seat is taken. Free a seat (deactivate a user or cancel an invitation) or upgrade before inviting someone else.'
    case 'CLOSE': return 'Nearly all seats are in use.'
    case 'NEAR': return 'Most seats are in use.'
    default: return null
  }
}

export default function SeatUsage() {
  const { data } = useQuery<SeatSnapshot | null>({
    queryKey: ['team-seat-usage'],
    queryFn: async () => (await apiClient.get('/api/v1/usage')).data?.data ?? null,
    retry: false,
  })
  // No tier assigned yet means no limits to show; stay out of the way.
  if (!data || !data.tier || data.seats.limit == null) return null
  const msg = seatMessage(data)
  const warn = data.seats.level !== 'NONE'
  return (
    <div role="status" data-testid="seat-usage"
      style={{ marginBottom: 16, padding: '12px 16px', borderRadius: 12, border: '1px solid var(--hf-border)', background: warn ? 'var(--hf-warning-soft)' : 'var(--hf-surface-muted)', fontSize: 13, color: 'var(--hf-text)' }}>
      <strong>{data.seats.used} of {data.seats.limit} seats in use</strong>
      <span style={{ color: 'var(--hf-text-muted)' }}> · {data.activeUsers} active, {data.pendingInvitations} invited and not yet accepted{data.limitsProvisional ? ' · limits provisional' : ''}</span>
      {msg && <div style={{ marginTop: 4 }}>{msg}</div>}
    </div>
  )
}
