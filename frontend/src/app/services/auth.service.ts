import { Injectable, inject, Injector } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Auth, GoogleAuthProvider, signInWithPopup, signOut, user, User } from '@angular/fire/auth';
import { Observable, from, of, switchMap, take, tap, catchError, EMPTY, lastValueFrom } from 'rxjs';
import { Router } from '@angular/router';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private auth: Auth = inject(Auth);
  private router: Router = inject(Router);
  private http = inject(HttpClient);
  private injector = inject(Injector); // Inject the Injector (why?)

  private authApiUrl = '/api/auth';

  readonly user$: Observable<User | null> = user(this.auth); // Observable stream of user state

  constructor() {
    // Log user state changes
    this.user$.subscribe(user => {
      console.log('Auth State Changed:', user ? `Logged in as ${user.email}` : 'Logged out');
    });
  }

  async googleSignIn(): Promise<void> {
    const provider = new GoogleAuthProvider();
    provider.setCustomParameters({
      prompt: 'select_account' // always prompt
    });
    try {
      const credential = await signInWithPopup(this.auth, provider);
      const firebaseUser = credential.user;
      console.log('Google Sign-In successful:', firebaseUser);

      if (firebaseUser) {
        // Get ID token
        const idToken = await firebaseUser.getIdToken();

        // Send token to backend to establish session.
        // Convert the observable to a promise and wait for it.
        // The promise resolves on success, rejects on HTTP error.
        await lastValueFrom(this.establishSession(idToken));
        console.log('AuthService: Backend session established successfully.');
        // NOTE: Navigation and developer check/registration are now handled by the calling component (WelcomeComponent)

      } else {
         console.error('Firebase user object is null after sign-in.');
         // Handle this unexpected state
         throw new Error('Firebase user object is null after sign-in.'); // Re-throw to signal failure
      }

    } catch (error) {
      console.error('Google Sign-In failed:', error);
      // Handle specific errors (e.g., popup closed, network error) if needed
      throw error;
    }
  }

  async signOut(): Promise<void> {
    // First, try to log out from the backend session
    this.http.post(`${this.authApiUrl}/logout`, {}, { withCredentials: true }).pipe( // Use authApiUrl here
      catchError(err => {
        console.warn('Backend logout failed (maybe session expired?):', err);
        return of(null); // Continue even if backend logout fails
      })
    ).subscribe(async () => {
      // Then, sign out from Firebase
      try {
        await signOut(this.auth);
        console.log('Firebase Sign-out successful.');
        // Navigate back to the welcome page after sign-out
        this.router.navigate(['/']);
      } catch (error) {
        console.error('Firebase Sign-out failed:', error);
      }
    });
  }

  /** Sends ID token to backend to create a session */
  private establishSession(idToken: string): Observable<any> {
    console.log('AuthService: Sending ID token to backend for session creation...');
    return this.http.post(`${this.authApiUrl}/login`, { idToken }, { withCredentials: true });
  }

  /**
   * Gets the current user's Firebase ID token.
   * Returns an Observable that emits the token string or null if not authenticated.
   */
  getIdToken(): Observable<string | null> {
    return this.user$.pipe(
      take(1), // Take the current user state
      switchMap(user => { // switchMap is already imported correctly
        if (user) {
          // If user exists, get the ID token
          return from(user.getIdToken()); // from() converts Promise<string> to Observable<string>
        } else {
          // If no user, return an observable emitting null
          return of(null);
        }
      })
    );
  }

}
