import React from 'react';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { Ionicons } from '@expo/vector-icons';
import { useAuthStore } from '../store/authStore';
import { ROLE_TABS } from './roleTabs';
import { colors } from '../theme/tokens';

const Tab = createBottomTabNavigator();

export function MainNavigator() {
  const role = useAuthStore((s) => s.user?.role);
  if (!role) return null;

  const tabs = ROLE_TABS[role];

  return (
    <Tab.Navigator
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: colors.primaryDark,
        tabBarInactiveTintColor: colors.textMuted,
        tabBarStyle: { borderTopColor: colors.border },
      }}
    >
      {tabs.map((tab) => (
        <Tab.Screen
          key={tab.name}
          name={tab.name}
          component={tab.component}
          options={{
            tabBarLabel: tab.label,
            tabBarIcon: ({ color, size }: { color: string; size: number }) => (
              <Ionicons name={tab.icon} color={color} size={size} />
            ),
          }}
        />
      ))}
    </Tab.Navigator>
  );
}
