import { Controller, Get, Post, Body, Param, Put, Delete } from '@nestjs/common';
import { SemestersService } from './semesters.service';

@Controller('semesters')
export class SemestersController {
  constructor(private readonly semestersService: SemestersService) {}

  @Post()
  create(@Body() createSemesterDto: any) {
    return this.semestersService.create(createSemesterDto);
  }

  @Get()
  findAll() {
    return this.semestersService.findAll();
  }

  @Get(':id')
  findOne(@Param('id') id: string) {
    return this.semestersService.findOne(id);
  }

  @Put(':id')
  update(@Param('id') id: string, @Body() updateSemesterDto: any) {
    return this.semestersService.update(id, updateSemesterDto);
  }

  @Delete(':id')
  remove(@Param('id') id: string) {
    return this.semestersService.remove(id);
  }
}
