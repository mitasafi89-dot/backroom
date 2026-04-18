import { IsString, IsOptional, IsNotEmpty } from 'class-validator';
import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';

export class AnonymousAuthDto {
  @ApiProperty({
    description: 'Unique device identifier',
    example: 'android-xxxx-yyyy-zzzz',
  })
  @IsString()
  @IsNotEmpty()
  deviceId: string;

  @ApiPropertyOptional({
    description: 'User locale',
    example: 'en',
    default: 'en',
  })
  @IsString()
  @IsOptional()
  locale?: string;
}

