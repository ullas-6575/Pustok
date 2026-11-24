package com.example.EPustok.market;
import org.springframework.data.jpa.repository.JpaRepository;
public interface Categories extends JpaRepository<Category,Long>{
boolean existsByName(String name);
boolean existsByNameAndIdNot(String name, Long id);
}
