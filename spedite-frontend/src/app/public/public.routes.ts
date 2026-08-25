import { Routes } from '@angular/router';
import { Layout } from './layout/layout';
import { Home } from './pages/home/home';

export const PUBLIC_ROUTES: Routes = [
  {
    path: '',
    component: Layout,
    children: [
      {
        path: '',
        component: Home
      }
    ]
  }
];
