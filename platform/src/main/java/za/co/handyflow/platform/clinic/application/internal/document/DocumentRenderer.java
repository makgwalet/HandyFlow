package za.co.handyflow.platform.clinic.application.internal.document;

/** Turns a {@link DocumentModel} into an A4 PDF. One implementation per PDF library, so the library can be replaced (CLINIC-DEC-023, CLIN-DOC-001A). */
public interface DocumentRenderer {

    /** Short stable name stored with every issued document, for example {@code itext-7.2.5}. */
    String name();

    byte[] render(DocumentModel model);
}
