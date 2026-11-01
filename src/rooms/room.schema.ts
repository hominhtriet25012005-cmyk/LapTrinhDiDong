import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { Document } from 'mongoose';

export enum RoomType {
  THEORY = 'Phòng lý thuyết',
  LAB = 'Phòng Lab',
}

@Schema({ timestamps: true })
export class Room extends Document {
  @Prop({ required: true, uppercase: true, trim: true })
  roomName: string;

  @Prop({ required: true, trim: true })
  building: string;

  @Prop({ required: true, enum: RoomType, default: RoomType.THEORY })
  type: RoomType;
}

export const RoomSchema = SchemaFactory.createForClass(Room);
