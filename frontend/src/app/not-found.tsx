import Link from "next/link";

export default function NotFound() {
  return (
    <div className="mx-auto max-w-md py-16 text-center">
      <h1 className="text-xl font-semibold">Page not found</h1>
      <p className="mt-2 text-sm text-muted">This page doesn&apos;t exist yet.</p>
      <Link href="/" className="mt-6 inline-block text-sm font-medium underline">
        Back to dashboard
      </Link>
    </div>
  );
}
