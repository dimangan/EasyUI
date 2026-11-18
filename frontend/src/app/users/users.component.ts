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
import { SubscriptionApi, SubscriptionLink, User, UserApi } from '../user-api';

@Component({
  selector: 'app-users',
  imports: [FormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './users.html',
  styleUrl: './users.css'
})
export class UsersComponent {
  private readonly api = inject(UserApi);
  private readonly subscriptions = inject(SubscriptionApi);
  private readonly createDialog = viewChild.required<ElementRef<HTMLDialogElement>>('createDialog');
  private readonly subscriptionDialog =
    viewChild.required<ElementRef<HTMLDialogElement>>('subscriptionDialog');
  readonly users = signal<User[]>([]);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly formError = signal('');
  readonly selectedName = signal('');
  readonly linkLoading = signal(false);
  readonly subscription = signal<SubscriptionLink | null>(null);
  readonly copyMessage = signal('');
  private linkRequest = 0;
  name = '';

  constructor() {
    this.refresh();
  }

  refresh(): void {
    this.loading.set(true);
    this.api.list().subscribe({
      next: (users) => {
        this.users.set(users);
        this.error.set('');
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Не удалось загрузить пользователей.');
        this.loading.set(false);
      }
    });
  }

  openCreate(): void {
    this.formError.set('');
    this.createDialog().nativeElement.showModal();
  }

  closeCreate(): void {
    this.createDialog().nativeElement.close();
  }

  create(valid: boolean | null): void {
    const name = this.name.trim();
    if (!valid || !name || this.busy()) return;
    this.busy.set(true);
    this.formError.set('');
    this.api.create(name).subscribe({
      next: () => {
        this.busy.set(false);
        this.name = '';
        this.closeCreate();
        this.error.set('');
        this.refresh();
      },
      error: () => {
        this.busy.set(false);
        this.formError.set('Не удалось добавить пользователя.');
      }
    });
  }

  remove(user: User): void {
    if (this.busy() || !confirm(`Удалить пользователя «${user.name}»?`)) return;
    this.busy.set(true);
    this.error.set('');
    this.api.delete(user.id).subscribe({
      next: () => {
        this.busy.set(false);
        this.refresh();
      },
      error: () => {
        this.busy.set(false);
        this.error.set('Не удалось удалить пользователя.');
        this.refresh();
      }
    });
  }

  openSubscription(user: User): void {
    const request = ++this.linkRequest;
    this.selectedName.set(user.name);
    this.subscription.set(null);
    this.copyMessage.set('');
    this.linkLoading.set(true);
    this.subscriptionDialog().nativeElement.showModal();
    this.subscriptions.getLink(user.id).subscribe({
      next: (data) => {
        if (request !== this.linkRequest) return;
        try {
          if (typeof data?.link !== 'string' || typeof data?.qr !== 'string')
            throw new Error('Invalid subscription');
          const url = new URL(data.link);
          if (
            (url.protocol === 'https:' || url.protocol === 'http:') &&
            /^[A-Za-z0-9+/]+={0,2}$/.test(data.qr)
          )
            this.subscription.set({ link: url.href, qr: data.qr });
        } catch {
          /* unavailable */
        }
        this.linkLoading.set(false);
      },
      error: () => {
        if (request === this.linkRequest) this.linkLoading.set(false);
      }
    });
  }

  clearSubscription(): void {
    this.linkRequest++;
    this.subscription.set(null);
    this.selectedName.set('');
    this.copyMessage.set('');
  }

  async copy(link: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(link);
      this.copyMessage.set('Ссылка скопирована.');
    } catch {
      this.copyMessage.set('Не удалось скопировать. Выделите ссылку вручную.');
    }
  }
}
