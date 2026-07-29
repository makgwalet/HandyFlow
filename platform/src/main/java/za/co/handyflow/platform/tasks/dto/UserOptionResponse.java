package za.co.handyflow.platform.tasks.dto;

import java.util.UUID;

/** Lightweight {id, name} pair used to populate the assignee picker. */
public record UserOptionResponse(UUID id, String name) {
}