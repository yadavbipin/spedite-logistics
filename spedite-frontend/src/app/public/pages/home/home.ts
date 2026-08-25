import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {
  readonly stats = [
    { value: '3', label: 'print-ready LR copies', tone: 'blue' },
    { value: 'Live', label: 'booking visibility', tone: 'teal' },
    { value: '3', label: 'steps to confirm a booking', tone: 'amber' }
  ];

  readonly services = [
    {
      title: 'Smart booking flow',
      description: 'A calmer, guided experience for capturing consignor, consignee, and truck details.',
      note: 'Save drafts, resume later, and keep records tidy.'
    },
    {
      title: 'Fast document turnaround',
      description: 'Generate LR and invoice-ready records with a cleaner, more confident workflow.',
      note: 'Designed for busy dispatch desks.'
    },
    {
      title: 'Operations visibility',
      description: 'Track draft, booked, and in-transit work from a single dashboard snapshot.',
      note: 'No guesswork during peak hours.'
    }
  ];

  readonly steps = [
    {
      step: '01',
      title: 'Plan the load',
      description: 'Capture the route, freight type, and special handling notes.'
    },
    {
      step: '02',
      title: 'Confirm the booking',
      description: 'Review details, save the draft, and lock in the shipment when ready.'
    },
    {
      step: '03',
      title: 'Stay in sync',
      description: 'Use the staff portal to revisit records and keep everyone aligned.'
    }
  ];
}
