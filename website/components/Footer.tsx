"use client";

import Link from "next/link";
import WaveformAvatar from "./WaveformAvatar";

const footerLinks = {
  Product: [
    { label: "How it Works", href: "#how-it-works" },
    { label: "Safety", href: "#safety" },
    { label: "Download", href: "#download" },
  ],
  Community: [
    { label: "Volunteer", href: "/volunteer" },
    { label: "FAQ", href: "/faq" },
    { label: "Crisis Resources", href: "/crisis" },
  ],
  Legal: [
    { label: "Privacy Policy", href: "/privacy" },
    { label: "Terms of Service", href: "/terms" },
    { label: "Cookie Policy", href: "/privacy#cookies" },
  ],
};

export default function Footer() {
  return (
    <footer className="border-t border-white/5 bg-obsidian-light/50" role="contentinfo">
      <div className="max-w-7xl mx-auto px-5 md:px-8 py-16">
        <div className="grid grid-cols-2 md:grid-cols-4 gap-10 mb-14">
          {/* Brand column */}
          <div className="col-span-2 md:col-span-1">
            <div className="flex items-center gap-2 mb-4">
              <WaveformAvatar seed={1} size={32} color="#E6B86A" breathing={false} />
              <span className="text-lg font-semibold tracking-tight">
                <span className="text-amber">b</span>ackroom
              </span>
            </div>
            <p className="text-sm text-white/40 leading-relaxed max-w-[240px]">
              Anonymous, consent-first voice support. No profiles. No history.
              Just a moment of connection.
            </p>
          </div>

          {/* Link columns */}
          {Object.entries(footerLinks).map(([heading, links]) => (
            <div key={heading}>
              <h3 className="text-xs font-semibold uppercase tracking-wider text-white/30 mb-4">
                {heading}
              </h3>
              <ul className="space-y-3">
                {links.map((l) => (
                  <li key={l.label}>
                    <Link
                      href={l.href}
                      className="text-sm text-white/50 hover:text-white transition-colors"
                    >
                      {l.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>

        {/* Bottom bar */}
        <div className="flex flex-col md:flex-row items-center justify-between gap-4 pt-8 border-t border-white/5">
          <p className="text-xs text-white/30">
            © {new Date().getFullYear()} Backroom. Built in Kenya 🇰🇪 — For
            everyone, everywhere.
          </p>
          <div className="flex items-center gap-3 text-xs text-white/30">
            <span>Not a crisis line.</span>
            <Link
              href="/crisis"
              className="text-amber/70 hover:text-amber transition-colors"
            >
              Find crisis resources →
            </Link>
          </div>
        </div>
      </div>
    </footer>
  );
}

