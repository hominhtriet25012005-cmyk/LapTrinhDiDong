import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model } from 'mongoose';
import { Room } from './room.schema';

@Injectable()
export class RoomsService {
  constructor(@InjectModel(Room.name) private roomModel: Model<Room>) {}

  async create(dto: any): Promise<Room> {
    return new this.roomModel(dto).save();
  }

  async findAll(): Promise<Room[]> {
    return this.roomModel.find().exec();
  }

  async findOne(id: string): Promise<Room> {
    const room = await this.roomModel.findById(id).exec();
    if (!room) throw new NotFoundException('Không tìm thấy phòng học');
    return room;
  }

  async update(id: string, dto: any): Promise<Room> {
    const updated = await this.roomModel.findByIdAndUpdate(id, dto, { new: true }).exec();
    if (!updated) throw new NotFoundException('Không tìm thấy phòng học');
    return updated;
  }

  async remove(id: string): Promise<Room> {
    const deleted = await this.roomModel.findByIdAndDelete(id).exec();
    if (!deleted) throw new NotFoundException('Không tìm thấy phòng học');
    return deleted;
  }
}
