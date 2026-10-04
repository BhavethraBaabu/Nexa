import type { Metadata } from "next";
import { MeetingDetail } from "@/features/meetings/components/meeting-detail";

export const metadata: Metadata = { title: "Meeting · Nexa" };

export default function Page() {
  return <MeetingDetail />;
}
