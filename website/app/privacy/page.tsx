import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "Privacy Policy",
  description: "Backroom's privacy policy. We collect zero personal data. Your voice is anonymized on-device.",
};

export default function PrivacyPage() {
  return (
    <div className="min-h-screen bg-obsidian text-white">
      <div className="max-w-3xl mx-auto px-5 md:px-8 py-20 prose prose-invert prose-sm">
        <Link href="/" className="text-amber/60 hover:text-amber text-sm mb-8 inline-block no-underline">
          ← Back to Backroom
        </Link>

        <h1>Privacy Policy</h1>
        <p className="text-white/40">Last updated: April 18, 2026</p>

        <h2>Our Core Principle</h2>
        <p>
          Backroom is built on the principle of <strong>zero knowledge</strong>.
          We cannot identify you because we never collect identifying information
          in the first place.
        </p>

        <h2>What We Do NOT Collect</h2>
        <ul>
          <li>Names, email addresses, or phone numbers</li>
          <li>Profile photos or biometric data</li>
          <li>IP addresses (not logged)</li>
          <li>Voice recordings (calls are never recorded or stored)</li>
          <li>Chat transcripts (none exist)</li>
          <li>Location data</li>
          <li>Device identifiers for tracking</li>
        </ul>

        <h2>What We Do Collect</h2>
        <ul>
          <li>Anonymous session tokens (temporary, for connection matching)</li>
          <li>Aggregate, non-identifying usage statistics (e.g., total calls made, average duration)</li>
          <li>Crash reports (anonymized, no personal data)</li>
        </ul>

        <h2>Voice Anonymization</h2>
        <p>
          All voice data is processed <strong>on your device</strong> using
          real-time voice masking before any audio is transmitted to the other
          party. We never receive, process, or store your original voice.
        </p>

        <h2>Data Retention</h2>
        <p>
          Session tokens expire when a call ends. There is no persistent user
          data to retain or delete.
        </p>

        <h2 id="cookies">Cookies</h2>
        <p>
          Our website uses only essential cookies required for the site to
          function. We do not use advertising or tracking cookies.
        </p>

        <h2>Third-Party Services</h2>
        <p>
          We use Firebase Cloud Messaging for optional push notifications. No
          personal data is shared with Firebase beyond anonymous device tokens.
        </p>

        <h2>Children</h2>
        <p>
          Backroom is not intended for users under 13. We do not knowingly
          collect data from children.
        </p>

        <h2>Contact</h2>
        <p>
          Questions about privacy? Email{" "}
          <a href="mailto:privacy@backroom.llc" className="text-amber">
            privacy@backroom.llc
          </a>
        </p>
      </div>
    </div>
  );
}

