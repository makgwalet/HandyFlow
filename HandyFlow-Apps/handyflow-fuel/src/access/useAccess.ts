import { useQuery } from "@tanstack/react-query";
import { ApiError } from "@/api/client";
import { accessSchema, fetchAccess, type Access } from "@/api/access";
import { useAuth } from "@/auth/AuthContext";
import { useOutbox } from "@/offline/OutboxProvider";

/** The signed-in person's Fuel access. With no signal it uses the last answer, so the menu does not change when the phone goes offline. */
export function useAccess(): { access: Access | null; isLoading: boolean; error: string | null } {
  const { session } = useAuth();
  const { kv } = useOutbox();
  const key = `access.${session?.userId ?? ""}`;
  const q = useQuery({
    queryKey: ["fuel-access", session?.userId],
    enabled: !!session && !!kv,
    retry: false,
    staleTime: 5 * 60_000,
    queryFn: async (): Promise<Access> => {
      try {
        const a = await fetchAccess();
        await kv?.set(key, JSON.stringify(a));
        return a;
      } catch (e) {
        if (e instanceof ApiError && e.isNetwork) {
          const raw = await kv?.get(key);
          const saved = raw ? accessSchema.safeParse(JSON.parse(raw)) : null;
          if (saved?.success) return saved.data;
        }
        throw e;
      }
    },
  });
  return { access: q.data ?? null, isLoading: q.isLoading, error: q.error instanceof Error ? q.error.message : null };
}
