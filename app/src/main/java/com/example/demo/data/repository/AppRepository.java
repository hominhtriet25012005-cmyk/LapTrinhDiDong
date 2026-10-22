package com.example.demo.data.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.example.demo.data.local.AppDatabase;
import com.example.demo.data.local.entity.ClassSectionEntity;
import com.example.demo.data.local.entity.ScheduleEntity;
import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.entity.SubjectEntity;
import com.example.demo.data.local.projection.ScheduleDetails;
import com.example.demo.data.local.projection.SemesterSummary;
import com.example.demo.data.local.relation.ClassSectionWithSchedules;
import com.example.demo.data.local.relation.SemesterWithSubjects;
import com.example.demo.data.local.relation.SubjectWithClassSections;
import com.example.demo.data.validation.ScheduleValidator;

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
    private final ExecutorService writeExecutor;
    private final SchedulePersistence schedulePersistence;

    private AppRepository(Context context) {
        database = AppDatabase.getInstance(context);
        schedulePersistence = new SchedulePersistence(database);
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
        return writeExecutor.submit(() -> database.semesterDao().delete(semester));
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
        return database.subjectDao().search(semesterId, query == null ? "" : query.trim());
    }

    public LiveData<List<SubjectEntity>> searchAllSubjects(String query) {
        return database.subjectDao().searchAll(query == null ? "" : query.trim());
    }

    public LiveData<SubjectWithClassSections> observeSubjectWithClassSections(long subjectId) {
        return database.subjectDao().observeWithClassSections(subjectId);
    }

    public Future<Long> insertSubject(SubjectEntity subject) {
        return writeExecutor.submit(() -> database.subjectDao().insert(subject));
    }

    public Future<Integer> updateSubject(SubjectEntity subject) {
        return writeExecutor.submit(() -> database.subjectDao().update(subject));
    }

    public Future<Integer> deleteSubject(SubjectEntity subject) {
        return writeExecutor.submit(() -> database.subjectDao().delete(subject));
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
        return writeExecutor.submit(() -> schedulePersistence.saveClassSection(classSection));
    }

    public Future<Integer> updateClassSection(ClassSectionEntity classSection) {
        return writeExecutor.submit(() -> {
            schedulePersistence.saveClassSection(classSection);
            return 1;
        });
    }

    public Future<Integer> deleteClassSection(ClassSectionEntity classSection) {
        return writeExecutor.submit(() -> schedulePersistence.deleteClassSection(classSection));
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

    public LiveData<List<ScheduleDetails>> observeAllScheduleDetails() {
        return database.scheduleDao().observeAllDetails();
    }

    public LiveData<List<ScheduleDetails>> searchScheduleDetails(String query, long semesterId,
                                                                  int dayOfWeek, String roomQuery) {
        return database.scheduleDao().searchDetails(
                query == null ? "" : query.trim(),
                semesterId,
                dayOfWeek,
                roomQuery == null ? "" : roomQuery.trim()
        );
    }

    public LiveData<ScheduleDetails> observeScheduleDetails(long scheduleId) {
        return database.scheduleDao().observeDetailsById(scheduleId);
    }

    public Future<Long> insertSchedule(ScheduleEntity schedule) {
        validateSchedule(schedule);
        return writeExecutor.submit(() -> schedulePersistence.saveSchedule(schedule));
    }

    public Future<Integer> updateSchedule(ScheduleEntity schedule) {
        validateSchedule(schedule);
        return writeExecutor.submit(() -> {
            schedulePersistence.saveSchedule(schedule);
            return 1;
        });
    }

    public Future<Long> saveScheduleWithClassSection(ClassSectionEntity classSection,
                                                     ScheduleEntity schedule) {
        validateSchedule(schedule);
        return writeExecutor.submit(() ->
                schedulePersistence.saveScheduleWithClassSection(classSection, schedule));
    }

    public Future<Integer> deleteSchedule(ScheduleEntity schedule) {
        return writeExecutor.submit(() -> database.scheduleDao().delete(schedule));
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
