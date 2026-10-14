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
    private final OcrEngine ocr;

    public CaptureService(DocumentTextReader reader, OcrEngine ocr) {
        this.reader = reader;
        this.ocr = ocr;
    }

    public CaptureResponse extract(byte[] file) {
        if (file == null || file.length == 0) throw new BusinessException("The file is empty.");
        if (file.length > DocumentTextReader.MAX_BYTES) throw new BusinessException("The file is larger than 10 MB.");
        DocumentTextReader.Kind kind = DocumentTextReader.kind(file);
        String text = reader.read(file);
        boolean ocrUsed = false;
        if (text.isBlank() && kind != DocumentTextReader.Kind.OTHER && ocr.enabled()) {
            text = ocr.read(file, kind == DocumentTextReader.Kind.PDF);
            ocrUsed = !text.isBlank();
        }
        CaptureRules.Found f = CaptureRules.parse(text, LocalDate.now());
        List<String> notes = new ArrayList<>(f.notes());
        if (ocrUsed) notes.add(0, "Read from a photo or scan, which is less reliable than a PDF with text. Check the amounts and numbers digit by digit.");
        if (text.isBlank()) {
            notes.add(0, kind == DocumentTextReader.Kind.IMAGE
                    ? (ocr.enabled() ? "No text could be read from this photo. Try a clearer, straight-on picture, or fill the fields in by hand."
                        : "This is a photo or scan and reading images is not switched on. Fill the fields in by hand.")
                    : kind == DocumentTextReader.Kind.PDF
                        ? (ocr.enabled() ? "No text could be read from this PDF. Fill the fields in by hand."
                            : "This PDF has no selectable text (probably a scan) and reading scans is not switched on. Fill the fields in by hand.")
                        : "Only PDF, JPEG and PNG files can be read. Fill the fields in by hand.");
        }
        return new CaptureResponse(!text.isBlank(), f.supplierName(), f.documentNumber(), f.documentDate(), f.dueDate(),
                f.total(), f.vatAmount(), f.supplierVatNumber(), notes);
    }
}
