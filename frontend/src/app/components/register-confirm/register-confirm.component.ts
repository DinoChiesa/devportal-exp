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
import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';
import { HttpErrorResponse } from '@angular/common/http';
import { lastValueFrom } from 'rxjs'; 

@Component({
  selector: 'app-register-confirm',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './register-confirm.component.html',
  styleUrls: ['./register-confirm.component.css']
})
export class RegisterConfirmComponent {
  private apiService = inject(ApiService);
  private authService = inject(AuthService);
  private router = inject(Router);

  isProcessing = false;
  errorMessage: string | null = null;

  async confirmRegistration(): Promise<void> {
    if (this.isProcessing) return;

    this.isProcessing = true;
    this.errorMessage = null;
    console.log('RegisterConfirmComponent: Confirming registration...');

    try {
      // Use lastValueFrom to convert Observable to Promise
      await lastValueFrom(this.apiService.registerSelfAsDeveloper());
      console.log('RegisterConfirmComponent: Registration successful.');
      this.router.navigate(['/dashboard']); // Navigate to dashboard on success
    } catch (error) {
      console.error('RegisterConfirmComponent: Registration failed:', error);
      this.errorMessage = 'Failed to complete registration. Please try again later.';
      if (error instanceof HttpErrorResponse) {
        // Customize error message based on backend response if needed
        this.errorMessage = `Registration failed: ${error.message || 'Server error'}`;
      }
      // Optionally sign out the user if registration fails critically
      // await this.authService.signOut();
    } finally {
      this.isProcessing = false;
    }
  }

  async cancelRegistration(): Promise<void> {
    if (this.isProcessing) return; // Prevent action while confirm is processing

    console.log('RegisterConfirmComponent: Cancelling registration and signing out...');
    this.isProcessing = true; // Prevent double clicks
    this.errorMessage = null;
    try {
      await this.authService.signOut();
      // Navigation to '/' is handled by signOut()
    } catch (error) {
      console.error('RegisterConfirmComponent: Error during sign out:', error);
      this.errorMessage = 'An error occurred during sign out.';
      this.isProcessing = false; // Re-enable buttons if signout fails
    }
    // No finally block needed here as successful signOut navigates away
  }
}
