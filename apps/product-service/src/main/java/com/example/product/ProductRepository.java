package com.example.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * 재고가 주문 수량 이상일 때만 재고를 차감한다.
     *
     * 성공: 1 반환
     * 재고 부족 또는 상품 없음: 0 반환
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
        UPDATE Product p
           SET p.stock = p.stock - :quantity
         WHERE p.id = :productId
           AND p.stock >= :quantity
    """)
    int decreaseStock(
        @Param("productId") Long productId,
        @Param("quantity") Integer quantity
    );
}