package za.co.handyflow.platform.agriculture.dto;

/** Whether milk and eggs from an animal or group are held on the day asked about, each with the treatment holding it and the first clear day. */
public record ProduceWithdrawalResponse(WithdrawalStatusResponse milk, WithdrawalStatusResponse eggs) {}
