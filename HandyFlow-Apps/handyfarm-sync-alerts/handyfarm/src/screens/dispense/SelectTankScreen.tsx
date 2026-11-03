import React from 'react';
import { View, Text, Pressable, StyleSheet, FlatList } from 'react-native';
import { WizardHeader } from '../../components/WizardHeader';
import { SelectableRow } from '../../components/SelectableRow';
import { colors, radii, spacing, typography } from '../../theme/tokens';
import { useDispenseFlowStore } from '../../store/dispenseFlowStore';
import { MOCK_TANKS } from '../../data/mockData';

export function SelectTankScreen({ navigation }: any) {
  const { tankId, setTank } = useDispenseFlowStore();

  return (
    <View style={styles.container}>
      <WizardHeader title="Dispense Fuel" step={1} totalSteps={5} />
      <View style={styles.body}>
        <Text style={styles.label}>Select tank</Text>
        <FlatList
          data={MOCK_TANKS}
          keyExtractor={(t) => t.id}
          renderItem={({ item }) => (
            <SelectableRow
              title={`${item.name}`}
              subtitle={`${item.currentLitres.toLocaleString()} L available`}
              selected={tankId === item.id}
              onPress={() => setTank(item.id)}
            />
          )}
        />
      </View>
      <View style={styles.footer}>
        <Pressable
          disabled={!tankId}
          onPress={() => navigation.navigate('SelectVehicle')}
          style={[styles.next, !tankId && styles.disabled]}
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
  label: { ...typography.caption, marginBottom: spacing.md },
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
