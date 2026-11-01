import { NestFactory } from '@nestjs/core';
import { AppModule } from './app.module';
import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';

async function bootstrap() {
  const app = await NestFactory.create(AppModule);

  // Bật CORS cho phép kết nối từ máy ảo Android & client
  app.enableCors();

  // Cấu hình Swagger UI
  const config = new DocumentBuilder()
    .setTitle('Schedule Backend API')
    .setDescription('Tài liệu API cho ứng dụng quản lý thời khóa biểu')
    .setVersion('1.0')
    .build();
    
  const document = SwaggerModule.createDocument(app, config);
  SwaggerModule.setup('api-docs', app, document);

  await app.listen(3000);
  console.log('Server is running on: http://localhost:3000/api-docs');
}
bootstrap();
