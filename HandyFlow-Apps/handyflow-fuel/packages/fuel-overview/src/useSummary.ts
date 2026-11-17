import { useQuery } from "@tanstack/react-query";
import { api, ApiError } from "@/api/client";
import { useAuth } from "@/auth/AuthContext";
import { useOutbox } from "@/offline/OutboxProvider";
import { summarySchema, type Summary } from "./summary";

interface Saved { at: string; summary: Summary }

/** This month's figures from the server. With no signal it shows the last copy, labelled with when it was saved. */
export function useSummary() {
  const { session } = useAuth();
  const { kv } = useOutbox();
  const key = `overview.summary.${session?.userId ?? ""}`;
  const q = useQuery({
    queryKey: ["fuel-overview-summary", session?.userId],
    enabled: !!session && !!kv,
    retry: false,
    queryFn: async (): Promise<Saved> => {
      try {
        const summary = summarySchema.parse(await api<unknown>("/api/v1/fuel/dashboard-summary"));
        await kv?.set(key, JSON.stringify({ at: new Date().toISOString(), summary }));
        return { at: "", summary };
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
    summary: q.data?.summary ?? null,
    savedAt: q.data?.at ? q.data.at : null,
    isLoading: q.isLoading || (!kv && !!session),
    error: q.error instanceof Error ? q.error.message : null,
    refetch: q.refetch,
  };
}
