import { ApiError, api, configureApi } from "@/api/client";

jest.mock("@/config/env", () => ({ API_BASE_URL: "http://api.test" }));

const reply = (status: number, body: unknown) => ({ status, ok: status >= 200 && status < 300, json: async () => body }) as Response;
const fetchMock = jest.fn();
beforeEach(() => { fetchMock.mockReset(); (globalThis as any).fetch = fetchMock; });

function setup(over: Partial<Parameters<typeof configureApi>[0]> = {}) {
  let token = "old";
  const onUnauthorized = jest.fn();
  const onPaymentRequired = jest.fn();
  configureApi({ token: () => token, onUnauthorized, onPaymentRequired, refresh: async () => { token = "new"; return "refreshed"; }, ...over });
  return { onUnauthorized, onPaymentRequired };
}

describe("api", () => {
  it("sends the bearer token and unwraps the envelope", async () => {
    setup();
    fetchMock.mockResolvedValueOnce(reply(200, { success: true, data: { x: 1 } }));
    expect(await api("/p")).toEqual({ x: 1 });
    expect(fetchMock.mock.calls[0]![1].headers.Authorization).toBe("Bearer old");
  });
  it("after a 401 refreshes once and sends the request again with the new token", async () => {
    setup();
    fetchMock.mockResolvedValueOnce(reply(401, { success: false, message: "expired" })).mockResolvedValueOnce(reply(200, { success: true, data: "ok" }));
    expect(await api("/p")).toBe("ok");
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[1]![1].headers.Authorization).toBe("Bearer new");
  });
  it("does not loop: a second 401 after a refresh signs the person out", async () => {
    const { onUnauthorized } = setup();
    fetchMock.mockResolvedValue(reply(401, { success: false, message: "no" }));
    await expect(api("/p")).rejects.toMatchObject({ status: 401 });
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(onUnauthorized).toHaveBeenCalled();
  });
  it("signs out when the refresh is refused", async () => {
    const { onUnauthorized } = setup({ refresh: async () => "expired" });
    fetchMock.mockResolvedValueOnce(reply(401, { success: false }));
    await expect(api("/p")).rejects.toBeInstanceOf(ApiError);
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });
  it("treats a refresh with no signal as no signal, not as a sign-out, so queued work is kept", async () => {
    const { onUnauthorized } = setup({ refresh: async () => "offline" });
    fetchMock.mockResolvedValueOnce(reply(401, { success: false }));
    await expect(api("/p")).rejects.toMatchObject({ status: 0 });
    expect(onUnauthorized).not.toHaveBeenCalled();
  });
  it("never refreshes for an anonymous call such as login", async () => {
    const refresh = jest.fn();
    const { onUnauthorized } = setup({ refresh });
    fetchMock.mockResolvedValueOnce(reply(401, { success: false, message: "Bad password" }));
    await expect(api("/login", { anonymous: true })).rejects.toMatchObject({ message: "Bad password", status: 401 });
    expect(refresh).not.toHaveBeenCalled();
    expect(onUnauthorized).not.toHaveBeenCalled();
  });
  it("reports an inactive subscription (402) without signing out", async () => {
    const { onPaymentRequired, onUnauthorized } = setup();
    fetchMock.mockResolvedValueOnce(reply(402, { success: false, message: "Subscription inactive" }));
    await expect(api("/p")).rejects.toMatchObject({ status: 402, isPaymentRequired: true });
    expect(onPaymentRequired).toHaveBeenCalled();
    expect(onUnauthorized).not.toHaveBeenCalled();
  });
  it("turns a failed fetch into status 0", async () => {
    setup();
    fetchMock.mockRejectedValueOnce(new Error("offline"));
    await expect(api("/p")).rejects.toMatchObject({ status: 0, isNetwork: true });
  });
});
