import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"

const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { post: (...a: any[]) => post(...a) } }))
import { AllergyWarning, missingReasons, useAllergyChecks, type AllergyResult } from "./PrescriptionAllergyCheck"

const hit: AllergyResult = { name: "Penicillin V", alerts: [{ allergen: "Penicillin", severity: "SEVERE", reaction: "Rash" }], note: "Name only." }

beforeEach(() => post.mockReset())
afterEach(cleanup)

describe("missingReasons", () => {
  const rx = [{ id: "1", medicationName: "Penicillin V" }, { id: "2", medicationName: "Paracetamol" }]
  it("lists matched medicines with no reason", () => {
    expect(missingReasons(rx, { "1": hit })).toEqual(["Penicillin V"])
  })
  it("accepts a reason, ignoring blanks and spaces", () => {
    expect(missingReasons([{ ...rx[0], allergyReason: "  " }], { "1": hit })).toEqual(["Penicillin V"])
    expect(missingReasons([{ ...rx[0], allergyReason: "Tolerated before" }], { "1": hit })).toEqual([])
  })
  it("ignores a result for a name the prescriber has since changed", () => {
    expect(missingReasons([{ id: "1", medicationName: "Amoxicillin" }], { "1": hit })).toEqual([])
  })
})

describe("AllergyWarning", () => {
  it("renders nothing without a match", () => {
    const { container } = render(<AllergyWarning result={{ ...hit, alerts: [] }} reason="" onReason={() => {}} />)
    expect(container.textContent).toBe("")
  })
  it("shows the match, the standing note, and takes a reason", () => {
    const onReason = vi.fn()
    render(<AllergyWarning result={hit} reason="" onReason={onReason} />)
    expect(screen.getByRole("alert").textContent).toContain("Penicillin (severe): Rash")
    expect(screen.getByText("Name only.")).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Reason to prescribe anyway"), { target: { value: "ok" } })
    expect(onReason).toHaveBeenCalledWith("ok")
  })
})

function Probe({ id, rx }: { id: string | null; rx: { id: string; medicationName: string }[] }) {
  const r = useAllergyChecks(id, rx)
  return <div data-testid="n">{Object.values(r).map(x => x.alerts.length).join(",")}</div>
}

describe("useAllergyChecks", () => {
  it("does nothing until the consultation draft exists", async () => {
    render(<Probe id={null} rx={[{ id: "1", medicationName: "Penicillin V" }]} />)
    await new Promise(r => setTimeout(r, 700))
    expect(post).not.toHaveBeenCalled()
  })
  it("asks once per medicine name and exposes the matches", async () => {
    post.mockResolvedValue({ data: { data: { alerts: [{ allergen: "Penicillin" }], note: "n" } } })
    const rx = [{ id: "1", medicationName: " Penicillin V " }]
    const { rerender } = render(<Probe id="c1" rx={rx} />)
    await waitFor(() => expect(screen.getByTestId("n").textContent).toBe("1"), { timeout: 3000 })
    expect(post).toHaveBeenCalledWith("/api/v1/clinic/consultations/c1/prescriptions/allergy-check", { medicationName: "Penicillin V" })
    rerender(<Probe id="c1" rx={[...rx]} />)
    await new Promise(r => setTimeout(r, 700))
    expect(post).toHaveBeenCalledTimes(1)
  })
})
