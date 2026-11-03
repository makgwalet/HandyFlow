import React from 'react';
import { View, Text, Pressable, StyleSheet, ScrollView } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useAuthStore } from '../../store/authStore';
import { Card } from '../../components/Card';
import { colors, radii, spacing, typography } from '../../theme/tokens';
import { ROLE_LABEL } from '../../types/domain';

const MENU_ITEMS: { key: string; label: string; icon: keyof typeof Ionicons.glyphMap }[] = [
  { key: 'shift', label: 'My Shift', icon: 'time-outline' },
  { key: 'sync', label: 'Sync Centre', icon: 'sync-outline' },
  { key: 'history', label: 'Transaction History', icon: 'time-outline' },
  { key: 'devices', label: 'My Devices', icon: 'phone-portrait-outline' },
  { key: 'help', label: 'Help', icon: 'help-circle-outline' },
  { key: 'settings', label: 'Settings', icon: 'settings-outline' },
];

export function MoreScreen({ navigation }: any) {
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);

  if (!user) return null;

  const menuItems = MENU_ITEMS.filter((item) => item.key !== 'shift' || user.role === 'OPERATOR');

  const handlePress = (key: string) => {
    if (key === 'shift') {
      navigation.navigate('Shift', { screen: 'ShiftDashboard' });
    }
    if (key === 'sync') {
      navigation.navigate('SyncCentre');
    }
  };

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <Card style={styles.profileCard}>
        <View style={styles.avatar}>
          <Text style={styles.avatarText}>{user.initials}</Text>
        </View>
        <View style={{ flex: 1 }}>
          <Text style={styles.name}>{user.name}</Text>
          <Text style={styles.role}>
            {ROLE_LABEL[user.role]} · {user.site}
          </Text>
        </View>
      </Card>

      <Card padded={false} style={styles.menuCard}>
        {menuItems.map((item, i) => (
          <View key={item.key}>
            <Pressable style={styles.menuRow} onPress={() => handlePress(item.key)}>
              <Ionicons name={item.icon} size={20} color={colors.textSecondary} />
              <Text style={styles.menuLabel}>{item.label}</Text>
              <Ionicons name="chevron-forward" size={18} color={colors.textMuted} />
            </Pressable>
            {i < menuItems.length - 1 && <View style={styles.divider} />}
          </View>
        ))}
      </Card>

      <Pressable style={styles.logoutButton} onPress={() => logout()}>
        <Text style={styles.logoutText}>Log out</Text>
      </Pressable>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  content: { padding: spacing.lg, gap: spacing.md },
  profileCard: { flexDirection: 'row', alignItems: 'center', gap: spacing.md },
  avatar: {
    width: 52,
    height: 52,
    borderRadius: radii.pill,
    backgroundColor: colors.primaryDark,
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarText: { color: colors.textOnDark, fontWeight: '700', fontSize: 18 },
  name: { ...typography.bodyStrong, fontSize: 17 },
  role: { ...typography.caption },
  menuCard: {},
  menuRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.lg,
  },
  menuLabel: { ...typography.body, flex: 1 },
  divider: { height: 1, backgroundColor: colors.border, marginLeft: spacing.lg + 20 + spacing.md },
  logoutButton: {
    marginTop: spacing.md,
    borderRadius: radii.md,
    borderWidth: 1,
    borderColor: colors.dangerBg,
    backgroundColor: colors.dangerBg,
    paddingVertical: spacing.md,
    alignItems: 'center',
  },
  logoutText: { color: colors.danger, fontWeight: '700' },
});
