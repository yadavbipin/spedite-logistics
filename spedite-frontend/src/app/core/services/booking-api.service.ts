import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { API_BASE_URL } from '../config/api.config';
import { BookingEntity, BookingRequestDto, FreightDetails } from '../models/booking.model';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class BookingApiService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = `${API_BASE_URL}/bookings`;

  createBooking(payload: BookingRequestDto): Observable<BookingEntity> {
    return this.http.post<BookingEntity>(this.endpoint, payload);
  }

  listBookings(filters?: { status?: string; query?: string }): Observable<BookingEntity[]> {
    let params = new HttpParams();

    if (filters?.status) {
      params = params.set('status', filters.status);
    }
    if (filters?.query) {
      params = params.set('q', filters.query);
    }

    return this.http.get<BookingEntity[]>(this.endpoint, { params });
  }

  getBookingByLr(lrNumber: string): Observable<BookingEntity> {
    return this.http.get<BookingEntity>(`${this.endpoint}/${encodeURIComponent(lrNumber)}`);
  }

  updateBooking(lrNumber: string, payload: BookingRequestDto): Observable<BookingEntity> {
    return this.http.put<BookingEntity>(`${this.endpoint}/${encodeURIComponent(lrNumber)}`, payload);
  }

  updateFinancialDetails(lrNumber: string, freightDetails: FreightDetails): Observable<BookingEntity> {
    return this.http.put<BookingEntity>(
      `${this.endpoint}/${encodeURIComponent(lrNumber)}/financials`,
      freightDetails
    );
  }

  confirmBooking(lrNumber: string): Observable<BookingEntity> {
    return this.http.post<BookingEntity>(`${this.endpoint}/confirm/${encodeURIComponent(lrNumber)}`, {});
  }

  markDelivered(lrNumber: string): Observable<BookingEntity> {
    return this.http.post<BookingEntity>(`${this.endpoint}/deliver/${encodeURIComponent(lrNumber)}`, {});
  }
}
