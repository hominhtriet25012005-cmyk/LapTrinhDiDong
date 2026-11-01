import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { HydratedDocument } from 'mongoose';

export type SemesterDocument = HydratedDocument<Semester>;

@Schema({ timestamps: true })
export class Semester {
  @Prop({ required: true })
  name: string;

  @Prop({ required: true })
  startDate: string;

  @Prop({ required: true })
  endDate: string;
}

export const SemesterSchema = SchemaFactory.createForClass(Semester);
