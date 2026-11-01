import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model } from 'mongoose';
import { Subject } from './subject.schema';

@Injectable()
export class SubjectsService {
  constructor(@InjectModel(Subject.name) private subjectModel: Model<Subject>) {}

  async create(dto: any): Promise<Subject> {
    return new this.subjectModel(dto).save();
  }

  async findAll(): Promise<Subject[]> {
    return this.subjectModel.find().exec();
  }

  async findOne(id: string): Promise<Subject> {
    const subject = await this.subjectModel.findById(id).exec();
    if (!subject) throw new NotFoundException('Không tìm thấy môn học');
    return subject;
  }

  async update(id: string, dto: any): Promise<Subject> {
    const updated = await this.subjectModel.findByIdAndUpdate(id, dto, { new: true }).exec();
    if (!updated) throw new NotFoundException('Không tìm thấy môn học');
    return updated;
  }

  async remove(id: string): Promise<Subject> {
    const deleted = await this.subjectModel.findByIdAndDelete(id).exec();
    if (!deleted) throw new NotFoundException('Không tìm thấy môn học');
    return deleted;
  }
}
