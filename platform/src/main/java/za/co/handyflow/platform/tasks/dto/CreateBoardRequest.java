package za.co.handyflow.platform.tasks.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBoardRequest(
        @NotBlank @Size(max = 100) String name,
        String description,
        @Size(max = 20) String color) {}
