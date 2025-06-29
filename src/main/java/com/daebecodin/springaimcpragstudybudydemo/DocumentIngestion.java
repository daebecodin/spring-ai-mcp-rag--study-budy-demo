package com.daebecodin.springaimcpragstudybudydemo;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class DocumentIngestion implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestion.class);
    private final VectorStore vectorStore; // gives us access ot embeddings in a vector database

    @Value("classpath:/docs/mapping-request.pdf")
    // opens an input stream from my pdf to the database
    public Resource mappingRequestPDF;

    // constructor injecting the vector store
    public DocumentIngestion(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     *
     * @param args the chunks of a text from the processed document
     * @throws Exception problems processing document
     */
    @Override
    public void run(String... args) throws Exception{
        // this class splits the input pdf into paragraph chunks and outputs a single Document object ber chunk
        var pdfReader = new PagePdfDocumentReader(mappingRequestPDF); // constructing; splitting the resource

        // TextSplitter proved methods for text splitting
        TextSplitter splitter = new TokenTextSplitter(); // splits the text into targeted-sized chunks

        // a list of split documents is added to the vector store
        vectorStore.accept(splitter.apply(pdfReader.get()));

        // logs a message to the console
        log.info("Vector Store Initialized");
    }
    

}
