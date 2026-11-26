// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TrainProvSlotResources from "./TrainProvSlotResources"

const get = vi.fn()
const post = vi.fn()
let admin = true
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (p: string[]) => (p.includes("TRAININGPROVIDER_ADMIN") && p.length === 1 ? admin : true) }))

const booking = (over: object = {}) => ({
  id: "b1", slotId: "s1", resourceId: "r1", resourceNo: "RES-00001", resourceName: "Welding rig", resourceType: "EQUIPMENT", resourceStatus: "IN_SERVICE", quantity: 1, status: "BOOKED",
  clashNote: null, overrideReason: null, overrideAt: null, releasedReason: null, releasedAt: null, slotStatus: "SCHEDULED", clashesNow: false, clashNowNote: null, venueNote: null, createdAt: "", ...over,
})
const wrap = (ui: React.ReactNode) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
let list: unknown[] = []

beforeEach(() => {
  admin = true; get.mockReset(); post.mockReset(); list = [booking()]
  get.mockImplementation((url: string) => Promise.resolve({
    data: { data: /site-resources$/.test(url) ? [{ id: "r1", resourceNo: "RES-00001", name: "Welding rig", quantity: 1, shareable: false, status: "IN_SERVICE" }] : list },
  }))
  post.mockImplementation((url: string) => Promise.resolve({ data: { data: /check$/.test(url) ? { ok: true, clash: null } : booking() } }))
})
afterEach(cleanup)

const open = async () => { wrap(<TrainProvSlotResources slotId="s1" siteId="site1" canManage />); fireEvent.click(screen.getByText("Resources")) }

describe("slot resources", () => {
  it("says booking is not a standard and shows what is booked", async () => {
    await open()
    expect(await screen.findByText(/does not show that it meets any standard/)).toBeTruthy()
    expect(await screen.findByText(/Welding rig/)).toBeTruthy()
    expect(screen.getByText("Booked")).toBeTruthy()
  })

  it("will not book without choosing a resource", async () => {
    list = []
    await open()
    fireEvent.click(await screen.findByText("Book a resource"))
    fireEvent.click(screen.getByText("Book resource"))
    expect((await screen.findByRole("alert")).textContent).toBe("Choose the resource")
    expect(post).not.toHaveBeenCalled()
  })

  it("books a free resource after a clean check", async () => {
    list = []
    await open()
    fireEvent.click(await screen.findByText("Book a resource"))
    await screen.findByRole("option", { name: /Welding rig/ })
    fireEvent.change(screen.getByLabelText("Resource"), { target: { value: "r1" } })
    fireEvent.click(screen.getByText("Book resource"))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringMatching(/slots\/s1$/), { resourceId: "r1", quantity: 1, overrideReason: null }))
  })

  it("shows a clash and lets an administrator approve it with a reason", async () => {
    list = []
    post.mockImplementation((url: string) => Promise.resolve({ data: { data: /check$/.test(url) ? { ok: false, clash: "Welding rig: 1 of 1 already booked at that time" } : booking() } }))
    await open()
    fireEvent.click(await screen.findByText("Book a resource"))
    await screen.findByRole("option", { name: /Welding rig/ })
    fireEvent.change(screen.getByLabelText("Resource"), { target: { value: "r1" } })
    fireEvent.click(screen.getByText("Book resource"))
    expect((await screen.findByRole("alert")).textContent).toContain("already booked at that time")
    fireEvent.click(screen.getByText("Book anyway"))
    expect((await screen.findAllByRole("alert")).some(a => /at least 10/.test(a.textContent ?? ""))).toBe(true)
    fireEvent.change(screen.getByLabelText("Why is the clash acceptable?"), { target: { value: "Rig is split between two groups" } })
    fireEvent.click(screen.getByText("Book anyway"))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringMatching(/slots\/s1$/), expect.objectContaining({ overrideReason: "Rig is split between two groups" })))
  })

  it("tells someone who is not an administrator that only an administrator can approve a clash", async () => {
    admin = false; list = []
    post.mockImplementation((url: string) => Promise.resolve({ data: { data: /check$/.test(url) ? { ok: false, clash: "Welding rig: 1 of 1 already booked at that time" } : booking() } }))
    await open()
    fireEvent.click(await screen.findByText("Book a resource"))
    await screen.findByRole("option", { name: /Welding rig/ })
    fireEvent.change(screen.getByLabelText("Resource"), { target: { value: "r1" } })
    fireEvent.click(screen.getByText("Book resource"))
    expect(await screen.findByText("Only an administrator can approve a clash.")).toBeTruthy()
    expect(screen.queryByText("Book anyway")).toBeNull()
  })

  it("needs a reason to release and prompts on a clash that appeared later", async () => {
    list = [booking({ clashesNow: true, clashNowNote: "Welding rig: 2 of 1 already booked" })]
    await open()
    expect(await screen.findByText(/a prompt to check, not a finding/)).toBeTruthy()
    fireEvent.click(screen.getByText("Release"))
    fireEvent.click(screen.getByText("Release booking"))
    expect((await screen.findAllByRole("alert")).some(a => /at least 10/.test(a.textContent ?? ""))).toBe(true)
    fireEvent.change(screen.getByLabelText("Why is it released?"), { target: { value: "The practical was moved" } })
    fireEvent.click(screen.getByText("Release booking"))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringMatching(/b1\/release$/), { reason: "The practical was moved" }))
  })
})
