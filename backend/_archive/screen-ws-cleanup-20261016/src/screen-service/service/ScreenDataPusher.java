package com.dataviz.screen.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ScreenDataPusher {

    @Scheduled(fixedDelay = 5000)
    public void refreshAndPushData() {
        log.debug("Refreshing screen data and pushing via WebSocket");
        // TODO: Fetch latest data from data sources
        // TODO: Push updated data to connected WebSocket clients
    }
}
