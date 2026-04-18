import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "Become a Volunteer Listener",
  description:
    "Help others by listening anonymously. No training required — just empathy and a quiet space. Volunteer as a Backroom listener.",
};

export default function VolunteerPage() {
  return (
    <div className="min-h-screen bg-obsidian text-white">
      <div className="max-w-3xl mx-auto px-5 md:px-8 py-20">
        <Link href="/" className="text-amber/60 hover:text-amber text-sm mb-8 inline-block">
          ← Back to Backroom
        </Link>

        <h1 className="text-4xl md:text-5xl font-bold mb-6">
          Listen. That&apos;s it.
        </h1>
        <p className="text-lg text-white/50 leading-relaxed mb-12 max-w-xl">
          You don&apos;t need a degree. You don&apos;t need to fix anyone. You
          just need to be present for a few minutes. That&apos;s powerful.
        </p>

        <div className="space-y-6 mb-16">
          {[
            {
              title: "1. Download the app",
              desc: "Get Backroom on Android. No account required.",
            },
            {
              title: "2. Switch to Listener mode",
              desc: "Tap the role switcher to 'Listener.' You'll start seeing Soft Previews from sharers.",
            },
            {
              title: "3. Choose a conversation",
              desc: "Accept based on topic, intensity, and duration. You're never obligated to take any call.",
            },
            {
              title: "4. Listen with empathy",
              desc: "Your voice is anonymized too. Just be present, acknowledge, and let them know they're heard.",
            },
          ].map((step) => (
            <div key={step.title} className="glass rounded-2xl p-6">
              <h3 className="font-semibold text-amber mb-1">{step.title}</h3>
              <p className="text-white/40 text-sm">{step.desc}</p>
            </div>
          ))}
        </div>

        <div className="glass rounded-3xl p-8 border-l-4 border-teal">
          <h2 className="text-xl font-semibold mb-3 text-teal">Guidelines</h2>
          <ul className="space-y-2 text-sm text-white/50">
            <li>• <strong>Don&apos;t</strong> give advice unless asked</li>
            <li>• <strong>Don&apos;t</strong> try to diagnose or prescribe</li>
            <li>• <strong>Do</strong> validate their feelings</li>
            <li>• <strong>Do</strong> use the crisis referral button if needed</li>
            <li>• <strong>Do</strong> end the call if you feel overwhelmed — self-care first</li>
          </ul>
        </div>
      </div>
    </div>
  );
}

