package com.example.EPustok.market;
import jakarta.persistence.*;
import lombok.Getter;
import java.math.BigDecimal;
@Entity @Getter public class Book {
@Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
@Column(nullable=false) private Long sellerId;
@Column(nullable=false) private String title;
@Column(nullable=false) private String author;
@Column(nullable=false,precision=12,scale=2) private BigDecimal price;
@Column(nullable=false) private int stock;
@ManyToOne(optional=false) private Category category;
protected Book(){}
public Book(Long sellerId){this.sellerId=sellerId;}
public void update(String title,String author,BigDecimal price,int stock,Category category){this.title=title;this.author=author;this.price=price;this.stock=stock;this.category=category;}
}