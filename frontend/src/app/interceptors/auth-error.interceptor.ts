import { inject } from '@angular/core';
import {
  HttpInterceptorFn,
  HttpHandlerFn,
  HttpRequest,
  HttpErrorResponse
} from '@angular/common/http';
import { throwError } from 'rxjs';
import { catchError, switchMap, take } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';
import { ApiService } from '../services/api.service';

export const authInterceptor: HttpInterceptorFn = ( req: HttpRequest<unknown>, next: HttpHandlerFn ) => {
  const authService = inject(AuthService);
  const apiService = inject(ApiService);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        // Prevent re-login loop if the /api/auth/login request itself fails with 401
        if (req.url.includes('/api/auth/login')) {
          console.error('authInterceptor: 401 on login request itself. Signing out.');
          apiService.clearAllCaches();
          authService.signOut();
          return throwError(() => error);
        }

        // Try to re-establish a session with the backend if there is an active frontend Firebase auth user
        return authService.user$.pipe(
          take(1), // Get the current Firebase user state
          switchMap(user => {
            if (user) {
              // Firebase user exists, try to get ID token and re-establish session
              console.log('authInterceptor: 401 detected. Attempting to re-establish session.');
              return authService.getIdToken().pipe(
                switchMap(idToken => {
                  if (idToken) {
                    return authService.establishSession(idToken).pipe(
                      switchMap(() => {
                        console.log('authInterceptor: Session re-established. Retrying original request.');
                        return next(req);
                      }),
                      catchError(reLoginError => {
                        console.error('authInterceptor: Failed to re-establish session after 401. Signing out.', reLoginError);
                        apiService.clearAllCaches();
                        authService.signOut();
                        return throwError(() => reLoginError); // Or original error
                      })
                    );
                  } else {
                    // No ID token available
                    console.error('authInterceptor: no ID token available. Signing out.');
                    apiService.clearAllCaches();
                    authService.signOut();
                    return throwError(() => error); // Original 401 error
                  }
                })
              );
            } else {
              // No Firebase user, proceed with sign out
              console.error('authInterceptor: 401 detected. No Firebase user. Signing out.');
              apiService.clearAllCaches();
              authService.signOut();
              return throwError(() => error); // Original 401 error
            }
          })
        );
      }
      console.error('HTTP Error:', error);
      return throwError(() => error);
    })
  );
};
