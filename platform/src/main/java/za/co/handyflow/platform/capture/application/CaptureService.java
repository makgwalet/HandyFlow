package za.co.handyflow.platform.capture.application;

import org.springframework.stereotype.Service;
import za.co.handyflow.platform.capture.dto.CaptureResponse;
import za.co.handyflow.platform.shared.BusinessException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class CaptureService {

    private final DocumentTextReader reader;

    public CaptureService(DocumentTextReader reader) {
        this.reader = reader;
    }

    public CaptureResponse extract(byte[] file) {
        if (file == null || file.length == 0) throw new BusinessException("The file is empty.");
        if (file.length > DocumentTextReader.MAX_BYTES) throw new BusinessException("The file is larger than 10 MB.");
        DocumentTextReader.Kind kind = DocumentTextReader.kind(file);
        String text = reader.read(file);
        CaptureRules.Found f = CaptureRules.parse(text, LocalDate.now());
        List<String> notes = new ArrayList<>(f.notes());
        if (text.isBlank()) {
            notes.add(0, kind == DocumentTextReader.Kind.IMAGE
                    ? "This is a photo or scan. Only PDFs with selectable text can be read for now; fill the fields in by hand."
                    : kind == DocumentTextReader.Kind.PDF
                        ? "This PDF has no selectable text (probably a scan). Fill the fields in by hand."
                        : "Only PDF files can be read. Fill the fields in by hand.");
        }
        return new CaptureResponse(!text.isBlank(), f.supplierName(), f.documentNumber(), f.documentDate(), f.dueDate(),
                f.total(), f.vatAmount(), f.supplierVatNumber(), notes);
    }
}
