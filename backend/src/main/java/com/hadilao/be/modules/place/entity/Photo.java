package com.hadilao.be.modules.place.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "photos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = "place")
@ToString(exclude = "place")
public class Photo {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    @JsonIgnore
    private Place place;

    @Column(columnDefinition = "TEXT")
    private String url;

    @Column(name = "google_photo_ref")
    private String googlePhotoRef;

    private Integer width;

    private Integer height;

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;
}
