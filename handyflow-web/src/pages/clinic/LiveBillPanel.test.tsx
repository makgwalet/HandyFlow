import { afterEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen } from "@testing-library/react"
import LiveBillPanel, { customBillLine } from "./LiveBillPanel"

afterEach(cleanup)

describe("customBillLine", () => {
  it("needs a description", () => expect(customBillLine({ description: "  ", quantity: "1", unitPrice: "5" })).toEqual({ error: "Describe the item" }))
  it("needs a quantity above zero", () => {
    expect(customBillLine({ description: "Gauze", quantity: "0", unitPrice: "5" })).toHaveProperty("error")
    expect(customBillLine({ description: "Gauze", quantity: "-1", unitPrice: "5" })).toHaveProperty("error")
  })
  it("refuses a negative price", () => expect(customBillLine({ description: "Gauze", quantity: "1", unitPrice: "-2" })).toHaveProperty("error"))
  it("treats an empty quantity as 1 and an empty price as 0", () => {
    const r: any = customBillLine({ description: "Gauze", quantity: "", unitPrice: "" })
    expect(r.line).toMatchObject({ quantity: 1, unitPrice: 0, gross: 0, type: "CONSUMABLE" })
  })
  it("rounds the gross to cents", () => {
    const r: any = customBillLine({ description: "Gloves", quantity: "3", unitPrice: "12.335" })
    expect(r.line.gross).toBe(37.01)
  })
})

const lines: any[] = [
  { id: "consult-0191", type: "CONSULTATION", description: "Consultation — intermediate", tariffCode: "0191", quantity: 1, unitPrice: 520, gross: 520 },
  { id: "x1", type: "PROCEDURE", description: "Injection IM", tariffCode: "0115", quantity: 1, unitPrice: 85, gross: 85 },
]
const show = (add = vi.fn(), remove = vi.fn()) => ({ add, remove, ...render(
  <LiveBillPanel billLines={lines} billTotal={605} removeBillLine={remove} addBillLine={add} />) })

describe("LiveBillPanel", () => {
  it("lists the lines and the total; the consultation line cannot be removed", () => {
    const { remove } = show()
    expect(screen.getByText("Injection IM")).toBeTruthy()
    expect(screen.getByText(/2 items/)).toBeTruthy()
    expect(screen.getAllByRole("button").filter(b => !b.textContent)).toHaveLength(1)
    fireEvent.click(screen.getAllByRole("button").find(b => !b.textContent)!)
    expect(remove).toHaveBeenCalledWith("x1")
  })
  it("adds a valid custom item and refuses an empty one", () => {
    const { add } = show()
    fireEvent.click(screen.getByText("Add custom item"))
    fireEvent.click(screen.getByText("Add"))
    expect(screen.getByRole("alert").textContent).toBe("Describe the item")
    expect(add).not.toHaveBeenCalled()
    fireEvent.change(screen.getByPlaceholderText("Description"), { target: { value: "Gauze" } })
    fireEvent.change(screen.getByPlaceholderText("R price"), { target: { value: "12.5" } })
    fireEvent.click(screen.getByText("Add"))
    expect(add).toHaveBeenCalledWith({ type: "CONSUMABLE", description: "Gauze", quantity: 1, unitPrice: 12.5, gross: 12.5 })
    expect(screen.queryByPlaceholderText("Description")).toBeNull()
  })
})
