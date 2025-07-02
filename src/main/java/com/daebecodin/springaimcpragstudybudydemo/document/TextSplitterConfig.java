package com.daebecodin.springaimcpragstudybudydemo.document;

import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the chunk processing of the documents
 * Convert to Application Proprties maybe
 */
@Configuration
public class TextSplitterConfig {

    @Value("${spring.ai.text-splitter.chunk-size:500}")
    private int chunkSize;

    @Value("${spring.ai.text-splitter.overlap:100}")
    private int overlap;

    @Value("${spring.ai.text-splitter.min-chunk-size:5}")
    private int minChunkSize;

    @Value("${spring.ai.text-splitter.max-chunk-size:10000}")
    private int maxChunkSize;

    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter(
            chunkSize,
            overlap,
            minChunkSize,
            maxChunkSize,
            true
        );
    }
}
