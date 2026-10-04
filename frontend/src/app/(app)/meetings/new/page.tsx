import type { Metadata } from "next";
import { NewMeetingForm } from "@/features/meetings/components/new-meeting-form";

export const metadata: Metadata = { title: "New meeting · Nexa" };

export default function Page() {
  return <NewMeetingForm />;
}
