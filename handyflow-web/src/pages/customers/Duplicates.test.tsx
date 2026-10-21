import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import DuplicatesPanel from "./DuplicatesPanel"
import { differingFields, groupKey, oldestFirst, summaryText, type DupGroup, type Duplicates } from "./duplicates.logic"

const get = vi.fn(), post = vi.fn(), del = vi.fn()
type Args = unknown[]
let perms = new Set<string>()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: Args) => get(...a), post: (...a: Args) => post(...a), delete: (...a: Args) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
const m = (id: string, name: string, created: string, o: object = {}) => ({ id, name, email: null, phone: null, taxNumber: null, customerType: "CUSTOMER", createdAt: created, ...o })
const group: DupGroup = { customers: [m("2", "Tau Mining (Pty) Ltd", "2026-05-01T08:00:00Z", { email: "a@tau.co.za" }), m("1", "Tau Mining Ltd", "2026-01-01T08:00:00Z", { email: "b@tau.co.za" })], reasons: ["Same name"] }
const result: Duplicates = { groups: [group], scanned: 40, truncated: false }
const wrap = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><DuplicatesPanel /></QueryClientProvider>)
beforeEach(() => { perms = new Set(["CUSTOMER_READ", "CUSTOMER_UPDATE", "CUSTOMER_DELETE"]); for (const f of [get, post, del]) f.mockReset(); get.mockResolvedValue({ data: { data: result } }); post.mockResolvedValue({ data: {} }); del.mockResolvedValue({ data: {} }) })
afterEach(cleanup)

describe("duplicate rules", () => {
  it("orders oldest first, keys groups and says what differs", () => {
    expect(oldestFirst(group).map(c => c.id)).toEqual(["1", "2"]); expect(groupKey(group)).toBe("1|2"); expect(differingFields(group)).toEqual(["email"])
    expect(summaryText(result)).toBe("1 possible duplicate group among 40 customers."); expect(summaryText({ groups: [], scanned: 3, truncated: false })).toBe("No likely duplicates among 3 customers.")
  })
})

describe("DuplicatesPanel", () => {
  it("shows each group with its reason and marks the oldest", async () => {
    wrap(); expect(await screen.findByText("Same name")).toBeTruthy(); expect(screen.getByText("(oldest)")).toBeTruthy(); expect(screen.getByLabelText("Duplicate summary").textContent).toContain("1 possible duplicate group")
  })
  it("remembers a 'not a duplicate' decision", async () => {
    wrap(); fireEvent.click(await screen.findByRole("button", { name: "Not a duplicate" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/crm/customers/duplicates/dismiss", { customerIds: ["2", "1"] }))
  })
  it("asks before deleting a record", async () => {
    wrap(); fireEvent.click(await screen.findByLabelText("Delete Tau Mining Ltd")); expect(del).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole("button", { name: "Yes, delete Tau Mining Ltd" })); await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/crm/customers/1"))
  })
  it("hides actions the person is not allowed to use", async () => {
    perms = new Set(["CUSTOMER_READ"]); wrap(); await screen.findByText("Same name"); expect(screen.queryByRole("button", { name: "Not a duplicate" })).toBeNull(); expect(screen.queryByLabelText("Delete Tau Mining Ltd")).toBeNull()
  })
  it("shows a server error", async () => {
    post.mockRejectedValue({ response: { data: { message: "Customer not found" } } }); wrap(); fireEvent.click(await screen.findByRole("button", { name: "Not a duplicate" })); expect((await screen.findByRole("alert")).textContent).toContain("Customer not found")
  })
  it("says so when there is nothing to show, or the check fails", async () => {
    get.mockResolvedValue({ data: { data: { groups: [], scanned: 5, truncated: false } } }); wrap(); expect(await screen.findByText("No likely duplicates among 5 customers.")).toBeTruthy(); cleanup()
    get.mockRejectedValue(new Error("x")); wrap(); expect((await screen.findByRole("alert")).textContent).toContain("Couldn't check")
  })
})
