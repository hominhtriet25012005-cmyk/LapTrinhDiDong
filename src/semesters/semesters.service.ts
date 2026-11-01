import { Injectable } from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model } from 'mongoose';
import { Semester, SemesterDocument } from './semester.schema';

@Injectable()
export class SemestersService {
  constructor(
    @InjectModel(Semester.name) private semesterModel: Model<SemesterDocument>,
  ) {}

  async create(createSemesterDto: any): Promise<Semester> {
    const newSemester = new this.semesterModel(createSemesterDto);
    return newSemester.save();
  }

  async findAll(): Promise<Semester[]> {
    return this.semesterModel.find().exec();
  }

  async findOne(id: string): Promise<Semester | null> {
    return this.semesterModel.findById(id).exec();
  }

  async update(id: string, updateSemesterDto: any): Promise<Semester | null> {
    return this.semesterModel.findByIdAndUpdate(id, updateSemesterDto, { new: true }).exec();
  }

  async remove(id: string): Promise<Semester | null> {
    return this.semesterModel.findByIdAndDelete(id).exec();
  }
}
