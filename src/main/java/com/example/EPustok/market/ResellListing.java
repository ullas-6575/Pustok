package com.example.EPustok.market;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;

@Entity
@Table(name = "marketplace_resell_listings")
@Getter
public class ResellListing {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "listing_type", nullable = false, length = 20)
    private String listingType;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String author;

    @Column(name = "book_condition", nullable = false, length = 30)
    private String bookCondition;

    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "desired_book")
    private String desiredBook;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 20)
    private String status = "AVAILABLE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ResellListing() {}

    public ResellListing(
            Long userId,
            String listingType,
            String title,
            String author,
            String bookCondition,
            BigDecimal price,
            String desiredBook,
            String description) {
        this.userId = userId;
        this.listingType = listingType;
        this.title = title;
        this.author = author;
        this.bookCondition = bookCondition;
        this.price = price;
        this.desiredBook = desiredBook;
        this.description = description;
        this.status = "AVAILABLE";
    }
}
