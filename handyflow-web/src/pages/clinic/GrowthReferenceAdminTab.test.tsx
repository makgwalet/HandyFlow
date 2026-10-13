import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn(), post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a) } }))
const perms: Record<string, boolean> = {}
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => !!perms[p] }))
import GrowthReferenceAdminTab from "./GrowthReferenceAdminTab"

const set = (o: any) => ({ id: "s1", measure: "WEIGHT_FOR_AGE", sex: "F", title: "WHO weight-for-age girls", source: "WHO", sourceVersion: "2006", minAgeMonths: 0, maxAgeMonths: 60, status: "CLINICAL_REVIEW", pointCount: 61, ...o })
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><GrowthReferenceAdminTab /></QueryClientProvider>)

beforeEach(() => { get.mockReset(); post.mockReset(); for (const k of Object.keys(perms)) delete perms[k]; get.mockResolvedValue({ data: { data: [set({})] } }) })
afterEach(cleanup)

describe("GrowthReferenceAdminTab", () => {
  it("shows the sets, and no buttons to someone who cannot approve", async () => {
    perms.CLINIC_CONTENT_ADMIN = true
    show()
    await screen.findByText("Weight for age")
    expect(screen.getByText("In clinical review")).toBeTruthy()
    expect(screen.queryByRole("button", { name: /Confirm it matches/ })).toBeNull()
  })

  it("approving asks first and then posts the step", async () => {
    perms.CLINIC_CONTENT_APPROVE = true
    post.mockResolvedValue({ data: {} })
    show()
    fireEvent.click(await screen.findByRole("button", { name: "Confirm it matches its source" }))
    expect(screen.getByText(/match the source cited/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.click(screen.getAllByRole("button", { name: "Confirm it matches its source" })[1])
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/growth-reference/s1/approve"))
  })

  it("shows the server's refusal, for example the same person trying to activate", async () => {
    perms.CLINIC_CONTENT_APPROVE = true
    get.mockResolvedValue({ data: { data: [set({ status: "APPROVED" })] } })
    post.mockRejectedValue({ response: { data: { message: "A different person must activate it" } } })
    show()
    fireEvent.click(await screen.findByRole("button", { name: "Activate" }))
    fireEvent.click(screen.getAllByRole("button", { name: "Activate" })[1])
    expect((await screen.findByRole("alert")).textContent).toBe("A different person must activate it")
  })
})
