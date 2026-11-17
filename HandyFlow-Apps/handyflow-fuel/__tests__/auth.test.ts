import { displayName, fuelSessionSchema, loginFuel } from "@/api/auth";
import { isExpired } from "@/auth/AuthContext";

jest.mock("@/api/client", () => ({ api: jest.fn(), ApiError: class extends Error {} }));
import { api } from "@/api/client";
const mockApi = api as jest.Mock;

const response = { accessToken: "t", tokenType: "Bearer", expiresIn: 3600, userId: "u1", tenantId: "t1", email: "d@x.co.za", firstName: "Sipho", lastName: "Dlamini", permissions: ["FUEL_DRIVER"] };

describe("login", () => {
  it("posts the company code lower-cased and the email trimmed, without a token", async () => {
    mockApi.mockResolvedValueOnce(response);
    await loginFuel({ email: " d@x.co.za ", password: "pw", tenantSlug: " ACME " });
    expect(mockApi).toHaveBeenCalledWith("/api/v1/auth/login", { method: "POST", anonymous: true, body: { email: "d@x.co.za", password: "pw", tenantSlug: "acme" } });
  });
  it("works out when the sign-in ends from expiresIn", async () => {
    mockApi.mockResolvedValueOnce(response);
    const s = await loginFuel({ email: "d@x.co.za", password: "pw", tenantSlug: "acme" }, () => Date.parse("2026-01-01T00:00:00Z"));
    expect(s.expiresAt).toBe("2026-01-01T01:00:00.000Z");
    expect(fuelSessionSchema.safeParse(s).success).toBe(true);
  });
  it("fails loudly when the server answer has no permissions", async () => {
    const { permissions: _p, ...bad } = response;
    mockApi.mockResolvedValueOnce(bad);
    await expect(loginFuel({ email: "d@x.co.za", password: "pw", tenantSlug: "acme" })).rejects.toThrow();
  });
});

describe("session", () => {
  it("knows when it has expired", () => {
    expect(isExpired({ expiresAt: "2026-01-01T00:00:00Z" }, Date.parse("2026-01-01T00:00:01Z"))).toBe(true);
    expect(isExpired({ expiresAt: "2026-01-01T00:00:00Z" }, Date.parse("2025-12-31T23:59:59Z"))).toBe(false);
  });
  it("shows the name, or the email when there is none", () => {
    expect(displayName(response)).toBe("Sipho Dlamini");
    expect(displayName({ email: "d@x.co.za", firstName: null, lastName: null })).toBe("d@x.co.za");
  });
});
