import { screenFromData } from "@/push";

describe("opening a screen from an alert", () => {
  it("opens only screens the app lists", () => {
    expect(screenFromData({ screen: "Swaps" })).toBe("Swaps");
    expect(screenFromData({ screen: "Home" })).toBe("Home");
  });
  it("ignores anything else, including screens that need parameters", () => {
    expect(screenFromData({ screen: "Scan" })).toBeNull();
    expect(screenFromData({ screen: "Incident" })).toBeNull();
    expect(screenFromData({ screen: 42 })).toBeNull();
    expect(screenFromData({})).toBeNull();
    expect(screenFromData(null)).toBeNull();
    expect(screenFromData(undefined)).toBeNull();
  });
});
