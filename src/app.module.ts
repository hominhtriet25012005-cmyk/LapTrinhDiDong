import { Module } from '@nestjs/common';
import { MongooseModule } from '@nestjs/mongoose';
import { AppController } from './app.controller';
import { AppService } from './app.service';
import { SemestersModule } from './semesters/semesters.module';

@Module({
  imports: [
    // Kết nối tới MongoDB local
    MongooseModule.forRoot('mongodb://localhost:27017/schedule-app'),
    SemestersModule,
  ],
  controllers: [AppController],
  providers: [AppService],
})
export class AppModule {}
