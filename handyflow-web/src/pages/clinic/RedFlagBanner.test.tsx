import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { flagReasonProblem, flagSource, splitFlags, type ActiveRedFlag } from "./redFlags"

const flag = (over: Partial<ActiveRedFlag> = {}): ActiveRedFlag => ({
  groupCode: "RESP", groupName: "Respiratory", groupVersion: 2, flagCode: "STRIDOR", label: "Stridor reported",
  severity: "URGENT", message: "Reported by the nurse", reviewed: false, ...over,
})

describe("red flag helpers", () => {
  it("lists open flags first, urgent before info, and keeps reviewed ones apart", () => {
    const s = splitFlags([flag({ flagCode: "A", severity: "INFO" }), flag({ flagCode: "B" }), flag({ flagCode: "C", reviewed: true })])
    expect(s.open.map(f => f.flagCode)).toEqual(["B", "A"])
    expect(s.done.map(f => f.flagCode)).toEqual(["C"])
    expect(s.urgentOpen).toBe(true)
  })
  it("names the source group and version", () => {
    expect(flagSource(flag())).toBe("Respiratory (v2)")
  })
  it("needs a usable reason", () => {
    expect(flagReasonProblem("  ")).not.toBeNull()
    expect(flagReasonProblem("x".repeat(501))).not.toBeNull()
    expect(flagReasonProblem("Seen earlier by the nurse")).toBeNull()
  })
})

const get = vi.fn(), post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a) } }))
import RedFlagBanner from "./RedFlagBanner"

const show = (onOverrideReason = vi.fn()) => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <RedFlagBanner consultationId="c1" onOverrideReason={onOverrideReason} />
  </QueryClientProvider>)

let flags: ActiveRedFlag[] = []
beforeEach(() => {
  flags = [flag()]
  get.mockReset(); post.mockReset()
  get.mockImplementation(() => Promise.resolve({ data: { data: flags } }))
  post.mockResolvedValue({ data: {} })
})
afterEach(cleanup)

describe("RedFlagBanner", () => {
  it("shows the flag with its source and stays until reviewed", async () => {
    show()
    expect(await screen.findByRole("alert", { name: "Red flags to review" })).toBeTruthy()
    expect(screen.getByText(/Stridor reported/)).toBeTruthy()
    expect(screen.getByText("Source: Respiratory (v2)")).toBeTruthy()
    expect(screen.getByText(/does not mean it is resolved/)).toBeTruthy()
  })
  it("Mark reviewed posts the review for that flag", async () => {
    show()
    fireEvent.click(await screen.findByText("Mark reviewed"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/consultations/c1/red-flags/RESP/STRIDOR/review"))
  })
  it("a reviewed flag leaves the red banner and is listed as reviewed", async () => {
    flags = [flag({ reviewed: true, reviewedBy: "Dr Nkosi" })]
    show()
    expect(await screen.findByText(/Reviewed: Stridor reported/)).toBeTruthy()
    expect(screen.queryByRole("alert", { name: "Red flags to review" })).toBeNull()
  })
  it("hands up a reason to sign without reviewing, and clears it again on cancel", async () => {
    const onReason = vi.fn()
    show(onReason)
    fireEvent.click(await screen.findByText("Sign without reviewing…"))
    fireEvent.change(screen.getByLabelText(/Reason for signing without reviewing/), { target: { value: " Seen by the nurse " } })
    expect(onReason).toHaveBeenLastCalledWith("Seen by the nurse")
    fireEvent.click(screen.getByText(/Cancel: I will review them/))
    expect(onReason).toHaveBeenLastCalledWith("")
  })
  it("shows nothing when there are no flags", async () => {
    flags = []
    const { container } = show()
    await waitFor(() => expect(get).toHaveBeenCalled())
    expect(container.textContent).toBe("")
  })
})
