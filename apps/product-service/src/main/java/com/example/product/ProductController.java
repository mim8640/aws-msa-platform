package com.example.product;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private static final Duration UPLOAD_URL_DURATION =
        Duration.ofMinutes(5);

    private static final Duration DOWNLOAD_URL_DURATION =
        Duration.ofMinutes(10);

    private final ProductRepository repo;
    private final S3Presigner presigner;
    private final String bucket;

    public ProductController(
            ProductRepository repo,
            S3Presigner presigner,
            @Value("${app.s3.bucket}") String bucket) {

        this.repo = repo;
        this.presigner = presigner;
        this.bucket = bucket;
    }


    // =========================================================
    // 상품 생성/수정 Request
    // =========================================================

    public record ProductRequest(

        @NotBlank
        @Size(max = 255)
        String name,

        @NotNull
        @DecimalMin("0.00")
        @Digits(integer = 13, fraction = 2)
        BigDecimal price,

        @Size(max = 2000)
        String description,

        @Size(max = 512)
        String imageKey,

        @NotNull
        @Min(0)
        Integer stock

    ) {
    }


    // =========================================================
    // 상품 Response
    // =========================================================

    public record ProductResponse(

        Long id,

        Long ownerId,

        String name,

        BigDecimal price,

        String description,

        String imageKey,

        String imageUrl,

        Integer stock

    ) {
    }


    // =========================================================
    // 이미지 Presigned URL Request
    // =========================================================

    public record ImageRequest(

        @NotBlank
        String contentType

    ) {
    }


    // =========================================================
    // 재고 차감 Request
    // =========================================================

    public record StockDecreaseRequest(

        @NotNull
        @Min(1)
        Integer quantity

    ) {
    }


    // =========================================================
    // 재고 차감 Response
    // =========================================================

    public record StockDecreaseResponse(

        Long productId,

        Integer quantity,

        Integer remainingStock

    ) {
    }


    // =========================================================
    // 상품 전체 조회
    //
    // GET /api/v1/products
    // =========================================================

    @GetMapping
    public Page<ProductResponse> list(

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size) {

        if (page < 0 || size < 1 || size > 100) {

            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid page or size"
            );
        }

        return repo.findAll(
                PageRequest.of(
                    page,
                    size,
                    Sort.by(
                        Sort.Direction.DESC,
                        "id"
                    )
                )
            )
            .map(this::toResponse);
    }


    // =========================================================
    // 상품 단건 조회
    //
    // GET /api/v1/products/{id}
    // =========================================================

    @GetMapping("/{id}")
    public ProductResponse get(

            @PathVariable
            Long id) {

        Product product = find(id);

        return toResponse(product);
    }


    // =========================================================
    // 상품 생성
    //
    // POST /api/v1/products
    // =========================================================

    @PostMapping
    public ResponseEntity<ProductResponse> create(

            @AuthenticationPrincipal
            Jwt jwt,

            @Valid
            @RequestBody
            ProductRequest request) {

        Long ownerId =
            memberId(jwt);

        checkImageKey(
            request.imageKey(),
            ownerId
        );

        Product product =
            new Product(
                ownerId,
                request.name(),
                request.price(),
                request.description(),
                request.imageKey(),
                request.stock()
            );

        Product savedProduct =
            repo.save(product);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(
                toResponse(savedProduct)
            );
    }


    // =========================================================
    // 상품 수정
    //
    // PUT /api/v1/products/{id}
    // =========================================================

    @PutMapping("/{id}")
    public ProductResponse update(

            @PathVariable
            Long id,

            @AuthenticationPrincipal
            Jwt jwt,

            @Valid
            @RequestBody
            ProductRequest request) {

        Product product =
            find(id);

        Long ownerId =
            memberId(jwt);

        checkOwner(
            product,
            ownerId
        );

        checkImageKey(
            request.imageKey(),
            ownerId
        );

        product.update(
            request.name(),
            request.price(),
            request.description(),
            request.imageKey(),
            request.stock()
        );

        Product savedProduct =
            repo.save(product);

        return toResponse(
            savedProduct
        );
    }


    // =========================================================
    // 상품 삭제
    //
    // DELETE /api/v1/products/{id}
    // =========================================================

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(

            @PathVariable
            Long id,

            @AuthenticationPrincipal
            Jwt jwt) {

        Product product =
            find(id);

        Long ownerId =
            memberId(jwt);

        checkOwner(
            product,
            ownerId
        );

        repo.delete(product);

        return ResponseEntity
            .noContent()
            .build();
    }


    // =========================================================
    // 재고 차감
    //
    // POST /api/v1/products/{id}/stock/decrease
    //
    // Request:
    // {
    //   "quantity": 2
    // }
    // =========================================================

    @PostMapping("/{id}/stock/decrease")
    public StockDecreaseResponse decreaseStock(

            @PathVariable
            Long id,

            @AuthenticationPrincipal
            Jwt jwt,

            @Valid
            @RequestBody
            StockDecreaseRequest request) {

        // 인증된 요청인지 확인
        memberId(jwt);

        // 상품이 실제 존재하는지 먼저 확인
        if (!repo.existsById(id)) {

            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Product not found"
            );
        }

        int updatedRows =
            repo.decreaseStock(
                id,
                request.quantity()
            );

        // 상품은 존재하지만 UPDATE가 되지 않았다면
        // 재고가 주문 수량보다 부족한 것
        if (updatedRows == 0) {

            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Insufficient stock"
            );
        }

        Product product =
            find(id);

        return new StockDecreaseResponse(
            id,
            request.quantity(),
            product.getStock()
        );
    }


    // =========================================================
    // S3 이미지 업로드 Presigned URL
    //
    // POST /api/v1/products/images/presigned-url
    // =========================================================

    @PostMapping("/images/presigned-url")
    public Map<String, String> presign(

            @AuthenticationPrincipal
            Jwt jwt,

            @Valid
            @RequestBody
            ImageRequest request) {

        Long ownerId =
            memberId(jwt);

        String extension =
            switch (request.contentType()) {

                case "image/jpeg" ->
                    ".jpg";

                case "image/png" ->
                    ".png";

                case "image/webp" ->
                    ".webp";

                default ->
                    throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unsupported image type"
                    );
            };

        String key =
            "products/"
                + ownerId
                + "/"
                + UUID.randomUUID()
                + extension;

        PutObjectRequest putObjectRequest =
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(
                    request.contentType()
                )
                .build();

        PutObjectPresignRequest presignRequest =
            PutObjectPresignRequest.builder()
                .signatureDuration(
                    UPLOAD_URL_DURATION
                )
                .putObjectRequest(
                    putObjectRequest
                )
                .build();

        String uploadUrl =
            presigner
                .presignPutObject(
                    presignRequest
                )
                .url()
                .toString();

        return Map.of(
            "key",
            key,
            "uploadUrl",
            uploadUrl
        );
    }


    // =========================================================
    // 상품 조회
    // =========================================================

    private Product find(
            Long id) {

        return repo
            .findById(id)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found"
                    )
            );
    }


    // =========================================================
    // 상품 소유자 확인
    // =========================================================

    private void checkOwner(

            Product product,

            Long ownerId) {

        if (!product
                .getOwnerId()
                .equals(ownerId)) {

            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You cannot modify this product"
            );
        }
    }


    // =========================================================
    // 이미지 Key 확인
    // =========================================================

    private void checkImageKey(

            String imageKey,

            Long ownerId) {

        if (imageKey == null
                || imageKey.isBlank()) {

            return;
        }

        String expectedPrefix =
            "products/"
                + ownerId
                + "/";

        if (!imageKey.startsWith(
                expectedPrefix)) {

            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid image key"
            );
        }
    }


    // =========================================================
    // Product -> ProductResponse
    // =========================================================

    private ProductResponse toResponse(

            Product product) {

        String imageUrl =
            createImageUrl(
                product.getImageKey()
            );

        return new ProductResponse(

            product.getId(),

            product.getOwnerId(),

            product.getName(),

            product.getPrice(),

            product.getDescription(),

            product.getImageKey(),

            imageUrl,

            product.getStock()
        );
    }


    // =========================================================
    // S3 다운로드용 Presigned GET URL
    // =========================================================

    private String createImageUrl(

            String imageKey) {

        if (imageKey == null
                || imageKey.isBlank()) {

            return null;
        }

        GetObjectRequest getObjectRequest =
            GetObjectRequest.builder()
                .bucket(bucket)
                .key(imageKey)
                .build();

        GetObjectPresignRequest presignRequest =
            GetObjectPresignRequest.builder()
                .signatureDuration(
                    DOWNLOAD_URL_DURATION
                )
                .getObjectRequest(
                    getObjectRequest
                )
                .build();

        return presigner
            .presignGetObject(
                presignRequest
            )
            .url()
            .toString();
    }


    // =========================================================
    // JWT -> Member ID
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