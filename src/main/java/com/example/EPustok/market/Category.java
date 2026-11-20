package com.example.EPustok.market;
import jakarta.persistence.*;
import lombok.Getter;
@Entity @Getter public class Category {
@Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
@Column(nullable=false,unique=true,length=100) private String name;
protected Category(){} public Category(String name){this.name=name;} public void rename(String name){this.name=name;}
}