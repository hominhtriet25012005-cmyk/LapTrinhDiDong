package com.example.demo.data.validation;

import com.example.demo.data.local.entity.ScheduleEntity;

import java.util.List;

/** Detects weekly schedule overlaps that occur on at least one common calendar date. */
public final class ScheduleConflictValidator {
    private ScheduleConflictValidator() {
    }

    public static boolean hasConflict(ScheduleEntity candidate, List<ScheduleEntity> existingSchedules) {
        if (candidate == null || existingSchedules == null || existingSchedules.isEmpty()) {
            return false;
        }
        for (ScheduleEntity existing : existingSchedules) {
            if (existing == null || (candidate.id > 0 && existing.id == candidate.id)) {
                continue;
            }
            if (candidate.dayOfWeek != existing.dayOfWeek
                    || candidate.startMinute >= existing.endMinute
                    || existing.startMinute >= candidate.endMinute) {
                continue;
            }
            if (hasSharedOccurrence(candidate, existing)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSharedOccurrence(ScheduleEntity first, ScheduleEntity second) {
        long overlapStart = Math.max(first.validFromEpochDay, second.validFromEpochDay);
        long overlapEnd = Math.min(first.validToEpochDay, second.validToEpochDay);
        if (overlapStart > overlapEnd) {
            return false;
        }

        int weekdayAtStart = (int) ((Math.floorMod(overlapStart, 7) + 3) % 7) + 1;
        int daysUntilOccurrence = Math.floorMod(first.dayOfWeek - weekdayAtStart, 7);
        return overlapStart + daysUntilOccurrence <= overlapEnd;
    }
}
