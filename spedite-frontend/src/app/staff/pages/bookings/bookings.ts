import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnDestroy, OnInit, inject } from '@angular/core';
import { FormArray, FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  BookingEntity,
  BookingRequestDto,
  BookingStatus,
  FreightChargeItem,
  FreightSettlementDetails,
  MaterialDto,
} from '../../../core/models/booking.model';
import { Invoice } from '../../../core/models/invoice.model';
import { BookingApiService } from '../../../core/services/booking-api.service';
import { InvoiceApiService } from '../../../core/services/invoice-api.service';
import { LrApiService } from '../../../core/services/lr-api.service';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

type BookingFilter = 'ALL' | BookingStatus;

interface BuiltyPreviewMaterial {
  title: string;
  meta: string;
}

interface BuiltyPreviewModel {
  copies: string[];
  lrNumber: string;
  lrDate: string;
  status: string;
  readyToPrint: boolean;
  missingFields: string[];
  companyName: string;
  companyAddress: string;
  companyPhone: string;
  companyGstin: string;
  consignorName: string;
  consignorAddress: string;
  consignorPhone: string;
  consignorGst: string;
  consigneeName: string;
  consigneeAddress: string;
  consigneePhone: string;
  consigneeGst: string;
  route: string;
  truckNumber: string;
  vehicleType: string;
  loadingDate: string;
  reportingDate: string;
  ewayBillNo: string;
  freightType: string;
  riskType: string;
  transportMode: string;
  loadType: string;
  materials: BuiltyPreviewMaterial[];
  notes: string[];
}

@Component({
  selector: 'app-bookings',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './bookings.html',
  styleUrl: './bookings.scss',
})
export class Bookings implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly bookingApi = inject(BookingApiService);
  private readonly invoiceApi = inject(InvoiceApiService);
  private readonly lrApi = inject(LrApiService);

  readonly builtyCopyLabels = ['Driver Copy', 'Consignor Copy', 'Consignee Copy'];
  readonly companyName = 'Asian Trans Logistics';
  readonly companyAddress = 'Plot No. 04, Shivshakti Nagar, Amravati Road, Dattawadi, Nagpur, Maharashtra, 440023';
  readonly companyPhone = '7057378589';
  readonly companyGstin = '27ACDPY2673C2ZG';

  readonly lookupLrControl = new FormControl('', { nonNullable: true });
  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly statusControl = new FormControl<BookingFilter>('ALL', { nonNullable: true });

  createChargeGroup(): FormGroup {
    return this.fb.group({
      // Define your form controls for the charge group here
      chargeName: [''],
      amount: [0]
    });
  }

  readonly bookingForm = this.fb.group({
    ewayBillNo: [''],
    transportMode: ['ROAD', Validators.required],
    riskType: ['OWNER_RISK', Validators.required],
    loadingDate: [''],
    reportingDate: [''],
    remarks: [''],
    consignor: this.createPartyGroup(),
    consignee: this.createPartyGroup(),
    truckDetails: this.fb.group({
      truckNumber: [''],
      vehicleType: [''],
      fromLocation: [''],
      toLocation: [''],
      weightGuarantee: [null as number | null],
      loadType: ['FULL_LOAD'],
      driver: this.fb.group({
        name: [''],
        mobile: [''],
        licenseNumber: [''],
      }),
    }),
    freightDetails: this.fb.group({
      freightType: ['PAID'],
      basicFreight: [null as number | null],
      hideFreightInPdf: [false],
      settlementDetails: this.fb.group({
        brokerAdvancePaid: [null as number | null],
        brokerBalancePaid: [null as number | null],
        consigneeReceivedAmount: [null as number | null],
        podReceivedDate: [''],
        paymentMode: [''],
        referenceNumber: [''],
        notes: [''],
      }),
      manualCharges: this.fb.array<FormGroup>([this.createChargeGroup()]),
    }),
    insuranceDetails: this.fb.group({
      insured: [false],
      insuranceCompany: [''],
      policyNumber: [''],
      insuranceDate: [''],
      insuranceAmount: [null as number | null],
      notes: [''],
    }),
    demurrageDetails: this.fb.group({
      applicable: [false],
      chargeType: ['PER_HOUR'],
      chargeAfter: ['1 hour'],
    }),
    materials: this.fb.array<FormGroup>([this.createMaterialGroup()]),
  });

  isLoading = false;
  errorMessage = '';
  successMessage = '';
  connectionStatus: 'checking' | 'online' | 'offline' = 'checking';
  activeTab: 'overview' | 'manage' | 'finance' = 'overview';
  currentBooking?: BookingEntity;
  currentInvoice?: Invoice;
  recentBookings: BookingEntity[] = [];
  builtyPreview: BuiltyPreviewModel = this.createBuiltyPreview();
  private autoPersistTimer?: ReturnType<typeof setTimeout>;
  private autoPersistInFlight = false;

  ngOnInit(): void {
    this.bookingForm.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.refreshBuiltyPreview();
        this.scheduleAutoPersist();
      });
    this.syncEditorAvailability();
    this.loadBookings();
  }

  ngOnDestroy(): void {
    this.clearAutoPersistTimer();
  }

  get materials(): FormArray<FormGroup> {
    return this.bookingForm.get('materials') as FormArray<FormGroup>;
  }

  get freightCharges(): FormArray<FormGroup> {
    return this.bookingForm.get('freightDetails.manualCharges') as FormArray<FormGroup>;
  }

  get settlementDetailsGroup(): FormGroup {
    return this.bookingForm.get('freightDetails.settlementDetails') as FormGroup;
  }

  get confirmedBookings(): BookingEntity[] {
    return this.recentBookings.filter(booking => this.isBookedBooking(booking));
  }

  get onRouteBookings(): BookingEntity[] {
    return this.recentBookings.filter(booking => this.normalizeBookingStatus(booking.bookingStatus) === 'IN_TRANSIT');
  }

  get draftBookings(): BookingEntity[] {
    return this.recentBookings.filter(booking => this.normalizeBookingStatus(booking.bookingStatus) === 'DRAFT');
  }

  get vehiclesBookedCount(): number {
    return this.recentBookings.length;
  }

  addMaterial(): void {
    this.materials.push(this.createMaterialGroup());
  }

  removeMaterial(index: number): void {
    if (this.materials.length > 1) {
      this.materials.removeAt(index);
    }
  }

  addCharge(): void {
    this.freightCharges.push(this.createChargeGroup());
  }

  removeCharge(index: number): void {
    if (this.freightCharges.length > 1) {
      this.freightCharges.removeAt(index);
    }
  }

  loadBookings(options?: { preserveMessages?: boolean }): void {
    this.isLoading = true;
    if (!options?.preserveMessages) {
      this.clearMessages();
    }

    this.bookingApi.listBookings({
      status: this.statusControl.value === 'ALL' ? undefined : this.statusControl.value,
      query: this.searchControl.value.trim() || undefined,
    }).subscribe({
      next: bookings => {
        this.isLoading = false;
        this.connectionStatus = 'online';
        this.syncEditorAvailability();
        this.recentBookings = bookings;
      },
      error: (error: HttpErrorResponse) => {
        this.isLoading = false;
        if (this.isBackendUnavailable(error)) {
          this.connectionStatus = 'offline';
          this.syncEditorAvailability();
          this.recentBookings = [];
          this.errorMessage = 'Backend is offline right now. Start the API server to load and save LR drafts.';
          return;
        }
        this.errorMessage = this.extractError(error);
      },
    });
  }

  startNewBooking(): void {
    this.currentBooking = undefined;
    this.lookupLrControl.setValue('');
    this.clearAutoPersistTimer();
    this.activeTab = 'manage';
    this.resetForm();
    this.syncBookingFormState();
    this.refreshBuiltyPreview();
    this.clearMessages();
  }

  loadBookingByLr(lrNumberInput?: string): void {
    const lrNumber = (lrNumberInput ?? this.lookupLrControl.value).trim();
    if (!lrNumber) {
      this.errorMessage = 'Enter LR number to load booking.';
      return;
    }

    this.isLoading = true;
    this.clearMessages();
    this.activeTab = 'manage';
    this.scrollWorkspaceToTop();

    this.bookingApi.getBookingByLr(lrNumber).subscribe({
      next: booking => {
        this.isLoading = false;
        this.connectionStatus = 'online';
        this.syncEditorAvailability();
        this.openForEdit(booking);
        this.successMessage = this.isBookingLocked()
          ? `Loaded approved LR ${booking.lrNumber} in view-only mode.`
          : `Loaded booking ${booking.lrNumber}`;
      },
      error: (error: HttpErrorResponse) => {
        this.isLoading = false;
        if (this.isBackendUnavailable(error)) {
          this.connectionStatus = 'offline';
        }
        this.errorMessage = this.extractError(error);
      },
    });
  }

  openForEdit(booking: BookingEntity): void {
    this.currentBooking = booking;
    this.lookupLrControl.setValue(booking.lrNumber ?? '');
    this.clearAutoPersistTimer();
    this.patchFormFromBooking(booking);
    this.syncBookingFormState();
    this.upsertRecentBooking(booking);
    this.activeTab = 'manage';
    this.scrollWorkspaceToTop();
    this.bookingForm.markAsPristine();
    this.bookingForm.markAsUntouched();
    this.refreshBuiltyPreview();
  }

  editBooking(booking: BookingEntity): void {
    const lrNumber = booking.lrNumber?.trim();
    if (!lrNumber) {
      this.errorMessage = 'Booking is missing an LR number.';
      return;
    }

    this.connectionStatus = 'online';
    this.syncEditorAvailability();
    this.openForEdit(booking);

    if (this.isBookedBooking(booking)) {
      this.successMessage = `Loaded approved LR ${lrNumber} in view-only mode.`;
    } else {
      this.successMessage = `Loaded booking ${lrNumber}`;
    }
  }

  createBooking(): void {
    this.persistBuiltyDraft({ mode: 'create', switchToOverview: true, showSuccessMessage: true });
  }

  updateBooking(): void {
    const lrNumber = this.currentBooking?.lrNumber?.trim();
    if (!lrNumber) {
      this.errorMessage = 'Load or create a booking before updating.';
      return;
    }

    if (this.isBookingLocked()) {
      this.errorMessage = 'Approved bookings are view-only. Start a new draft to make changes.';
      return;
    }

    this.persistBuiltyDraft({ mode: 'update', switchToOverview: true, showSuccessMessage: true });
  }

  confirmBooking(): void {
    const lrNumber = this.currentBooking?.lrNumber?.trim();
    if (!lrNumber) {
      this.errorMessage = 'Load or create a booking before confirming.';
      return;
    }

    this.isLoading = true;
    this.clearMessages();

    this.bookingApi.confirmBooking(lrNumber).subscribe({
      next: booking => {
        this.isLoading = false;
        this.connectionStatus = 'online';
        this.syncEditorAvailability();
        this.currentBooking = booking;
        this.upsertRecentBooking(booking);
        this.bookingForm.markAsPristine();
        this.bookingForm.markAsUntouched();
        this.refreshBuiltyPreview();
        this.successMessage = `Booking confirmed for ${booking.lrNumber}`;
        this.activeTab = 'overview';
        this.loadBookings({ preserveMessages: true });
      },
      error: (error: HttpErrorResponse) => {
        this.isLoading = false;
        if (this.isBackendUnavailable(error)) {
          this.connectionStatus = 'offline';
        }
        this.errorMessage = this.extractError(error);
      },
    });
  }

  markDelivered(booking?: BookingEntity): void {
    const lrNumber = booking?.lrNumber?.trim() ?? this.currentBooking?.lrNumber?.trim();
    if (!lrNumber) {
      this.errorMessage = 'Load or create a booking before marking it delivered.';
      return;
    }

    this.isLoading = true;
    this.clearMessages();

    this.bookingApi.markDelivered(lrNumber).subscribe({
      next: updatedBooking => {
        this.isLoading = false;
        this.connectionStatus = 'online';
        this.syncEditorAvailability();
        this.currentBooking = updatedBooking;
        this.upsertRecentBooking(updatedBooking);
        this.bookingForm.markAsPristine();
        this.bookingForm.markAsUntouched();
        this.refreshBuiltyPreview();
        this.successMessage = `Booking marked delivered for ${updatedBooking.lrNumber}`;
        this.loadBookings({ preserveMessages: true });
      },
      error: (error: HttpErrorResponse) => {
        this.isLoading = false;
        if (this.isBackendUnavailable(error)) {
          this.connectionStatus = 'offline';
        }
        this.errorMessage = this.extractError(error);
      },
    });
  }

  openLrPdf(booking?: BookingEntity): void {
    const lrNumber = booking?.lrNumber?.trim() ?? this.currentBooking?.lrNumber?.trim();
    if (!lrNumber) {
      this.errorMessage = 'Load or create a booking first to open LR PDF.';
      return;
    }

    if (booking) {
      if (!this.hasPrintableBuiltyFields(booking)) {
        this.errorMessage = this.buildMissingFieldsMessage(booking);
        return;
      }
    } else if (!this.canPrintCurrentBuilty()) {
      this.errorMessage = this.buildMissingFieldsMessage(this.currentBooking);
      return;
    }

    this.isLoading = true;
    this.clearMessages();

    this.lrApi.getLrPdfByLrNumber(lrNumber).subscribe({
      next: blob => {
        this.isLoading = false;
        this.connectionStatus = 'online';
        this.syncEditorAvailability();
        this.openPdfBlob(blob);
        this.successMessage = `Opened LR PDF for ${lrNumber}`;
      },
      error: (error: HttpErrorResponse) => {
        this.isLoading = false;
        if (this.isBackendUnavailable(error)) {
          this.connectionStatus = 'offline';
        }
        this.errorMessage = this.extractError(error);
      },
    });
  }

  openInvoicePdf(booking?: BookingEntity): void {
    const bookingId = booking?.bookingId ?? this.currentBooking?.bookingId;
    if (!bookingId) {
      this.errorMessage = 'Load or create a delivered booking first to open invoice PDF.';
      return;
    }

    this.isLoading = true;
    this.clearMessages();

    this.invoiceApi.getInvoicePdfByBooking(bookingId).subscribe({
      next: blob => {
        this.isLoading = false;
        this.connectionStatus = 'online';
        this.syncEditorAvailability();
        this.openPdfBlob(blob);
        this.successMessage = `Opened invoice PDF for booking ${bookingId}`;
      },
      error: (error: HttpErrorResponse) => {
        this.isLoading = false;
        if (this.isBackendUnavailable(error)) {
          this.connectionStatus = 'offline';
        }
        this.errorMessage = this.extractError(error);
      },
    });
  }

  bookingRoute(booking: BookingEntity): string {
    const from = booking.truckDetails?.fromLocation?.trim();
    const to = booking.truckDetails?.toLocation?.trim();
    return from || to ? `${from || '-'} -> ${to || '-'}` : '-';
  }

  bookingVehicle(booking: BookingEntity): string {
    return booking.truckDetails?.truckNumber || '-';
  }

  private createPartyGroup(): FormGroup {
    return this.fb.group({
      name: [''],
      gstNumber: [''],
      contactNumber: [''],
      emailText: [''],
      address: this.fb.group({
        addressLine: [''],
        city: [''],
        state: [''],
        country: ['India'],
        pinCode: [''],
      }),
    });
  }

  private createMaterialGroup(material?: MaterialDto): FormGroup {
    return this.fb.group({
      materialName: [material?.materialName ?? ''],
      packagingType: [material?.packagingType ?? ''],
      noOfArticles: [material?.noOfArticles ?? null],
      actualWeight: [material?.actualWeight ?? null],
      chargedWeight: [material?.chargedWeight ?? null],
      rate: [material?.rate ?? null],
      hsnCode: [material?.hsnCode ?? ''],
      containerName: [material?.containerName ?? ''],
    });
  }

  private resetForm(): void {
    this.bookingForm.reset({
      transportMode: 'ROAD',
      riskType: 'OWNER_RISK',
      consignor: {
        emailText: '',
        address: { country: 'India' },
      },
      consignee: {
        emailText: '',
        address: { country: 'India' },
      },
      truckDetails: {
        loadType: 'FULL_LOAD',
      },
      freightDetails: {
        freightType: 'PAID',
        hideFreightInPdf: false,
      },
      insuranceDetails: {
        insured: false,
      },
      demurrageDetails: {
        applicable: false,
        chargeType: 'PER_HOUR',
        chargeAfter: '1 hour',
      },
    });
    this.replaceMaterials([{}]);
    this.bookingForm.markAsPristine();
    this.bookingForm.markAsUntouched();
    this.refreshBuiltyPreview();
  }

  private replaceMaterials(materials: MaterialDto[]): void {
    this.materials.clear();
    materials.forEach(item => this.materials.push(this.createMaterialGroup(item)));
    if (this.materials.length === 0) {
      this.materials.push(this.createMaterialGroup());
    }
  }

  private buildPayload(): BookingRequestDto {
    const value = this.bookingForm.getRawValue();
    const materials = (value.materials ?? [])
      .map(material => ({
        materialName: this.asText(material['materialName']),
        packagingType: this.asText(material['packagingType']),
        noOfArticles: this.asNumber(material['noOfArticles']),
        actualWeight: this.asNumber(material['actualWeight']),
        chargedWeight: this.asNumber(material['chargedWeight']),
        rate: this.asNumber(material['rate']),
        hsnCode: this.asText(material['hsnCode']),
        containerName: this.asText(material['containerName']),
      }))
      .filter(material => Object.values(material).some(Boolean));

    return {
      ewayBillNo: this.asText(value.ewayBillNo),
      transportMode: this.asText(value.transportMode),
      riskType: this.asText(value.riskType),
      loadingDate: this.asText(value.loadingDate),
      reportingDate: this.asText(value.reportingDate),
      remarks: this.asText(value.remarks),
      consignor: this.buildPartyPayload(value.consignor),
      consignee: this.buildPartyPayload(value.consignee),
      truckDetails: {
        truckNumber: this.asText(value.truckDetails?.truckNumber),
        vehicleType: this.asText(value.truckDetails?.vehicleType),
        fromLocation: this.asText(value.truckDetails?.fromLocation),
        toLocation: this.asText(value.truckDetails?.toLocation),
        weightGuarantee: this.asNumber(value.truckDetails?.weightGuarantee),
        loadType: this.asText(value.truckDetails?.loadType),
        driver: {
          name: this.asText(value.truckDetails?.driver?.name),
          mobile: this.asText(value.truckDetails?.driver?.mobile),
          licenseNumber: this.asText(value.truckDetails?.driver?.licenseNumber),
        },
      },
      freightDetails: {
        freightType: this.asText(value.freightDetails?.freightType),
        basicFreight: this.asNumber(value.freightDetails?.basicFreight),
        hideFreightInPdf: Boolean(value.freightDetails?.hideFreightInPdf),
      },
      insuranceDetails: {
        insured: Boolean(value.insuranceDetails?.insured),
        insuranceCompany: this.asText(value.insuranceDetails?.insuranceCompany),
        policyNumber: this.asText(value.insuranceDetails?.policyNumber),
        insuranceDate: this.asText(value.insuranceDetails?.insuranceDate),
        insuranceAmount: this.asNumber(value.insuranceDetails?.insuranceAmount),
        notes: this.asText(value.insuranceDetails?.notes),
      },
      demurrageDetails: {
        applicable: Boolean(value.demurrageDetails?.applicable),
        chargeType: this.asText(value.demurrageDetails?.chargeType),
        chargeAfter: this.asText(value.demurrageDetails?.chargeAfter),
      },
      materials,
    };
  }

  private buildPartyPayload(value: any) {
    return {
      name: this.asText(value?.name),
      gstNumber: this.asText(value?.gstNumber),
      contactNumber: this.asText(value?.contactNumber),
      email: this.asEmailList(value?.emailText),
      address: {
        addressLine: this.asText(value?.address?.addressLine),
        city: this.asText(value?.address?.city),
        state: this.asText(value?.address?.state),
        country: this.asText(value?.address?.country),
        pinCode: this.asText(value?.address?.pinCode),
      },
    };
  }

  private patchFormFromBooking(booking: BookingEntity): void {
    this.bookingForm.patchValue({
      ewayBillNo: booking.ewayBillNo ?? '',
      transportMode: booking.transportMode ?? 'ROAD',
      riskType: booking.riskType ?? 'OWNER_RISK',
      loadingDate: booking.loadingDate ?? '',
      reportingDate: booking.reportingDate ?? '',
      remarks: booking.remarks ?? '',
      consignor: {
        name: booking.consignor?.name ?? '',
        gstNumber: booking.consignor?.gstNumber ?? '',
        contactNumber: booking.consignor?.contactNumber ?? '',
        emailText: (booking.consignor?.email ?? []).join(', '),
        address: {
          addressLine: booking.consignor?.address?.addressLine ?? '',
          city: booking.consignor?.address?.city ?? '',
          state: booking.consignor?.address?.state ?? '',
          country: booking.consignor?.address?.country ?? 'India',
          pinCode: booking.consignor?.address?.pinCode ?? '',
        },
      },
      consignee: {
        name: booking.consignee?.name ?? '',
        gstNumber: booking.consignee?.gstNumber ?? '',
        contactNumber: booking.consignee?.contactNumber ?? '',
        emailText: (booking.consignee?.email ?? []).join(', '),
        address: {
          addressLine: booking.consignee?.address?.addressLine ?? '',
          city: booking.consignee?.address?.city ?? '',
          state: booking.consignee?.address?.state ?? '',
          country: booking.consignee?.address?.country ?? 'India',
          pinCode: booking.consignee?.address?.pinCode ?? '',
        },
      },
      truckDetails: {
        truckNumber: booking.truckDetails?.truckNumber ?? '',
        vehicleType: booking.truckDetails?.vehicleType ?? '',
        fromLocation: booking.truckDetails?.fromLocation ?? '',
        toLocation: booking.truckDetails?.toLocation ?? '',
        weightGuarantee: booking.truckDetails?.weightGuarantee ?? null,
        loadType: booking.truckDetails?.loadType ?? 'FULL_LOAD',
        driver: {
          name: booking.truckDetails?.driver?.name ?? '',
          mobile: booking.truckDetails?.driver?.mobile ?? '',
          licenseNumber: booking.truckDetails?.driver?.licenseNumber ?? '',
        },
      },
      freightDetails: {
        freightType: booking.freightDetails?.freightType ?? 'PAID',
        basicFreight: booking.freightDetails?.basicFreight ?? null,
        hideFreightInPdf: booking.freightDetails?.hideFreightInPdf ?? false,
      },
      insuranceDetails: {
        insured: booking.insuranceDetails?.insured ?? false,
        insuranceCompany: booking.insuranceDetails?.insuranceCompany ?? '',
        policyNumber: booking.insuranceDetails?.policyNumber ?? '',
        insuranceDate: booking.insuranceDetails?.insuranceDate ?? '',
        insuranceAmount: booking.insuranceDetails?.insuranceAmount ?? null,
        notes: booking.insuranceDetails?.notes ?? '',
      },
      demurrageDetails: {
        applicable: booking.demurrageDetails?.applicable ?? false,
        chargeType: booking.demurrageDetails?.chargeType ?? 'PER_HOUR',
        chargeAfter: booking.demurrageDetails?.chargeAfter ?? '1 hour',
      },
    });

    this.replaceMaterials(booking.materialDetails ?? []);
    this.refreshBuiltyPreview();
  }

  private refreshBuiltyPreview(): void {
    this.builtyPreview = this.createBuiltyPreview();
  }

  private createBuiltyPreview(): BuiltyPreviewModel {
    const value = this.bookingForm.getRawValue() as any;
    const consignor = value.consignor ?? {};
    const consignee = value.consignee ?? {};
    const truck = value.truckDetails ?? {};
    const freight = value.freightDetails ?? {};
    const previewMaterials: Array<MaterialDto | undefined> = Array.isArray(value.materials)
      ? value.materials
      : [];
    const materials = previewMaterials
      .map((material: MaterialDto | undefined, index: number) => this.createPreviewMaterial(material, index))
      .filter((material: BuiltyPreviewMaterial) => material.title !== '-' || material.meta !== '-');

    const fallbackMaterials = materials.length > 0
      ? materials
      : [{ title: 'No material entered yet', meta: 'Add material rows to complete the preview' }];

    const missingFields = this.getMissingBuiltyFields(this.currentBooking);

    return {
      copies: [...this.builtyCopyLabels],
      lrNumber: this.currentBooking?.lrNumber?.trim() || 'Draft',
      lrDate: this.currentBooking?.lrDate || this.previewText(value.loadingDate),
      status: this.currentBooking?.bookingStatus || 'DRAFT',
      readyToPrint: !!this.currentBooking?.lrNumber && !this.bookingForm.dirty && missingFields.length === 0,
      missingFields,
      companyName: this.companyName,
      companyAddress: this.companyAddress,
      companyPhone: this.companyPhone,
      companyGstin: this.companyGstin,
      consignorName: this.previewText(consignor.name),
      consignorAddress: this.formatAddressPreview(consignor.address),
      consignorPhone: this.previewText(consignor.contactNumber),
      consignorGst: this.previewText(consignor.gstNumber),
      consigneeName: this.previewText(consignee.name),
      consigneeAddress: this.formatAddressPreview(consignee.address),
      consigneePhone: this.previewText(consignee.contactNumber),
      consigneeGst: this.previewText(consignee.gstNumber),
      route: this.buildRoutePreview(truck['fromLocation'], truck['toLocation']),
      truckNumber: this.previewText(truck['truckNumber']),
      vehicleType: this.previewText(truck['vehicleType']),
      loadingDate: this.previewText(value.loadingDate),
      reportingDate: this.previewText(value.reportingDate),
      ewayBillNo: this.previewText(value.ewayBillNo),
      freightType: this.formatFreightType(freight.freightType),
      riskType: this.formatRiskType(value.riskType),
      transportMode: this.formatTransportMode(value.transportMode),
      loadType: this.formatLoadType(truck.loadType),
      materials: fallbackMaterials,
      notes: this.buildPreviewNotes(value),
    };
  }

  private createPreviewMaterial(material: MaterialDto | undefined, index: number): BuiltyPreviewMaterial {
    if (!material) {
      return { title: '-', meta: '-' };
    }

    const meta = [
      material.packagingType ? material.packagingType.toUpperCase() : '',
      material.noOfArticles !== undefined && material.noOfArticles !== null ? `${material.noOfArticles} NOS` : '',
      material.actualWeight !== undefined && material.actualWeight !== null ? `ACT ${material.actualWeight} KG` : '',
      material.chargedWeight !== undefined && material.chargedWeight !== null ? `CHG ${material.chargedWeight} MT` : '',
      material.hsnCode ?? '',
    ]
      .map(item => item.trim())
      .filter(Boolean)
      .join(' • ');

    return {
      title: `${index + 1}. ${this.previewText(material['materialName'], 'Material')}`,
      meta: meta || 'Add quantity, weight, or HSN details',
    };
  }

  private buildPreviewNotes(value: any): string[] {
    return [
      `Transport mode: ${this.formatTransportMode(value.transportMode)}`,
      `Risk type: ${this.formatRiskType(value.riskType)}`,
      `Loading date: ${this.previewText(value.loadingDate)}`,
      `Reporting date: ${this.previewText(value.reportingDate)}`,
      `E-Way bill: ${this.previewText(value.ewayBillNo)}`,
    ];
  }

  private getMissingBuiltyFields(booking?: BookingEntity): string[] {
    const value = this.bookingForm.getRawValue() as any;
    const issues = [
      { label: 'Consignor name', ok: this.hasText(value.consignor?.name) || this.hasText(booking?.consignor?.name) },
      { label: 'Consignee name', ok: this.hasText(value.consignee?.name) || this.hasText(booking?.consignee?.name) },
      { label: 'Loading date', ok: this.hasText(value.loadingDate) || this.hasText(booking?.loadingDate) },
      { label: 'Truck number', ok: this.hasText(value.truckDetails?.truckNumber) || this.hasText(booking?.truckDetails?.truckNumber) },
      { label: 'From location', ok: this.hasText(value.truckDetails?.fromLocation) || this.hasText(booking?.truckDetails?.fromLocation) },
      { label: 'To location', ok: this.hasText(value.truckDetails?.toLocation) || this.hasText(booking?.truckDetails?.toLocation) },
      {
        label: 'At least one material',
        ok: (value.materials ?? []).some((material: any) => this.hasText(material?.materialName)) ||
          (booking?.materialDetails ?? []).some(material => this.hasText(material?.materialName)),
      },
    ];

    return issues.filter(issue => !issue.ok).map(issue => issue.label);
  }

  private hasPrintableBuiltyFields(booking?: BookingEntity): boolean {
    return this.getMissingBuiltyFields(booking).length === 0;
  }

  canPrintCurrentBuilty(): boolean {
    return !!this.currentBooking?.lrNumber && !this.bookingForm.dirty && this.hasPrintableBuiltyFields(this.currentBooking);
  }

  private scheduleAutoPersist(): void {
    if (this.bookingForm.pristine || this.isLoading || this.autoPersistInFlight || this.connectionStatus !== 'online' || this.isBookingLocked()) {
      return;
    }

    if (!this.shouldAutoPersist()) {
      return;
    }

    this.clearAutoPersistTimer();
    this.autoPersistTimer = setTimeout(() => {
      void this.persistBuiltyDraft({
        mode: this.currentBooking?.lrNumber ? 'update' : 'create',
        switchToOverview: false,
        showSuccessMessage: false,
      });
    }, 900);
  }

  private shouldAutoPersist(): boolean {
    if (this.isBookingLocked()) {
      return false;
    }

    if (!this.currentBooking?.lrNumber) {
      return this.hasDraftContent();
    }

    return this.bookingForm.dirty;
  }

  private async persistBuiltyDraft(options: {
    mode: 'create' | 'update';
    switchToOverview: boolean;
    showSuccessMessage: boolean;
  }): Promise<void> {
    if (this.isLoading || this.autoPersistInFlight) {
      return;
    }

    if (this.bookingForm.invalid) {
      if (options.showSuccessMessage) {
        this.errorMessage = 'Please fill the required booking fields.';
      }
      return;
    }

    if (options.mode === 'create' && !this.hasDraftContent()) {
      if (options.showSuccessMessage) {
        this.errorMessage = 'Fill at least one booking detail before saving the draft.';
      }
      return;
    }

    const lrNumber = this.currentBooking?.lrNumber?.trim();
    if (options.mode === 'update' && !lrNumber) {
      if (options.showSuccessMessage) {
        this.errorMessage = 'Load or create a booking before updating.';
      }
      return;
    }

    if (options.mode === 'update' && this.isBookingLocked()) {
      if (options.showSuccessMessage) {
        this.errorMessage = 'Approved bookings are view-only. Start a new draft to save changes.';
      }
      return;
    }

    this.autoPersistInFlight = true;
    this.isLoading = true;
    if (options.showSuccessMessage) {
      this.clearMessages();
    }

    const request = options.mode === 'create'
      ? this.bookingApi.createBooking(this.buildPayload())
      : this.bookingApi.updateBooking(lrNumber as string, this.buildPayload());

    request.subscribe({
      next: booking => {
        this.isLoading = false;
        this.autoPersistInFlight = false;
        this.connectionStatus = 'online';
        this.syncEditorAvailability();
        this.currentBooking = booking;
        this.lookupLrControl.setValue(booking.lrNumber ?? '');
        this.upsertRecentBooking(booking);
        this.bookingForm.markAsPristine();
        this.bookingForm.markAsUntouched();
        this.refreshBuiltyPreview();
        this.clearAutoPersistTimer();

        if (options.showSuccessMessage) {
          this.successMessage = options.mode === 'create'
            ? `Draft saved with LR ${booking.lrNumber}`
            : `Booking updated for ${booking.lrNumber}`;
        }

        if (options.switchToOverview) {
          this.statusControl.setValue('ALL');
          this.searchControl.setValue('');
          this.activeTab = 'overview';
          this.loadBookings({ preserveMessages: true });
        } else {
          this.upsertRecentBooking(booking);
        }
      },
      error: (error: HttpErrorResponse) => {
        this.isLoading = false;
        this.autoPersistInFlight = false;
        if (this.isBackendUnavailable(error)) {
          this.connectionStatus = 'offline';
          this.syncEditorAvailability();
          this.errorMessage = 'Backend is offline right now. Start the API server to save LR drafts.';
          return;
        }
        if (options.showSuccessMessage) {
          this.errorMessage = this.extractError(error);
        }
      },
    });
  }

  private clearAutoPersistTimer(): void {
    if (this.autoPersistTimer) {
      clearTimeout(this.autoPersistTimer);
      this.autoPersistTimer = undefined;
    }
  }

  private hasDraftContent(): boolean {
    const value = this.bookingForm.getRawValue();
    const scalars = [
      value.ewayBillNo,
      value.loadingDate,
      value.reportingDate,
      value.remarks,
      value.consignor?.['name'],
      value.consignor?.['gstNumber'],
      value.consignor?.['contactNumber'],
      value.consignee?.['name'],
      value.consignee?.['gstNumber'],
      value.consignee?.['contactNumber'],
      value.truckDetails?.['truckNumber'],
      value.truckDetails?.['vehicleType'],
      value.truckDetails?.['fromLocation'],
      value.truckDetails?.['toLocation'],
      value.truckDetails?.['driver']?.['name'],
      value.truckDetails?.['driver']?.['mobile'],
      value.truckDetails?.['driver']?.['licenseNumber'],
      value.freightDetails?.['basicFreight'],
      value.insuranceDetails?.['insuranceCompany'],
      value.insuranceDetails?.['policyNumber'],
      value.insuranceDetails?.['insuranceDate'],
      value.insuranceDetails?.['insuranceAmount'],
      value.insuranceDetails?.['notes'],
      value.demurrageDetails?.['chargeAfter'],
    ];

    if (scalars.some(item => this.hasText(item) || this.asNumber(item) !== undefined)) {
      return true;
    }

    return (value.materials ?? []).some(material =>
      this.hasText(material?.['materialName']) ||
      this.hasText(material?.['packagingType']) ||
      this.hasText(material?.['hsnCode']) ||
      this.hasText(material?.['containerName']) ||
      this.asNumber(material?.['noOfArticles']) !== undefined ||
      this.asNumber(material?.['actualWeight']) !== undefined ||
      this.asNumber(material?.['chargedWeight']) !== undefined ||
      this.asNumber(material?.['rate']) !== undefined
    );
  }

  private isBackendUnavailable(error: HttpErrorResponse): boolean {
    return error.status === 0;
  }

  private normalizeBookingStatus(status?: string | null): string {
    return (status ?? '').trim().toUpperCase();
  }

  isBookedBooking(booking?: BookingEntity): boolean {
    return this.normalizeBookingStatus(booking?.bookingStatus) === 'BOOKED';
  }

  private syncEditorAvailability(): void {
    const disabled = this.connectionStatus !== 'online';

    if (disabled) {
      if (this.bookingForm.enabled) {
        this.bookingForm.disable({ emitEvent: false });
      }
      if (this.lookupLrControl.enabled) {
        this.lookupLrControl.disable({ emitEvent: false });
      }
      if (this.searchControl.enabled) {
        this.searchControl.disable({ emitEvent: false });
      }
      if (this.statusControl.enabled) {
        this.statusControl.disable({ emitEvent: false });
      }
      return;
    }

    if (this.bookingForm.disabled) {
      this.bookingForm.enable({ emitEvent: false });
    }
    if (this.lookupLrControl.disabled) {
      this.lookupLrControl.enable({ emitEvent: false });
    }
    if (this.searchControl.disabled) {
      this.searchControl.enable({ emitEvent: false });
    }
    if (this.statusControl.disabled) {
      this.statusControl.enable({ emitEvent: false });
    }
  }

  private syncBookingFormState(): void {
    if (this.isBookingLocked()) {
      if (this.bookingForm.enabled) {
        this.bookingForm.disable({ emitEvent: false });
      }
      return;
    }

    if (this.bookingForm.disabled) {
      this.bookingForm.enable({ emitEvent: false });
    }
  }

  isBookingLocked(): boolean {
    return this.isBookedBooking(this.currentBooking);
  }

  private scrollWorkspaceToTop(): void {
    if (typeof window === 'undefined') {
      return;
    }

    window.requestAnimationFrame(() => {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    });
  }

  private buildMissingFieldsMessage(booking?: BookingEntity): string {
    const missing = this.getMissingBuiltyFields(booking);
    if (!missing.length) {
      return 'Save the booking before printing the builty PDF.';
    }

    return `Fill ${missing.join(', ')} before printing the builty PDF.`;
  }

  private buildRoutePreview(from?: string | null, to?: string | null): string {
    const fromText = this.previewText(from);
    const toText = this.previewText(to);
    return fromText === '-' && toText === '-' ? '-' : `${fromText} -> ${toText}`;
  }

  private formatTransportMode(value?: string | null): string {
    const text = this.previewText(value);
    if (text === '-') {
      return '-';
    }
    return text.toUpperCase();
  }

  private formatRiskType(value?: string | null): string {
    switch ((value || '').trim().toUpperCase()) {
      case 'OWNER_RISK':
        return "Owner's Risk";
      case 'CARRIER_RISK':
        return "Carrier's Risk";
      default:
        return this.previewText(value);
    }
  }

  private formatLoadType(value?: string | null): string {
    switch ((value || '').trim().toUpperCase()) {
      case 'FULL_LOAD':
        return 'Full Load';
      case 'PART_LOAD':
        return 'Part Load';
      default:
        return this.previewText(value);
    }
  }

  private formatFreightType(value?: string | null): string {
    switch ((value || '').trim().toUpperCase()) {
      case 'PAID':
        return 'Paid';
      case 'TO_PAY':
        return 'To Pay';
      case 'TO_BE_BILLED':
        return 'To Be Billed';
      default:
        return this.previewText(value);
    }
  }

  private formatAddressPreview(address?: { addressLine?: string | null; city?: string | null; state?: string | null; country?: string | null; pinCode?: string | null } | null): string {
    if (!address) {
      return '-';
    }

    const parts = [address.addressLine, address.city, address.state, address.country, address.pinCode]
      .map(item => this.previewText(item))
      .filter(item => item !== '-');

    return parts.length ? parts.join(', ') : '-';
  }

  private previewText(value: unknown, fallback = '-'): string {
    if (value === null || value === undefined) {
      return fallback;
    }
    const text = String(value).trim();
    return text ? text : fallback;
  }

  private hasText(value: unknown): boolean {
    return this.previewText(value) !== '-';
  }

  private asText(value: unknown): string | undefined {
    const text = typeof value === 'string' ? value.trim() : '';
    return text ? text : undefined;
  }

  private asNumber(value: unknown): number | undefined {
    if (value === null || value === undefined || value === '') {
      return undefined;
    }
    const numberValue = Number(value);
    return Number.isFinite(numberValue) ? numberValue : undefined;
  }

  private asEmailList(value: unknown): string[] | undefined {
    const text = typeof value === 'string' ? value.trim() : '';
    if (!text) {
      return undefined;
    }
    const emails = text.split(',').map(item => item.trim()).filter(Boolean);
    return emails.length ? emails : undefined;
  }

  private extractError(error: HttpErrorResponse): string {
    const body = error.error as { message?: string } | string | null;
    if (typeof body === 'string' && body.trim()) {
      return body;
    }
    if (body && typeof body === 'object' && body.message) {
      return body.message;
    }
    return error.message || 'Request failed';
  }

  private clearMessages(): void {
    this.errorMessage = '';
    this.successMessage = '';
  }

  private openPdfBlob(blob: Blob): void {
    const url = URL.createObjectURL(blob);
    window.open(url, '_blank', 'noopener');
    setTimeout(() => URL.revokeObjectURL(url), 3000);
  }

  private upsertRecentBooking(booking: BookingEntity): void {
    if (!booking.lrNumber) {
      return;
    }

    const index = this.recentBookings.findIndex(item => item.lrNumber === booking.lrNumber);
    if (index >= 0) {
      this.recentBookings[index] = booking;
      this.recentBookings = [...this.recentBookings];
      return;
    }

    this.recentBookings = [booking, ...this.recentBookings];
  }
}
