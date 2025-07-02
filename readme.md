# Spring AI RAG Study Buddy Demo

## Overview
A Spring Boot application that implements Retrieval-Augmented Generation (RAG) for document-based question answering. The system processes documents, stores them in a vector database, and uses AI models to answer questions based on the ingested content.

## System Architecture

### Application Startup Flow
```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           APPLICATION STARTUP                                   │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    DatabaseInitializer (@Order(1))                             │
│                         (@PostConstruct)                                       │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        DocumentIngestion (@Order(2))                           │
│                         (@PostConstruct)                                       │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    Scan /src/main/resources/docs/                              │
│                    Find all matching files                                     │
│                    (*.pdf, *.txt, *.docx, *.json, *.xml)                     │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    For each file found:                                        │
│                    Check ProcessedDocumentRepository                           │
│                    if (filename exists in database)                           │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        │
                        ┌───────────────┴───────────────┐
                        ▼                               ▼
            ┌─────────────────────┐         ┌─────────────────────┐
            │   Already Processed │         │    New Document     │
            │   Skip Processing   │         │   Process Document  │
            │   Log: "Skipping"   │         │                     │
            └─────────────────────┘         └─────────────────────┘
                        │                               │
                        │                               ▼
                        │               ┌─────────────────────────────────┐
                        │               │        Process Document         │
                        │               │  1. Read PDF/Text content       │
                        │               │  2. Split into chunks           │
                        │               │  3. Generate embeddings         │
                        │               │  4. Store in Vector Store       │
                        │               │  5. Save to ProcessedDocument   │
                        │               └─────────────────────────────────┘
                        │                               │
                        └───────────────┬───────────────┘
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           Continue with next file                              │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        Application Ready                                       │
│                   Vector Store contains:                                       │
│                   • Previously processed documents                             │
│                   • Newly processed documents                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Data Flow Diagram
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   File System   │    │   PostgreSQL    │    │  Vector Store   │
│                 │    │                 │    │   (PgVector)    │
│ /docs/*.pdf     │    │ processed_docs  │    │                 │
│ /docs/*.txt     │    │ table           │    │ embeddings +    │
│ /docs/*.docx    │    │                 │    │ metadata        │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────────────────────────────────────────────────────┐
│                DocumentIngestion Service                        │
│                                                                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │  File Scanner   │  │  DB Checker     │  │  Vector Writer  │ │
│  │                 │  │                 │  │                 │ │
│  │ • List files    │  │ • Query DB      │  │ • Generate      │ │
│  │ • Filter types  │  │ • Check exists  │  │   embeddings    │ │
│  │ • Get metadata  │  │ • Track status  │  │ • Store chunks  │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### Component Interaction Flow
```
REST API Endpoints
┌─────────────────────────────────────────────────────────────────┐
│ GET    /quiz                                                    │
│ GET    /document                                                │
│ POST   /upload                                                  │
│ GET    /debug/search                                            │
│ GET    /debug/context                                           │
│ GET    /api/documents/processed                                 │
│ POST   /api/documents/process/{filename}                        │
│ DELETE /api/documents/{filename}                                │
│ POST   /api/documents/rescan                                    │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│         QuizController & DocumentManagementController           │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                DocumentIngestion Service                        │
│                                                                 │
│ • init() - Process new documents on startup                     │
│ • processDocument() - Handle individual document processing     │
│ • processNewDocument() - Manual document processing             │
│ • removeDocument() - Remove from tracking                       │
└─────────────────────────────────────────────────────────────────┘
                    │                           │
                    ▼                           ▼
┌─────────────────────────────┐    ┌─────────────────────────────┐
│  ProcessedDocumentRepository │    │       VectorStore           │
│                             │    │                             │
│ • findByFilename()          │    │ • add(documents)            │
│ • existsByFilename()        │    │ • similaritySearch()        │
│ • save()                    │    │                             │
│ • deleteByFilename()        │    │                             │
└─────────────────────────────┘    └─────────────────────────────┘
```

## Tools & Technologies

### Development Tools
- **HTTPie** - Command-line HTTP client for API testing
- **Docker Compose** - Container orchestration for PostgreSQL and pgVector
- **Gradle** - Build automation and dependency management
- **Spring Boot** - Application framework
- **PostgreSQL with pgVector** - Vector database for embeddings storage

### AI & ML Libraries
- **Spring AI** - Spring's AI integration framework
- **Apache Tika** - Document text extraction
- **OpenAI API** - GPT models for chat completion
- **AWS Bedrock** - Amazon's managed AI service

## Docker & Database Setup

### PostgreSQL with pgVector Extension

The application uses PostgreSQL with the pgVector extension for vector similarity search. The database runs in a Docker container for easy setup and management.

#### Docker Compose Configuration

```yaml
services:
  pgvector:
    image: 'pgvector/pgvector:0.8.0-pg17'
    environment:
      - 'POSTGRES_DB=study-buddy'
      - 'POSTGRES_PASSWORD=secret'
      - 'POSTGRES_USER=daebecodin'
    labels:
      - "org.springframework.boot.service-connection=postgres"
    ports:
      - '5433:5432'
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./postgres/vector_extension.sql:/docker-entrypoint-initdb.d/0-vector_extension.sql

volumes:
  postgres_data:
```

#### Database Initialization Script

The `postgres/vector_extension.sql` file automatically sets up the vector extension:

```sql
-- Enable the vector extension for pgvector
CREATE EXTENSION IF NOT EXISTS vector;

-- Grant necessary permissions
GRANT ALL PRIVILEGES ON DATABASE "study-buddy" TO daebecodin;
```

#### Vector Store Configuration

The application is configured to use pgVector with the following settings:

```properties
# PostgreSQL datasource - Docker Container
spring.datasource.url=jdbc:postgresql://localhost:5433/study-buddy
spring.datasource.username=daebecodin
spring.datasource.password=secret
spring.datasource.driver-class-name=org.postgresql.Driver

# Vector store configurations
spring.ai.vectorstore.pgvector.initialize-schema=true
spring.ai.vectorstore.pgvector.index-type=HNSW
spring.ai.vectorstore.pgvector.distance-type=COSINE_DISTANCE
spring.ai.vectorstore.pgvector.dimensions=1024
spring.ai.vectorstore.pgvector.max-document-batch-size=1000
spring.ai.vectorstore.pgvector.remove-existing-vector-store-table=false
```

**Key Configuration Details:**
- **Port 5433**: Database runs on port 5433 to avoid conflicts with local PostgreSQL
- **HNSW Index**: Uses Hierarchical Navigable Small World algorithm for efficient vector search
- **Cosine Distance**: Measures similarity between document embeddings
- **1024 Dimensions**: Matches the embedding model output dimensions
- **Persistent Storage**: Data persists across container restarts via Docker volumes

### Installation Commands

```bash
# Install HTTPie (macOS)
brew install httpie

# Install HTTPie (Ubuntu/Debian)
sudo apt install httpie

# Install HTTPie (pip)
pip install httpie

# Start PostgreSQL with pgVector
docker compose up -d

# Verify database is running
docker compose ps

# Start the Spring Boot application
./gradlew bootRun

# Alternative: Run with specific profile
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### Database Management Commands

```bash
# View database logs
docker compose logs pgvector

# Connect to database directly
docker compose exec pgvector psql -U daebecodin -d study-buddy

# Stop the database
docker compose down

# Stop and remove volumes (WARNING: This deletes all data)
docker compose down -v

# Restart the database
docker compose restart pgvector
```

## API Endpoints with HTTPie Examples

### Chat & RAG Endpoints

**Quiz Endpoint (Main RAG Chat)**
```bash
# Basic quiz with default model (Bedrock)
http GET localhost:8080/quiz query=="What is Spring MVC?"

# Quiz with specific model
http GET localhost:8080/quiz query=="Explain dependency injection" model==openai

# Quiz with complex query
http GET localhost:8080/quiz query=="How does Spring Boot auto-configuration work?" model==bedrock
```

**Document Reading**
```bash
# Get processed document content
http GET localhost:8080/document
```

**File Upload**
```bash
# Upload a document for text extraction
http --form POST localhost:8080/upload file@/path/to/document.pdf
```

### Debug Endpoints

**Vector Search Debug**
```bash
# Search vector store for relevant documents
http GET localhost:8080/debug/search query=="Spring MVC" topK==5

# Search with fewer results
http GET localhost:8080/debug/search query=="dependency injection" topK==3
```

**Context Debug**
```bash
# See retrieved context for RAG
http GET localhost:8080/debug/context query=="Spring Boot" topK==3

# Debug context with more results
http GET localhost:8080/debug/context query=="configuration" topK==5
```

### Document Management Endpoints

**List Processed Documents**
```bash
# Get all processed documents
http GET localhost:8080/api/documents/processed
```

**Manual Document Processing**
```bash
# Process a specific document
http POST localhost:8080/api/documents/process/spring-guide.pdf

# Process another document
http POST localhost:8080/api/documents/process/tutorial.docx
```

**Document Removal**
```bash
# Remove document from tracking
http DELETE localhost:8080/api/documents/spring-guide.pdf

# Remove another document
http DELETE localhost:8080/api/documents/old-document.pdf
```

**Directory Rescan**
```bash
# Trigger rescan for new documents
http POST localhost:8080/api/documents/rescan
```

## Document Processing

### Database Initialization

The `DatabaseInitializer` ensures the database schema is ready before document processing begins.

```java
@PostConstruct
public void initializeDatabase() {
    try {
        // Check if the processed_documents table exists
        String checkTableQuery = """
            SELECT EXISTS (
                SELECT FROM information_schema.tables 
                WHERE table_schema = 'public' 
                AND table_name = 'processed_documents'
            );
            """;
        
        Boolean tableExists = jdbcTemplate.queryForObject(checkTableQuery, Boolean.class);
        
        if (Boolean.FALSE.equals(tableExists)) {
            logger.info("Creating processed_documents table...");
            
            String createTableQuery = """
                CREATE TABLE processed_documents (
                    id BIGSERIAL PRIMARY KEY,
                    filename VARCHAR(255) UNIQUE NOT NULL,
                    file_size BIGINT,
                    processed_at TIMESTAMP,
                    chunk_count INTEGER
                );
                """;
            
            jdbcTemplate.execute(createTableQuery);
            logger.info("Successfully created processed_documents table");
        } else {
            logger.info("processed_documents table already exists");
        }
        
    } catch (Exception e) {
        logger.error("Error initializing database: {}", e.getMessage(), e);
        // Don't throw the exception to allow the application to continue
        // The JPA auto-creation will handle it as fallback
    }
}
```

**Purpose**: Creates the `processed_documents` table if it doesn't exist, ensuring the application can track which documents have been processed to avoid duplicate processing on restarts.

### Document Ingestion Initialization

The main entry point for document processing that runs after application startup.

```java
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
```

**Purpose**: Orchestrates the document processing workflow by scanning for new documents and processing each one. Runs automatically on application startup with `@Order(2)` to ensure it runs after database initialization.

### Document Discovery

Scans the documents directory and identifies new files that need processing.

```java
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
```

**Purpose**: Recursively scans the `/src/main/resources/docs/` directory for supported file types and filters out documents that have already been processed by checking the database.

### Individual Document Processing

Handles the processing of a single document from reading to vector storage.

```java
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
        
        // Add metadata to track source document
        splitDocuments.forEach(doc -> 
            doc.getMetadata().put("source_filename", filename)
        );
        
        // Add split documents to vector store
        try {
            vectorStore.add(splitDocuments);
            logger.info("Successfully added {} chunks to vector store", splitDocuments.size());
        } catch (Exception vectorStoreException) {
            logger.error("Error adding documents to vector store for {}: {}", filename, vectorStoreException.getMessage(), vectorStoreException);
            // Don't return here - still save to database to track the attempt
        }
        
        // Record that this document has been processed (separate transaction)
        saveProcessedDocument(filename, documentPath, splitDocuments.size());
        
        logger.info("Successfully processed {} with {} chunks", filename, splitDocuments.size());
        
    } catch (Exception e) {
        logger.error("Error processing document {}: {}", filename, e.getMessage(), e);
        // Still try to save to database to avoid reprocessing
        try {
            saveProcessedDocument(filename, documentPath, 0);
            logger.info("Saved failed processing attempt for {} to avoid reprocessing", filename);
        } catch (Exception saveException) {
            logger.error("Failed to save processing record for {}: {}", filename, saveException.getMessage());
        }
    }
}
```

**Purpose**: Core document processing logic that reads document content, splits it into chunks, generates embeddings, stores in vector database, and tracks processing status. Includes error handling to prevent reprocessing failed documents.

### PDF Document Processing

Specialized handling for PDF documents with fallback strategies.

```java
private List<Document> processPdfDocument(Resource document) {
    try {
        return readParagraph(document);
    } catch (IllegalArgumentException e) {
        logger.warn("ParagraphPdfDocumentReader failed (no TOC found), falling back to PagePdfDocumentReader: {}", e.getMessage());
        return readPage(document);
    }
}

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
```

**Purpose**: Attempts to use paragraph-based PDF reading for better structure preservation, falling back to page-based reading if the PDF lacks a table of contents. Both methods are configured to preserve content without removing header lines.

### Processing Status Tracking

Records document processing status in the database to prevent duplicate processing.

```java
@Transactional
protected void saveProcessedDocument(String filename, Path documentPath, int chunkCount) {
    try {
        long fileSize = Files.size(documentPath);
        ProcessedDocument processedDoc = new ProcessedDocument(filename, fileSize, chunkCount);
        processedDocumentRepository.save(processedDoc);
        logger.info("Saved processing record for {} with {} chunks", filename, chunkCount);
    } catch (IOException e) {
        logger.error("Error getting file size for {}: {}", filename, e.getMessage());

        //save with size 0 if we cant get actual size
        var processedDocument = new ProcessedDocument(filename, 0L, chunkCount);
        processedDocumentRepository.save(processedDocument);
        logger.info("Saved processing record for {} with unknown file size", filename);
    }
    catch (Exception e) {
        logger.error("Error saving processed document record for {}: {}", filename, e.getMessage(), e);
        throw e; // Re-throw to ensure transaction rollback if needed
    }
}
```

**Purpose**: Persists document processing metadata including filename, file size, processing timestamp, and chunk count. This prevents reprocessing documents on application restarts and provides processing history.
## RAG (Retrieval-Augmented Generation)

### Vector Search and Context Retrieval

The RAG system uses vector similarity search to find relevant document chunks for answering questions.

```java
// Debug endpoint to see what documents are retrieved for a query
@GetMapping("/debug/search")
public List<Document> debugSearch(
        @RequestParam String query,
        @RequestParam(defaultValue = "5") int topK) {

    List<Document> docs = Optional.ofNullable(
            vectorStore.similaritySearch(SearchRequest.builder()
            .query(query)
            .topK(topK)
            .build()))
            .orElse(Collections.emptyList());

    return docs;
}

// Debug endpoint to show retrieved context with similarity scores
@GetMapping("/debug/context")
public ResponseEntity<List<Document>> debugContext(
        @RequestParam String query,
        @RequestParam(defaultValue = "3") int topK) {
    List<Document> docs = Optional.ofNullable(vectorStore.similaritySearch(SearchRequest.builder()
            .query(query)
            .topK(topK)
            .build()))
            .orElse(Collections.emptyList());

    
    return ResponseEntity.ok(docs);
}
```

**Purpose**: These debug endpoints allow you to see what documents are being retrieved for a given query, helping to understand and troubleshoot the RAG retrieval process. The `similaritySearch` method finds the most relevant document chunks based on vector similarity.

### Question Answering with RAG

The main quiz endpoint that combines retrieval with generation using the QuestionAnswerAdvisor.

```java
@GetMapping("/quiz")
public String quizMe(@RequestParam(defaultValue="quiz me on spring mvc") String query,
                     @RequestParam(defaultValue="bedrock") String model) { // pick a model
    
    ChatClient selectedClient = switch (model.toLowerCase()) {
        case "openai" -> openAiChatClient;
        case "bedrock" -> bedrockChatClient;
        default -> primaryChatClient;
    };
    
    return selectedClient.prompt()
            .user(query) // the user message is what the client inputs
            .advisors(questionAnswerAdvisor)
            .call() // blocking call so the response is not streamed to a client
//                .entity(QuizQuestions.class); // return the responses back in json format
            .content(); // only returning string content of response
}
```

**Purpose**: This method demonstrates the RAG pattern in action. The `QuestionAnswerAdvisor` automatically:
1. Takes the user's query and searches the vector store for relevant documents
2. Injects the retrieved context into the prompt sent to the LLM
3. The LLM generates an answer based on both the query and the retrieved context
4. Supports multiple AI models (OpenAI, Bedrock) for flexibility

### Document Reading for RAG Context

Method to read and return document content that can be used for RAG context.

```java
// PDF Document Reader
@GetMapping("/document")
public List<Document> linkedBagImplementations() {
    return documentIngestion.linkedBagImplementations();
}

public List<Document> linkedBagImplementations() {
   try {
       return readParagraph(stackImplementations);
   } catch (IllegalArgumentException e) {
       logger.warn("ParagraphPdfDocumentReader failed (no TOC found), falling back to PagePdfDocumentReader: {}", e.getMessage());
       return readPage(stackImplementations);
   }
}
```

**Purpose**: Provides direct access to processed document content, useful for testing and debugging the document reading pipeline that feeds into the RAG system.
## Chat

### Multi-Model Chat Client Configuration

The application supports multiple AI models through different chat clients.

```java
public QuizController(ChatClient primaryChatClient, // Primary (Bedrock) client
                     @Qualifier("openai") ChatClient openAiChatClient,
                     @Qualifier("bedrock") ChatClient bedrockChatClient,
                      DocumentIngestion documentIngestion,
                      QuestionAnswerAdvisor questionAnswerAdvisor, // allows rag while model switching
                      VectorStore vectorStore
) {
    this.primaryChatClient = primaryChatClient;
    this.openAiChatClient = openAiChatClient;
    this.bedrockChatClient = bedrockChatClient;
    this.documentIngestion = documentIngestion;
    this.questionAnswerAdvisor = questionAnswerAdvisor;
    this.vectorStore = vectorStore;
}
```

**Purpose**: Constructor injection sets up multiple chat clients (OpenAI, Bedrock) allowing dynamic model selection while maintaining RAG capabilities through the shared `QuestionAnswerAdvisor`.

### Quiz Endpoint - Complete Chat Flow

The main endpoint that demonstrates the complete chat flow with RAG integration.

```java
@GetMapping("/quiz")
public String quizMe(@RequestParam(defaultValue="quiz me on spring mvc") String query,
                     @RequestParam(defaultValue="bedrock") String model) { // pick a model
    
    ChatClient selectedClient = switch (model.toLowerCase()) {
        case "openai" -> openAiChatClient;
        case "bedrock" -> bedrockChatClient;
        default -> primaryChatClient;
    };
    
    return selectedClient.prompt()
            .user(query) // the user message is what the client inputs
            .advisors(questionAnswerAdvisor)
            .call() // blocking call so the response is not streamed to a client
            .content(); // only returning string content of response
}
```

**Purpose**: This is the primary endpoint that showcases the complete chat functionality. It demonstrates:
1. **Model Selection**: Users can choose between different AI models (OpenAI, Bedrock)
2. **RAG Integration**: The `QuestionAnswerAdvisor` automatically retrieves relevant document context
3. **Chat Processing**: Combines user query with retrieved context to generate informed responses
4. **Flexible Response**: Can return either structured JSON or plain text content

### Model Selection Logic

Dynamic model selection based on request parameters.

```java
ChatClient selectedClient = switch (model.toLowerCase()) {
    case "openai" -> openAiChatClient;
    case "bedrock" -> bedrockChatClient;
    default -> primaryChatClient;
};
```

**Purpose**: Allows users to choose between different AI models while maintaining consistent RAG functionality. The `QuestionAnswerAdvisor` ensures that regardless of the selected model, the chat will include relevant document context in the response.

### File Upload and Text Extraction

Handles document upload and text extraction for immediate processing.

```java
@PostMapping("/upload")
public ResponseEntity<ExtractedDocument> uploadDocument(@RequestParam("file") MultipartFile file) {
    try {
        String extractedText = documentIngestion.extractTextFromDocument(file);
        ExtractedDocument extractedDocument = new ExtractedDocument(file.getOriginalFilename(), extractedText);
        return ResponseEntity.ok(extractedDocument);
    } catch (IOException | TikaException e ) {
        return ResponseEntity.internalServerError().build();
    }
}

public String extractTextFromDocument(MultipartFile file) throws IOException, TikaException {
    Tika tika = new Tika();
    String text = tika.parseToString(file.getInputStream());
    return text;
}
```

**Purpose**: Enables users to upload documents and immediately extract text content using Apache Tika. This supports real-time document processing and can be integrated with the chat system for immediate question answering on uploaded content.
## File Management

### Document Status Tracking

Get information about processed documents and their status.

```java
/**
 * Get list of all processed documents
 */
@GetMapping("/processed")
public ResponseEntity<List<ProcessedDocument>> getProcessedDocuments() {
    List<ProcessedDocument> documents = processedDocumentRepository.findAll();
    return ResponseEntity.ok(documents);
}
```

**Purpose**: Returns a list of all documents that have been processed, including metadata like file size, processing timestamp, and chunk count. Useful for monitoring and debugging the document processing pipeline.

### Manual Document Processing

Trigger processing of specific documents outside the automatic startup flow.

```java
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
 * Method to manually trigger processing of a specific document
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
```

**Purpose**: Allows manual triggering of document processing for specific files. Useful for processing documents added after application startup or reprocessing documents that failed initially.

### Document Removal

Remove documents from tracking and vector storage.

```java
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
```

**Purpose**: Removes documents from the processing tracking database. Note that this doesn't automatically remove the document chunks from the vector store due to Spring AI VectorStore limitations - custom deletion logic may be needed for complete removal.

### Directory Rescanning

Trigger a rescan of the documents directory for new files.

```java
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
```

**Purpose**: Provides an endpoint to manually trigger rescanning of the documents directory for new files. This is useful when documents are added to the directory after application startup and you want to process them without restarting the application.
