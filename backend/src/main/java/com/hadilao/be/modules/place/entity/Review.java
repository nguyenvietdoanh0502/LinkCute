package com.hadilao.be.modules.place.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reviews")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = "place")
@ToString(exclude = "place")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    @JsonIgnore
    private Place place;

    @Column(name = "author_name", length = 255)
    private String authorName;

    private Integer rating;

    @Column(columnDefinition = "TEXT")
    private String text;

    @Column(name = "relative_time_description", length = 100)
    private String relativeTimeDescription;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(length = 50)
    @Builder.Default
    private String source = "google";
}
