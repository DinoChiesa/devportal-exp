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

import { Component, inject, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { Subscription, lastValueFrom } from 'rxjs';
import { take } from 'rxjs/operators';
import { AuthService } from '../../services/auth.service';
import { ApiService } from '../../services/api.service';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
  selector: 'app-welcome',
  standalone: true,
  imports: [CommonModule], // Add CommonModule if using directives
  templateUrl: './welcome.component.html',
  styleUrls: ['./welcome.component.css']
})
export class WelcomeComponent implements OnInit, OnDestroy {
  private authService = inject(AuthService);
  private apiService = inject(ApiService); // Inject ApiService
  private router = inject(Router);
  private authSubscription: Subscription | undefined;
  isSigningIn = false;
  signInError: string | null = null;

  ngOnInit(): void {
    // Check auth state once when component loads
    this.authSubscription = this.authService.user$.pipe(
      take(1) // Only need the current state, don't keep listening here
    ).subscribe(user => {
      if (user) {
        console.log('WelcomeComponent: User already logged in, redirecting to /dashboard');
        this.router.navigate(['/dashboard']); // Redirect to dashboard
      } else {
        console.log('WelcomeComponent: User not logged in.');
      }
    });
  }

  ngOnDestroy(): void {
    // Unsubscribe to prevent memory leaks, although take(1) mostly handles this
    this.authSubscription?.unsubscribe();
  }

  async signIn(): Promise<void> {
    if (this.isSigningIn) {
      return; // Prevent multiple clicks
    }
    this.isSigningIn = true;
    this.signInError = null; // Clear previous errors

    try {
      // Step 1: Sign in via Firebase and establish backend session
      await this.authService.googleSignIn();
      console.log('WelcomeComponent: Google Sign-In and session established.');

      // Step 2: Check if the developer is registered
      try {
        console.log('WelcomeComponent: Checking developer details...');
        // Use lastValueFrom to convert Observable to Promise
        await lastValueFrom(this.apiService.getDeveloperDetails());
        console.log('WelcomeComponent: Developer found. Navigating to dashboard.');
        this.router.navigate(['/dashboard']);
        // No need to reset isSigningIn here as we are navigating away
      } catch (detailsError) {
        if (detailsError instanceof HttpErrorResponse && detailsError.status === 404) {
          // Step 3a: Developer not found (404), navigate to confirmation page
          console.log('WelcomeComponent: Developer not found (404). Navigating to registration confirmation.');
          this.router.navigate(['/register-confirm']);
          // No need to reset isSigningIn here as we are navigating away
        } else {
          // Step 3b: Other error fetching details
          console.error('WelcomeComponent: Error checking developer details:', detailsError);
          this.signInError = 'Failed to verify developer status after sign-in.';
          await this.authService.signOut(); // Sign out on error
          this.isSigningIn = false; // Reset flag
        }
      }
    } catch (signInError) {
      // Step 1 failed (Firebase sign-in or session establishment)
      console.error("WelcomeComponent: Sign-in process failed:", signInError);
      this.signInError = 'Sign-in failed. Please try again.';
      // Ensure user is signed out if googleSignIn failed partially
      // Check current user status via observable before attempting sign out
      const user = await lastValueFrom(this.authService.user$.pipe(take(1)));
      if (user) {
         console.log('WelcomeComponent: Cleaning up sign-in failure by signing out.');
         await this.authService.signOut().catch(e => console.error("Error during cleanup signout", e));
      }
      this.isSigningIn = false; // Reset flag on failure
    }
    // Note: isSigningIn is intentionally NOT reset to false on successful navigation
    // because the component instance will be destroyed. It's only reset on errors
    // where the user stays on the WelcomeComponent.
  }
}
