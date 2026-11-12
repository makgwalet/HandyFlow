import type { ExpoConfig } from "expo/config";

// One source for build identity. API address comes from the environment so the
// same code runs against local, staging and production without edits.
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
    [
      "expo-camera",
      { cameraPermission: "HandyFlow Guard uses the camera to scan checkpoint codes." },
    ],
  ],
  extra: {
    apiBaseUrl: process.env.EXPO_PUBLIC_API_BASE_URL ?? "http://10.0.2.2:8080",
  },
};

export default config;
