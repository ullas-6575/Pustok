package com.example.EPustok.market;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResellListings extends JpaRepository<ResellListing, Long> {
    List<ResellListing> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<ResellListing> findByStatusOrderByCreatedAtDesc(String status);
}
