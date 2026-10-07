import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn()
let canWrite = true
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => canWrite }))
import MyDayPanel, { countStatus } from "./MyDayPanel"

const show = (nav = vi.fn()) => ({ nav, ...render(
  <QueryClientProvider client={new QueryClient()}><MyDayPanel onNavigate={nav} /></QueryClientProvider>) })

beforeEach(() => { get.mockReset(); canWrite = true
  get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.includes("drafts")
    ? [{ status: "DRAFT" }, { status: "DRAFT" }]
    : [{ status: "READY_FOR_DOCTOR" }, { status: "READY_FOR_DOCTOR" }, { status: "RETURNED_TO_NURSE" }] } })) })
afterEach(cleanup)

describe("countStatus", () => {
  it("counts matching items and returns undefined while unknown", () => {
    expect(countStatus([{ status: "A" }, { status: "B" }, { status: "A" }], "A")).toBe(2)
    expect(countStatus(undefined, "A")).toBeUndefined()
  })
})

describe("MyDayPanel", () => {
  it("shows the counts and links each tile to its screen", async () => {
    const { nav } = show()
    expect(await screen.findByText("Waiting for a doctor")).toBeTruthy()
    await waitFor(() => expect(screen.getAllByText("2")).toHaveLength(2))   // drafts and waiting-for-doctor
    expect(screen.getByText("1")).toBeTruthy()                                // returned to nurse
    expect(get.mock.calls.some(c => String(c[0]).includes("drafts?mine=true"))).toBe(true)
    fireEvent.click(screen.getByText("Returned to nurse"))
    expect(nav).toHaveBeenCalledWith("handoff")
    fireEvent.click(screen.getByText("My unfinished drafts"))
    expect(nav).toHaveBeenCalledWith("drafts")
  })
  it("shows a dash, not zero, for a count that failed to load", async () => {
    get.mockImplementation(() => Promise.reject(new Error("x")))
    show()
    expect(await screen.findAllByText("—")).toHaveLength(4)
  })
  it("is hidden without clinical write permission and asks for nothing", () => {
    canWrite = false
    const { container } = show()
    expect(container.textContent).toBe("")
    expect(get).not.toHaveBeenCalled()
  })
})
