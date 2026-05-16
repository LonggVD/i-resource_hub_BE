package com.example.i_resource_hub.repository.specification;

import com.example.i_resource_hub.entity.OrganizationUnit;
import com.example.i_resource_hub.entity.ResourceItem;
import com.example.i_resource_hub.entity.ResourceTemplate;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class ResourceItemSpecification {

    public static Specification<ResourceItem> filter(String templateId,
                                                     String unitId,
                                                     String status,
                                                     String conditionStatus,
                                                     String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Always exclude soft-deleted items
            predicates.add(cb.isFalse(root.get("isDeleted")));

            if (StringUtils.hasText(templateId)) {
                Join<ResourceItem, ResourceTemplate> templateJoin = root.join("template");
                predicates.add(cb.equal(templateJoin.get("id"), templateId));
            }

            if (StringUtils.hasText(unitId)) {
                // 1 item "thuộc unit" nếu managedByUnit trực tiếp khớp HOẶC
                // template.unit khớp (fallback) — đồng nhất với BookingService.getEffectiveUnit.
                // Phải dùng LEFT JOIN để không loại mất items có managedByUnit IS NULL.
                Join<ResourceItem, OrganizationUnit> directUnit =
                        root.join("managedByUnit", JoinType.LEFT);
                Join<ResourceItem, ResourceTemplate> tplJoin =
                        root.join("template", JoinType.LEFT);
                Join<ResourceTemplate, OrganizationUnit> tplUnit =
                        tplJoin.join("unit", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.equal(directUnit.get("id"), unitId),
                        cb.equal(tplUnit.get("id"), unitId)
                ));
            }

            if (StringUtils.hasText(status)) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (StringUtils.hasText(conditionStatus)) {
                predicates.add(cb.equal(root.get("conditionStatus"), conditionStatus));
            }

            // Keyword: serialNumber LIKE %kw% OR template.name LIKE %kw%
            if (StringUtils.hasText(keyword)) {
                String pattern = "%" + keyword.toLowerCase() + "%";
                Join<ResourceItem, ResourceTemplate> templateJoin = root.join("template");
                Predicate serial = cb.like(cb.lower(root.get("serialNumber")), pattern);
                Predicate templateName = cb.like(cb.lower(templateJoin.get("name")), pattern);
                predicates.add(cb.or(serial, templateName));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
