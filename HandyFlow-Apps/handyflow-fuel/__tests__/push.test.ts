import { registerForPush, targetFromData } from "@/push";

const mockRegister = jest.fn(async () => ({}));
let mockIsDevice = true;
let mockGranted = true;
let mockCanAsk = true;
let mockProject: string | undefined = "proj-1";
const mockToken = jest.fn(async () => ({ data: "ExponentPushToken[abcdefghijklmnop]" }));

jest.mock("@/api/push", () => ({ registerPushToken: (...a: unknown[]) => (mockRegister as any)(...a) }));
jest.mock("@/auth/deviceStore", () => ({ getDeviceId: async () => "dev-1" }));
jest.mock("expo-device", () => ({ get isDevice() { return mockIsDevice; } }));
jest.mock("expo-constants", () => ({ __esModule: true, default: { get expoConfig() { return { extra: { eas: { projectId: mockProject } } }; }, easConfig: undefined } }));
jest.mock("expo-notifications", () => ({
  getPermissionsAsync: async () => ({ granted: mockGranted }),
  requestPermissionsAsync: async () => ({ granted: mockCanAsk }),
  getExpoPushTokenAsync: (...a: unknown[]) => (mockToken as any)(...a),
  setNotificationHandler: jest.fn(),
  setNotificationChannelAsync: jest.fn(),
  AndroidImportance: { HIGH: 4 },
}));

const ID = "3f2b8a40-7c1e-4d55-9a6b-0a1b2c3d4e5f";

beforeEach(() => { mockRegister.mockClear(); mockToken.mockClear(); mockIsDevice = true; mockGranted = true; mockCanAsk = true; mockProject = "proj-1"; });

describe("opening a screen from an alert", () => {
  it("opens the screens the server names", () => {
    expect(targetFromData({ screen: "StayRequests" })).toEqual({ screen: "StayRequests" });
    expect(targetFromData({ screen: "DriverLoads" })).toEqual({ screen: "DriverLoads" });
    expect(targetFromData({ screen: "LoadDetail", loadId: ID })).toEqual({ screen: "LoadDetail", params: { loadId: ID } });
  });
  it("opens the tank and the shift an alert names", () => {
    expect(targetFromData({ screen: "TankDetail", tankId: ID })).toEqual({ screen: "TankDetail", params: { tankId: ID } });
    expect(targetFromData({ screen: "ShiftDetail", shiftId: ID })).toEqual({ screen: "ShiftDetail", params: { shiftId: ID } });
  });
  it("ignores anything else, including a load with no valid id", () => {
    expect(targetFromData({ screen: "LoadDetail" })).toBeNull();
    expect(targetFromData({ screen: "LoadDetail", loadId: "../../x" })).toBeNull();
    expect(targetFromData({ screen: "CloseShift", shiftId: ID })).toBeNull();
    expect(targetFromData({ screen: "TankDetail", tankId: "../x" })).toBeNull();
    expect(targetFromData({ screen: "ShiftDetail" })).toBeNull();
    expect(targetFromData({ screen: 42 })).toBeNull();
    expect(targetFromData({})).toBeNull();
    expect(targetFromData(null)).toBeNull();
    expect(targetFromData(undefined)).toBeNull();
  });
});

describe("registering the phone", () => {
  it("gives the server the token and the phone's id", async () => {
    expect(await registerForPush()).toBe(true);
    expect(mockToken).toHaveBeenCalledWith({ projectId: "proj-1" });
    expect(mockRegister).toHaveBeenCalledWith("dev-1", "ExponentPushToken[abcdefghijklmnop]");
  });
  it("does nothing on an emulator", async () => { mockIsDevice = false; expect(await registerForPush()).toBe(false); expect(mockRegister).not.toHaveBeenCalled(); });
  it("does nothing when the person says no", async () => { mockGranted = false; mockCanAsk = false; expect(await registerForPush()).toBe(false); expect(mockRegister).not.toHaveBeenCalled(); });
  it("asks once when permission is not yet given", async () => { mockGranted = false; mockCanAsk = true; expect(await registerForPush()).toBe(true); });
  it("does nothing without an EAS project id", async () => { mockProject = undefined; expect(await registerForPush()).toBe(false); expect(mockToken).not.toHaveBeenCalled(); });
  it("never throws when the server refuses", async () => { mockRegister.mockRejectedValueOnce(new Error("boom")); expect(await registerForPush()).toBe(false); });
});
