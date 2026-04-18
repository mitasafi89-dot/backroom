import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Backroom — Anonymous Voice Support",
  description:
    "Talk to someone who cares. Anonymous, safe, two-way voice conversations with real people. No judgement, no identity.",
  keywords: ["mental health", "anonymous", "voice support", "therapy", "listening"],
  openGraph: {
    title: "Backroom — Anonymous Voice Support",
    description: "Talk to someone who cares. Anonymous, safe, voice conversations.",
    type: "website",
    url: "https://backroom.llc",
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body className="antialiased bg-[#0a0a0a] text-white">
        {children}
      </body>
    </html>
  );
}
