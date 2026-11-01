import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { Document, Types } from 'mongoose';

@Schema({ timestamps: true })
export class ClassEntity extends Document {
  @Prop({ required: true, unique: true, uppercase: true, trim: true })
  classCode: string;

  @Prop({ required: true, trim: true })
  teacherName: string;

  @Prop({ type: Types.ObjectId, ref: 'Subject', required: true })
  subjectId: Types.ObjectId;

  @Prop({ type: Types.ObjectId, ref: 'Semester', required: true })
  semesterId: Types.ObjectId;
}

export const ClassSchema = SchemaFactory.createForClass(ClassEntity);
