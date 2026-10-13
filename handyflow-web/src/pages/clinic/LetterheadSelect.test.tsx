import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"

const get = vi.fn()
// A plain function for the failing case: vitest's spy would report the error it threw as the test's own.
let failing = 0
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => { if (failing) { failing++; return Promise.reject(new Error("403")) } return get(...a) } } }))
import LetterheadSelect from "./LetterheadSelect"

const head = (id: string, name: string, o: any = {}) => ({ id, name, branchName: null, practiceName: "P", lines: [], isDefault: false, active: true, hasLogo: false, ...o })
beforeEach(() => { get.mockReset(); failing = 0 })
afterEach(cleanup)

describe("LetterheadSelect", () => {
  it("is hidden when there is nothing to choose between", async () => {
    get.mockResolvedValue({ data: { data: [head("a", "Main", { isDefault: true })] } })
    const { container } = render(<LetterheadSelect id="x" value="" onChange={() => {}} />)
    await waitFor(() => expect(get).toHaveBeenCalled())
    expect(container.textContent).toBe("")
  })
  it("offers the practice default and each active letterhead, and reports the choice", async () => {
    get.mockResolvedValue({ data: { data: [head("a", "Main", { isDefault: true }), head("b", "North", { branchName: "Northside" }), head("c", "Old", { active: false })] } })
    const onChange = vi.fn()
    render(<LetterheadSelect id="x" value="" onChange={onChange} />)
    const sel = await screen.findByLabelText("Letterhead")
    expect(Array.from((sel as HTMLSelectElement).options).map(o => o.textContent)).toEqual(["The practice default", "Main (default)", "North (Northside)"])
    fireEvent.change(sel, { target: { value: "b" } })
    expect(onChange).toHaveBeenCalledWith("b")
  })
  it("shows nothing, rather than failing, when the lookup fails", async () => {
    failing = 1
    const { container } = render(<LetterheadSelect id="x" value="" onChange={() => {}} />)
    await waitFor(() => expect(failing).toBeGreaterThan(1))
    await new Promise(r => setTimeout(r, 20))   // let the failed lookup settle before the test ends
    expect(container.textContent).toBe("")
  })
})
