import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { configureApi } from "@/api/client";
import { GuardSession, guardSessionSchema, loginGuard, logoutGuard, changePin as changePinApi } from "@/api/guardAuth";
import { unregisterPushToken } from "@/api/pushApi";
import { clearSession, getDeviceId, loadSession, saveSession } from "./deviceStore";

type Status = "loading" | "signedOut" | "signedIn";

interface AuthValue {
  status: Status;
  session: GuardSession | null;
  signIn: (identifier: string, pin: string) => Promise<void>;
  signOut: () => Promise<void>;
  changePin: (currentPin: string, newPin: string) => Promise<void>;
}

const AuthContext = createContext<AuthValue | null>(null);

function isExpired(s: GuardSession): boolean {
  return Date.parse(s.expiresAt) <= Date.now();
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<Status>("loading");
  const [session, setSession] = useState<GuardSession | null>(null);
  const tokenRef = useRef<string | null>(null);

  const drop = useCallback(async () => {
    tokenRef.current = null;
    setSession(null);
    setStatus("signedOut");
    await clearSession();
  }, []);

  useEffect(() => {
    configureApi({ token: () => tokenRef.current, onUnauthorized: () => void drop() });
  }, [drop]);

  useEffect(() => {
    (async () => {
      try {
        const stored = await loadSession();
        if (stored) {
          const parsed = guardSessionSchema.safeParse(JSON.parse(stored));
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

  const signIn = useCallback(async (identifier: string, pin: string) => {
    const deviceId = await getDeviceId();
    const next = await loginGuard({ identifier, pin, deviceId });
    tokenRef.current = next.accessToken;
    await saveSession(JSON.stringify(next));
    setSession(next);
    setStatus("signedIn");
  }, []);

  const signOut = useCallback(async () => {
    try {
      await unregisterPushToken(await getDeviceId()); // stop this guard's alerts on this phone while the sign-in still works
    } catch {
      /* offline: the next guard to register on this phone takes the token over */
    }
    try {
      await logoutGuard(); // revoke on the server; ignore failure, local sign-out must always work
    } catch {
      /* offline sign-out still clears the device */
    }
    await drop();
  }, [drop]);

  const changePin = useCallback(
    async (currentPin: string, newPin: string) => {
      await changePinApi(currentPin, newPin);
      if (session) {
        const updated = { ...session, mustChangePIN: false };
        setSession(updated);
        await saveSession(JSON.stringify(updated));
      }
    },
    [session],
  );

  const value = useMemo(
    () => ({ status, session, signIn, signOut, changePin }),
    [status, session, signIn, signOut, changePin],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
}
