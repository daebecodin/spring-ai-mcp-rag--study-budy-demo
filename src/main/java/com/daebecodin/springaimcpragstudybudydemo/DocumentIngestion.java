package com.daebecodin.springaimcpragstudybudydemo;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DocumentIngestion implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestion.class);
    private final VectorStore vectorStore; // gives us access ot embeddings in a vector database

//    @Value("classpath:/docs/mapping-request.pdf")
//    // opens an input stream from my pdf to the database
//    public Resource mappingRequestPDF;

    @Value("classpath:/docs/stack-implementations.pdf")
    // opens an input stream from my pdf to the database
    public Resource stackImplementations;

    // constructor injecting the vector store
    public DocumentIngestion(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     *
     * @param args the chunks of a text from the processed document
     */
    @Override
    public void run(String... args){
        try {
            log.info("Starting document ingestion process...");
            
            // Check if resource exists
            if (!stackImplementations.exists()) {
                log.error("PDF resource not found: {}", stackImplementations.getDescription());
                return;
            }
            
            log.info("Processing PDF: {}", stackImplementations.getFilename());
            
            // this class splits the input pdf into paragraph chunks and outputs a single Document object ber chunk
            var pdfReader = new PagePdfDocumentReader(stackImplementations); // constructing; splitting the resource
            List<Document> documents = pdfReader.get();
            
            log.info("PDF reader extracted {} documents", documents.size());

            // TextSplitter proved methods for text splitting
            TextSplitter splitter = new TokenTextSplitter(); // splits the text into targeted-sized chunks
            List<Document> splitDocuments = splitter.apply(documents);
            
            log.info("Text splitter created {} document chunks", splitDocuments.size());

            // Check if OpenAI API key is properly configured before attempting to create embeddings
            try {
                // a list of split documents is added to the vector store
                vectorStore.accept(splitDocuments);
                log.info("✅ Vector Store Initialized with {} documents", splitDocuments.size());
            } catch (Exception e) {
                log.error("❌ Failed to create embeddings. This is likely due to missing or invalid OpenAI API key.");
                log.error("Please set your OPENAI_API_KEY environment variable with a valid API key.");
                log.error("You can get your API key from: https://platform.openai.com/account/api-keys");
                log.error("Error details: {}", e.getMessage());
                
                // Don't throw the exception to allow the application to continue running
                // throw e;
            }
            
        } catch (Exception e) {
            log.error("Error during document ingestion: ", e);
            // Don't throw the exception to allow the application to continue running for testing
            // throw e;
        }
    }
    

}
