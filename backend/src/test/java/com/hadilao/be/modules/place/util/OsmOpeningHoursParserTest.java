package com.hadilao.be.modules.place.util;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class OsmOpeningHoursParserTest {

    @Test
    void parsesDayRangesSplitHoursAndOvernightHours() {
        var hours = OsmOpeningHoursParser.parse("Mo-Fr 08:00-12:00,13:00-22:00; Sa 18:00-02:00; Su off");

        assertThat(hours).hasSize(11);
        assertThat(hours).anySatisfy(hour -> {
            assertThat(hour.dayOfWeek()).isEqualTo(0);
            assertThat(hour.openTime()).isEqualTo(LocalTime.of(8, 0));
            assertThat(hour.closeTime()).isEqualTo(LocalTime.of(12, 0));
            assertThat(hour.crossesMidnight()).isFalse();
        });
        assertThat(hours).anySatisfy(hour -> {
            assertThat(hour.dayOfWeek()).isEqualTo(5);
            assertThat(hour.crossesMidnight()).isTrue();
        });
    }

    @Test
    void parsesTwentyFourSevenAndLeavesUnsupportedSyntaxUnnormalized() {
        assertThat(OsmOpeningHoursParser.parse("24/7")).hasSize(7);
        assertThat(OsmOpeningHoursParser.parse("sunrise-sunset")).isEmpty();
    }
}
