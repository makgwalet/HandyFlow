package za.co.handyflow.platform.fleet.domain.model;

/**
 * Traffic-light reading of a tyre's last tread-depth measurement. Thresholds are in
 * {@link Tyre}: REPLACE_NOW at or below the 1.0 mm legal minimum (South African road-traffic
 * regulations), REPLACE_SOON below 3.0 mm (a conventional company policy line — adjust to your own).
 */
public enum TreadStatus {
    OK, REPLACE_SOON, REPLACE_NOW, NO_READING
}