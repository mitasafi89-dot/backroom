"use client";

import { useMemo } from "react";
import { motion } from "framer-motion";

interface WaveformAvatarProps {
  seed?: number;
  size?: number;
  color?: string;
  breathing?: boolean;
}

/**
 * Algorithmically generated sound-wave circle avatar.
 * Each seed produces a unique pattern.
 */
export default function WaveformAvatar({
  seed = 42,
  size = 64,
  color = "#E6B86A",
  breathing = true,
}: WaveformAvatarProps) {
  const lines = useMemo(() => {
    const count = 32;
    const rng = (s: number) => {
      const x = Math.sin(s * 9301 + 49297) % 1;
      return x - Math.floor(x);
    };
    return Array.from({ length: count }, (_, i) => {
      const angle = (360 / count) * i;
      const h = 6 + rng(seed + i * 7) * 14;
      return { angle, h, delay: rng(seed + i * 13) * 2 };
    });
  }, [seed]);

  const r = size / 2 - 4;
  const cx = size / 2;
  const cy = size / 2;

  return (
    <svg
      width={size}
      height={size}
      viewBox={`0 0 ${size} ${size}`}
      aria-hidden="true"
      className="flex-shrink-0"
    >
      <circle cx={cx} cy={cy} r={r} fill="none" stroke={color} strokeWidth="0.5" opacity="0.2" />
      {lines.map((l, i) => {
        const rad = (l.angle * Math.PI) / 180;
        const x1 = cx + r * Math.cos(rad);
        const y1 = cy + r * Math.sin(rad);
        const x2 = cx + (r - l.h) * Math.cos(rad);
        const y2 = cy + (r - l.h) * Math.sin(rad);
        if (breathing) {
          return (
            <motion.line
              key={i}
              x1={x1}
              y1={y1}
              x2={x2}
              y2={y2}
              stroke={color}
              strokeWidth="1.2"
              strokeLinecap="round"
              animate={{ opacity: [0.3, 0.8, 0.3] }}
              transition={{
                duration: 2.5 + l.delay,
                repeat: Infinity,
                ease: "easeInOut",
                delay: l.delay,
              }}
            />
          );
        }
        return (
          <line
            key={i}
            x1={x1}
            y1={y1}
            x2={x2}
            y2={y2}
            stroke={color}
            strokeWidth="1.2"
            strokeLinecap="round"
            opacity="0.6"
          />
        );
      })}
    </svg>
  );
}

