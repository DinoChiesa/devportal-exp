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
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Observable, BehaviorSubject, combineLatest, of } from 'rxjs';
import { map, startWith, tap } from 'rxjs/operators';
import { ApiService } from '../../services/api.service';
import { ApiProduct } from '../../models/api-product.model';
import { AuthService } from '../../services/auth.service';
import { NavbarComponent } from '../navbar/navbar.component';

@Component({
  selector: 'app-api-catalog',
  standalone: true,
  imports: [CommonModule, FormsModule, NavbarComponent],
  templateUrl: './api-catalog.component.html',
  styleUrls: ['./api-catalog.component.css']
})
export class ApiCatalogComponent implements OnInit {
  private apiService = inject(ApiService);
  private authService = inject(AuthService);

  // Subject to hold the current search term
  private searchTerm = new BehaviorSubject<string>('');
  // Observable for the search term
  searchTerm$ = this.searchTerm.asObservable();

  filteredProducts$: Observable<ApiProduct[]> | undefined;
  // Array to hold all products fetched from the API
  private allProducts: ApiProduct[] = [];

  isLoading = true; // Flag for loading state

  ngOnInit(): void {
    // Fetch all products once
    this.apiService.getApiProducts().pipe(
      tap(() => this.isLoading = true) // Set loading true before fetch
    ).subscribe({
      next: (products) => {
        this.allProducts = products;
        this.initializeFilteredProducts(); // Initialize filtering after data arrives
        this.isLoading = false; // Set loading false after fetch
        console.log('API Products loaded:', this.allProducts);
      },
      error: (err) => {
        console.error('Error loading API products in component:', err);
        this.allProducts = []; // Ensure it's an empty array on error
        this.initializeFilteredProducts(); // Still initialize filtering
        this.isLoading = false; // Set loading false on error
      }
    });
  }

  private initializeFilteredProducts(): void {
    // Combine the latest search term and the full product list
    this.filteredProducts$ = combineLatest([
      this.searchTerm$.pipe(startWith('')), // Start with an empty search term
      of(this.allProducts) // Use 'of' to create an observable from the array
    ]).pipe(
      map(([term, products]) => {
        const lowerCaseTerm = term.toLowerCase();
        if (!lowerCaseTerm) {
          return products; // If no search term, return all products
        }
        // Filter products based on name containing the search term (case-insensitive)
        return products.filter(product =>
          product.name.toLowerCase().includes(lowerCaseTerm)
        );
      })
    );
  }


  // Method to update the search term subject when input changes
  onSearchTermChange(event: Event): void {
    const inputElement = event.target as HTMLInputElement;
    this.searchTerm.next(inputElement.value);
  }

  // Logout method removed, handled by NavbarComponent now
}
