package com.example.adminsystem.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApproveBookingDTO {
    @NotNull(message = "预约id不能为空")
    private Long bookingId;

    @NotNull(message = "审批状态不能为空")
    private Integer status;

    private String rejectReason;
}
