package com.eventreg.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class EventCreateRequest {

    @NotBlank
    @Size(max = 200)
    private String title;

    private String description;

    @NotBlank
    @Size(max = 200)
    private String venue;

    @NotNull
    @Future(message = "Event date must be in the future")
    private LocalDateTime eventDate;

    @NotNull
    @Min(1)
    private Integer maxCapacity;

    @Min(0)
    private Integer waitlistCap = 0;

    @NotNull
    @DecimalMin(value = "0.0")
    private BigDecimal price;
}
