package com.example.order;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderRepository repo;

    private final RestClient productClient;


    // =========================================================
    // 생성자
    // =========================================================

    public OrderController(

            OrderRepository repo,

            @Value("${app.services.product-url}")
            String productServiceUrl) {

        this.repo = repo;

        // Spring Bean으로 RestClient.Builder를 주입받지 않고
        // Product Service URL을 사용하여 직접 RestClient 생성
        this.productClient =
            RestClient.create(productServiceUrl);
    }


    // =========================================================
    // 주문 생성 Request
    //
    // {
    //   "productId": 1,
    //   "quantity": 2
    // }
    // =========================================================

    public record CreateRequest(

        @NotNull
        Long productId,

        @NotNull
        @Min(1)
        Integer quantity

    ) {
    }


    // =========================================================
    // Product Service 재고 차감 Request
    // =========================================================

    public record StockDecreaseRequest(

        Integer quantity

    ) {
    }


    // =========================================================
    // Product Service 재고 차감 Response
    // =========================================================

    public record StockDecreaseResponse(

        Long productId,

        Integer quantity,

        Integer remainingStock

    ) {
    }


    // =========================================================
    // 주문 생성
    //
    // POST /api/v1/orders
    //
    // 처리 순서:
    //
    // 1. JWT 사용자 확인
    // 2. Product Service 호출
    // 3. 상품 재고 차감
    // 4. 주문 DB 저장
    // 5. 주문 결과 반환
    // =========================================================

    @PostMapping
    public ResponseEntity<CustomerOrder> create(

            @AuthenticationPrincipal
            Jwt jwt,

            @Valid
            @RequestBody
            CreateRequest request) {

        // -----------------------------------------------------
        // 1. 로그인 사용자 확인
        // -----------------------------------------------------

        Long memberId =
            memberId(jwt);


        // -----------------------------------------------------
        // 2. Product Service 재고 차감
        // -----------------------------------------------------

        decreaseProductStock(

            jwt,

            request.productId(),

            request.quantity()
        );


        // -----------------------------------------------------
        // 3. 주문 객체 생성
        // -----------------------------------------------------

        CustomerOrder order =
            new CustomerOrder(

                memberId,

                request.productId(),

                request.quantity()
            );


        // -----------------------------------------------------
        // 4. 주문 DB 저장
        // -----------------------------------------------------

        CustomerOrder savedOrder =
            repo.save(order);


        // -----------------------------------------------------
        // 5. 201 Created
        // -----------------------------------------------------

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(savedOrder);
    }


    // =========================================================
    // 내 주문 목록 조회
    //
    // GET /api/v1/orders
    // =========================================================

    @GetMapping
    public List<CustomerOrder> list(

            @AuthenticationPrincipal
            Jwt jwt) {

        Long memberId =
            memberId(jwt);

        return repo.findByMemberId(
            memberId
        );
    }


    // =========================================================
    // 주문 단건 조회
    //
    // GET /api/v1/orders/{id}
    // =========================================================

    @GetMapping("/{id}")
    public CustomerOrder get(

            @PathVariable
            Long id,

            @AuthenticationPrincipal
            Jwt jwt) {

        Long memberId =
            memberId(jwt);


        CustomerOrder order =
            repo.findById(id)
                .orElseThrow(
                    () ->
                        new ResponseStatusException(

                            HttpStatus.NOT_FOUND,

                            "Order not found"
                        )
                );


        // -----------------------------------------------------
        // 다른 사용자의 주문 조회 방지
        // -----------------------------------------------------

        if (!order
                .getMemberId()
                .equals(memberId)) {

            throw new ResponseStatusException(

                HttpStatus.FORBIDDEN,

                "You cannot access this order"
            );
        }


        return order;
    }


    // =========================================================
    // Product Service 재고 차감
    // =========================================================

    private StockDecreaseResponse decreaseProductStock(

            Jwt jwt,

            Long productId,

            Integer quantity) {

        try {

            StockDecreaseResponse response =
                productClient
                    .post()
                    .uri(
                        "/api/v1/products/{id}/stock/decrease",
                        productId
                    )

                    // 사용자가 보낸 JWT를
                    // Product Service에도 그대로 전달
                    .headers(headers ->
                        headers.setBearerAuth(
                            jwt.getTokenValue()
                        )
                    )

                    .body(
                        new StockDecreaseRequest(
                            quantity
                        )
                    )

                    .retrieve()

                    .body(
                        StockDecreaseResponse.class
                    );


            if (response == null) {

                throw new ResponseStatusException(

                    HttpStatus.BAD_GATEWAY,

                    "Invalid response from product service"
                );
            }


            return response;


        } catch (HttpClientErrorException.NotFound exception) {

            // Product Service -> 404
            throw new ResponseStatusException(

                HttpStatus.NOT_FOUND,

                "Product not found"
            );


        } catch (HttpClientErrorException.Conflict exception) {

            // Product Service -> 409
            throw new ResponseStatusException(

                HttpStatus.CONFLICT,

                "Insufficient stock"
            );


        } catch (HttpClientErrorException.Unauthorized exception) {

            // Product Service -> 401
            throw new ResponseStatusException(

                HttpStatus.UNAUTHORIZED,

                "Authentication failed"
            );


        } catch (HttpClientErrorException.Forbidden exception) {

            // Product Service -> 403
            throw new ResponseStatusException(

                HttpStatus.FORBIDDEN,

                "Product service access denied"
            );


        } catch (HttpClientErrorException exception) {

            // Product Service 기타 4xx
            throw new ResponseStatusException(

                HttpStatus.BAD_GATEWAY,

                "Product service request failed"
            );


        } catch (ResourceAccessException exception) {

            // Product Service 연결 실패
            throw new ResponseStatusException(

                HttpStatus.SERVICE_UNAVAILABLE,

                "Product service unavailable"
            );
        }
    }


    // =========================================================
    // JWT에서 Member ID 추출
    // =========================================================

    private Long memberId(

            Jwt jwt) {

        if (jwt == null) {

            throw new ResponseStatusException(

                HttpStatus.UNAUTHORIZED,

                "Authentication required"
            );
        }


        try {

            Long id =
                Long.valueOf(
                    jwt.getSubject()
                );


            if (id <= 0) {

                throw new NumberFormatException();
            }


            return id;


        } catch (NumberFormatException exception) {

            throw new ResponseStatusException(

                HttpStatus.UNAUTHORIZED,

                "Invalid member identity"
            );
        }
    }
}