package com.seventhray.contracts.parser;

import com.seventhray.contracts.exception.DocumentParseException;
import com.seventhray.contracts.exception.InvalidRequestException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;

@Component
public class DocumentParser {

    public enum Type { PDF, DOCX, DOC }

    public static Type detect(String filename, byte[] data) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (startsWith(data, 0x25, 0x50, 0x44, 0x46) && name.endsWith(".pdf")) return Type.PDF;
        if (startsWith(data, 0x50, 0x4B) && name.endsWith(".docx")) return Type.DOCX;
        if (startsWith(data, 0xD0, 0xCF, 0x11, 0xE0) && name.endsWith(".doc")) return Type.DOC;
        throw new InvalidRequestException(
                "Unsupported or mismatched file. Upload a real .pdf, .docx or .doc file.");
    }

    public String extractText(String filename, byte[] data) {
        Type type = detect(filename, data);
        try {
            String text = switch (type) {
                case PDF -> parsePdf(data);
                case DOCX -> parseDocx(data);
                case DOC -> parseDoc(data);
            };
            return normalise(text);
        } catch (IOException | RuntimeException e) {
            throw new DocumentParseException("Could not read the document: " + e.getMessage(), e);
        }
    }

    private String parsePdf(byte[] data) throws IOException {
        try (PDDocument doc = Loader.loadPDF(data)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        }
    }

    private String parseDocx(byte[] data) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(data))) {
            for (IBodyElement el : doc.getBodyElements()) {
                if (el instanceof XWPFParagraph p) {
                    sb.append(p.getText()).append("\n\n");
                } else if (el instanceof XWPFTable t) {
                    for (XWPFTableRow row : t.getRows()) {
                        StringBuilder line = new StringBuilder();
                        for (XWPFTableCell cell : row.getTableCells()) {
                            if (line.length() > 0) line.append(" | ");
                            line.append(cell.getText().replace('\n', ' '));
                        }
                        sb.append(line).append("\n");
                    }
                    sb.append("\n");
                }
            }
        }
        return sb.toString();
    }

    private String parseDoc(byte[] data) throws IOException {
        try (HWPFDocument doc = new HWPFDocument(new ByteArrayInputStream(data));
             WordExtractor ex = new WordExtractor(doc)) {
            return ex.getText();
        }
    }

    static String normalise(String text) {
        if (text == null) return "";
        return text.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f\\u00A0 ]+", " ")
                .replaceAll(" ?\n ?", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    private static boolean startsWith(byte[] data, int... sig) {
        if (data == null || data.length < sig.length) return false;
        for (int i = 0; i < sig.length; i++) {
            if ((data[i] & 0xFF) != sig[i]) return false;
        }
        return true;
    }
}
