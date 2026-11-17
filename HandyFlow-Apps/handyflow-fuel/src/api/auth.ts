import { z } from "zod";
import { api } from "./client";

/**
 * Mirrors the backend AuthResponse. Parsed, not cast, so a contract drift fails loudly.
 * `permissions` is what decides which modes the person sees (see access/modes.ts).
 */
export const loginResponseSchema = z.object({
  accessToken: z.string().min(1),
  tokenType: z.string().optional(),
  expiresIn: z.number(),
  userId: z.string(),
  tenantId: z.string(),
  email: z.string(),
  firstName: z.string().nullish(),
  lastName: z.string().nullish(),
  permissions: z.array(z.string()),
});

/** What the phone keeps. `expiresAt` is worked out on the phone from `expiresIn` at sign-in. */
export const fuelSessionSchema = loginResponseSchema.extend({ expiresAt: z.string() });
export type FuelSession = z.infer<typeof fuelSessionSchema>;

export interface LoginInput {
  email: string;
  password: string;
  tenantSlug: string;
}

export async function loginFuel(input: LoginInput, now: () => number = Date.now): Promise<FuelSession> {
  const raw = await api<unknown>("/api/v1/auth/login", {
    method: "POST",
    anonymous: true,
    body: { email: input.email.trim(), password: input.password, tenantSlug: input.tenantSlug.trim().toLowerCase() },
  });
  const parsed = loginResponseSchema.parse(raw);
  return { ...parsed, expiresAt: new Date(now() + parsed.expiresIn * 1000).toISOString() };
}

export function displayName(s: Pick<FuelSession, "firstName" | "lastName" | "email">): string {
  const name = [s.firstName, s.lastName].filter(Boolean).join(" ").trim();
  return name || s.email;
}
