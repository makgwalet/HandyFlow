// Clients, sites, technicians and vendors as flat lists, for the dropdowns and for turning ids into names.
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { pageOf } from "./kit"
import { whoFrom, type Who } from "./fm.logic"
import { usePermission } from "../../hooks/usePermission"

export const BASE = "/api/v1/facilitiesmanagement"
export interface FClient { id: string; clientCode: string | null; tradingName: string; registrationNumber: string | null; contactName: string | null; contactEmail: string | null; contactPhone: string | null; address: string | null; status: string; vatNumber: string | null }
export interface FSite { id: string; clientId: string; name: string; siteType: string; address: Record<string, string> | null; notes: string | null; status: string }
export interface FTech { id: string; name: string; contactPhone: string | null; specialization: string | null; active: boolean }
export interface FVendor { id: string; companyName: string; serviceType: string | null; active: boolean; onboardingStatus: string | null }

const all = <T,>(path: string) => async () => pageOf<T>(await apiClient.get(`${BASE}/${path}`, { params: { size: 500 } })).content

export function useLookups() {
  const clients = useQuery({ queryKey: ["fm-clients-all"], queryFn: all<FClient>("clients") })
  const sites = useQuery({ queryKey: ["fm-sites-all"], queryFn: all<FSite>("sites") })
  const c = clients.data ?? [], s = sites.data ?? []
  return {
    clients: c, sites: s, loading: clients.isLoading || sites.isLoading, failed: clients.isError || sites.isError,
    clientName: (id: string | null | undefined) => c.find(x => x.id === id)?.tradingName ?? "",
    siteName: (id: string | null | undefined) => s.find(x => x.id === id)?.name ?? "",
  }
}
export type Lookups = ReturnType<typeof useLookups>

/** Technicians and vendors are only needed when assigning work, so they load on demand. */
export function useAssignees(enabled: boolean) {
  const techs = useQuery({ enabled, queryKey: ["fm-technicians-all"], queryFn: all<FTech>("technicians") })
  const vendors = useQuery({ enabled, queryKey: ["fm-vendors-all"], queryFn: all<FVendor>("vendors") })
  return { techs: (techs.data ?? []).filter(t => t.active), vendors: (vendors.data ?? []).filter(v => v.active), loading: techs.isLoading || vendors.isLoading }
}

/** What this person may do to work orders, read from their authorities the way the server does. */
export function useWho(): Who & { admin: boolean } {
  const admin = usePermission("FACILITIESMANAGEMENT_ADMIN"), mg = usePermission("FACILITIESMANAGEMENT_MANAGE")
  const as = usePermission("FACILITIESMANAGEMENT_WORKORDER_ASSIGN"), te = usePermission("FACILITIESMANAGEMENT_TECHNICIAN")
  const has = (p: string) => ({ FACILITIESMANAGEMENT_ADMIN: admin, FACILITIESMANAGEMENT_MANAGE: mg, FACILITIESMANAGEMENT_WORKORDER_ASSIGN: as, FACILITIESMANAGEMENT_TECHNICIAN: te } as Record<string, boolean>)[p] ?? false
  return { ...whoFrom(has), admin }
}
