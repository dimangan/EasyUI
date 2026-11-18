import { Routes } from '@angular/router';
import { authGuard } from './auth.guard';
import { LoginComponent } from './login/login.component';
import { UserInboundsComponent } from './user-inbounds/user-inbounds.component';
import { ShellComponent } from './shell/shell.component';
import { ServersComponent } from './servers/servers.component';
import { ServerInboundsComponent } from './server-inbounds/server-inbounds.component';
import { UsersComponent } from './users/users.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'servers' },
      { path: 'servers', component: ServersComponent },
      { path: 'servers/:id/inbounds', component: ServerInboundsComponent },
      { path: 'users', component: UsersComponent },
      { path: 'users/:id/inbounds', component: UserInboundsComponent }
    ]
  },
  { path: '**', redirectTo: '' }
];
