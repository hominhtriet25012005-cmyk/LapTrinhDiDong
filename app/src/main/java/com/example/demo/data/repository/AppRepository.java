package com.example.demo.data.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.example.demo.data.local.AppDatabase;
import com.example.demo.data.local.entity.ClassSectionEntity;
import com.example.demo.data.local.entity.ScheduleEntity;
import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.entity.SubjectEntity;
import com.example.demo.data.local.projection.ScheduleDetails;
import com.example.demo.data.local.projection.SemesterSummary;
import com.example.demo.data.local.relation.ClassSectionWithSchedules;
import com.example.demo.data.local.relation.SemesterWithSubjects;
import com.example.demo.data.local.relation.SubjectWithClassSections;
import com.example.demo.data.model.ClassOccurrence;
import com.example.demo.data.model.ScheduleResolver;
import com.example.demo.data.model.ScheduleSnapshot;
import com.example.demo.data.search.SearchFilters;
import com.example.demo.data.validation.ScheduleValidator;
import com.example.demo.notifications.NotificationScheduler;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * App-level data facade. Observable reads are backed by Room; writes are serialized
 * on a background executor so callers cannot accidentally block the UI thread.
 */
public final class AppRepository {
    private static volatile AppRepository instance;

    private final AppDatabase database;
    private final Context appContext;
    private final ExecutorService writeExecutor;
    private final SchedulePersistence schedulePersistence;
    private final ScheduleExceptionPersistence scheduleExceptionPersistence;
    private final SubjectPersistence subjectPersistence;

    private AppRepository(Context context) {
        appContext = context.getApplicationContext();
        database = AppDatabase.getInstance(appContext);
        schedulePersistence = new SchedulePersistence(database);
        scheduleExceptionPersistence = new ScheduleExceptionPersistence(database);
        subjectPersistence = new SubjectPersistence(database);
        writeExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "personal-schedule-db");
            thread.setDaemon(true);
            return thread;
        });
    }

    public static AppRepository getInstance(Context context) {
        AppRepository current = instance;
        if (current == null) {
            synchronized (AppRepository.class) {
                current = instance;
                if (current == null) {
                    current = new AppRepository(context.getApplicationContext());
                    instance = current;
                }
            }
        }
        return current;
    }

    public LiveData<List<SemesterEntity>> observeSemesters() {
        return database.semesterDao().observeAll();
    }

    public LiveData<List<SemesterSummary>> observeSemesterSummaries() {
        return database.semesterDao().observeSummaries();
    }

    public LiveData<SemesterEntity> observeSemester(long semesterId) {
        return database.semesterDao().observeById(semesterId);
    }

    public Future<SemesterEntity> getSemester(long semesterId) {
        return writeExecutor.submit(() -> database.semesterDao().getById(semesterId));
    }

    public LiveData<SemesterEntity> observeCurrentSemester() {
        return database.semesterDao().observeCurrentSemester();
    }

    public LiveData<SemesterWithSubjects> observeSemesterWithSubjects(long semesterId) {
        return database.semesterDao().observeWithSubjects(semesterId);
    }

    public Future<Long> insertSemester(SemesterEntity semester) {
        ScheduleValidator.validateSemesterDates(semester.startDateEpochDay, semester.endDateEpochDay);
        return writeExecutor.submit(() -> database.semesterDao().insert(semester));
    }

    public Future<Integer> updateSemester(SemesterEntity semester) {
        ScheduleValidator.validateSemesterDates(semester.startDateEpochDay, semester.endDateEpochDay);
        return writeExecutor.submit(() -> database.semesterDao().update(semester));
    }

    public Future<Integer> deleteSemester(SemesterEntity semester) {
        return writeExecutor.submit(() -> {
            int deleted = database.semesterDao().delete(semester);
            if (deleted > 0) {
                syncNotifications();
            }
            return deleted;
        });
    }

    public Future<Boolean> setCurrentSemester(long semesterId) {
        return writeExecutor.submit(() -> database.semesterDao().setCurrentSemester(semesterId));
    }

    public LiveData<List<SubjectEntity>> observeSubjects(long semesterId) {
        return database.subjectDao().observeForSemester(semesterId);
    }

    public LiveData<SubjectEntity> observeSubject(long subjectId) {
        return database.subjectDao().observeById(subjectId);
    }

    public LiveData<List<SubjectEntity>> observeAllSubjects() {
        return database.subjectDao().observeAll();
    }

    public LiveData<List<SubjectEntity>> searchSubjects(long semesterId, String query) {
        return Transformations.map(database.subjectDao().observeForSemester(semesterId),
                subjects -> SearchFilters.filterSubjects(subjects, query));
    }

    public LiveData<List<SubjectEntity>> searchAllSubjects(String query) {
        return Transformations.map(database.subjectDao().observeAll(),
                subjects -> SearchFilters.filterSubjects(subjects, query));
    }

    public LiveData<SubjectWithClassSections> observeSubjectWithClassSections(long subjectId) {
        return database.subjectDao().observeWithClassSections(subjectId);
    }

    public Future<Long> insertSubject(SubjectEntity subject) {
        return writeExecutor.submit(() -> subjectPersistence.insert(subject));
    }

    public Future<Integer> updateSubject(SubjectEntity subject) {
        return writeExecutor.submit(() -> {
            int updated = subjectPersistence.update(subject);
            if (updated > 0) {
                syncNotifications();
            }
            return updated;
        });
    }

    public Future<Integer> deleteSubject(SubjectEntity subject) {
        return writeExecutor.submit(() -> {
            int deleted = database.subjectDao().delete(subject);
            if (deleted > 0) {
                syncNotifications();
            }
            return deleted;
        });
    }

    public LiveData<List<ClassSectionEntity>> observeClassSections(long subjectId) {
        return database.classSectionDao().observeForSubject(subjectId);
    }

    public LiveData<ClassSectionEntity> observeClassSection(long classSectionId) {
        return database.classSectionDao().observeById(classSectionId);
    }

    public LiveData<ClassSectionWithSchedules> observeClassSectionWithSchedules(long classSectionId) {
        return database.classSectionDao().observeWithSchedules(classSectionId);
    }

    public Future<Long> insertClassSection(ClassSectionEntity classSection) {
        return writeExecutor.submit(() -> {
            long id = schedulePersistence.saveClassSection(classSection);
            syncNotifications();
            return id;
        });
    }

    public Future<Integer> updateClassSection(ClassSectionEntity classSection) {
        return writeExecutor.submit(() -> {
            schedulePersistence.saveClassSection(classSection);
            syncNotifications();
            return 1;
        });
    }

    public Future<Integer> deleteClassSection(ClassSectionEntity classSection) {
        return writeExecutor.submit(() -> {
            int deleted = schedulePersistence.deleteClassSection(classSection);
            if (deleted > 0) {
                syncNotifications();
            }
            return deleted;
        });
    }

    public LiveData<List<ScheduleEntity>> observeSchedules(long classSectionId) {
        return database.scheduleDao().observeForClassSection(classSectionId);
    }

    public LiveData<ScheduleEntity> observeSchedule(long scheduleId) {
        return database.scheduleDao().observeById(scheduleId);
    }

    public LiveData<List<ScheduleEntity>> observeSchedulesForDay(int dayOfWeek) {
        return database.scheduleDao().observeForDayOfWeek(dayOfWeek);
    }

    public Future<List<ScheduleDetails>> getAllScheduleDetails() {
        return writeExecutor.submit(() -> database.scheduleDao().getAllDetails());
    }

    /** Reads recurring rows and their exceptions atomically, then resolves one dated snapshot. */
    public Future<ScheduleSnapshot> getScheduleSnapshot(LocalDate fromInclusive,
                                                        LocalDate toInclusive) {
        if (fromInclusive == null || toInclusive == null) {
            throw new IllegalArgumentException("Ngày bắt đầu và kết thúc là bắt buộc.");
        }
        return writeExecutor.submit(() -> database.runInTransaction(() ->
                ScheduleResolver.resolve(database.scheduleDao().getAllDetails(),
                        database.scheduleExceptionDao().getAll(), fromInclusive, toInclusive)));
    }

    /** Searches resolved meetings by their actual date, day, room, subject and semester. */
    public Future<List<ClassOccurrence>> searchScheduleOccurrences(
            String query, long semesterId, int dayOfWeek, String roomQuery,
            LocalDate fromInclusive, LocalDate toInclusive) {
        if (fromInclusive == null || toInclusive == null) {
            throw new IllegalArgumentException("Ngày bắt đầu và kết thúc là bắt buộc.");
        }
        return writeExecutor.submit(() -> database.runInTransaction(() -> {
            ScheduleSnapshot snapshot = ScheduleResolver.resolve(
                    database.scheduleDao().getAllDetails(),
                    database.scheduleExceptionDao().getAll(), fromInclusive, toInclusive);
            return SearchFilters.filterOccurrences(snapshot.occurrences, query, semesterId,
                    dayOfWeek, roomQuery, fromInclusive.toEpochDay(), toInclusive.toEpochDay());
        }));
    }

    public LiveData<List<ScheduleDetails>> observeAllScheduleDetails() {
        return database.scheduleDao().observeAllDetails();
    }

    public LiveData<List<ScheduleDetails>> searchScheduleDetails(String query, long semesterId,
                                                                  int dayOfWeek, String roomQuery) {
        return searchScheduleDetails(query, semesterId, dayOfWeek, roomQuery, null, null);
    }

    public LiveData<List<ScheduleDetails>> searchScheduleDetails(String query, long semesterId,
                                                                  int dayOfWeek, String roomQuery,
                                                                  Long fromEpochDay,
                                                                  Long toEpochDay) {
        return Transformations.map(database.scheduleDao().observeAllDetails(),
                schedules -> SearchFilters.filterScheduleDetails(
                        schedules, query, semesterId, dayOfWeek, roomQuery,
                        fromEpochDay, toEpochDay));
    }

    public LiveData<ScheduleDetails> observeScheduleDetails(long scheduleId) {
        return database.scheduleDao().observeDetailsById(scheduleId);
    }

    public LiveData<List<ScheduleExceptionEntity>> observeScheduleExceptions(long scheduleId) {
        return database.scheduleExceptionDao().observeForSchedule(scheduleId);
    }

    public Future<List<ScheduleExceptionEntity>> getScheduleExceptions(long scheduleId) {
        return writeExecutor.submit(() -> database.scheduleExceptionDao().getForSchedule(scheduleId));
    }

    public Future<List<ScheduleExceptionEntity>> getAllScheduleExceptions() {
        return writeExecutor.submit(() -> database.scheduleExceptionDao().getAll());
    }

    public LiveData<List<ScheduleExceptionEntity>> observeAllScheduleExceptions() {
        return database.scheduleExceptionDao().observeAll();
    }

    public Future<Long> insertScheduleException(ScheduleExceptionEntity exception) {
        return writeExecutor.submit(() -> {
            long id = scheduleExceptionPersistence.insert(exception);
            syncNotifications();
            return id;
        });
    }

    public Future<Integer> updateScheduleException(ScheduleExceptionEntity exception) {
        return writeExecutor.submit(() -> {
            int updated = scheduleExceptionPersistence.update(exception);
            if (updated > 0) {
                syncNotifications();
            }
            return updated;
        });
    }

    public Future<Integer> deleteScheduleException(ScheduleExceptionEntity exception) {
        return writeExecutor.submit(() -> {
            int deleted = scheduleExceptionPersistence.delete(exception);
            if (deleted > 0) {
                syncNotifications();
            }
            return deleted;
        });
    }

    public Future<Long> insertSchedule(ScheduleEntity schedule) {
        validateSchedule(schedule);
        return writeExecutor.submit(() -> {
            long id = schedulePersistence.saveSchedule(schedule);
            syncNotifications();
            return id;
        });
    }

    public Future<Integer> updateSchedule(ScheduleEntity schedule) {
        validateSchedule(schedule);
        return writeExecutor.submit(() -> {
            schedulePersistence.saveSchedule(schedule);
            syncNotifications();
            return 1;
        });
    }

    public Future<Long> saveScheduleWithClassSection(ClassSectionEntity classSection,
                                                     ScheduleEntity schedule) {
        validateSchedule(schedule);
        return writeExecutor.submit(() -> {
            long id = schedulePersistence.saveScheduleWithClassSection(classSection, schedule);
            syncNotifications();
            return id;
        });
    }

    public Future<Integer> deleteSchedule(ScheduleEntity schedule) {
        return writeExecutor.submit(() -> {
            int deleted = database.scheduleDao().delete(schedule);
            if (deleted > 0) {
                syncNotifications();
            }
            return deleted;
        });
    }

    private void syncNotifications() {
        NotificationScheduler.syncAll(appContext);
    }

    private static void validateSchedule(ScheduleEntity schedule) {
        if (schedule == null) {
            throw new IllegalArgumentException("Schedule must not be null.");
        }
        ScheduleValidator.validateSchedule(
                schedule.dayOfWeek,
                schedule.startMinute,
                schedule.endMinute,
                schedule.validFromEpochDay,
                schedule.validToEpochDay
        );
    }
}
