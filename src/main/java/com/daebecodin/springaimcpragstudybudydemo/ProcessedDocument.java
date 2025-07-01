package com.daebecodin.springaimcpragstudybudydemo;

import jakarta.persistence.*;
import java.time.LocalDateTime;

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
    
    // Constructors
    public ProcessedDocument() {}
    
    public ProcessedDocument(String filename, Long fileSize, Integer chunkCount) {
        this.filename = filename;
        this.fileSize = fileSize;
        this.chunkCount = chunkCount;
        this.processedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getFilename() {
        return filename;
    }
    
    public void setFilename(String filename) {
        this.filename = filename;
    }
    
    public Long getFileSize() {
        return fileSize;
    }
    
    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }
    
    public LocalDateTime getProcessedAt() {
        return processedAt;
    }
    
    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }
    
    public Integer getChunkCount() {
        return chunkCount;
    }
    
    public void setChunkCount(Integer chunkCount) {
        this.chunkCount = chunkCount;
    }
}
