import { describe, it, expect, vi, afterEach } from "vitest"
import { render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const createSite = vi.fn()
vi.mock("../../api/facilities.api", () => ({ facilitiesApi: { createSite: (...a: unknown[]) => createSite(...a), updateSite: vi.fn() } }))
vi.mock("../../store/toast.store", () => ({ errorMessage: (e: unknown) => String(e), toast: { success: vi.fn(), error: vi.fn() } }))

import { SiteFormDialog } from "./forms"

const mount = () => render(<QueryClientProvider client={new QueryClient()}><SiteFormDialog onClose={() => {}} /></QueryClientProvider>)

describe("site form", () => {
  afterEach(cleanup)

  it("keeps letters out of the postal code and offers the nine provinces", () => {
    mount()
    const postal = screen.getByLabelText("Postal code") as HTMLInputElement
    fireEvent.change(postal, { target: { value: "21a9b6" } })
    expect(postal.value).toBe("2196")
    const province = screen.getByLabelText("Province") as HTMLSelectElement
    expect(Array.from(province.options).map(o => o.value).filter(Boolean)).toHaveLength(9)
  })

  it("says so when the postal code is not four digits and will not save", async () => {
    mount()
    fireEvent.change(screen.getByLabelText("Postal code"), { target: { value: "21" } })
    expect((await screen.findByRole("alert")).textContent).toContain("four digits")
  })

  it("sends the chosen province and postal code", async () => {
    createSite.mockResolvedValue({})
    mount()
    const name = document.querySelector("input[maxlength='200']") as HTMLInputElement
    fireEvent.change(name, { target: { value: "Midrand Depot" } })
    fireEvent.change(screen.getByLabelText("Province"), { target: { value: "Gauteng" } })
    fireEvent.change(screen.getByLabelText("Postal code"), { target: { value: "1685" } })
    fireEvent.click(screen.getByText("Save"))
    await waitFor(() => expect(createSite).toHaveBeenCalled())
    expect(createSite.mock.calls[0][0].address).toMatchObject({ province: "Gauteng", postalCode: "1685" })
  })
})
