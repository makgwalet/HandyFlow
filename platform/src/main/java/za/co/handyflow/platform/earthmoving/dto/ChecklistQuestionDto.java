package za.co.handyflow.platform.earthmoving.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** One question as the screen sends and reads it. {@code answerType} is PASS_FAIL, NUMERIC (with min and/or max and a unit), TEXT or PHOTO. {@code failOutcome} (DEFECT or UNSAFE) applies when a number is out of range. */
public record ChecklistQuestionDto(UUID id, String section, String text, String answerType, boolean required, boolean builtIn,
                                   BigDecimal min, BigDecimal max, String unit, String failOutcome) {}
