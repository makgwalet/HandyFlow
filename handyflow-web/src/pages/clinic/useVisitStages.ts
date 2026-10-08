// The stages a visit type requires before signing (CLINIC-DEC-012), fetched once per visit type.
// While loading, or if the request fails, the default applies (Symptoms + Diagnosis): never less than the server enforces.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { DEFAULT_REQUIRED, requiredFromApi, type RequiredStages } from "./signRules"

export function useVisitStages(visitType: string): RequiredStages {
  const vt = (visitType || "CONSULTATION").trim().toUpperCase()
  const { data } = useQuery({
    queryKey: ["clinic-visit-stages", vt],
    staleTime: 5 * 60_000,
    retry: false,
    queryFn: async () => {
      const r = await apiClient.get(`/api/v1/clinic/visit-types/${encodeURIComponent(vt)}/stages`)
      return requiredFromApi(r.data?.data ?? r.data)
    },
  })
  return data ?? DEFAULT_REQUIRED
}
