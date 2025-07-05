// Copyright © 2025 Google LLC.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Observable, of, forkJoin, Subscription } from 'rxjs';
import { switchMap, catchError, map, tap, finalize } from 'rxjs/operators';
import { ApiService } from '../../services/api.service';
import { DeveloperApp } from '../../models/developer-app.model';
import { ApiProduct } from '../../models/api-product.model';
import { NavbarComponent } from '../navbar/navbar.component';
import { ExpiryDatePipe } from '../../pipes/expiry-date.pipe';

@Component({
  selector: 'app-developer-apps',
  standalone: true,
  imports: [CommonModule, NavbarComponent, FormsModule, ExpiryDatePipe],
  templateUrl: './developer-apps.component.html',
  styleUrls: ['./developer-apps.component.css']
})
export class DeveloperAppsComponent implements OnInit, OnDestroy { // Implement OnDestroy
  private apiService = inject(ApiService);
  private appsSubscription: Subscription | undefined; // To hold the subscription

  developerApps: DeveloperApp[] = [];
  isLoading = true;
  errorLoading = false;
  showCreateForm = false;
  newAppName = ''; // Model for the new app name input
  availableApiProducts: ApiProduct[] = [];
  selectedApiProducts = new Map<string, boolean>(); // Map to track selected products [productId, isSelected]
  apiProductFilter = ''; // Property to hold the filter text
  filteredApiProducts: ApiProduct[] = []; // Property for the filtered list
  isCreating = false; // Flag for create operation in progress
  createError: string | null = null;

  // Map to track visibility state of secrets [consumerKey -> isVisible]
  secretVisibility = new Map<string, boolean>();
  copiedNotification: string | null = null;

  ngOnInit(): void {
    this.loadInitialData();
  }

  private loadInitialData(): void {
    this.isLoading = true;
    this.errorLoading = false;
    this.developerApps = [];
    console.log('DeveloperAppsComponent: loadInitialData - Starting app loading (manual subscribe).');
    const appsLoadingPipeline$ = this.apiService.getDeveloperApps().pipe(
      tap(names => console.log('DeveloperAppsComponent: Received app names:', names)),
      switchMap(appNames => {
        if (!appNames || appNames.length === 0) {
          console.log('No developer app names found.');
          this.isLoading = false;
          return of([]); // Return empty array if no names
        }
        console.log('Found app names:', appNames);
        // Create an array of Observables, each fetching details for one app name
        const detailObservables = appNames.map(name =>
          this.apiService.getDeveloperAppDetails(name).pipe(
            tap(details => console.log(`DeveloperAppsComponent: Received details for app ${name}:`, details)),
            catchError(err => {
              console.error(`Failed to load details for app ${name}`, err);
              // Return a placeholder object on error for this specific app
              return of({ appId: '', name: `${name} (Error Loading)`, status: 'Error' } as DeveloperApp);
            })
          )
        );
        // Use forkJoin to wait for all detail requests to complete
        console.log(`DeveloperAppsComponent: Fetching details for ${detailObservables.length} apps.`);
        return forkJoin(detailObservables);
      }),
      tap(apps => console.log('DeveloperAppsComponent: forkJoin completed, apps with details:', apps)),
      map(apps => {
          // No need to set isLoading here, finalize handles it
          return apps;
      }),
      catchError(err => {
        console.error('DeveloperAppsComponent: Error in the main loading pipeline:', err); // Log pipeline error
        this.isLoading = false;
        this.errorLoading = true;
        return of([]); // Return empty array on overall pipeline error
      }),
      finalize(() => {
        console.log('DeveloperAppsComponent: Observable pipeline finalized.');
        // Finalize might run before error/next in some complex scenarios,
        // ensure isLoading is false *after* processing.
        // this.isLoading = false; // Moved setting isLoading to next/error handlers
      })
    );

    // Manually subscribe to trigger the pipeline
    this.appsSubscription = appsLoadingPipeline$.subscribe({
      next: (apps) => {
        console.log('DeveloperAppsComponent: Received final apps array:', apps);
        this.developerApps = apps;
        this.isLoading = false; // Set loading false on success
        this.errorLoading = false;
      },
      error: (err) => {
        console.error('DeveloperAppsComponent: Error subscribing to apps pipeline:', err);
        this.developerApps = []; // Clear apps on error
        this.isLoading = false; // Set loading false on error
        this.errorLoading = true;
      },
      complete: () => {
        console.log('DeveloperAppsComponent: Apps loading pipeline completed.');
        // Ensure loading is false on completion if next/error didn't run (e.g., empty initial list)
         if (this.isLoading) {
             console.log('DeveloperAppsComponent: Setting isLoading = false on completion.');
             this.isLoading = false;
         }
      }
    });
  }

  ngOnDestroy(): void {
    console.log('DeveloperAppsComponent: ngOnDestroy - Unsubscribing.');
    this.appsSubscription?.unsubscribe();
  }

  // Fetch available API products for the create form
  private loadAvailableProducts(): void {
    
    this.apiService.getApiProducts().subscribe({
      next: (products) => {
        this.availableApiProducts = products;
        this.filteredApiProducts = [...products]; 
        // Initialize selection map
        this.selectedApiProducts.clear();
        products.forEach(p => this.selectedApiProducts.set(p.id, false));
        console.log('DeveloperAppsComponent: Available API products loaded:', products);
      },
      error: (err) => {
        this.filteredApiProducts = []; // Clear filtered list on error too
        console.error('DeveloperAppsComponent: Failed to load available API products:', err);
        this.availableApiProducts = []; // Clear on error
      }
    });
  }

  // Toggle the create app form visibility
  toggleCreateForm(): void {
    this.showCreateForm = !this.showCreateForm;
    if (this.showCreateForm) {
      this.loadAvailableProducts(); // Fetch products needed for the create form
    }
    this.newAppName = ''; // Reset name on toggle
    this.createError = null; // Reset error
    // Reset selections
    this.availableApiProducts.forEach(p => this.selectedApiProducts.set(p.id, false));
  }

  // Handle the creation of a new app
  createApp(): void {
    if (!this.newAppName || this.newAppName.trim() === '') {
      this.createError = 'App name cannot be empty.';
      return;
    }

    const selectedProductNames = this.availableApiProducts
      .filter(p => this.selectedApiProducts.get(p.id))
      .map(p => p.id);

    if (selectedProductNames.length === 0) {
        this.createError = 'Please select at least one API Product.';
        return;
    }

    this.isCreating = true;
    this.createError = null;
    console.log(`DeveloperAppsComponent: Attempting to create app '${this.newAppName}' with products: ${selectedProductNames.join(', ')}`);

    this.apiService.createDeveloperApp(this.newAppName.trim(), selectedProductNames).subscribe({
      next: (newApp) => {
        console.log('DeveloperAppsComponent: App created successfully:', newApp);
        this.isCreating = false;
        this.showCreateForm = false;
        this.loadInitialData();
      },
      error: (err) => {
        console.error('DeveloperAppsComponent: Failed to create app:', err);
        this.isCreating = false;
        this.createError = `Failed to create app. ${err.error?.message || err.message || 'Please try again.'}`;
      }
    });
  }

  // Apply filter to the API product list
  applyFilter(): void {
    const filterValue = this.apiProductFilter.toLowerCase();
    this.filteredApiProducts = this.availableApiProducts.filter(product =>
      product.name.toLowerCase().includes(filterValue) ||
      product.description.toLowerCase().includes(filterValue)
    );
  }

  // Method to refresh the app list (can be called after create/delete)
  refreshApps(): void {
      this.loadInitialData();
  }

  // Handle deletion of an app
  deleteApp(appName: string): void {
    if (!appName) {
      console.error('Cannot delete app without a name.');
      return;
    }

    // // Confirmation dialog
    // if (!confirm(`Are you sure you want to delete the app "${appName}"? This action cannot be undone.`)) {
    //   return;
    // }

    console.log(`DeveloperAppsComponent: Attempting to delete app ${appName}`);
    // TODO: Add visual feedback for deletion in progress? (e.g., disable button, show spinner on the item)

    this.apiService.deleteDeveloperApp(appName).subscribe({
      next: () => {
        console.log(`App ${appName} deleted successfully.`);
        // Update the local list instead of reloading everything
        this.developerApps = this.developerApps.filter(app => app.name !== appName);
        console.log(`Local developer app list updated after deleting ${appName}.`);
        // No need to call refreshApps() anymore
      },
      error: (err) => {
        console.error(`Failed to delete app ${appName}:`, err);
        alert(`Failed to delete app ${appName}. ${err.error?.message || err.message || 'Please try again.'}`);
        // Optionally handle UI state here if needed
      }
    });
  }

  // Toggle visibility of a specific secret
  toggleSecretVisibility(consumerKey: string): void {
    const currentState = this.secretVisibility.get(consumerKey) ?? false;
    this.secretVisibility.set(consumerKey, !currentState);
  }

  copyToClipboard(dataItem: string | undefined, notificationId: string): void {
    if (!dataItem) {
      console.warn('Attempted to copy undefined dataItem.');
      return;
    }
    navigator.clipboard.writeText(dataItem).then(() => {
      console.log('dataItem copied to clipboard.');
      this.copiedNotification = notificationId;
      setTimeout(() => {
        if (this.copiedNotification === notificationId) {
          this.copiedNotification = null;
        }
      }, 2200); // Hide after 2.2s (total lifetime including fade-out)
    }).catch(err => {
      console.error('Failed to copy dataItem: ', err);
      alert('Failed to copy dataItem. See console for details.');
    });
  }

  // Helper to check visibility state in the template
  isSecretVisible(consumerKey: string): boolean {
    return this.secretVisibility.get(consumerKey) ?? false;
  }
}
