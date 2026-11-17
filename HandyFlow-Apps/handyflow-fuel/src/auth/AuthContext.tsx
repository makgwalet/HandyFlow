import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { configureApi } from "@/api/client";
import { fuelSessionSchema, loginFuel, type FuelSession, type LoginInput } from "@/api/auth";
import { clearSession, loadSession, saveSession, saveTenantSlug } from "./deviceStore";

type Status = "loading" | "signedOut" | "signedIn";

interface AuthValue {
  status: Status;
  session: FuelSession | null;
  /** True after the server said the sign-in expired while the phone still has unsent work. */
  expired: boolean;
  signIn: (input: LoginInput) => Promise<void>;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthValue | null>(null);

export const isExpired = (s: Pick<FuelSession, "expiresAt">, now = Date.now()): boolean => Date.parse(s.expiresAt) <= now;

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<Status>("loading");
  const [session, setSession] = useState<FuelSession | null>(null);
  const [expired, setExpired] = useState(false);
  const tokenRef = useRef<string | null>(null);

  const drop = useCallback(async (becauseExpired: boolean) => {
    tokenRef.current = null;
    setSession(null);
    setExpired(becauseExpired);
    setStatus("signedOut");
    await clearSession();
  }, []);

  useEffect(() => {
    configureApi({ token: () => tokenRef.current, onUnauthorized: () => void drop(true) });
  }, [drop]);

  useEffect(() => {
    (async () => {
      try {
        const stored = await loadSession();
        if (stored) {
          const parsed = fuelSessionSchema.safeParse(JSON.parse(stored));
          if (parsed.success && !isExpired(parsed.data)) {
            tokenRef.current = parsed.data.accessToken;
            setSession(parsed.data);
            setStatus("signedIn");
            return;
          }
          await clearSession();
        }
      } catch {
        await clearSession();
      }
      setStatus("signedOut");
    })();
  }, []);

  const signIn = useCallback(async (input: LoginInput) => {
    const next = await loginFuel(input);
    tokenRef.current = next.accessToken;
    await saveSession(JSON.stringify(next));
    await saveTenantSlug(input.tenantSlug.trim().toLowerCase());
    setExpired(false);
    setSession(next);
    setStatus("signedIn");
  }, []);

  // Local sign-out must always work. Queued writes stay on the phone under their owner's id and are sent on that person's next sign-in.
  const signOut = useCallback(() => drop(false), [drop]);

  const value = useMemo(() => ({ status, session, expired, signIn, signOut }), [status, session, expired, signIn, signOut]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
}
