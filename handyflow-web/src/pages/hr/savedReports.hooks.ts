// Saved report views: one list per person, and the two things that change it.
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { unwrap } from "./reports.shared"
import { savedBody, type Colleague, type ReportKind, type SavedForm, type SavedKind, type SavedReport } from "./savedReports.logic"

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

export function useUpdateSavedReport() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (v: { id: string; form: SavedForm; kind: SavedKind }) => apiClient.put(`${URL}/${v.id}`, savedBody(v.form, v.kind)),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  })
}

export function useColleagues(enabled: boolean) {
  return useQuery<Colleague[]>({ queryKey: ["hr-saved-report-colleagues"], enabled, retry: false, staleTime: 60_000,
    queryFn: async () => unwrap<Colleague[]>(await apiClient.get(`${URL}/colleagues`)) })
}

export function useShareReport() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (v: { id: string; userIds: string[] }) => apiClient.put(`${URL}/${v.id}/shares`, { userIds: v.userIds }),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  })
}

export function useLeaveSharedReport() {
  const qc = useQueryClient()
  return useMutation({ mutationFn: (id: string) => apiClient.delete(`${URL}/${id}/shares/me`), onSuccess: () => qc.invalidateQueries({ queryKey: KEY }) })
}
