package com.example.i_resource_hub.repository;

import com.example.i_resource_hub.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, String> {

     boolean existsByCategoryName(String name);

     //lấy tất car danh mục chưa bị xoá
     @Query( value =
        """
            SELECT *
            FROM categories
            WHERE is_deleted = false
        """,
             nativeQuery = true )
     List<Category> findByDeletedFalse();

     @Query( value =
        """
            SELECT *
            FROM categories
            WHERE is_deleted = true
        """,
        nativeQuery = true )
     List<Category> findByDeletedTrue();

     //xoá cứng bản ghi
     @Query(value = """
            DELETE FROM categories
            WHERE is_deleted = true
            AND updated_at < :thresholdate
        """, nativeQuery = true)
     void deleteCategoryOldRecords(@Param("thresholdate")LocalDateTime thresholdate);
}
