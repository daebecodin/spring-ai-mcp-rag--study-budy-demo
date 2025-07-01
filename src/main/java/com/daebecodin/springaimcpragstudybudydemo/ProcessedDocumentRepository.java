package com.daebecodin.springaimcpragstudybudydemo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProcessedDocumentRepository extends JpaRepository<ProcessedDocument, Long> {
    
    Optional<ProcessedDocument> findByFilename(String filename);
    
    boolean existsByFilename(String filename);
    
    void deleteByFilename(String filename);
}
