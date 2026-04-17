package com.example.i_resource_hub.repository.specification;

import com.example.i_resource_hub.entity.User;
import com.example.i_resource_hub.entity.OrganizationUnit;
import com.example.i_resource_hub.entity.Role;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class UserSpecification {

    public static Specification<User> filterUsers(String keyword, String unitId, String status, String roleId) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Text search: username, fullName, studentCode, email
            if (StringUtils.hasText(keyword)) {
                String searchPattern = "%" + keyword.toLowerCase() + "%";
                Predicate keywordPredicate = criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("username")), searchPattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("fullName")), searchPattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("studentCode")), searchPattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), searchPattern)
                );
                predicates.add(keywordPredicate);
            }

            // OrganizationUnit filter
            if (StringUtils.hasText(unitId)) {
                Join<User, OrganizationUnit> unitJoin = root.join("unit");
                predicates.add(criteriaBuilder.equal(unitJoin.get("id"), unitId));
            }

            // Status filter
            if (StringUtils.hasText(status)) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }

            // Role filter
            if (StringUtils.hasText(roleId)) {
                Join<User, Role> roleJoin = root.join("roles");
                predicates.add(criteriaBuilder.equal(roleJoin.get("id"), roleId));
            }

            // Only non-deleted users
            predicates.add(criteriaBuilder.isNull(root.get("deletedAt")));

            // To avoid duplicates when joining @ManyToMany (roles)
            query.distinct(true);

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
