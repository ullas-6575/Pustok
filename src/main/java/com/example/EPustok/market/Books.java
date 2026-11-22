package com.example.EPustok.market;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;
public interface Books extends JpaRepository<Book,Long>, JpaSpecificationExecutor<Book> {List<Book> findBySellerId(Long sellerId); boolean existsByCategoryId(Long categoryId);}
