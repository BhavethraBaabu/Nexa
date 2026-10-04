import type { Metadata } from "next";
import { LoginForm } from "@/features/auth/components/login-form";
import { RedirectIfAuthenticated } from "@/features/auth/components/session-gates";

export const metadata: Metadata = { title: "Sign in · Nexa" };

export default function LoginPage() {
  return (
    <RedirectIfAuthenticated>
      <LoginForm />
    </RedirectIfAuthenticated>
  );
}
