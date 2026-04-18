"use client";

import { useEffect, useRef } from "react";

export default function CursorSpotlight() {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;

    const move = (e: MouseEvent) => {
      el.style.setProperty("--x", `${e.clientX}px`);
      el.style.setProperty("--y", `${e.clientY}px`);
      el.style.opacity = "1";
    };

    const leave = () => {
      el.style.opacity = "0";
    };

    window.addEventListener("mousemove", move);
    window.addEventListener("mouseleave", leave);
    return () => {
      window.removeEventListener("mousemove", move);
      window.removeEventListener("mouseleave", leave);
    };
  }, []);

  return (
    <div
      ref={ref}
      className="pointer-events-none fixed inset-0 z-40 opacity-0 transition-opacity duration-500 hidden md:block"
      style={{
        background:
          "radial-gradient(400px circle at var(--x, -100px) var(--y, -100px), rgba(230,184,106,0.04), transparent 70%)",
      }}
      aria-hidden="true"
    />
  );
}

