package com.hadilao.be.modules.plan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(
        name = "plan_items",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_plan_items_client_item",
                        columnNames = {"plan_id", "client_item_id"}
                ),
                @UniqueConstraint(
                        name = "uq_plan_items_place",
                        columnNames = {"plan_id", "place_id"}
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanItem {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(name = "client_item_id", nullable = false, length = 128)
    private String clientItemId;

    @Column(nullable = false)
    private int position;

    @Column(name = "place_id", nullable = false)
    private UUID placeId;

    @Column(name = "place_name", nullable = false)
    private String placeName;

    @Column(name = "place_address", columnDefinition = "TEXT")
    private String placeAddress;

    @Column(name = "place_district", length = 100)
    private String placeDistrict;

    @Column(name = "place_category", nullable = false, length = 50)
    private String placeCategory;

    @Column(name = "place_photo_url", columnDefinition = "TEXT")
    private String placePhotoUrl;

    @Column(name = "place_lat")
    private Double placeLat;

    @Column(name = "place_lng")
    private Double placeLng;

    @Column(name = "place_price_level")
    private Integer placePriceLevel;

    @Column(name = "place_price_min")
    private Double placePriceMin;

    @Column(name = "place_price_max")
    private Double placePriceMax;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;
}
