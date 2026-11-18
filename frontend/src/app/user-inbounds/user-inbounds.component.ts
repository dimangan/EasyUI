import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal
} from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { UserInbound, UserInboundApi, UserInboundAssignments } from '../user-inbound-api';

@Component({
  selector: 'app-user-inbounds',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './user-inbounds.html',
  styleUrl: './user-inbounds.css'
})
export class UserInboundsComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(UserInboundApi);
  private userId = 0;
  private version = 0;
  private read?: Subscription;
  private write?: Subscription;
  readonly assignments = signal<UserInboundAssignments | null>(null);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly message = signal('');
  readonly dirty = computed(
    () =>
      this.assignments()?.changes.some((group) =>
        group.inbounds.some((i) => i.peeked !== i.newPeeked)
      ) ?? false
  );

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      this.version++;
      this.read?.unsubscribe();
      this.write?.unsubscribe();
    });
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      this.version++;
      this.read?.unsubscribe();
      this.write?.unsubscribe();
      this.saving.set(false);
      this.userId = Number(params.get('id'));
      this.assignments.set(null);
      this.error.set('');
      this.message.set('');
      this.load();
    });
  }

  unavailableNames(): string {
    return (
      this.assignments()
        ?.unavailableServers.map((server) => server.name)
        .join(', ') ?? ''
    );
  }

  retry(): void {
    this.error.set('');
    this.message.set('');
    this.load(this.assignments() ?? undefined, false, true);
  }

  private load(submitted?: UserInboundAssignments, saveFailed = false, retry = false): void {
    const id = this.userId;
    const version = this.version;
    if (!Number.isSafeInteger(id) || id <= 0) {
      this.loading.set(false);
      this.error.set('Некорректный идентификатор пользователя.');
      return;
    }
    this.loading.set(true);
    this.read = this.api.list(id).subscribe({
      next: (data) => {
        if (version !== this.version) return;
        this.loading.set(false);
        this.error.set('');
        if (data.user.id !== id) {
          this.error.set('Получены данные другого пользователя.');
          return;
        }
        if (!submitted) {
          this.assignments.set(data);
          return;
        }
        // Keep every changed selection until the server confirms its current value.
        let unconfirmed = false;
        for (const previous of submitted.changes) {
          const changed = previous.inbounds.filter((i) => i.peeked !== i.newPeeked);
          if (!changed.length) continue;
          let group = data.changes.find((g) => g.server.id === previous.server.id);
          if (!group) {
            group = { server: previous.server, inbounds: [] };
            data.changes.push(group);
          }
          for (const requested of changed) {
            const current = group.inbounds.find((i) => i.id === requested.id);
            if (!current) {
              group.inbounds.push(requested);
              unconfirmed = true;
            } else if (current.peeked !== requested.newPeeked) {
              current.newPeeked = requested.newPeeked;
              unconfirmed = true;
            }
          }
        }
        this.assignments.set(data);
        if (unconfirmed)
          this.error.set(
            'Не все изменения подтверждены сервером. Неподтверждённые изменения сохранены для повторной отправки.'
          );
        else if (retry) this.message.set('Данные обновлены.');
        else if (saveFailed)
          this.message.set(
            'Сервер сообщил об ошибке, но обновлённые данные подтверждают выбранные назначения.'
          );
        else this.message.set('Изменения сохранены.');
      },
      error: () => {
        if (version !== this.version) return;
        this.loading.set(false);
        this.error.set(
          submitted
            ? 'Не удалось проверить сохранение. Выбранные изменения сохранены для повторной отправки.'
            : 'Не удалось загрузить инбаунды пользователя.'
        );
      }
    });
  }

  toggle(serverId: number, inbound: UserInbound, checked: boolean): void {
    if (this.saving() || this.loading()) return;
    this.assignments.update(
      (data) =>
        data && {
          ...data,
          changes: data.changes.map((group) =>
            group.server.id === serverId
              ? {
                  ...group,
                  inbounds: group.inbounds.map((i) =>
                    i === inbound ? { ...i, newPeeked: checked } : i
                  )
                }
              : group
          )
        }
    );
    this.error.set('');
    this.message.set('');
  }

  save(): void {
    const data = this.assignments();
    if (!data || data.user.id !== this.userId || !this.dirty() || this.saving() || this.loading())
      return;
    const version = this.version;
    this.saving.set(true);
    this.error.set('');
    this.message.set('');
    this.write = this.api.save(data).subscribe({
      next: () => {
        if (version !== this.version) return;
        this.saving.set(false);
        this.load(data);
      },
      error: () => {
        if (version !== this.version) return;
        this.saving.set(false);
        this.load(data, true);
      }
    });
  }
}
