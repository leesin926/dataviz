package com.dataviz.collab.dto;

import lombok.Data;

@Data
public class SubscriptionDTO {

    private String targetType;

    private Long targetId;

    private String notifyTypes;
}
