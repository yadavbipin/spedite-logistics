import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { Invoice, PaymentCreateRequest, PaymentUpdateRequest } from '../models/invoice.model';

@Injectable({ providedIn: 'root' })
export class InvoiceApiService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = `${API_BASE_URL}/invoices`;

  generateInvoice(bookingId: number): Observable<Invoice> {
    return this.http.post<Invoice>(`${this.endpoint}/generate/${bookingId}`, {});
  }

  listInvoices(): Observable<Invoice[]> {
    return this.http.get<Invoice[]>(this.endpoint);
  }

  updatePayment(invoiceId: number, payload: PaymentUpdateRequest): Observable<Invoice> {
    return this.http.put<Invoice>(`${this.endpoint}/${invoiceId}/payment`, payload);
  }

  recordPayment(invoiceId: number, payload: PaymentCreateRequest): Observable<Invoice> {
    return this.http.post<Invoice>(`${this.endpoint}/${invoiceId}/payments`, payload);
  }

  getInvoiceById(invoiceId: number): Observable<Invoice> {
    return this.http.get<Invoice>(`${this.endpoint}/${invoiceId}`);
  }

  getInvoiceByBooking(bookingId: number): Observable<Invoice> {
    return this.http.get<Invoice>(`${this.endpoint}/booking/${bookingId}`);
  }

  getInvoicePdf(invoiceId: number): Observable<Blob> {
    return this.http.get(`${this.endpoint}/${invoiceId}/pdf`, {
      responseType: 'blob',
    });
  }

  getInvoicePdfByBooking(bookingId: number): Observable<Blob> {
    return this.http.get(`${this.endpoint}/booking/${bookingId}/pdf`, {
      responseType: 'blob',
    });
  }
}
