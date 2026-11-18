import type { ExpoConfig } from "expo/config";

// One source for build identity. The API address comes from the environment so the same code runs against
// local, staging and production without edits.
const apiBaseUrl = process.env.EXPO_PUBLIC_API_BASE_URL ?? "http://10.0.2.2:8080";

// A cloud build (EAS sets EAS_BUILD_PROFILE) must point at a real https server.
if (process.env.EAS_BUILD_PROFILE && (!apiBaseUrl.startsWith("https://") || apiBaseUrl.includes("REPLACE"))) {
  throw new Error(`EXPO_PUBLIC_API_BASE_URL must be a real https address for EAS builds (got "${apiBaseUrl}"). Edit eas.json.`);
}

const config: ExpoConfig = {
  name: "HandyFlow Fuel",
  slug: "handyflow-fuel",
  scheme: "handyflowfuel",
  version: "0.1.0",
  orientation: "portrait",
  icon: "./assets/icon.png",
  userInterfaceStyle: "automatic",
  ios: {
    bundleIdentifier: "za.co.handyflow.fuel",
    supportsTablet: false,
    infoPlist: {
      NSLocationWhenInUseUsageDescription: "HandyFlow Fuel records where you are when you save a delivery photo or a delivery.",
      NSCameraUsageDescription: "HandyFlow Fuel uses the camera to photograph delivery proof such as meters, seals and delivery notes.",
    },
  },
  android: {
    package: "za.co.handyflow.fuel",
    adaptiveIcon: {
      backgroundColor: "#E6F4FE",
      foregroundImage: "./assets/android-icon-foreground.png",
      backgroundImage: "./assets/android-icon-background.png",
      monochromeImage: "./assets/android-icon-monochrome.png",
    },
    permissions: ["ACCESS_FINE_LOCATION", "CAMERA"],
    predictiveBackGestureEnabled: false,
  },
  plugins: [
    "expo-secure-store",
    "expo-sqlite",
    ["expo-location", { locationWhenInUsePermission: "HandyFlow Fuel records where you are when you save a delivery photo or a delivery." }],
    ["expo-notifications", { defaultChannel: "default" }],
    ["expo-image-picker", { cameraPermission: "HandyFlow Fuel takes photos of delivery proof." }],
  ],
  extra: { apiBaseUrl },
};

export default config;
