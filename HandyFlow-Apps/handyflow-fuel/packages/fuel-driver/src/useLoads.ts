import { useQuery } from "@tanstack/react-query";
import { api, ApiError } from "@/api/client";
import { useAuth } from "@/auth/AuthContext";
import { useOutbox } from "@/offline/OutboxProvider";
import { loadListSchema, sortLoads, type Load } from "./loads";

export interface LoadsState {
  loads: Load[];
  /** When the list shown was last fetched. Set when the phone could not reach the server and is showing the saved list. */
  savedAt: string | null;
  isLoading: boolean;
  error: string | null;
  refetch: () => Promise<unknown>;
}

interface Saved { at: string; loads: Load[] }

/** The signed-in driver's loads. With no signal it shows the last list that was fetched, labelled with when. */
export function useLoads(): LoadsState {
  const { session } = useAuth();
  const { kv } = useOutbox();
  const key = `driver.loads.${session?.userId ?? ""}`;

  const q = useQuery({
    queryKey: ["fuel-driver-loads", session?.userId],
    enabled: !!session && !!kv,
    retry: false,
    queryFn: async (): Promise<Saved> => {
      try {
        const loads = loadListSchema.parse(await api<unknown>("/api/v1/fuel/driver/loads"));
        const saved: Saved = { at: new Date().toISOString(), loads };
        await kv?.set(key, JSON.stringify(saved));
        return { ...saved, at: "" }; // "" = fresh from the server
      } catch (e) {
        if (e instanceof ApiError && e.isNetwork) {
          const raw = await kv?.get(key);
          if (raw) {
            try { return JSON.parse(raw) as Saved; } catch { /* fall through to the error */ }
          }
        }
        throw e;
      }
    },
  });

  return {
    loads: sortLoads(q.data?.loads ?? []),
    savedAt: q.data?.at ? q.data.at : null,
    isLoading: q.isLoading || (!kv && !!session),
    error: q.error ? (q.error instanceof Error ? q.error.message : "Could not load") : null,
    refetch: q.refetch,
  };
}
