import type { Metadata } from "next";
import { MeetingsList } from "@/features/meetings/components/meetings-list";

export const metadata: Metadata = { title: "Meetings · Nexa" };

export default function Page() {
  return <MeetingsList />;
}
