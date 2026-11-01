import { Module } from '@nestjs/common';
import { MongooseModule } from '@nestjs/mongoose';
import { SemestersModule } from './semesters/semesters.module';
import { SubjectsModule } from './subjects/subjects.module';
import { RoomsModule } from './rooms/rooms.module';
import { ClassesModule } from './classes/classes.module';
import { SchedulesModule } from './schedules/schedules.module';
import { UsersModule } from './users/users.module';
import { AuthModule } from './auth/auth.module';
import { EnrollmentsModule } from './enrollments/enrollments.module';

@Module({
  imports: [
    MongooseModule.forRoot('mongodb://localhost:27017/schedule-app'),
    SemestersModule,
    SubjectsModule,
    RoomsModule,
    ClassesModule,
    SchedulesModule,
    UsersModule,
    AuthModule,
    EnrollmentsModule,
  ],
})
export class AppModule {}
