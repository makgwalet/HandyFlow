import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { AppState } from "react-native";
import * as Crypto from "expo-crypto";
import { sendItem } from "./send";
import { useAuth } from "@/auth/AuthContext";
import { flush, type OutboxItem, type OutboxKind, type OutboxStore } from "./outbox";
import { openLocalDb, type KeyValueStore } from "./sqliteStore";

interface OutboxValue {
  ready: boolean;
  pending: OutboxItem[];
  failed: OutboxItem[];
  /** For saved data such as the last load list. Null until the phone's database is open. */
  kv: KeyValueStore | null;
  /** Saves the write on the phone first, then tries to send it. Resolves once it is safely stored. */
  enqueue: (kind: OutboxKind, payload: Record<string, unknown>) => Promise<OutboxItem>;
  syncNow: () => Promise<void>;
  /** Puts a refused item back in the queue (after the cause is fixed). */
  retry: (id: string) => Promise<void>;
  discard: (id: string) => Promise<void>;
}

const OutboxContext = createContext<OutboxValue | null>(null);
const RETRY_EVERY_MS = 30_000;

export function OutboxProvider({ children }: { children: React.ReactNode }) {
  const { session } = useAuth();
  const ownerId = session?.userId ?? "";
  const storeRef = useRef<OutboxStore | null>(null);
  const [kv, setKv] = useState<KeyValueStore | null>(null);
  const [ready, setReady] = useState(false);
  const [pending, setPending] = useState<OutboxItem[]>([]);
  const [failed, setFailed] = useState<OutboxItem[]>([]);
  const pendingCount = pending.length;

  const refresh = useCallback(async () => {
    const store = storeRef.current;
    if (!store || !ownerId) return;
    setPending(await store.pending(ownerId));
    setFailed(await store.failed(ownerId));
  }, [ownerId]);

  const syncNow = useCallback(async () => {
    const store = storeRef.current;
    if (!store || !ownerId) return;
    try {
      await flush(store, sendItem, ownerId);
    } finally {
      await refresh();
    }
  }, [refresh, ownerId]);

  useEffect(() => {
    let alive = true;
    (async () => {
      const db = await openLocalDb();
      storeRef.current = db.outbox;
      if (!alive) return;
      setKv(db.kv);
      setReady(true);
      await refresh();
      void syncNow();
    })();
    return () => { alive = false; };
  }, [refresh, syncNow]);

  // Try again whenever the app comes to the front, and every 30 s while something is waiting.
  useEffect(() => {
    const sub = AppState.addEventListener("change", (s) => { if (s === "active") void syncNow(); });
    const timer = setInterval(() => { if (pendingCount > 0) void syncNow(); }, RETRY_EVERY_MS);
    return () => { sub.remove(); clearInterval(timer); };
  }, [pendingCount, syncNow]);

  const enqueue = useCallback(async (kind: OutboxKind, payload: Record<string, unknown>) => {
    const store = storeRef.current;
    if (!store) throw new Error("The phone's queue is not ready yet. Try again in a moment.");
    const now = new Date().toISOString();
    const item: OutboxItem = { id: Crypto.randomUUID(), ownerId, kind, payload, recordedAt: now, status: "PENDING", attempts: 0, lastError: null, createdAt: now };
    await store.add(item);
    await refresh();
    void syncNow(); // do not make the driver wait for the network
    return item;
  }, [refresh, syncNow, ownerId]);

  const retry = useCallback(async (id: string) => {
    await storeRef.current?.update(id, { status: "PENDING", attempts: 0, lastError: null });
    await refresh();
    void syncNow();
  }, [refresh, syncNow]);

  const discard = useCallback(async (id: string) => {
    await storeRef.current?.remove(id);
    await refresh();
  }, [refresh]);

  const value = useMemo(() => ({ ready, pending, failed, kv, enqueue, syncNow, retry, discard }), [ready, pending, failed, kv, enqueue, syncNow, retry, discard]);
  return <OutboxContext.Provider value={value}>{children}</OutboxContext.Provider>;
}

export function useOutbox(): OutboxValue {
  const ctx = useContext(OutboxContext);
  if (!ctx) throw new Error("useOutbox must be used inside OutboxProvider");
  return ctx;
}
