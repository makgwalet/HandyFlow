// src/navigation/sectionBadges.ts
//
// Live counts for sidebar section badges. Each source is only fetched while
// the user is inside the module that owns it (`enabled`), so people outside
// Bookings never pay for its request.
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../api/client'
import type { ModuleSections } from './moduleSections'

/** Pending bookings, from the paged list's total (size=1: we only need the count). */
function usePendingBookingsCount(enabled: boolean): number {
  const { data = 0 } = useQuery<number>({
    queryKey: ['bookings-pending-count'],
    enabled,
    queryFn: async () => {
      const res = await apiClient.get('/api/v1/bookings?status=PENDING&size=1')
      const payload = res.data?.data ?? res.data
      return (payload?.totalElements ?? 0) as number
    },
    refetchInterval: 60_000,
  })
  return data
}

/** Badge text by section id for the given module (only sections with a positive count). */
export function useSectionBadges(config: ModuleSections): Record<string, string> {
  const pending = usePendingBookingsCount(config.moduleKey === 'bookings')
  const badges: Record<string, string> = {}
  if (config.moduleKey === 'bookings' && pending > 0) badges['bookings'] = pending > 99 ? '99+' : String(pending)
  return badges
}
