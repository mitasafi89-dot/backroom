import {
  Controller,
  Post,
  Body,
  HttpCode,
  HttpStatus,
  Logger,
  UseGuards,
  Req,
} from '@nestjs/common';
import { ApiTags, ApiOperation, ApiResponse, ApiBearerAuth } from '@nestjs/swagger';
import { ConfigService } from '@nestjs/config';
import { MpesaService, MpesaCallbackData } from './mpesa.service';
import { InitiatePaymentDto, QueryPaymentDto } from './dto/mpesa.dto';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';

@ApiTags('mpesa')
@Controller('mpesa')
export class MpesaController {
  private readonly logger = new Logger(MpesaController.name);

  constructor(
    private readonly mpesaService: MpesaService,
    private readonly configService: ConfigService,
  ) {}

  /**
   * Initiate M-Pesa STK Push payment for subscription
   */
  @Post('pay')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @ApiOperation({ summary: 'Initiate M-Pesa payment for subscription' })
  @ApiResponse({ status: 200, description: 'STK Push initiated successfully' })
  @ApiResponse({ status: 400, description: 'Invalid phone number or payment failed' })
  @ApiResponse({ status: 401, description: 'Unauthorized' })
  async initiatePayment(@Body() dto: InitiatePaymentDto, @Req() req: any) {
    const userId = req.user?.id;

    // Get price based on plan
    const monthlyPrice = this.configService.get<number>('subscription.monthlyPrice') ?? 500;
    const yearlyPrice = this.configService.get<number>('subscription.yearlyPrice') ?? 5000;
    const amount = dto.plan === 'yearly' ? yearlyPrice : monthlyPrice;

    const response = await this.mpesaService.initiateSTKPush({
      phoneNumber: dto.phoneNumber,
      amount,
      accountReference: `BKRM${userId?.slice(0, 6) || 'SUB'}`,
      transactionDesc: `Backroom ${dto.plan}`,
    });

    return {
      success: true,
      message: 'Please check your phone and enter M-Pesa PIN',
      checkoutRequestId: response.CheckoutRequestID,
      merchantRequestId: response.MerchantRequestID,
      plan: dto.plan,
      amount,
    };
  }

  /**
   * Query payment status
   */
  @Post('query')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @ApiOperation({ summary: 'Check M-Pesa payment status' })
  @ApiResponse({ status: 200, description: 'Payment status retrieved' })
  async queryPaymentStatus(@Body() dto: QueryPaymentDto) {
    const status = await this.mpesaService.querySTKPushStatus(dto.checkoutRequestId);

    return {
      success: status.ResultCode === '0',
      resultCode: status.ResultCode,
      resultDesc: status.ResultDesc,
    };
  }

  /**
   * M-Pesa callback endpoint (called by Safaricom)
   * This must be publicly accessible (no auth)
   */
  @Post('callback')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'M-Pesa callback webhook (Safaricom)' })
  @ApiResponse({ status: 200, description: 'Callback processed' })
  async handleCallback(@Body() data: MpesaCallbackData) {
    this.logger.log('Received M-Pesa callback');

    const result = this.mpesaService.processCallback(data);

    if (result.success) {
      // Payment successful - activate subscription
      // TODO: Update user subscription in database
      this.logger.log(
        `Payment successful: CheckoutRequestID=${result.checkoutRequestId}, ` +
          `Amount=${result.metadata?.Amount}, Phone=${result.metadata?.PhoneNumber}`,
      );

      // Here you would:
      // 1. Look up the pending transaction by checkoutRequestId
      // 2. Update the user's subscription status
      // 3. Set subscription expiry date
      // 4. Send push notification to user
    } else {
      // Payment failed or cancelled
      this.logger.warn(
        `Payment failed: ${result.resultDesc} (Code: ${result.resultCode})`,
      );
    }

    // Safaricom expects a specific response format
    return {
      ResultCode: 0,
      ResultDesc: 'Callback received successfully',
    };
  }

  /**
   * M-Pesa timeout endpoint
   */
  @Post('timeout')
  @HttpCode(HttpStatus.OK)
  @ApiOperation({ summary: 'M-Pesa timeout webhook' })
  async handleTimeout(@Body() data: any) {
    this.logger.warn('M-Pesa request timed out', data);

    return {
      ResultCode: 0,
      ResultDesc: 'Timeout handled',
    };
  }
}

