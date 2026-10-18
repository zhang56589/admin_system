package com.example.adminsystem.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class BookingVO {
    private Long id;
    private Long roomId;
    private String roomName;
    private Long userId;
    private String username;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer resStatus;
    private String rejectReason;
    private String title;
    private String reason;
    private LocalDateTime createTime;
}
