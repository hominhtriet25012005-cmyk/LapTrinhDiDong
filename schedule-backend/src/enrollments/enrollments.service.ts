import { Injectable } from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model, Types } from 'mongoose';
import { Enrollment } from './enrollment.schema';
import { Schedule } from '../schedules/schedule.schema';

@Injectable()
export class EnrollmentsService {
  constructor(
    @InjectModel(Enrollment.name) private enrollmentModel: Model<Enrollment>,
    @InjectModel(Schedule.name) private scheduleModel: Model<Schedule>,
  ) {}

  async enrollClass(userId: string, classId: string) {
    return new this.enrollmentModel({
      userId: new Types.ObjectId(userId),
      classId: new Types.ObjectId(classId),
    }).save();
  }

  async getMySchedule(userId: string) {
    const enrollments = await this.enrollmentModel.find({ userId: new Types.ObjectId(userId) }).exec();
    const classIds = enrollments.map((e) => e.classId);

    return this.scheduleModel
      .find({ classId: { $in: classIds } })
      .populate({
        path: 'classId',
        populate: [{ path: 'subjectId' }, { path: 'semesterId' }],
      })
      .populate('roomId')
      .exec();
  }
}
