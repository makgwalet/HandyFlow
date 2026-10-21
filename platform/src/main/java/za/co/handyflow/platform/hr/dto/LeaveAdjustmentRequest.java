package za.co.handyflow.platform.hr.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Days to add (positive) or take off (negative) a balance, with the date it belongs to and why. */
public record LeaveAdjustmentRequest(String leaveType, LocalDate entryDate, BigDecimal days, String reason) {}
