export interface Invoice {
  invoiceId: number;
  invoiceNumber: string;
  booking?: {
    bookingId?: number;
    lrNumber?: string;
    consignor?: { name?: string };
    consignee?: { name?: string };
  };
  billToType?: string;
  billToId?: number;
  invoiceDate?: string;
  dueDate?: string;
  subtotalAmount?: number;
  gstApplicable?: boolean;
  gstPercent?: number;
  cgst?: number;
  sgst?: number;
  igst?: number;
  totalInvoiceAmount?: number;
  invoiceStatus?: string;
  paymentStatus?: 'PENDING' | 'PARTIAL' | 'PAID';
  createdAt?: string;
  charges?: InvoiceCharge[];
  brokerAdvancePaidAmount?: number;
  brokerBalancePaidAmount?: number;
  brokerTotalPaidAmount?: number;
  consigneeReceivedAmount?: number;
  expenseChargesAmount?: number;
  billableExtraChargesAmount?: number;
  amountPendingFromConsignee?: number;
  netProfitLossAmount?: number;
  profitLossBasis?: string;
  profitLossStatus?: string;
  podReceivedDate?: string;
  settlementSummary?: string;
  payments?: InvoicePayment[];
}

export interface InvoicePayment {
  paymentId: number;
  amount: number;
  receivedAt: string;
  paymentMode?: string;
  referenceNumber?: string;
  notes?: string;
  createdAt?: string;
}

export interface PaymentCreateRequest {
  amount: number;
  receivedAt?: string;
  paymentMode?: string;
  referenceNumber?: string;
  notes?: string;
}

export interface PaymentUpdateRequest {
  receivedAmount: number;
  paymentMode?: string;
  referenceNumber?: string;
  podReceivedDate?: string;
  notes?: string;
}

export interface InvoiceCharge {
  chargeId?: number;
  chargeType?: string;
  description?: string;
  rate?: number;
  quantity?: number;
  amount?: number;
  taxable?: boolean;
}
