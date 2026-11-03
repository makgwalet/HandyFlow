import React, { useMemo, useState } from 'react';
import { View, Text, Pressable, TextInput, StyleSheet, FlatList } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { WizardHeader } from '../../components/WizardHeader';
import { SelectableRow } from '../../components/SelectableRow';
import { colors, radii, spacing, typography } from '../../theme/tokens';
import { useDispenseFlowStore } from '../../store/dispenseFlowStore';
import { MOCK_VEHICLES } from '../../data/mockVehicles';

export function SelectVehicleScreen({ navigation }: any) {
  const { vehicleId, setVehicle } = useDispenseFlowStore();
  const [query, setQuery] = useState('');

  const results = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return MOCK_VEHICLES;
    return MOCK_VEHICLES.filter(
      (v) =>
        v.registration.toLowerCase().includes(q) || v.label.toLowerCase().includes(q)
    );
  }, [query]);

  return (
    <View style={styles.container}>
      <WizardHeader title="Select Vehicle / Asset" step={2} totalSteps={5} />
      <View style={styles.body}>
        <View style={styles.searchBar}>
          <Ionicons name="search" size={18} color={colors.textMuted} />
          <TextInput
            value={query}
            onChangeText={setQuery}
            placeholder="Search registration..."
            placeholderTextColor={colors.textMuted}
            style={styles.searchInput}
          />
        </View>
        <FlatList
          data={results}
          keyExtractor={(v) => v.id}
          renderItem={({ item }) => (
            <SelectableRow
              title={item.registration}
              subtitle={item.label}
              icon="car-sport-outline"
              selected={vehicleId === item.id}
              onPress={() => setVehicle(item.id)}
            />
          )}
          ListEmptyComponent={<Text style={styles.empty}>No vehicles match "{query}"</Text>}
        />
      </View>
      <View style={styles.footer}>
        <Pressable
          disabled={!vehicleId}
          onPress={() => navigation.navigate('EnterQuantity')}
          style={[styles.next, !vehicleId && styles.disabled]}
        >
          <Text style={styles.nextText}>Next</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.bg },
  body: { flex: 1, padding: spacing.lg },
  searchBar: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radii.md,
    paddingHorizontal: spacing.md,
    marginBottom: spacing.md,
  },
  searchInput: { flex: 1, paddingVertical: spacing.md, fontSize: 15, color: colors.textPrimary },
  empty: { ...typography.caption, textAlign: 'center', marginTop: spacing.xl },
  footer: { padding: spacing.lg, backgroundColor: colors.surface, borderTopWidth: 1, borderTopColor: colors.border },
  next: {
    backgroundColor: colors.primaryDark,
    borderRadius: radii.md,
    paddingVertical: spacing.md,
    alignItems: 'center',
  },
  disabled: { opacity: 0.4 },
  nextText: { color: colors.textOnDark, fontWeight: '700', fontSize: 16 },
});
