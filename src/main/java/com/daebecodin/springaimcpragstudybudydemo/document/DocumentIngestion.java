package com.daebecodin.springaimcpragstudybudydemo.document;

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

    /**
     * Initialization method to process documents before startup.
     * We call getDocumentsToProcess to discover new documents; if none, then we move one
     * If there are new documents, the document name is extracted, converted to a url and then read by a document reader.
     * @throws IOException Input/Output Problems
     */
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
            
            for (Path documentPath : documentsToProcess) { // iterate though documents
                processDocument(documentPath); // process document
            }
            
            logger.info("Document ingestion process completed");
            
        } catch (Exception e) {
            logger.error("Error during document ingestion: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * This method checks the directory for new files by cross-referencing the
     * current file's names with names in the database.
     * If it's a new file then we add it to a new List of documents
     * @return New list of documents to be processed
     * @throws IOException Input/Output Problems
     */
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

    /**
     * Ensures a processed document is of a compatible file type
     * @param filename This is the name of the file to be processed
     * @return compares the file name and the temp file; return the result
     */
    private boolean matchesPattern(String filename) {
        // Simple pattern matching for common document types
        String lowerFilename = filename.toLowerCase();
        return lowerFilename.endsWith(".pdf") || 
               lowerFilename.endsWith(".txt") || 
               lowerFilename.endsWith(".docx") ||
               lowerFilename.endsWith(".json") ||
               lowerFilename.endsWith(".xml");
    }

    /**
     * This method accepts a Path representation and extracts its name to a string
     * That String is converted into a url for the document
     * We run a check for the document file type; Depending on the type, we will use a different
     * DocumentReader. The document is then split into manageable chunks and appended with its metadata
     * Once the document is in chunks, we add them to the vector store to be embedded
     *
     * @param documentPath This is the file path of the document to be processed
     */
    protected void processDocument(Path documentPath) {
        String filename = documentPath.getFileName().toString();
        logger.info("Processing document: {}", filename);
        
        try {
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
            
            // Add metadata to track a source document
            splitDocuments.forEach(doc -> 
                doc.getMetadata().put("source_filename", filename)
            );
            
            // Add split documents to the vector store
            try {
                vectorStore.add(splitDocuments);
                logger.info("Successfully added {} chunks to vector store", splitDocuments.size());
            } catch (Exception vectorStoreException) {
                logger.error("Error adding documents to vector store for {}: {}", filename, vectorStoreException.getMessage(), vectorStoreException);
                // Don't return here - still save it to a database to track the attempt
            }
            
            // logs that this document has been processed (separate transaction)
            saveProcessedDocument(filename, documentPath, splitDocuments.size());
            
            logger.info("Successfully processed {} with {} chunks", filename, splitDocuments.size());
            
        } catch (Exception e) {
            logger.error("Error processing document {}: {}", filename, e.getMessage(), e);
            // Still try to save it to a database to avoid reprocessing
            try {
                // logs that this document has been processed (separate transaction)
                saveProcessedDocument(filename, documentPath, 0);
                logger.info("Saved failed processing attempt for {} to avoid reprocessing", filename);
            } catch (Exception saveException) {
                logger.error("Failed to save processing record for {}: {}", filename, saveException.getMessage());
            }
        }
    }

    /**
     * Logs a successful document injection
     * @param filename Ingested file name
     * @param documentPath Ingested file path
     * @param chunkCount Chunks created
     */
    @Transactional
    protected void saveProcessedDocument(String filename, Path documentPath, int chunkCount) {
        try {
            long fileSize = Files.size(documentPath); // size of the document
            ProcessedDocument processedDoc = new ProcessedDocument(filename, fileSize, chunkCount);
            processedDocumentRepository.save(processedDoc);
            logger.info("Saved processing record for {} with {} chunks", filename, chunkCount);
        } catch (IOException e) {
            logger.error("Error getting file size for {}: {}", filename, e.getMessage());

            //save with size 0 if we cant get actual zise
            var processedDocument = new ProcessedDocument(filename, 0L, chunkCount);
            processedDocumentRepository.save(processedDocument);
            logger.info("Saved processing record for {} with unknown file size", filename);
        }
        catch (Exception e) {
            logger.error("Error saving processed document record for {}: {}", filename, e.getMessage(), e);
            throw e; // Re-throw to ensure transaction rollback if needed
        }
    }

    /**
     * Helper to process a PDF Document
     * @param document The document to be processed
     * @return The processed document
     */
    private List<Document> processPdfDocument(Resource document) {
        try {
            return readParagraph(document);
        } catch (IllegalArgumentException e) {
            logger.warn("ParagraphPdfDocumentReader failed (no TOC found), falling back to PagePdfDocumentReader: {}", e.getMessage());
            return readPage(document);
        }
    }

    /**
     * Process a document in my project files
     * @return The processed document
     */
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
     * This method extracts text from any given document using Tika
     * @param file The document be extracted
     * @return text extracted from document
     * @throws IOException Input/Output Problems
     * @throws TikaException TikaSpecific Problems
     */
    public String extractTextFromDocument(MultipartFile file) throws IOException, TikaException {
        Tika tika = new Tika(); // Tika instance
        return  tika.parseToString(file.getInputStream()); // tika parses the inputted file's input stream
    }

    /**
     *
     * @param filename the new document to be processed
     * @throws IOException Input/Output Problems
     */
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
     * This method removes a document from the database
     * @param filename The documents file path
     */
    @Transactional
    public void removeDocument(String filename) {
        if (processedDocumentRepository.existsByFilename(filename)) {
            // Note: Spring AI VectorStore doesn't have a direct way to delete by metadata
            // We may need to implement custom deletion logic based on your vector store
            processedDocumentRepository.deleteByFilename(filename);
            logger.info("Removed document {} from tracking", filename);
        } else {
            logger.warn("Document {} not found in tracking", filename);
        }
    }
}
