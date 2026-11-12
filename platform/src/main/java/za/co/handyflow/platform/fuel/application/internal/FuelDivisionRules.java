package za.co.handyflow.platform.fuel.application.internal;

import za.co.handyflow.platform.fuel.domain.model.FuelDivision;

import java.util.Set;

import static za.co.handyflow.platform.fuel.domain.model.FuelDivision.*;

/**
 * Which division(s) a Fuel API route belongs to. A route is allowed when the tenant has at least one of the listed
 * divisions active. Routes not listed here are shared foundation (tanks, depots, stock, ledger, compliance, dashboard,
 * settings, configuration, the customer portal) and are open to every Fuel tenant.
 *
 * Customers, orders, prices, invoicing and deliveries are shared by Wholesale and Distribution: a delivery company
 * has customers and orders as much as a wholesaler does. Procurement is shared by Wholesale and Retail.
 * Truck Stop reservations (/truck-stop) need the Truck Stop division.
 */
public final class FuelDivisionRules {
    private FuelDivisionRules() {}

    public static final String PREFIX = "/api/v1/fuel";
    private static final Set<FuelDivision> NONE = Set.of();

    /** The divisions that grant access to this path, or an empty set when the path is shared foundation. */
    public static Set<FuelDivision> requirementFor(String path) {
        if (path == null || !path.startsWith(PREFIX)) return NONE;
        String rest = path.substring(PREFIX.length());
        if (rest.startsWith("/")) rest = rest.substring(1);
        int slash = rest.indexOf('/');
        String first = slash < 0 ? rest : rest.substring(0, slash);
        return switch (first) {
            case "forecourt" -> Set.of(RETAIL_FORECOURT);
            case "tankers", "drivers", "planning", "driver", "routes", "cost-actuals" -> Set.of(DISTRIBUTION);
            case "customer-accounts", "customer-sites", "orders", "invoicing", "prices", "deliveries", "variances", "evidence", "enquiries" -> Set.of(WHOLESALE_SUPPLY, DISTRIBUTION);
            case "purchase-orders", "supplier-invoices" -> Set.of(WHOLESALE_SUPPLY, RETAIL_FORECOURT);
            case "truck-stop" -> Set.of(TRUCK_STOP);
            default -> NONE;
        };
    }

    public static String label(FuelDivision d) {
        return switch (d) {
            case WHOLESALE_SUPPLY -> "Wholesale & Supply";
            case DISTRIBUTION -> "Distribution";
            case RETAIL_FORECOURT -> "Retail & Forecourt";
            case TRUCK_STOP -> "Truck Stop & Driver Services";
        };
    }
}
