package com.example.demo.data.local;

import android.content.Context;
import android.database.sqlite.SQLiteConstraintException;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.demo.data.local.entity.ClassSectionEntity;
import com.example.demo.data.local.entity.ScheduleEntity;
import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.entity.SubjectEntity;
import com.example.demo.data.local.projection.ScheduleDetails;
import com.example.demo.data.local.projection.SemesterSummary;
import com.example.demo.data.repository.SchedulePersistence;
import com.example.demo.data.validation.ScheduleConflictException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class AppDatabaseTest {

    private AppDatabase database;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
    }

    @After
    public void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    public void onlyOneSemesterCanBeCurrentThroughTransactionalDaoOperation() {
        SemesterEntity first = createSemester("Fall", "2026-2027", true);
        SemesterEntity second = createSemester("Spring", "2026-2027", false);
        long firstId = database.semesterDao().insert(first);
        long secondId = database.semesterDao().insert(second);

        assertTrue(database.semesterDao().setCurrentSemester(secondId));
        assertEquals(secondId, database.semesterDao().getCurrentSemester().id);
        assertEquals(1, database.semesterDao().countCurrentSemesters());

        assertFalse(database.semesterDao().setCurrentSemester(99_999L));
        assertEquals(secondId, database.semesterDao().getCurrentSemester().id);
        assertEquals(1, database.semesterDao().countCurrentSemesters());
        assertTrue(firstId > 0);
    }

    @Test
    public void semesterCanBeLoadedByIdForDeferredPdfExport() {
        SemesterEntity expected = createSemester("Fall", "2026-2027", true);
        expected.startDateEpochDay = 20_000L;
        long semesterId = database.semesterDao().insert(expected);

        SemesterEntity actual = database.semesterDao().getById(semesterId);

        assertNotNull(actual);
        assertEquals(semesterId, actual.id);
        assertEquals("Fall", actual.name);
        assertEquals(expected.startDateEpochDay, actual.startDateEpochDay);
        assertEquals(null, database.semesterDao().getById(99_999L));
    }

    @Test
    public void insertingAnotherCurrentSemesterKeepsCurrentFlagUnique() {
        long firstId = database.semesterDao().insert(createSemester("Fall", "2026-2027", true));
        long secondId = database.semesterDao().insert(createSemester("Spring", "2026-2027", true));

        assertEquals(secondId, database.semesterDao().getCurrentSemester().id);
        assertEquals(1, database.semesterDao().countCurrentSemesters());
        assertTrue(firstId > 0);
    }

    @Test
    public void updatingSemesterToCurrentClearsThePreviousCurrentSemester() {
        database.semesterDao().insert(createSemester("Fall", "2026-2027", true));
        SemesterEntity next = createSemester("Spring", "2026-2027", false);
        next.id = database.semesterDao().insert(next);

        next.isCurrent = true;
        assertEquals(1, database.semesterDao().update(next));

        assertEquals(next.id, database.semesterDao().getCurrentSemester().id);
        assertEquals(1, database.semesterDao().countCurrentSemesters());
    }

    @Test
    public void semesterSummaryUsesActualLinkedSubjectCount() {
        SemesterEntity fall = createSemester("Fall", "2026-2027", true);
        fall.startDateEpochDay = 20_000L;
        long firstId = database.semesterDao().insert(fall);
        SemesterEntity spring = createSemester("Spring", "2026-2027", false);
        spring.startDateEpochDay = 19_000L;
        database.semesterDao().insert(spring);
        database.subjectDao().insert(createSubject(firstId, "CS101", "Algorithms"));

        List<SemesterSummary> summaries = database.semesterDao().getSummaries();

        assertEquals(2, summaries.size());
        assertEquals("Fall", summaries.get(0).semester.name);
        assertEquals(1, summaries.get(0).subjectCount);
        assertEquals("Spring", summaries.get(1).semester.name);
        assertEquals(0, summaries.get(1).subjectCount);
    }

    @Test
    public void subjectCodeIsUniqueWithinSemesterButMayRepeatAcrossSemesters() {
        SemesterEntity first = createSemester("Fall", "2026-2027", true);
        SemesterEntity second = createSemester("Spring", "2026-2027", false);
        long firstId = database.semesterDao().insert(first);
        long secondId = database.semesterDao().insert(second);

        database.subjectDao().insert(createSubject(firstId, "CS101", "Algorithms"));
        assertThrows(SQLiteConstraintException.class,
                () -> database.subjectDao().insert(createSubject(firstId, "CS101", "Duplicate")));
        assertTrue(database.subjectDao().insert(createSubject(secondId, "CS101", "Algorithms II")) > 0);
    }

    @Test
    public void subjectCrudRoundTripPersistsUpdatesAndDeletes() {
        long semesterId = database.semesterDao().insert(createSemester("Fall", "2026-2027", true));
        SubjectEntity subject = createSubject(semesterId, "CS101", "Algorithms");
        subject.id = database.subjectDao().insert(subject);

        subject.name = "Advanced Algorithms";
        subject.credits = 4;
        assertEquals(1, database.subjectDao().update(subject));
        assertEquals("Advanced Algorithms", database.subjectDao().getAll().get(0).name);
        assertEquals(4, database.subjectDao().getAll().get(0).credits);

        assertEquals(1, database.subjectDao().delete(subject));
        assertTrue(database.subjectDao().getAll().isEmpty());
    }

    @Test
    public void subjectSearchCanFilterAllSemestersOrOneSemester() throws Exception {
        long currentSemesterId = database.semesterDao().insert(createSemester("Fall", "2026-2027", true));
        long previousSemesterId = database.semesterDao().insert(createSemester("Spring", "2025-2026", false));
        database.subjectDao().insert(createSubject(currentSemesterId, "CS101", "Algorithms"));
        database.subjectDao().insert(createSubject(currentSemesterId, "CS102", "Networks"));
        database.subjectDao().insert(createSubject(previousSemesterId, "MATH101", "Algebra"));

        List<SubjectEntity> allSemesterMatches = awaitValue(database.subjectDao().searchAll("cs"));
        List<SubjectEntity> currentSemesterMatches = awaitValue(
                database.subjectDao().search(currentSemesterId, "algorithm"));

        assertEquals(2, allSemesterMatches.size());
        assertEquals(1, currentSemesterMatches.size());
        assertEquals("CS101", currentSemesterMatches.get(0).code);
    }

    @Test
    public void deletingSemesterCascadesThroughSubjectsClassesAndSchedules() {
        SemesterEntity semester = createSemester("Fall", "2026-2027", true);
        long semesterId = database.semesterDao().insert(semester);
        long subjectId = database.subjectDao().insert(createSubject(semesterId, "CS101", "Algorithms"));

        ClassSectionEntity section = new ClassSectionEntity(subjectId, "CS101-01", "Dr. Nguyen", "Lecture");
        long sectionId = database.classSectionDao().insert(section);
        database.scheduleDao().insert(new ScheduleEntity(
                sectionId, 1, 450, 570, "Room A101", 20_000L, 20_100L, "Bring laptop"));

        assertEquals(1, database.scheduleDao().getAll().size());
        database.semesterDao().delete(semester);

        assertTrue(database.subjectDao().getAll().isEmpty());
        assertTrue(database.classSectionDao().getAll().isEmpty());
        assertTrue(database.scheduleDao().getAll().isEmpty());
    }

    @Test
    public void scheduleDetailsProjectionJoinsScheduleClassSubjectAndSemester() {
        long semesterId = database.semesterDao().insert(createSemester("Fall", "2026-2027", true));
        long subjectId = database.subjectDao().insert(createSubject(semesterId, "CS101", "Algorithms"));
        long sectionId = database.classSectionDao().insert(
                new ClassSectionEntity(subjectId, "CS101-01", "Dr. Nguyen", "Lecture"));
        database.scheduleDao().insert(new ScheduleEntity(
                sectionId, 1, 450, 570, "Room A101", 20_000L, 20_100L, "Bring laptop"));

        List<ScheduleDetails> details = database.scheduleDao().getAllDetails();

        assertEquals(1, details.size());
        ScheduleDetails detail = details.get(0);
        assertNotNull(detail);
        assertEquals("CS101", detail.subjectCode);
        assertEquals("Algorithms", detail.subjectName);
        assertEquals("CS101-01", detail.classCode);
        assertEquals("Fall", detail.semesterName);
        assertEquals(450, detail.startMinute);
        assertEquals("Room A101", detail.room);
    }

    @Test
    public void observeAllScheduleDetailsEmitsRealJoinedRowsForStatistics() throws Exception {
        long semesterId = database.semesterDao().insert(createSemester("Fall", "2026-2027", true));
        insertSearchSchedule(semesterId, "CS101", "Algorithms", "CS101-A", 1, "Room A101");

        List<ScheduleDetails> details = awaitValue(database.scheduleDao().observeAllDetails());

        assertEquals(1, details.size());
        assertEquals(semesterId, details.get(0).semesterId);
        assertEquals("CS101", details.get(0).subjectCode);
        assertEquals("CS101-A", details.get(0).classCode);
    }

    @Test
    public void notificationPreferenceUpdatesExistingScheduleRows() {
        long semesterId = database.semesterDao().insert(createSemester("Fall", "2026-2027", true));
        insertSearchSchedule(semesterId, "CS101", "Algorithms", "CS101-A", 1, "Room A101");
        insertSearchSchedule(semesterId, "CS202", "Networks", "CS202-B", 2, "Room B202");

        assertEquals(2, database.scheduleDao().setNotificationEnabledForAll(true));
        for (ScheduleEntity schedule : database.scheduleDao().getAll()) {
            assertTrue(schedule.notificationEnabled);
        }

        assertEquals(2, database.scheduleDao().setNotificationEnabledForAll(false));
        for (ScheduleEntity schedule : database.scheduleDao().getAll()) {
            assertFalse(schedule.notificationEnabled);
        }
    }

    @Test
    public void scheduleSearchMatchesSubjectCodeNameClassAndRoom() throws Exception {
        long semesterId = database.semesterDao().insert(createSemester("Fall", "2026-2027", true));
        insertSearchSchedule(semesterId, "CS101", "Algorithms", "CS101-A", 1, "Room A101");
        insertSearchSchedule(semesterId, "CS202", "Networks", "CS202-B", 2, "Room B202");

        assertEquals(1, awaitValue(database.scheduleDao().searchDetails("cs101", 0L, 0, "")).size());
        assertEquals(1, awaitValue(database.scheduleDao().searchDetails("algorithms", 0L, 0, "")).size());
        assertEquals(1, awaitValue(database.scheduleDao().searchDetails("cs202-b", 0L, 0, "")).size());
        assertEquals(1, awaitValue(database.scheduleDao().searchDetails("b202", 0L, 0, "")).size());
    }

    @Test
    public void scheduleSearchCombinesSemesterWeekdayAndRoomFilters() throws Exception {
        long currentSemesterId = database.semesterDao().insert(createSemester("Fall", "2026-2027", true));
        long previousSemesterId = database.semesterDao().insert(createSemester("Spring", "2025-2026", false));
        insertSearchSchedule(currentSemesterId, "CS101", "Algorithms", "CS101-A", 1, "Room A101");
        insertSearchSchedule(currentSemesterId, "CS202", "Networks", "CS202-B", 2, "Room A101");
        insertSearchSchedule(previousSemesterId, "CS303", "Databases", "CS303-C", 1, "Room A101");

        List<ScheduleDetails> matches = awaitValue(
                database.scheduleDao().searchDetails("", currentSemesterId, 1, "A101"));

        assertEquals(1, matches.size());
        assertEquals("CS101", matches.get(0).subjectCode);
        assertEquals(currentSemesterId, matches.get(0).semesterId);
        assertEquals(1, matches.get(0).dayOfWeek);
    }

    private void insertSearchSchedule(long semesterId, String subjectCode, String subjectName,
                                      String classCode, int dayOfWeek, String room) {
        long subjectId = database.subjectDao().insert(createSubject(semesterId, subjectCode, subjectName));
        long classSectionId = database.classSectionDao().insert(
                new ClassSectionEntity(subjectId, classCode, "Dr. Nguyen", ""));
        database.scheduleDao().insert(new ScheduleEntity(
                classSectionId, dayOfWeek, 450, 570, room, 20_000L, 20_100L, ""));
    }

    @Test
    public void schedulePersistenceReusesClassAndRejectsConflictsAtomically() {
        long semesterId = database.semesterDao().insert(createSemester("Fall", "2026-2027", true));
        long subjectId = database.subjectDao().insert(createSubject(semesterId, "CS101", "Algorithms"));
        SchedulePersistence persistence = new SchedulePersistence(database);
        ClassSectionEntity firstClass = new ClassSectionEntity(subjectId, "CS101-01", "Dr. A", "");
        ScheduleEntity firstSchedule = new ScheduleEntity(0L, 1, 540, 600, "A101",
                20_000L, 20_100L, "");

        long firstScheduleId = persistence.saveScheduleWithClassSection(firstClass, firstSchedule);
        ClassSectionEntity duplicateClass = new ClassSectionEntity(subjectId, " cs101-01 ", "Dr. B", "");
        ScheduleEntity overlappingSchedule = new ScheduleEntity(0L, 1, 570, 630, "A102",
                20_000L, 20_100L, "");

        assertTrue(firstScheduleId > 0);
        assertThrows(ScheduleConflictException.class,
                () -> persistence.saveScheduleWithClassSection(duplicateClass, overlappingSchedule));
        assertEquals(1, database.classSectionDao().getAll().size());
        assertEquals(1, database.scheduleDao().getAll().size());
    }

    @Test
    public void classSectionCanBeCreatedUpdatedAndDeleted() {
        long semesterId = database.semesterDao().insert(createSemester("Fall", "2026-2027", true));
        long subjectId = database.subjectDao().insert(createSubject(semesterId, "CS101", "Algorithms"));
        SchedulePersistence persistence = new SchedulePersistence(database);
        ClassSectionEntity classSection = new ClassSectionEntity(subjectId, "CS101-01", "Dr. A", "Lecture");

        classSection.id = persistence.saveClassSection(classSection);
        classSection.lecturer = "Dr. B";
        assertEquals(classSection.id, persistence.saveClassSection(classSection));
        assertEquals("Dr. B", database.classSectionDao().getById(classSection.id).lecturer);
        assertEquals(1, persistence.deleteClassSection(classSection));
        assertTrue(database.classSectionDao().getAll().isEmpty());
    }

    private static SemesterEntity createSemester(String name, String year, boolean current) {
        return new SemesterEntity(name, year, null, null, current, "");
    }

    private static SubjectEntity createSubject(long semesterId, String code, String name) {
        return new SubjectEntity(semesterId, code, name, 3, "Dr. Nguyen", "blue", "");
    }

    private static <T> T awaitValue(LiveData<T> liveData) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> value = new AtomicReference<>();
        Observer<T> observer = nextValue -> {
            value.set(nextValue);
            latch.countDown();
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> liveData.observeForever(observer));
        boolean delivered = latch.await(3, TimeUnit.SECONDS);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> liveData.removeObserver(observer));
        assertTrue("Room LiveData did not emit a value in time", delivered);
        return value.get();
    }
}
