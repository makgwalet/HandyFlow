package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Sales by division over a period, with the period of equal length just before it. What counts as a sale in each division:
 * <ul>
 *   <li>DISTRIBUTION: delivered, not-reversed loads (delivered litres and the amount invoiced for them);</li>
 *   <li>RETAIL_FORECOURT: closed, not-reversed shifts (litres sold and sales value from the pump readings), dated as the forecourt reports are;</li>
 *   <li>WHOLESALE_SUPPLY: customer-billed dispatches from a tank (a price per litre is set; internal issues and transfers are not sales);</li>
 *   <li>TRUCK_STOP: stays checked out in the period, at the nightly rate for the nights actually stayed, before VAT.</li>
 * </ul>
 * Money fields are null unless the reader may see margin, as in the group report. Only divisions the company runs and the reader may see appear.
 *
 * @param count loads, shifts, dispatches or stays, by division
 * @param dailyRevenue revenue on each day of the period (index 0 is {@code from}); null when money is not shown
 */
public final class DivisionSalesDtos {
    private DivisionSalesDtos() {}

    public record Row(String division, String label, long count, BigDecimal litres, BigDecimal revenue, List<BigDecimal> dailyRevenue,
                      long previousCount, BigDecimal previousLitres, BigDecimal previousRevenue) {}

    public record Report(LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo, boolean moneyShown, List<Row> divisions, Row total) {}
}
