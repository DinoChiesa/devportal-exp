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

import { Routes } from '@angular/router';
import { WelcomeComponent } from './components/welcome/welcome.component';
import { ApiCatalogComponent } from './components/api-catalog/api-catalog.component';
import { DashboardComponent } from './components/dashboard/dashboard.component';
import { DeveloperAppsComponent } from './components/developer-apps/developer-apps.component';
import { AccountDetailsComponent } from './components/account-details/account-details.component';
import { RegisterConfirmComponent } from './components/register-confirm/register-confirm.component'; // Import the new component
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: '', component: WelcomeComponent, pathMatch: 'full' },
  {
    path: 'register-confirm', // Route for the new registration confirmation component
    component: RegisterConfirmComponent,
    canActivate: [authGuard] // Protect this route
  },
  {
    path: 'dashboard',
    component: DashboardComponent,
    canActivate: [authGuard]
  },
  {
    path: 'catalog',
    component: ApiCatalogComponent,
    canActivate: [authGuard]
  },
  {
    path: 'apps',
    component: DeveloperAppsComponent,
    canActivate: [authGuard]
  },
  {
    path: 'account', // New account details route
    component: AccountDetailsComponent,
    canActivate: [authGuard]
  },
  // Add other routes here later
  { path: '**', redirectTo: '' } // Redirect unknown paths to welcome page
];
