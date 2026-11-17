import { useQuery } from "@tanstack/react-query";
import { api, ApiError } from "@/api/client";
import { useAuth } from "@/auth/AuthContext";
import { useOutbox } from "@/offline/OutboxProvider";
import { sortTanks, tankListSchema, type Tank } from "./tanks";

interface Saved { at: string; tanks: Tank[] }

/** The tanks this person may see (the server already limits them by scope). With no signal it shows the last list, labelled with when. */
export function useTanks() {
  const { session } = useAuth();
  const { kv } = useOutbox();
  const key = `depot.tanks.${session?.userId ?? ""}`;
  const q = useQuery({
    queryKey: ["fuel-depot-tanks", session?.userId],
    enabled: !!session && !!kv,
    retry: false,
    queryFn: async (): Promise<Saved> => {
      try {
        const tanks = tankListSchema.parse(await api<unknown>("/api/v1/fuel/tanks"));
        await kv?.set(key, JSON.stringify({ at: new Date().toISOString(), tanks }));
        return { at: "", tanks };
      } catch (e) {
        if (e instanceof ApiError && e.isNetwork) {
          const raw = await kv?.get(key);
          if (raw) { try { return JSON.parse(raw) as Saved; } catch { /* show the error */ } }
        }
        throw e;
      }
    },
  });
  return {
    tanks: sortTanks(q.data?.tanks ?? []),
    savedAt: q.data?.at ? q.data.at : null,
    isLoading: q.isLoading || (!kv && !!session),
    error: q.error instanceof Error ? q.error.message : null,
    refetch: q.refetch,
  };
}
