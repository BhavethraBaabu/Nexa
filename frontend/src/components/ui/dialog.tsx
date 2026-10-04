"use client";

import { useEffect, useRef, type ReactNode } from "react";

/** Accessible modal built on the native <dialog> element (focus trapping and Esc handled by the browser). */
export function Dialog({ open, title, onClose, children }: { open: boolean; title: string; onClose: () => void; children: ReactNode }) {
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog
      ref={ref}
      onClose={onClose}
      aria-labelledby="dialog-title"
      className="w-[min(36rem,calc(100vw-2rem))] rounded-lg border border-border bg-surface p-0 text-foreground backdrop:bg-black/40"
    >
      <div className="flex items-center justify-between border-b border-border px-5 py-3">
        <h2 id="dialog-title" className="text-sm font-medium">{title}</h2>
        <button type="button" onClick={onClose} aria-label="Close" className="rounded px-2 text-muted hover:bg-surface-strong">✕</button>
      </div>
      <div className="px-5 py-4">{open && children}</div>
    </dialog>
  );
}
