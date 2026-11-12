package za.co.handyflow.platform.security.dto;

import java.util.UUID;

/** What a guard may see of a colleague when choosing who to ask for a swap: a name, nothing else. */
public record SwapColleagueView(UUID id, String name) {}
