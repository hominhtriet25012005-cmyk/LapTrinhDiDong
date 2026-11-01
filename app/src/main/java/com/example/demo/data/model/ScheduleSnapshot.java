package com.example.demo.data.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Immutable, date-bounded list of effective class meetings shared by app calculations. */
public final class ScheduleSnapshot {
    private static final Comparator<ClassOccurrence> ORDER = Comparator
            .comparing((ClassOccurrence occurrence) -> occurrence.date)
            .thenComparingInt(occurrence -> occurrence.startMinute)
            .thenComparingInt(occurrence -> occurrence.endMinute)
            .thenComparingLong(occurrence -> occurrence.schedule.scheduleId)
            .thenComparing(occurrence -> occurrence.originalDate)
            .thenComparingLong(occurrence -> occurrence.exceptionId);

    public final LocalDate startDate;
    public final LocalDate endDate;
    public final List<ClassOccurrence> occurrences;

    ScheduleSnapshot(LocalDate startDate, LocalDate endDate,
                     List<ClassOccurrence> occurrences) {
        this.startDate = startDate;
        this.endDate = endDate;
        List<ClassOccurrence> ordered = occurrences == null
                ? new ArrayList<>() : new ArrayList<>(occurrences);
        ordered.sort(ORDER);
        this.occurrences = Collections.unmodifiableList(ordered);
    }

    public List<ClassOccurrence> on(LocalDate date) {
        if (date == null || occurrences.isEmpty()) {
            return Collections.emptyList();
        }
        List<ClassOccurrence> result = new ArrayList<>();
        for (ClassOccurrence occurrence : occurrences) {
            if (occurrence.date.equals(date)) {
                result.add(occurrence);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** Returns occurrences in the intersection of this snapshot and the requested range. */
    public List<ClassOccurrence> between(LocalDate fromInclusive, LocalDate toInclusive) {
        if (fromInclusive == null || toInclusive == null || fromInclusive.isAfter(toInclusive)
                || occurrences.isEmpty()) {
            return Collections.emptyList();
        }
        LocalDate from = fromInclusive.isBefore(startDate) ? startDate : fromInclusive;
        LocalDate to = toInclusive.isAfter(endDate) ? endDate : toInclusive;
        if (from.isAfter(to)) {
            return Collections.emptyList();
        }
        List<ClassOccurrence> result = new ArrayList<>();
        for (ClassOccurrence occurrence : occurrences) {
            if (!occurrence.date.isBefore(from) && !occurrence.date.isAfter(to)) {
                result.add(occurrence);
            }
        }
        return Collections.unmodifiableList(result);
    }
}
