import { describe, it, expect, vi, afterEach } from "vitest"
import { render, screen, fireEvent, cleanup } from "@testing-library/react"
import SearchPick, { filterOptions } from "./SearchPick"

afterEach(cleanup)
const many = Array.from({ length: 12 }, (_, i) => [`id${i}`, i === 3 ? "Zeta Facilities" : `Client ${i}`] as const)

describe("SearchPick", () => {
  it("is a plain drop-down for a short list", () => {
    render(<SearchPick label="Type" value="" onChange={() => {}} options={[["a", "A"], ["b", "B"]]} />)
    expect(screen.queryByLabelText("Search Type")).toBeNull()
    expect(screen.getByLabelText("Type")).toBeTruthy()
  })
  it("gets a filter box for a long list, keeps the chosen one, and says when nothing matches", () => {
    const onChange = vi.fn()
    render(<SearchPick label="Client" value="id5" onChange={onChange} options={many} blank="Choose" />)
    const box = screen.getByLabelText("Search Client")
    fireEvent.change(box, { target: { value: "zeta" } })
    const opts = Array.from((screen.getByLabelText("Client") as HTMLSelectElement).options).map(o => o.text)
    expect(opts).toEqual(["Choose", "Zeta Facilities", "Client 5"])
    fireEvent.change(screen.getByLabelText("Client"), { target: { value: "id3" } })
    expect(onChange).toHaveBeenCalledWith("id3")
    fireEvent.change(box, { target: { value: "nonsense" } })
    expect(screen.getByText(/Nothing matches "nonsense"/)).toBeTruthy()
  })
  it("filters case-insensitively", () => {
    expect(filterOptions(many, " ZETA ", "").map(o => o[0])).toEqual(["id3"])
    expect(filterOptions(many, "", "")).toHaveLength(12)
  })
})
