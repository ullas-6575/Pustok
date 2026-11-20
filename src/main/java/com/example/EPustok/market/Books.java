package com.example.EPustok.market;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface Books extends JpaRepository<Book,Long>{List<Book> findBySellerId(Long sellerId); boolean existsByCategoryId(Long categoryId);}