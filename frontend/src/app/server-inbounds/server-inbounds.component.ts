import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  signal,
  viewChild
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { UpperCasePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import {
  Inbound,
  InboundApi,
  Protocol,
  Security,
  Server,
  ServerApi,
  Transport
} from '../server-api';

const FLAGS = [
  { emoji: '🇦🇷', name: 'Аргентина' },
  { emoji: '🇦🇺', name: 'Австралия' },
  { emoji: '🇦🇹', name: 'Австрия' },
  { emoji: '🇧🇪', name: 'Бельгия' },
  { emoji: '🇧🇷', name: 'Бразилия' },
  { emoji: '🇨🇦', name: 'Канада' },
  { emoji: '🇨🇳', name: 'Китай' },
  { emoji: '🇨🇿', name: 'Чехия' },
  { emoji: '🇩🇰', name: 'Дания' },
  { emoji: '🇫🇮', name: 'Финляндия' },
  { emoji: '🇫🇷', name: 'Франция' },
  { emoji: '🇩🇪', name: 'Германия' },
  { emoji: '🇭🇰', name: 'Гонконг' },
  { emoji: '🇮🇳', name: 'Индия' },
  { emoji: '🇮🇩', name: 'Индонезия' },
  { emoji: '🇮🇪', name: 'Ирландия' },
  { emoji: '🇮🇱', name: 'Израиль' },
  { emoji: '🇮🇹', name: 'Италия' },
  { emoji: '🇯🇵', name: 'Япония' },
  { emoji: '🇰🇿', name: 'Казахстан' },
  { emoji: '🇰🇷', name: 'Южная Корея' },
  { emoji: '🇱🇻', name: 'Латвия' },
  { emoji: '🇱🇹', name: 'Литва' },
  { emoji: '🇳🇱', name: 'Нидерланды' },
  { emoji: '🇳🇴', name: 'Норвегия' },
  { emoji: '🇵🇱', name: 'Польша' },
  { emoji: '🇷🇴', name: 'Румыния' },
  { emoji: '🇷🇺', name: 'Россия' },
  { emoji: '🇸🇬', name: 'Сингапур' },
  { emoji: '🇪🇸', name: 'Испания' },
  { emoji: '🇸🇪', name: 'Швеция' },
  { emoji: '🇨🇭', name: 'Швейцария' },
  { emoji: '🇹🇷', name: 'Турция' },
  { emoji: '🇦🇪', name: 'ОАЭ' },
  { emoji: '🇬🇧', name: 'Великобритания' },
  { emoji: '🇺🇸', name: 'США' },
  { emoji: '🇺🇿', name: 'Узбекистан' },
  { emoji: '🇻🇳', name: 'Вьетнам' }
] as const;

@Component({
  selector: 'app-server-inbounds',
  imports: [FormsModule, RouterLink, UpperCasePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './server-inbounds.html'
})
export class ServerInboundsComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly servers = inject(ServerApi);
  private readonly api = inject(InboundApi);
  private readonly dialog = viewChild<ElementRef<HTMLDialogElement>>('dialog');
  private serverId = 0;
  private routeVersion = 0;
  private read?: Subscription;
  readonly server = signal<Server | null>(null);
  readonly inbounds = signal<Inbound[]>([]);
  readonly loading = signal(true);
  readonly available = signal(false);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly formError = signal('');
  readonly flags = FLAGS;
  flag = '';
  name = '';
  protocol: Protocol = 'VLESS';
  transport: Transport = 'TCP';
  security: Security = 'TLS';
  port: number | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      this.routeVersion++;
      this.read?.unsubscribe();
    });
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      this.serverId = Number(params.get('id'));
      this.routeVersion++;
      this.dialog()?.nativeElement.close();
      this.formError.set('');
      this.refresh();
    });
  }

  refresh(): void {
    this.read?.unsubscribe();
    this.server.set(null);
    this.inbounds.set([]);
    this.loading.set(true);
    this.available.set(false);
    this.error.set('');
    const serverId = this.serverId;
    this.read = this.servers.list().subscribe({
      next: (servers) => {
        const server = servers.find((s) => s.id === serverId);
        this.server.set(server ?? null);
        if (!server || server.status !== 'WORKING' || server.setupState !== 'CONFIGURED') {
          this.loading.set(false);
          this.error.set('Инбаунды доступны только для работающего и настроенного сервера.');
          return;
        }
        this.available.set(true);
        this.read = this.api.list(serverId).subscribe({
          next: (items) => {
            this.inbounds.set(items);
            this.error.set('');
            this.loading.set(false);
          },
          error: () => {
            this.loading.set(false);
            this.error.set('Не удалось загрузить инбаунды.');
          }
        });
      },
      error: () => {
        this.loading.set(false);
        this.error.set('Не удалось загрузить сервер.');
      }
    });
  }

  open(): void {
    this.formError.set('');
    this.dialog()?.nativeElement.showModal();
  }

  close(): void {
    this.dialog()?.nativeElement.close();
  }

  protocolChanged(): void {
    this.transport = this.protocol === 'VLESS' ? 'TCP' : 'UDP';
  }

  create(valid: boolean | null): void {
    const server = this.server();
    if (
      !valid ||
      !FLAGS.some((item) => item.emoji === this.flag) ||
      !this.name.trim() ||
      (this.port !== null &&
        (!Number.isInteger(this.port) || this.port < 1 || this.port > 65535)) ||
      this.busy() ||
      !this.available() ||
      !server ||
      server.id !== this.serverId
    )
      return;
    const version = this.routeVersion;
    this.busy.set(true);
    this.formError.set('');
    this.api
      .create({
        serverId: server.id,
        name: `${this.flag} ${this.name.trim()}`,
        protocol: this.protocol,
        transport: this.transport,
        security: this.security,
        port: this.port
      })
      .subscribe({
        next: (result) => {
          this.busy.set(false);
          if (version !== this.routeVersion) return;
          if (!result) {
            this.formError.set('Не удалось добавить инбаунд.');
            return;
          }
          this.close();
          this.flag = '';
          this.name = '';
          this.port = null;
          this.refresh();
        },
        error: () => {
          this.busy.set(false);
          if (version === this.routeVersion) this.formError.set('Не удалось добавить инбаунд.');
        }
      });
  }

  remove(item: Inbound): void {
    const server = this.server();
    if (
      this.busy() ||
      !this.available() ||
      !server ||
      server.id !== this.serverId ||
      !this.inbounds().includes(item) ||
      !confirm(`Удалить инбаунд «${item.remark}»?`)
    )
      return;
    const version = this.routeVersion;
    if (version !== this.routeVersion || server.id !== this.serverId) return;
    this.busy.set(true);
    this.error.set('');
    this.api.delete(server.id, item.id, item.port).subscribe({
      next: (success) => {
        this.busy.set(false);
        if (version !== this.routeVersion) return;
        this.refresh();
        if (!success) this.error.set('Не удалось удалить инбаунд.');
      },
      error: () => {
        this.busy.set(false);
        if (version !== this.routeVersion) return;
        this.refresh();
        this.error.set('Не удалось удалить инбаунд.');
      }
    });
  }
}
