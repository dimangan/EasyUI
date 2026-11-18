import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../auth.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule],
  templateUrl: './login.html',
  styleUrl: './login.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LoginComponent {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  username = '';
  password = '';
  readonly pending = signal(false);
  readonly error = signal('');

  submit(): void {
    if (this.pending() || !this.username || !this.password) return;
    this.pending.set(true);
    this.error.set('');
    const password = this.password;
    this.password = '';
    this.auth.login(this.username, password).subscribe({
      next: () => {
        this.password = '';
        this.pending.set(false);
        void this.router.navigate(['/servers']);
      },
      error: () => {
        this.password = '';
        this.pending.set(false);
        this.error.set('Не удалось войти. Проверьте данные и подключение к серверу.');
      }
    });
  }
}
