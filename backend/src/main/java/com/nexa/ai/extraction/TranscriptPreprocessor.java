package com.nexa.ai.extraction;

import org.springframework.stereotype.Component;

import java.text.Normalizer;

/**
 * Cleans a transcript before analysis: normalizes Unicode and line endings, removes invisible
 * characters and collapses runs of blank lines. Content is never reworded or dropped.
 */
@Component
public class TranscriptPreprocessor {

    public String clean(String transcript) {
        String text = Normalizer.normalize(transcript, Normalizer.Form.NFC)
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\u200B-\\u200D\\uFEFF\\u00AD]", "")
                .replace(' ', ' ')
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n");
        return text.strip();
    }

    /** Canonical form for comparing evidence quotes with the transcript: case, quotes and spacing ignored. */
    public static String canonical(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
                .toLowerCase()
                .replaceAll("[\\u2018\\u2019\\u201A\\u2032]", "'")
                .replaceAll("[\\u201C\\u201D\\u201E\\u2033]", "\"")
                .replaceAll("[\\u2013\\u2014]", "-")
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
    }
}
