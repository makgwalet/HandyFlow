import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { AppState } from "react-native";
import * as Crypto from "expo-crypto";
import { sendOutboxItem } from "@/api/guardWork";
import { useAuth } from "@/auth/AuthContext";
import type { Context } from "./payloads";
import { flush, type OutboxItem, type OutboxStore } from "./outbox";
import { openSqliteOutbox } from "./sqliteStore";

interface OutboxValue {
  ready: boolean;
  pendingCount: number;
  /** What is waiting to be sent, so a screen can show "saved, will send" instead of offering the same action again. */
  pending: OutboxItem[];
  failed: OutboxItem[];
  /** Saves the write on the phone first, then tries to send it. Resolves once it is safely stored. */
  /** Who is signed in plus id and clock sources, for building queue items. */
  context: () => Context;
  enqueue: (item: OutboxItem) => Promise<void>;
  syncNow: () => Promise<void>;
  dismissFailed: (id: string) => Promise<void>;
}

const OutboxContext = createContext<OutboxValue | null>(null);
const RETRY_EVERY_MS = 30_000;

export function OutboxProvider({ children }: { children: React.ReactNode }) {
  const { session } = useAuth();
  const guardId = session?.guardId ?? "";
  const storeRef = useRef<OutboxStore | null>(null);
  const [ready, setReady] = useState(false);
  const [pending, setPending] = useState<OutboxItem[]>([]);
  const pendingCount = pending.length;
  const [failed, setFailed] = useState<OutboxItem[]>([]);

  const refresh = useCallback(async () => {
    const store = storeRef.current;
    if (!store || !guardId) return;
    setPending(await store.pending(guardId));
    setFailed(await store.failed(guardId));
  }, [guardId]);

  const syncNow = useCallback(async () => {
    const store = storeRef.current;
    if (!store || !guardId) return;
    try {
      await flush(store, sendOutboxItem, guardId);
    } finally {
      await refresh();
    }
  }, [refresh, guardId]);

  useEffect(() => {
    let alive = true;
    (async () => {
      storeRef.current = await openSqliteOutbox();
      if (!alive) return;
      setReady(true);
      await refresh();
      void syncNow();
    })();
    return () => {
      alive = false;
    };
  }, [refresh, syncNow]);

  // Try again whenever the app comes to the front, and every 30 s while something is waiting.
  useEffect(() => {
    const sub = AppState.addEventListener("change", (s) => {
      if (s === "active") void syncNow();
    });
    const timer = setInterval(() => {
      if (pendingCount > 0) void syncNow();
    }, RETRY_EVERY_MS);
    return () => {
      sub.remove();
      clearInterval(timer);
    };
  }, [pendingCount, syncNow]);

  const enqueue = useCallback(
    async (item: OutboxItem) => {
      const store = storeRef.current;
      if (!store) throw new Error("The phone's queue is not ready yet. Try again in a moment.");
      await store.add(item);
      await refresh();
      void syncNow(); // do not make the guard wait for the network
    },
    [refresh, syncNow],
  );

  const dismissFailed = useCallback(
    async (id: string) => {
      await storeRef.current?.remove(id);
      await refresh();
    },
    [refresh],
  );

  const context = useCallback(
    (): Context => ({ guardId, newId: () => Crypto.randomUUID(), now: () => new Date() }),
    [guardId],
  );

  const value = useMemo(
    () => ({ ready, pendingCount, pending, failed, context, enqueue, syncNow, dismissFailed }),
    [ready, pendingCount, pending, failed, context, enqueue, syncNow, dismissFailed],
  );
  return <OutboxContext.Provider value={value}>{children}</OutboxContext.Provider>;
}

export function useOutbox(): OutboxValue {
  const ctx = useContext(OutboxContext);
  if (!ctx) throw new Error("useOutbox must be used inside OutboxProvider");
  return ctx;
}
