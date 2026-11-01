import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { Document } from 'mongoose';

export enum SubjectType {
  THEORY = 'Lý thuyết',
  PRACTICE = 'Thực hành',
}

@Schema({ timestamps: true })
export class Subject extends Document {
  @Prop({ required: true, unique: true, uppercase: true, trim: true })
  subjectCode: string;

  @Prop({ required: true, trim: true })
  name: string;

  @Prop({ required: true, min: 1 })
  credits: number;

  @Prop({ required: true, enum: SubjectType, default: SubjectType.THEORY })
  type: SubjectType;
}

export const SubjectSchema = SchemaFactory.createForClass(Subject);
