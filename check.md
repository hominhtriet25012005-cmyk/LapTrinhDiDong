# Check — Lộ trình backend Personal Schedule Management

**Cập nhật:** 2026-11-01  
**Source chính:** D:\Claude\Project  
**Thư mục tham khảo:** D:\Claude\LapTrinhDiDong-main — code của bạn, chỉ dùng để đối chiếu; không phải source chuẩn.  
**Phạm vi:** Java backend, Room/SQLite, quy tắc nghiệp vụ, thông báo, backup/restore, PDF/Excel và kiểm thử. Giao diện sẽ được nối/cập nhật ở giai đoạn riêng sau.

## Mục tiêu

Hoàn thiện backend để dữ liệu lịch học nhất quán giữa trang chủ, lịch, thống kê, tìm kiếm, thông báo và file xuất. Bổ sung lịch ngoại lệ theo ngày (hủy, đổi, học bù), sao lưu/khôi phục thật, cài đặt được lưu và xuất Excel mà không thay thế giao diện hiện tại.

## Nguyên tắc triển khai

- Chỉ sửa source trong D:\Claude\Project. Thư mục hỗ trợ chỉ là tài liệu tham khảo.
- Không chép đè hàng loạt file từ thư mục hỗ trợ. Mỗi lớp được đối chiếu API, schema, kiểm thử và tích hợp riêng.
- Không đổi package name, không chuyển Kotlin/Compose, không viết lại project.
- Không sửa layout, màu sắc, bố cục, điều hướng hoặc luồng màn hình trong đợt backend. Có thể đăng ký Application/receiver hoặc quyền Manifest nếu module backend thực sự cần.
- Giữ nguyên mọi thay đổi chưa commit; không reset, stash, checkout, xóa hoặc ghi đè file ngoài phạm vi.
- Source hiện ở nhánh codex/theme-toggle-flicker và có thay đổi chưa commit. Không đưa các thay đổi đó vào main.
- Không dùng destructive migration. Mọi thay đổi Room phải có migration từ schema đang phát hành và test giữ nguyên dữ liệu.
- Sau mỗi module code phải chạy build và test phù hợp, ghi lệnh và kết quả thực tế. Chỉ đánh dấu hoàn tất khi có bằng chứng chạy được.
- Nếu thiếu JDK, SDK hoặc emulator, ghi chính xác giới hạn và giữ trạng thái chưa xác minh.

## Kết quả rà soát source chính

- Room hiện có 4 entity: SemesterEntity, SubjectEntity, ClassSectionEntity và ScheduleEntity; database đang ở version 1.
- AppRepository dùng Room/LiveData và executor ghi dữ liệu. SchedulePersistence đã lưu lớp/lịch trong transaction và kiểm tra xung đột của lịch lặp.
- Main đã có CRUD dữ liệu học tập, thống kê cơ bản, tìm kiếm cơ bản, thông báo cho lịch lặp và xuất PDF.
- Năm học đang được nhập độc lập với ngày học kỳ trong thay đổi chưa commit ở source chính. Đây là hướng đúng cho HK2 năm học 2025–2026 dù ngày bắt đầu thuộc năm 2026; cần build/test trước khi xem là ổn định.
- BackupActivity ở source chính hiện chỉ cập nhật nhãn thời gian và Toast; chưa ghi/đọc/khôi phục file dữ liệu.
- ExportActivity hiện chưa xuất Excel.
- Cài đặt ngày đầu tuần chưa được lưu cho các bộ tính; một số phép tính đang cố định Thứ Hai. Lựa chọn giao diện cũng cần được lưu/áp dụng khi khởi động mà không làm tái phát lỗi đổi theme.
- Truy vấn tìm kiếm hiện dùng LIKE/COLLATE NOCASE; chưa xử lý tìm kiếm tiếng Việt không dấu thống nhất.
- Room chưa có bảng cho hủy/đổi/học bù một buổi riêng lẻ. Các bộ tính hiện chủ yếu dựa trên lịch lặp hằng tuần.
- Nhắc lịch/bản tin hiện dùng AlarmManager.setAndAllowWhileIdle; bản tin ngày chưa liệt kê toàn bộ buổi học.
- Giai đoạn rà soát này chỉ đọc source; chưa chạy build hoặc test.

## Đối chiếu thư mục tham khảo

Các phần có giá trị để tham khảo: ScheduleExceptionEntity/DAO, ScheduleResolver, AppSettings/WeekSettings, BackupManager/BackupCodec, ScheduleExcelExporter/XlsxWriter và ScheduleReminderPlanner.

Không lấy nguyên trạng vì:

- SemesterFormMapper ở thư mục tham khảo suy năm học từ ngày bắt đầu. Cách này có thể gán sai năm học cho HK2; giữ logic nhập năm học độc lập ở source chính.
- Migration v1→v2 ở thư mục tham khảo thêm bảng lịch ngoại lệ và dựa trên schema v1 trùng với source chính, nhưng chưa thấy test nâng cấp dữ liệu v1→v2.
- Kiểm tra buổi bị trùng của lịch ngoại lệ được gọi từ OccurrenceActivity; lớp persistence chưa tự đảm bảo xung đột. Backend source chính phải kiểm tra nghiệp vụ tại tầng lưu để không phụ thuộc giao diện.
- Có test cho BackupCodec nhưng chưa thấy test đủ cho BackupManager restore/rollback.
- Planner tham khảo vẫn dùng báo thức không chính xác tuyệt đối; cần giữ chính sách quyền và fallback Android trong module thông báo.
- Mã hỗ trợ cần được build/test trong source chính sau khi tích hợp; test có sẵn ở thư mục tham khảo không chứng minh source chính đã hoạt động.

## Lộ trình triển khai

### Giai đoạn 0 — Baseline và bảo vệ working tree

**Trạng thái:** Bảo vệ working tree và kiểm tra diff đã xong; build/test baseline chưa chạy được do Gradle wrapper không lấy được distribution.

- Source chính đang ở nhánh codex/theme-toggle-flicker.
- Baseline ban đầu có 20 file tracked đã sửa và 5 file untracked. Các thay đổi giữ nguyên; không reset, stash, checkout hoặc ghi đè source.
- git diff --check trả exit code 0, không có lỗi whitespace; Git chỉ cảnh báo một số file LF sẽ được chuẩn hóa thành CRLF ở lần ghi tiếp theo.
- SDK được cấu hình trong local.properties; Android platform 37.0 và build-tools 36.0.0 hiện diện.
- JDK 23.0.1 ở C:\Program Files\Java\jdk-23 chạy được. Lệnh java mặc định qua Oracle javapath bị treo; các lần chạy Gradle dùng JAVA_HOME tạm trỏ trực tiếp tới JDK.
- Gradle wrapper yêu cầu 9.6.0. Distribution chưa có trong Gradle home nằm trong project. Để không dựa vào cache ngoài project, đã thử dùng project-local Gradle home; wrapper không tải được distribution vì services.gradle.org trả UnknownHostException.
- Đã thử .\gradlew.bat --offline :app:testDebugUnitTest và .\gradlew.bat --offline :app:assembleDebug trước khi xác định nguyên nhân; cả hai không in log và đã được dừng thủ công. Các task test/build chưa thực sự bắt đầu.
- adb.exe tồn tại nhưng adb devices -l không khởi động được ADB server (could not read ok); không có thiết bị sẵn sàng. connectedDebugAndroidTest chưa chạy.
- Trong giai đoạn này chỉ cập nhật check.md; không sửa source. Chưa xác định được lỗi môi trường ngoài việc wrapper distribution không tải được và ADB server không khởi động.

**Hoàn tất khi:** Gradle 9.6.0 khả dụng trong cache được phép dùng hoặc có thể tải; unit test và assembleDebug chạy xong; instrumentation test chạy nếu emulator/device khả dụng. Trước đó giữ trạng thái baseline chưa đạt.### Giai đoạn 1 — Xác minh năm học

**Trạng thái:** Có thay đổi chưa commit; chưa xác minh build/test

**Thành phần:** SemesterFormMapper, form học kỳ, truy vấn/danh sách và test hiện có.

- Đảm bảo năm học được nhập riêng, không suy ra từ ngày bắt đầu.
- Chấp nhận 2025-2026, 2025–2026 và dấu gạch ngang tương đương; chuẩn hóa và từ chối năm không liên tiếp.
- Kiểm tra thêm/sửa/đọc lại, thứ tự danh sách, lọc và PDF.
- Không thay mapper hiện tại bằng mapper cũ ở thư mục tham khảo.

**Kiểm chứng:** SemesterFormMapperTest, test lưu/đọc Room và assembleDebug.

**Hoàn tất khi:** HK2 bắt đầu trong năm 2026 vẫn lưu đúng năm học 2025–2026; dữ liệu cũ không bị đổi sai.

### Giai đoạn 2 — Củng cố validation và tìm kiếm

**Trạng thái:** Backend đã triển khai; Android compile/assemble được xác nhận trong build Giai đoạn 6. Toàn bộ suite và Room instrumentation còn chờ.

**Thành phần:** AppRepository, SubjectValidator/SubjectPersistence, SchedulePersistence, DAO, SearchFilters và test.

- Chuẩn hóa và kiểm tra môn học ở tầng lưu: học kỳ phải tồn tại, tên/mã bắt buộc, tín chỉ không âm; mã môn được trim và chuyển uppercase.
- Chặn mã môn trùng không phân biệt hoa thường trong cùng học kỳ bằng kiểm tra trong transaction. AppRepository chuyển thao tác thêm/sửa môn qua SubjectPersistence.
- Chuẩn hóa mã lớp học phần và trường văn bản trước khi lưu; DAO so mã lớp sau khi trim/uppercase. Kiểm tra xung đột và lưu lịch vẫn nằm trong transaction.
- Tìm môn theo tên/mã và tìm lịch theo tên/mã môn, mã lớp, phòng; hỗ trợ tìm tiếng Việt có/không dấu.
- Lọc lịch kết hợp học kỳ, thứ, phòng và khoảng ngày. Khoảng ngày chỉ khớp khi có ít nhất một buổi lặp đúng thứ trong khoảng đó và trong thời hạn hiệu lực của lịch.
- Giữ nguyên schema Room, không thêm migration và không sửa layout trong giai đoạn này. API tìm kiếm cũ vẫn hoạt động; AppRepository có thêm overload nhận khoảng ngày để màn hình nối sau.

**Kiểm chứng thực tế:** Biên dịch thủ công các lớp Java thuần và chạy 5 lớp JUnit mục tiêu bằng JDK 23/JUnit 4.13.2: **18 test đạt**. `git diff --check` đạt; Git chỉ báo cảnh báo chuẩn hóa LF/CRLF trên các file đang sửa. Lệnh `:app:testDebugUnitTest` và `:app:assembleDebug` đã thử lại nhưng wrapper dừng trước khi chạy task vì không phân giải được `services.gradle.org` để tải Gradle 9.6.0. Instrumentation test chưa chạy do ADB server không khởi động được.

**Còn cần để hoàn tất:** Chạy toàn bộ Gradle unit suite và instrumentation khi emulator/ADB khả dụng; targeted unit test Giai đoạn 6 và Android compile đã chạy được, nhưng chưa bao phủ toàn bộ truy vấn Room.

### Giai đoạn 3 — Lưu cài đặt và quy tắc tuần

**Trạng thái:** Code và test mục tiêu đã triển khai; Android compile/assemble được xác nhận trong build Giai đoạn 6. Instrumentation và kiểm tra theme trên emulator còn chờ.

**Thành phần:** AppSettings, WeekSettings, WeekCalculator, ScheduleApplication, SettingsFragment, HomeDashboardCalculator, TimetableFragment và ScheduleExportCalculator.

- Lưu lựa chọn sáng/tối và ngày đầu tuần bằng SharedPreferences; mặc định sáng và Thứ Hai. Giá trị ngày tuần ngoài ISO 1–7 bị từ chối khi ghi và dùng mặc định an toàn khi đọc.
- Đăng ký ScheduleApplication trong Manifest để đọc theme đã lưu và gọi AppCompatDelegate trước khi Activity dựng giao diện. SettingsFragment dùng lại các điều khiển sẵn có; không sửa XML/layout.
- Nối lựa chọn ngày đầu tuần vào dashboard, màn hình lịch tuần và phạm vi xuất PDF. Thứ tự lịch tuần bắt đầu từ ngày đã chọn; các API calculator cũ vẫn dùng Thứ Hai để giữ tương thích.
- Thêm test cho tuần từ Thứ Hai/Chủ Nhật, ranh giới tháng, thứ tự lịch dashboard, phạm vi PDF và lưu cài đặt Android.

**Kiểm chứng thực tế:** Biên dịch thủ công các lớp Java thuần và chạy 8 lớp JUnit mục tiêu bằng JDK 23/JUnit 4.13.2: **33 test đạt**. `git diff --check` đạt (còn cảnh báo chuẩn hóa LF/CRLF trên các file có sẵn). `:app:testDebugUnitTest` và `:app:assembleDebug` đã thử lại nhưng Gradle wrapper dừng trước khi chạy task do `UnknownHostException: services.gradle.org` khi tải Gradle 9.6.0. `AppSettingsTest` instrumentation được bổ sung nhưng chưa chạy vì ADB server không khởi động được; theme sau khi khởi động lại chưa được xác nhận trực quan trên emulator.

**Còn cần để hoàn tất:** Chạy toàn bộ Gradle unit suite, AppSettingsTest trên emulator/device và xác nhận theme đã lưu được áp dụng ngay ở lần khởi động Activity tiếp theo.

### Giai đoạn 4 — Lịch ngoại lệ và Room migration

**Trạng thái:** Đã triển khai entity, persistence và migration; Room schema v2 đã được sinh trong build Giai đoạn 6; migration instrumentation còn chờ.

**Mục tiêu:** Hủy, sửa tại chỗ/đổi ngày và học bù cho một buổi cụ thể mà không sửa lịch lặp gốc.

**Thành phần:** ScheduleExceptionEntity/Dao, validator, conflict validator, ScheduleExceptionPersistence, AppRepository và AppDatabase.MIGRATION_1_2. Module này chỉ làm backend; các hộp thoại ngoại lệ có sẵn trong ScheduleDetailActivity hiện vẫn là demo và chưa nối với Room.

- Bảng `schedule_exceptions` lưu scheduleId, ngày gốc, loại, ngày/giờ/phòng thay thế và ghi chú. Một ngoại lệ gắn với đúng một buổi gốc; unique index `(scheduleId, originalDateEpochDay)` không chứa cột nullable. Index ngày thay thế phục vụ truy vấn; foreign key xóa cascade từ lịch.
- Quy tắc: CANCEL loại buổi gốc; MODIFY có thể giữ ngày hoặc dời ngày; MAKEUP thay buổi gốc bằng buổi học bù ở ngày khác. Giờ null kế thừa giờ lịch gốc; phòng null kế thừa, phòng rỗng chủ ý xóa phòng.
- Migration cộng thêm bảng/index từ Room schema v1 lên v2; không sửa/xóa hoặc copy lại các bảng dữ liệu cũ. AppDatabase đăng ký migration tường minh, không bật destructive fallback.
- Persistence xác thực ngày gốc đúng thứ và trong khoảng hiệu lực, type/giờ/ngày thay thế, khóa ngoại tồn tại và không trùng ngoại lệ gốc; lưu/sửa/xóa chạy trong transaction.
- Kiểm tra xung đột với buổi lặp đang hoạt động, buổi đã dời/học bù và ngoại lệ hủy. Khi sửa lịch lặp, từ chối transaction nếu ngày gốc cũ không còn hợp lệ hoặc buổi dời bị trùng. Xóa lịch/lớp/môn/học kỳ dùng chuỗi foreign key cascade.
- Thêm API quan sát/đọc/thêm/sửa/xóa ngoại lệ trong AppRepository để Giai đoạn 5 dùng; không đổi giao diện.

**Kiểm chứng thực tế:** Bộ thuần Java chạy lại cuối lượt gồm 20 test validator/xung đột lịch và ngoại lệ qua JDK 23/JUnit 4.13.2: đạt, bao gồm hai regression test lịch lặp va chạm với ngoại lệ đã dời/học bù. Trước thay đổi regression cuối, bộ kiểm tra thuần Java rộng hơn đã đạt 42 test. Trong build Giai đoạn 6, `:app:assembleDebug` thành công và Room annotation processor sinh `app/schemas/2.json`; test migration/persistence trên emulator chưa chạy. Gradle test mục tiêu của Giai đoạn 6 thành công, nhưng chưa chạy toàn bộ unit suite.

**Còn cần để hoàn tất:** Chạy MigrationTest từ schema v1 có dữ liệu, kiểm tra migration/FK/index và persistence/cascade/rollback trên emulator. Build đã xác nhận Room compiler và schema v2; migration instrumentation vẫn chưa được kiểm chứng.

### Giai đoạn 5 — Bộ phân giải buổi học và tính toán dùng chung

**Trạng thái:** Backend resolver và các luồng ngày/tuần/thống kê/xuất đã triển khai; Android compile/assemble được xác nhận trong build Giai đoạn 6. Layout không đổi.

**Thành phần:** ScheduleSnapshot, ClassOccurrence, ScheduleResolver, AppRepository và tích hợp vào HomeDashboardCalculator, SemesterStatisticsCalculator, SearchFilters, ScheduleExportCalculator, HomeFragment, TimetableFragment, StatsActivity và luồng PDF. Các calculator cũ còn API tương thích nhưng chuyển qua resolver chung.

- Biến lịch lặp cộng ngoại lệ thành danh sách buổi học thực tế theo khoảng ngày.
- Hỗ trợ đúng buổi thường, bị hủy, đổi ngày/giờ/phòng, học bù và thứ tự theo ngày/giờ.
- Dashboard và lịch ngày/tuần/tháng đọc snapshot theo khoảng ngày; thời gian rảnh tính từ occurrence thực tế.
- Thống kê tính tổng phút học thật trong học kỳ; chỉ số tuần là trung bình làm tròn trên các tuần trong snapshot.
- SearchFilters và repository có API tìm kiếm occurrence theo ngày, thứ, phòng, học kỳ và nội dung. SearchActivity hiện vẫn dùng luồng tìm lịch lặp cũ vì màn hình chưa có bộ chọn khoảng ngày; phần giao diện/tích hợp tìm kiếm được để sau.
- PDF xuất từ snapshot cùng resolver; Excel chưa triển khai ở Giai đoạn 5.
- Giữ nguyên các quy tắc học kỳ và ngày hiệu lực.
- Bảo đảm hủy một buổi không ảnh hưởng các tuần khác; đổi ngày không hiện hai buổi; học bù không nhân đôi.

**Kiểm chứng thực tế:** Biên dịch thủ công và chạy 54 test Java thuần bằng JDK 23/JUnit 4.13.2: đạt, gồm 34 test Giai đoạn 5 và 20 test regression lịch/ngoại lệ Giai đoạn 4. Bộ Giai đoạn 5 gồm resolver, hủy/đổi/học bù, khoảng ngày, thứ tự, dashboard/thời gian rảnh, thống kê, tìm kiếm occurrence, PDF scope và một test truyền cùng snapshot qua dashboard/stats/search/export. Build Giai đoạn 6 đã chạy `:app:assembleDebug` thành công, xác nhận compiler Android/Room và wiring Activity/Fragment biên dịch; unit test toàn suite vẫn chưa chạy trong lượt đó.

**Còn cần để hoàn tất:** Chạy toàn bộ Gradle unit suite và rà soát tích hợp tìm kiếm occurrence khi giao diện có chọn khoảng ngày. Chưa đánh dấu giai đoạn hoàn tất.

### Giai đoạn 6 — Thông báo dựa trên buổi học thực tế

**Trạng thái:** Backend đã triển khai; test mục tiêu và debug build đạt. Còn cần xác nhận hành vi thông báo trên emulator/device.

**Thành phần:** NotificationScheduler, ScheduleReminderPlanner, DailySummaryFormatter, NotificationPreferences, receivers và AppRepository. Không cần đổi Manifest ở giai đoạn này vì quyền thông báo và receiver khởi động/đổi giờ đã được khai báo.

- NotificationScheduler đọc lịch lặp và ngoại lệ trong cùng Room transaction rồi dùng ScheduleResolver tạo occurrence thực tế. Planner chọn nhắc tiếp theo cho từng lịch từ toàn bộ thời hạn; buổi hủy bị bỏ qua, buổi đổi/học bù dùng ngày, giờ và phòng hiệu lực.
- Buổi học bù kế thừa `reminderMinutesBefore` của lịch gốc (mặc định dữ liệu ScheduleEntity là 1.440 phút). Cài đặt toàn cục là công tắc chính; cờ nhắc riêng của từng lịch vẫn phải bật.
- Alarm PendingIntent mang ngày occurrence, giờ bắt đầu và ID ngoại lệ. Khi alarm tới, app đọc Room và phân giải lại đúng ngày; nếu buổi đã bị hủy/xóa/đổi thì không hiện thông báo cũ, sau đó lập lại alarm theo dữ liệu mới nhất.
- AppRepository đồng bộ notification sau thêm/sửa/xóa lịch và ngoại lệ, cập nhật lớp/môn ảnh hưởng nội dung thông báo, và xóa lịch cha (lớp/môn/học kỳ). MainActivity đồng bộ lại khi mở app; receiver hiện có phục hồi sau reboot và đồng bộ sau đổi ngày/giờ/múi giờ.
- Bản tin theo giờ cài đặt (mặc định 06:00) lấy occurrence thực tế trong ngày và liệt kê tất cả môn, giờ bắt đầu/kết thúc và phòng; buổi đổi/học bù được đưa vào đúng ngày mới.
- Quyền POST_NOTIFICATIONS tiếp tục được kiểm tra/request bằng NotificationsActivity. Kênh thông báo giữ âm thanh mặc định của Android và tôn trọng tùy chọn người dùng.
- Dùng `AlarmManager.setAndAllowWhileIdle` dạng **không chính xác**, không xin quyền exact alarm đặc biệt. Android có thể trì hoãn lúc tiết kiệm pin/Doze; mô tả cài đặt đã nêu rõ giới hạn này. Không cam kết alarm chạy đúng tuyệt đối 06:00/24 giờ trước.
- Backup/restore Giai đoạn 7 gọi `NotificationScheduler.syncAll()` sau transaction restore thành công để cập nhật alarm theo lịch vừa khôi phục.

**Kiểm chứng thực tế:** Biên dịch các lớp planner/resolver/formatter và chạy 5 test mục tiêu bằng JDK 25, `--release 11`, JUnit 4.13.2: **5/5 đạt** (hủy, đổi ngày/giờ/phòng, học bù, cờ bật/tắt và nhắc đã quá hạn, bản tin gồm nhiều buổi). Gradle `:app:testDebugUnitTest --tests com.example.demo.notifications.ScheduleReminderPlannerTest` và `:app:assembleDebug` chạy **BUILD SUCCESSFUL** với Gradle 9.6.0/JDK 25. `strings.xml` parse XML đạt; `git diff --check` không báo lỗi whitespace. Chưa chạy instrumentation/emulator để xác nhận quyền runtime, âm thanh, reboot và chỉnh giờ/múi giờ.

**Còn cần để xác nhận runtime:** Cài APK trên emulator/device và kiểm tra quyền POST_NOTIFICATIONS, nội dung/âm thanh notification, sửa/xóa lịch trước giờ nhắc, reboot, đổi ngày/giờ/múi giờ và độ trễ alarm không chính xác. Backend restore đã gọi đồng bộ alarm; cần kiểm thử hành vi này sau khi UI SAF được nối.

### Giai đoạn 7 — Backup/restore dữ liệu thật

**Trạng thái:** Backend đã triển khai; chưa chạy được Room instrumentation vì ADB daemon không khởi động. Giao diện backup hiện tại vẫn là placeholder và chưa nối SAF.

**Thành phần:** BackupData, BackupCodec, BackupManager, Room và test. Chỉ chuẩn bị API để giao diện nối Storage Access Framework sau.

- Tạo định dạng JSON có app identifier, format version và thời điểm tạo.
- Sao lưu snapshot nhất quán gồm học kỳ, môn, lớp, lịch và bảng ngoại lệ.
- Không đưa cài đặt ứng dụng vào backup; restore giữ nguyên tùy chọn trên thiết bị và chỉ thay dữ liệu học tập.
- Giới hạn dung lượng và xác thực định dạng, phiên bản, ID, giá trị, quan hệ FK, ngày/giờ trước khi restore.
- Restore toàn bộ trong một transaction; lỗi giữa chừng phải rollback để giữ nguyên dữ liệu hiện tại.
- Thực hiện I/O ngoài main thread; API nhận URI để màn hình sau nối SAF.
- Dùng format version 1 có đủ năm bảng; file sai phiên bản hoặc thiếu collection bị từ chối. Khi có định dạng cũ thực tế, cần thêm nhánh chuyển đổi rõ ràng và tăng version, không tự suy đoán dữ liệu.
- Sau transaction restore thành công, gọi `NotificationScheduler.syncAll()` để lập lại nhắc lịch theo dữ liệu vừa khôi phục.

**Kiểm chứng thực tế ngày 01/11/2026:** Toàn bộ `:app:testDebugUnitTest` đạt **93/93**; `:app:assembleDebug` và `:app:assembleDebugAndroidTest` đều `BUILD SUCCESSFUL`. AndroidTest APK biên dịch, gồm test restore năm bảng/giữ ID và rollback khi insert lỗi. `adb devices` thất bại với `could not read ok from ADB Server`, vì vậy instrumentation chưa được thực thi. SAF và đồng bộ notification sau restore chưa smoke-test trên thiết bị; màn hình vẫn chưa nối API.

**Hoàn tất khi:** Nối UI qua SAF, kiểm tra đọc/ghi tệp thật và chạy Room instrumentation chứng minh restore thành công/rollback; xác nhận notification được đồng bộ lại sau restore trên thiết bị.

### Giai đoạn 8 — Xuất Excel

**Trạng thái:** Backend exporter đã triển khai; giao diện giữ nguyên và chưa nối thao tác lưu Excel.

**Thành phần:** `ScheduleExcelExporter` tự tạo XLSX bằng `ZipOutputStream` và OpenXML inline strings; không thêm thư viện ngoài. Dùng `ScheduleExportCalculator`/`ScheduleResolver` làm nguồn occurrence giống PDF.

- Có API xuất lịch theo ngày, tuần, tháng và học kỳ; `CURRENT_DAY` được bổ sung vào calculator mà không đổi lựa chọn phạm vi trên UI hiện tại.
- Workbook lịch gồm ngày, thứ, giờ, mã/tên môn, lớp, phòng, giảng viên, loại buổi và ghi chú; sắp theo ngày/giờ. Buổi hủy không xuất hiện; buổi đổi/học bù dùng ngày, giờ, phòng hiệu lực.
- Workbook môn học có hai sheet: danh sách môn và lớp học phần, lọc theo học kỳ được truyền vào exporter.
- XLSX dùng inline strings để dữ liệu người dùng luôn là text; escape XML, thay ký tự XML không hợp lệ, giới hạn sheet name 31 ký tự, 10.000 dòng dữ liệu mỗi sheet và 32.767 ký tự mỗi ô.
- API nhận `OutputStream`, không đóng stream của caller; màn hình sau có thể kết hợp với SAF `CreateDocument`. Không sửa layout/activity ở giai đoạn backend.

**Kiểm chứng thực tế ngày 01/11/2026:** `:app:testDebugUnitTest` đạt **100/100**; `:app:assembleDebug` và `:app:assembleDebugAndroidTest` đều `BUILD SUCCESSFUL`. Năm test exporter giải nén ZIP, xác nhận package entry, parse XML, Unicode/XML escaping, thứ tự ngày/giờ, học bù/buổi hủy, lọc môn/lớp và giới hạn; hai test calculator xác minh phạm vi theo ngày và biên học kỳ. Không tìm thấy Excel/LibreOffice trên máy nên chưa mở file bằng ứng dụng bảng tính; `ExportActivity` vẫn báo Excel chưa hỗ trợ vì chưa nối API.

**Hoàn tất giai đoạn backend khi:** API tạo XLSX hợp lệ với dữ liệu thật và phạm vi đồng nhất với PDF. Còn bước tích hợp UI/SAF và xác nhận mở file trên ứng dụng bảng tính ở giai đoạn tích hợp.

### Giai đoạn 9 — Tích hợp và hồi quy backend

**Trạng thái:** Unit regression và build hoàn tất; Room instrumentation/runtime verification còn chờ ADB. Chưa sửa giao diện.

- Thêm regression test dùng cùng dữ liệu học kỳ/môn/lớp/lịch/ngoại lệ và một `ScheduleSnapshot` cho dashboard, thống kê, `SearchFilters` occurrence, PDF range/export calculator, Excel, bản tin notification và reminder planner. Test encode/decode backup rồi phân giải lại để so ngày/giờ/phòng/loại occurrence.
- Schema Room v1 và v2 đều có trong `app/schemas`. `AppDatabaseTest` mở database mới (Room tự validate schema); `ScheduleExceptionMigrationTest` tạo database v1 có dữ liệu, migrate qua `MIGRATION_1_2`, xác nhận version 2, giữ nguyên hàng cũ và thử unique/FK/cascade. Test đã biên dịch nhưng chưa thực thi trên Android runtime.
- Rà Manifest: chỉ khai báo `POST_NOTIFICATIONS` và `RECEIVE_BOOT_COMPLETED`; receiver/Activity nội bộ để `exported=false`, launcher Activity là `true`. Không xin exact-alarm permission vì alarm hiện dùng chế độ không chính xác; không xin quyền lưu trữ rộng vì file đi qua SAF.
- API backend cho occurrence search đã có trong `AppRepository.searchScheduleOccurrences`; test tích hợp backend dùng `SearchFilters.filterOccurrences`. `SearchActivity` hiện vẫn gọi API tìm lịch lặp `searchScheduleDetails`, do UI chưa có phạm vi ngày/occurrence picker. Giữ nguyên luồng UI theo phạm vi giai đoạn này và ghi rõ đây là điểm nối tiếp.
- Contract để UI nối sau: search occurrence nhận khoảng ngày, semester/day/room/text; Excel nhận `OutputStream` + scope/rows; backup nhận content `Uri`; notification tự đồng bộ sau ghi/restore.

**Kiểm chứng thực tế ngày 01/11/2026:** Toàn bộ `:app:testDebugUnitTest` đạt **100/100**; `:app:assembleDebug` và `:app:assembleDebugAndroidTest` đều `BUILD SUCCESSFUL`; test tích hợp occurrence đạt. `git diff --check` đạt. `adb devices` lỗi `could not read ok from ADB Server`, nên instrumentation migration/database/backup và smoke test notification chưa chạy.

**Còn cần xác nhận:** Chạy `:app:connectedDebugAndroidTest` khi ADB/emulator hoạt động để thực thi schema mới, migration v1→v2, Room CRUD, backup restore/rollback; kiểm tra quyền/notification runtime. UI search vẫn chưa dùng occurrence search API.

**Hoàn tất khi:** Full unit/build pass, instrumentation khả dụng pass, database cũ giữ nguyên sau migration và các consumer backend cùng nhận đúng occurrence. Tích hợp UI search/SAF và smoke test app là phần riêng vì giai đoạn này không sửa giao diện.

## Lệnh kiểm chứng chuẩn

Sau mỗi module đã sửa:

1. Unit test: .\gradlew.bat :app:testDebugUnitTest
2. Instrumentation test: .\gradlew.bat :app:connectedDebugAndroidTest
3. Debug build: .\gradlew.bat :app:assembleDebug

Nếu instrumentation test không có emulator/device, ghi rõ “chưa chạy” thay vì đánh dấu đạt. Có thể chạy test mục tiêu trước rồi chạy toàn bộ suite ở Giai đoạn 9.

## Ngoài phạm vi backend đợt này

- Không sửa XML layout, màu sắc, bố cục, điều hướng hoặc thay đổi thiết kế Figma.
- Không chép Activity/Fragment của thư mục tham khảo vào source chính.
- Không đẩy code lên GitHub hoặc merge nhánh khi chưa được yêu cầu.
- Không xóa dữ liệu người dùng, không dùng destructive migration.

## Nhật ký triển khai

| Giai đoạn | Trạng thái | Build/test | Ghi chú |
|---|---|---|---|
| 0. Baseline | Một phần hoàn tất | git diff --check đạt; build/test chưa chạy vì Gradle distribution không tải được; instrumentation chưa chạy | Working tree được giữ nguyên; cần Gradle 9.6.0 và ADB khả dụng. |
| 1. Năm học | Cần xác minh | Chưa chạy ở lượt rà soát này | Code/test đã có thay đổi trong working tree. |
| 2. Validation/tìm kiếm | Backend đã triển khai; chờ build/tích hợp Room | 18 unit test mục tiêu chạy thủ công đạt; Gradle test/assemble dừng trước task do DNS; instrumentation chưa chạy | Không đổi schema/layout; API tìm kiếm cũ được giữ tương thích. |
| 3. Cài đặt/tuần | Code đã triển khai; chờ build/runtime verification | 33 unit test Java thuần chạy thủ công đạt; Gradle test/assemble dừng trước task do DNS; instrumentation chưa chạy | Theme nạp từ Application; SettingsFragment dùng control sẵn có; chưa sửa layout. |
| 4. Lịch ngoại lệ/migration | Code đã triển khai; chờ build/migration verification | 42 test Java thuần chạy thủ công đạt; Gradle test/assemble dừng trước task do DNS; instrumentation chưa chạy | Migration v1→v2 và test có trong source; Room schema v2 chưa được annotation processor sinh. |
| 5. Resolver/tính toán | Backend đã triển khai; chờ build và hoàn tất search UI wiring | 34 test Java thuần mục tiêu đạt; Gradle dừng trước task do DNS | Resolver dùng chung; XML không đổi; SearchActivity còn dùng API lịch lặp. |
| 6. Thông báo | Backend đã triển khai; chờ runtime verification | 5 unit test mục tiêu đạt; Gradle test mục tiêu và assembleDebug thành công; instrumentation chưa chạy | Dùng ScheduleResolver; alarm không chính xác để tránh yêu cầu special exact-alarm access; Giai đoạn 7 gọi đồng bộ lại alarm sau restore. |
| 7. Backup/restore | Backend đã triển khai; UI/SAF chưa nối | 93/93 unit test pass; debug và AndroidTest APK build thành công; instrumentation chưa chạy do ADB daemon lỗi | JSON version 1, snapshot transaction, kiểm tra giới hạn/FK/ID/giá trị, restore rollback nguyên tử, đồng bộ notification sau restore. |
| 8. Excel | Backend đã triển khai; UI/SAF chưa nối | 100/100 unit test pass; debug và AndroidTest APK build thành công; XML/ZIP đã kiểm tra, chưa mở trên app bảng tính | Lịch theo ngày/tuần/tháng/học kỳ; workbook môn và lớp; cùng ScheduleResolver với PDF. |
| 9. Hồi quy | Backend unit/build đã đạt; instrumentation/runtime còn chờ ADB | 100/100 unit test pass; debug + AndroidTest APK build thành công; `git diff --check` đạt; `adb devices` thất bại | Shared-occurrence regression qua dashboard/stats/search API/PDF/Excel/backup/notification. SearchActivity còn dùng recurring API; migration v1→v2 và Room tests mới compile. |
