import React from 'react';
import { Text } from 'react-native';
import { NavigationContainer, DefaultTheme, DarkTheme, NavigatorScreenParams } from '@react-navigation/native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { useTheme } from '../design-system/ThemeProvider';
import { HomeScreen } from '../screens/HomeScreen';
import { PlaceholderScreen } from '../screens/PlaceholderScreen';
import { ProductionStackNavigator, ProductionStackParamList } from './ProductionStackNavigator';

export type RootTabParamList = {
  Home: undefined;
  FarmMap: undefined;
  Production: NavigatorScreenParams<ProductionStackParamList> | undefined;
  Tasks: undefined;
  More: undefined;
};

const Tab = createBottomTabNavigator<RootTabParamList>();

const TAB_ICONS: Record<keyof RootTabParamList, string> = {
  Home: '🏠',
  FarmMap: '🗺️',
  Production: '🐄',
  Tasks: '✅',
  More: '☰',
};

export function RootNavigator() {
  const { colors, scheme } = useTheme();

  const navTheme = {
    ...(scheme === 'dark' ? DarkTheme : DefaultTheme),
    colors: {
      ...(scheme === 'dark' ? DarkTheme.colors : DefaultTheme.colors),
      background: colors.background,
      card: colors.surface,
      text: colors.textPrimary,
      border: colors.border,
      primary: colors.accent,
    },
  };

  return (
    <NavigationContainer theme={navTheme}>
      <Tab.Navigator
        screenOptions={({ route }) => ({
          headerShown: false,
          tabBarActiveTintColor: colors.accent,
          tabBarInactiveTintColor: colors.textSecondary,
          tabBarStyle: { backgroundColor: colors.surface, borderTopColor: colors.border },
          tabBarIcon: () => <Text style={{ fontSize: 20 }}>{TAB_ICONS[route.name as keyof RootTabParamList]}</Text>,
        })}
      >
        <Tab.Screen name="Home" component={HomeScreen} options={{ title: 'Today' }} />
        <Tab.Screen name="FarmMap" options={{ title: 'Farm Map' }}>
          {() => <PlaceholderScreen title="Farm Map" />}
        </Tab.Screen>
        <Tab.Screen name="Production" component={ProductionStackNavigator} options={{ title: 'Livestock' }} />
        <Tab.Screen name="Tasks" options={{ title: 'Tasks' }}>
          {() => <PlaceholderScreen title="Tasks" />}
        </Tab.Screen>
        <Tab.Screen name="More" options={{ title: 'More' }}>
          {() => <PlaceholderScreen title="More" />}
        </Tab.Screen>
      </Tab.Navigator>
    </NavigationContainer>
  );
}
