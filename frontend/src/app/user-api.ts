import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map } from 'rxjs';

export interface User { id: number; name: string; totalTraffic: string}
export interface SubscriptionLink { link: string; qr: string; }
const userView = (user: User): User => ({ id: user.id, name: user.name, totalTraffic: user.totalTraffic });

@Injectable({ providedIn: 'root' })
export class UserApi {
  private readonly http = inject(HttpClient);
  list() { return this.http.get<User[]>('/user/list').pipe(map(users => users.map(userView))); }
  create(name: string) { return this.http.post<User>('/user/create', { name }).pipe(map(userView)); }
  delete(id: number) { return this.http.delete<void>(`/user/delete/${id}`); }
}

@Injectable({ providedIn: 'root' })
export class SubscriptionApi {
  private readonly http = inject(HttpClient);
  getLink(userId: number) { return this.http.get<SubscriptionLink>(`/link/${userId}`); }
}
