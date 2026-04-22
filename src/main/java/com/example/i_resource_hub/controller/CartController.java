package com.example.i_resource_hub.controller;

import com.example.i_resource_hub.dto.response.CartItemResponse;
import com.example.i_resource_hub.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@CrossOrigin("*")
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<List<CartItemResponse>> getMyCart() {
        return ResponseEntity.ok(cartService.getMyCart());
    }

    @PostMapping
    public ResponseEntity<Void> addToCart(
            @RequestParam String templateId,
            @RequestParam(defaultValue = "1") Integer quantity) {
        cartService.addToCart(templateId, quantity);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{itemId}")
    public ResponseEntity<Void> updateCartItem(
            @PathVariable String itemId,
            @RequestParam(required = false) Integer quantity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String slotId) {
        cartService.updateCartItem(itemId, quantity, date, slotId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> removeFromCart(@PathVariable String itemId) {
        cartService.removeFromCart(itemId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/clear")
    public ResponseEntity<Void> clearCart() {
        cartService.clearCart();
        return ResponseEntity.ok().build();
    }
}
