import { Controller, Post, Get, Body, Param } from '@nestjs/common';
import { EnrollmentsService } from './enrollments.service';

@Controller('enrollments')
export class EnrollmentsController {
  constructor(private readonly enrollmentsService: EnrollmentsService) {}

  @Post()
  enroll(@Body() body: { userId: string; classId: string }) {
    return this.enrollmentsService.enrollClass(body.userId, body.classId);
  }

  @Get('user/:userId')
  getMySchedule(@Param('userId') userId: string) {
    return this.enrollmentsService.getMySchedule(userId);
  }
}
