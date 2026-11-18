module.exports = {
  preset: "jest-expo",
  // The first run on a cold cache transforms the whole React Native tree; 5 s is too tight for the screen tests on a laptop.
  testTimeout: 30000,
  moduleNameMapper: {
    "^@/(.*)$": "<rootDir>/src/$1",
    "^@driver/(.*)$": "<rootDir>/packages/fuel-driver/src/$1",
    "^@depot/(.*)$": "<rootDir>/packages/fuel-depot/src/$1",
    "^@overview/(.*)$": "<rootDir>/packages/fuel-overview/src/$1",
    "^@forecourt/(.*)$": "<rootDir>/packages/fuel-forecourt/src/$1",
    "^@truckstop/(.*)$": "<rootDir>/packages/fuel-truckstop/src/$1",
  },
  transformIgnorePatterns: [
    "node_modules/(?!((jest-)?react-native|@react-native(-community)?|expo(nent)?|@expo(nent)?/.*|@expo-google-fonts/.*|react-navigation|@react-navigation/.*|@sentry/react-native|native-base|react-native-svg))",
  ],
};
