package za.co.handyflow.platform.tasks.dto;
import jakarta.validation.constraints.NotBlank;
public record CreateBoardRequest(@NotBlank String name, String description, String color) {}
