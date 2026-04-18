"use client";

import { motion } from "framer-motion";
import { Shield, Mic, Heart, Users, Clock, Lock, ChevronDown, Star } from "lucide-react";

const fadeUp = {
  hidden: { opacity: 0, y: 30 },
  visible: (i: number) => ({
    opacity: 1,
    y: 0,
    transition: { delay: i * 0.1, duration: 0.6, ease: "easeOut" },
  }),
};

export default function Home() {
  return (
    <div className="min-h-screen bg-[#0a0a0a] text-white">
      {/* Nav */}
      <nav className="fixed top-0 w-full z-50 bg-[#0a0a0a]/80 backdrop-blur-xl border-b border-white/5">
        <div className="max-w-6xl mx-auto px-6 h-16 flex items-center justify-between">
          <span className="text-xl font-bold tracking-tight">
            <span className="text-purple-400">b</span>ackroom
          </span>
          <a
            href="#download"
            className="px-5 py-2 rounded-full bg-purple-600 hover:bg-purple-500 text-sm font-medium transition-colors"
          >
            Get the App
          </a>
        </div>
      </nav>

      {/* Hero */}
      <section className="relative min-h-screen flex items-center justify-center px-6 overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-b from-purple-900/20 via-transparent to-transparent" />
        <div className="absolute top-1/4 left-1/2 -translate-x-1/2 w-[600px] h-[600px] bg-purple-600/10 rounded-full blur-[120px]" />

        <motion.div
          initial="hidden"
          animate="visible"
          className="relative text-center max-w-3xl"
        >
          <motion.p
            custom={0}
            variants={fadeUp}
            className="text-purple-400 text-sm font-medium tracking-widest uppercase mb-6"
          >
            Anonymous Voice Support
          </motion.p>
          <motion.h1
            custom={1}
            variants={fadeUp}
            className="text-5xl md:text-7xl font-bold tracking-tight leading-tight mb-6"
          >
            Talk to someone
            <br />
            <span className="text-purple-400">who cares.</span>
          </motion.h1>
          <motion.p
            custom={2}
            variants={fadeUp}
            className="text-lg md:text-xl text-zinc-400 max-w-xl mx-auto mb-10"
          >
            Anonymous, safe, two-way voice conversations with real people.
            No judgement. No identity. Just human connection.
          </motion.p>
          <motion.div custom={3} variants={fadeUp} className="flex flex-col sm:flex-row gap-4 justify-center">
            <a
              href="#download"
              className="px-8 py-4 rounded-full bg-purple-600 hover:bg-purple-500 font-semibold text-lg transition-colors"
            >
              Download for Android
            </a>
            <a
              href="#how-it-works"
              className="px-8 py-4 rounded-full border border-white/10 hover:border-white/20 font-semibold text-lg transition-colors"
            >
              How It Works
            </a>
          </motion.div>
        </motion.div>

        <motion.div
          animate={{ y: [0, 10, 0] }}
          transition={{ repeat: Infinity, duration: 2 }}
          className="absolute bottom-10"
        >
          <ChevronDown className="w-6 h-6 text-zinc-500" />
        </motion.div>
      </section>

      {/* How It Works */}
      <section id="how-it-works" className="py-32 px-6">
        <div className="max-w-6xl mx-auto">
          <motion.div
            initial="hidden"
            whileInView="visible"
            viewport={{ once: true, margin: "-100px" }}
            className="text-center mb-20"
          >
            <motion.h2 custom={0} variants={fadeUp} className="text-4xl md:text-5xl font-bold mb-4">
              How It Works
            </motion.h2>
            <motion.p custom={1} variants={fadeUp} className="text-zinc-400 text-lg max-w-md mx-auto">
              Three simple steps to connect with someone
            </motion.p>
          </motion.div>

          <div className="grid md:grid-cols-3 gap-8">
            {[
              {
                step: "01",
                title: "Share What's on Your Mind",
                desc: "Choose a topic, set the tone, and write a short preview of what you want to talk about.",
                icon: Mic,
              },
              {
                step: "02",
                title: "Get Matched",
                desc: "A volunteer listener sees your preview and accepts. You're connected in seconds.",
                icon: Users,
              },
              {
                step: "03",
                title: "Talk Freely",
                desc: "Have a two-way anonymous voice conversation. Both voices are anonymized for safety.",
                icon: Heart,
              },
            ].map((item, i) => (
              <motion.div
                key={item.step}
                initial="hidden"
                whileInView="visible"
                viewport={{ once: true }}
                custom={i}
                variants={fadeUp}
                className="relative p-8 rounded-2xl bg-white/[0.03] border border-white/[0.06] hover:border-purple-500/30 transition-colors"
              >
                <span className="text-purple-400/30 text-6xl font-bold absolute top-4 right-6">
                  {item.step}
                </span>
                <item.icon className="w-10 h-10 text-purple-400 mb-4" />
                <h3 className="text-xl font-semibold mb-2">{item.title}</h3>
                <p className="text-zinc-400">{item.desc}</p>
              </motion.div>
            ))}
          </div>
        </div>
      </section>

      {/* Features */}
      <section className="py-32 px-6 bg-white/[0.02]">
        <div className="max-w-6xl mx-auto">
          <motion.div
            initial="hidden"
            whileInView="visible"
            viewport={{ once: true, margin: "-100px" }}
            className="text-center mb-20"
          >
            <motion.h2 custom={0} variants={fadeUp} className="text-4xl md:text-5xl font-bold mb-4">
              Safety First. Always.
            </motion.h2>
            <motion.p custom={1} variants={fadeUp} className="text-zinc-400 text-lg max-w-lg mx-auto">
              Every feature is designed to protect your identity and wellbeing
            </motion.p>
          </motion.div>

          <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-6">
            {[
              {
                icon: Shield,
                title: "Voice Anonymization",
                desc: "Your voice is transformed in real-time. Nobody can recognize you.",
              },
              {
                icon: Lock,
                title: "No Personal Data",
                desc: "No names, no photos, no phone numbers. Completely anonymous.",
              },
              {
                icon: Clock,
                title: "Controlled Duration",
                desc: "Choose 5, 10, or 15 minute calls. You're always in control.",
              },
              {
                icon: Heart,
                title: "Crisis Resources",
                desc: "Built-in crisis detection with instant access to professional help.",
              },
              {
                icon: Users,
                title: "Block & Report",
                desc: "One-tap blocking and reporting for a safe community.",
              },
              {
                icon: Star,
                title: "Consent First",
                desc: "Listeners preview your topic before accepting. No surprises.",
              },
            ].map((item, i) => (
              <motion.div
                key={item.title}
                initial="hidden"
                whileInView="visible"
                viewport={{ once: true }}
                custom={i}
                variants={fadeUp}
                className="p-6 rounded-2xl bg-white/[0.03] border border-white/[0.06]"
              >
                <item.icon className="w-8 h-8 text-purple-400 mb-3" />
                <h3 className="text-lg font-semibold mb-1">{item.title}</h3>
                <p className="text-zinc-400 text-sm">{item.desc}</p>
              </motion.div>
            ))}
          </div>
        </div>
      </section>

      {/* CTA / Download */}
      <section id="download" className="py-32 px-6">
        <div className="max-w-3xl mx-auto text-center">
          <motion.div
            initial="hidden"
            whileInView="visible"
            viewport={{ once: true }}
          >
            <motion.h2 custom={0} variants={fadeUp} className="text-4xl md:text-5xl font-bold mb-6">
              Ready to talk?
            </motion.h2>
            <motion.p custom={1} variants={fadeUp} className="text-zinc-400 text-lg mb-10 max-w-md mx-auto">
              Download Backroom and connect with someone who will listen.
              Available on Android.
            </motion.p>
            <motion.div custom={2} variants={fadeUp}>
              <a
                href="#"
                className="inline-flex items-center gap-3 px-10 py-5 rounded-full bg-purple-600 hover:bg-purple-500 font-semibold text-lg transition-colors"
              >
                <svg viewBox="0 0 24 24" className="w-6 h-6 fill-current">
                  <path d="M17.523 2.736a.4.4 0 00-.471.07L3.32 14.147l5.905 3.31 8.298-14.72zm.49 18.527L5.674 18.41l8.27-4.636 4.069 7.49zm1.513-.256l-3.676-6.768 3.676-2.06a.4.4 0 010 .696l-3.676 2.06 3.676 6.768a.4.4 0 010-.696zM2.893 14.861a.4.4 0 01-.163-.508L6.56 3.765 2.73 14.37l.163.492z" />
                </svg>
                Get it on Google Play
              </a>
              <p className="text-zinc-500 text-sm mt-4">Coming soon to Google Play Store</p>
            </motion.div>
          </motion.div>
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t border-white/5 py-12 px-6">
        <div className="max-w-6xl mx-auto flex flex-col md:flex-row items-center justify-between gap-6">
          <span className="text-xl font-bold tracking-tight">
            <span className="text-purple-400">b</span>ackroom
          </span>
          <div className="flex gap-8 text-sm text-zinc-500">
            <a href="#" className="hover:text-white transition-colors">Privacy Policy</a>
            <a href="#" className="hover:text-white transition-colors">Terms of Service</a>
            <a href="#" className="hover:text-white transition-colors">Crisis Resources</a>
          </div>
          <p className="text-zinc-600 text-sm">
            © 2026 Backroom. Built in Kenya 🇰🇪
          </p>
        </div>
      </footer>
    </div>
  );
}
