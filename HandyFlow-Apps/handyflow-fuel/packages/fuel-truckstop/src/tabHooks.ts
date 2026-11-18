import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/api/client";
import { offeringListSchema, tabListSchema, tabSchema } from "./tabs";

const base = "/api/v1/fuel/truck-stop";

/** Tabs are money: they are read fresh and every change needs signal. Nothing is shown from an old copy. */
export function useTabs(status: "OPEN" | "SETTLED" | "VOID" = "OPEN") {
  const q = useQuery({ queryKey: ["fuel-truckstop-tabs", status], retry: false, queryFn: async () => tabListSchema.parse(await api<unknown>(`${base}/tabs?status=${status}&limit=100`)) });
  return { tabs: q.data ?? [], isLoading: q.isLoading, error: q.error instanceof Error ? q.error.message : null, refetch: q.refetch };
}

export function useTab(id: string) {
  const q = useQuery({ queryKey: ["fuel-truckstop-tab", id], retry: false, queryFn: async () => {
    const all = tabListSchema.parse(await api<unknown>(`${base}/tabs?limit=200`));
    const t = all.find((x) => x.id === id);
    if (!t) throw new Error("This tab is not on the list.");
    return t;
  } });
  return { tab: q.data ?? null, isLoading: q.isLoading, error: q.error instanceof Error ? q.error.message : null };
}

export function useOfferings() {
  const q = useQuery({ queryKey: ["fuel-truckstop-offerings"], retry: false, queryFn: async () => offeringListSchema.parse(await api<unknown>(`${base}/offerings`)) });
  return { offerings: (q.data ?? []).filter((o) => o.active && !o.serviceMissing), isLoading: q.isLoading, error: q.error instanceof Error ? q.error.message : null };
}

export function useRefreshTabs() {
  const qc = useQueryClient();
  return async () => {
    await qc.invalidateQueries({ queryKey: ["fuel-truckstop-tabs"] });
    await qc.invalidateQueries({ queryKey: ["fuel-truckstop-tab"] });
  };
}

export const parseTab = (raw: unknown) => tabSchema.parse(raw);
export const TAB_BASE = `${base}/tabs`;
export const STAY_BASE = `${base}/stays`;
