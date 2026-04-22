package com.example.i_resource_hub.repository;

import com.example.i_resource_hub.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, String> {
    List<CartItem> findByUser_Id(String userId);
    Optional<CartItem> findByUser_IdAndResourceTemplate_Id(String userId, String templateId);
    void deleteByUser_Id(String userId);
}
