package com.sep490.slms2026.util;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class EsmsSendResponse {

    @JsonProperty("CodeResult")
    private String codeResult;

    @JsonProperty("CountRegenerate")
    private Integer countRegenerate;

    @JsonProperty("SMSID")
    private String smsId;

    @JsonProperty("ErrorMessage")
    private String errorMessage;
}
