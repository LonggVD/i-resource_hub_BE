package com.example.i_resource_hub.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PenaltyRequest {

    @NotBlank(message = "Người vi phạm không được để trống")
    private String userId;

    private String bookingId;

    @NotBlank(message = "Loại vi phạm không được để trống")
    @Pattern(regexp = "OVERDUE|DAMAGE|LOST",
            message = "Loại vi phạm phải là OVERDUE, DAMAGE hoặc LOST")
    private String penaltyType;

    @Min(value = 0, message = "Điểm trừ không thể âm")
    private Integer penaltyPoint;

    @Size(max = 1000, message = "Mô tả tối đa 1000 ký tự")
    private String description;

    @PositiveOrZero(message = "Số tiền phạt không thể âm")
    private Double fineAmount;

    private Boolean requiresReview;

    private java.util.List<String> evidenceUrls;
}
