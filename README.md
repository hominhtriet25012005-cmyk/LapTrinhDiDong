# Personal Schedule Management

> Ứng dụng Android quản lý thời khóa biểu cá nhân, viết bằng Java và XML. README này dùng để theo dõi hiện trạng, quyết định kiến trúc và tiến độ triển khai từng giai đoạn.

## Thông tin project

| Hạng mục | Hiện trạng |
|---|---|
| Nền tảng | Android Studio, Java 11, XML Layout |
| UI | Material Design 3; giao diện hiện tại được dựng theo Figma |
| Lưu trữ | Room trên SQLite (database v2; có migration v1→v2) |
| Thông báo | Android Notifications có âm thanh; nhắc từ occurrence lịch đã phân giải |
| Xuất dữ liệu | PDF |
| Package / applicationId | `com.example.demo` (giữ nguyên) |
| SDK | minSdk 26, compileSdk 37, targetSdk 37 |
| Module | `:app` |

## Trạng thái hiện tại

**Roadmap giao diện/chức năng chính đã đến giai đoạn 9 — Xuất PDF; roadmap backend riêng theo [check.md](check.md) đã chạy Giai đoạn 9 — Tích hợp/hồi quy.** Full unit suite và APK build đạt; Room instrumentation/runtime vẫn chờ ADB hoạt động. Room là nguồn dữ liệu thật cho học kỳ, môn học, lớp và lịch; notification backend phân giải buổi lặp cùng ngoại lệ hủy/đổi/học bù. Backup/restore có JSON version 1 và API bất đồng bộ nhận SAF `Uri` nhưng màn hình chưa nối. Backend Excel tạo workbook lịch và môn/lớp, nhưng màn hình chưa nối exporter. `SearchActivity` hiện vẫn tìm lịch lặp; occurrence search API đã có nhưng chưa được nối vào màn hình.

### Những phần đã có

- Giao diện cho trang chủ, thời khóa biểu, môn học, học kỳ, tìm kiếm, thống kê, thông báo, xuất dữ liệu, sao lưu và trạng thái rỗng/lỗi.
- Activity, Fragment, adapter và model Java tương ứng với các màn hình.
- Điều hướng bottom navigation và các màn hình chi tiết/form.
- Nền tảng Room và CRUD học kỳ; danh sách học kỳ lấy dữ liệu thật, không còn dùng danh sách giả.
- CRUD môn học bằng Room, liên kết khóa ngoại với học kỳ, lọc học kỳ hiện tại/tất cả và tìm theo mã hoặc tên.
- CRUD lớp học phần và buổi học lặp theo thứ/ngày áp dụng; chọn lớp từ môn học, sửa/xóa theo ID và xác nhận khi xóa dây chuyền.
- Thời khóa biểu theo ngày/tuần/tháng lấy từ Room; lịch chỉ hiện nếu có ít nhất một lần học trong khoảng ngày đang xem.
- Kiểm tra giờ/ngày hợp lệ và chặn lịch trùng giờ cùng thứ khi có giao nhau thực tế trong ngày áp dụng.
- Trang chủ lấy lịch hôm nay/tuần từ Room, chỉ rõ buổi đang diễn ra hoặc sắp tới, số buổi/số giờ, số môn của học kỳ hiện tại và khoảng trống giữa các buổi.
- Tra cứu thời khóa biểu lấy dữ liệu thật từ Room, tìm theo tên/mã môn, mã lớp và phòng; lọc kết hợp theo học kỳ, thứ trong tuần và phòng. Backend có API tìm occurrence thực tế, nhưng màn hình hiện vẫn hiển thị lịch lặp vì chưa có bộ chọn khoảng ngày.
- Thống kê theo học kỳ lấy môn học và lịch từ Room; KPI, biểu đồ theo thứ và so sánh khối lượng giữa các học kỳ được tính từ số lần học thực tế trong khoảng ngày áp dụng.
- Nhắc lịch lấy buổi học thực tế từ Room, bỏ buổi hủy và áp dụng ngày/giờ/phòng mới cho buổi đổi hoặc học bù; bản tin hằng ngày liệt kê tất cả buổi với giờ và phòng. Alarm dùng chế độ Android không chính xác để không yêu cầu quyền exact-alarm đặc biệt.
- Backup/restore backend đóng gói học kỳ, môn, lớp, lịch và ngoại lệ vào JSON có version; kiểm tra giới hạn/quan hệ/dữ liệu trước restore và thay toàn bộ trong một Room transaction. Sau restore thành công, đồng bộ lại notification.
- Backend Excel tạo XLSX từ cùng occurrence/range với PDF, có phạm vi ngày/tuần/tháng/học kỳ; workbook môn học có sheet lớp học phần riêng. Màn hình Export chưa nối API nên vẫn báo Excel chưa hỗ trợ.

### Những phần cần triển khai

- Nối các hộp thoại ngoại lệ lịch trên giao diện với API Room đã có ở backend.
- Xác nhận trực tiếp trên emulator/device: cấp/từ chối quyền, chuông báo, hủy/cập nhật alarm, summary và khôi phục sau reboot/đổi giờ.
- Kiểm thử trực tiếp việc tạo/lưu PDF thật qua SAF trên emulator/device.
- Nối lựa chọn Excel với exporter qua SAF và mở thử tệp bằng ứng dụng bảng tính.
- Nối màn hình sao lưu/khôi phục với `BackupManager` và Storage Access Framework; sau đó smoke test file thật và notification trên emulator/device.
- Khi thiết kế bộ chọn ngày cho màn hình tìm kiếm, nối `SearchActivity` sang API occurrence để kết quả phản ánh buổi hủy/đổi/học bù.
- Chạy Room migration/backup instrumentation và kiểm tra notification runtime trên emulator/device khi ADB hoạt động.
- Kiểm thử theo module và kiểm thử luồng demo tích hợp.

### Ghi chú build và kiểm thử

- Xác minh giai đoạn PDF ngày 22/10/2026: `testDebugUnitTest`, `assembleDebug` và `assembleDebugAndroidTest` thành công với Gradle 9.6.0/JDK 25.0.3; 39 unit test đạt.
- Xác minh backend thông báo ngày 01/11/2026: JDK 25 biên dịch test mục tiêu (5/5 đạt); Gradle 9.6.0 `:app:testDebugUnitTest --tests com.example.demo.notifications.ScheduleReminderPlannerTest` và `:app:assembleDebug` thành công.
- Giai đoạn backend 7 ngày 01/11/2026: JSON backup giới hạn 5 MiB và có version; snapshot năm bảng trong transaction; validate dữ liệu/FK/ID; restore atomic; `BackupManager` đọc/ghi content `Uri` ngoài main thread và gọi đồng bộ notification sau restore. `:app:testDebugUnitTest` đạt 93/93; `:app:assembleDebug` và `:app:assembleDebugAndroidTest` thành công. Room instrumentation đã biên dịch nhưng chưa thực thi vì `adb devices` báo `could not read ok from ADB Server`; màn hình UI/SAF và smoke test notification vẫn còn.
- Giai đoạn backend 8 ngày 01/11/2026: thêm `ScheduleExcelExporter` tạo XLSX OpenXML không thêm thư viện; lịch theo ngày/tuần/tháng/học kỳ và môn/lớp theo học kỳ. `:app:testDebugUnitTest` đạt 100/100; `:app:assembleDebug` và `:app:assembleDebugAndroidTest` thành công. Test đã mở ZIP và parse XML; chưa kiểm chứng bằng Excel/LibreOffice vì không có ứng dụng đó trên máy. UI/SAF chưa nối.
- Giai đoạn backend 9 ngày 01/11/2026: mở rộng regression test dùng cùng occurrence cho dashboard, thống kê, search API, PDF range, Excel, notification planner/summary và backup JSON round-trip; migration test xác nhận Room version 2 sau v1→v2. `:app:testDebugUnitTest` đạt 100/100; `:app:assembleDebug` và `:app:assembleDebugAndroidTest` thành công; `git diff --check` đạt. `adb devices` thất bại (`could not read ok from ADB Server`), nên Room instrumentation và runtime smoke test chưa chạy. `SearchActivity` vẫn dùng lịch lặp; UI không đổi.
- Room schema v1→v2 được sinh tại `app/schemas/com.example.demo.data.local.AppDatabase/2.json`; instrumentation migration chưa chạy.
- `assembleDebugAndroidTest` đã biên dịch/đóng gói thành công 16 Room instrumentation test (cùng 1 test mẫu), bao gồm test tải học kỳ bằng ID. Chúng **chưa được thực thi** trên emulator/device; `adb.exe` có trong Android SDK nhưng daemon lỗi `could not read ok from ADB Server`, nên tương tác SQLite và smoke test giao diện vẫn cần xác nhận.
- Giai đoạn 6: `testDebugUnitTest`, `assembleDebug` và `assembleDebugAndroidTest` đều thành công; 26 unit test pass. Hai Room instrumentation test mới kiểm tra tìm theo mã/tên/lớp/phòng và giao bộ lọc học kỳ/thứ/phòng đã compile nhưng chưa chạy trên thiết bị.
- Không redesign giao diện Figma; form chỉ bỏ dữ liệu điền sẵn khỏi runtime, có trạng thái rỗng khi chưa có học kỳ, và dùng menu toolbar sẵn có để đặt thao tác xóa.

## Lộ trình triển khai

Đánh dấu `[x]` sau khi module đã hoàn thành và xác minh. Không chuyển sang module kế tiếp khi module hiện tại còn lỗi build hoặc kiểm thử chưa rõ kết quả.

| # | Giai đoạn | Nội dung chính | Trạng thái |
|---:|---|---|---|
| 0 | Khảo sát source và lập kế hoạch | Kiểm tra Java, XML, Manifest, Gradle; phân loại chức năng và đề xuất kiến trúc | [x] Hoàn tất |
| 1 | Room Database nền tảng | Entity, DAO, relation/projection, database, repository, validation, test database | [~] Build/unit pass; instrumentation chờ thiết bị |
| 2 | Quản lý học kỳ | CRUD, ngày bắt đầu/kết thúc, học kỳ hiện tại duy nhất | [~] Đã triển khai; instrumentation chờ thiết bị |
| 3 | Quản lý môn học | CRUD, liên kết học kỳ, tìm kiếm và lọc theo học kỳ | [~] Build/unit pass; Room instrumentation chờ emulator/device |
| 4 | Quản lý lớp và lịch học | CRUD lớp học phần/lịch lặp, lịch ngày-tuần-tháng, kiểm tra giờ và xung đột | [~] Build + 22 unit test pass; instrumentation/smoke test chờ thiết bị |
| 5 | Trang chủ, ngày/tuần và thời gian trống | Dữ liệu hôm nay/tuần, lớp tiếp theo, KPI cơ bản, khoảng trống lịch | [~] Build + 26 unit test pass; smoke test thiết bị còn chờ |
| 6 | Tra cứu | Tìm theo môn, mã, lớp, phòng; lọc học kỳ và thứ trong tuần | [~] Đã nối Room; build/unit pass, instrumentation chờ thiết bị |
| 7 | Thống kê | KPI và biểu đồ tính từ dữ liệu của học kỳ | [~] Đã nối Room; unit/build pass, instrumentation và smoke test chờ thiết bị |
| 8 | Thông báo có âm thanh | Quyền thông báo, channel, nhắc occurrence thực tế, cập nhật/hủy và khôi phục sau reboot | [~] Stage6 backend: 5 test mục tiêu + assembleDebug pass; instrumentation/smoke test chờ thiết bị |
| 9 | Xuất PDF | Chọn học kỳ/phạm vi, tạo PDF nhiều trang và lưu qua Storage Access Framework | [~] Build + 39 unit test pass; instrumentation compile, SAF/PDF smoke test chờ thiết bị |
| 10 | Hoàn thiện và kiểm thử demo | Bỏ dữ liệu mẫu runtime, xử lý trạng thái rỗng/lỗi, kiểm thử tích hợp và APK cuối | [ ] Chưa bắt đầu |

## Mô hình dữ liệu dự kiến

```text
Semester 1 ─── N Subject 1 ─── N ClassSection 1 ─── N Schedule
```

| Bảng | Dữ liệu chính |
|---|---|
| `SemesterEntity` | Tên, năm học, ngày bắt đầu/kết thúc, học kỳ hiện tại, ghi chú |
| `SubjectEntity` | Mã/tên môn, tín chỉ, giảng viên mặc định, màu, ghi chú, khóa ngoại học kỳ |
| `ClassSectionEntity` | Mã lớp/nhóm, giảng viên, ghi chú, khóa ngoại môn học |
| `ScheduleEntity` | Thứ trong tuần, phút bắt đầu/kết thúc, phòng, ngày áp dụng, ghi chú và cấu hình nhắc |

Quy ước dự kiến: ngày dùng `epochDay`, giờ dùng số phút từ 00:00, thứ dùng số 1–7. Khóa ngoại và transaction bảo đảm dữ liệu liên kết; không dùng destructive migration và không nạp dữ liệu demo vào database production.

### Mô hình đã triển khai ở giai đoạn 1

- `SemesterEntity` → `SubjectEntity` → `ClassSectionEntity` → `ScheduleEntity`, khóa ngoại xóa dây chuyền và index cho các khóa/truy vấn chính.
- Bốn DAO hỗ trợ CRUD và truy vấn LiveData; học kỳ hiện tại được đổi trong transaction để chỉ có tối đa một học kỳ hiện tại.
- `ScheduleDetails` là projection join lịch với lớp, môn và học kỳ; các relation phục vụ tải cây dữ liệu theo nhu cầu.
- `AppDatabase` dùng singleton, schema version 1 và không bật destructive migration. `AppRepository` cung cấp API nền để UI tích hợp ở các giai đoạn kế tiếp.
- Thời gian lưu bằng epoch day/phút trong ngày; `ScheduleValidator` kiểm tra thứ, khoảng giờ, ngày áp dụng và ngày học kỳ.
- Phiên bản thư viện: Room 2.8.5, Lifecycle LiveData 2.11.0; annotation processor Java được cấu hình qua Gradle Version Catalog.

### Giai đoạn 2 — Quản lý học kỳ

- `SemestersActivity` quan sát Room LiveData và hiển thị học kỳ cùng số môn đếm từ truy vấn SQLite; trạng thái rỗng hướng dẫn tạo học kỳ đầu tiên.
- Form hỗ trợ tạo/sửa/xóa có xác nhận; thao tác chạy nền qua `AppRepository`. Chỉnh sửa truyền ID và nạp lại dữ liệu từ database, không truyền model giả qua Intent.
- Kiểm tra tên và năm học bắt buộc; năm học phải theo dạng hai năm liên tiếp (`YYYY-YYYY`), ngày theo định dạng nghiêm ngặt `dd/MM/yyyy`, và ngày kết thúc không trước ngày bắt đầu. Ngày có thể bỏ trống.
- Form có trường nhập năm học độc lập với ngày bắt đầu/kết thúc (ví dụ học kỳ bắt đầu năm 2026 vẫn có thể thuộc `2025–2026`); dữ liệu được chuẩn hóa thành `YYYY–YYYY` và danh sách ưu tiên sắp xếp theo năm học mới nhất. Cột `academicYear` đã có trong Room nên thay đổi này không cần migration.
- Instrumentation test bổ sung kiểm tra cập nhật học kỳ hiện tại và số môn trong summary; source test đã biên dịch nhưng cần emulator/device để thực thi.

### Giai đoạn 3 — Quản lý môn học

- Danh sách môn học bỏ dữ liệu mẫu runtime, quan sát Room và tìm theo tên/mã; chip lọc học kỳ hiện tại và tất cả học kỳ dùng truy vấn Room.
- Chip học kỳ hiện tại hiển thị tên/năm học từ dữ liệu thật. Form nạp danh sách học kỳ thật, mặc định học kỳ hiện tại (hoặc học kỳ đầu tiên nếu chưa đặt hiện tại), và không cho lưu khi database chưa có học kỳ.
- Form tạo/sửa ghi Room, truyền ID qua Intent, chuẩn hóa mã môn thành chữ hoa, kiểm tra tên/mã/học kỳ/tín chỉ; báo trùng mã trong cùng học kỳ theo unique index hiện có. Số tín chỉ để trống được lưu là 0 vì trường này không bắt buộc trên form.
- Chi tiết môn học tải lại bản ghi và học kỳ từ Room; chỉnh sửa và xóa có xác nhận, lỗi được phản hồi. Phần lớp liên quan hiển thị lớp thật nếu có hoặc trạng thái rỗng, không dựng lịch giả.
- TDD cho `SubjectFormMapper`: 4 unit test mới; bổ sung 2 Room instrumentation test cho CRUD và tìm kiếm. Cả 16 unit test pass; APK debug và APK AndroidTest build thành công. Instrumentation đã biên dịch nhưng chưa chạy do môi trường không có ADB hoạt động.

### Giai đoạn 4 — Quản lý lớp và lịch học

- Màn hình chi tiết môn học quản lý các lớp học phần thật trong Room: thêm/sửa, phát hiện mã lớp trùng không phân biệt hoa thường trong cùng môn, và xóa có cảnh báo lịch liên quan cũng bị xóa.
- Form lịch bỏ dữ liệu điền sẵn, nạp môn/lớp từ Room, hỗ trợ tạo lớp nhanh, chọn thứ, giờ và khoảng ngày; ngày học kỳ được điền mặc định nếu đã có. Chỉnh sửa dùng schedule ID và nạp lại bản ghi thật.
- Chi tiết lịch hỗ trợ sửa/xóa thật. Thời khóa biểu ngày/tuần/tháng truy vấn lịch thật, lọc theo ít nhất một lần xảy ra trong khoảng đang xem, sắp xếp theo thứ/giờ và mở đúng lịch theo ID.
- Lưu lịch và cập nhật lớp trong một transaction. Xung đột được chặn khi hai lịch cùng thứ, giờ giao nhau và khoảng áp dụng có ít nhất một ngày học chung; các buổi liền kề, khác thứ hoặc không trùng ngày được phép.
- TDD bổ sung 3 test mapper và 3 test xung đột; thêm 2 Room instrumentation test cho CRUD lớp và tính nguyên tử khi lịch xung đột. 22/22 unit test pass; `assembleDebug` và `assembleDebugAndroidTest` thành công. Có 11 Room instrumentation test đã biên dịch nhưng chưa chạy trên thiết bị; chưa xác minh smoke test trực tiếp.
- Không đổi schema Room, package hoặc ngôn ngữ; giữ luồng bố cục XML hiện hữu và chỉ thêm bộ chọn lớp/trạng thái rỗng cần cho chức năng.

### Giai đoạn 5 — Trang chủ, ngày/tuần và thời gian trống

- `HomeFragment` bỏ toàn bộ danh sách lịch mẫu runtime và tải projection lịch từ Room ngoài main thread; mỗi lần Trang chủ hoạt động sẽ cập nhật ngày và dashboard theo ngày hiện tại của thiết bị.
- Banner hiển thị lớp đang diễn ra trước, nếu không thì lớp tiếp theo trong ngày; thời gian/phòng/giảng viên và điều hướng chi tiết lấy từ đúng schedule ID. Khi hết lịch hoặc không có lớp sẽ có trạng thái rỗng tương ứng.
- KPI hiển thị số buổi và thời lượng học hôm nay, số môn thuộc học kỳ hiện tại; dòng tổng hợp bổ sung số buổi và thời lượng trong tuần hiện tại (Thứ Hai–Chủ Nhật). Học kỳ và môn học được quan sát bằng Room LiveData.
- Thời gian trống được tính giữa các buổi liên tiếp trong ngày và hiển thị theo khoảng giờ/thời lượng. Không tự suy diễn thời gian trước buổi đầu hoặc sau buổi cuối vì ứng dụng chưa có cấu hình giờ bắt đầu/kết thúc ngày học.
- Giữ cấu trúc Figma chính; thay nội dung mẫu runtime bằng placeholder/trạng thái tải, thêm dòng tổng hợp tuần và thẻ khoảng trống. Không đổi schema/package, không thêm dữ liệu demo.
- TDD bổ sung 4 test calculator cho lần học thực tế theo ngày áp dụng, chọn lớp đang/sắp diễn ra, tổng KPI ngày/tuần, khe trống và ngày không có lịch. Cả 26/26 unit test pass; `assembleDebug` và `assembleDebugAndroidTest` thành công. Không chạy smoke test trên thiết bị trong lượt này; Room instrumentation vẫn chỉ mới compile, như ghi chú build bên trên.

### Giai đoạn 6 — Tra cứu thời khóa biểu

- `ScheduleDao.searchDetails` truy vấn projection lịch/lớp/môn/học kỳ từ Room; hỗ trợ tìm theo mã/tên môn, mã lớp hoặc phòng, đồng thời kết hợp bộ lọc ID học kỳ, thứ (1–7) và tiêu chí phòng riêng.
- `SearchActivity` bỏ lịch mẫu, học kỳ giả và từ khóa mặc định; danh sách học kỳ lấy từ Room, mặc định hiển thị tất cả lịch thật. Kết quả cập nhật khi nhập từ khóa/phòng, chọn học kỳ/thứ hoặc bấm áp dụng; xóa bộ lọc trả về tất cả lịch.
- Kết quả được chuyển thành thẻ lịch hiện có và mở màn hình chi tiết bằng schedule ID thật. Bố cục Figma được giữ; bổ sung chip “Tất cả” để bỏ lọc thứ, cùng bộ đếm và trạng thái rỗng dùng tài nguyên chuỗi.
- Thêm 2 Room instrumentation test cho bốn trường tìm kiếm và giao các bộ lọc. `testDebugUnitTest`, `assembleDebug` và `assembleDebugAndroidTest` đều thành công; 26/26 unit test pass. Tổng 13 Room instrumentation test đã compile nhưng chưa thực thi trên emulator/device; chưa smoke test UI trực tiếp.
- Không đổi schema Room, package hoặc công nghệ; APK debug và APK AndroidTest được tạo lại.

### Giai đoạn 7 — Thống kê theo học kỳ

- `StatsActivity` quan sát summary học kỳ và toàn bộ lịch chi tiết bằng Room LiveData. Bộ chọn học kỳ hiển thị dữ liệu thật, mặc định chọn học kỳ hiện tại (hoặc học kỳ mới nhất); nút phạm vi hiển thị ngày bắt đầu/kết thúc thực tế.
- KPI gồm số môn trong học kỳ, số buổi lặp mỗi tuần và tổng giờ mỗi tuần. Biểu đồ ngày cập nhật số lượng và chiều cao cột; biểu đồ khối lượng tạo một dòng cho mỗi học kỳ và so sánh tổng giờ.
- `SemesterStatisticsCalculator` tính buổi/tuần và giờ toàn kỳ dựa trên thứ cùng số lần lịch thực sự xảy ra. Giới hạn ngày học kỳ được dùng nếu có; khi thiếu ngày biên, phép tính dùng ngày áp dụng của từng lịch. Không có dữ liệu được thể hiện bằng 0/trạng thái rỗng thay vì số mẫu.
- TDD bổ sung 4 unit test cho KPI, phân bố theo thứ, lần học thực tế, lịch sai/ngoài kỳ và học kỳ thiếu ngày biên; thêm 1 Room instrumentation test cho truy vấn projection quan sát lịch. `testDebugUnitTest`, `assembleDebug`, `assembleDebugAndroidTest` thành công; 30/30 unit test pass. Tổng 14 Room instrumentation test compile nhưng chưa chạy; chưa smoke test trên emulator/device.
- Không đổi schema, package hay công nghệ; giữ bố cục thống kê hiện có và thay giá trị tĩnh bằng dữ liệu động.

### Giai đoạn 8 — Thông báo có âm thanh

- Giữ nguyên layout Figma của `NotificationsActivity`; xin `POST_NOTIFICATIONS` thật trên Android 13+, hiển thị trạng thái theo quyền hệ thống, đồng thời mở cài đặt ứng dụng nếu quyền/channel đang bị tắt.
- Tạo notification channel mức ưu tiên cao với âm thanh mặc định của hệ thống; âm thanh/vibrate tiếp tục chịu quyền điều khiển của người dùng trong Android Settings.
- Hai switch và giờ tóm tắt được lưu trong SharedPreferences. Trạng thái toàn cục vẫn là công tắc chính, còn `notificationEnabled` của từng lịch tiếp tục được kiểm tra. Buổi bù kế thừa số phút nhắc của lịch gốc (mặc định 1.440 phút).
- Alarm được chọn từ `ScheduleSnapshot` mà `ScheduleResolver` tạo trong Room transaction; buổi hủy bị bỏ qua, buổi đổi/học bù dùng ngày, giờ và phòng hiệu lực. Khi alarm đến, dữ liệu được phân giải lại trước khi hiện thông báo; repository đồng bộ sau ghi lịch/ngoại lệ và xóa dữ liệu cha.
- Bản tóm tắt lấy lịch của đúng ngày và liệt kê mọi buổi với tên môn, giờ bắt đầu/kết thúc, phòng. Alarm vẫn dùng `AlarmManager.setAndAllowWhileIdle()` dạng không chính xác, không xin quyền exact-alarm đặc biệt; Android có thể trì hoãn khi Doze/tiết kiệm pin. Receiver hiện có phục hồi sau reboot và đổi ngày/giờ/múi giờ.
- Stage6 thêm 5 unit test mục tiêu cho hủy, buổi đổi, học bù, trạng thái nhắc, reminder đã quá hạn và nội dung summary nhiều buổi. `:app:testDebugUnitTest --tests com.example.demo.notifications.ScheduleReminderPlannerTest` và `:app:assembleDebug` thành công; notification instrumentation/device test chưa chạy. Backend Giai đoạn 7 đã gọi `NotificationScheduler.syncAll()` sau khi restore thành công; cần nối UI và smoke test trên thiết bị.
- Không đổi schema Room, package name, ngôn ngữ hay bố cục XML. Cài đặt thông báo dùng SharedPreferences; các trường nhắc hiện hữu trong `ScheduleEntity` tiếp tục được dùng.

### Giai đoạn 9 — Xuất thời khóa biểu PDF

- `ExportActivity` nạp danh sách học kỳ và lịch từ Room; mặc định chọn học kỳ hiện tại (hoặc học kỳ mới nhất), bỏ các nhãn học kỳ và dữ liệu xem trước mẫu khỏi runtime.
- Hỗ trợ phạm vi tuần hiện tại (Thứ Hai–Chủ Nhật), tháng hiện tại và toàn học kỳ. Ngày tuần/tháng được cắt theo ngày bắt đầu/kết thúc học kỳ; khi học kỳ thiếu ngày biên, dùng biên lịch áp dụng thật để suy ra phạm vi toàn kỳ.
- Mở `ActivityResultContracts.CreateDocument` với MIME `application/pdf`, để người dùng tự chọn vị trí/tên tệp và không cần quyền truy cập Downloads. Hủy trình chọn không tạo tệp và lỗi ghi được báo rõ.
- `ScheduleExportCalculator` mở lịch lặp thành các ngày học thật theo thứ ISO và ngày áp dụng, lọc theo học kỳ, sắp xếp theo ngày/giờ; xem trước đếm số buổi và số môn thực tế.
- `SchedulePdfWriter` dùng `PdfDocument` có sẵn của Android, tạo bảng A4 ngang nhiều trang với tiêu đề, môn, mã lớp, giờ, phòng, giảng viên và số trang; phạm vi rỗng vẫn tạo PDF thông báo không có buổi học. Lựa chọn Excel giữ nguyên trên giao diện nhưng hiện thông báo chưa hỗ trợ, không giả lập xuất thành công.
- Thêm 6 unit test cho phạm vi và lần học, cùng 1 Room instrumentation test cho `SemesterDao.getById`. `testDebugUnitTest`, `assembleDebug`, `assembleDebugAndroidTest` thành công; 39/39 unit test pass và 16 Room instrumentation test compile. Chưa chạy instrumentation/smoke test do ADB daemon không khởi động; cần kiểm tra PDF mở được và nội dung đúng trên thiết bị.
- Không đổi schema Room, package, công nghệ hoặc bố cục Figma; chỉ thay nội dung mẫu tĩnh bằng dữ liệu động/trạng thái rỗng.

## Nguyên tắc triển khai

- Giữ nguyên package `com.example.demo` và bố cục XML theo Figma.
- Giữ Java + XML; không chuyển Kotlin, Flutter hoặc Compose.
- Không viết lại toàn bộ project và không xóa tính năng đang hoạt động.
- Không dùng dữ liệu giả trong bản chạy thực tế; fixture chỉ dùng trong kiểm thử.
- Truy vấn/ghi Room ngoài main thread; truyền ID giữa các màn hình và tải dữ liệu từ repository.
- Từng module cần có phạm vi thay đổi nhỏ, kiểm thử tương ứng, build debug và báo cáo kết quả thực tế.
- Không tuyên bố một tính năng đã hoạt động nếu mới chỉ cập nhật giao diện hoặc hiện Toast.

## Kiểm tra sau mỗi module

- [ ] Unit test cho logic/validation/aggregation liên quan.
- [ ] Room instrumentation test khi môi trường emulator/device sẵn sàng.
- [ ] `assembleDebug` chạy thành công.
- [ ] Smoke test các màn hình và luồng bị ảnh hưởng.
- [ ] Cập nhật trạng thái module, kết quả build/test và vấn đề còn lại trong README.

Lệnh build/test trên Windows:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest  # cần emulator hoặc thiết bị đang kết nối
```

## Nhật ký tiến độ

| Ngày | Giai đoạn | Kết quả / ghi chú |
|---|---|---|
| 22/10/2026 | 0 — Khảo sát | UI đã có; xác định Room và tích hợp dữ liệu, thông báo, PDF còn thiếu; đề xuất mô hình dữ liệu và lộ trình. |
| 22/10/2026 | 1 — Room nền tảng | Thêm 4 entity, 4 DAO, 3 relation, projection, AppDatabase, repository, validation và test. Build debug/test thành công; unit test pass. Room instrumentation đã biên dịch nhưng chưa chạy vì ADB daemon không khởi động. |
| 22/10/2026 | 2 — Quản lý học kỳ | Nối list/form với Room; CRUD, xác nhận cascade khi xóa, học kỳ hiện tại duy nhất, validate ngày, count môn học thật và empty state. `testDebugUnitTest assembleDebug assembleDebugAndroidTest` thành công; 12/12 unit test pass. 7 instrumentation test được biên dịch, chưa thực thi do ADB không khởi động. |
| 22/10/2026 | 3 — Quản lý môn học | Nối danh sách/form/chi tiết với Room; CRUD, liên kết học kỳ, lọc và tìm kiếm thật; loại bỏ dữ liệu môn học mẫu runtime. 16/16 unit test pass; APK debug và APK AndroidTest build thành công; 9 Room instrumentation test compile nhưng chưa chạy do ADB không hoạt động. |
| 22/10/2026 | 4 — Quản lý lớp và lịch học | Nối CRUD lớp, CRUD lịch, chi tiết và thời khóa biểu ngày/tuần/tháng với Room; thêm kiểm tra ngày/giờ và xung đột recurring; không đổi schema. 22/22 unit test pass; `assembleDebug` và `assembleDebugAndroidTest` thành công; 11 Room instrumentation test compile nhưng chưa chạy trên thiết bị. |
| 22/10/2026 | 5 — Trang chủ, ngày/tuần và thời gian trống | Trang chủ dùng lịch/semester/subject Room; cập nhật buổi đang hoặc sắp diễn ra, KPI ngày/tuần, thời gian trống giữa lớp và trạng thái rỗng; bỏ dữ liệu lịch giả. 26/26 unit test pass; app và APK AndroidTest build thành công; chưa smoke test trên thiết bị. |
| 22/10/2026 | 6 — Tra cứu | Nối tìm kiếm theo môn/mã/lớp/phòng và lọc học kỳ/thứ/phòng với Room; bỏ dữ liệu mẫu và mở chi tiết bằng schedule ID. 26/26 unit test pass; `assembleDebug`, `assembleDebugAndroidTest` thành công; 13 Room instrumentation test compile nhưng chưa chạy trên thiết bị. |
| 22/10/2026 | 7 — Thống kê | Nối KPI, biểu đồ theo thứ và khối lượng từng học kỳ với Room; tổng giờ tính theo các lần diễn ra thực tế. 30/30 unit test pass; app và APK AndroidTest build thành công; 14 Room instrumentation test compile nhưng chưa thực thi trên thiết bị. |
| 22/10/2026 | 8 — Thông báo | Nối quyền runtime Android 13+, channel âm thanh, nhắc trước 24 giờ/tóm tắt hằng ngày, cập nhật alarm sau CRUD và phục hồi sau reboot/đổi giờ. 33/33 unit test pass; `assembleDebug` và `assembleDebugAndroidTest` thành công; 15 Room test compile, chưa chạy vì không có ADB/emulator. |
| 22/10/2026 | 9 — Xuất PDF | Nối học kỳ/lịch Room với chọn tuần/tháng/toàn kỳ, xem trước dữ liệu thật và xuất bảng PDF A4 ngang phân trang qua Android SAF. 39/39 unit test pass; APK debug và AndroidTest build thành công; 16 Room test compile. SAF/PDF và instrumentation chưa smoke-test vì ADB daemon không khởi động. |
| 01/11/2026 | Backend Giai đoạn 6 — Thông báo occurrence | Nhắc từ lịch đã phân giải, cập nhật theo sửa/xóa/ngoại lệ, summary liệt kê toàn bộ buổi trong ngày; test mục tiêu và debug build đạt. Kiểm tra runtime và instrumentation còn chờ thiết bị. |
| 01/11/2026 | Backend Giai đoạn 7 — Backup/restore | JSON version 1, snapshot/restore nguyên tử cho năm bảng Room, validation, API bất đồng bộ qua SAF `Uri`, đồng bộ notification sau restore; 93/93 unit test và hai build APK đạt. Instrumentation compile nhưng ADB không khởi động; UI/SAF chưa nối. |
| 01/11/2026 | Backend Giai đoạn 8 — Xuất Excel | XLSX OpenXML, lịch theo ngày/tuần/tháng/học kỳ và danh sách môn/lớp; 100/100 unit test, APK debug/AndroidTest đạt. Chưa mở thử bằng ứng dụng bảng tính; UI/SAF chưa nối. |
| 01/11/2026 | Backend Giai đoạn 9 — Tích hợp/hồi quy | Shared-occurrence regression và backup round-trip test; 100/100 unit test, debug/AndroidTest build đạt. ADB daemon lỗi nên instrumentation migration/Room và runtime chưa chạy; SearchActivity vẫn chờ nối occurrence API. |

