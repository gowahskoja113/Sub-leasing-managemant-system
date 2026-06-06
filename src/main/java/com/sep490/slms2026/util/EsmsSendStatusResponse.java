package com.sep490.slms2026.util;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class EsmsSendStatusResponse {

    @JsonProperty("CodeResponse")
    private String codeResponse;

    @JsonProperty("CodeResult")
    private String codeResult;

    @JsonProperty("SMSID")
    private String smsId;

    @JsonProperty("SendStatus")
    private String sendStatus;

    @JsonProperty("SendSuccess")
    private String sendSuccess;

    @JsonProperty("SendFailed")
    private String sendFailed;

    @JsonProperty("SentSuccess")
    private String sentSuccess;

    @JsonProperty("SentFailed")
    private String sentFailed;
}
