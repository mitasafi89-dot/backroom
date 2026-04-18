#!/bin/bash

# Backroom Real-Time Log Monitor
# ================================
# This script monitors both Android app logs and server logs in real-time

echo "=========================================="
echo "🔍 BACKROOM REAL-TIME LOG MONITOR"
echo "=========================================="
echo ""

# Find adb
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
if [ ! -f "$ADB" ]; then
    ADB="adb"
fi

# Check for connected devices
echo "📱 Checking for connected devices..."
DEVICES=$("$ADB" devices | grep -v "List" | grep -v "^$" | wc -l)

if [ "$DEVICES" -eq 0 ]; then
    echo "⚠️  No Android devices connected!"
    echo "   Please connect a device via USB or start an emulator."
    echo ""
    echo "📋 Server logs only:"
    echo "----------------------------------------"
    docker logs -f backroom-ws-signaling
else
    echo "✅ Found $DEVICES device(s)"
    echo ""
    echo "📋 Starting combined log monitoring..."
    echo "   Press Ctrl+C to stop"
    echo "----------------------------------------"
    echo ""

    # Clear old logs and start monitoring
    "$ADB" logcat -c

    # Monitor both app logs
    "$ADB" logcat -v time | grep -E "(ListenerService|MainActivity|CallManager|SignalingClient|HomeScreen|WaitingScreen|InCallScreen|WebRTCManager|BackroomApp)" --line-buffered
fi

