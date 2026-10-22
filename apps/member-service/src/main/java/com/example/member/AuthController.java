package com.example.member;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import javax.crypto.SecretKey;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
 private final MemberRepository repo; private final PasswordEncoder encoder;
 private final StringRedisTemplate redis; private final SecretKey key;
 AuthController(MemberRepository repo, PasswordEncoder encoder, StringRedisTemplate redis,
   @Value("${app.jwt.secret}") String secret) {
   this.repo=repo; this.encoder=encoder; this.redis=redis;
   this.key=Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
 }
 record Signup(@Email String email,@Size(min=10,max=72) String password,@NotBlank String name){}
 record Login(@Email String email,@NotBlank String password){}
 record TokenResponse(String accessToken,String refreshToken,long expiresIn){}
 @PostMapping("/auth/signup")
 ResponseEntity<?> signup(@Valid @RequestBody Signup r){
   if(repo.existsByEmail(r.email())) return ResponseEntity.status(409).body(Map.of("error","email_exists"));
   Member m=repo.save(new Member(r.email().toLowerCase(Locale.ROOT),encoder.encode(r.password()),r.name()));
   return ResponseEntity.status(201).body(Map.of("id",m.getId(),"email",m.getEmail(),"name",m.getName()));
 }
 @PostMapping("/auth/login")
 ResponseEntity<?> login(@Valid @RequestBody Login r){
   var m=repo.findByEmail(r.email().toLowerCase(Locale.ROOT)).orElse(null);
   if(m==null || !encoder.matches(r.password(),m.getPasswordHash()))
     return ResponseEntity.status(401).body(Map.of("error","invalid_credentials"));
   Instant now=Instant.now();
   String access=Jwts.builder().subject(m.getId().toString()).claim("email",m.getEmail())
     .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(900))).signWith(key, Jwts.SIG.HS512).compact();
   String refresh=UUID.randomUUID().toString();
   redis.opsForValue().set("refresh:"+refresh,m.getId().toString(),Duration.ofDays(14));
   return ResponseEntity.ok(new TokenResponse(access,refresh,900));
 }
}
