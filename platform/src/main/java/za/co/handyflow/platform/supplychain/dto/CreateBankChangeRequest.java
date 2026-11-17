package za.co.handyflow.platform.supplychain.dto;

public record CreateBankChangeRequest(String bankName, String accountNumber, String branchCode, String reason) {}
