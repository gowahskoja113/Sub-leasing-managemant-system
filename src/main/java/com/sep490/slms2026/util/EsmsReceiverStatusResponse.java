package com.sep490.slms2026.util;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class EsmsReceiverStatusResponse {

    @JsonProperty("CodeResult")
    private String codeResult;

    @JsonProperty("ReceiverList")
    private List<Receiver> receiverList;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Receiver {

        @JsonProperty("Phone")
        private String phone;

        @JsonProperty("IsSent")
        private Boolean sent;

        @JsonProperty("SentResult")
        private Boolean sentResult;
    }
}
