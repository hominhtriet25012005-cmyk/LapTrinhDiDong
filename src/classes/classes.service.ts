import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model } from 'mongoose';
import { ClassEntity } from './class.schema';

@Injectable()
export class ClassesService {
  constructor(@InjectModel(ClassEntity.name) private classModel: Model<ClassEntity>) {}

  async create(dto: any): Promise<ClassEntity> {
    return new this.classModel(dto).save();
  }

  async findAll(): Promise<ClassEntity[]> {
    return this.classModel.find().populate('subjectId').populate('semesterId').exec();
  }

  async findOne(id: string): Promise<ClassEntity> {
    const classItem = await this.classModel.findById(id).populate('subjectId').populate('semesterId').exec();
    if (!classItem) throw new NotFoundException('Không tìm thấy lớp học phần');
    return classItem;
  }

  async update(id: string, dto: any): Promise<ClassEntity> {
    const updated = await this.classModel.findByIdAndUpdate(id, dto, { new: true }).exec();
    if (!updated) throw new NotFoundException('Không tìm thấy lớp học phần');
    return updated;
  }

  async remove(id: string): Promise<ClassEntity> {
    const deleted = await this.classModel.findByIdAndDelete(id).exec();
    if (!deleted) throw new NotFoundException('Không tìm thấy lớp học phần');
    return deleted;
  }
}
