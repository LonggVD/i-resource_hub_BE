package com.example.i_resource_hub.service;

import com.example.i_resource_hub.dto.response.CartItemResponse;
import com.example.i_resource_hub.entity.CartItem;
import com.example.i_resource_hub.entity.ResourceTemplate;
import com.example.i_resource_hub.entity.TimeSlot;
import com.example.i_resource_hub.entity.User;
import com.example.i_resource_hub.repository.CartItemRepository;
import com.example.i_resource_hub.repository.ResourceItemRepository;
import com.example.i_resource_hub.repository.ResourceTemplateRepository;
import com.example.i_resource_hub.repository.TimeSlotRepository;
import com.example.i_resource_hub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ResourceTemplateRepository resourceTemplateRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final UserRepository userRepository;
    private final ResourceItemRepository resourceItemRepository;

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Transactional(readOnly = true)
    public List<CartItemResponse> getMyCart() {
        User user = getCurrentUser();
        return cartItemRepository.findByUser_Id(user.getId()).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void addToCart(String templateId, Integer quantity) {
        User user = getCurrentUser();
        ResourceTemplate template = resourceTemplateRepository.findById(templateId)
                .orElseThrow(() -> new RuntimeException("Template not found"));

        cartItemRepository.findByUser_IdAndResourceTemplate_Id(user.getId(), templateId)
                .ifPresentOrElse(
                        item -> item.setQuantity(item.getQuantity() + quantity),
                        () -> {
                            CartItem newItem = CartItem.builder()
                                    .user(user)
                                    .resourceTemplate(template)
                                    .quantity(quantity)
                                    .bookingDate(LocalDate.now())
                                    .build();
                            cartItemRepository.save(newItem);
                        });
    }

    @Transactional
    public void updateCartItem(String itemId, Integer quantity, LocalDate date, String slotId) {
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Cart item not found"));

        if (quantity != null)
            item.setQuantity(quantity);
        if (date != null)
            item.setBookingDate(date);
        if (slotId != null) {
            TimeSlot slot = timeSlotRepository.findById(slotId).orElse(null);
            item.setSlot(slot);
        }
        cartItemRepository.save(item);
    }

    @Transactional
    public void removeFromCart(String itemId) {
        cartItemRepository.deleteById(itemId);
    }

    @Transactional
    public void clearCart() {
        User user = getCurrentUser();
        cartItemRepository.deleteByUser_Id(user.getId());
    }

    private CartItemResponse mapToResponse(CartItem item) {
        return CartItemResponse.builder()
                .id(item.getId())
                .resourceTemplateId(item.getResourceTemplate().getId())
                .resourceName(item.getResourceTemplate().getName())
                .imageUrl(item.getResourceTemplate().getImageUrl())
                .totalQuantity((int) resourceItemRepository
                        .countByTemplate_IdAndIsDeletedFalse(item.getResourceTemplate().getId()))
                .availableQuantity((int) resourceItemRepository
                        .countByTemplate_IdAndIsDeletedFalseAndStatus(item.getResourceTemplate().getId(), "AVAILABLE"))
                .unitName(item.getResourceTemplate().getUnit() != null
                        ? item.getResourceTemplate().getUnit().getUnitName()
                        : "")
                .quantity(item.getQuantity())
                .bookingDate(item.getBookingDate())
                .slotId(item.getSlot() != null ? item.getSlot().getId() : null)
                .slotName(item.getSlot() != null ? item.getSlot().getSlotName() : null)
                .build();
    }
}
