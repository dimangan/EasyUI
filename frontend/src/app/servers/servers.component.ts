import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  signal,
  viewChild
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CreateServer, Server, ServerApi } from '../server-api';

@Component({
  selector: 'app-servers',
  imports: [FormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './servers.html',
  styleUrl: './servers.css'
})
export class ServersComponent {
  private readonly api = inject(ServerApi);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  readonly servers = signal<Server[]>([]);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly formError = signal('');
  name = '';
  username = '';
  password = '';
  host = '';
  port = 22;
  domain = '';
  useKey = false;
  keyName = '';
  publicKey = '';
  privateKey = '';

  constructor() {
    this.refresh();
  }

  refresh(): void {
    this.loading.set(true);
    this.api.list().subscribe({
      next: (items) => {
        this.servers.set(items);
        this.error.set('');
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Не удалось загрузить серверы.');
        this.loading.set(false);
      }
    });
  }

  canOpen(s: Server): boolean {
    return s.status === 'WORKING' && s.setupState === 'CONFIGURED';
  }

  status(value: string): string {
    return (
      (
        {
          WAITING_FOR_SETUP: 'Ожидает настройки',
          SETUP_IN_PROGRESS: 'Настраивается',
          SETUP_FAILED: 'Ошибка настройки',
          WORKING: 'Работает'
        } as Record<string, string>
      )[value] ??
      value ??
      '—'
    );
  }

  setupState(value: string): string {
    return (
      (
        {
          NOT_CONFIGURED: 'Не настроен',
          SUDO_USER_ADDED: 'Добавлен sudo-пользователь',
          AUTHORIZED_KEYS_ADDED: 'Добавлен ключ',
          FIREWALL_ENABLED: 'Фаервол включён',
          UPDATED: 'Пакеты обновлены',
          NGINX_INSTALLED: 'Nginx установлен',
          SNAPD_INSTALLED: 'Snapd установлен',
          CERTBOT_INSTALLED: 'Certbot установлен',
          CERTBOT_LINKED: 'Certbot подключён',
          CERTIFICATE_ISSUED: 'Сертификат выпущен',
          NGINX_CONFIGURED: 'Nginx настроен',
          NGINX_HTML_ADDED: 'Сайт добавлен',
          NGINX_RESTARTED: 'Nginx перезапущен',
          XUI_INSTALLED: '3x-ui установлен',
          XUI_SSL_CONFIGURED: 'SSL 3x-ui настроен',
          XUI_ADMIN_ADDED: 'Администратор 3x-ui добавлен',
          XUI_SETTING_LOADED: 'Настройки 3x-ui загружены',
          XUI_TOKEN_ISSUED: 'Токен 3x-ui получен',
          DEFAULT_PORTS_OPENED: 'Порты открыты',
          CONFIGURED: 'Настроен'
        } as Record<string, string>
      )[value] ??
      value ??
      '—'
    );
  }

  open(): void {
    this.formError.set('');
    this.dialog().nativeElement.showModal();
  }

  clearSecrets(): void {
    this.password = '';
    this.publicKey = '';
    this.privateKey = '';
  }

  close(): void {
    this.clearSecrets();
    this.dialog().nativeElement.close();
  }

  create(valid: boolean | null): void {
    if (!valid || !Number.isInteger(this.port) || this.port < 1 || this.port > 65535 || this.busy())
      return;
    const body: CreateServer = {
      name: this.name,
      username: this.username,
      password: this.password,
      host: this.host,
      port: this.port,
      domain: this.domain,
      ...(this.useKey
        ? {
            securedKey: {
              name: this.keyName,
              publicKey: this.publicKey,
              privateKey: this.privateKey
            }
          }
        : {})
    };
    this.clearSecrets();
    this.busy.set(true);
    this.formError.set('');
    this.api.create(body).subscribe({
      next: () => {
        this.busy.set(false);
        this.close();
        this.name = '';
        this.username = '';
        this.host = '';
        this.port = 22;
        this.domain = '';
        this.useKey = false;
        this.keyName = '';
        this.error.set('');
        this.refresh();
      },
      error: () => {
        this.busy.set(false);
        this.formError.set('Не удалось добавить сервер. Повторите ввод пароля и ключей.');
      }
    });
  }

  remove(s: Server): void {
    if (this.busy() || !confirm(`Удалить сервер «${s.name}»?`)) return;
    this.busy.set(true);
    this.error.set('');
    this.api.delete(s.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.refresh();
      },
      error: () => {
        this.busy.set(false);
        this.error.set('Не удалось удалить сервер.');
        this.refresh();
      }
    });
  }

  setup(s: Server): void {
    if (this.busy() || !confirm(`Запустить настройку сервера «${s.name}»?`)) return;
    this.busy.set(true);
    this.error.set('');
    this.api.setup(s.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.refresh();
      },
      error: () => {
        this.busy.set(false);
        this.error.set('Не удалось запустить настройку сервера.');
        this.refresh();
      }
    });
  }
}
