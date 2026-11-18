import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { defer, Observable, tap } from 'rxjs';

const TOKEN_KEY = 'easy_ui.auth.token';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly tokenState = signal<string | null>(null);
  readonly sessionExpired = signal(false);
  private sessionVersion = 0;
  private expiryTimer?: ReturnType<typeof setTimeout>;

  constructor() {
    let token: string | null = null;
    try {
      token = sessionStorage.getItem(TOKEN_KEY);
    } catch {
      // Authentication still works in memory when browser storage is unavailable.
    }
    if (token && this.expiration(token) > Date.now()) {
      this.storeToken(token);
    } else {
      if (token) this.sessionExpired.set(true);
      this.removeStoredToken();
    }
  }

  login(username: string, password: string): Observable<string> {
    return this.http.post('/login', { username, password }, { responseType: 'text' }).pipe(
      tap(token => {
        if (this.expiration(token) <= Date.now()) throw new Error('Invalid token');
        this.sessionExpired.set(false);
        this.storeToken(token);
      })
    );
  }

  changeCredentials(username: string, password: string): Observable<string> {
    return defer(() => {
      const requestToken = this.getToken();
      const requestSession = this.sessionVersion;
      return this.http.post('/change-credentials', { username, password }, { responseType: 'text' }).pipe(
        tap(token => {
          if (!requestToken || this.getToken() !== requestToken || this.sessionVersion !== requestSession) throw new Error('Session changed');
          if (this.expiration(token) <= Date.now()) throw new Error('Invalid token');
          this.storeToken(token);
        })
      );
    });
  }

  getToken(): string | null {
    const token = this.tokenState();
    if (token && this.expiration(token) <= Date.now()) {
      this.logout(true);
      return null;
    }
    return token;
  }

  logout(expired = false): void {
    this.sessionExpired.set(expired);
    this.clearToken();
    void this.router.navigate(['/login']);
  }

  private clearToken(): void {
    this.sessionVersion++;
    clearTimeout(this.expiryTimer);
    this.tokenState.set(null);
    this.removeStoredToken();
  }

  private removeStoredToken(): void {
    try {
      sessionStorage.removeItem(TOKEN_KEY);
    } catch {
      // Clearing in-memory authentication must not depend on storage access.
    }
  }

  private storeToken(token: string): void {
    this.sessionVersion++;
    clearTimeout(this.expiryTimer);
    this.tokenState.set(token);
    try {
      sessionStorage.setItem(TOKEN_KEY, token);
    } catch {
      // Continue with the in-memory token and expiry timer.
    }
    const delay = this.expiration(token) - Date.now();
    this.expiryTimer = setTimeout(() => {
      if (this.expiration(token) <= Date.now()) this.logout(true);
      else this.storeToken(token);
    }, Math.min(delay, 2_147_483_647));
  }

  private expiration(token: string): number {
    try {
      const parts = token.split('.');
      if (parts.length !== 3) return 0;
      const payload = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/')));
      return typeof payload.exp === 'number' && Number.isFinite(payload.exp) ? payload.exp * 1000 : 0;
    } catch {
      return 0;
    }
  }
}
