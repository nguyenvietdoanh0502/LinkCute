package com.hadilao.be.modules.place.dto;

import com.hadilao.be.modules.place.enums.PlaceCategory;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CategoryDTO {
    private PlaceCategory category;
    private String name;
    private Long count;
}
