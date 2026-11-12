import { z } from "zod";
import { api } from "./client";

/** Mirrors backend GuardLoginResponse. Parsed, not cast, so a contract drift fails loudly. */
export const guardSessionSchema = z.object({
  accessToken: z.string().min(1),
  tokenType: z.string(),
  expiresIn: z.number(),
  guardId: z.string(),
  tenantId: z.string(),
  fullName: z.string(),
  grade: z.string().nullish(),
  status: z.string(),
  mustChangePIN: z.boolean(),
  expiresAt: z.string(),
});
export type GuardSession = z.infer<typeof guardSessionSchema>;

export const PIN_PATTERN = /^\d{6}$/;

export interface LoginInput {
  /** Phone number or employee code. */
  identifier: string;
  pin: string;
  deviceId: string;
}

export async function loginGuard(input: LoginInput): Promise<GuardSession> {
  const id = input.identifier.trim();
  // The backend takes exactly one of phone / employeeCode. A leading + or all digits means phone.
  const isPhone = /^\+?\d{7,15}$/.test(id.replace(/[\s-]/g, ""));
  const raw = await api<unknown>("/api/v1/auth/guard/login", {
    method: "POST",
    anonymous: true,
    body: {
      phone: isPhone ? id.replace(/[\s-]/g, "") : undefined,
      employeeCode: isPhone ? undefined : id,
      pin: input.pin,
      deviceId: input.deviceId,
    },
  });
  return guardSessionSchema.parse(raw);
}

export function changePin(currentPin: string, newPin: string): Promise<void> {
  return api<void>("/api/v1/guard/auth/change-pin", { method: "POST", body: { currentPin, newPin } });
}

export function logoutGuard(): Promise<void> {
  return api<void>("/api/v1/guard/auth/logout", { method: "POST" });
}

export function activateReplacementDevice(input: {
  code: string;
  newDeviceHardwareId: string;
  newDeviceName?: string;
}): Promise<unknown> {
  return api<unknown>("/api/v1/auth/guard/activate-device", {
    method: "POST",
    anonymous: true,
    body: input,
  });
}
