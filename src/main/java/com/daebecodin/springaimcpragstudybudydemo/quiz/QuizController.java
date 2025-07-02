package com.daebecodin.springaimcpragstudybudydemo.quiz;

import com.daebecodin.springaimcpragstudybudydemo.document.DocumentIngestion;
import org.apache.tika.exception.TikaException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RestController
public class QuizController {

   private final ChatClient primaryChatClient;
   private final ChatClient openAiChatClient;
   private final ChatClient bedrockChatClient;
   private final DocumentIngestion documentIngestion;
   private final VectorStore vectorStore;
   private final QuestionAnswerAdvisor questionAnswerAdvisor;

    /**
     * Constructs a Controller with all chat clients, document process capabilities, data storage, and memory
     * @param primaryChatClient The chat model the request will make calls to
     * @param openAiChatClient OpenAi ChatClient
     * @param bedrockChatClient AWS Bedrock ChatClient
     * @param documentIngestion Our processes for document ingestion
     * @param questionAnswerAdvisor Provides chat memory for the conversion
     * @param vectorStore Stores data as embedding for easy retrieval for the model; allows similarity searches
     */
    public QuizController(ChatClient primaryChatClient,
                         @Qualifier("openai") ChatClient openAiChatClient,
                         @Qualifier("bedrock") ChatClient bedrockChatClient,
                          DocumentIngestion documentIngestion,
                          QuestionAnswerAdvisor questionAnswerAdvisor,
                          VectorStore vectorStore
    ) {
        this.primaryChatClient = primaryChatClient;
        this.openAiChatClient = openAiChatClient;
        this.bedrockChatClient = bedrockChatClient;
        this.documentIngestion = documentIngestion;
        this.questionAnswerAdvisor = questionAnswerAdvisor;
        this.vectorStore = vectorStore;
    }


    /**
     *
     * @param query The request
     * @param model The model to query
     * @return The model response
     */
    @GetMapping("/quiz")
    public String quizMe(@RequestParam(defaultValue="quiz me on spring mvc") String query,
                         @RequestParam(defaultValue="openai") String model) { // pick a model
        
        ChatClient selectedClient = switch (model.toLowerCase()) {
            case "openai" -> openAiChatClient;
            case "bedrock" -> bedrockChatClient;
            default -> primaryChatClient;
        };
        
        return selectedClient.prompt()
                .user(query) // the user message is what the client inputs
                .advisors(questionAnswerAdvisor)
                .call() // blocking call so the response is not streamed to a client
//                .entity(QuizQuestions.class); // return the responses in JSON format
                .content(); // only returning string content of response
    }

    // PDF Document Reader
    @GetMapping("/document")
    public List<Document> linkedBagImplementations() {
        return documentIngestion.linkedBagImplementations();

    }

    /**
     * Uploads a document and returns the extracted text
     * @param file file to be uploaded
     * @return extracted text
     */
    @PostMapping("/upload-for-text")
    public ResponseEntity<ExtractedDocument> uploadDocument(@RequestParam("file") MultipartFile file) {
        try {
            var extractedText = documentIngestion.extractTextFromDocument(file);
            var extractedDocument = new ExtractedDocument(file.getOriginalFilename(), extractedText);
            return ResponseEntity.ok(extractedDocument);
        } catch (IOException | TikaException e ) {
            return ResponseEntity.internalServerError().build();
        }
    }



    /**
     * Debug endpoint to see what documents are retrieved for a query
     * @param query  The client request
     * @param topK The similarity threshold
     * @return Collection of all the embeddings used for the response
     */
    @GetMapping("/debug/search")
    public List<Document> debugSearch(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {

        return Optional.ofNullable(
                vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build()))
                .orElse(Collections.emptyList());

    }

    // Debug endpoint to show retrieved context with similarity scores
    @GetMapping("/debug/context")
    public ResponseEntity<List<DebugContext>> debugContext(
            @RequestParam String query,
            @RequestParam(defaultValue = "3") int topK) {
        List<Document> docs = Optional.ofNullable(vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build()))
                .orElse(Collections.emptyList());

        List<DebugContext> debugContexts = docs.stream()
                .map(doc -> {
                    // create a preview of the first 200 characters
                    String text = doc.getText();
                    assert text != null;
                    String preview = text.length() > 200 ? text.substring(0, 200) : text;
                    return new DebugContext(
                            preview,
                            doc.getMetadata(),
                            text.length(),
                            doc.getId()
                    );
                }).toList();

        
        return ResponseEntity.ok(debugContexts);
    }

    public record ExtractedDocument(String fileName, String content) {
    }
    public record DebugContext(String contentPreview, Object metadata, int fullContentLength, String documentId) {
    }

}


