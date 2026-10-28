// Saved report views: one list per person, and the two things that change it.
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { unwrap } from "./reports.shared"
import { savedBody, type ReportKind, type SavedForm, type SavedReport } from "./savedReports.logic"

const URL = "/api/v1/hr/saved-reports"
const KEY = ["hr-saved-reports"]

export function useSavedReports(enabled: boolean) {
  return useQuery<SavedReport[]>({ queryKey: KEY, enabled, retry: false, staleTime: 30_000, queryFn: async () => unwrap<SavedReport[]>(await apiClient.get(URL)) })
}

export function useSaveReport() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (v: { form: SavedForm; kind: ReportKind }) => apiClient.post(URL, savedBody(v.form, v.kind)),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  })
}

export function useDeleteSavedReport() {
  const qc = useQueryClient()
  return useMutation({ mutationFn: (id: string) => apiClient.delete(`${URL}/${id}`), onSuccess: () => qc.invalidateQueries({ queryKey: KEY }) })
}
