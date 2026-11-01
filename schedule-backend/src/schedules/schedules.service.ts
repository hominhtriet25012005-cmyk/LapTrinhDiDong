import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model } from 'mongoose';
import { Schedule } from './schedule.schema';

@Injectable()
export class SchedulesService {
  constructor(@InjectModel(Schedule.name) private scheduleModel: Model<Schedule>) {}

  async create(dto: any): Promise<Schedule> {
    return new this.scheduleModel(dto).save();
  }

  async findAll(): Promise<Schedule[]> {
    return this.scheduleModel
      .find()
      .populate({
        path: 'classId',
        populate: [{ path: 'subjectId' }, { path: 'semesterId' }],
      })
      .populate('roomId')
      .exec();
  }

  async findOne(id: string): Promise<Schedule> {
    const schedule = await this.scheduleModel
      .findById(id)
      .populate({
        path: 'classId',
        populate: [{ path: 'subjectId' }, { path: 'semesterId' }],
      })
      .populate('roomId')
      .exec();
    if (!schedule) throw new NotFoundException('Không tìm thấy lịch học');
    return schedule;
  }

  async update(id: string, dto: any): Promise<Schedule> {
    const updated = await this.scheduleModel.findByIdAndUpdate(id, dto, { new: true }).exec();
    if (!updated) throw new NotFoundException('Không tìm thấy lịch học');
    return updated;
  }

  async remove(id: string): Promise<Schedule> {
    const deleted = await this.scheduleModel.findByIdAndDelete(id).exec();
    if (!deleted) throw new NotFoundException('Không tìm thấy lịch học');
    return deleted;
  }
}
