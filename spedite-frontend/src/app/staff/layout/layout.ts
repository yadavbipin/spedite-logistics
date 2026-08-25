// import { Component } from '@angular/core';

// @Component({
//   selector: 'app-layout',
//   imports: [],
//   templateUrl: './layout.html',
//   styleUrl: './layout.scss',
// })
// export class Layout {

// }


import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet} from '@angular/router';

interface SidebarSection {
  title: string;
  items: Array<{
    label: string;
    link: string;
    exact?: boolean;
    muted?: boolean;
  }>;
}

@Component({
  selector: 'app-staff-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './layout.html',
  styleUrls: ['./layout.scss']
})
export class Layout {
  readonly companyName = 'Spedite Logistics';
  readonly companyPhone = '7057378589';
  readonly versionLabel = 'v1.0.0';
  readonly financialYear = 'FY 2026-27';
  readonly sections: SidebarSection[] = [
    {
      title: 'Operations',
      items: [
        { label: 'Dashboard', link: '/staff/dashboard', exact: true },
        { label: 'Lorry Receipt', link: '/staff/bookings' },
      ],
    },
    {
      title: 'Workspaces',
      items: [
        { label: 'Search LRs', link: '/staff/bookings' },
        { label: 'Generate LR / Bilty', link: '/staff/bookings' },
        { label: 'Payments', link: '/staff/payments' },
      ],
    },
  ];
}
