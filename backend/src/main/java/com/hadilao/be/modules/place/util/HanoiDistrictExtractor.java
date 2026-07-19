package com.hadilao.be.modules.place.util;

import java.util.LinkedHashMap;
import java.util.Map;

public final class HanoiDistrictExtractor {

    private static final Map<String, String> DISTRICTS = createDistricts();

    private HanoiDistrictExtractor() {
    }

    public static String extract(String address) {
        String normalizedAddress = " " + PlaceTextNormalizer.normalize(address) + " ";
        return DISTRICTS.entrySet().stream()
                .filter(entry -> normalizedAddress.contains(" " + entry.getKey() + " "))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static Map<String, String> createDistricts() {
        Map<String, String> districts = new LinkedHashMap<>();
        add(districts, "Nam Từ Liêm");
        add(districts, "Bắc Từ Liêm");
        add(districts, "Hai Bà Trưng");
        add(districts, "Hoàn Kiếm");
        add(districts, "Thanh Xuân");
        add(districts, "Cầu Giấy");
        add(districts, "Long Biên");
        add(districts, "Hoàng Mai");
        add(districts, "Hà Đông");
        add(districts, "Đống Đa");
        add(districts, "Ba Đình");
        add(districts, "Tây Hồ");
        add(districts, "Sóc Sơn");
        add(districts, "Đông Anh");
        add(districts, "Gia Lâm");
        add(districts, "Thanh Trì");
        add(districts, "Mê Linh");
        add(districts, "Sơn Tây");
        add(districts, "Ba Vì");
        add(districts, "Phúc Thọ");
        add(districts, "Đan Phượng");
        add(districts, "Hoài Đức");
        add(districts, "Quốc Oai");
        add(districts, "Thạch Thất");
        add(districts, "Chương Mỹ");
        add(districts, "Thanh Oai");
        add(districts, "Thường Tín");
        add(districts, "Phú Xuyên");
        add(districts, "Ứng Hòa");
        add(districts, "Mỹ Đức");
        return districts;
    }

    private static void add(Map<String, String> districts, String district) {
        districts.put(PlaceTextNormalizer.normalize(district), district);
    }
}
