// Reading the deadline rules; shared by the deadline screens so the component files export only components.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { BASE } from "./lookups"
import type { DeadlineRule } from "./legal.logic"

export const unwrap = <T,>(r: { data?: { data?: T } | T }): T => ((r.data as { data?: T } | undefined)?.data ?? r.data) as T

export function useRules(includeInactive = false) {
  return useQuery({
    queryKey: ["lp-deadline-rules", includeInactive],
    queryFn: async () => unwrap<DeadlineRule[]>(await apiClient.get(`${BASE}/deadlines/rules`, { params: { includeInactive } })) ?? [],
  })
}
