// @vitest-environment jsdom
import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }))
vi.mock("../../api/client", () => ({ apiClient: api }))
vi.mock("../security/SiteAccessPanel", () => ({ default: (p: { userId: string; userName: string; actorId?: string }) => <div data-testid="panel">{p.userId}|{p.userName}|{p.actorId}</div> }))
vi.mock("./SeatUsage", () => ({ default: () => null }))
import TeamTab from "./TeamTab"
import { useAuthStore } from "../../store/auth.store"

const users = [
  { id: "u1", email: "me@x.co", firstName: "Me", lastName: "Admin", phone: null, jobTitle: null, department: null, status: "ACTIVE", roles: ["Admin"], permissions: [], createdAt: "2026-01-01T00:00:00Z" },
  { id: "u2", email: "t@x.co", firstName: "Thandi", lastName: "Nkosi", phone: null, jobTitle: null, department: null, status: "ACTIVE", roles: ["Manager"], permissions: [], createdAt: "2026-01-01T00:00:00Z" },
  { id: "u3", email: "o@x.co", firstName: "Old", lastName: "Hand", phone: null, jobTitle: null, department: null, status: "INACTIVE", roles: ["Viewer"], permissions: [], createdAt: "2026-01-01T00:00:00Z" },
]
function show() {
  api.get.mockImplementation((url: string) => Promise.resolve({ data: url.endsWith("/users") ? users : [] }))
  return render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><TeamTab /></QueryClientProvider>)
}
const as = (permissions: string[]) => useAuthStore.setState({ user: { userId: "u1", tenantId: "t", email: "me@x.co", firstName: "Me", lastName: "Admin", permissions } } as never)

describe("TeamTab site access", () => {
  beforeEach(() => { api.get.mockReset() })
  afterEach(() => { cleanup(); useAuthStore.setState({ user: null } as never) })

  it("offers site access to a security administrator for active users only, and opens the panel for that user", async () => {
    as(["SECURITY_ADMIN", "USER_MANAGE"]); show()
    fireEvent.click(await screen.findByLabelText("Site access for Thandi Nkosi"))
    expect(screen.getByTestId("panel").textContent).toBe("u2|Thandi Nkosi|u1")
    expect(screen.queryByLabelText("Site access for Old Hand")).toBeNull()
  })

  it("offers nothing to someone without SECURITY_ADMIN", async () => {
    as(["USER_MANAGE", "SECURITY_MANAGE"]); show()
    await screen.findByText("Thandi Nkosi")
    expect(screen.queryByLabelText("Site access for Thandi Nkosi")).toBeNull()
  })
})
