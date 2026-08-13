package com.hadilao.be.modules.call.dto;

public record CallSignalDelivery(
        CallSignalDTO signal,
        String recipientUsername
) {
}
