import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';

@Injectable({ providedIn: 'root' })
export class LrApiService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = `${API_BASE_URL}/lr`;

  getLrPdfByLrNumber(lrNumber: string): Observable<Blob> {
    return this.http.get(`${this.endpoint}/${encodeURIComponent(lrNumber)}/pdf`, {
      responseType: 'blob',
    });
  }
}
