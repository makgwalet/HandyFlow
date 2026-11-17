import { useCallback, useEffect, useState } from "react";
import { api } from "@/api/client";
import { useAuth } from "@/auth/AuthContext";
import { useOutbox } from "@/offline/OutboxProvider";
import { arrivalCheckSchema, type ArrivalCheck } from "./arrival";

/** The last arrival check for a load, kept on the phone so it can be used offline within its window. `run` asks the server for a new one. */
export function useArrival(loadId: string) {
  const { session } = useAuth();
  const { kv } = useOutbox();
  const key = `driver.arrival.${session?.userId ?? ""}.${loadId}`;
  const [check, setCheck] = useState<ArrivalCheck | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!kv) return;
    void kv.get(key).then((raw) => {
      if (!raw) return;
      const parsed = arrivalCheckSchema.safeParse(JSON.parse(raw));
      if (parsed.success) setCheck(parsed.data);
    }).catch(() => undefined);
  }, [kv, key]);

  const run = useCallback(async () => {
    setBusy(true); setError(null);
    try {
      const fresh = arrivalCheckSchema.parse(await api<unknown>(`/api/v1/fuel/driver/loads/${loadId}/arrival-check`, { method: "POST" }));
      setCheck(fresh);
      await kv?.set(key, JSON.stringify(fresh));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not check");
    } finally { setBusy(false); }
  }, [loadId, kv, key]);

  return { check, busy, error, run };
}
