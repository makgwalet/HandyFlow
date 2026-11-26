// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TrainProvPriceBook from "./TrainProvPriceBook"
import TrainProvFeeSuggestion from "./TrainProvFeeSuggestion"

const get = vi.fn()
const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))

const line = (over: object = {}) => ({
  id: "p1", programmeVersionId: "v1", funderId: null, funderName: null, feeType: "TUITION", description: null, amount: 1500, includesVat: true, validFrom: "2020-03-01", validTo: null,
  sourceNote: "Price list of 2020 as approved", status: "ACTIVE", withdrawnReason: null, inForceToday: true, createdAt: "", ...over,
})
const wrap = (ui: React.ReactNode) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
let list: unknown[] = []

beforeEach(() => {
  get.mockReset(); post.mockReset(); list = [line()]
  get.mockImplementation((url: string) => Promise.resolve({
    data: { data: /suggest$/.test(url)
      ? { enrolmentId: "e1", on: "2020-03-05", note: "A suggestion from the price book, not an invoice. Confirm it, or enter another amount with a reason.",
          rows: [{ priceLineId: "p1", feeType: "TUITION", description: null, amount: 1500, includesVat: true, general: true, funderName: null, sourceNote: "Price list of 2020 as approved" }] }
      : /funders$/.test(url) ? [{ id: "f1", name: "Skills fund" }] : list },
  }))
  post.mockResolvedValue({ data: { data: line() } })
})
afterEach(cleanup)

const open = async (admin = true) => { wrap(<TrainProvPriceBook versionId="v1" isAdmin={admin} />); fireEvent.click(screen.getByText("Fees (price book)")) }

describe("price book", () => {
  it("says nothing is seeded or billed and shows the fee with its source", async () => {
    await open()
    expect(await screen.findByText(/Nothing is seeded and nothing is billed from here/)).toBeTruthy()
    expect(await screen.findByText(/Tuition: R1 500,00 including VAT · any funder · from 2020-03-01, no end date/)).toBeTruthy()
    expect(screen.getByText(/Source: Price list of 2020 as approved/)).toBeTruthy()
  })

  it("only lets an administrator change it", async () => {
    await open(false)
    expect(await screen.findByText("Only an administrator can add or withdraw a fee.")).toBeTruthy()
    expect(screen.queryByText("Add a fee")).toBeNull()
    expect(screen.queryByText("Withdraw")).toBeNull()
  })

  it("will not save without saying whether VAT is included", async () => {
    list = []
    await open()
    fireEvent.click(await screen.findByText("Add a fee"))
    fireEvent.change(screen.getByLabelText("Kind of fee"), { target: { value: "TUITION" } })
    fireEvent.change(screen.getByLabelText("Amount"), { target: { value: "1500" } })
    fireEvent.click(screen.getByText("Save fee"))
    expect((await screen.findByRole("alert")).textContent).toBe("Say whether the amount includes VAT")
    expect(post).not.toHaveBeenCalled()
  })

  it("adds a fee", async () => {
    list = []
    await open()
    fireEvent.click(await screen.findByText("Add a fee"))
    fireEvent.change(screen.getByLabelText("Kind of fee"), { target: { value: "TUITION" } })
    fireEvent.change(screen.getByLabelText("Amount"), { target: { value: "1500.50" } })
    fireEvent.change(screen.getByLabelText("Includes VAT"), { target: { value: "no" } })
    fireEvent.change(screen.getByLabelText("Where does the fee come from?"), { target: { value: "Price list of 2020 as approved" } })
    fireEvent.click(screen.getByText("Save fee"))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringMatching(/price-lines$/), expect.objectContaining({ programmeVersionId: "v1", feeType: "TUITION", amount: 1500.5, includesVat: false, funderId: null })))
  })

  it("needs a reason to withdraw", async () => {
    await open()
    fireEvent.click(await screen.findByText("Withdraw"))
    fireEvent.click(screen.getByText("Withdraw fee"))
    expect((await screen.findByRole("alert")).textContent).toContain("at least 10")
    fireEvent.change(screen.getByLabelText("Why is it withdrawn?"), { target: { value: "Replaced by the new price list" } })
    fireEvent.click(screen.getByText("Withdraw fee"))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringMatching(/p1\/withdraw$/), { reason: "Replaced by the new price list" }))
  })
})

describe("fee suggestion", () => {
  it("is a suggestion and not an invoice", async () => {
    wrap(<TrainProvFeeSuggestion enrolmentId="e1" />)
    fireEvent.click(screen.getByText("Fee suggestion from the price book"))
    expect(await screen.findByText(/not an invoice/)).toBeTruthy()
    expect(await screen.findByText(/Tuition: R1 500,00 including VAT/)).toBeTruthy()
  })
})
