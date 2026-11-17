import { z } from "zod";
import { api } from "./client";

/**
 * Mirrors the backend MobileSession. Parsed, not cast, so a contract drift fails loudly.
 * `permissions` decide which modes the person sees (access/modes.ts). The server checks them again on every call.
 */
export const mobileSessionSchema = z.object({
  accessToken: z.string().min(1),
  tokenType: z.string().optional(),
  expiresIn: z.number(),
  userId: z.string(),
  tenantId: z.string(),
  email: z.string(),
  firstName: z.string().nullish(),
  lastName: z.string().nullish(),
  permissions: z.array(z.string()),
  subscriptionStatus: z.string().nullish(),
  refreshToken: z.string().min(1),
  refreshTokenExpiresAt: z.string(),
});

/** What the phone keeps (in the keychain / keystore only). `expiresAt` is worked out on the phone from `expiresIn`. */
export const fuelSessionSchema = mobileSessionSchema.extend({ expiresAt: z.string() });
export type FuelSession = z.infer<typeof fuelSessionSchema>;

export interface LoginInput {
  email: string;
  password: string;
  tenantSlug: string;
}

const withExpiry = (s: z.infer<typeof mobileSessionSchema>, now: number): FuelSession => ({ ...s, expiresAt: new Date(now + s.expiresIn * 1000).toISOString() });

export async function loginFuel(input: LoginInput, deviceId: string, now: () => number = Date.now): Promise<FuelSession> {
  const raw = await api<unknown>("/api/v1/auth/mobile/login", {
    method: "POST",
    anonymous: true,
    body: { email: input.email.trim(), password: input.password, tenantSlug: input.tenantSlug.trim().toLowerCase(), deviceId },
  });
  return withExpiry(mobileSessionSchema.parse(raw), now());
}

/**
 * Trades the refresh token for a new access token. The server rotates it: the new refresh token in the answer must be saved
 * before anything else, because the old one is dead the moment this succeeds. Permissions come back fresh too.
 */
export async function refreshSession(current: FuelSession, deviceId: string, now: () => number = Date.now): Promise<FuelSession> {
  const raw = await api<unknown>("/api/v1/auth/mobile/refresh", {
    method: "POST",
    anonymous: true,
    body: { refreshToken: current.refreshToken, deviceId },
  });
  const next = mobileSessionSchema.parse(raw);
  // A refresh does not restate the subscription status; keep what the sign-in said.
  return withExpiry({ ...next, subscriptionStatus: next.subscriptionStatus ?? current.subscriptionStatus }, now());
}

export function logoutFuel(refreshToken: string): Promise<void> {
  return api<void>("/api/v1/auth/mobile/logout", { method: "POST", anonymous: true, body: { refreshToken } });
}

/** The access token has run out or is about to (within `skewMs`). */
export const accessExpiring = (s: Pick<FuelSession, "expiresAt">, now = Date.now(), skewMs = 60_000): boolean => Date.parse(s.expiresAt) - skewMs <= now;

/** The refresh token can still be used, so the person need not sign in again. */
export const refreshUsable = (s: Pick<FuelSession, "refreshTokenExpiresAt">, now = Date.now()): boolean => Date.parse(s.refreshTokenExpiresAt) > now;

export function displayName(s: Pick<FuelSession, "firstName" | "lastName" | "email">): string {
  const name = [s.firstName, s.lastName].filter(Boolean).join(" ").trim();
  return name || s.email;
}
