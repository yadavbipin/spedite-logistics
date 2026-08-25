import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  readonly highlights = [
    {
      title: 'One workspace',
      description: 'Keep staff workflows together in one operations portal.'
    },
    {
      title: 'Faster dispatch',
      description: 'Jump straight into dashboards, bookings, and approvals.'
    },
    {
      title: 'Clear visibility',
      description: 'See the status of every shipment without digging around.'
    }
  ];
}
