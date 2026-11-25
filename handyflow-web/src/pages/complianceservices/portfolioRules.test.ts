import { describe, it, expect } from "vitest"
import { filtered, linkFor, headline, counts, severityLabel, type ClientEntry } from "./portfolioRules"

const entry = (o: Partial<ClientEntry> = {}): ClientEntry => ({ clientId: "c1", clientName: "Acme", riskRating: null, high: 0, medium: 1, low: 0, signals: [], ...o })

describe("portfolioRules", () => {
  it("filters to clients with urgent signs, and by name", () => {
    const list = [entry({ clientName: "Acme", high: 1 }), entry({ clientId: "c2", clientName: "Beta" })]
    expect(filtered(list, "HIGH", "").map(c => c.clientName)).toEqual(["Acme"])
    expect(filtered(list, "ALL", "bet").map(c => c.clientName)).toEqual(["Beta"])
    expect(filtered(list, "ALL", "").length).toBe(2)
  })
  it("links a sign to the client's tab", () => {
    expect(linkFor("c1", "documents")).toBe("/complianceservices/clients/c1?tab=documents")
  })
  it("words the headline and never says clients comply", () => {
    expect(headline({ activeClients: 0, flaggedClients: 0, withHigh: 0 })).toBe("There are no active clients.")
    expect(headline({ activeClients: 12, flaggedClients: 0, withHigh: 0 })).toMatch(/not a statement that they comply/)
    expect(headline({ activeClients: 12, flaggedClients: 5, withHigh: 2 })).toBe("5 of 12 active clients show signs of trouble, 2 with urgent ones.")
    expect(headline({ activeClients: 12, flaggedClients: 3, withHigh: 0 })).toBe("3 of 12 active clients show signs of trouble.")
  })
  it("counts signs by urgency", () => {
    expect(counts({ high: 2, medium: 0, low: 1 })).toBe("2 urgent, 1 to note")
    expect(counts({ high: 0, medium: 3, low: 0 })).toBe("3 soon")
  })
  it("words severities", () => {
    expect(severityLabel("HIGH")).toBe("Urgent")
    expect(severityLabel("LOW")).toBe("Note")
  })
})
