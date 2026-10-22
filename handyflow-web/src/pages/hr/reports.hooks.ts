// One query per report and period. Nothing here changes data.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { BASE, unwrap } from "./reports.shared"
import type { Period } from "./reports.logic"

export function useReport<T>(kind: "workforce" | "time" | "payroll" | "compliance", p: Period, enabled: boolean) {
  return useQuery<T>({
    queryKey: ["hr-report", kind, p.from, p.to], enabled, retry: false, staleTime: 30_000,
    queryFn: async () => unwrap<T>(await apiClient.get(`${BASE}/${kind}?from=${p.from}&to=${p.to}`)),
  })
}
