"use client";

import { useState } from "react";
import { motion } from "framer-motion";

interface RoleSwitcherProps {
  onChange?: (role: "sharer" | "listener") => void;
}

export default function RoleSwitcher({ onChange }: RoleSwitcherProps) {
  const [role, setRole] = useState<"sharer" | "listener">("sharer");

  const handleSwitch = (newRole: "sharer" | "listener") => {
    setRole(newRole);
    onChange?.(newRole);
  };

  return (
    <div
      className="relative inline-flex rounded-full glass p-1 gap-0.5"
      role="radiogroup"
      aria-label="Choose your role"
    >
      {(["sharer", "listener"] as const).map((r) => (
        <button
          key={r}
          role="radio"
          aria-checked={role === r}
          onClick={() => handleSwitch(r)}
          className={`relative z-10 px-5 py-2 rounded-full text-sm font-medium transition-colors duration-300 ${
            role === r ? "text-obsidian" : "text-white/50 hover:text-white/70"
          }`}
        >
          {role === r && (
            <motion.div
              layoutId="role-pill"
              className={`absolute inset-0 rounded-full ${
                r === "sharer"
                  ? "bg-gradient-to-r from-amber to-amber-dark"
                  : "bg-gradient-to-r from-teal to-teal-dark"
              }`}
              transition={{ type: "spring", stiffness: 400, damping: 30 }}
            />
          )}
          <span className="relative z-10 capitalize">{r}</span>
        </button>
      ))}
    </div>
  );
}

