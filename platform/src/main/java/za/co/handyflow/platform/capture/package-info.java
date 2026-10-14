/**
 * Document capture: reads the text out of an uploaded receipt or supplier invoice and suggests
 * the fields for a claim or a bill. It stores nothing and creates nothing; the person checks the
 * suggestions and saves through the normal Expenses or AP screen.
 * <p>
 * Only PDFs with a text layer are read. There is no OCR engine, so photos and scans return no text.
 */
@ApplicationModule(allowedDependencies = {"shared"})
package za.co.handyflow.platform.capture;

import org.springframework.modulith.ApplicationModule;
