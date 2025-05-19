// Defines the structure for API Product data received from the backend
export interface ApiProduct {
  id: string;
  name: string;
  description: string;
  specUrl?: string; // Optional: URL to the API specification
  // Add other relevant fields as needed
}
