import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { ApiError, configureApi, type RefreshOutcome } from "@/api/client";
import { accessExpiring, fuelSessionSchema, loginFuel, logoutFuel, refreshSession, refreshUsable, type FuelSession, type LoginInput } from "@/api/auth";
import { unregisterPushToken } from "@/api/push";
import { clearSession, getDeviceId, loadSession, saveSession, saveTenantSlug } from "./deviceStore";

type Status = "loading" | "signedOut" | "signedIn";

interface AuthValue {
  status: Status;
  session: FuelSession | null;
  /** True after a sign-in ended while the phone may still hold unsent work. */
  expired: boolean;
  /** The company's Fuel subscription is not active (the server answered 402). */
  subscriptionInactive: boolean;
  signIn: (input: LoginInput) => Promise<void>;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<Status>("loading");
  const [session, setSession] = useState<FuelSession | null>(null);
  const [expired, setExpired] = useState(false);
  const [subscriptionInactive, setSubscriptionInactive] = useState(false);
  const sessionRef = useRef<FuelSession | null>(null);
  const refreshing = useRef<Promise<RefreshOutcome> | null>(null);

  const adopt = useCallback((s: FuelSession | null) => {
    sessionRef.current = s;
    setSession(s);
  }, []);

  const drop = useCallback(async (becauseExpired: boolean) => {
    adopt(null);
    setExpired(becauseExpired);
    setStatus("signedOut");
    await clearSession();
  }, [adopt]);

  /**
   * One refresh at a time: two requests that both get a 401 share one call, because a second call with the same
   * refresh token would look like theft to the server and end the session.
   * The new refresh token is saved before the new access token is used.
   */
  const refresh = useCallback((): Promise<RefreshOutcome> => {
    if (refreshing.current) return refreshing.current;
    const run = (async (): Promise<RefreshOutcome> => {
      const current = sessionRef.current;
      if (!current || !refreshUsable(current)) return "expired";
      try {
        const next = await refreshSession(current, await getDeviceId());
        await saveSession(JSON.stringify(next));
        adopt(next);
        return "refreshed";
      } catch (e) {
        if (e instanceof ApiError && (e.isNetwork || e.status >= 500)) return "offline";
        return "expired";
      }
    })().finally(() => { refreshing.current = null; });
    refreshing.current = run;
    return run;
  }, [adopt]);

  useEffect(() => {
    configureApi({
      token: () => sessionRef.current?.accessToken ?? null,
      onUnauthorized: () => void drop(true),
      onPaymentRequired: () => setSubscriptionInactive(true),
      refresh,
    });
  }, [drop, refresh]);

  useEffect(() => {
    (async () => {
      try {
        const stored = await loadSession();
        if (stored) {
          const parsed = fuelSessionSchema.safeParse(JSON.parse(stored));
          // An out-of-date access token is fine while the refresh token lives: the first request renews it,
          // so a phone started without signal still opens with its saved lists and queue.
          if (parsed.success && refreshUsable(parsed.data)) {
            adopt(parsed.data);
            setStatus("signedIn");
            if (accessExpiring(parsed.data)) void refresh();
            return;
          }
          await clearSession();
        }
      } catch {
        await clearSession();
      }
      setStatus("signedOut");
    })();
  }, [adopt, refresh]);

  const signIn = useCallback(async (input: LoginInput) => {
    const next = await loginFuel(input, await getDeviceId());
    await saveSession(JSON.stringify(next));
    await saveTenantSlug(input.tenantSlug.trim().toLowerCase());
    setExpired(false);
    setSubscriptionInactive(false);
    adopt(next);
    setStatus("signedIn");
  }, [adopt]);

  // Local sign-out must always work. Queued writes stay on the phone under their owner's id and are sent on that person's next sign-in.
  const signOut = useCallback(async () => {
    const current = sessionRef.current;
    // Stop this person's alerts on this phone while the sign-in still works. Offline, the next person to sign in here takes the token over.
    if (current) { try { await unregisterPushToken(await getDeviceId()); } catch { /* best effort */ } }
    if (current) { try { await logoutFuel(current.refreshToken); } catch { /* offline: the server-side token expires on its own */ } }
    await drop(false);
  }, [drop]);

  const value = useMemo(() => ({ status, session, expired, subscriptionInactive, signIn, signOut }), [status, session, expired, subscriptionInactive, signIn, signOut]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
}
