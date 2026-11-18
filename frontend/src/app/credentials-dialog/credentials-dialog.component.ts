import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  output,
  signal,
  viewChild
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../auth.service';

@Component({
  selector: 'app-credentials-dialog',
  imports: [FormsModule],
  templateUrl: './credentials-dialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CredentialsDialogComponent {
  private readonly auth = inject(AuthService);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  readonly closed = output<void>();
  username = '';
  password = '';
  readonly pending = signal(false);
  readonly error = signal('');

  open(): void {
    this.clear();
    this.dialog().nativeElement.showModal();
  }

  close(): void {
    this.clear();
    this.dialog().nativeElement.close();
  }

  onClose(): void {
    this.clear();
    this.closed.emit();
  }

  onCancel(event: Event): void {
    if (this.pending()) event.preventDefault();
    else this.clear();
  }

  clear(): void {
    this.username = '';
    this.password = '';
    this.error.set('');
  }

  submit(valid: boolean | null): void {
    if (this.pending() || !valid || !this.username.trim() || !this.password) return;
    this.pending.set(true);
    this.error.set('');
    const password = this.password;
    this.password = '';
    this.auth.changeCredentials(this.username, password).subscribe({
      next: () => {
        this.pending.set(false);
        this.close();
      },
      error: () => {
        this.password = '';
        this.pending.set(false);
        this.error.set(
          'Не удалось изменить логин и пароль. Проверьте данные и подключение к серверу.'
        );
      }
    });
  }
}
