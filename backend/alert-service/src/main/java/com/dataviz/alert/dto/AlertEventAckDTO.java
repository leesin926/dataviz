package com.dataviz.alert.dto;

import lombok.Data;

@Data
public class AlertEventAckDTO {

    /**
     * Event ID
     */
    private Long id;

    /**
     * Acknowledgement note
     */
    private String note;
}
