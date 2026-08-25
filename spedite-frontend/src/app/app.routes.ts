import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadChildren: () =>
      import('./public/public.routes').then(r => r.PUBLIC_ROUTES)
  },
  {
    path: 'staff',
    loadChildren: () =>
      import('./staff/staff.routes').then(r => r.STAFF_ROUTES)
  },
  {
    path: 'auth',
    loadChildren: () =>
      import('./auth/auth.routes').then(r => r.AUTH_ROUTES)
  },
  {
    path: '**',
    redirectTo: ''
  }
];
