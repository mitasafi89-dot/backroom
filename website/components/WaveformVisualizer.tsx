"use client";

import { motion } from "framer-motion";
import { useMemo } from "react";

/**
 * Two intersecting circular waveforms — amber (sharer) & teal (listener)
 * pulsing gently like sound-wave rings. Purely CSS + Framer Motion.
 */
export default function WaveformVisualizer() {
  /* Generate bar data once */
  const bars = useMemo(() => {
    const count = 48;
    return Array.from({ length: count }, (_, i) => ({
      angle: (360 / count) * i,
      h: 18 + Math.random() * 22,
      delay: Math.random() * 2,
    }));
  }, []);

  return (
    <div className="relative w-[320px] h-[320px] md:w-[420px] md:h-[420px]" aria-hidden="true">
      {/* Ambient glow behind */}
      <div className="absolute inset-0 rounded-full bg-amber/5 blur-[80px]" />

      {/* Outer ring — Amber (Sharer) */}
      <motion.div
        className="absolute inset-0"
        animate={{ rotate: 360 }}
        transition={{ duration: 120, repeat: Infinity, ease: "linear" }}
      >
        <svg viewBox="0 0 420 420" className="w-full h-full">
          <circle
            cx="210"
            cy="210"
            r="180"
            fill="none"
            stroke="#E6B86A"
            strokeWidth="1"
            opacity="0.15"
          />
          {bars.map((b, i) => {
            const rad = (b.angle * Math.PI) / 180;
            const r = 180;
            const cx = 210 + r * Math.cos(rad);
            const cy = 210 + r * Math.sin(rad);
            return (
              <motion.line
                key={`a-${i}`}
                x1={cx}
                y1={cy}
                x2={210 + (r - b.h) * Math.cos(rad)}
                y2={210 + (r - b.h) * Math.sin(rad)}
                stroke="#E6B86A"
                strokeWidth="1.5"
                strokeLinecap="round"
                opacity="0.5"
                animate={{ opacity: [0.3, 0.7, 0.3] }}
                transition={{
                  duration: 2.5 + b.delay,
                  repeat: Infinity,
                  ease: "easeInOut",
                  delay: b.delay,
                }}
              />
            );
          })}
        </svg>
      </motion.div>

      {/* Inner ring — Teal (Listener) */}
      <motion.div
        className="absolute inset-[60px] md:inset-[80px]"
        animate={{ rotate: -360 }}
        transition={{ duration: 90, repeat: Infinity, ease: "linear" }}
      >
        <svg viewBox="0 0 300 300" className="w-full h-full">
          <circle
            cx="150"
            cy="150"
            r="120"
            fill="none"
            stroke="#4A9B9B"
            strokeWidth="1"
            opacity="0.15"
          />
          {bars.slice(0, 36).map((b, i) => {
            const angle = (360 / 36) * i;
            const rad = (angle * Math.PI) / 180;
            const r = 120;
            const h = 14 + Math.random() * 18;
            return (
              <motion.line
                key={`t-${i}`}
                x1={150 + r * Math.cos(rad)}
                y1={150 + r * Math.sin(rad)}
                x2={150 + (r - h) * Math.cos(rad)}
                y2={150 + (r - h) * Math.sin(rad)}
                stroke="#4A9B9B"
                strokeWidth="1.5"
                strokeLinecap="round"
                opacity="0.5"
                animate={{ opacity: [0.25, 0.65, 0.25] }}
                transition={{
                  duration: 3 + b.delay,
                  repeat: Infinity,
                  ease: "easeInOut",
                  delay: b.delay * 0.7,
                }}
              />
            );
          })}
        </svg>
      </motion.div>

      {/* Centre breathing orb */}
      <motion.div
        className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-4 h-4 rounded-full bg-amber/60"
        animate={{ scale: [1, 1.6, 1], opacity: [0.6, 1, 0.6] }}
        transition={{ duration: 3, repeat: Infinity, ease: "easeInOut" }}
      />
    </div>
  );
}

