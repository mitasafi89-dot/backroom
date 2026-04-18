import { Controller, Post, Body, Get, UseGuards, Req } from '@nestjs/common';
import { ApiTags, ApiOperation, ApiResponse, ApiBearerAuth } from '@nestjs/swagger';
import { AuthService } from './auth.service';
import { AnonymousAuthDto } from './dto/auth.dto';
import { JwtAuthGuard } from './guards/jwt-auth.guard';

@ApiTags('auth')
@Controller('auth')
export class AuthController {
  constructor(private readonly authService: AuthService) {}

  /**
   * Authenticate anonymously with device ID
   */
  @Post('anonymous')
  @ApiOperation({ summary: 'Create or authenticate anonymous user' })
  @ApiResponse({ status: 200, description: 'Authentication successful' })
  @ApiResponse({ status: 400, description: 'Invalid input' })
  async authenticateAnonymous(@Body() dto: AnonymousAuthDto) {
    return this.authService.authenticateAnonymous(dto);
  }

  /**
   * Refresh access token
   */
  @Post('refresh')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @ApiOperation({ summary: 'Refresh access token' })
  @ApiResponse({ status: 200, description: 'Token refreshed' })
  @ApiResponse({ status: 401, description: 'Unauthorized' })
  async refreshToken(@Req() req: any) {
    return this.authService.refreshToken(req.user.id);
  }

  /**
   * Get current user info
   */
  @Get('me')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @ApiOperation({ summary: 'Get current user info' })
  @ApiResponse({ status: 200, description: 'User info retrieved' })
  @ApiResponse({ status: 401, description: 'Unauthorized' })
  async getCurrentUser(@Req() req: any) {
    return {
      id: req.user.id,
      anonymous: req.user.anonymous,
      subscriptionTier: req.user.subscriptionTier,
      createdAt: req.user.createdAt,
    };
  }

  /**
   * Get TURN credentials for WebRTC
   */
  @Get('turn-credentials')
  @UseGuards(JwtAuthGuard)
  @ApiBearerAuth()
  @ApiOperation({ summary: 'Get time-limited TURN credentials' })
  @ApiResponse({ status: 200, description: 'TURN credentials generated' })
  @ApiResponse({ status: 401, description: 'Unauthorized' })
  async getTurnCredentials(@Req() req: any) {
    return this.authService.generateTurnCredentials(req.user.id);
  }
}

