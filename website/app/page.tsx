"use client";

import { useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {
  Shield,
  Zap,
  Phone,
  Ear,
  Mic,
  MessageCircle,
  ChevronDown,
  Heart,
  Lock,
  Clock,
  Users,
  Star,
} from "lucide-react";
import Navbar from "@/components/Navbar";
import Footer from "@/components/Footer";
import CrisisPill from "@/components/CrisisPill";
import CursorSpotlight from "@/components/CursorSpotlight";
import WaveformVisualizer from "@/components/WaveformVisualizer";
import RoleSwitcher from "@/components/RoleSwitcher";
import WaveformAvatar from "@/components/WaveformAvatar";

/* ─── animation presets ─── */
const fadeUp = {
  hidden: { opacity: 0, y: 30 },
  visible: (i: number) => ({
    opacity: 1,
    y: 0,
    transition: { delay: i * 0.1, duration: 0.6, ease: "easeOut" as const },
  }),
};

const stagger = {
  hidden: {},
  visible: { transition: { staggerChildren: 0.12 } },
};

/* ─── main page ─── */
export default function Home() {
  const [role, setRole] = useState<"sharer" | "listener">("sharer");
  const [softPreviewOpen, setSoftPreviewOpen] = useState(false);

  const bgTint =
    role === "listener"
      ? "bg-gradient-to-b from-[#0d1214] via-obsidian to-obsidian"
      : "bg-gradient-to-b from-obsidian via-obsidian to-obsidian";

  return (
    <div className={`min-h-screen transition-colors duration-1000 ${bgTint}`}>
      <CursorSpotlight />
      <Navbar />
      <CrisisPill />

      {/* ═══════════════════════ HERO ═══════════════════════ */}
      <section className="relative min-h-screen flex items-center justify-center px-5 md:px-8 pt-20 overflow-hidden">
        {/* Ambient gradients */}
        <div className="absolute top-[10%] left-[20%] w-[500px] h-[500px] bg-amber/5 rounded-full blur-[140px] pointer-events-none" />
        <div className="absolute bottom-[10%] right-[15%] w-[400px] h-[400px] bg-teal/5 rounded-full blur-[120px] pointer-events-none" />

        <div className="relative max-w-7xl w-full mx-auto grid lg:grid-cols-2 gap-12 lg:gap-8 items-center">
          {/* Left — Copy */}
          <motion.div initial="hidden" animate="visible">
            <motion.div custom={0} variants={fadeUp} className="mb-6">
              <RoleSwitcher onChange={setRole} />
            </motion.div>

            <motion.h1
              custom={1}
              variants={fadeUp}
              className="text-4xl sm:text-5xl lg:text-6xl font-bold tracking-tight leading-[1.1] mb-6"
            >
              Because silence is heavy,
              <br />
              <span className="text-amber">but words can be light.</span>
            </motion.h1>

            <motion.p
              custom={2}
              variants={fadeUp}
              className="text-lg text-white/50 max-w-lg mb-4 leading-relaxed"
            >
              Anonymous, consent-first voice support. No profiles. No history.
              Just a moment of connection.
            </motion.p>

            <motion.p
              custom={3}
              variants={fadeUp}
              className="font-serif italic text-white/30 text-base mb-10"
            >
              &ldquo;Your voice is modified. Your identity is hidden.&rdquo;
            </motion.p>

            <motion.div
              custom={4}
              variants={fadeUp}
              className="flex flex-col sm:flex-row gap-4"
            >
              <div className="relative">
                <a
                  href="#download"
                  className="inline-flex items-center gap-2 px-8 py-4 rounded-full font-semibold text-lg bg-gradient-to-r from-amber to-amber-dark text-obsidian hover:shadow-[0_0_40px_rgba(230,184,106,0.25)] transition-all duration-300"
                  aria-label="Download Backroom for Android"
                  onMouseEnter={() => setSoftPreviewOpen(true)}
                  onMouseLeave={() => setSoftPreviewOpen(false)}
                >
                  Get the App
                </a>

                {/* Soft-preview toast */}
                <AnimatePresence>
                  {softPreviewOpen && (
                    <motion.div
                      initial={{ opacity: 0, y: 10, scale: 0.95 }}
                      animate={{ opacity: 1, y: 0, scale: 1 }}
                      exit={{ opacity: 0, y: 10, scale: 0.95 }}
                      transition={{ duration: 0.25 }}
                      className="absolute left-0 top-full mt-3 w-[320px] glass rounded-2xl p-4 text-sm text-white/60 leading-relaxed z-20 hidden md:block"
                    >
                      <p className="text-white/80 font-medium mb-1">
                        ⚡ Soft Preview
                      </p>
                      You&apos;ll hear a 3-second muted preview of the
                      listener&apos;s environment before connecting. No voices
                      revealed yet.
                    </motion.div>
                  )}
                </AnimatePresence>
              </div>

              <a
                href="#how-it-works"
                className="inline-flex items-center gap-2 px-8 py-4 rounded-full font-semibold text-lg border border-white/10 hover:border-white/20 text-white/70 hover:text-white transition-all duration-300"
              >
                How It Works
              </a>
            </motion.div>
          </motion.div>

          {/* Right — Waveform visualizer */}
          <motion.div
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ duration: 1, delay: 0.3, ease: "easeOut" }}
            className="flex justify-center lg:justify-end"
          >
            <WaveformVisualizer />
          </motion.div>
        </div>

        {/* Scroll hint */}
        <motion.div
          animate={{ y: [0, 10, 0] }}
          transition={{ repeat: Infinity, duration: 2.5 }}
          className="absolute bottom-8 left-1/2 -translate-x-1/2"
        >
          <ChevronDown className="w-5 h-5 text-white/20" />
        </motion.div>
      </section>

      {/* ═══════════════════ HOW IT WORKS — Bento Grid ═══════════════════ */}
      <section id="how-it-works" className="py-28 md:py-36 px-5 md:px-8">
        <div className="max-w-7xl mx-auto">
          <motion.div
            initial="hidden"
            whileInView="visible"
            viewport={{ once: true, margin: "-80px" }}
            className="text-center mb-16 md:mb-20"
          >
            <motion.p
              custom={0}
              variants={fadeUp}
              className="text-amber text-sm font-medium tracking-widest uppercase mb-4"
            >
              How it Works
            </motion.p>
            <motion.h2
              custom={1}
              variants={fadeUp}
              className="text-3xl sm:text-4xl md:text-5xl font-bold mb-4"
            >
              Three steps to human connection
            </motion.h2>
            <motion.p
              custom={2}
              variants={fadeUp}
              className="text-white/40 text-lg max-w-md mx-auto"
            >
              No sign-up, no profile, no trace.
            </motion.p>
          </motion.div>

          {/* Bento tiles */}
          <motion.div
            initial="hidden"
            whileInView="visible"
            viewport={{ once: true }}
            variants={stagger}
            className="grid md:grid-cols-3 gap-5"
          >
            {/* Tile 1 — Choose Role */}
            <motion.div
              variants={fadeUp}
              custom={0}
              className="glass glass-hover rounded-3xl p-8 relative overflow-hidden group"
            >
              <div className="absolute top-4 right-5 text-6xl font-bold text-white/[0.03]">
                01
              </div>
              <div className="flex gap-4 mb-6">
                <div className="w-12 h-12 rounded-2xl bg-amber/10 flex items-center justify-center">
                  <Mic className="w-5 h-5 text-amber" />
                </div>
                <div className="w-12 h-12 rounded-2xl bg-teal/10 flex items-center justify-center">
                  <Ear className="w-5 h-5 text-teal" />
                </div>
              </div>
              <h3 className="text-xl font-semibold mb-2">Choose Your Role</h3>
              <p className="text-white/40 text-sm leading-relaxed">
                Tap <span className="text-amber">&quot;I want to Share&quot;</span>{" "}
                or{" "}
                <span className="text-teal">&quot;I want to Listen.&quot;</span>{" "}
                No account needed. Choose your topic and intensity level.
              </p>
            </motion.div>

            {/* Tile 2 — Voice Masking */}
            <motion.div
              variants={fadeUp}
              custom={1}
              className="glass glass-hover rounded-3xl p-8 relative overflow-hidden"
            >
              <div className="absolute top-4 right-5 text-6xl font-bold text-white/[0.03]">
                02
              </div>
              <div className="mb-6">
                <VoiceMaskToggle />
              </div>
              <h3 className="text-xl font-semibold mb-2">Voice Masking</h3>
              <p className="text-white/40 text-sm leading-relaxed">
                Your voice is anonymized in real-time, at the source. The
                listener hears you — but can never identify you. Mandatory, not
                optional.
              </p>
            </motion.div>

            {/* Tile 3 — Soft Preview */}
            <motion.div
              variants={fadeUp}
              custom={2}
              className="glass glass-hover rounded-3xl p-8 relative overflow-hidden"
            >
              <div className="absolute top-4 right-5 text-6xl font-bold text-white/[0.03]">
                03
              </div>
              <div className="mb-6 glass rounded-2xl p-4 max-w-[240px]">
                <div className="flex items-center gap-2 mb-2">
                  <WaveformAvatar seed={7} size={28} color="#4A9B9B" breathing={false} />
                  <span className="text-xs text-teal">Listener ready</span>
                </div>
                <p className="text-xs text-white/50 leading-relaxed">
                  <MessageCircle className="inline w-3 h-3 mr-1 opacity-40" />
                  Quiet room · Topic: Venting · 10 min
                </p>
              </div>
              <h3 className="text-xl font-semibold mb-2">Soft Preview</h3>
              <p className="text-white/40 text-sm leading-relaxed">
                Before connecting, you see the listener&apos;s environment cues
                — not their words or voice. Consent-first, always.
              </p>
            </motion.div>
          </motion.div>
        </div>
      </section>

      {/* ═══════════════════ SAFETY CORNER ═══════════════════ */}
      <section id="safety" className="py-28 md:py-36 px-5 md:px-8 relative">
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[600px] bg-teal/[0.04] rounded-full blur-[160px] pointer-events-none" />

        <div className="relative max-w-7xl mx-auto">
          <motion.div
            initial="hidden"
            whileInView="visible"
            viewport={{ once: true, margin: "-80px" }}
            className="text-center mb-16 md:mb-20"
          >
            <motion.p
              custom={0}
              variants={fadeUp}
              className="text-teal text-sm font-medium tracking-widest uppercase mb-4"
            >
              Safety
            </motion.p>
            <motion.h2
              custom={1}
              variants={fadeUp}
              className="text-3xl sm:text-4xl md:text-5xl font-bold mb-4"
            >
              Designed for the nervous system.
            </motion.h2>
            <motion.p
              custom={2}
              variants={fadeUp}
              className="text-white/40 text-lg max-w-xl mx-auto font-serif italic"
            >
              Every feature exists to make you feel safer — not to extract data,
              not to build a profile, not to sell you anything.
            </motion.p>
          </motion.div>

          <motion.div
            initial="hidden"
            whileInView="visible"
            viewport={{ once: true }}
            variants={stagger}
            className="grid md:grid-cols-3 gap-5"
          >
            {[
              {
                icon: Shield,
                color: "teal",
                title: "Mandatory Anonymization",
                desc: "Voice is filtered at the source before transmission. There is no toggle to turn it off. Your identity stays yours.",
              },
              {
                icon: Zap,
                color: "amber",
                title: "Soft Preview",
                desc: "You hear their vibe — ambient room tone — not their words. 3 seconds of environmental preview before any voice is shared.",
              },
              {
                icon: Phone,
                color: "teal",
                title: "1-Tap Crisis Link",
                desc: "Always visible, never buried in a menu. Connects directly to local crisis lines and professional help.",
              },
            ].map((item, i) => (
              <motion.div
                key={item.title}
                variants={fadeUp}
                custom={i}
                className="glass glass-hover rounded-3xl p-8"
              >
                <div
                  className={`w-12 h-12 rounded-2xl mb-6 flex items-center justify-center ${
                    item.color === "teal" ? "bg-teal/10" : "bg-amber/10"
                  }`}
                >
                  <item.icon
                    className={`w-5 h-5 ${
                      item.color === "teal" ? "text-teal" : "text-amber"
                    }`}
                  />
                </div>
                <h3 className="text-xl font-semibold mb-2">{item.title}</h3>
                <p className="text-white/40 text-sm leading-relaxed">
                  {item.desc}
                </p>
              </motion.div>
            ))}
          </motion.div>

          <motion.div
            initial="hidden"
            whileInView="visible"
            viewport={{ once: true }}
            variants={stagger}
            className="grid grid-cols-2 md:grid-cols-4 gap-4 mt-6"
          >
            {[
              { icon: Lock, label: "No Personal Data", sub: "Zero profiles" },
              { icon: Clock, label: "Controlled Duration", sub: "5 / 10 / 15 min" },
              { icon: Users, label: "Block & Report", sub: "One tap" },
              { icon: Star, label: "Consent First", sub: "Preview before connect" },
            ].map((item, i) => (
              <motion.div
                key={item.label}
                variants={fadeUp}
                custom={i}
                className="glass glass-hover rounded-2xl p-5 text-center"
              >
                <item.icon className="w-5 h-5 text-white/30 mx-auto mb-2" />
                <p className="text-sm font-medium">{item.label}</p>
                <p className="text-xs text-white/30 mt-1">{item.sub}</p>
              </motion.div>
            ))}
          </motion.div>
        </div>
      </section>

      {/* ═══════════════════ TESTIMONIALS ═══════════════════ */}
      <section className="py-28 md:py-36 px-5 md:px-8">
        <div className="max-w-7xl mx-auto">
          <motion.div
            initial="hidden"
            whileInView="visible"
            viewport={{ once: true, margin: "-80px" }}
            className="text-center mb-16"
          >
            <motion.h2
              custom={0}
              variants={fadeUp}
              className="text-3xl sm:text-4xl font-bold mb-4"
            >
              Real moments. Anonymous voices.
            </motion.h2>
            <motion.p custom={1} variants={fadeUp} className="text-white/40 text-lg">
              What it feels like, in their own words.
            </motion.p>
          </motion.div>

          <motion.div
            initial="hidden"
            whileInView="visible"
            viewport={{ once: true }}
            variants={stagger}
            className="grid md:grid-cols-3 gap-5"
          >
            {[
              {
                quote:
                  "I hadn\u2019t talked to anyone about it in months. 10 minutes with a stranger changed something. I still don\u2019t know who they were.",
                seed: 12,
                color: "#E6B86A",
              },
              {
                quote:
                  "As a listener, it\u2019s not about fixing anyone. Sometimes people just need to know there\u2019s a warm body on the other end of the silence.",
                seed: 47,
                color: "#4A9B9B",
              },
              {
                quote:
                  "I was scared to call a hotline. This felt different \u2014 no judgment, no forms, no follow-up. Just a voice in the dark.",
                seed: 83,
                color: "#E6B86A",
              },
            ].map((t, i) => (
              <motion.div
                key={i}
                variants={fadeUp}
                custom={i}
                className="glass rounded-3xl p-8"
              >
                <WaveformAvatar seed={t.seed} size={48} color={t.color} breathing={false} />
                <p className="font-serif italic text-white/50 leading-relaxed mt-5 text-[15px]">
                  &ldquo;{t.quote}&rdquo;
                </p>
              </motion.div>
            ))}
          </motion.div>
        </div>
      </section>

      {/* ═══════════════════ FAQ ═══════════════════ */}
      <section id="faq" className="py-28 md:py-36 px-5 md:px-8">
        <div className="max-w-3xl mx-auto">
          <motion.div
            initial="hidden"
            whileInView="visible"
            viewport={{ once: true, margin: "-80px" }}
            className="text-center mb-16"
          >
            <motion.h2
              custom={0}
              variants={fadeUp}
              className="text-3xl sm:text-4xl font-bold mb-4"
            >
              Questions? Good.
            </motion.h2>
            <motion.p custom={1} variants={fadeUp} className="text-white/40 text-lg">
              Safety starts with understanding.
            </motion.p>
          </motion.div>

          <motion.div
            initial="hidden"
            whileInView="visible"
            viewport={{ once: true }}
            variants={stagger}
          >
            {faqs.map((faq, i) => (
              <FAQItem key={i} q={faq.q} a={faq.a} i={i} />
            ))}
          </motion.div>
        </div>
      </section>

      {/* ═══════════════════ DOWNLOAD CTA ═══════════════════ */}
      <section id="download" className="py-28 md:py-36 px-5 md:px-8 relative">
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[500px] h-[500px] bg-amber/[0.05] rounded-full blur-[160px] pointer-events-none" />

        <motion.div
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true }}
          className="relative max-w-3xl mx-auto text-center"
        >
          <motion.h2
            custom={0}
            variants={fadeUp}
            className="text-4xl sm:text-5xl font-bold mb-6"
          >
            Ready to talk?
          </motion.h2>
          <motion.p
            custom={1}
            variants={fadeUp}
            className="text-white/40 text-lg mb-10 max-w-md mx-auto"
          >
            Download Backroom and connect with someone who will listen.
            Available on Android.
          </motion.p>
          <motion.div custom={2} variants={fadeUp} className="flex flex-col items-center gap-4">
            <a
              href="#"
              className="inline-flex items-center gap-3 px-10 py-5 rounded-full bg-gradient-to-r from-amber to-amber-dark text-obsidian font-semibold text-lg hover:shadow-[0_0_50px_rgba(230,184,106,0.3)] transition-all duration-300"
              aria-label="Get Backroom on Google Play"
            >
              <svg viewBox="0 0 24 24" className="w-6 h-6 fill-current">
                <path d="M3.609 1.814 13.793 12 3.61 22.186a.996.996 0 0 1-.61-.92V2.734a1 1 0 0 1 .609-.92zm10.89 10.893 2.302 2.302-10.937 6.333 8.635-8.635zM18.8 11.15l2.807 1.627a1 1 0 0 1 0 1.726l-2.808 1.626L16.13 12l2.67-2.85zM5.863 3.45 16.8 9.784l-2.302 2.302L5.863 3.45z" />
              </svg>
              Get it on Google Play
            </a>
            <p className="text-white/30 text-sm">Coming soon to Google Play Store</p>
          </motion.div>
        </motion.div>
      </section>

      <Footer />

      {/* JSON-LD Structured Data */}
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{
          __html: JSON.stringify({
            "@context": "https://schema.org",
            "@type": "MobileApplication",
            name: "Backroom",
            operatingSystem: "Android",
            applicationCategory: "HealthApplication",
            description:
              "Free anonymous voice support for anxiety, depression, loneliness, and life struggles.",
            offers: { "@type": "Offer", price: "0", priceCurrency: "USD" },
          }),
        }}
      />
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{
          __html: JSON.stringify({
            "@context": "https://schema.org",
            "@type": "FAQPage",
            mainEntity: faqs.map((f) => ({
              "@type": "Question",
              name: f.q,
              acceptedAnswer: { "@type": "Answer", text: f.a },
            })),
          }),
        }}
      />
    </div>
  );
}

/* ─── Sub-components ─── */

function VoiceMaskToggle() {
  const [masked, setMasked] = useState(true);
  return (
    <button
      onClick={() => setMasked(!masked)}
      className="flex items-center gap-3 text-sm"
      aria-label="Toggle voice mask demo"
    >
      <span className={`transition-colors ${masked ? "text-white/30" : "text-white/70"}`}>
        Real Voice
      </span>
      <div className="relative w-12 h-6 rounded-full bg-white/10 cursor-pointer">
        <motion.div
          className="absolute top-0.5 w-5 h-5 rounded-full"
          animate={{
            left: masked ? 26 : 2,
            backgroundColor: masked ? "#4A9B9B" : "#E6B86A",
          }}
          transition={{ type: "spring", stiffness: 400, damping: 30 }}
        />
      </div>
      <span className={`transition-colors ${masked ? "text-teal" : "text-white/30"}`}>
        Modified
      </span>
    </button>
  );
}

function FAQItem({ q, a, i }: { q: string; a: string; i: number }) {
  const [open, setOpen] = useState(false);
  return (
    <motion.div variants={fadeUp} custom={i} className="border-b border-white/5">
      <button
        onClick={() => setOpen(!open)}
        className="w-full flex items-center justify-between py-5 text-left group"
        aria-expanded={open}
      >
        <span className="text-[15px] font-medium text-white/80 group-hover:text-white transition-colors pr-4">
          {q}
        </span>
        <motion.div animate={{ rotate: open ? 180 : 0 }} transition={{ duration: 0.2 }}>
          <ChevronDown className="w-4 h-4 text-white/30 flex-shrink-0" />
        </motion.div>
      </button>
      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: "auto", opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.3 }}
            className="overflow-hidden"
          >
            <p className="text-sm text-white/40 leading-relaxed pb-5 pr-8">{a}</p>
          </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  );
}

/* ─── FAQ Data ─── */
const faqs = [
  {
    q: "Is Backroom really anonymous?",
    a: "Yes. There are no accounts, no email addresses, no phone numbers, and no profile pictures. Your voice is anonymized in real-time using voice masking technology before it leaves your device. We store zero personal data.",
  },
  {
    q: "Is this free? What\u2019s the catch?",
    a: "The core experience \u2014 anonymous voice support \u2014 is completely free. There\u2019s no catch. An optional Backroom Plus subscription unlocks longer calls and extra features, but venting anonymously is always free.",
  },
  {
    q: "I can\u2019t afford therapy. Is this a replacement?",
    a: "Backroom is NOT therapy and not a replacement for professional mental health treatment. It\u2019s peer support \u2014 a real human who will listen without judgment. If you need professional help, we always provide crisis resources.",
  },
  {
    q: "What if I\u2019m in crisis right now?",
    a: "Please use our 1-Tap Crisis Link \u2014 it\u2019s always visible on every screen. It connects you directly to professional crisis helplines including: 988 Suicide & Crisis Lifeline (US), Crisis Text Line (text HOME to 741741), Befrienders Kenya (+254 722 178 177), and Samaritans (116 123, UK).",
  },
  {
    q: "How is this different from calling a hotline?",
    a: "Hotlines are staffed by trained counselors for emergencies. Backroom is peer-to-peer \u2014 you\u2019re talking to another regular human who volunteered to listen. No forms, no follow-up calls, no case files.",
  },
  {
    q: "What does \u2018Soft Preview\u2019 mean?",
    a: "Before a voice call connects, both parties see a brief environmental preview \u2014 ambient background info like \u2018quiet room, topic: venting, 10 minutes.\u2019 You never hear the other person\u2019s actual voice until you both consent.",
  },
  {
    q: "Can the listener figure out who I am?",
    a: "No. Voice masking is mandatory and happens on your device before any audio is transmitted. There are no names, locations, or identifying information shared. Listeners see only a unique waveform avatar.",
  },
  {
    q: "How do I volunteer as a listener?",
    a: "Open the app and switch to \u2018Listener\u2019 mode. You\u2019ll see incoming Soft Previews from people who want to talk. Choose which conversations to accept based on topic, intensity, and duration.",
  },
  {
    q: "What topics can I talk about?",
    a: "Anything weighing on you: anxiety, loneliness, relationship problems, breakups, grief, work burnout, family issues, identity questions, or just \u2018I had a bad day and need to vent.\u2019",
  },
  {
    q: "Is my conversation recorded?",
    a: "No. Calls are never recorded, transcribed, or stored. When the call ends, the data is gone. There is no call history, no chat logs, and no way to replay a conversation.",
  },
];
