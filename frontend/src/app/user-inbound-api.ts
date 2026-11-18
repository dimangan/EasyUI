import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map } from 'rxjs';

export interface UserInbound { id: number; remark: string; tag: string; protocol: string; port: number; network: string; peeked: boolean; newPeeked: boolean; }
export interface UserInboundAssignments { unavailableServers: { id: number; name: string }[]; user: { id: number; name: string }; changes: { server: { id: number; name: string }; inbounds: UserInbound[] }[]; }
interface AssignmentResponse {
  unavailableServers: { id: number; name: string }[];
  user: { id: number; name: string };
  changes: { server: { id: number; name: string }; inbounds: { id: number; remark: string; tag: string; protocol: string; port: number; streamSettings?: { network?: string }; peeked: boolean; newPeeked: boolean }[] }[];
}

@Injectable({ providedIn: 'root' })
export class UserInboundApi {
  private readonly http = inject(HttpClient);
  list(userId: number) {
    return this.http.get<AssignmentResponse>(`/user/inbound/list/${userId}`).pipe(map(response => ({
      user: { id: response.user.id, name: response.user.name },
      unavailableServers: response.unavailableServers ?? [],
      changes: response.changes.map(group => ({
        server: { id: group.server.id, name: group.server.name },
        inbounds: group.inbounds.map(i => ({ id: i.id, remark: i.remark, tag: i.tag, protocol: i.protocol, port: i.port, network: i.streamSettings?.network ?? '', peeked: i.peeked, newPeeked: i.newPeeked }))
      }))
    })));
  }
  save(assignments: UserInboundAssignments) {
    const body = {
      user: { id: assignments.user.id, name: assignments.user.name },
      changes: assignments.changes.map(group => ({
        server: { id: group.server.id, name: group.server.name },
        inbounds: group.inbounds.map(i => ({ id: i.id, peeked: i.peeked, newPeeked: i.newPeeked }))
      }))
    };
    return this.http.post<void>('/user/inbound/change', body);
  }
}
