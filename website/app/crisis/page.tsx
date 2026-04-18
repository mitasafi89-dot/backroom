import type { Metadata } from "next";
import Link from "next/link";

export const metadata: Metadata = {
  title: "Crisis Resources",
  description:
    "If you are in crisis or danger, please reach out to these professional helplines immediately. Backroom is not a crisis service.",
};

const resources = [
  {
    region: "United States",
    lines: [
      { name: "988 Suicide & Crisis Lifeline", contact: "Call or text 988", url: "https://988lifeline.org" },
      { name: "Crisis Text Line", contact: "Text HOME to 741741", url: "https://www.crisistextline.org" },
      { name: "SAMHSA National Helpline", contact: "1-800-662-4357", url: "https://www.samhsa.gov/find-help/national-helpline" },
      { name: "Trevor Project (LGBTQ+)", contact: "1-866-488-7386", url: "https://www.thetrevorproject.org" },
    ],
  },
  {
    region: "Kenya",
    lines: [
      { name: "Befrienders Kenya", contact: "+254 722 178 177", url: "https://www.befrienderskenya.org" },
      { name: "Kenya Red Cross", contact: "1199", url: "https://www.redcross.or.ke" },
    ],
  },
  {
    region: "United Kingdom",
    lines: [
      { name: "Samaritans", contact: "116 123 (free, 24/7)", url: "https://www.samaritans.org" },
      { name: "SHOUT", contact: "Text SHOUT to 85258", url: "https://giveusashout.org" },
    ],
  },
  {
    region: "International",
    lines: [
      { name: "International Association for Suicide Prevention", contact: "Find your country", url: "https://www.iasp.info/resources/Crisis_Centres/" },
      { name: "Befrienders Worldwide", contact: "Find your country", url: "https://www.befrienders.org" },
    ],
  },
];

export default function CrisisPage() {
  return (
    <div className="min-h-screen bg-obsidian text-white">
      <div className="max-w-3xl mx-auto px-5 md:px-8 py-20">
        <Link href="/" className="text-amber/60 hover:text-amber text-sm mb-8 inline-block">
          ← Back to Backroom
        </Link>

        <div className="glass rounded-3xl p-8 md:p-12 mb-8 border-l-4 border-amber">
          <h1 className="text-3xl font-bold mb-4">
            If you are in immediate danger, call your local emergency number.
          </h1>
          <p className="text-white/50 leading-relaxed">
            Backroom is a peer support app — not a crisis service. If you or
            someone you know is in crisis, please contact one of these
            professional helplines immediately. They are free, confidential, and
            available 24/7.
          </p>
        </div>

        {resources.map((r) => (
          <div key={r.region} className="mb-10">
            <h2 className="text-lg font-semibold text-white/70 mb-4">{r.region}</h2>
            <div className="space-y-3">
              {r.lines.map((line) => (
                <a
                  key={line.name}
                  href={line.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="glass glass-hover rounded-2xl p-5 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 block"
                >
                  <div>
                    <p className="font-medium">{line.name}</p>
                    <p className="text-sm text-amber">{line.contact}</p>
                  </div>
                  <span className="text-xs text-white/30">Visit →</span>
                </a>
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

