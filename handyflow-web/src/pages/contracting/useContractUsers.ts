// People in this organisation, for the escalation screens (reassigning, who covers, who to escalate to).
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../api/client'

export interface UserRow { id: string; firstName?: string; lastName?: string; email?: string }
export const userName = (u: UserRow) => [u.firstName, u.lastName].filter(Boolean).join(' ') || u.email || u.id

export function useUsers() {
  const q = useQuery<UserRow[]>({
    queryKey: ['contract-escalation', 'users'],
    queryFn: async () => { const d = (await apiClient.get<UserRow[]>('/api/v1/identity/users')).data; return Array.isArray(d) ? d : [] },
    staleTime: 5 * 60_000,
  })
  const users = q.data ?? []
  const nameOf = (id: string | null | undefined) => { const u = id ? users.find(x => x.id === id) : undefined; return u ? userName(u) : 'a colleague' }
  return { users, nameOf }
}
