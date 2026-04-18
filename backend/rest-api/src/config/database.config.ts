import { ConfigService } from '@nestjs/config';
import { TypeOrmModuleOptions } from '@nestjs/typeorm';

export const databaseConfig = (
  configService: ConfigService,
): TypeOrmModuleOptions => ({
  type: 'postgres',
  host: configService.get<string>('database.host'),
  port: configService.get<number>('database.port'),
  username: configService.get<string>('database.username'),
  password: configService.get<string>('database.password'),
  database: configService.get<string>('database.database'),
  entities: [__dirname + '/../database/entities/*.entity{.ts,.js}'],
  synchronize: configService.get<string>('nodeEnv') === 'development', // Only for dev!
  logging: configService.get<string>('nodeEnv') === 'development',
  ssl: configService.get<string>('nodeEnv') === 'production'
    ? { rejectUnauthorized: false }
    : false,
});

