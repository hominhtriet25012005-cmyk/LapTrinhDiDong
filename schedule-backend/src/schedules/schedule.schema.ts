import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { Document, Types } from 'mongoose';

@Schema({ timestamps: true })
export class Schedule extends Document {
  @Prop({ type: Types.ObjectId, ref: 'ClassEntity', required: true })
  classId: Types.ObjectId;

  @Prop({ type: Types.ObjectId, ref: 'Room', required: true })
  roomId: Types.ObjectId;

  @Prop({ required: true, min: 2, max: 8 }) // 2 = Thứ hai, ..., 8 = Chủ nhật
  dayOfWeek: number;

  @Prop({ required: true, min: 1, max: 12 })
  startPeriod: number;

  @Prop({ required: true, min: 1, max: 12 })
  endPeriod: number;

  @Prop({ type: [Number], required: true })
  weeks: number[];
}

export const ScheduleSchema = SchemaFactory.createForClass(Schedule);
