import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, from, switchMap, of, Subject, timer } from 'rxjs'; // Added Subject, timer
import { catchError, shareReplay, takeUntil, tap } from 'rxjs/operators'; // Added shareReplay, takeUntil, tap
import { AuthService } from './auth.service';
import { ApiProduct } from '../models/api-product.model';
import { DeveloperApp } from '../models/developer-app.model';
import { DeveloperDetails } from '../models/developer-details.model'; // Import DeveloperDetails

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private http = inject(HttpClient);
  private authService = inject(AuthService);
  private apiUrl = '/api';
  private readonly CACHE_LIFETIME_MS = 90 * 1000; // 10 seconds

  // --- Cache for Developer Apps ---
  private developerAppsCache$: Observable<string[]> | null = null;
  private developerAppsCacheTimestamp: number | null = null;
  // Cache for individual app details (keyed by app name)
  // Store observable and timestamp together
  private appDetailsCache = new Map<string, { observable: Observable<DeveloperApp>, timestamp: number }>();
  // Cache for the main developer details
  private developerDetailsCache$: Observable<DeveloperDetails> | null = null;
  private developerDetailsCacheTimestamp: number | null = null;
  // --- End Cache ---

  constructor() { }

  /**
   * Fetches the list of API products from the backend.
   * Includes the Firebase ID token in the Authorization header.
   * @returns Observable<ApiProduct[]>
   */
  getApiProducts(): Observable<ApiProduct[]> {
    // No longer need to check getIdToken() here, rely on session cookie
    console.log('ApiService: > getApiProducts'); 
    // Add withCredentials: true to ensure cookies are sent
    return this.http.get<ApiProduct[]>(`${this.apiUrl}/apiproducts`, { withCredentials: true }).pipe(
      catchError(error => {
        console.error('Error fetching API products:', error);
        // If unauthorized (401), the backend before filter should handle it.
        // The browser request might fail, or the component might get an error.
        // Handle error appropriately, e.g., return empty array or re-throw
        return of([]); // Return empty array on error
      })
    );
  }

  /**
   * Fetches the list of developer app names for the authenticated user.
   * Implements caching with a 60-second lifetime using shareReplay.
   * Relies on the session cookie being sent automatically by the browser.
   * @returns Observable<string[]>
   */
  getDeveloperApps(): Observable<string[]> {
    const now = Date.now();
    const isCacheValid = this.developerAppsCache$ && this.developerAppsCacheTimestamp && (now - this.developerAppsCacheTimestamp < this.CACHE_LIFETIME_MS);

    if (isCacheValid) {
      console.log('ApiService: > getDeveloperApps - Returning valid cached observable.');
      return this.developerAppsCache$!; // Non-null assertion as isCacheValid checks it
    }

    // Cache is invalid or doesn't exist, create new observable
    console.log(`ApiService: > getDeveloperApps - Cache invalid or missing. Creating new observable. Timestamp: ${this.developerAppsCacheTimestamp}, Now: ${now}`);
    this.developerAppsCache$ = this.http.get<string[]>(`${this.apiUrl}/myapps`, { withCredentials: true }).pipe(
      tap(() => {
        console.log('ApiService: Fetched fresh developer app names from backend.');
        this.developerAppsCacheTimestamp = Date.now(); // Update timestamp on successful fetch
      }),
      catchError(error => {
        console.error('ApiService: Error fetching developer app names:', error);
        this.clearDeveloperAppsCache();
        return of([]); // Return empty array on error
      }),
      shareReplay({ bufferSize: 1, refCount: true }) // Share amongst subscribers, clean up if refCount hits 0
    );

    return this.developerAppsCache$;
  }

  /**
   * Manually invalidates the developer apps cache.
   */
  clearDeveloperAppsCache(): void {
    console.log('ApiService: Clearing developer apps list and details caches.');
    this.developerAppsCache$ = null; // Clear the list observable
    this.developerAppsCacheTimestamp = null;
    this.appDetailsCache.clear(); // Clear the individual details map
    // No need to call .next() on a subject anymore
  }

  /**
   * Fetches the detailed information for a specific developer app.
   * Relies on the session cookie being sent automatically by the browser.
   * @param appName The name of the app to fetch.
   * @returns Observable<DeveloperApp>
   */
  getDeveloperAppDetails(appName: string): Observable<DeveloperApp> {
    const now = Date.now();
    const cachedEntry = this.appDetailsCache.get(appName);
    const isCacheValid = cachedEntry && (now - cachedEntry.timestamp < this.CACHE_LIFETIME_MS);

    if (isCacheValid) {
       console.log(`ApiService: > getDeveloperAppDetails - Returning valid cached observable for app: ${appName}.`);
       return cachedEntry.observable;
    }

    // Cache is invalid or doesn't exist for this app
    console.log(`ApiService: > getDeveloperAppDetails - Cache invalid or missing for app: ${appName}. Creating new observable.`);
    const detail$ = this.http.get<DeveloperApp>(`${this.apiUrl}/myapps/${appName}`, { withCredentials: true }).pipe(
      tap(() => {
        console.log(`ApiService: Fetched fresh details for app ${appName} from backend.`);
        // Update timestamp within the map entry upon successful fetch
        const existingEntry = this.appDetailsCache.get(appName);
        if (existingEntry) {
            existingEntry.timestamp = Date.now();
        }
        // Note: We don't update the timestamp directly here because the observable
        // might be shared. Instead, we update it when setting the cache entry below.
      }),
      catchError(error => {
        console.error(`Error fetching details for app ${appName}:`, error);
        this.appDetailsCache.delete(appName); // Remove failed entry from cache
        throw error; // Re-throw the error to be handled by the component
      }),
      shareReplay({ bufferSize: 1, refCount: true })
    );

    // Store the new observable and the current timestamp
    this.appDetailsCache.set(appName, { observable: detail$, timestamp: Date.now() });
    return detail$;
  }

  /**
   * Creates a new developer app for the authenticated user.
   * @param appName The desired name for the new app.
   * @param selectedProducts An array of API Product names to associate with the app.
   * @returns Observable<DeveloperApp> The details of the newly created app.
   */
  createDeveloperApp(appName: string, selectedProducts: string[]): Observable<DeveloperApp> {
    console.log(`ApiService: > createDeveloperApp, name: ${appName}, products: ${selectedProducts.join(', ')}`);
    const payload = { name: appName, apiProducts: selectedProducts };
    return this.http.post<DeveloperApp>(`${this.apiUrl}/myapps`, payload, { withCredentials: true }).pipe(
      tap(newApp => {
         console.log('ApiService: App created:', newApp);
         this.clearDeveloperAppsCache(); // Invalidate cache after creating an app
      }),
      catchError(error => {
        console.error(`Error creating developer app ${appName}:`, error);
        throw error; // Re-throw error to be handled by the component
      })
    );
  }

  /**
   * Deletes a specific developer app for the authenticated user.
   * @param appName The name of the app to delete.
   * @returns Observable<any> The response from the backend (likely empty on success).
   */
  deleteDeveloperApp(appName: string): Observable<any> {
    console.log(`ApiService: > deleteDeveloperApp, appName: ${appName}`);
    return this.http.delete<any>(`${this.apiUrl}/myapps/${appName}`, { withCredentials: true }).pipe(
      tap(() => {
        console.log(`ApiService: App "${appName}" deleted successfully.`);
        this.clearDeveloperAppsCache(); // Invalidate cache after deleting an app
      }),
      catchError(error => {
        console.error(`Error deleting developer app ${appName}:`, error);
        throw error; // Re-throw error to be handled by the component
      })
    );
  }

  /**
   * Fetches the details for the currently authenticated developer.
   * Relies on the session cookie being sent automatically by the browser.
   * @returns Observable<DeveloperDetails>
   */
  getDeveloperDetails(): Observable<DeveloperDetails> {
    const now = Date.now();
    const isCacheValid = this.developerDetailsCache$ && this.developerDetailsCacheTimestamp && (now - this.developerDetailsCacheTimestamp < this.CACHE_LIFETIME_MS);

    if (isCacheValid) {
      console.log('ApiService: > getDeveloperDetails - Returning valid cached observable.');
      return this.developerDetailsCache$!; // Non-null assertion
    }

    console.log(`ApiService: > getDeveloperDetails - Cache invalid or missing. Creating new observable. Timestamp: ${this.developerDetailsCacheTimestamp}, Now: ${now}`);
    this.developerDetailsCache$ = this.http.get<DeveloperDetails>(`${this.apiUrl}/me`, { withCredentials: true }).pipe(
      tap(() => {
        console.log('ApiService: Fetched fresh developer details from backend.');
        this.developerDetailsCacheTimestamp = Date.now(); // Update timestamp on success
      }),
      catchError(error => {
        console.error('ApiService: Error fetching developer details:', error);
        this.clearDeveloperDetailsCache();
        throw error; // Re-throw error to be handled by the component
      }),
      shareReplay({ bufferSize: 1, refCount: true }) // Share amongst subscribers
    );

    return this.developerDetailsCache$;
  }

  /**
   * Manually invalidates the developer details cache.
   */
  clearDeveloperDetailsCache(): void {
    console.log('ApiService: Clearing developer details cache.');
    this.developerDetailsCache$ = null;
    this.developerDetailsCacheTimestamp = null;
  }

  /**
   * Sends a new public key, requesting a generated certificate for the authenticated developer.
   * @param keyId The unique identifier generated for the key pair.
   * @param publicKeyPem The public key in PEM format.
   * @returns Observable<any> The response from the backend (structure TBD).
   */
  generateAndRegisterCertificate(keyId: string, publicKeyPem: string): Observable<any> {
    console.log(`ApiService: > generateAndRegisterCertificate, keyId: ${keyId}`);
    const payload = {
      keyId: keyId,
      publicKey: publicKeyPem
    };
    // Assuming the backend endpoint is /api/me/certificates
    return this.http.post<any>(`${this.apiUrl}/me/certificates`, payload, { withCredentials: true }).pipe(
      tap(() => {
        console.log(`ApiService: Certificate registered (keyId: ${keyId}), clearing details cache.`);
        this.clearDeveloperDetailsCache(); // Invalidate details cache
      }),
      catchError(error => {
        console.error(`Error registering certificate (keyId: ${keyId}):`, error);
        throw error; // Re-throw error to be handled by the component
      })
    );
  }

  /**
   * Uploads an existing public key certificate for the authenticated developer.
   * @param keyId A placeholder or identifier (backend might ignore or use it).
   * @param certPem The x509 certificate in PEM format.
   * @returns Observable<any> The response from the backend (structure TBD).
   */
  uploadCertificate(keyId: string, certPem: string): Observable<any> {
    console.log(`ApiService: > uploadCertificate, keyId: ${keyId}`);
    const payload = {
      keyId: keyId, // You might not need keyId if backend derives it or ignores it for upload
      certificate: certPem
    };
    // Assuming the backend endpoint is /api/me/certificates (POST)
    // Adjust endpoint if necessary
    return this.http.post<any>(`${this.apiUrl}/me/certificates`, payload, { withCredentials: true })
      .pipe(
        tap(() => {
          console.log(`ApiService: Certificate uploaded (keyId: ${keyId}), clearing details cache.`);
          this.clearDeveloperDetailsCache(); // Invalidate details cache
        }),
        catchError(error => {
          console.error(`Error uploading certificate (keyId: ${keyId}):`, error);
          throw error; // Re-throw error to be handled by the component
        })
      );
  }

  /**
   * De-registers (deletes) a specific certificate for the authenticated developer.
   * @param certId The ID of the certificate to delete.
   * @returns Observable<any> The response from the backend (likely empty on success).
   */
  deregisterCertificate(certId: string): Observable<any> {
    console.log(`ApiService: > deregisterCertificate, certId: ${certId}`);
    // Assuming the backend endpoint is /api/me/certificates/{certId}
    return this.http.delete<any>(`${this.apiUrl}/me/certificates/${certId}`, { withCredentials: true }).pipe(
      tap(() => {
        console.log(`ApiService: Certificate deregistered (certId: ${certId}), clearing details cache.`);
        this.clearDeveloperDetailsCache(); // Invalidate details cache
      }),
      catchError(error => {
        console.error(`Error de-registering certificate (certId: ${certId}):`, error);
        throw error; // Re-throw error to be handled by the component
      })
    );
  }

  /**
   * Triggers the backend to register the currently authenticated user
   * (based on their session) as a developer in Apigee.
   * @returns Observable<any> The response from the backend (likely developer details on success).
   */
  registerSelfAsDeveloper(): Observable<any> {
    console.log('ApiService: > registerSelfAsDeveloper');
    // The backend endpoint uses session details, so no payload needed from frontend
    return this.http.post<any>(`${this.apiUrl}/registerSelfAsDeveloper`, {}, { withCredentials: true }).pipe(
      tap(() => {
        console.log('ApiService: Registered self as developer, clearing details cache.');
        this.clearDeveloperDetailsCache(); // Invalidate details cache
      }),
      catchError(error => {
        console.error('Error registering self as developer:', error);
        throw error; // Re-throw error to be handled by the component/service
      })
    );
  }

  clearAllCaches(): void {
    this.clearDeveloperAppsCache();
    this.clearDeveloperDetailsCache();
  }

  // Add other API methods here (e.g., getProductById, createProduct, etc.)
}
