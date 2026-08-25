import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  readonly metrics = [
    {
      value: '24',
      label: 'bookings today',
      detail: '+6 from yesterday',
      tone: 'blue'
    },
    {
      value: '12',
      label: 'on route',
      detail: '3 waiting for POD',
      tone: 'teal'
    },
    {
      value: '18 min',
      label: 'avg. processing time',
      detail: 'Draft to confirmation',
      tone: 'amber'
    },
    {
      value: '4',
      label: 'items needing attention',
      detail: 'Follow up before close',
      tone: 'rose'
    }
  ];

  readonly quickActions = [
    {
      title: 'Create booking',
      description: 'Open the LR editor and start a fresh shipment record.',
      link: '/staff/bookings',
      cta: 'Open editor'
    },
    {
      title: 'Review records',
      description: 'Search recent bookings, reopen drafts, or update details.',
      link: '/staff/bookings',
      cta: 'Search bookings'
    },
    {
      title: 'Visit public site',
      description: 'Check the customer-facing landing page and brand presentation.',
      link: '/',
      cta: 'Open homepage'
    }
  ];

  readonly workflow = [
    {
      step: '1',
      title: 'Capture the shipment',
      description: 'Load the route, parties, truck details, and freight terms.'
    },
    {
      step: '2',
      title: 'Track the status',
      description: 'Move the booking from draft to confirmed and on the road.'
    },
    {
      step: '3',
      title: 'Close the loop',
      description: 'Revisit PDFs, follow up on exceptions, and keep records clean.'
    }
  ];

  readonly recentBookings = [
    {
      id: 'LR-2041',
      customer: 'Apex Traders',
      route: 'Ahmedabad → Pune',
      status: 'BOOKED',
      amount: '₹8,300'
    },
    {
      id: 'LR-2042',
      customer: 'Nexa Supplies',
      route: 'Surat → Bengaluru',
      status: 'IN_TRANSIT',
      amount: '₹11,200'
    },
    {
      id: 'LR-2043',
      customer: 'Metro Freight',
      route: 'Rajkot → Jaipur',
      status: 'DRAFT',
      amount: '₹6,750'
    }
  ];
}
