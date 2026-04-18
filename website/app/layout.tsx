import type { Metadata } from "next";
import { Inter, Lora } from "next/font/google";
import "./globals.css";

const inter = Inter({
  subsets: ["latin"],
  variable: "--font-inter",
  display: "swap",
});

const lora = Lora({
  subsets: ["latin"],
  variable: "--font-lora",
  display: "swap",
});

export const metadata: Metadata = {
  metadataBase: new URL("https://backroom.llc"),
  title: {
    default: "Backroom — Anonymous Voice Support | Talk to Someone Who Cares",
    template: "%s | Backroom",
  },
  description:
    "Free anonymous voice support for anxiety, depression, loneliness, and life struggles. No profiles, no history — just real human connection with voice anonymization. Available 24/7.",
  keywords: [
    "anonymous voice support",
    "someone to talk to",
    "anonymous therapy alternative",
    "free mental health support",
    "talk to someone anonymously",
    "vent anonymously",
    "anonymous listening service",
    "peer support app",
    "loneliness help",
    "anxiety support",
    "depression help anonymous",
    "mental health Kenya",
    "crisis support",
    "I need to talk to someone",
    "feeling alone",
    "anonymous counseling",
    "voice chat anonymous",
    "safe space to talk",
    "emotional support",
    "volunteer listener",
    "free therapy chat",
    "online crisis help",
    "cant afford therapy",
    "just need someone to listen",
    "anonymous vent line",
    "peer counseling",
    "active listening app",
    "talk to a stranger safely",
    "overwhelmed need help",
    "breakup support",
    "grief support anonymous",
    "burnout help",
    "no one to talk to",
    "midnight someone to call",
    "voice support not text",
  ],
  openGraph: {
    title: "Backroom — Anonymous Voice Support",
    description:
      "Talk to someone who cares. Anonymous, consent-first voice conversations. No profiles. No history. Just a moment of connection.",
    type: "website",
    url: "https://backroom.llc",
    siteName: "Backroom",
    locale: "en_US",
  },
  twitter: {
    card: "summary_large_image",
    title: "Backroom — Anonymous Voice Support",
    description:
      "Free anonymous voice support. Talk. Listen. Connect. Your voice is modified. Your identity is hidden.",
  },
  robots: { index: true, follow: true },
  alternates: { canonical: "https://backroom.llc" },
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en" className={`${inter.variable} ${lora.variable}`}>
      <head>
        <meta name="theme-color" content="#0F0F12" />
      </head>
      <body className="antialiased bg-obsidian text-white font-sans noise-bg">
        <div className="relative z-10">{children}</div>
      </body>
    </html>
  );
}
