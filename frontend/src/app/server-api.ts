import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map } from 'rxjs';

export interface Server { id: number; name: string; username: string; password: string; host: string; port: number; domain: string; status: string; setupState: string; xuiPort?: number, xuiPath?: string}
export interface CreateServer { name: string; username: string; password: string; host: string; port: number; domain: string; securedKey?: { name: string; publicKey: string; privateKey: string }; }
export type Protocol = 'VLESS' | 'HYSTERIA';
export type Transport = 'TCP' | 'GRPC' | 'XHTTP' | 'UDP';
export type Security = 'NONE' | 'TLS' | 'REALITY';
export interface Inbound { id: number; remark: string; protocol: string; port: number; network: string; security: string; }
export interface CreateInbound { serverId: number; name: string; protocol: Protocol; transport: Transport; security: Security; port: number | null; }
interface InboundResponse { id: number; remark: string; protocol: string; port: number; streamSettings?: { network?: string; security?: string }; }
const serverView = (s: Server): Server => ({ id: s.id, name: s.name, username: s.username, password: s.password, host: s.host, port: s.port, domain: s.domain, status: s.status, setupState: s.setupState, xuiPort: s.xuiPort, xuiPath: s.xuiPath });
const inboundView = (i: InboundResponse): Inbound => ({ id: i.id, remark: i.remark, protocol: i.protocol, port: i.port, network: i.streamSettings?.network ?? '', security: i.streamSettings?.security ?? '' });
@Injectable({ providedIn: 'root' })
export class ServerApi {
  private readonly http = inject(HttpClient);
  list() { return this.http.get<Server[]>('/server/list').pipe(map(items => items.map(serverView))); }
  create(body: CreateServer) { return this.http.post<Server>('/server/create', body).pipe(map(serverView)); }
  delete(id: number) { return this.http.delete<void>(`/server/delete/${id}`); }
  setup(id: number) { return this.http.get<void>(`/server/setup/${id}`); }
}
@Injectable({ providedIn: 'root' })
export class InboundApi {
  private readonly http = inject(HttpClient);
  list(serverId: number) { return this.http.get<InboundResponse[]>(`/inbound/list/${serverId}`).pipe(map(items => items.map(inboundView))); }
  create(body: CreateInbound) { return this.http.post<InboundResponse | null>('/inbound/create', body).pipe(map(item => item ? inboundView(item) : null)); }
  delete(serverId: number, inboundId: number, port: number) { return this.http.post<boolean>('/inbound/delete', { serverId, inboundId, port }); }
}
