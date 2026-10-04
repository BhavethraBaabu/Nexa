import type { Invitation, Member, Organization, Role } from "@/types/api";

type AuthFetch = <T>(path: string, init?: RequestInit) => Promise<T>;

export function teamApi(authFetch: AuthFetch) {
  return {
    organization: () => authFetch<Organization>("/api/v1/organizations/current"),
    renameOrganization: (name: string) =>
      authFetch<Organization>("/api/v1/organizations/current", { method: "PATCH", body: JSON.stringify({ name }) }),
    members: () => authFetch<Member[]>("/api/v1/organizations/members"),
    invitations: () => authFetch<Invitation[]>("/api/v1/organizations/invitations"),
    invite: (email: string, role: Role) =>
      authFetch<Invitation>("/api/v1/organizations/members/invite", { method: "POST", body: JSON.stringify({ email, role }) }),
    changeRole: (memberId: string, role: Role) =>
      authFetch<Member>(`/api/v1/organizations/members/${memberId}`, { method: "PATCH", body: JSON.stringify({ role }) }),
    removeMember: (memberId: string) => authFetch<void>(`/api/v1/organizations/members/${memberId}`, { method: "DELETE" }),
    revokeInvitation: (invitationId: string) =>
      authFetch<void>(`/api/v1/organizations/invitations/${invitationId}`, { method: "DELETE" }),
  };
}

export const ROLES: Role[] = ["ADMIN", "MANAGER", "MEMBER"];

export function roleLabel(role: Role) {
  return role.charAt(0) + role.slice(1).toLowerCase();
}
