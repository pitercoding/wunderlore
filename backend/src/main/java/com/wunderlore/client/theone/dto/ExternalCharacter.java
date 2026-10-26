package com.wunderlore.client.theone.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ExternalCharacter(
        @JsonProperty("_id") String id,
        String name
    ) {
}
