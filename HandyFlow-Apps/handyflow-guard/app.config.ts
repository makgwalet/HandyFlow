import type { ExpoConfig } from "expo/config";

// One source for build identity. API address comes from the environment so the
// same code runs against local, staging and production without edits.
const apiBaseUrl = process.env.EXPO_PUBLIC_API_BASE_URL ?? "http://10.0.2.2:8080";

// A cloud build (EAS sets EAS_BUILD_PROFILE) must point at a real https server. Refuse to build an app that would
// ship with the placeholder address from eas.json, or with a plain http address a phone's network layer will block.
if (process.env.EAS_BUILD_PROFILE && (!apiBaseUrl.startsWith("https://") || apiBaseUrl.includes("REPLACE"))) {
  throw new Error(`EXPO_PUBLIC_API_BASE_URL must be a real https address for EAS builds (got "${apiBaseUrl}"). Edit eas.json.`);
}

const config: ExpoConfig = {
  name: "HandyFlow Guard",
  slug: "handyflow-guard",
  scheme: "handyflowguard",
  version: "0.1.0",
  orientation: "portrait",
  icon: "./assets/icon.png",
  userInterfaceStyle: "automatic",
  ios: {
    bundleIdentifier: "za.co.handyflow.guard",
    supportsTablet: false,
    infoPlist: {
      NSLocationWhenInUseUsageDescription:
        "HandyFlow Guard records your position at checkpoints and when you raise an alert.",
      NSCameraUsageDescription:
        "HandyFlow Guard uses the camera to scan checkpoint codes and attach incident photos.",
    },
  },
  android: {
    package: "za.co.handyflow.guard",
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
    [
      "expo-location",
      {
        locationWhenInUsePermission:
          "HandyFlow Guard records your position at checkpoints and when you raise an alert.",
      },
    ],
    [
      "expo-image-picker",
      { cameraPermission: "HandyFlow Guard takes photos to attach to incident reports." },
    ],
    ["expo-notifications", { defaultChannel: "default" }],
    [
      "expo-camera",
      { cameraPermission: "HandyFlow Guard uses the camera to scan checkpoint codes." },
    ],
  ],
  extra: {
    apiBaseUrl,
  },
};

export default config;
