import { Module } from '@nestjs/common';
import { MpesaModule } from '../mpesa/mpesa.module';

// Subscriptions module - manages subscription state
// Integrates with M-Pesa for payments
@Module({
  imports: [MpesaModule],
  controllers: [],
  providers: [],
  exports: [],
})
export class SubscriptionsModule {}

