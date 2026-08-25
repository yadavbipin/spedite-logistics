import { Routes } from '@angular/router';

export const STAFF_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./layout/layout').then(m => m.Layout),

    children: [
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./pages/dashboard/dashboard').then(m => m.Dashboard)
      },
      {
        path: 'bookings',
        loadComponent: () =>
          import('./pages/bookings/bookings').then(m => m.Bookings)
      },
      {
        path: 'payments',
        loadComponent: () =>
          import('./pages/payments/payments').then(m => m.Payments)
      },

      {
        path: '',
        redirectTo: 'dashboard',
        pathMatch: 'full'
      }
    ]
  }
];
