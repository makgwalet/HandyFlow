import Constants from "expo-constants";

/** Backend base URL, no trailing slash. Set EXPO_PUBLIC_API_BASE_URL per build profile. */
export const API_BASE_URL: string = String(
  (Constants.expoConfig?.extra as { apiBaseUrl?: string } | undefined)?.apiBaseUrl ??
    "http://10.0.2.2:8080",
).replace(/\/+$/, "");
