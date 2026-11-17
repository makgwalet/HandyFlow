import { accessExpiring, displayName, fuelSessionSchema, loginFuel, refreshSession, refreshUsable, type FuelSession } from "@/api/auth";

jest.mock("@/api/client", () => ({ api: jest.fn(), ApiError: class extends Error {} }));
import { api } from "@/api/client";
const mockApi = api as jest.Mock;

const response = {
  accessToken: "t", tokenType: "Bearer", expiresIn: 3600, userId: "u1", tenantId: "t1", email: "d@x.co.za", firstName: "Sipho", lastName: "Dlamini",
  permissions: ["FUEL_DRIVER"], subscriptionStatus: "ACTIVE", refreshToken: "r1", refreshTokenExpiresAt: "2027-01-01T00:00:00Z",
};
const T0 = Date.parse("2026-01-01T00:00:00Z");

describe("login", () => {
  it("posts to the mobile login with the company code lower-cased, the email trimmed and the phone's id, without a token", async () => {
    mockApi.mockResolvedValueOnce(response);
    await loginFuel({ email: " d@x.co.za ", password: "pw", tenantSlug: " ACME " }, "dev-1");
    expect(mockApi).toHaveBeenCalledWith("/api/v1/auth/mobile/login", { method: "POST", anonymous: true, body: { email: "d@x.co.za", password: "pw", tenantSlug: "acme", deviceId: "dev-1" } });
  });
  it("works out when the access token ends from expiresIn", async () => {
    mockApi.mockResolvedValueOnce(response);
    const s = await loginFuel({ email: "d@x.co.za", password: "pw", tenantSlug: "acme" }, "d", () => T0);
    expect(s.expiresAt).toBe("2026-01-01T01:00:00.000Z");
    expect(fuelSessionSchema.safeParse(s).success).toBe(true);
  });
  it("fails loudly when the answer has no refresh token", async () => {
    const { refreshToken: _r, ...bad } = response;
    mockApi.mockResolvedValueOnce(bad);
    await expect(loginFuel({ email: "d@x.co.za", password: "pw", tenantSlug: "acme" }, "d")).rejects.toThrow();
  });
  it("fails loudly when the answer has no permissions", async () => {
    const { permissions: _p, ...bad } = response;
    mockApi.mockResolvedValueOnce(bad);
    await expect(loginFuel({ email: "d@x.co.za", password: "pw", tenantSlug: "acme" }, "d")).rejects.toThrow();
  });
});

describe("refresh", () => {
  const current = (): FuelSession => ({ ...response, expiresAt: "2026-01-01T00:00:00.000Z" });
  it("sends the refresh token without a bearer header and returns the rotated one", async () => {
    mockApi.mockResolvedValueOnce({ ...response, accessToken: "t2", refreshToken: "r2", subscriptionStatus: null, permissions: ["FUEL_DRIVER", "FUEL_READ"] });
    const next = await refreshSession(current(), "dev-1", () => T0);
    expect(mockApi).toHaveBeenCalledWith("/api/v1/auth/mobile/refresh", { method: "POST", anonymous: true, body: { refreshToken: "r1", deviceId: "dev-1" } });
    expect(next.refreshToken).toBe("r2");
    expect(next.accessToken).toBe("t2");
    expect(next.permissions).toContain("FUEL_READ");
  });
  it("keeps the subscription status the sign-in gave, since a refresh does not restate it", async () => {
    mockApi.mockResolvedValueOnce({ ...response, subscriptionStatus: null, refreshToken: "r2" });
    expect((await refreshSession(current(), "d")).subscriptionStatus).toBe("ACTIVE");
  });
  it("knows when the access token is about to run out, and whether the refresh token still works", () => {
    expect(accessExpiring({ expiresAt: "2026-01-01T00:00:30Z" }, T0)).toBe(true);
    expect(accessExpiring({ expiresAt: "2026-01-01T00:10:00Z" }, T0)).toBe(false);
    expect(refreshUsable({ refreshTokenExpiresAt: "2025-12-31T00:00:00Z" }, T0)).toBe(false);
    expect(refreshUsable({ refreshTokenExpiresAt: "2026-02-01T00:00:00Z" }, T0)).toBe(true);
  });
});

describe("display", () => {
  it("shows the name, or the email when there is none", () => {
    expect(displayName(response)).toBe("Sipho Dlamini");
    expect(displayName({ email: "d@x.co.za", firstName: null, lastName: null })).toBe("d@x.co.za");
  });
});
