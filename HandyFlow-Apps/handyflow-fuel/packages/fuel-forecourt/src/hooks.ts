import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError } from "@/api/client";
import { useAuth } from "@/auth/AuthContext";
import { useOutbox } from "@/offline/OutboxProvider";
import { adjustmentListSchema, pumpListSchema, shiftListSchema, shiftSchema } from "./shifts";

/** Message for a failed save. Shifts are saved online only, so no signal says exactly that. */
export function failText(e: unknown): string {
  if (e instanceof ApiError && e.isNetwork) return "No signal. Nothing was saved. Try again when you have signal.";
  return e instanceof Error ? e.message : "Could not save";
}

/** Shifts, newest first. With no signal it shows the last list, labelled with when it was saved. */
export function useShifts() {
  const { session } = useAuth();
  const { kv } = useOutbox();
  const key = `forecourt.shifts.${session?.userId ?? ""}`;
  const q = useQuery({
    queryKey: ["fuel-forecourt-shifts", session?.userId],
    enabled: !!session && !!kv,
    retry: false,
    queryFn: async () => {
      try {
        const shifts = shiftListSchema.parse(await api<unknown>("/api/v1/fuel/forecourt/shifts?limit=30"));
        await kv?.set(key, JSON.stringify({ at: new Date().toISOString(), shifts }));
        return { at: "", shifts };
      } catch (e) {
        if (e instanceof ApiError && e.isNetwork) {
          const raw = await kv?.get(key);
          if (raw) { try { return JSON.parse(raw) as { at: string; shifts: ReturnType<typeof shiftListSchema.parse> }; } catch { /* show the error */ } }
        }
        throw e;
      }
    },
  });
  return { shifts: q.data?.shifts ?? [], savedAt: q.data?.at || null, isLoading: q.isLoading || (!kv && !!session), error: q.error instanceof Error ? q.error.message : null, refetch: q.refetch };
}

/** One shift, fresh from the server. A shift's figures are not shown from an old copy. */
export function useShift(id: string) {
  const q = useQuery({ queryKey: ["fuel-forecourt-shift", id], retry: false, queryFn: async () => shiftSchema.parse(await api<unknown>(`/api/v1/fuel/forecourt/shifts/${id}`)) });
  return { shift: q.data ?? null, isLoading: q.isLoading, error: q.error instanceof Error ? q.error.message : null, refetch: q.refetch };
}

export function usePumps() {
  const q = useQuery({ queryKey: ["fuel-forecourt-pumps"], retry: false, queryFn: async () => pumpListSchema.parse(await api<unknown>("/api/v1/fuel/forecourt/pumps")) });
  return { pumps: q.data ?? [], isLoading: q.isLoading, error: q.error instanceof Error ? q.error.message : null };
}

export function useAdjustments(shiftId: string) {
  const q = useQuery({ queryKey: ["fuel-forecourt-adjustments", shiftId], retry: false, queryFn: async () => adjustmentListSchema.parse(await api<unknown>(`/api/v1/fuel/forecourt/shifts/${shiftId}/adjustments`)) });
  return { adjustments: q.data ?? [], isLoading: q.isLoading };
}

export function useRefreshShifts() {
  const qc = useQueryClient();
  return async (shiftId?: string) => {
    await qc.invalidateQueries({ queryKey: ["fuel-forecourt-shifts"] });
    await qc.invalidateQueries({ queryKey: ["fuel-forecourt-pumps"] });
    if (shiftId) {
      await qc.invalidateQueries({ queryKey: ["fuel-forecourt-shift", shiftId] });
      await qc.invalidateQueries({ queryKey: ["fuel-forecourt-adjustments", shiftId] });
    }
  };
}

/** Supervisor decisions are online only: a decision about money is never queued on a phone to be replayed later. */
export const SUPERVISOR_BASE = "/api/v1/fuel/forecourt";
