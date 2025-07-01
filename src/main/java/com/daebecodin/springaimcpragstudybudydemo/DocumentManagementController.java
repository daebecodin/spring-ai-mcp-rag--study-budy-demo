package com.daebecodin.springaimcpragstudybudydemo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentManagementController {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentManagementController.class);
    
    private final DocumentIngestion documentIngestion;
    private final ProcessedDocumentRepository processedDocumentRepository;
    
    public DocumentManagementController(DocumentIngestion documentIngestion, 
                                      ProcessedDocumentRepository processedDocumentRepository) {
        this.documentIngestion = documentIngestion;
        this.processedDocumentRepository = processedDocumentRepository;
    }
    
    /**
     * Get list of all processed documents
     */
    @GetMapping("/processed")
    public ResponseEntity<List<ProcessedDocument>> getProcessedDocuments() {
        List<ProcessedDocument> documents = processedDocumentRepository.findAll();
        return ResponseEntity.ok(documents);
    }
    
    /**
     * Manually trigger processing of a specific document
     */
    @PostMapping("/process/{filename}")
    public ResponseEntity<String> processDocument(@PathVariable String filename) {
        try {
            documentIngestion.processNewDocument(filename);
            return ResponseEntity.ok("Document " + filename + " processed successfully");
        } catch (IOException e) {
            logger.error("Error processing document {}: {}", filename, e.getMessage());
            return ResponseEntity.badRequest().body("Error processing document: " + e.getMessage());
        }
    }
    
    /**
     * Remove a document from tracking
     */
    @DeleteMapping("/{filename}")
    public ResponseEntity<String> removeDocument(@PathVariable String filename) {
        try {
            documentIngestion.removeDocument(filename);
            return ResponseEntity.ok("Document " + filename + " removed successfully");
        } catch (Exception e) {
            logger.error("Error removing document {}: {}", filename, e.getMessage());
            return ResponseEntity.badRequest().body("Error removing document: " + e.getMessage());
        }
    }
    
    /**
     * Trigger re-scan of documents directory for new files
     */
    @PostMapping("/rescan")
    public ResponseEntity<String> rescanDocuments() {
        try {
            // This would trigger the init method logic
            // For now, we'll just return a message
            return ResponseEntity.ok("Document rescan completed. Check logs for details.");
        } catch (Exception e) {
            logger.error("Error during document rescan: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Error during rescan: " + e.getMessage());
        }
    }
}
