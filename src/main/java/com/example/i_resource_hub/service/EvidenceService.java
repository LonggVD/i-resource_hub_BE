package com.example.i_resource_hub.service;

import com.example.i_resource_hub.dto.request.EvidenceRequest;
import com.example.i_resource_hub.dto.response.EvidenceResponse;
import com.example.i_resource_hub.entity.Booking;
import com.example.i_resource_hub.entity.BookingEvidence;
import com.example.i_resource_hub.entity.ResourceItem;
import com.example.i_resource_hub.entity.User;
import com.example.i_resource_hub.repository.BookingEvidenceRepository;
import com.example.i_resource_hub.repository.BookingRepository;
import com.example.i_resource_hub.repository.UserRepository;
import com.example.i_resource_hub.security.AuthorizationHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EvidenceService {

    private final BookingEvidenceRepository evidenceRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final AuthorizationHelper authHelper;

    @Transactional
    public EvidenceResponse addEvidence(EvidenceRequest request, String userId) {
        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn mượn"));

        // RBAC: manager chỉ thêm minh chứng cho đơn thuộc khoa mình.
        // Admin: pass. Dùng cùng pattern effective-unit như BookingService.getEffectiveUnit
        // (managedByUnit > resourceItem.managedByUnit > template.unit) để không bỏ sót.
        authHelper.requireSameUnitOrAdmin(
                effectiveUnitId(booking),
                "đơn mượn #" + booking.getId());

        User user = userRepository.findByUsername(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        BookingEvidence evidence = BookingEvidence.builder()
                .booking(booking)
                .evidenceType(request.getEvidenceType())
                .imageUrl(request.getImageUrl())
                .description(request.getDescription())
                .createdBy(user)
                .build();

        evidenceRepository.save(evidence);

        // Đánh dấu thẻ mượn có sự cố nếu là ảnh DAMAGE
        if ("DAMAGE".equalsIgnoreCase(request.getEvidenceType())) {
            booking.setHasDamage(true);
            booking.setDamageDescription(request.getDescription());
            bookingRepository.save(booking);
        }

        return toResponse(evidence);
    }

    @Transactional(readOnly = true)
    public List<EvidenceResponse> getEvidencesByBooking(String bookingId) {
        return evidenceRepository.findByBookingIdAndIsDeletedFalseOrderByCreatedAtDesc(bookingId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<EvidenceResponse> getEvidencesByBookings(List<String> bookingIds) {
        return evidenceRepository.findByBookingIds(bookingIds)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Đơn vị "hiệu lực" của 1 booking — managedByUnit > item.managedByUnit > template.unit.
     * Cùng pattern với BookingService.getEffectiveUnit / PenaltyService.effectiveBookingUnitId.
     */
    private String effectiveUnitId(Booking booking) {
        if (booking == null) return null;
        if (booking.getManagedByUnit() != null) return booking.getManagedByUnit().getId();
        ResourceItem item = booking.getResourceItem();
        if (item != null) {
            if (item.getManagedByUnit() != null) return item.getManagedByUnit().getId();
            if (item.getTemplate() != null && item.getTemplate().getUnit() != null) {
                return item.getTemplate().getUnit().getId();
            }
        }
        return null;
    }

    private EvidenceResponse toResponse(BookingEvidence e) {
        Booking booking = e.getBooking();
        String borrowerName = null;
        String borrowerId = null;
        String userId = null;
        String serialNumber = null;
        String deviceName = null;
        String ownerUnitName = null;

        if (booking != null) {
            if (booking.getUser() != null) {
                borrowerName = booking.getUser().getFullName();
                borrowerId = booking.getUser().getStudentCode();
                userId = booking.getUser().getId();
            }
            if (booking.getResourceItem() != null) {
                serialNumber = booking.getResourceItem().getSerialNumber();
                if (booking.getResourceItem().getTemplate() != null) {
                    deviceName = booking.getResourceItem().getTemplate().getName();
                }
            }
            if (booking.getManagedByUnit() != null) {
                ownerUnitName = booking.getManagedByUnit().getUnitName();
            }
        }

        return EvidenceResponse.builder()
                .id(e.getId())
                .bookingId(booking != null ? booking.getId() : null)
                .resourceItemId(e.getResourceItem() != null ? e.getResourceItem().getId() : null)
                .evidenceType(e.getEvidenceType())
                .imageUrl(e.getImageUrl())
                .description(e.getDescription())
                .resolution(e.getResolution())
                .isResolved(e.getIsResolved())
                .createdBy(e.getCreatedBy() != null ? e.getCreatedBy().getFullName() : null)
                .createdAt(e.getCreatedAt())
                .borrowerName(borrowerName)
                .borrowerId(borrowerId)
                .userId(userId)
                .serialNumber(serialNumber)
                .deviceName(deviceName)
                .ownerUnitName(ownerUnitName)
                .build();
    }
}
