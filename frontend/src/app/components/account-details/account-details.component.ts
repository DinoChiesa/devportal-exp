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
import { Observable, of, Subscription } from 'rxjs';
import { catchError, finalize } from 'rxjs/operators';
import JSZip from 'jszip';
import { ApiService } from '../../services/api.service';
import { DeveloperDetails } from '../../models/developer-details.model';
import { NavbarComponent } from '../navbar/navbar.component';

@Component({
  selector: 'app-account-details',
  standalone: true,
  imports: [CommonModule, NavbarComponent],
  templateUrl: './account-details.component.html',
  styleUrls: ['./account-details.component.css']
})
export class AccountDetailsComponent implements OnInit {
  private apiService = inject(ApiService);
  private detailsSubscription: Subscription | undefined;
  details: DeveloperDetails | null = null;
  isLoading = true;
  errorLoading = false;

  // --- State for modals and menus ---
  showConfirmationModal = false;
  isGeneratingCertificate = false;
  certificateGenerationStatus: string[] = [];
  showCertMenu = false;
  showUploadModal = false;
  selectedFile: File | null = null;
  uploadError: string | null = null;
  isUploading = false;

  private delay(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  ngOnInit(): void {
    this.loadDeveloperDetails();
  }

  private loadDeveloperDetails(): void {
    this.isLoading = true;
    this.errorLoading = false;
    this.details = null;
    console.log('AccountDetailsComponent: loadDeveloperDetails - Loading developer details.');
    const developerDetails$ = this.apiService.getDeveloperDetails().pipe(
      catchError(err => {
        console.error('AccountDetailsComponent: Error loading developer details pipeline:', err);
        this.errorLoading = true;
        return of(null);
      }),
      // Use finalize regardless of success/error to stop loading indicator
      finalize(() => {
          console.log('AccountDetailsComponent: Detail loading finalized.');
          this.isLoading = false;
      })
    );

    // Manually subscribe to trigger the pipeline
    this.detailsSubscription = developerDetails$.subscribe({
      next: (retrievedDetails) => {
        console.log('AccountDetailsComponent: Received details:', retrievedDetails);
        // retrievedDetails could be null if catchError returned of(null)
        this.details = retrievedDetails;
        this.isLoading = false;
        // Only set errorLoading false if details were actually received
        this.errorLoading = !retrievedDetails;
      },
      error: (err) => {
        console.error('AccountDetailsComponent: Error subscribing to details pipeline:', err);
        this.details = null; // Ensure details is null on error
        this.isLoading = false;
        this.errorLoading = true;
      },
      complete: () => {
        console.log('AccountDetailsComponent: Details loading pipeline completed.');
        // Ensure loading is false on completion if next/error didn't run (e.g., empty initial list)
         if (this.isLoading) {
             console.log('AccountDetailsComponent: Setting isLoading = false on completion.');
             this.isLoading = false;
         }
      }
    });
  }

   // Helper to format timestamp string (assuming it's epoch milliseconds)
   formatDate(timestampStr: string | undefined): string {
    if (!timestampStr) return 'N/A';
    try {
      const timestamp = parseInt(timestampStr, 10);
      if (isNaN(timestamp)) return 'Invalid Date';
      return new Date(timestamp).toLocaleString(); // Adjust format as needed
    } catch (e) {
      return 'Invalid Date';
    }
  }

  // --- Certificate Generation Logic (with Modals and Delays) ---

  // Method called by the main button to show the confirmation modal
  requestCertificateGeneration(): void {
    this.showConfirmationModal = true;
    this.showCertMenu = false; // Close menu when starting generation
  }

  // Method called by the Cancel button on the confirmation modal
  cancelCertificateGeneration(): void {
    this.showConfirmationModal = false;
  }

  // Renamed original method, now called by Accept button on confirmation modal
  async startCertificateGenerationProcess(): Promise<void> {
    this.showConfirmationModal = false; // Close confirmation modal
    // --- Start Progress Modal ---
    this.isGeneratingCertificate = true;
    this.certificateGenerationStatus = [];

    try {
      // --- Step 1: Initial Delay + Generate Keys ---
      await this.delay(2200);
      this.certificateGenerationStatus.push("Generating an RSA keypair, with modulus length of 2048...");
      const keyPair = await window.crypto.subtle.generateKey(
        {
          name: "RSASSA-PKCS1-v1_5", // Standard algorithm
          modulusLength: 2048,
          publicExponent: new Uint8Array([0x01, 0x00, 0x01]), // 65537
          hash: { name: "SHA-256" },
        },
        true,
        ["sign", "verify"]
      );
      // --- End Step 1 ---

      // --- Step 2: Delay + Export Keys ---
      await this.delay(1200);
      this.certificateGenerationStatus.push("Exporting the public and private keys...");
      const privateKeyBuffer = await window.crypto.subtle.exportKey(
        "pkcs8", // Standard format for private keys
        keyPair.privateKey
      );
      const publicKeyBuffer = await window.crypto.subtle.exportKey(
        "spki", // Standard format for public keys
        keyPair.publicKey
      );
      console.log('Key pair generated and exported.');
      // --- End Step 2 ---

      // Format keys (no delay specified, happens quickly)
      const privateKeyPem = this.formatPrivateKeyToPem(privateKeyBuffer);
      const publicKeyPem = this.formatPublicKeyToPem(publicKeyBuffer);
      const timestamp = this.getFormattedTimestamp();
      const key_id = this.generateKeyId();

      // Ensure details and email are available before proceeding
      if (!this.details?.email) {
        const errorMsg = "Error: Could not retrieve developer email. Cannot proceed.";
        console.error(errorMsg);
        this.certificateGenerationStatus.push(errorMsg);
        await this.delay(1500); // Give time to read error in modal
        alert("Could not retrieve developer email for download. Please try reloading the page.");
        this.isGeneratingCertificate = false; // Close modal
        return;
      }

      // --- Step 3: Delay + Send Request ---
      await this.delay(1400);
      this.certificateGenerationStatus.push("Sending the request to generate and register a certificate...");

      // Wrap API call in a Promise to await its completion
      await new Promise<void>((resolve, reject) => {
        this.apiService.generateAndRegisterCertificate(key_id, publicKeyPem).subscribe({
          next: async (resp) => { // Make next handler async
            try {
              // --- Step 4: Delay + Process Response ---
              await this.delay(1400);
              const fingerprint = resp.fingerprint || 'N/A';
              this.certificateGenerationStatus.push(`Received the certificate. Fingerprint: ${fingerprint}`);
              // --- End Step 4 ---

              // --- Step 5: Delay + Create ZIP and Initiate Download ---
              await this.delay(1400);
              this.certificateGenerationStatus.push("Creating a ZIP archive with your key and certificate...");
              await this.delay(1400);

              const zip = new JSZip();
              const manifestContent = [`Partner Connection Credential\n`,
                                       `key id: ${key_id}`,
                                       `created: ${timestamp}`,
                                       `email of requester: ${this.details?.email || 'N/A'}\n`,
                                       `Certificate information:`,
                                       `  Subject DN: ${resp.subjectDN || 'N/A'}`,
                                       `  SHA256 fingerprint: ${fingerprint}`,
                                       `  notBefore: ${resp.notBefore || 'N/A'}`,
                                       `  notAfter: ${resp.notAfter || 'N/A'}`].join('\n') + '\n';

              zip.file("Manifest.txt", manifestContent);
              zip.file(`client-rsa-private-key-${key_id}.pem`, privateKeyPem);
              zip.file(`client-certificate-${key_id}.pem`, resp.pem);
              this.certificateGenerationStatus.push("Initiating the download...");
              await this.delay(1150);

              // Generate ZIP file as Blob
              const zipBlob = await zip.generateAsync({ type: "blob" });

              // Trigger download
              const url = URL.createObjectURL(zipBlob);
              const a = document.createElement('a');
              a.href = url;
              a.download = `credential-${key_id}.zip`; // Use key_id in filename
              document.body.appendChild(a);
              a.click();
              document.body.removeChild(a);
              URL.revokeObjectURL(url);
              console.log('Credential ZIP archive download initiated.');
              // --- End Step 5 ---

              // --- Step 6: Final Message + Refresh ---
              this.certificateGenerationStatus.push("Done.");
              await this.delay(3200);
              // --- End Step 6 ---

              this.loadDeveloperDetails(); // Refresh details in background
              resolve(); // Signal success for the outer Promise
            } catch (innerError) {
              reject(innerError); // Propagate errors from within next
            }
          },
          error: async (err) => { // Make error handler async
            const errorMsg = `Error: Failed backend registration. ${err.error?.message || err.message || 'Unknown error'}`;
            console.error('Failed to register certificate with backend:', err);
            this.certificateGenerationStatus.push(errorMsg);
            await this.delay(1500); // Give time to read error in modal
            alert(`Failed to register certificate with backend. ${err.error?.message || err.message || 'Please try again.'}`);
            reject(err); // Signal failure for the outer Promise
          }
        });
      });

      // This runs only if the Promise resolved (API call was successful)
      this.isGeneratingCertificate = false; // Close modal on success

    } catch (error: any) {
      const errorMsg = `Error during key generation/export: ${error.message || 'Unknown error'}`;
      console.error('Error generating or exporting keys:', error);
      // Avoid adding duplicate error messages if already added
      if (!this.certificateGenerationStatus.some(s => s.startsWith('Error:'))) {
         this.certificateGenerationStatus.push(errorMsg);
      }
      await this.delay(1500); // Give time to read error in modal
      // Avoid double alerting if API call already alerted
      if (!error?.error?.message) {
          alert('Failed to generate keys. See console for details.');
      }
      this.isGeneratingCertificate = false; // Close modal on error
    }
  }

  private generateKeyId(): string {
    const chars = 'abcdefghijklmnopqrstuvwxyz0123456789';
    const segmentLength = 3;
    const numSegments = 3;
    let segments: string[] = [];

    for (let i = 0; i < numSegments; i++) {
      let segment = '';
      for (let j = 0; j < segmentLength; j++) {
        segment += chars.charAt(Math.floor(Math.random() * chars.length));
      }
      segments.push(segment);
    }

    return segments.join('-'); // Join segments with dashes
  }

  private arrayBufferToBase64(buffer: ArrayBuffer): string {
    let binary = '';
    const bytes = new Uint8Array(buffer);
    const len = bytes.byteLength;
    for (let i = 0; i < len; i++) { binary += String.fromCharCode(bytes[i]); }
    return window.btoa(binary);
  }

  private formatPrivateKeyToPem(pkcs8Buffer: ArrayBuffer): string {
    const base64Key = this.arrayBufferToBase64(pkcs8Buffer);
    let pemKey = "-----BEGIN PRIVATE KEY-----\n";
    let offset = 0;
    while (offset < base64Key.length) {
      pemKey += base64Key.substring(offset, offset + 64) + "\n";
      offset += 64;
    }
    pemKey += "-----END PRIVATE KEY-----\n";
    return pemKey;
  }

   private formatPublicKeyToPem(spkiBuffer: ArrayBuffer): string {
    const base64Key = this.arrayBufferToBase64(spkiBuffer);
    let pemKey = "-----BEGIN PUBLIC KEY-----\n";
    let offset = 0;
    while (offset < base64Key.length) {
      pemKey += base64Key.substring(offset, offset + 64) + "\n";
      offset += 64;
    }
    pemKey += "-----END PUBLIC KEY-----\n";
    return pemKey;
  }

  private getFormattedTimestamp(): string {
    const now = new Date();
    const year = now.getFullYear();
    const month = (now.getMonth() + 1).toString().padStart(2, '0');
    const day = now.getDate().toString().padStart(2, '0');
    const hours = now.getHours().toString().padStart(2, '0');
    const minutes = now.getMinutes().toString().padStart(2, '0');
    const seconds = now.getSeconds().toString().padStart(2, '0'); // Get and pad seconds
    return `${year}${month}${day}-${hours}${minutes}${seconds}`; // Add seconds to the format
  }

  // --- Certificate Action Menu Logic ---
  toggleCertMenu(): void {
    this.showCertMenu = !this.showCertMenu;
  }

  // --- Upload Certificate Logic ---

  // Called when "Upload" option is selected from menu
  requestCertificateUpload(): void {
    this.showUploadModal = true;
    this.showCertMenu = false; // Close menu
    this.selectedFile = null; // Reset file selection
    this.uploadError = null; // Reset error
    this.isUploading = false; // Reset upload state
  }

  // Called by Cancel button on upload modal
  cancelUpload(): void {
    this.showUploadModal = false;
    this.selectedFile = null;
    this.uploadError = null;
    this.isUploading = false;
  }

  // Called when a file is selected in the input
  onFileSelected(event: Event): void {
    const element = event.currentTarget as HTMLInputElement;
    let fileList: FileList | null = element.files;
    this.uploadError = null; // Clear previous error on new selection

    if (fileList && fileList.length > 0) {
      const file = fileList[0];
      // Validate file type
      if (!file.name.toLowerCase().endsWith('.pem') && !file.name.toLowerCase().endsWith('.cer')) {
        this.uploadError = 'Invalid file type. Please select a .pem or .cer file.';
        this.selectedFile = null;
        element.value = ''; // Clear the input
        return;
      }
      this.selectedFile = file;
      console.log('File selected for upload:', this.selectedFile.name);
    } else {
      this.selectedFile = null;
    }
  }

  // Called by Upload button on upload modal
  uploadSelectedCertificate(): void {
    if (!this.selectedFile) {
      this.uploadError = 'Please select a certificate file first.';
      return;
    }
    if (this.isUploading) return;

    this.isUploading = true;
    this.uploadError = null;
    const reader = new FileReader();

    reader.onload = (e: ProgressEvent<FileReader>) => {
      const certPem = e.target?.result as string;
      if (!certPem) {
        this.uploadError = 'Could not read the selected file.';
        this.isUploading = false;
        return;
      }

      console.log('Attempting to upload certificate content...');
      this.apiService.uploadCertificate('placeholder-keyid', certPem).subscribe({
        next: () => {
          console.log('Certificate uploaded successfully.');
          this.isUploading = false;
          this.showUploadModal = false;
          this.loadDeveloperDetails(); // Refresh details
        },
        error: (e) => {
          console.error('Failed to upload certificate:', e);
          this.isUploading = false;
          this.uploadError = `Upload failed: ${e.error?.error || e.message || 'Server error'}`;
        }
      });
    };

    reader.onerror = () => {
      this.uploadError = 'Error reading the file.';
      this.isUploading = false;
    };

    reader.readAsText(this.selectedFile);
  }


  // --- Delete Certificate Logic ---
  deleteCertificate(certId: string): void {
    if (!certId) {
      console.error('Cannot delete certificate without an ID.');
      return;
    }
    // // Optional: Add a confirmation dialog
    // if (!confirm(`Are you sure you want to delete certificate ${certId}? This cannot be undone.`)) {
    //   return;
    // }

    console.log(`AccountDetailsComponent: Attempting to delete certificate ${certId}`);
    // TODO: Add visual feedback for deletion in progress?

    this.apiService.deregisterCertificate(certId).subscribe({
      next: () => {
        console.log(`Certificate ${certId} deleted successfully.`);
        // Update the local list instead of reloading everything
        if (this.details && this.details.certificates) {
          this.details.certificates = this.details.certificates.filter(cert => cert.id !== certId);
          console.log(`Local certificate list updated after deleting ${certId}.`);
        } else {
          // Fallback or log if details/certificates are unexpectedly null
          console.warn('Details or certificates array was null, reloading details as a fallback.');
          this.loadDeveloperDetails();
        }
      },
      error: (err) => {
        console.error(`Failed to delete certificate ${certId}:`, err);
        alert(`Failed to delete certificate ${certId}. ${err.error?.message || err.message || 'Please try again.'}`);
        // Optionally handle UI state here if needed
      }
    });
  }

}
