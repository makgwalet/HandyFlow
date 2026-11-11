import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, cleanup, fireEvent, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import VendorsTab from "./VendorsTab"
import { filterVendors, sortVendors, uncommitted, vendorTotals, type Vendor } from "./vendorOps"

const get = vi.fn(); const post = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))
const v = (o: Partial<Vendor> = {}): Vendor => ({ id: "v1", vendorType: "CATERING", companyName: "Tasty Foods", contactName: "Lerato", contactPhone: "082 111 2222", contactEmail: "l@tasty.co.za", serviceDescription: "Lunch", quotedAmount: 5000, confirmed: false, notes: null, ...o })

describe("vendorOps", () => {
  const list = [v(), v({ id: "v2", companyName: "Sound Co", vendorType: "AV_TECH", quotedAmount: null, confirmed: true }), v({ id: "v3", companyName: "Alpha Secure", vendorType: "SECURITY", quotedAmount: 9000, confirmed: true })]
  it("filters by search, type and status", () => {
    expect(filterVendors(list, { q: "sound", type: "ALL", status: "ALL" }).map(x => x.id)).toEqual(["v2"]); expect(filterVendors(list, { q: "", type: "SECURITY", status: "ALL" }).map(x => x.id)).toEqual(["v3"])
    expect(filterVendors(list, { q: "", type: "ALL", status: "CONFIRMED" })).toHaveLength(2); expect(filterVendors(list, { q: "LUNCH", type: "ALL", status: "AWAITING" }).map(x => x.id)).toEqual(["v1"])
  })
  it("sorts, with missing quotes always last", () => {
    expect(sortVendors(list, "quotedAmount", "desc").map(x => x.id)).toEqual(["v3", "v1", "v2"]); expect(sortVendors(list, "quotedAmount", "asc").map(x => x.id)).toEqual(["v1", "v3", "v2"]); expect(sortVendors(list, "companyName", "asc")[0].id).toBe("v3")
  })
  it("totals and finds confirmed vendors with nothing committed", () => {
    expect(vendorTotals(list)).toMatchObject({ count: 3, confirmed: 2, awaiting: 1, quoted: 14000, confirmedQuoted: 9000 })
    const fin = [{ vendorId: "v3", committed: 9000, invoiced: 0, paid: 0, outstanding: 9000 }]; expect(uncommitted(list[1], fin)).toBe(true); expect(uncommitted(list[2], fin)).toBe(false); expect(uncommitted(list[1], undefined)).toBe(false)
  })
})

describe("vendors table", () => {
  let vendors: Vendor[]
  beforeEach(() => {
    get.mockReset(); post.mockReset(); perms = []; post.mockResolvedValue({ data: { data: {} } })
    vendors = [v(), v({ id: "v2", companyName: "Sound Co", vendorType: "AV_TECH", confirmed: true, quotedAmount: 12000, contactEmail: "s@sound.co.za" })]
    get.mockImplementation(async (u: string) => ({ data: { data: u.endsWith("/budget/suppliers") ? [{ vendorId: "v1", committed: 5000, invoiced: 5000, paid: 2000, outstanding: 3000 }] : vendors } }))
  })
  afterEach(cleanup)
  const mount = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><VendorsTab eventId="e1" eventTitle="Summit" onChangeEvent={() => {}} /></QueryClientProvider>)

  it("a reader sees the table, contact links and no buttons or money columns", async () => {
    perms = ["EVENTS_READ"]; mount(); await waitFor(() => screen.getByText("Tasty Foods"))
    expect(screen.queryByText("Add vendor")).toBeNull(); expect(screen.queryByLabelText("Confirm Tasty Foods")).toBeNull(); expect(screen.queryByText("Still owed")).toBeNull(); expect(screen.getByText("l@tasty.co.za").closest("a")?.getAttribute("href")).toBe("mailto:l@tasty.co.za"); expect(get).not.toHaveBeenCalledWith("/api/v1/events/e1/budget/suppliers")
  })
  it("searches and sorts", async () => {
    perms = ["EVENTS_READ"]; mount(); await waitFor(() => screen.getByText("Tasty Foods"))
    fireEvent.change(screen.getByLabelText("Search vendors"), { target: { value: "sound" } }); expect(screen.queryByText("Tasty Foods")).toBeNull(); expect(screen.getByText("Sound Co")).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Search vendors"), { target: { value: "zzz" } }); expect(screen.getByText("No vendors match.")).toBeTruthy()
  })
  it("finance users see what is owed and the uncommitted warning", async () => {
    perms = ["EVENTS_BUDGET_READ"]; mount(); await waitFor(() => screen.getByText("Still owed")); await waitFor(() => expect(screen.getByRole("note").textContent).toMatch(/Not committed/)); expect(screen.getByText(/3[\s ]?000[.,]00/)).toBeTruthy()
  })
  it("confirms an awaiting vendor", async () => {
    perms = ["EVENTS_SUPPLIER_MANAGE"]; mount(); await waitFor(() => screen.getByLabelText("Confirm Tasty Foods")); expect(screen.queryByLabelText("Confirm Sound Co")).toBeNull()
    fireEvent.click(screen.getByLabelText("Confirm Tasty Foods")); await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/events/e1/vendors/v1/confirm"))
  })
  it("adds a vendor with optional fields as null and shows a refusal", async () => {
    perms = ["EVENTS_MANAGE"]; post.mockRejectedValueOnce({ response: { data: { message: "A vendor with that name already exists" } } }); mount(); await waitFor(() => screen.getByText("Add vendor")); fireEvent.click(screen.getByText("Add vendor"))
    const d = () => within(screen.getByRole("dialog")); expect((d().getByText("Add vendor", { selector: "button" }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(d().getByLabelText(/Company/), { target: { value: "Lights Ltd" } }); fireEvent.click(d().getByText("Add vendor", { selector: "button" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/events/e1/vendors", expect.objectContaining({ companyName: "Lights Ltd", contactName: null, quotedAmount: null }))); await waitFor(() => d().getByText(/already exists/))
  })
})
