// UNVERIFIED SCAFFOLD — no existing frontend Settings page was found in
// project knowledge to match conventions against. This assumes nothing
// about your API client, auth wrapper, or component library beyond plain
// fetch() + Tailwind. Replace the fetch calls with your real API client
// and re-style to match your existing Settings tabs before shipping.

import { useEffect, useState } from "react";

interface PosSettings {
  cashVarianceToleranceAmount: string;
  cashVarianceTolerancePct: string;
  cashVarianceCriticalAmount: string;
  cashVarianceCriticalPct: string;
}

export default function PosSettingsPanel() {
  const [settings, setSettings] = useState<PosSettings | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [savedAt, setSavedAt] = useState<number | null>(null);

  useEffect(() => {
    // ASSUMPTION: bearer token read from wherever the rest of the app
    // stores it — verify against your real auth wrapper.
    const token = localStorage.getItem("accessToken");
    fetch("/api/v1/pos/settings", {
      headers: { Authorization: `Bearer ${token}` },
    })
      .then((r) => r.json())
      .then((res) => setSettings(res.data))
      .catch(() => setError("Could not load POS settings."));
  }, []);

  function updateField(field: keyof PosSettings, value: string) {
    if (!settings) return;
    setSettings({ ...settings, [field]: value });
  }

  async function save() {
    if (!settings) return;
    setSaving(true);
    setError(null);
    try {
      const token = localStorage.getItem("accessToken");
      const res = await fetch("/api/v1/pos/settings", {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          cashVarianceToleranceAmount: settings.cashVarianceToleranceAmount,
          cashVarianceTolerancePct: settings.cashVarianceTolerancePct,
          cashVarianceCriticalAmount: settings.cashVarianceCriticalAmount,
          cashVarianceCriticalPct: settings.cashVarianceCriticalPct,
        }),
      });
      if (!res.ok) throw new Error("Save failed");
      const body = await res.json();
      setSettings(body.data);
      setSavedAt(Date.now());
    } catch {
      setError("Could not save settings — please try again.");
    } finally {
      setSaving(false);
    }
  }

  if (error && !settings) {
    return <div className="text-sm text-red-600">{error}</div>;
  }
  if (!settings) {
    return <div className="text-sm text-gray-500">Loading settings…</div>;
  }

  return (
    <div className="max-w-lg space-y-6">
      <div>
        <h3 className="text-base font-semibold text-gray-900">
          Cash-up variance alerts
        </h3>
        <p className="mt-1 text-sm text-gray-500">
          A till closing over or under by more than the amounts below
          notifies your admins. Small variances are ignored — they're
          usually just counting rounding, not a real problem.
        </p>
      </div>

      <fieldset className="space-y-4">
        <legend className="text-sm font-medium text-gray-700">
          No alert if variance is within
        </legend>
        <div className="flex gap-4">
          <label className="flex-1 text-sm">
            Flat amount (R)
            <input
              type="number"
              step="0.01"
              min="0"
              className="mt-1 block w-full rounded-md border-gray-300 shadow-sm"
              value={settings.cashVarianceToleranceAmount}
              onChange={(e) =>
                updateField("cashVarianceToleranceAmount", e.target.value)
              }
            />
          </label>
          <label className="flex-1 text-sm">
            Or % of expected cash
            <input
              type="number"
              step="0.001"
              min="0"
              max="1"
              className="mt-1 block w-full rounded-md border-gray-300 shadow-sm"
              value={settings.cashVarianceTolerancePct}
              onChange={(e) =>
                updateField("cashVarianceTolerancePct", e.target.value)
              }
            />
          </label>
        </div>
        <p className="text-xs text-gray-400">
          Whichever is greater applies. E.g. 20.00 and 0.01 (1%) means a
          till under R2,000 uses the flat R20 floor; a bigger till uses 1%
          instead.
        </p>
      </fieldset>

      <fieldset className="space-y-4">
        <legend className="text-sm font-medium text-gray-700">
          Escalate to urgent (CRITICAL) beyond
        </legend>
        <div className="flex gap-4">
          <label className="flex-1 text-sm">
            Flat amount (R)
            <input
              type="number"
              step="0.01"
              min="0"
              className="mt-1 block w-full rounded-md border-gray-300 shadow-sm"
              value={settings.cashVarianceCriticalAmount}
              onChange={(e) =>
                updateField("cashVarianceCriticalAmount", e.target.value)
              }
            />
          </label>
          <label className="flex-1 text-sm">
            Or % of expected cash
            <input
              type="number"
              step="0.001"
              min="0"
              max="1"
              className="mt-1 block w-full rounded-md border-gray-300 shadow-sm"
              value={settings.cashVarianceCriticalPct}
              onChange={(e) =>
                updateField("cashVarianceCriticalPct", e.target.value)
              }
            />
          </label>
        </div>
      </fieldset>

      {error && <p className="text-sm text-red-600">{error}</p>}

      <div className="flex items-center gap-3">
        <button
          onClick={save}
          disabled={saving}
          className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
        >
          {saving ? "Saving…" : "Save settings"}
        </button>
        {savedAt && (
          <span className="text-xs text-gray-400">Saved</span>
        )}
      </div>
    </div>
  );
}
