"use client";

import { useState, useEffect } from "react";
import { motion, AnimatePresence } from "framer-motion";
import Link from "next/link";

const navLinks = [
  { href: "#how-it-works", label: "How it Works" },
  { href: "#safety", label: "Safety" },
  { href: "/volunteer", label: "Volunteer" },
];

export default function Navbar() {
  const [scrolled, setScrolled] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 20);
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  return (
    <nav
      className={`fixed top-0 w-full z-50 transition-all duration-500 ${
        scrolled
          ? "glass border-b border-white/5"
          : "bg-transparent"
      }`}
      role="navigation"
      aria-label="Main navigation"
    >
      <div className="max-w-7xl mx-auto px-5 md:px-8 h-16 flex items-center justify-between">
        {/* Logo */}
        <Link href="/" className="flex items-center gap-2 group" aria-label="Backroom home">
          {/* Waveform logo mark */}
          <svg width="28" height="28" viewBox="0 0 28 28" fill="none" className="opacity-80 group-hover:opacity-100 transition-opacity">
            <circle cx="14" cy="14" r="12" stroke="#E6B86A" strokeWidth="1.5" opacity="0.4" />
            <circle cx="14" cy="14" r="7" stroke="#4A9B9B" strokeWidth="1.5" opacity="0.6" />
            <circle cx="14" cy="14" r="3" fill="#E6B86A" opacity="0.8" />
          </svg>
          <span className="text-lg font-semibold tracking-tight">
            <span className="text-amber">b</span>
            <span className="text-white/90">ackroom</span>
          </span>
        </Link>

        {/* Desktop links */}
        <div className="hidden md:flex items-center gap-8">
          {navLinks.map((link) => (
            <Link
              key={link.label}
              href={link.href}
              className="text-sm text-white/50 hover:text-white/90 transition-colors duration-300"
            >
              {link.label}
            </Link>
          ))}
          <Link
            href="#download"
            className="px-5 py-2 rounded-full text-sm font-medium bg-gradient-to-r from-amber to-amber-dark text-obsidian hover:shadow-[0_0_30px_rgba(230,184,106,0.3)] transition-all duration-300"
            aria-label="Open Backroom app"
          >
            Open Backroom
          </Link>
        </div>

        {/* Mobile menu button */}
        <button
          onClick={() => setMobileOpen(!mobileOpen)}
          className="md:hidden p-2"
          aria-label={mobileOpen ? "Close menu" : "Open menu"}
          aria-expanded={mobileOpen}
        >
          <div className="w-5 flex flex-col gap-1.5">
            <motion.div
              animate={mobileOpen ? { rotate: 45, y: 6 } : { rotate: 0, y: 0 }}
              className="w-full h-[1.5px] bg-white/60"
            />
            <motion.div
              animate={mobileOpen ? { opacity: 0 } : { opacity: 1 }}
              className="w-full h-[1.5px] bg-white/60"
            />
            <motion.div
              animate={mobileOpen ? { rotate: -45, y: -6 } : { rotate: 0, y: 0 }}
              className="w-full h-[1.5px] bg-white/60"
            />
          </div>
        </button>
      </div>

      {/* Mobile menu */}
      <AnimatePresence>
        {mobileOpen && (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: "auto" }}
            exit={{ opacity: 0, height: 0 }}
            className="md:hidden glass border-t border-white/5 overflow-hidden"
          >
            <div className="px-5 py-6 flex flex-col gap-4">
              {navLinks.map((link) => (
                <Link
                  key={link.label}
                  href={link.href}
                  onClick={() => setMobileOpen(false)}
                  className="text-white/60 hover:text-white transition-colors py-2"
                >
                  {link.label}
                </Link>
              ))}
              <Link
                href="#download"
                onClick={() => setMobileOpen(false)}
                className="mt-2 px-5 py-3 rounded-full text-center font-medium bg-gradient-to-r from-amber to-amber-dark text-obsidian"
              >
                Open Backroom
              </Link>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </nav>
  );
}

