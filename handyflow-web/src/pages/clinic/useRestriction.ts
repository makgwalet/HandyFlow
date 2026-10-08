// Is this patient's record restricted, and can the signed-in person open it now? (CLINIC-DEC-008, 009)
// Any failure to ask is treated as "not restricted": the server refuses restricted requests itself, so this only decides what to show.
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"

export interface Restriction {
  restricted: boolean
  category: string | null
  flaggedAt: string | null
  canAccess: boolean
  breakGlassUntil: string | null
}

export const OPEN: Restriction = { restricted: false, category: null, flaggedAt: null, canAccess: true, breakGlassUntil: null }

export const CATEGORIES: { value: string; label: string }[] = [
  { value: "MENTAL_HEALTH", label: "Mental health" },
  { value: "HIV", label: "HIV" },
  { value: "SEXUAL_REPRODUCTIVE", label: "Sexual and reproductive health" },
  { value: "STAFF", label: "Staff member" },
  { value: "VIP", label: "VIP / sensitive person" },
  { value: "OTHER", label: "Other" },
]

export const MIN_REASON = 10

/** Same bounds as the server (RestrictedRecordRules.reason): 10 to 500 characters once trimmed. */
export function reasonProblem(reason: string): string | null {
  const t = reason.trim()
  if (t.length < MIN_REASON) return `Give a reason of at least ${MIN_REASON} characters.`
  if (t.length > 500) return "Keep the reason under 500 characters."
  return null
}

export function categoryLabel(value: string | null): string {
  return CATEGORIES.find(c => c.value === value)?.label ?? "Restricted"
}

function normalise(d: any): Restriction {
  if (!d || typeof d.restricted !== "boolean") return OPEN
  return { restricted: d.restricted, category: d.category ?? null, flaggedAt: d.flaggedAt ?? null,
    canAccess: d.canAccess !== false, breakGlassUntil: d.breakGlassUntil ?? null }
}

export function useRestriction(patientId: string | undefined) {
  const qc = useQueryClient()
  const key = ["clinic-restriction", patientId]
  const query = useQuery<Restriction>({
    queryKey: key, enabled: !!patientId, retry: false, staleTime: 30_000,
    queryFn: async () => {
      try {
        const r = await apiClient.get(`/api/v1/clinic/patients/${patientId}/restriction`)
        return normalise(r?.data?.data)
      } catch { return OPEN }
    },
  })
  // Everything the record shows must be asked for again after the gate changes.
  const refresh = () => { qc.invalidateQueries({ queryKey: key }); qc.invalidateQueries() }
  const breakGlass = useMutation({
    mutationFn: async (reason: string) => { await apiClient.post(`/api/v1/clinic/patients/${patientId}/break-glass`, { reason: reason.trim() }) },
    onSuccess: refresh,
  })
  const flag = useMutation({
    mutationFn: async (v: { category: string; reason: string }) => { await apiClient.put(`/api/v1/clinic/patients/${patientId}/restriction`, { category: v.category, reason: v.reason.trim() }) },
    onSuccess: refresh,
  })
  const release = useMutation({
    mutationFn: async (reason: string) => { await apiClient.delete(`/api/v1/clinic/patients/${patientId}/restriction`, { data: { reason: reason.trim() } }) },
    onSuccess: refresh,
  })
  return { restriction: query.data ?? OPEN, breakGlass, flag, release }
}
