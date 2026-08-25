import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  Invoice,
  InvoicePayment,
  PaymentCreateRequest,
} from '../../../core/models/invoice.model';
import { InvoiceApiService } from '../../../core/services/invoice-api.service';

type PaymentFilter = 'ALL' | 'PENDING' | 'PARTIAL' | 'PAID' | 'OVERDUE';

interface PartyPerformance {
  name: string;
  invoiceCount: number;
  billed: number;
  received: number;
  outstanding: number;
  expenses: number;
  profitLoss: number;
}

interface LedgerEntry extends InvoicePayment {
  invoiceNumber: string;
  lrNumber: string;
  party: string;
}

@Component({
  selector: 'app-payments',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './payments.html',
  styleUrl: './payments.scss',
})
export class Payments implements OnInit {
  private readonly invoiceApi = inject(InvoiceApiService);
  private readonly fb = inject(FormBuilder);
  private readonly cdr = inject(ChangeDetectorRef);

  invoices: Invoice[] = [];
  selectedInvoice?: Invoice;
  filter: PaymentFilter = 'ALL';
  searchTerm = '';
  selectedParty = '';
  selectedMonth = '';
  selectedYear = '';
  loading = false;
  saving = false;
  openingPdfId?: number;
  successMessage = '';
  errorMessage = '';

  readonly paymentModes = ['NEFT', 'RTGS', 'IMPS', 'UPI', 'CHEQUE', 'CASH'];
  readonly months = [
    { value: '1', label: 'January' }, { value: '2', label: 'February' },
    { value: '3', label: 'March' }, { value: '4', label: 'April' },
    { value: '5', label: 'May' }, { value: '6', label: 'June' },
    { value: '7', label: 'July' }, { value: '8', label: 'August' },
    { value: '9', label: 'September' }, { value: '10', label: 'October' },
    { value: '11', label: 'November' }, { value: '12', label: 'December' },
  ];

  readonly paymentForm = this.fb.group({
    amount: [null as number | null, [Validators.required, Validators.min(0.01)]],
    receivedAt: ['', Validators.required],
    paymentMode: ['', Validators.required],
    referenceNumber: [''],
    notes: [''],
  });

  ngOnInit(): void {
    this.loadInvoices();
  }

  get years(): number[] {
    const values = new Set<number>([new Date().getFullYear()]);
    for (const invoice of this.invoices) {
      if (invoice.invoiceDate) values.add(new Date(`${invoice.invoiceDate}T00:00:00`).getFullYear());
      for (const payment of invoice.payments ?? []) {
        values.add(new Date(payment.receivedAt).getFullYear());
      }
    }
    return [...values].sort((a, b) => b - a);
  }

  get parties(): string[] {
    return [...new Set(this.invoices.map(invoice => this.consignorName(invoice)))]
      .filter(Boolean)
      .sort((a, b) => a.localeCompare(b));
  }

  get filteredInvoices(): Invoice[] {
    const query = this.searchTerm.trim().toLowerCase();
    return this.invoices.filter(invoice => {
      if (this.filter !== 'ALL' && this.displayStatus(invoice) !== this.filter) return false;
      if (this.selectedParty && this.consignorName(invoice) !== this.selectedParty) return false;
      if (!this.matchesPeriod(invoice.invoiceDate)) return false;
      if (!query) return true;
      return [
        invoice.invoiceNumber,
        invoice.booking?.lrNumber,
        this.consignorName(invoice),
        this.partyName(invoice),
      ].some(value => value?.toLowerCase().includes(query));
    });
  }

  get totalBilled(): number {
    return this.filteredInvoices.reduce((sum, invoice) => sum + (invoice.totalInvoiceAmount ?? 0), 0);
  }

  get totalReceived(): number {
    return this.filteredInvoices.reduce((sum, invoice) => sum + (invoice.consigneeReceivedAmount ?? 0), 0);
  }

  get totalOutstanding(): number {
    return this.filteredInvoices.reduce((sum, invoice) => sum + (invoice.amountPendingFromConsignee ?? 0), 0);
  }

  get totalProfitLoss(): number {
    return this.filteredInvoices.reduce((sum, invoice) => sum + (invoice.netProfitLossAmount ?? 0), 0);
  }

  get overdueCount(): number {
    return this.filteredInvoices.filter(invoice => this.displayStatus(invoice) === 'OVERDUE').length;
  }

  get partyPerformance(): PartyPerformance[] {
    const grouped = new Map<string, PartyPerformance>();
    for (const invoice of this.filteredInvoices) {
      const name = this.consignorName(invoice);
      const current = grouped.get(name) ?? {
        name, invoiceCount: 0, billed: 0, received: 0, outstanding: 0, expenses: 0, profitLoss: 0,
      };
      current.invoiceCount += 1;
      current.billed += invoice.totalInvoiceAmount ?? 0;
      current.received += invoice.consigneeReceivedAmount ?? 0;
      current.outstanding += invoice.amountPendingFromConsignee ?? 0;
      current.expenses += invoice.expenseChargesAmount ?? 0;
      current.profitLoss += invoice.netProfitLossAmount ?? 0;
      grouped.set(name, current);
    }
    return [...grouped.values()].sort((a, b) => b.profitLoss - a.profitLoss);
  }

  get paymentLedger(): LedgerEntry[] {
    const entries: LedgerEntry[] = [];
    for (const invoice of this.invoices) {
      if (this.selectedParty && this.consignorName(invoice) !== this.selectedParty) continue;
      for (const payment of invoice.payments ?? []) {
        if (!this.matchesPeriod(payment.receivedAt)) continue;
        entries.push({
          ...payment,
          invoiceNumber: invoice.invoiceNumber,
          lrNumber: invoice.booking?.lrNumber ?? '—',
          party: this.consignorName(invoice),
        });
      }
    }
    return entries.sort((a, b) => new Date(b.receivedAt).getTime() - new Date(a.receivedAt).getTime());
  }

  loadInvoices(): void {
    this.loading = true;
    this.errorMessage = '';
    this.invoiceApi.listInvoices().subscribe({
      next: invoices => {
        this.invoices = invoices;
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: error => {
        this.errorMessage = this.apiError(error, 'Could not load invoices.');
        this.loading = false;
        this.cdr.markForCheck();
      },
    });
  }

  setFilter(filter: PaymentFilter): void { this.filter = filter; }
  updateSearch(event: Event): void { this.searchTerm = (event.target as HTMLInputElement).value; }
  updateParty(event: Event): void { this.selectedParty = (event.target as HTMLSelectElement).value; }
  updateMonth(event: Event): void { this.selectedMonth = (event.target as HTMLSelectElement).value; }
  updateYear(event: Event): void { this.selectedYear = (event.target as HTMLSelectElement).value; }

  clearFilters(): void {
    this.filter = 'ALL';
    this.searchTerm = '';
    this.selectedParty = '';
    this.selectedMonth = '';
    this.selectedYear = '';
  }

  editPayment(invoice: Invoice): void {
    this.selectedInvoice = invoice;
    this.successMessage = '';
    this.errorMessage = '';
    this.paymentForm.reset({
      amount: null,
      receivedAt: this.localDateTimeValue(new Date()),
      paymentMode: '',
      referenceNumber: '',
      notes: '',
    });
  }

  closeEditor(): void { if (!this.saving) this.selectedInvoice = undefined; }

  savePayment(): void {
    if (!this.selectedInvoice || this.paymentForm.invalid) {
      this.paymentForm.markAllAsTouched();
      return;
    }

    const value = this.paymentForm.getRawValue();
    const payload: PaymentCreateRequest = {
      amount: Number(value.amount),
      receivedAt: value.receivedAt || undefined,
      paymentMode: value.paymentMode || undefined,
      referenceNumber: value.referenceNumber?.trim() || undefined,
      notes: value.notes?.trim() || undefined,
    };

    this.saving = true;
    this.errorMessage = '';
    this.invoiceApi.recordPayment(this.selectedInvoice.invoiceId, payload).subscribe({
      next: updated => {
        this.invoices = this.invoices.map(invoice => invoice.invoiceId === updated.invoiceId ? updated : invoice);
        this.successMessage = `${this.money(payload.amount)} received against ${updated.invoiceNumber}.`;
        this.selectedInvoice = undefined;
        this.saving = false;
        this.cdr.markForCheck();
      },
      error: error => {
        this.errorMessage = this.apiError(error, 'Could not record the payment.');
        this.saving = false;
        this.cdr.markForCheck();
      },
    });
  }

  openInvoicePdf(invoice: Invoice): void {
    this.openingPdfId = invoice.invoiceId;
    this.errorMessage = '';
    this.invoiceApi.getInvoicePdf(invoice.invoiceId).subscribe({
      next: pdf => {
        const url = URL.createObjectURL(pdf);
        window.open(url, '_blank', 'noopener');
        window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
        this.openingPdfId = undefined;
        this.cdr.markForCheck();
      },
      error: error => {
        this.errorMessage = this.apiError(error, 'Could not open the invoice PDF.');
        this.openingPdfId = undefined;
        this.cdr.markForCheck();
      },
    });
  }

  displayStatus(invoice: Invoice): PaymentFilter {
    if (invoice.paymentStatus === 'PAID') return 'PAID';
    if (this.isOverdue(invoice)) return 'OVERDUE';
    if (invoice.paymentStatus === 'PARTIAL' || (invoice.consigneeReceivedAmount ?? 0) > 0) return 'PARTIAL';
    return 'PENDING';
  }

  partyName(invoice: Invoice): string {
    return invoice.billToType === 'CONSIGNEE'
      ? invoice.booking?.consignee?.name ?? 'Consignee'
      : invoice.booking?.consignor?.name ?? 'Consignor';
  }

  consignorName(invoice: Invoice): string { return invoice.booking?.consignor?.name ?? 'Unknown consignor'; }
  profitLabel(value: number): string { return value > 0 ? 'Profit' : value < 0 ? 'Loss' : 'Break even'; }

  private matchesPeriod(value?: string): boolean {
    if (!value) return !this.selectedMonth && !this.selectedYear;
    const date = new Date(value.includes('T') ? value : `${value}T00:00:00`);
    if (this.selectedYear && date.getFullYear() !== Number(this.selectedYear)) return false;
    if (this.selectedMonth && date.getMonth() + 1 !== Number(this.selectedMonth)) return false;
    return true;
  }

  private isOverdue(invoice: Invoice): boolean {
    if (!invoice.dueDate || invoice.paymentStatus === 'PAID') return false;
    return new Date(`${invoice.dueDate}T23:59:59`).getTime() < Date.now();
  }

  private localDateTimeValue(date: Date): string {
    const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
    return local.toISOString().slice(0, 16);
  }

  private money(value: number): string {
    return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(value);
  }

  private apiError(error: { error?: { message?: string } }, fallback: string): string {
    return error?.error?.message || fallback;
  }
}
