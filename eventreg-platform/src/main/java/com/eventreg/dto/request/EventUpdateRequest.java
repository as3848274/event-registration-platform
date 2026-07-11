package com.eventreg.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class EventUpdateRequest {

    @Size(max = 200)
    private String title;

    private String description;

    @Size(max = 200)
    private String venue;

    @Future
    private LocalDateTime eventDate;

    @Min(1)
    private Integer maxCapacity;

    @Min(0)
    private Integer waitlistCap;

    @DecimalMin(value = "0.0")
    private BigDecimal price;
}
