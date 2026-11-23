package za.co.handyflow.platform.agriculture.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * offspringCount is the number born alive and stillbornCount the number born dead; either may be left out (zero), but not
 * both. The record becomes BIRTHED if any were born alive and STILLBORN if none were. (There used to be no way to say
 * stillborn, so every birth was stored as BIRTHED.)
 * <p>
 * {@code offspring} has one entry per animal born alive: the sex, and optionally the tag number and a name. It is needed when the
 * dam is an individually tracked animal, and ignored for a group. A blank tag number gets a temporary tag.
 */
public record RecordBirthRequest(@NotNull LocalDate actualBirthDate, Integer offspringCount, Integer stillbornCount, List<Offspring> offspring) {

    /** One animal born alive. */
    public record Offspring(String sex, String tagNumber, String name) {}

    /** Counts only (callers written before the offspring were recorded). */
    public RecordBirthRequest(LocalDate actualBirthDate, Integer offspringCount, Integer stillbornCount) {
        this(actualBirthDate, offspringCount, stillbornCount, null);
    }
}
