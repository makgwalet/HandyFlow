import { ApiError, api, configureApi } from "@/api/client";
import { guardSessionSchema, loginGuard, PIN_PATTERN } from "@/api/guardAuth";

const okSession = {
  accessToken: "t", tokenType: "Bearer", expiresIn: 46800, guardId: "g1", tenantId: "t1",
  fullName: "Lerato Mokoena", grade: "C", status: "ACTIVE", mustChangePIN: false,
  expiresAt: "2099-01-01T00:00:00Z",
};

function mockFetch(status: number, body: unknown) {
  const fn = jest.fn().mockResolvedValue({ ok: status >= 200 && status < 300, status, json: async () => body });
  (globalThis as unknown as { fetch: unknown }).fetch = fn;
  return fn;
}

describe("api client", () => {
  beforeEach(() => configureApi({ token: () => "tok", onUnauthorized: jest.fn() }));

  it("sends the bearer token and unwraps data", async () => {
    const f = mockFetch(200, { success: true, data: { a: 1 } });
    await expect(api<{ a: number }>("/x")).resolves.toEqual({ a: 1 });
    expect((f.mock.calls[0]![1] as RequestInit).headers).toMatchObject({ Authorization: "Bearer tok" });
  });

  it("signs out on 401 for signed-in calls only", async () => {
    const onUnauthorized = jest.fn();
    configureApi({ token: () => "tok", onUnauthorized });
    mockFetch(401, { success: false, message: "no" });
    await expect(api("/x")).rejects.toBeInstanceOf(ApiError);
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
    await expect(api("/login", { anonymous: true })).rejects.toBeInstanceOf(ApiError);
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it("reports a network failure as status 0", async () => {
    (globalThis as unknown as { fetch: unknown }).fetch = jest.fn().mockRejectedValue(new Error("offline"));
    await expect(api("/x")).rejects.toMatchObject({ status: 0, isNetwork: true });
  });
});

describe("guard login", () => {
  it("sends phone for a number and employeeCode otherwise, never both", async () => {
    const f = mockFetch(200, { success: true, data: okSession });
    await loginGuard({ identifier: "082 555 0101", pin: "123456", deviceId: "d" });
    const phoneBody = JSON.parse((f.mock.calls[0]![1] as RequestInit).body as string);
    expect(phoneBody.phone).toBe("0825550101");
    expect(phoneBody.employeeCode).toBeUndefined();

    await loginGuard({ identifier: "G-0042", pin: "123456", deviceId: "d" });
    const codeBody = JSON.parse((f.mock.calls[1]![1] as RequestInit).body as string);
    expect(codeBody.employeeCode).toBe("G-0042");
    expect(codeBody.phone).toBeUndefined();
  });

  it("rejects a malformed server response instead of trusting it", async () => {
    mockFetch(200, { success: true, data: { accessToken: "" } });
    await expect(loginGuard({ identifier: "G-1", pin: "123456", deviceId: "d" })).rejects.toBeDefined();
    expect(guardSessionSchema.safeParse(okSession).success).toBe(true);
  });

  it("accepts only 6-digit PINs", () => {
    expect(PIN_PATTERN.test("123456")).toBe(true);
    for (const bad of ["12345", "1234567", "12a456", ""]) expect(PIN_PATTERN.test(bad)).toBe(false);
  });
});
