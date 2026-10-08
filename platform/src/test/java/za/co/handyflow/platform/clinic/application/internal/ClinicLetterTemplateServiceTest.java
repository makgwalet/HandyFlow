package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.clinic.domain.model.*;
import za.co.handyflow.platform.clinic.domain.repository.*;
import za.co.handyflow.platform.clinic.dto.LetterDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClinicLetterTemplateServiceTest {

    @Mock ClinicLetterTemplateRepository repo;
    @Mock ClinicLetterValues letterValues;
    @InjectMocks ClinicLetterTemplateService service;

    final TenantId t = TenantId.of(UUID.randomUUID());

    private ClinicLetterTemplate template(String body) {
        return ClinicLetterTemplate.create(t, "GENERAL_LETTER", "Repeat letter", "Letter for {{patient.firstName}}", body, null, null, null, null);
    }

    @Test
    @DisplayName("rendering fills the merge fields from the patient and the visit")
    void renderFillsFields() {
        var x = template("{{patient.name}} born {{patient.dob}} was seen on {{visit.date}} for {{visit.reason}}.");
        when(repo.findOne(t, x.getId())).thenReturn(Optional.of(x));
        UUID visit = UUID.randomUUID();
        var values = LetterMerge.values(new LetterMerge.Source("Liam", "Botha", LocalDate.of(2019, 3, 12), null, null, null,
                LocalDate.of(2026, 10, 7), "Cough", null, null, null, null, null, null, LocalDate.of(2026, 10, 8), null, null));
        when(letterValues.load(t, null, visit, null, null, null)).thenReturn(new ClinicLetterValues.Loaded(null, null, null, values));

        RenderedTemplate r = service.render(t, x.getId(), null, visit, null, null);

        assertThat(r.title()).isEqualTo("Letter for Liam");
        assertThat(r.body()).isEqualTo("Liam Botha born 12 March 2019 was seen on 7 October 2026 for Cough.");
    }

    @Test
    @DisplayName("an archived template cannot be applied or changed")
    void archivedIsGone() {
        var x = template("text");
        x.archive();
        when(repo.findOne(t, x.getId())).thenReturn(Optional.of(x));
        assertThatThrownBy(() -> service.render(t, x.getId(), null, UUID.randomUUID(), null, null)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.update(t, x.getId(), new TemplateRequest("GENERAL_LETTER", "n", "T", "b", null, null, null))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("two live templates of one kind cannot share a name")
    void duplicateNameRefused() {
        when(repo.nameTaken(t, "GENERAL_LETTER", "Repeat letter", null)).thenReturn(true);
        assertThatThrownBy(() -> service.create(t, new TemplateRequest("GENERAL_LETTER", "Repeat letter", "T", "b", null, null, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("already exists");
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("archiving keeps the row and marks it")
    void archiveMarks() {
        var x = template("text");
        when(repo.findOne(t, x.getId())).thenReturn(Optional.of(x));
        service.archive(t, x.getId());
        assertThat(x.isArchived()).isTrue();
        verify(repo).save(x);
    }
}
