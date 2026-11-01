import { Controller, Get } from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model } from 'mongoose';

@Controller()
export class AppController {
  constructor(
    @InjectModel('Semester') private semesterModel: Model<any>,
    @InjectModel('Subject') private subjectModel: Model<any>,
    @InjectModel('Room') private roomModel: Model<any>,
    @InjectModel('Class') private classModel: Model<any>,
    @InjectModel('Schedule') private scheduleModel: Model<any>,
  ) {}

  @Get('seed')
  async seedData() {
    // 1. Tạo Học kỳ
    const semester = await this.semesterModel.create({
      name: 'Học kỳ 1 (2025 - 2026)',
      startDate: '2025-09-01',
      endDate: '2026-01-15',
    });

    // 2. Tạo Môn học
    const subject = await this.subjectModel.create({
      subjectCode: 'LTDĐ',
      name: 'Lập trình di động',
      credits: 3,
    });

    // 3. Tạo Phòng học
    const room = await this.roomModel.create({
      roomName: 'A.302',
      building: 'Tòa A',
    });

    // 4. Tạo Lớp học
    const newClass = await this.classModel.create({
      code: 'L01',
      name: 'Lập trình di động - Nhóm 1',
      subjectId: subject._id,
      semesterId: semester._id,
    });

    // 5. Tạo Lịch học
    const schedule = await this.scheduleModel.create({
      classId: newClass._id,
      roomId: room._id,
      dayOfWeek: 6,
      startPeriod: 1,
      endPeriod: 4,
    });

    return {
      message: 'Đã seed toàn bộ dữ liệu thành công!',
      data: { semester, subject, room, newClass, schedule },
    };
  }
}
