package com.example.marketplace.repository;

import com.example.marketplace.entity.EmailLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Sort;

import java.util.List;

public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {

    long countBySuccess(boolean success);

    List<EmailLog> findAll(Sort sort);
}
