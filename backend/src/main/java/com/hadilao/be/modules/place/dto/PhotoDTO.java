package com.hadilao.be.modules.place.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PhotoDTO {
    private String url;
    private String googlePhotoRef;
    private Integer width;
    private Integer height;
    private Integer sortOrder;
}
