package za.co.handyflow.platform.crm.dto;

import java.util.UUID;

/** parentId null makes the company a top-level company. */
public record SetParentRequest(UUID parentId) {}
