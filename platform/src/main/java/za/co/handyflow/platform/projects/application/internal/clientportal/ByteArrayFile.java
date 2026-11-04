package za.co.handyflow.platform.projects.application.internal.clientportal;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/** In-memory MultipartFile, so an accepted client file goes into the register through the normal upload path (checks and scan again). */
final class ByteArrayFile implements MultipartFile {
    private final String name;
    private final String contentType;
    private final byte[] content;

    ByteArrayFile(String name, String contentType, byte[] content) { this.name = name; this.contentType = contentType; this.content = content; }

    @Override public String getName() { return "file"; }
    @Override public String getOriginalFilename() { return name; }
    @Override public String getContentType() { return contentType; }
    @Override public boolean isEmpty() { return content.length == 0; }
    @Override public long getSize() { return content.length; }
    @Override public byte[] getBytes() { return content; }
    @Override public InputStream getInputStream() { return new ByteArrayInputStream(content); }
    @Override public void transferTo(File dest) throws IOException { Files.write(dest.toPath(), content); }
}
