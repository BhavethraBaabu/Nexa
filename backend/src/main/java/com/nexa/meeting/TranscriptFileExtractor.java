package com.nexa.meeting;

import com.nexa.common.exception.InvalidRequestException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Extracts plain text from uploaded transcripts (PRD section 9: TXT, PDF, DOCX). The format is
 * checked from the file's content, not just its name, so a renamed file can't reach the wrong parser.
 */
@Component
public class TranscriptFileExtractor {

    public String extract(String fileName, byte[] bytes) {
        if (bytes.length == 0) {
            throw new InvalidRequestException("The file is empty");
        }
        String extension = extension(fileName);
        try {
            String text = switch (extension) {
                case "pdf" -> {
                    requireMagic(bytes, new byte[]{'%', 'P', 'D', 'F'}, "PDF");
                    yield pdf(bytes);
                }
                case "docx" -> {
                    requireMagic(bytes, new byte[]{'P', 'K', 3, 4}, "Word (.docx)");
                    yield docx(bytes);
                }
                case "txt", "vtt", "srt", "md" -> utf8(bytes);
                default -> throw new InvalidRequestException("Unsupported file type. Upload a .txt, .pdf or .docx file.");
            };
            if (text.isBlank()) {
                throw new InvalidRequestException("No text was found in this file. Scanned PDFs aren't supported yet.");
            }
            return text.strip();
        } catch (IOException e) {
            throw new InvalidRequestException("This file couldn't be read. It may be damaged or password-protected.");
        }
    }

    private static String pdf(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static String docx(byte[] bytes) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        } catch (org.apache.poi.UnsupportedFileFormatException | IllegalStateException e) {
            throw new IOException(e);
        }
    }

    private static String utf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
                    .replace("﻿", "");
        } catch (CharacterCodingException e) {
            throw new InvalidRequestException("Text files must be UTF-8 encoded");
        }
    }

    private static void requireMagic(byte[] bytes, byte[] magic, String kind) {
        if (bytes.length < magic.length) {
            throw new InvalidRequestException("This doesn't look like a valid " + kind + " file");
        }
        for (int i = 0; i < magic.length; i++) {
            if (bytes[i] != magic[i]) {
                throw new InvalidRequestException("This doesn't look like a valid " + kind + " file");
            }
        }
    }

    private static String extension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
