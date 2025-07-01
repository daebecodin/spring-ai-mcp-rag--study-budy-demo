package com.daebecodin.springaimcpragstudybudydemo;

import jakarta.annotation.PostConstruct;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Service
@Order(2) // Run after DatabaseInitializer
public class DocumentIngestion {

    private static final Logger logger = LoggerFactory.getLogger(DocumentIngestion.class);
    
    private final VectorStore vectorStore;
    private final TikaDocumentReader tikaDocumentReader;
    private final TokenTextSplitter textSplitter;
    private final ProcessedDocumentRepository processedDocumentRepository;

    @Value("classpath:/docs/Spring-Framework-Reference-Documentation.pdf")
    private Resource stackImplementations;

    @Value("classpath:docs")
    private Resource documentDirectory;

    @Value("${inputFilenamePattern}")
    private String inputFilePattern;

    DocumentIngestion(VectorStore vectorStore, TikaDocumentReader tikaDocumentReader, 
                     TokenTextSplitter textSplitter, ProcessedDocumentRepository processedDocumentRepository) {
        this.vectorStore = vectorStore;
        this.tikaDocumentReader = tikaDocumentReader;
        this.textSplitter = textSplitter;
        this.processedDocumentRepository = processedDocumentRepository;
    }

    @PostConstruct
    @Transactional
    void init() throws IOException {
        try {
            logger.info("Starting document ingestion process...");
            
            // Get all documents in the docs directory
            List<Path> documentsToProcess = getDocumentsToProcess();
            
            if (documentsToProcess.isEmpty()) {
                logger.info("No new documents to process");
                return;
            }
            
            logger.info("Found {} new documents to process", documentsToProcess.size());
            
            for (Path documentPath : documentsToProcess) {
                processDocument(documentPath);
            }
            
            logger.info("Document ingestion process completed");
            
        } catch (Exception e) {
            logger.error("Error during document ingestion: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    private List<Path> getDocumentsToProcess() throws IOException {
        List<Path> newDocuments = new ArrayList<>();
        
        // Get the docs directory path
        Path docsPath = Paths.get(documentDirectory.getURI());
        
        // Find all files matching the pattern
        try (Stream<Path> files = Files.walk(docsPath)) {
            files.filter(Files::isRegularFile)
                 .filter(path -> matchesPattern(path.getFileName().toString()))
                 .forEach(path -> {
                     String filename = path.getFileName().toString();
                     
                     try {
                         // Check if this document has already been processed
                         if (!processedDocumentRepository.existsByFilename(filename)) {
                             newDocuments.add(path);
                             logger.info("Found new document to process: {}", filename);
                         } else {
                             logger.debug("Document {} already processed, skipping", filename);
                         }
                     } catch (Exception e) {
                         // If there's an error checking the database (e.g., table doesn't exist yet),
                         // assume the document is new and needs processing
                         logger.warn("Error checking if document {} exists in database, will process: {}", filename, e.getMessage());
                         newDocuments.add(path);
                     }
                 });
        }
        
        return newDocuments;
    }
    
    private boolean matchesPattern(String filename) {
        // Simple pattern matching for common document types
        String lowerFilename = filename.toLowerCase();
        return lowerFilename.endsWith(".pdf") || 
               lowerFilename.endsWith(".txt") || 
               lowerFilename.endsWith(".docx") ||
               lowerFilename.endsWith(".json") ||
               lowerFilename.endsWith(".xml");
    }
    
    @Transactional
    private void processDocument(Path documentPath) {
        try {
            String filename = documentPath.getFileName().toString();
            logger.info("Processing document: {}", filename);
            
            Resource resource = new UrlResource(documentPath.toUri());
            
            List<Document> documents;
            if (filename.toLowerCase().endsWith(".pdf")) {
                documents = processPdfDocument(resource);
            } else {
                // Use Tika for other document types
                TikaDocumentReader reader = new TikaDocumentReader(resource);
                documents = reader.read();
            }
            
            logger.info("Read {} documents from {}", documents.size(), filename);
            
            // Split documents into smaller chunks
            List<Document> splitDocuments = textSplitter.apply(documents);
            logger.info("Split into {} chunks", splitDocuments.size());
            
            // Add metadata to track source document
            splitDocuments.forEach(doc -> 
                doc.getMetadata().put("source_filename", filename)
            );
            
            // Add split documents to vector store
            vectorStore.add(splitDocuments);
            
            // Record that this document has been processed
            long fileSize = Files.size(documentPath);
            ProcessedDocument processedDoc = new ProcessedDocument(filename, fileSize, splitDocuments.size());
            processedDocumentRepository.save(processedDoc);
            
            logger.info("Successfully processed {} with {} chunks", filename, splitDocuments.size());
            
        } catch (Exception e) {
            logger.error("Error processing document {}: {}", documentPath.getFileName(), e.getMessage(), e);
        }
    }
    
    private List<Document> processPdfDocument(Resource document) {
        try {
            return readParagraph(document);
        } catch (IllegalArgumentException e) {
            logger.warn("ParagraphPdfDocumentReader failed (no TOC found), falling back to PagePdfDocumentReader: {}", e.getMessage());
            return readPage(document);
        }
    }

    public List<Document> linkedBagImplementations() {
       try {
           return readParagraph(stackImplementations);
       } catch (IllegalArgumentException e) {
           logger.warn("ParagraphPdfDocumentReader failed (no TOC found), falling back to PagePdfDocumentReader: {}", e.getMessage());
           return readPage(stackImplementations);
       }
    }

    /**
     * Document Ingestion using Page reader
     * @param resource the PDF resource to read
     * @return the read document
     */
    private List<Document> readPage(Resource resource) {
        PagePdfDocumentReader pageText = new PagePdfDocumentReader(
                resource,
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

    /**
     * Document Ingestion using Paragraph Reader
     * @param resource the PDF resource to read
     * @return the read document
     */
    private List<Document> readParagraph(Resource resource) {
        ParagraphPdfDocumentReader paragraphText = new ParagraphPdfDocumentReader(
                resource,
                PdfDocumentReaderConfig.builder()
                        .withPageTopMargin(0)
                        .withPageExtractedTextFormatter(ExtractedTextFormatter.builder()
                                .withNumberOfTopTextLinesToDelete(0)
                                .build())
                        .withPagesPerDocument(1)
                        .build()
        );

        return paragraphText.read();
    }

    /**
     * Extract text from uploaded document
     */
    public String extractTextFromDocument(MultipartFile file) throws IOException, TikaException {
        Tika tika = new Tika();
        String text = tika.parseToString(file.getInputStream());
        return text;
    }
    
    /**
     * Method to manually trigger processing of a specific document
     */
    @Transactional
    public void processNewDocument(String filename) throws IOException {
        Path documentPath = Paths.get(documentDirectory.getURI()).resolve(filename);
        if (Files.exists(documentPath) && !processedDocumentRepository.existsByFilename(filename)) {
            processDocument(documentPath);
        } else if (processedDocumentRepository.existsByFilename(filename)) {
            logger.info("Document {} already processed", filename);
        } else {
            logger.warn("Document {} not found", filename);
        }
    }
    
    /**
     * Method to remove a document from tracking and vector store
     */
    @Transactional
    public void removeDocument(String filename) {
        if (processedDocumentRepository.existsByFilename(filename)) {
            // Note: Spring AI VectorStore doesn't have a direct way to delete by metadata
            // You might need to implement custom deletion logic based on your vector store
            processedDocumentRepository.deleteByFilename(filename);
            logger.info("Removed document {} from tracking", filename);
        } else {
            logger.warn("Document {} not found in tracking", filename);
        }
    }
}
