package za.co.handyflow.platform.agriculture.dto;

import java.time.LocalDate;

/** Whether an animal, group or crop is held on {@code asOf}, by which treatment, and the first day it is clear. */
public record WithdrawalStatusResponse(boolean held, LocalDate asOf, LocalDate treatedOn, LocalDate clearOn, String product) {}
