export type BookingStatus = 'DRAFT' | 'BOOKED' | 'IN_TRANSIT' | 'DELIVERED' | 'CANCELLED';

export interface Address {
  city?: string;
  state?: string;
  country?: string;
  pinCode?: string;
  addressLine?: string;
}

export interface PartyDto {
  name?: string;
  gstNumber?: string;
  contactNumber?: string;
  email?: string[];
  address?: Address;
}

export interface DriverDetails {
  name?: string;
  mobile?: string;
  licenseNumber?: string;
}

export interface MaterialDto {
  materialName?: string;
  packagingType?: string;
  noOfArticles?: number;
  actualWeight?: number;
  chargedWeight?: number;
  rate?: number;
  hsnCode?: string;
  containerName?: string;
}

export interface FreightDetails {
  freightType?: string;
  basicFreight?: number;
  otherCharges?: Record<string, unknown>;
  gstDetails?: Record<string, unknown>;
  advanceDetails?: Record<string, unknown>;
  tdsDetails?: Record<string, unknown>;
  hideFreightInPdf?: boolean;
  settlementDetails?: FreightSettlementDetails;
  manualCharges?: FreightChargeItem[];
}

export interface FreightChargeItem {
  chargeType?: string;
  description?: string;
  amount?: number;
  taxable?: boolean;
  direction?: 'BILLABLE' | 'EXPENSE';
}

export interface FreightSettlementDetails {
  brokerAdvancePaid?: number;
  brokerBalancePaid?: number;
  consigneeReceivedAmount?: number;
  podReceivedDate?: string;
  paymentMode?: string;
  referenceNumber?: string;
  notes?: string;
}

export interface InsuranceDetails {
  insured?: boolean;
  insuranceCompany?: string;
  policyNumber?: string;
  insuranceDate?: string;
  insuranceAmount?: number;
  notes?: string;
}

export interface DemurrageDetails {
  applicable?: boolean;
  chargeType?: string;
  chargeAfter?: string;
}

export interface TruckDetails {
  truckNumber?: string;
  vehicleType?: string;
  fromLocation?: string;
  toLocation?: string;
  weightGuarantee?: number;
  loadType?: string;
  driver?: DriverDetails;
}

export interface BookingRequestDto {
  ewayBillNo?: string;
  lrDetails?: unknown;
  consignor?: PartyDto;
  consignee?: PartyDto;
  truckDetails?: TruckDetails;
  materials?: MaterialDto[];
  freightDetails?: FreightDetails;
  insuranceDetails?: InsuranceDetails;
  demurrageDetails?: DemurrageDetails;
  riskType?: string;
  transportMode?: string;
  loadingDate?: string;
  reportingDate?: string;
  remarks?: string;
}

export interface BookingEntity {
  bookingId: number;
  lrNumber: string;
  lrDate: string;
  ewayBillNo?: string;
  bookingStatus?: BookingStatus;
  transportMode?: string;
  riskType?: string;
  loadingDate?: string;
  reportingDate?: string;
  remarks?: string;
  truckDetails?: TruckDetails;
  freightDetails?: FreightDetails;
  insuranceDetails?: InsuranceDetails;
  demurrageDetails?: DemurrageDetails;
  consignor?: {
    consignorId?: number;
    name?: string;
    gstNumber?: string;
    contactNumber?: string;
    email?: string[];
    address?: Address;
  };
  consignee?: {
    consigneeId?: number;
    name?: string;
    gstNumber?: string;
    contactNumber?: string;
    email?: string[];
    address?: Address;
  };
  materialDetails?: MaterialDto[];
  createdAt?: string;
}
