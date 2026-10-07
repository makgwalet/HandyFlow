import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"

let allowed = true
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => allowed }))
const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { post: (...a: any[]) => post(...a) } }))
import RxFillControl, { fillSummary } from "./RxFillControl"

beforeEach(() => { allowed = true; post.mockReset(); post.mockResolvedValue({ data: {} }) })
afterEach(cleanup)

describe("fillSummary", () => {
  it("uses the server's counts when present", () => {
    expect(fillSummary({ id: "1", repeats: 2, dispensed: false, fillsUsed: 1, fillsRemaining: 2 })).toEqual({ used: 1, total: 3, remaining: 2 })
  })
  it("derives them for older responses", () => {
    expect(fillSummary({ id: "1", repeats: 0, dispensed: true })).toEqual({ used: 1, total: 1, remaining: 0 })
    expect(fillSummary({ id: "1", repeats: 3, dispensed: false })).toEqual({ used: 0, total: 4, remaining: 4 })
  })
})

describe("RxFillControl", () => {
  it("shows progress and records a fill, then asks the page to refresh", async () => {
    const onRecorded = vi.fn()
    render(<RxFillControl rx={{ id: "rx1", repeats: 1, dispensed: false, fillsUsed: 0, fillsRemaining: 2 }} onRecorded={onRecorded} />)
    expect(screen.getByText(/0 of 2 fills used, 2 left/)).toBeTruthy()
    fireEvent.click(screen.getByText("Record fill"))
    await waitFor(() => expect(onRecorded).toHaveBeenCalled())
    expect(post).toHaveBeenCalledWith("/api/v1/clinic/prescriptions/rx1/fills", {})
  })

  it("offers 'Record repeat' after the first fill", () => {
    render(<RxFillControl rx={{ id: "rx1", repeats: 1, dispensed: false, fillsUsed: 1, fillsRemaining: 1 }} onRecorded={() => {}} />)
    expect(screen.getByText("Record repeat")).toBeTruthy()
  })

  it("shows no button when every fill is used or the user may not record fills", () => {
    const { rerender } = render(<RxFillControl rx={{ id: "1", repeats: 0, dispensed: true, fillsUsed: 1, fillsRemaining: 0 }} onRecorded={() => {}} />)
    expect(screen.queryByRole("button")).toBeNull()
    expect(screen.getByText(/1 of 1 fill used/)).toBeTruthy()
    allowed = false
    rerender(<RxFillControl rx={{ id: "2", repeats: 1, dispensed: false, fillsUsed: 0, fillsRemaining: 2 }} onRecorded={() => {}} />)
    expect(screen.queryByRole("button")).toBeNull()
  })

  it("shows the server's refusal and does not refresh", async () => {
    post.mockRejectedValue({ response: { data: { message: "All 1 authorised fills of this prescription have been used." } } })
    const onRecorded = vi.fn()
    render(<RxFillControl rx={{ id: "rx1", repeats: 0, dispensed: false, fillsUsed: 0, fillsRemaining: 1 }} onRecorded={onRecorded} />)
    fireEvent.click(screen.getByText("Record fill"))
    expect(await screen.findByRole("alert")).toBeTruthy()
    expect(onRecorded).not.toHaveBeenCalled()
  })
})
