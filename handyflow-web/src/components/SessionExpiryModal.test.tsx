import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, act, cleanup } from "@testing-library/react"
import { SessionExpiryModal } from "./SessionExpiryModal"

const state = vi.hoisted(() => ({ token: "t1", expiry: 0 }))
const setAuth = vi.hoisted(() => vi.fn())
const logout = vi.hoisted(() => vi.fn())
vi.mock("../store/auth.store", () => {
  const store: any = (sel: any) => sel({ token: state.token, isAuthenticated: true, logout })
  store.getState = () => ({ user: { userId: "u" }, setAuth })
  return { useAuthStore: store }
})
const api = vi.hoisted(() => ({ post: vi.fn() }))
vi.mock("../api/client", () => ({ apiClient: api }))
vi.mock("../utils/jwt", () => ({ decodeJwtExpiry: () => state.expiry }))

beforeEach(() => {
  vi.useFakeTimers(); vi.setSystemTime(new Date("2026-10-14T10:00:00Z"))
  api.post.mockReset(); api.post.mockResolvedValue({ data: { accessToken: "t2", userId: "u", tenantId: "t", email: "e", firstName: "a", lastName: "b", permissions: [] } })
  setAuth.mockReset()
})
afterEach(() => { cleanup(); vi.useRealTimers() })

describe("SessionExpiryModal", () => {
  it("extends the session quietly while the user is working, with no warning", async () => {
    state.expiry = Date.now() + 4 * 60_000
    render(<SessionExpiryModal />)
    await act(async () => { window.dispatchEvent(new Event("mousemove")); await vi.advanceTimersByTimeAsync(5000) })
    expect(api.post).toHaveBeenCalledWith("/api/v1/auth/refresh")
    expect(setAuth).toHaveBeenCalled()
    expect(screen.queryByText("Your session is about to expire")).toBeNull()
  })

  it("warns someone who has gone idle", async () => {
    state.expiry = Date.now() + 20 * 60_000
    render(<SessionExpiryModal />)
    await act(async () => { await vi.advanceTimersByTimeAsync(10 * 60_000) })  // untouched for 10 minutes
    await act(async () => { await vi.advanceTimersByTimeAsync(8.5 * 60_000) }) // 1.5 minutes left
    expect(screen.getByText("Your session is about to expire")).toBeTruthy()
    expect(api.post).not.toHaveBeenCalled()
  })
})
