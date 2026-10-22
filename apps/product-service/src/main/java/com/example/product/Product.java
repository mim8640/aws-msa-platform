package com.example.product;

import java.math.BigDecimal;

import jakarta.persistence.*;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @Column(length = 2000)
    private String description;

    @Column(name = "image_key", length = 512)
    private String imageKey;

    // 상품 재고
    @Column(nullable = false)
    private Integer stock = 0;

    protected Product() {
    }

    public Product(
            Long ownerId,
            String name,
            BigDecimal price,
            String description,
            String imageKey,
            Integer stock) {

        this.ownerId = ownerId;
        update(name, price, description, imageKey, stock);
    }

    public void update(
            String name,
            BigDecimal price,
            String description,
            String imageKey,
            Integer stock) {

        this.name = name;
        this.price = price;
        this.description = description;
        this.imageKey = imageKey;
        this.stock = stock;
    }

    /**
     * 주문 시 재고 차감
     */
    public void decreaseStock(Integer quantity) {

        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException(
                "Quantity must be greater than 0"
            );
        }

        if (stock < quantity) {
            throw new IllegalArgumentException(
                "Insufficient stock"
            );
        }

        stock -= quantity;
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getDescription() {
        return description;
    }

    public String getImageKey() {
        return imageKey;
    }

    public Integer getStock() {
        return stock;
    }
}