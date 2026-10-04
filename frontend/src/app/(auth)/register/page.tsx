import type { Metadata } from "next";
import { RegisterForm } from "@/features/auth/components/register-form";
import { RedirectIfAuthenticated } from "@/features/auth/components/session-gates";

export const metadata: Metadata = { title: "Create organization · Nexa" };

export default function RegisterPage() {
  return (
    <RedirectIfAuthenticated>
      <RegisterForm />
    </RedirectIfAuthenticated>
  );
}
