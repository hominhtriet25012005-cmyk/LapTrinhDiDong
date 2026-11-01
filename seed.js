const mongoose = require('mongoose');

const MONGO_URI = process.env.MONGODB_URI || 'mongodb://127.0.0.1:27017/schedule';

async function seed() {
  try {
    console.log('🔄 Đang kết nối tới MongoDB...');
    await mongoose.connect(MONGO_URI);
    const db = mongoose.connection.db;

    console.log('🧹 Làm sạch dữ liệu cũ...');
    await db.collection('semesters').deleteMany({});
    await db.collection('subjects').deleteMany({});
    await db.collection('rooms').deleteMany({});
    await db.collection('classes').deleteMany({});
    await db.collection('schedules').deleteMany({});

    console.log('🚀 Tạo dữ liệu mới phủ đủ các ngày trong tuần...');

    // 1. Tạo Học kỳ
    const semesterRes = await db.collection('semesters').insertOne({
      name: "Học kỳ 1 (2025 - 2026)",
      startDate: "2025-09-01",
      endDate: "2026-01-15",
      createdAt: new Date(),
      updatedAt: new Date()
    });

    // 2. Tạo Môn học
    const subjectRes = await db.collection('subjects').insertOne({
      subjectCode: "LTDĐ",
      name: "Lập trình di động",
      credits: 3,
      type: "Lý thuyết",
      createdAt: new Date(),
      updatedAt: new Date()
    });

    // 3. Tạo Phòng học
    const roomRes = await db.collection('rooms').insertOne({
      roomName: "A.302",
      building: "Tòa A",
      createdAt: new Date(),
      updatedAt: new Date()
    });

    // 4. Tạo Lớp học
    const classRes = await db.collection('classes').insertOne({
      className: "Lập trình di động - Nhóm 1",
      code: "L01",
      subjectId: subjectRes.insertedId,
      semesterId: semesterRes.insertedId,
      createdAt: new Date(),
      updatedAt: new Date()
    });

    // 5. Tạo Lịch học cho TẤT CẢ các chỉ số Thứ trong tuần (phủ cả chuẩn JS, ISO & VN)
    const dayIndices = [0, 1, 2, 3, 4, 5, 6, 7, 8];
    for (const day of dayIndices) {
      await db.collection('schedules').insertOne({
        classId: classRes.insertedId,
        roomId: roomRes.insertedId,
        dayOfWeek: day,
        startPeriod: 1,
        endPeriod: 4,
        weeks: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10],
        createdAt: new Date(),
        updatedAt: new Date()
      });
    }

    console.log('=============================================');
    console.log('✅ SEED DỮ LIỆU ĐA NGÀY THÀNH CÔNG 100%!');
    console.log('=============================================');
  } catch (error) {
    console.error('❌ Lỗi khi seed dữ liệu:', error);
  } finally {
    await mongoose.disconnect();
    process.exit(0);
  }
}

seed();
