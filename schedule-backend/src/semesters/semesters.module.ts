import { Module } from '@nestjs/common';
import { MongooseModule } from '@nestjs/mongoose';
import { SemestersController } from './semesters.controller';
import { SemestersService } from './semesters.service';
import { Semester, SemesterSchema } from './semester.schema';

@Module({
  imports: [
    MongooseModule.forFeature([{ name: Semester.name, schema: SemesterSchema }]),
  ],
  controllers: [SemestersController],
  providers: [SemestersService],
  exports: [SemestersService],
})
export class SemestersModule {}
