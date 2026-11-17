import React from "react";
import { fireEvent, render, screen } from "@testing-library/react-native";
import { CompleteScreen } from "@driver/CompleteScreen";
import { FailedAttemptScreen } from "@driver/FailedAttemptScreen";

const mockEnqueue = jest.fn(async () => ({}));
const mockPopTo = jest.fn();

jest.mock("@/offline/OutboxProvider", () => ({ useOutbox: () => ({ enqueue: mockEnqueue }) }));
jest.mock("@driver/useLoads", () => ({
  useLoads: () => ({ loads: [{ id: "L1", status: "IN_TRANSIT", fuelType: "DIESEL_50", litresOrdered: 10000, loadedLitres: 10000, failedAttempts: 0 }] }),
}));
jest.mock("@/ui/Screen", () => {
  const { View, Text } = require("react-native");
  return { Screen: ({ title, children }: any) => <View><Text>{title}</Text>{children}</View>, Card: ({ children }: any) => <View>{children}</View> };
});

const props: any = { route: { params: { loadId: "L1" } }, navigation: { popTo: mockPopTo } };
beforeEach(() => { mockEnqueue.mockClear(); mockPopTo.mockClear(); });

describe("CompleteScreen", () => {
  it("blocks a save with no receiver and shows why", async () => {
    await render(<CompleteScreen {...props} />);
    await fireEvent.press(screen.getByLabelText("Save delivery"));
    expect(screen.getByText(/name of the person who received/i)).toBeTruthy();
    expect(mockEnqueue).not.toHaveBeenCalled();
  });
  it("queues the delivery with the load id and returns to the load", async () => {
    await render(<CompleteScreen {...props} />);
    await fireEvent.changeText(screen.getByLabelText("Received by (name)"), "Sipho");
    await fireEvent.press(screen.getByLabelText("Save delivery"));
    expect(mockEnqueue).toHaveBeenCalledWith("COMPLETE", { loadId: "L1", body: expect.objectContaining({ litresDelivered: 10000, receiverName: "Sipho" }) });
    expect(mockPopTo).toHaveBeenCalledWith("LoadDetail", { loadId: "L1" });
  });
});

describe("FailedAttemptScreen", () => {
  it("needs a reason", async () => {
    await render(<FailedAttemptScreen {...props} />);
    await fireEvent.press(screen.getByLabelText("Save failed delivery"));
    expect(screen.getByText("Pick a reason.")).toBeTruthy();
    expect(mockEnqueue).not.toHaveBeenCalled();
  });
  it("queues the failure with the chosen reason", async () => {
    await render(<FailedAttemptScreen {...props} />);
    await fireEvent.press(screen.getByText(/Site closed/));
    await fireEvent.press(screen.getByLabelText("Save failed delivery"));
    expect(mockEnqueue).toHaveBeenCalledWith("FAILED_ATTEMPT", { loadId: "L1", body: expect.objectContaining({ reasonCode: "SITE_CLOSED" }) });
  });
});
