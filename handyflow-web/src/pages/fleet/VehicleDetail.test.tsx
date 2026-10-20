import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import VehicleDetail from "./VehicleDetail"
import { complianceTone, expiryText, identityRows, uploadProblem } from "./vehicleDetail.logic"

const get = vi.fn(), post = vi.fn()
type Args = unknown[]
let perms = new Set<string>()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: Args) => get(...a), post: (...a: Args) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const show = (identity = {}) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><VehicleDetail vehicleId="v1" identity={identity} /></QueryClientProvider>)

beforeEach(() => {
  perms = new Set(["FLEET_READ", "FLEET_MANAGE"]); get.mockReset(); post.mockReset(); post.mockResolvedValue({ data: { data: {} } })
  get.mockImplementation((url: string) =>
    url.endsWith("/compliance") ? ok({ overallStatus: "EXPIRING_SOON", items: [
      { key: "LICENCE_DISC", label: "Licence disc", status: "VALID", daysRemaining: 200, detail: null },
      { key: "INSURANCE", label: "Insurance", status: "EXPIRING_SOON", daysRemaining: 18, detail: null },
      { key: "SERVICE", label: "Service", status: "DUE", daysRemaining: null, detail: "Due at 120 000 km" }] })
    : url.endsWith("/documents") ? ok([{ id: "d1", type: "INSURANCE", documentNumber: "P-77", expiryDate: "2026-11-01", version: 2, fileName: "policy.pdf", expired: false, daysToExpiry: 18 }])
    : url.endsWith("/insurance") ? ok({ insured: true, insurer: "Santam", policyNumber: "P-77", policyType: "COMPREHENSIVE", policyExpiry: "2026-11-01", daysToExpiry: 18, premium: 1200, excess: 5000, coverAmount: 300000, trackingRequired: true, trackingProvider: "Netstar" })
    : url.includes("/accidents") ? ok({ content: [{ id: "a1", referenceNumber: "ACC-1", occurredAt: "2026-10-01T08:00:00Z", description: "Rear-ended", status: "IN_REPAIR", vehicleDrivable: false }] })
    : ok([]))
})
afterEach(cleanup)

describe("detail rules", () => {
  it("words expiry by days", () => {
    expect(expiryText(null)).toBe("No expiry"); expect(expiryText(-1)).toBe("Expired 1 day ago"); expect(expiryText(0)).toBe("Expires today"); expect(expiryText(18)).toBe("18 days left")
  })
  it("maps statuses to traffic lights", () => {
    expect(["VALID", "EXPIRING_SOON", "DUE", "EXPIRED", "MISSING", "NOT_APPLICABLE"].map(complianceTone)).toEqual(["ok", "warn", "warn", "bad", "bad", "none"])
  })
  it("shows a missing engine number as a visible gap", () => {
    const rows = identityRows({ vin: "VIN1", gvmKg: 3500 })
    expect(rows.find(r => r.label === "Engine number")).toMatchObject({ value: "Not recorded", missing: true })
    expect(rows.find(r => r.label === "GVM")?.value).toMatch(/3.500 kg/)
  })
  it("requires an expiry for licence, roadworthy and insurance uploads", () => {
    const f = new File(["x"], "a.pdf")
    expect(uploadProblem("", f, "")).toMatch(/type/); expect(uploadProblem("OTHER", null, "")).toMatch(/file/)
    expect(uploadProblem("INSURANCE", f, "")).toMatch(/expiry/); expect(uploadProblem("INSURANCE", f, "2027-01-01")).toBeNull(); expect(uploadProblem("OTHER", f, "")).toBeNull()
  })
})

describe("VehicleDetail", () => {
  it("opens on the identity register with the gaps flagged", () => {
    show({ vin: "VIN1" })
    expect(screen.getByText("VIN1")).toBeTruthy()
    expect(screen.getAllByText("Not recorded").length).toBeGreaterThan(5)
  })
  it("shows the compliance lights", async () => {
    show(); fireEvent.click(screen.getByRole("tab", { name: "Compliance" }))
    const row = await screen.findByLabelText("Insurance")
    expect(row.textContent).toContain("Expiring soon"); expect(row.textContent).toContain("18 days left")
    expect(screen.getByLabelText("Service").textContent).toContain("Due at 120 000 km")
  })
  it("lists documents and uploads with the expiry the server needs", async () => {
    show(); fireEvent.click(screen.getByRole("tab", { name: "Documents" }))
    await screen.findByText("policy.pdf")
    fireEvent.change(screen.getByLabelText("Document type"), { target: { value: "INSURANCE" } })
    fireEvent.change(screen.getByLabelText("Document file"), { target: { files: [new File(["x"], "new.pdf")] } })
    expect((screen.getByText("Upload") as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Expiry date"), { target: { value: "2027-01-01" } })
    fireEvent.click(screen.getByText("Upload"))
    await waitFor(() => expect(post).toHaveBeenCalled())
    const [url, form] = post.mock.calls[0]
    expect(url).toBe("/api/v1/fleet/vehicles/v1/documents"); expect((form as FormData).get("type")).toBe("INSURANCE"); expect((form as FormData).get("expiryDate")).toBe("2027-01-01")
  })
  it("hides upload from read-only users", async () => {
    perms = new Set(["FLEET_READ"]); show(); fireEvent.click(screen.getByRole("tab", { name: "Documents" }))
    await screen.findByText("policy.pdf"); expect(screen.queryByText("Upload")).toBeNull()
  })
  it("shows the insurance profile", async () => {
    show(); fireEvent.click(screen.getByRole("tab", { name: "Insurance" }))
    expect(await screen.findByText("Santam")).toBeTruthy(); expect(screen.getByText(/Required · Netstar/)).toBeTruthy()
  })
  it("marks a case that keeps the vehicle off the road", async () => {
    show(); fireEvent.click(screen.getByRole("tab", { name: "Accidents" }))
    expect(await screen.findByText("Cannot be driven")).toBeTruthy()
  })
})
