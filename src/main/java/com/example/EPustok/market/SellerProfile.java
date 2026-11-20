package com.example.EPustok.market;
import jakarta.persistence.*;
import lombok.Getter;
@Entity @Getter public class SellerProfile {
@Id private Long id;
@Column(nullable=false) private String shopName;
@Column(nullable=false) private String phone;
@Column(nullable=false) private String address;
@Column(nullable=false) private String status;
@Version private long version;
protected SellerProfile(){}
public SellerProfile(Long id,String shopName,String phone,String address){this.id=id;this.shopName=shopName;this.phone=phone;this.address=address;this.status="PENDING";}
public void decide(boolean approved){status=approved?"APPROVED":"REJECTED";}
}