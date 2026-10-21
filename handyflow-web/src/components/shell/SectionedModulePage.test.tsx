import { describe, it, expect, afterEach } from "vitest"
import { render, screen, cleanup, within } from "@testing-library/react"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { Truck } from "lucide-react"
import { SectionedModulePage } from "./SectionedModulePage"
import type { ModuleSections } from "../../navigation/moduleSections"

afterEach(cleanup)
const cfg = (group: string): ModuleSections => ({ moduleKey: "x", basePath: "/x", title: "Fleet", icon: Truck, defaultSection: "a", groups: [{ label: group, sections: [{ id: "a", label: "Vehicles", icon: Truck }] }] })
const show = (group: string) => render(<MemoryRouter initialEntries={["/x/a"]}><Routes><Route path="/x/:section" element={<SectionedModulePage config={cfg(group)} render={() => <p>body</p>} />} /></Routes></MemoryRouter>)

describe("breadcrumbs", () => {
  it("does not repeat the module name when the group has the same name", () => {
    show("Fleet"); const crumbs = within(screen.getByRole("navigation", { name: "Breadcrumb" }))
    expect(crumbs.getAllByText("Fleet")).toHaveLength(1); expect(crumbs.getByText("Vehicles")).toBeTruthy()
  })
  it("keeps a group that has its own name", () => {
    show("Operations"); expect(within(screen.getByRole("navigation", { name: "Breadcrumb" })).getByText("Operations")).toBeTruthy()
  })
})
