package com.example.order;
import java.time.Instant;
import jakarta.persistence.*;
@Entity @Table(name="orders")
public class CustomerOrder {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false) private Long memberId;
 @Column(nullable=false) private Long productId;
 @Column(nullable=false) private Integer quantity;
 @Column(nullable=false) private Instant createdAt=Instant.now();
 protected CustomerOrder(){}
 public CustomerOrder(Long m,Long p,Integer q){memberId=m;productId=p;quantity=q;}
 public Long getId(){return id;} public Long getMemberId(){return memberId;}
 public Long getProductId(){return productId;} public Integer getQuantity(){return quantity;}
 public Instant getCreatedAt(){return createdAt;}
}
