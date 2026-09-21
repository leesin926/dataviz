package com.dataviz.alert.service;

import com.dataviz.alert.entity.AlertEvent;

/**
 * Sends notifications via configured channels (email, webhook, SMS, dingtalk)
 */
public interface AlertNotifyService {

    /**
     * Send notifications for the given alert event based on rule's notifyChannels config
     */
    void notify(AlertEvent event);
}
