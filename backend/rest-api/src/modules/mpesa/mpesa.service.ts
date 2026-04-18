import { Injectable, Logger, HttpException, HttpStatus } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { HttpService } from '@nestjs/axios';
import { firstValueFrom } from 'rxjs';

export interface MpesaSTKPushRequest {
  phoneNumber: string; // Format: 254XXXXXXXXX
  amount: number;
  accountReference: string;
  transactionDesc: string;
}

export interface MpesaSTKPushResponse {
  MerchantRequestID: string;
  CheckoutRequestID: string;
  ResponseCode: string;
  ResponseDescription: string;
  CustomerMessage: string;
}

export interface MpesaCallbackData {
  Body: {
    stkCallback: {
      MerchantRequestID: string;
      CheckoutRequestID: string;
      ResultCode: number;
      ResultDesc: string;
      CallbackMetadata?: {
        Item: Array<{
          Name: string;
          Value: string | number;
        }>;
      };
    };
  };
}

@Injectable()
export class MpesaService {
  private readonly logger = new Logger(MpesaService.name);
  private accessToken: string | null = null;
  private tokenExpiry: Date | null = null;

  private readonly baseUrl: string;
  private readonly consumerKey: string;
  private readonly consumerSecret: string;
  private readonly shortcode: string;
  private readonly passkey: string;
  private readonly callbackUrl: string;

  constructor(
    private readonly configService: ConfigService,
    private readonly httpService: HttpService,
  ) {
    const environment = this.configService.get<string>('mpesa.environment');
    this.baseUrl = environment === 'production'
      ? 'https://api.safaricom.co.ke'
      : 'https://sandbox.safaricom.co.ke';

    this.consumerKey = this.configService.get<string>('mpesa.consumerKey') ?? '';
    this.consumerSecret = this.configService.get<string>('mpesa.consumerSecret') ?? '';
    this.shortcode = this.configService.get<string>('mpesa.shortcode') ?? '';
    this.passkey = this.configService.get<string>('mpesa.passkey') ?? '';
    this.callbackUrl = this.configService.get<string>('mpesa.callbackUrl') ?? '';
  }

  /**
   * Get OAuth access token from Safaricom
   */
  private async getAccessToken(): Promise<string> {
    // Return cached token if still valid
    if (this.accessToken && this.tokenExpiry && new Date() < this.tokenExpiry) {
      return this.accessToken;
    }

    try {
      const auth = Buffer.from(`${this.consumerKey}:${this.consumerSecret}`).toString('base64');

      const response = await firstValueFrom(
        this.httpService.get(`${this.baseUrl}/oauth/v1/generate?grant_type=client_credentials`, {
          headers: {
            Authorization: `Basic ${auth}`,
          },
        }),
      );

      this.accessToken = response.data.access_token;
      // Token expires in 1 hour, we refresh 5 minutes early
      this.tokenExpiry = new Date(Date.now() + 55 * 60 * 1000);

      this.logger.log('M-Pesa access token refreshed');
      return this.accessToken!;
    } catch (error) {
      this.logger.error('Failed to get M-Pesa access token', error);
      throw new HttpException(
        'Payment service temporarily unavailable',
        HttpStatus.SERVICE_UNAVAILABLE,
      );
    }
  }

  /**
   * Generate password for STK Push
   */
  private generatePassword(): { password: string; timestamp: string } {
    const timestamp = new Date()
      .toISOString()
      .replace(/[^0-9]/g, '')
      .slice(0, 14); // Format: YYYYMMDDHHmmss

    const password = Buffer.from(`${this.shortcode}${this.passkey}${timestamp}`).toString('base64');

    return { password, timestamp };
  }

  /**
   * Format phone number to 254XXXXXXXXX format
   */
  private formatPhoneNumber(phone: string): string {
    // Remove any spaces, dashes, or plus signs
    let formatted = phone.replace(/[\s\-\+]/g, '');

    // Convert 07XXXXXXXX to 254XXXXXXXXX
    if (formatted.startsWith('07') || formatted.startsWith('01')) {
      formatted = '254' + formatted.slice(1);
    }

    // Convert 7XXXXXXXX to 254XXXXXXXXX
    if (formatted.startsWith('7') || formatted.startsWith('1')) {
      formatted = '254' + formatted;
    }

    // Validate format
    if (!/^254[17]\d{8}$/.test(formatted)) {
      throw new HttpException(
        'Invalid phone number format. Use 07XXXXXXXX or 254XXXXXXXXX',
        HttpStatus.BAD_REQUEST,
      );
    }

    return formatted;
  }

  /**
   * Initiate STK Push (Lipa Na M-Pesa Online)
   * This sends a payment prompt to the user's phone
   */
  async initiateSTKPush(request: MpesaSTKPushRequest): Promise<MpesaSTKPushResponse> {
    const accessToken = await this.getAccessToken();
    const { password, timestamp } = this.generatePassword();
    const phoneNumber = this.formatPhoneNumber(request.phoneNumber);

    const payload = {
      BusinessShortCode: this.shortcode,
      Password: password,
      Timestamp: timestamp,
      TransactionType: 'CustomerPayBillOnline',
      Amount: Math.round(request.amount), // M-Pesa only accepts whole numbers
      PartyA: phoneNumber,
      PartyB: this.shortcode,
      PhoneNumber: phoneNumber,
      CallBackURL: this.callbackUrl,
      AccountReference: request.accountReference.slice(0, 12), // Max 12 chars
      TransactionDesc: request.transactionDesc.slice(0, 13), // Max 13 chars
    };

    try {
      this.logger.log(`Initiating STK Push for ${phoneNumber}, Amount: ${request.amount}`);

      const response = await firstValueFrom(
        this.httpService.post(
          `${this.baseUrl}/mpesa/stkpush/v1/processrequest`,
          payload,
          {
            headers: {
              Authorization: `Bearer ${accessToken}`,
              'Content-Type': 'application/json',
            },
          },
        ),
      );

      this.logger.log(`STK Push initiated: ${response.data.CheckoutRequestID}`);
      return response.data;
    } catch (error) {
      this.logger.error('STK Push failed', error.response?.data || error.message);
      throw new HttpException(
        error.response?.data?.errorMessage || 'Payment initiation failed',
        HttpStatus.BAD_REQUEST,
      );
    }
  }

  /**
   * Query STK Push transaction status
   */
  async querySTKPushStatus(checkoutRequestId: string): Promise<any> {
    const accessToken = await this.getAccessToken();
    const { password, timestamp } = this.generatePassword();

    const payload = {
      BusinessShortCode: this.shortcode,
      Password: password,
      Timestamp: timestamp,
      CheckoutRequestID: checkoutRequestId,
    };

    try {
      const response = await firstValueFrom(
        this.httpService.post(
          `${this.baseUrl}/mpesa/stkpushquery/v1/query`,
          payload,
          {
            headers: {
              Authorization: `Bearer ${accessToken}`,
              'Content-Type': 'application/json',
            },
          },
        ),
      );

      return response.data;
    } catch (error) {
      this.logger.error('STK Query failed', error.response?.data || error.message);
      throw new HttpException(
        'Failed to query payment status',
        HttpStatus.BAD_REQUEST,
      );
    }
  }

  /**
   * Process callback from Safaricom
   */
  processCallback(data: MpesaCallbackData): {
    success: boolean;
    checkoutRequestId: string;
    merchantRequestId: string;
    resultCode: number;
    resultDesc: string;
    metadata?: Record<string, any>;
  } {
    const callback = data.Body.stkCallback;

    const result = {
      success: callback.ResultCode === 0,
      checkoutRequestId: callback.CheckoutRequestID,
      merchantRequestId: callback.MerchantRequestID,
      resultCode: callback.ResultCode,
      resultDesc: callback.ResultDesc,
      metadata: {} as Record<string, any>,
    };

    // Extract metadata if payment was successful
    if (callback.CallbackMetadata?.Item) {
      for (const item of callback.CallbackMetadata.Item) {
        result.metadata[item.Name] = item.Value;
      }
    }

    this.logger.log(
      `M-Pesa callback: ${result.success ? 'SUCCESS' : 'FAILED'} - ${result.resultDesc}`,
    );

    return result;
  }
}

