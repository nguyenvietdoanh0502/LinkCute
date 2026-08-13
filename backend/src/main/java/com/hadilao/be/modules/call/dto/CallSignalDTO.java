package com.hadilao.be.modules.call.dto;

import com.hadilao.be.modules.call.enums.CallSignalType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallSignalDTO {
    private UUID callId;
    private UUID senderUserId;
    private UUID recipientUserId;
    private CallSignalType type;
    private String sdp;
    private String candidate;
    private String sdpMid;
    private Integer sdpMLineIndex;
}
