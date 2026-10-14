package za.co.handyflow.platform.accounting.dto;

import java.util.List;
import java.util.UUID;

public record ApplyBankRulesRequest(List<UUID> transactionIds) {}
