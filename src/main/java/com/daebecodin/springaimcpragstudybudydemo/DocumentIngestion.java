package com.daebecodin.springaimcpragstudybudydemo;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.List;
@Component
public class DocumentIngestion {

    private final VectorStore vectorStore;

    @Value("classpath:/docs/mapping-request.pdf")
    private Resource stackImplementations;

    DocumentIngestion(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public List<Document> linkedBagImplementations() {
       try {
           return readParagraph();
       } catch (IllegalArgumentException e) {
           Logger logger = LoggerFactory.getLogger(DocumentIngestion.class);
           logger.warn("ParagraphPdfDocumentReader failed (no TOC found), falling  back to PagePdfDocumentReader: {}", e.getMessage());
           return readPage();
       }
    }



    @PostConstruct
    void init() {
        vectorStore.add(linkedBagImplementations());
    }

    private List<Document> readPage() {

        PagePdfDocumentReader pageText = new PagePdfDocumentReader(
                stackImplementations,
                PdfDocumentReaderConfig.builder()
                        .withPageTopMargin(0)
                        .withPageExtractedTextFormatter(ExtractedTextFormatter.builder()
                                .withNumberOfTopTextLinesToDelete(0)
                                .build())
                        .withPagesPerDocument(1)
                        .build()
        );

        return pageText.read();
    }
    private List<Document> readParagraph() {
        // constructing a ParagraphPdfDocumentReader with a Resource and a configuration Builder
        ParagraphPdfDocumentReader paragraphText = new ParagraphPdfDocumentReader(
                stackImplementations,
                PdfDocumentReaderConfig.builder()
                        .withPageTopMargin(0) // sets the formatted pdf top margin to 0
                        .withPageExtractedTextFormatter(ExtractedTextFormatter.builder() // includes text formatting before final output
                                .withNumberOfTopTextLinesToDelete(0) // no removing of top lines
                                .build())
                        .withPagesPerDocument(1) // 1 page per outputted document
                        .build()
        );

        return paragraphText.read(); // returns the read text from the pdf
    }
}
