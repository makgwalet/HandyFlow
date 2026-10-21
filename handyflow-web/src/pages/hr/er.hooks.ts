// Data the employee relations forms need to offer choices: people (as employee ids) and logins (for the case officer).
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import type { PersonOption } from "./er.shared"

type Loose = { data?: { data?: unknown } }
const asList = (r: Loose): Record<string, unknown>[] => {
  const d = r.data?.data ?? r.data
  const rows = Array.isArray(d) ? d : (d as { content?: unknown })?.content
  return Array.isArray(rows) ? (rows as Record<string, unknown>[]) : []
}

/** Employees as choices. Fails quietly (an employee without HR access gets an empty list). */
export function useEmployeeOptions() {
  return useQuery<PersonOption[]>({
    queryKey: ["er-employee-options"], retry: false, staleTime: 60_000,
    queryFn: async () => asList(await apiClient.get("/api/v1/hr/employees?size=500")).map(e => ({
      id: String(e.id),
      name: `${String(e.fullName ?? `${e.firstName ?? ""} ${e.lastName ?? ""}`.trim())}${e.employeeNumber ? ` · ${String(e.employeeNumber)}` : ""}`,
    })),
  })
}

/** Logins as choices, for assigning a case officer. */
export function useLoginOptions() {
  return useQuery<PersonOption[]>({
    queryKey: ["er-login-options"], retry: false, staleTime: 60_000,
    queryFn: async () => asList(await apiClient.get("/api/v1/identity/users")).map(u => ({
      id: String(u.id), name: `${String(u.firstName ?? "")} ${String(u.lastName ?? "")}`.trim() + (u.email ? ` · ${String(u.email)}` : ""),
    })),
  })
}
