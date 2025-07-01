# Document Persistence System

## Overview
This application now includes a document persistence system that prevents re-processing documents on every startup. Only new documents are processed, while existing documents remain in the vector store.

## System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           APPLICATION STARTUP                                   │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        DocumentIngestion.init()                                │
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

## Data Flow Diagram

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

## Component Interaction Flow

```
REST API Endpoints
┌─────────────────────────────────────────────────────────────────┐
│ GET    /api/documents/processed                                 │
│ POST   /api/documents/process/{filename}                        │
│ DELETE /api/documents/{filename}                                │
│ POST   /api/documents/rescan                                    │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│              DocumentManagementController                       │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                DocumentIngestion Service                        │
│                                                                 │
│ • processNewDocument(filename)                                  │
│ • removeDocument(filename)                                      │
│ • getDocumentsToProcess()                                       │
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

## Database Initialization Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                    Application Startup                         │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│              DatabaseInitializer (@Order(1))                   │
│                    @PostConstruct                               │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│         Check if processed_documents table exists              │
│         SELECT EXISTS FROM information_schema.tables           │
└─────────────────────────────────────────────────────────────────┘
                                │
                ┌───────────────┴───────────────┐
                ▼                               ▼
    ┌─────────────────────┐         ┌─────────────────────┐
    │   Table Exists      │         │  Table Missing      │
    │   Log: "exists"     │         │  Create Table       │
    └─────────────────────┘         └─────────────────────┘
                │                               │
                │                               ▼
                │               ┌─────────────────────────────────┐
                │               │        CREATE TABLE             │
                │               │    processed_documents (        │
                │               │      id BIGSERIAL PRIMARY KEY, │
                │               │      filename VARCHAR(255),     │
                │               │      file_size BIGINT,          │
                │               │      processed_at TIMESTAMP,    │
                │               │      chunk_count INTEGER        │
                │               │    );                           │
                │               └─────────────────────────────────┘
                │                               │
                └───────────────┬───────────────┘
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│            DocumentIngestion (@Order(2))                       │
│                  @PostConstruct                                 │
│              Ready to process documents                         │
└─────────────────────────────────────────────────────────────────┘
```

## How It Works

### 1. Document Tracking
- A new `ProcessedDocument` entity tracks which documents have been processed
- Stores filename, file size, processing timestamp, and chunk count
- Uses PostgreSQL database for persistence

### 2. Database Initialization
- `DatabaseInitializer` component runs first on startup
- Automatically creates `processed_documents` table if it doesn't exist
- Ensures database schema is ready before document processing

### 3. Conditional Processing
- On startup, the app scans the `src/main/resources/docs` directory
- Only processes documents that aren't already tracked in the database
- Skips documents that have been previously processed

### 4. Vector Store Persistence
- Configuration: `spring.ai.vectorstore.pgvector.remove-existing-vector-store-table=false`
- Vector embeddings are preserved between application restarts
- New documents are added to existing vector store data

### 5. Error Handling
- Graceful handling of database connection issues
- If table doesn't exist, documents are processed as new
- Robust error recovery mechanisms

## Key Components

### ProcessedDocument Entity
```java
@Entity
@Table(name = "processed_documents")
public class ProcessedDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String filename;
    
    @Column(name = "file_size")
    private Long fileSize;
    
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
    
    @Column(name = "chunk_count")
    private Integer chunkCount;
}
```

### ProcessedDocumentRepository
```java
@Repository
public interface ProcessedDocumentRepository extends JpaRepository<ProcessedDocument, Long> {
    Optional<ProcessedDocument> findByFilename(String filename);
    boolean existsByFilename(String filename);
    void deleteByFilename(String filename);
}
```

### DatabaseInitializer
```java
@Component
@Order(1)
public class DatabaseInitializer {
    @PostConstruct
    public void initializeDatabase() {
        // Check and create processed_documents table if needed
    }
}
```

### DocumentIngestion Service
```java
@Service
@Order(2)
public class DocumentIngestion {
    @PostConstruct
    @Transactional
    void init() throws IOException {
        // Process only new documents
    }
}
```

## API Endpoints Documentation

### Chat and Quiz Endpoints

#### `/quiz` - AI-Powered Quiz Generation
```http
GET /quiz?query={question}&model={modelType}
```

**Purpose:** Generate AI responses for study questions using different language models.

**Parameters:**
- `query` (optional, default="tell me about rest controllers in spring"): The question or topic to ask about
- `model` (optional, default="bedrock"): Choose AI model - "openai", "bedrock", or "primary"

**Code Snippet:**
```java
@GetMapping("/quiz")
public String quizMe(@RequestParam(defaultValue="tell me about rest controllers in spring") String query,
                     @RequestParam(defaultValue="bedrock") String model) {
    
    ChatClient selectedClient = switch (model.toLowerCase()) {
        case "openai" -> openAiChatClient;
        case "bedrock" -> bedrockChatClient;
        default -> primaryChatClient;
    };
    
    return selectedClient.prompt()
            .user(query)
            // .advisors(questionAnswerAdvisor) // Uncomment for RAG
            .call()
            .content();
}
```

**What it returns:**
- String response from the selected AI model
- Study guidance and explanations based on the query
- Currently does NOT use RAG (QuestionAnswerAdvisor is commented out)

**Use cases:**
- Get AI-powered study help and explanations
- Test different AI models for response quality
- Generate quiz questions and study materials

**Example:**
```bash
curl "http://localhost:8080/quiz?query=explain%20Spring%20dependency%20injection&model=bedrock"
```

### Document Processing Endpoints

#### `/document` - Get Processed Documents
```http
GET /document
```

**Purpose:** Returns processed document content from the linked bag implementations.

**Code Snippet:**
```java
@GetMapping("/document")
public List<Document> linkedBagImplementations() {
    return documentIngestion.linkedBagImplementations();
}
```

**What it returns:**
- List of `Document` objects from processed files
- Raw document content and metadata

**Use cases:**
- View processed document structure
- Debug document parsing and chunking

#### `/upload` - Upload and Extract Document Content
```http
POST /upload
Content-Type: multipart/form-data
```

**Purpose:** Upload a document file and extract its text content using Apache Tika.

**Parameters:**
- `file` (required): Multipart file upload (PDF, DOCX, TXT, etc.)

**Code Snippet:**
```java
@PostMapping("/upload")
public ResponseEntity<ExtractedDocument> uploadDocument(@RequestParam("file") MultipartFile file) {
    try {
        String extractedText = documentIngestion.extractTextFromDocument(file);
        ExtractedDocument extractedDocument = new ExtractedDocument(file.getOriginalFilename(), extractedText);
        return ResponseEntity.ok(extractedDocument);
    } catch (IOException | TikaException e) {
        return ResponseEntity.internalServerError().build();
    }
}

private static class ExtractedDocument {
    private String fileName;
    private String content;
    
    public ExtractedDocument(String fileName, String content) {
        this.fileName = fileName;
        this.content = content;
    }
    
    // getters...
}
```

**What it returns:**
- JSON object with filename and extracted text content
- `ExtractedDocument` object containing file metadata

**Use cases:**
- Test document text extraction capabilities
- Preview content before adding to document store
- Validate file processing pipeline

**Example:**
```bash
curl -X POST -F "file=@mydocument.pdf" http://localhost:8080/upload
```

### Document Management API

#### `/api/documents/processed` - List Processed Documents
```http
GET /api/documents/processed
```

**Purpose:** Retrieve all documents that have been processed and stored in the database.

**Code Snippet:**
```java
@GetMapping("/processed")
public ResponseEntity<List<ProcessedDocument>> getProcessedDocuments() {
    List<ProcessedDocument> processedDocs = processedDocumentRepository.findAll();
    return ResponseEntity.ok(processedDocs);
}
```

**What it returns:**
- List of `ProcessedDocument` entities
- Includes filename, file size, processing timestamp, and chunk count

**Use cases:**
- Audit which documents have been ingested
- Monitor document processing status
- Track processing history and statistics

#### `/api/documents/process/{filename}` - Process Specific Document
```http
POST /api/documents/process/{filename}
```

**Purpose:** Manually trigger processing of a specific document file.

**Parameters:**
- `filename` (path parameter): Name of the file to process

**Code Snippet:**
```java
@PostMapping("/process/{filename}")
public ResponseEntity<String> processDocument(@PathVariable String filename) {
    try {
        documentIngestion.processNewDocument(filename);
        return ResponseEntity.ok("Document processed successfully: " + filename);
    } catch (Exception e) {
        return ResponseEntity.badRequest().body("Error processing document: " + e.getMessage());
    }
}
```

**What it returns:**
- Processing status and result information

**Use cases:**
- Force reprocessing of a specific document
- Process new documents without full application restart
- Selective document ingestion

#### `/api/documents/{filename}` - Remove Document Tracking
```http
DELETE /api/documents/{filename}
```

**Purpose:** Remove a document from the processed documents tracking database.

**Parameters:**
- `filename` (path parameter): Name of the file to remove from tracking

**Code Snippet:**
```java
@DeleteMapping("/{filename}")
public ResponseEntity<String> removeDocument(@PathVariable String filename) {
    try {
        documentIngestion.removeDocument(filename);
        return ResponseEntity.ok("Document removed from tracking: " + filename);
    } catch (Exception e) {
        return ResponseEntity.badRequest().body("Error removing document: " + e.getMessage());
    }
}
```

**What it returns:**
- Deletion confirmation status

**Use cases:**
- Clean up document tracking records
- Remove outdated or incorrect document entries
- Prepare for document reprocessing

**Note:** This only removes database tracking, not vector store embeddings.

#### `/api/documents/rescan` - Trigger Document Rescan
```http
POST /api/documents/rescan
```

**Purpose:** Scan the documents directory for new files and process any untracked documents.

**Code Snippet:**
```java
@PostMapping("/rescan")
public ResponseEntity<String> rescanDocuments() {
    try {
        List<String> newDocuments = documentIngestion.getDocumentsToProcess();
        for (String filename : newDocuments) {
            documentIngestion.processNewDocument(filename);
        }
        return ResponseEntity.ok("Rescan completed. Processed " + newDocuments.size() + " new documents.");
    } catch (Exception e) {
        return ResponseEntity.badRequest().body("Error during rescan: " + e.getMessage());
    }
}
```

**What it returns:**
- Scan results and processing status

**Use cases:**
- Process new documents added to the directory
- Refresh document store without application restart
- Batch process multiple new documents

### RAG Debug Endpoints

#### `/debug/search` - Vector Store Document Retrieval
```http
GET /debug/search?query={searchQuery}&topK={numberOfResults}
```

**Purpose:** Returns raw `Document` objects from the vector store that match your query.

**Parameters:**
- `query` (required): The search query to find relevant documents
- `topK` (optional, default=5): Number of top results to return

**Code Snippet:**
```java
@GetMapping("/debug/search")
public List<Document> debugSearch(@RequestParam String query, 
                                @RequestParam(defaultValue = "5") int topK) {
    return vectorStore.similaritySearch(SearchRequest.query(query).withTopK(topK));
}
```

**What it returns:**
- Complete `Document` objects with full content and metadata
- Shows exactly what documents your vector store considers most relevant
- Includes metadata like source filename, chunk IDs, and similarity scores

**Use cases:**
- Verify that documents were properly ingested into the vector store
- Test if your search queries retrieve relevant content
- Debug document chunking and embedding quality
- Inspect document metadata and structure

**Example:**
```bash
curl "http://localhost:8080/debug/search?query=Spring%20dependency%20injection&topK=3"
```

#### `/debug/context` - RAG Context Preview
```http
GET /debug/context?query={searchQuery}&topK={numberOfResults}
```

**Purpose:** Shows the processed text content that would be provided to your language model for RAG.

**Parameters:**
- `query` (required): The search query to find relevant context
- `topK` (optional, default=3): Number of document chunks to retrieve

**Code Snippet:**
```java
@GetMapping("/debug/context")
public ResponseEntity<String> debugContext(@RequestParam String query, 
                                         @RequestParam(defaultValue = "3") int topK) {
    List<String> docTexts = vectorStore.similaritySearch(SearchRequest.builder()
            .query(query)
            .topK(topK)
            .build())
           .stream()
           .map(Document::getText)
           .toList();
    
    StringBuilder contextString = new StringBuilder();
    contextString.append("Query: ").append(query).append("\n\n");
    contextString.append("Retrieved ").append(docTexts.size()).append(" document chunks:\n\n");
    
    for (int i = 0; i < docTexts.size(); i++) {
        contextString.append("--- Chunk ").append(i + 1).append(" ---\n");
        contextString.append(docTexts.get(i)).append("\n\n");
    }
    
    return ResponseEntity.ok(contextString.toString());
}
```

**What it returns:**
- Formatted string representation of retrieved document content
- Preview of exactly what context the `QuestionAnswerAdvisor` would use
- Text content extracted using `Document::getText` method
- Organized view showing query and retrieved chunks

**Use cases:**
- Preview the context that will be sent to your AI model
- Verify that relevant information is being retrieved for questions
- Test different query formulations to optimize retrieval
- Debug RAG response quality issues

**Example:**
```bash
curl "http://localhost:8080/debug/context?query=REST%20controllers%20Spring&topK=2"
```

## How to Verify RAG is Working

### Step 1: Check Document Processing
```bash
# Verify documents were ingested
curl -X GET http://localhost:8080/api/documents/processed
```

### Step 2: Test Vector Store Retrieval
```bash
# Test if vector store finds relevant documents
curl "http://localhost:8080/debug/search?query=your%20specific%20topic&topK=3"
```

### Step 3: Preview RAG Context
```bash
# See what context would be provided to the model
curl "http://localhost:8080/debug/context?query=your%20question&topK=3"
```

### Step 4: Test Model Response
```bash
# Test actual model response (ensure RAG advisor is enabled)
curl "http://localhost:8080/quiz?query=ask%20about%20your%20document%20content"
```

### Signs RAG is Working ✅
- Debug endpoints return relevant document chunks
- Model responses reference specific details from your PDFs
- Answers include technical specifics that match your document content
- Model mentions concepts unique to your ingested documents

### Signs RAG is NOT Working ❌
- Debug endpoints return empty or irrelevant results
- Model gives generic answers that could come from training data
- No specific references to your document content
- Responses don't reflect the knowledge in your ingested documents

### Document Management API
New REST endpoints for managing documents:

- `GET /api/documents/processed` - List all processed documents
- `POST /api/documents/process/{filename}` - Manually process a specific document
- `DELETE /api/documents/{filename}` - Remove document from tracking
- `POST /api/documents/rescan` - Trigger rescan for new documents

## Usage

### Adding New Documents
1. Place new PDF, TXT, DOCX, JSON, or XML files in `src/main/resources/docs/`
2. Restart the application OR call the rescan endpoint
3. Only new documents will be processed and added to the vector store

### Removing Documents
1. Delete the file from `src/main/resources/docs/`
2. Call `DELETE /api/documents/{filename}` to remove from tracking
3. Note: Vector store entries are not automatically removed (requires manual cleanup)

### Monitoring
- Check application logs for processing status
- Use `GET /api/documents/processed` to see all tracked documents
- Each document entry shows processing timestamp and chunk count

### API Examples
```bash
# List processed documents
curl -X GET http://localhost:8080/api/documents/processed

# Process a specific document
curl -X POST http://localhost:8080/api/documents/process/mydocument.pdf

# Remove document from tracking
curl -X DELETE http://localhost:8080/api/documents/mydocument.pdf

# Trigger rescan for new documents
curl -X POST http://localhost:8080/api/documents/rescan
```

## Benefits

1. **Faster Startup**: No re-processing of existing documents
2. **Persistent Knowledge**: Vector embeddings survive application restarts
3. **Incremental Updates**: Only new documents are processed
4. **Resource Efficiency**: Saves processing time and API calls
5. **Audit Trail**: Track when documents were processed
6. **Automatic Database Setup**: Tables created automatically
7. **Error Recovery**: Graceful handling of database issues
8. **Manual Control**: REST API for document management

## Configuration

Key application properties:
```properties
# Preserve vector store data
spring.ai.vectorstore.pgvector.remove-existing-vector-store-table=false

# JPA/Hibernate configuration
spring.jpa.hibernate.ddl-auto=update
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect

# Document processing patterns
inputFilenamePattern=*.{json,st,xml,pdf,mp3,mp4,docx,txt,pages,csv}
```

## Database Schema

The system automatically creates a `processed_documents` table:
```sql
CREATE TABLE processed_documents (
    id BIGSERIAL PRIMARY KEY,
    filename VARCHAR(255) UNIQUE NOT NULL,
    file_size BIGINT,
    processed_at TIMESTAMP,
    chunk_count INTEGER
);
```

## Troubleshooting

### Document Not Processing
- Check if document already exists in `processed_documents` table
- Verify file is in correct directory: `src/main/resources/docs/`
- Check file extension matches supported patterns

### Vector Store Issues
- Ensure PostgreSQL connection is working
- Check vector store configuration in application.properties
- Verify `remove-existing-vector-store-table=false`

### Manual Reset
To completely reset and reprocess all documents:
1. Stop the application
2. Delete all records from `processed_documents` table
3. Optionally drop and recreate vector store tables
4. Restart the application
