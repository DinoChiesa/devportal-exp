import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service'; // Import AuthService
import { map, take, tap } from 'rxjs/operators';
import { Observable } from 'rxjs';

export const authGuard: CanActivateFn = (route, state): Observable<boolean> => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.user$.pipe(
    take(1), // Take the first emission to avoid ongoing subscription
    map(user => !!user), // Map the user object to a boolean (true if user exists, false otherwise)
    tap(isLoggedIn => {
      if (!isLoggedIn) {
        console.log('AuthGuard: User not logged in, redirecting to welcome page.');
        router.navigate(['/']); // Redirect to the welcome page if not logged in
      } else {
        console.log('AuthGuard: User is logged in, allowing access.');
      }
    })
  );
};
