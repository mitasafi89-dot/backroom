"use client";

import Link from "next/link";

export default function CrisisPill() {
  return (
    <div className="fixed bottom-5 left-1/2 -translate-x-1/2 z-50 md:hidden">
      <Link
        href="/crisis"
        className="crisis-pulse flex items-center gap-2 px-5 py-3 rounded-full glass border border-amber/30 text-amber text-sm font-medium"
        aria-label="I need immediate help — crisis resources"
      >
        <span className="relative flex h-2 w-2">
          <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-amber opacity-75" />
          <span className="relative inline-flex rounded-full h-2 w-2 bg-amber" />
        </span>
        I need immediate help
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
          <path d="M5 12h14M12 5l7 7-7 7" />
        </svg>
      </Link>
    </div>
  );
}

