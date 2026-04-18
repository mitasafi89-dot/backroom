/**
 * Backroom WebSocket Signaling Server
 *
 * Handles:
 * - Preview delivery to listeners
 * - WebRTC signaling (SDP/ICE exchange)
 * - Presence & availability management
 * - Match notifications
 */

const WebSocket = require('ws');
const { v4: uuidv4 } = require('uuid');

const PORT = process.env.WS_PORT || 8443;
const HOST = process.env.WS_HOST || '0.0.0.0'; // Listen on all interfaces

// In-memory storage (use Redis in production)
const clients = new Map(); // clientId -> { ws, role, available, userId }
const availableListeners = new Map(); // listenerId -> boundaries
const pendingShares = new Map(); // shareId -> shareData
const activeCalls = new Map(); // callId -> { sharerId, listenerId }

const wss = new WebSocket.Server({ host: HOST, port: PORT });

console.log(`🚀 Backroom Signaling Server running on ws://${HOST}:${PORT}`);

wss.on('connection', (ws) => {
  const clientId = uuidv4();
  clients.set(clientId, { ws, role: null, available: false, userId: null });

  console.log(`✅ Client connected: ${clientId}`);

  // Send welcome message with client ID
  send(ws, {
    type: 'connected',
    payload: { clientId }
  });

  ws.on('message', (data) => {
    try {
      const message = JSON.parse(data.toString());
      handleMessage(clientId, message);
    } catch (err) {
      console.error('Invalid message:', err);
      send(ws, { type: 'error', payload: { message: 'Invalid JSON' } });
    }
  });

  ws.on('close', () => {
    console.log(`❌ Client disconnected: ${clientId}`);

    // Remove from available listeners
    availableListeners.delete(clientId);

    // End any active calls
    for (const [callId, call] of activeCalls.entries()) {
      if (call.sharerId === clientId || call.listenerId === clientId) {
        const otherId = call.sharerId === clientId ? call.listenerId : call.sharerId;
        const other = clients.get(otherId);
        if (other?.ws?.readyState === WebSocket.OPEN) {
          send(other.ws, { type: 'call_ended', payload: { callId, reason: 'peer_disconnected' } });
        }
        activeCalls.delete(callId);
      }
    }

    clients.delete(clientId);
  });

  ws.on('error', (err) => {
    console.error(`WebSocket error for ${clientId}:`, err);
  });
});

function handleMessage(clientId, message) {
  const client = clients.get(clientId);
  if (!client) return;

  const { type, payload } = message;

  console.log(`📩 ${clientId.slice(0, 8)}: ${type}`);

  switch (type) {
    case 'ping':
      send(client.ws, { type: 'pong', payload: { timestamp: Date.now() } });
      break;

    case 'register':
      // Register user with their anonymous ID
      client.userId = payload.userId;
      client.role = payload.role; // 'sharer' or 'listener'
      console.log(`👤 Registered: ${payload.userId} as ${payload.role}`);
      break;

    case 'availability':
      // Listener toggles availability
      client.available = payload.available;
      if (payload.available) {
        availableListeners.set(clientId, {
          topics: payload.topics || [],
          maxIntensity: payload.maxIntensity || 'heavy',
          maxDuration: payload.maxDuration || 15
        });
        console.log(`🟢 Listener available: ${clientId.slice(0, 8)}`);

        // Check for pending shares that match
        matchPendingShares();
      } else {
        availableListeners.delete(clientId);
        console.log(`🔴 Listener unavailable: ${clientId.slice(0, 8)}`);
      }
      send(client.ws, { type: 'availability_confirmed', payload: { available: payload.available } });
      break;

    case 'share_request':
      // Sharer submits a share request
      const shareId = uuidv4();
      pendingShares.set(shareId, {
        sharerId: clientId,
        topic: payload.topic,
        tone: payload.tone,
        intent: payload.intent,
        duration: payload.duration,
        createdAt: Date.now()
      });
      console.log(`📝 Share request: ${shareId.slice(0, 8)} - ${payload.topic}`);

      send(client.ws, {
        type: 'share_submitted',
        payload: { shareId, status: 'searching' }
      });

      // Try to match immediately
      matchPendingShares();
      break;

    case 'preview_accept':
      // Listener accepts a preview
      handlePreviewAccept(clientId, payload.shareId);
      break;

    case 'preview_decline':
      // Listener declines a preview
      handlePreviewDecline(clientId, payload.shareId);
      break;

    case 'webrtc/offer':
    case 'webrtc/answer':
    case 'ice':
      // Forward WebRTC signaling to peer
      forwardToPeer(clientId, payload.callId, message);
      break;

    case 'mute_state':
      // Forward mute state to peer
      forwardToPeer(clientId, payload.callId, {
        type: 'remote_mute_state',
        payload: {
          callId: payload.callId,
          muted: payload.muted
        }
      });
      break;

    case 'end_call':
      handleEndCall(clientId, payload.callId);
      break;

    case 'cancel_share':
      // Sharer cancels their share request
      for (const [shareId, share] of pendingShares.entries()) {
        if (share.sharerId === clientId) {
          pendingShares.delete(shareId);
          console.log(`🚫 Share cancelled: ${shareId.slice(0, 8)}`);
        }
      }
      send(client.ws, { type: 'share_cancelled', payload: {} });
      break;

    default:
      console.warn(`Unknown message type: ${type}`);
  }
}

function matchPendingShares() {
  for (const [shareId, share] of pendingShares.entries()) {
    // Find a matching listener
    for (const [listenerId, boundaries] of availableListeners.entries()) {
      // Skip if same person
      if (listenerId === share.sharerId) continue;

      // Check topic match
      if (boundaries.topics.length > 0 && !boundaries.topics.includes(share.topic)) {
        continue;
      }

      // Check duration
      if (share.duration > boundaries.maxDuration) {
        continue;
      }

      // Found a match! Send preview to listener
      const listener = clients.get(listenerId);
      if (listener?.ws?.readyState === WebSocket.OPEN) {
        send(listener.ws, {
          type: 'incoming_preview',
          payload: {
            shareId,
            topic: share.topic,
            tone: share.tone,
            previewText: share.intent,
            durationMinutes: share.duration,
            countdownSeconds: 30
          }
        });
        console.log(`📤 Preview sent to ${listenerId.slice(0, 8)} for share ${shareId.slice(0, 8)}`);

        // Mark listener as busy with this preview
        availableListeners.delete(listenerId);

        // Store the pending match
        share.pendingListenerId = listenerId;

        // Only send to one listener at a time
        break;
      }
    }
  }
}

function handlePreviewAccept(listenerId, shareId) {
  const share = pendingShares.get(shareId);
  if (!share) {
    console.warn(`Share not found: ${shareId}`);
    return;
  }

  const sharer = clients.get(share.sharerId);
  const listener = clients.get(listenerId);

  if (!sharer || !listener) {
    console.warn('Sharer or listener not found');
    return;
  }

  // Create call
  const callId = uuidv4();
  activeCalls.set(callId, {
    sharerId: share.sharerId,
    listenerId: listenerId,
    startedAt: Date.now()
  });

  // Remove from pending
  pendingShares.delete(shareId);

  // TURN credentials (simplified for dev)
  const turnServers = [
    {
      urls: ['stun:stun.l.google.com:19302'],
    },
    {
      urls: ['turn:localhost:3478'],
      username: 'backroom',
      credential: 'backroom_turn_secret'
    }
  ];

  // Notify both parties
  send(sharer.ws, {
    type: 'match_made',
    payload: {
      callId,
      role: 'sharer',
      topic: share.topic,
      intent: share.intent,
      durationMinutes: share.duration,
      turnServers
    }
  });

  send(listener.ws, {
    type: 'match_made',
    payload: {
      callId,
      role: 'listener',
      topic: share.topic,
      intent: share.intent,
      durationMinutes: share.duration,
      turnServers
    }
  });

  console.log(`🎉 Match made! Call ${callId.slice(0, 8)}`);
}

function handlePreviewDecline(listenerId, shareId) {
  const share = pendingShares.get(shareId);
  if (share && share.pendingListenerId === listenerId) {
    delete share.pendingListenerId;
    // Try to match with another listener
    matchPendingShares();
  }

  // Make listener available again
  const listener = clients.get(listenerId);
  if (listener) {
    availableListeners.set(listenerId, listener.boundaries || {
      topics: [],
      maxIntensity: 'heavy',
      maxDuration: 15
    });
  }

  console.log(`👎 Preview declined by ${listenerId.slice(0, 8)}`);
}

function forwardToPeer(senderId, callId, message) {
  const call = activeCalls.get(callId);
  if (!call) {
    console.warn(`Call not found: ${callId}`);
    return;
  }

  const peerId = call.sharerId === senderId ? call.listenerId : call.sharerId;
  const peer = clients.get(peerId);

  if (peer?.ws?.readyState === WebSocket.OPEN) {
    send(peer.ws, message);
    console.log(`📡 Forwarded ${message.type} to ${peerId.slice(0, 8)}`);
  }
}

function handleEndCall(clientId, callId) {
  const call = activeCalls.get(callId);
  if (!call) return;

  const otherId = call.sharerId === clientId ? call.listenerId : call.sharerId;
  const other = clients.get(otherId);

  if (other?.ws?.readyState === WebSocket.OPEN) {
    send(other.ws, {
      type: 'call_ended',
      payload: { callId, reason: 'peer_ended' }
    });
  }

  activeCalls.delete(callId);
  console.log(`📴 Call ended: ${callId.slice(0, 8)}`);
}

function send(ws, message) {
  if (ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(message));
  }
}

// Cleanup stale shares every 30 seconds
setInterval(() => {
  const now = Date.now();
  for (const [shareId, share] of pendingShares.entries()) {
    // Remove shares older than 5 minutes
    if (now - share.createdAt > 5 * 60 * 1000) {
      const sharer = clients.get(share.sharerId);
      if (sharer?.ws?.readyState === WebSocket.OPEN) {
        send(sharer.ws, {
          type: 'share_expired',
          payload: { shareId, reason: 'timeout' }
        });
      }
      pendingShares.delete(shareId);
      console.log(`⏰ Share expired: ${shareId.slice(0, 8)}`);
    }
  }
}, 30000);

// Log stats every 60 seconds
setInterval(() => {
  console.log(`📊 Stats: ${clients.size} clients, ${availableListeners.size} listeners, ${pendingShares.size} pending, ${activeCalls.size} calls`);
}, 60000);

