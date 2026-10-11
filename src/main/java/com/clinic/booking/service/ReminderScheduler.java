package com.clinic.booking.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls for due reminders. Note: with several app instances this would send duplicates; in that case use a
 * distributed lock (e.g. ShedLock) or a single dedicated scheduler instance.
 */
@Component
@ConditionalOnProperty(name = "clinic.reminder.enabled", havingValue = "true", matchIfMissing = true)
public class ReminderScheduler {

    private final ReminderService reminderService;

    public ReminderScheduler(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Scheduled(fixedDelayString = "${clinic.reminder.poll-interval-ms}",
            initialDelayString = "${clinic.reminder.poll-interval-ms}")
    public void run() {
        reminderService.sendDueReminders();
    }
}
