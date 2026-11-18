import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "@/api/client";
import { useAuth } from "@/auth/AuthContext";
import { useOutbox } from "@/offline/OutboxProvider";
import { bayListSchema, occupancySchema, requestListSchema, stayListSchema } from "./stays";

export function failText(e: unknown): string {
  if (e instanceof ApiError && e.isNetwork) return "No signal. Nothing was saved. Try again when you have signal.";
  return e instanceof Error ? e.message : "Could not save";
}

/** The yard: stays and today's occupancy. With no signal it shows the last copy, labelled with when it was saved. */
export function useYard() {
  const { session } = useAuth();
  const { kv } = useOutbox();
  const key = `truckstop.yard.${session?.userId ?? ""}`;
  const q = useQuery({
    queryKey: ["fuel-truckstop-yard", session?.userId],
    enabled: !!session && !!kv,
    retry: false,
    queryFn: async () => {
      try {
        const stays = stayListSchema.parse(await api<unknown>("/api/v1/fuel/truck-stop/stays?limit=100"));
        const occupancy = occupancySchema.parse(await api<unknown>("/api/v1/fuel/truck-stop/stays/occupancy"));
        await kv?.set(key, JSON.stringify({ at: new Date().toISOString(), stays, occupancy }));
        return { at: "", stays, occupancy };
      } catch (e) {
        if (e instanceof ApiError && e.isNetwork) {
          const raw = await kv?.get(key);
          if (raw) { try { return JSON.parse(raw) as { at: string; stays: ReturnType<typeof stayListSchema.parse>; occupancy: ReturnType<typeof occupancySchema.parse> }; } catch { /* show the error */ } }
        }
        throw e;
      }
    },
  });
  return { stays: q.data?.stays ?? [], occupancy: q.data?.occupancy ?? null, savedAt: q.data?.at || null, isLoading: q.isLoading || (!kv && !!session), error: q.error instanceof Error ? q.error.message : null, refetch: q.refetch };
}

export function useBays() {
  const q = useQuery({ queryKey: ["fuel-truckstop-bays"], retry: false, queryFn: async () => bayListSchema.parse(await api<unknown>("/api/v1/fuel/truck-stop/bays")) });
  return { bays: q.data ?? [], isLoading: q.isLoading };
}

export function usePendingRequests() {
  const q = useQuery({ queryKey: ["fuel-truckstop-requests"], retry: false, queryFn: async () => requestListSchema.parse(await api<unknown>("/api/v1/fuel/truck-stop/stay-requests?status=PENDING&limit=50")) });
  return { requests: q.data ?? [], isLoading: q.isLoading, error: q.error instanceof Error ? q.error.message : null, refetch: q.refetch };
}

export function useRefreshYard() {
  const qc = useQueryClient();
  return async () => {
    await qc.invalidateQueries({ queryKey: ["fuel-truckstop-yard"] });
    await qc.invalidateQueries({ queryKey: ["fuel-truckstop-requests"] });
  };
}
