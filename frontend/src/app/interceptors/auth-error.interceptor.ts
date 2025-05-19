import { inject } from '@angular/core';
import {
  HttpInterceptorFn,
  HttpHandlerFn,
  HttpRequest,
  HttpErrorResponse
} from '@angular/common/http';
import { throwError, EMPTY } from 'rxjs';
import { catchError, tap } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';
import { ApiService } from '../services/api.service';

export const authInterceptor: HttpInterceptorFn = ( req: HttpRequest<unknown>, next: HttpHandlerFn ) => {
  const authService = inject(AuthService);
  const apiService = inject(ApiService);
  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      console.error('HTTP Error:', error);
      if (error.status == 401) {
        console.error('authInterceptor: Caught 401 Unauthorized error. Signing out.');
        apiService.clearAllCaches();
        // signOut() handles backend logout AND navigation.
        authService.signOut();
      }
      return throwError(error);
    })
  );
};
