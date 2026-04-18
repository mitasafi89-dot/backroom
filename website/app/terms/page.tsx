import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "Terms of Service",
  description: "Terms of service for using the Backroom anonymous voice support application.",
};

export default function TermsPage() {
  return (
    <div className="min-h-screen bg-obsidian text-white">
      <div className="max-w-3xl mx-auto px-5 md:px-8 py-20 prose prose-invert prose-sm">
        <Link href="/" className="text-amber/60 hover:text-amber text-sm mb-8 inline-block no-underline">
          ← Back to Backroom
        </Link>

        <h1>Terms of Service</h1>
        <p className="text-white/40">Last updated: April 18, 2026</p>

        <h2>1. What Backroom Is</h2>
        <p>
          Backroom is a peer-to-peer anonymous voice support application. It
          connects people who want to talk with people who want to listen, using
          real-time voice anonymization technology.
        </p>

        <h2>2. What Backroom Is NOT</h2>
        <ul>
          <li>A crisis intervention service</li>
          <li>A therapy or counseling platform</li>
          <li>A medical service of any kind</li>
          <li>A substitute for professional mental health treatment</li>
        </ul>

        <h2>3. Eligibility</h2>
        <p>You must be at least 13 years old to use Backroom.</p>

        <h2>4. Acceptable Use</h2>
        <p>You agree NOT to:</p>
        <ul>
          <li>Attempt to identify, dox, or track other users</li>
          <li>Record, screenshot, or share any conversation</li>
          <li>Harass, threaten, or bully other users</li>
          <li>Share sexually explicit content</li>
          <li>Promote illegal activities</li>
          <li>Attempt to bypass voice anonymization</li>
        </ul>

        <h2>5. Safety & Moderation</h2>
        <p>
          Users can block and report other users with one tap. Reports are
          reviewed and may result in temporary or permanent bans. We reserve the
          right to terminate access for any violation.
        </p>

        <h2>6. Disclaimer of Liability</h2>
        <p>
          Backroom is provided &quot;as is.&quot; We make no guarantees about the
          quality, accuracy, or safety of conversations. Listeners are
          volunteers, not trained professionals. Use at your own discretion.
        </p>

        <h2>7. Privacy</h2>
        <p>
          See our <Link href="/privacy" className="text-amber">Privacy Policy</Link> for
          details on how we handle (or rather, don&apos;t handle) your data.
        </p>

        <h2>8. Contact</h2>
        <p>
          Questions?{" "}
          <a href="mailto:legal@backroom.llc" className="text-amber">
            legal@backroom.llc
          </a>
        </p>
      </div>
    </div>
  );
}

