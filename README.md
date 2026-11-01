# UniSchedule — Quản lý thời khóa biểu cá nhân

Ứng dụng Android quản lý thời khóa biểu của sinh viên, chạy hoàn toàn offline. Java 11 + XML Views + Room (SQLite). Không dùng Kotlin, Compose, Firebase, backend hay đăng nhập.

| Hạng mục | Giá trị |
|---|---|
| Package / applicationId | `com.example.demo` |
| SDK | minSdk 26, compileSdk 37, targetSdk 37 |
| Cơ sở dữ liệu | Room 2.8.5, `personal_schedule.db`, schema version 2 |
| Module | `:app` |

## Tính năng

- **Học kỳ, môn học, lớp học phần, lịch lặp hằng tuần**: CRUD đầy đủ, kiểm tra giờ/ngày hợp lệ, chặn trùng giờ cùng thứ trong khoảng ngày áp dụng.
- **Ngoại lệ lịch** (không đổi lịch lặp): hủy một buổi, đổi ngày/giờ/phòng một buổi, thêm buổi học bù, ghi lý do. Cảnh báo trùng giờ và cho phép xác nhận lưu. Có thể khôi phục buổi về lịch gốc.
- **Xem lịch**: Trang chủ (hôm nay, tuần, buổi đang/sắp diễn ra, khoảng trống giữa các buổi) và Thời khóa biểu theo ngày/tuần/tháng. Mọi màn hình dùng chung một bộ giải lịch nên số liệu luôn thống nhất.
- **Tìm kiếm**: theo từ khóa (không phân biệt hoa thường và dấu), học kỳ, thứ, phòng, **một ngày cụ thể hoặc khoảng ngày** (hiện cả buổi đã hủy/đổi/học bù).
- **Thống kê**: số môn, buổi/tuần, giờ/tuần, giờ theo thứ, số buổi hủy/đổi/học bù, biểu đồ theo thứ và khối lượng theo học kỳ; lọc theo toàn học kỳ, tuần, tháng hoặc khoảng ngày tùy chọn.
- **Thông báo**: nhắc trước 24 giờ cho từng buổi (kể cả học bù và buổi đã đổi giờ), tóm tắt hằng ngày (mặc định 06:00), xin quyền `POST_NOTIFICATIONS` (Android 13+), khôi phục sau khi khởi động lại máy, bỏ qua buổi đã hủy.
- **Xuất dữ liệu** (Storage Access Framework, không cần quyền bộ nhớ): PDF thời khóa biểu, Excel `.xlsx` thời khóa biểu (có trạng thái từng buổi) và Excel danh sách môn học. Phạm vi: tuần hiện tại, tháng hiện tại, toàn học kỳ hoặc khoảng ngày tùy chọn.
- **Sao lưu / khôi phục** JSON qua SAF: kiểm tra định dạng và ràng buộc trước khi ghi, khôi phục trong một transaction (lỗi thì giữ nguyên dữ liệu cũ).
- **Cài đặt**: giao diện sáng/tối/theo hệ thống và ngày đầu tuần (Thứ Hai / Chủ nhật / Thứ Bảy), được lưu bền vững.

## Cấu trúc mã nguồn (`app/src/main/java/com/example/demo`)

| Thư mục | Nội dung |
|---|---|
| `data/local` | Entity, DAO, projection, `AppDatabase` |
| `data/repository` | `AppRepository` (mọi truy cập DB chạy ngoài main thread) |
| `data/schedule` | `ScheduleResolver` (lịch lặp + ngoại lệ → các buổi học thật), `SessionSearch`, `SessionStatistics` |
| `data/validation` | Kiểm tra form, xung đột lịch, dashboard Trang chủ |
| `data/export` | `XlsxWriter` (tự viết OOXML, không thư viện), exporter Excel, ghi PDF |
| `data/backup` | `BackupCodec` (JSON), `BackupManager` |
| `data/settings` | `AppSettings`, `WeekSettings` |
| `notifications` | `NotificationScheduler`, `ScheduleReminderPlanner`, receiver |
| `ui/*` | Activity/Fragment theo từng màn hình |

## Mô hình dữ liệu

```text
Semester 1─N Subject 1─N ClassSection 1─N Schedule 1─N ScheduleException
                                     └────────────N ScheduleException (học bù, scheduleId = null)
```

- Ngày lưu bằng `epochDay`, giờ lưu bằng số phút từ 00:00, thứ theo ISO 1–7.
- `schedule_exceptions`: loại `CANCEL` / `MODIFY` / `MAKEUP`; duy nhất theo (`scheduleId`, `originalEpochDay`).
- Không dùng destructive migration; schema export tại `app/schemas/`.

## Build và kiểm thử

```powershell
.\gradlew.bat clean
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat assembleDebugAndroidTest   # chỉ biên dịch test instrumentation
.\gradlew.bat connectedDebugAndroidTest  # cần emulator/thiết bị đang kết nối
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`.

Unit test bao phủ logic thuần Java: bộ giải lịch và ngoại lệ, xung đột, kế hoạch nhắc lịch, mã hóa/kiểm tra sao lưu, ghi `.xlsx`, tìm kiếm theo ngày, thống kê, xuất theo khoảng ngày, ngày đầu tuần.

## Giới hạn đã biết

- Test instrumentation Room chỉ mới biên dịch; cần chạy trên emulator/thiết bị.
- Nhắc lịch dùng `setAndAllowWhileIdle` (không cần quyền báo thức chính xác) nên Android có thể trễ vài phút khi tiết kiệm pin/Doze.
- Nhãn cột biểu đồ thống kê cố định T2…CN, không đổi theo ngày đầu tuần.
- Khôi phục sao lưu thay thế toàn bộ dữ liệu hiện tại.

## Kiểm thử thủ công (smoke test)

1. Tạo học kỳ → môn → lớp → lịch; thử tạo lịch trùng giờ để thấy bị chặn.
2. Hủy một buổi, đổi giờ một buổi, thêm một buổi học bù; xem lại ở Trang chủ, Thời khóa biểu, Tìm kiếm theo ngày, Thống kê.
3. Bật nhắc lịch, cấp quyền thông báo; kiểm tra thông báo tóm tắt và nhắc 24 giờ; khởi động lại máy rồi kiểm tra lại.
4. Xuất PDF, Excel thời khóa biểu, Excel môn học; mở file trên máy.
5. Sao lưu → xóa dữ liệu → khôi phục; thử khôi phục một file không phải sao lưu để thấy báo lỗi.
6. Đổi giao diện sáng/tối và ngày đầu tuần, thoát hẳn app, mở lại.